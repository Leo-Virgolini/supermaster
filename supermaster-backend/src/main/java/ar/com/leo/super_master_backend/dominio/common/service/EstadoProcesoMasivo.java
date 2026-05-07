package ar.com.leo.super_master_backend.dominio.common.service;

import ar.com.leo.super_master_backend.dominio.common.dto.ProcesoMasivoEstadoDTO;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Encapsula el ciclo de vida de un proceso masivo: lock global, lock local,
 * estado (idle / ejecutando / completado / cancelado) con sus contadores, y la
 * exposición del progreso al SSE vía {@link ProcesoGlobalService#adquirir}.
 *
 * <p>Una instancia por proceso (e.g., uno para "recalculo-precios", otro para
 * "dux-importacion", etc.). Se construye una vez en el constructor del servicio
 * y se reusa para cada ejecución.
 *
 * <p>Ciclo típico:
 * <pre>
 *   if (!tracker.adquirir()) return false;       // lock global + local + reset estado
 *   try {
 *       tracker.actualizar(total, 0, 0, 0, "...");
 *       for (...) {
 *           ...
 *           tracker.actualizar(total, procesados, exitosos, errores, "...");
 *       }
 *       tracker.completar(total, exitosos, errores, "Completado");
 *   } catch (Exception e) {
 *       tracker.completarConError(e.getMessage());
 *   }
 * </pre>
 *
 * <p>Garantías:
 * <ul>
 *   <li>{@link #adquirir()} resetea el estado a "ejecutando(0)" ANTES de hacer
 *       broadcastEstado() en {@code procesoGlobal.adquirir}, así el primer evento
 *       SSE no transporta datos stale del proceso anterior.</li>
 *   <li>Si {@link #adquirir()} falla (otro proceso del grupo activo o este mismo
 *       en ejecución), hace rollback al estado previo para no contaminar el
 *       supplier del proceso ya en marcha.</li>
 *   <li>{@link #completar} y {@link #completarConError} liberan el lock global y
 *       el local. {@link ProcesoGlobalService#liberar} hace un broadcast extra con
 *       el estado final ANTES de remover el proceso del map: así el cliente recibe
 *       los contadores definitivos para clasificar la notificación.</li>
 * </ul>
 *
 * <p>Para procesos sin progreso (e.g., {@code ejecutarRecalculoAsync(Runnable)}),
 * usar el API raw de {@link ProcesoGlobalService} directamente.
 */
public class EstadoProcesoMasivo {

    private final String procesoId;
    private final String descripcionDefault;
    private final ProcesoGlobalService procesoGlobal;

    private volatile ProcesoMasivoEstadoDTO estado = ProcesoMasivoEstadoDTO.idle();
    private final AtomicBoolean enEjecucion = new AtomicBoolean(false);
    private final AtomicBoolean cancelado = new AtomicBoolean(false);

    public EstadoProcesoMasivo(String procesoId, String descripcionDefault,
                               ProcesoGlobalService procesoGlobal) {
        this.procesoId = procesoId;
        this.descripcionDefault = descripcionDefault;
        this.procesoGlobal = procesoGlobal;
    }

    /** Adquisición con la descripción default. */
    public boolean adquirir() {
        return adquirir(descripcionDefault);
    }

    /**
     * Adquiere el lock global y el local. Si tiene éxito, deja el estado en
     * "ejecutando(0)" y registra el supplier de progreso para el SSE.
     *
     * @param descripcion descripción dinámica que aparece en el badge del header.
     * @return true si se adquirió, false si hay conflicto de grupo o ya estaba
     *         ejecutando.
     */
    public boolean adquirir(String descripcion) {
        ProcesoMasivoEstadoDTO previo = estado;
        estado = ProcesoMasivoEstadoDTO.iniciado(0, LocalDateTime.now());
        if (!procesoGlobal.adquirir(procesoId, descripcion, this::obtener)) {
            estado = previo;
            return false;
        }
        if (!enEjecucion.compareAndSet(false, true)) {
            estado = previo;
            procesoGlobal.liberar(procesoId);
            return false;
        }
        cancelado.set(false);
        return true;
    }

    /** Estado actual (lo que el supplier expone al SSE). */
    public ProcesoMasivoEstadoDTO obtener() {
        return estado;
    }

    /**
     * Refresca los contadores mientras el proceso está en ejecución. Se puede
     * llamar tan seguido como se quiera; el broadcast del SSE sale cada 2s y solo
     * mientras hay procesos activos. Usá esto cada N items procesados para no
     * martillar el volatile.
     */
    public void actualizar(int total, int procesados, int exitosos, int errores, String mensaje) {
        ProcesoMasivoEstadoDTO actual = estado;
        LocalDateTime iniciadoEn = actual.iniciadoEn() != null ? actual.iniciadoEn() : LocalDateTime.now();
        estado = new ProcesoMasivoEstadoDTO(true, total, procesados, exitosos, errores,
                "ejecutando", iniciadoEn, null, mensaje);
    }

    /** Variante sin contadores (solo cambiar mensaje, total=procesados=0). */
    public void actualizar(String mensaje) {
        actualizar(0, 0, 0, 0, mensaje);
    }

    /**
     * Marca el proceso como completado con éxito y libera locks. El estado queda
     * con enEjecucion=false, finalizadoEn=ahora, estado="completado" o "cancelado"
     * según si hubo cancelación. El último broadcast pre-liberar lleva estos
     * contadores al cliente.
     */
    public void completar(int total, int exitosos, int errores, String mensaje) {
        ProcesoMasivoEstadoDTO actual = estado;
        LocalDateTime iniciadoEn = actual.iniciadoEn() != null ? actual.iniciadoEn() : LocalDateTime.now();
        String estadoStr = cancelado.get() ? "cancelado" : "completado";
        estado = new ProcesoMasivoEstadoDTO(false, total, total, exitosos, errores,
                estadoStr, iniciadoEn, LocalDateTime.now(), mensaje);
        liberarInterno();
    }

    /** Marca el proceso como completado con error fatal y libera locks. */
    public void completarConError(String error) {
        ProcesoMasivoEstadoDTO actual = estado;
        LocalDateTime iniciadoEn = actual.iniciadoEn() != null ? actual.iniciadoEn() : LocalDateTime.now();
        estado = new ProcesoMasivoEstadoDTO(false, 0, 0, 0, 0,
                "completado", iniciadoEn, LocalDateTime.now(), "Error: " + error);
        liberarInterno();
    }

    /**
     * Libera locks sin tocar el estado. Idempotente: si ya fue liberado, no hace
     * nada. Útil en bloques {@code finally} como safety net si algo crashea entre
     * {@link #adquirir()} y {@link #completar} / {@link #completarConError}.
     */
    public void liberar() {
        if (enEjecucion.compareAndSet(true, false)) {
            procesoGlobal.liberar(procesoId);
        }
    }

    private void liberarInterno() {
        liberar();
    }

    /** Marca cancelación. El servicio decide cuándo chequear y cortar el bucle. */
    public void cancelar() {
        cancelado.set(true);
    }

    public boolean estaCancelado() {
        return cancelado.get();
    }

    /**
     * Acceso al AtomicBoolean interno de cancelación. Útil para pasar la flag a
     * helpers que esperan {@code AtomicBoolean} (ej. {@code DuxService.importarProductosSincrono}).
     * No mutar desde afuera; usar {@link #cancelar()} para señalar cancelación.
     */
    public AtomicBoolean getFlagCancelado() {
        return cancelado;
    }

    public boolean estaEjecutando() {
        return enEjecucion.get();
    }
}

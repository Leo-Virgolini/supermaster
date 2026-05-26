package ar.com.leo.super_master_backend.dominio.common.service;

import ar.com.leo.super_master_backend.dominio.common.dto.ProcesoActivoDTO;
import ar.com.leo.super_master_backend.dominio.common.dto.ProcesoMasivoEstadoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Servicio que controla la ejecución de procesos masivos usando grupos de exclusión.
 * Procesos del mismo grupo no pueden correr en paralelo, pero procesos de grupos
 * distintos sí pueden.
 *
 * <p>Grupos:
 * <ul>
 *   <li><b>ML</b>: costo-envio, costo-venta, automatizacion-precios</li>
 *   <li><b>DUX</b>: dux-importacion, dux-deudas, dux-sync-programada, automatizacion-precios</li>
 *   <li><b>BD</b>: recalculo-precios, recalculo-canal, recalculo-pendiente-scoped, dux-importacion, dux-sync-programada, automatizacion-precios</li>
 *   <li><b>reposicion</b>: sin grupo (corre libre, no bloquea ni es bloqueado)</li>
 * </ul>
 *
 * <p>automatizacion-precios pertenece a ML, DUX y BD, por lo que bloquea los tres grupos.
 *
 * <p>Cada proceso puede registrar opcionalmente un supplier de progreso ({@link
 * ProcesoMasivoEstadoDTO}) que el SSE consulta en cada snapshot. Permite empujar
 * "procesados / total / errores" a los clientes sin que cada caller tenga que tocar
 * el SSE directamente.
 */
@Slf4j
@Service
public class ProcesoGlobalService {

    public record ProcesoInfo(String id, String descripcion, Set<String> grupos,
                                  String usuario, LocalDateTime iniciadoEn,
                                  Supplier<ProcesoMasivoEstadoDTO> progresoSupplier) {}

    // Procesos activos: procesoId -> ProcesoInfo
    private final ConcurrentHashMap<String, ProcesoInfo> procesosActivos = new ConcurrentHashMap<>();

    // ReentrantLock en lugar de synchronized: con virtual threads (Java 21+),
    // synchronized pinea el carrier thread durante toda la sección crítica,
    // degradando los virtual threads a kernel threads bajo contención.
    private final ReentrantLock adquirirLock = new ReentrantLock();

    // Lazy para evitar ciclo si el SSE service algún día consume al ProcesoGlobalService.
    @Autowired
    @Lazy
    private ProcesosActivosSseService sseService;

    // Definición de grupos por proceso (única fuente de verdad — el frontend
    // la consume por REST en /api/procesos/grupos para evitar duplicación).
    // BD: procesos que leen/escriben precios en la base de datos
    // ML: procesos que usan la API de MercadoLibre (rate limit compartido)
    // DUX: procesos que usan la API de DUX ERP (rate limit muy bajo)
    private static final Map<String, Set<String>> GRUPOS_POR_PROCESO = Map.ofEntries(
            Map.entry("recalculo-precios", Set.of("BD", "ML")),
            Map.entry("recalculo-canal", Set.of("BD")),
            Map.entry("recalculo-pendiente-scoped", Set.of("BD")),
            Map.entry("costo-envio", Set.of("ML")),
            Map.entry("costo-venta", Set.of("ML")),
            Map.entry("automatizacion-precios", Set.of("ML", "DUX", "BD")),
            Map.entry("dux-importacion", Set.of("DUX", "BD")),
            Map.entry("dux-deudas", Set.of("DUX")),
            Map.entry("dux-sync-programada", Set.of("DUX", "BD")),
            Map.entry("reposicion", Set.of())  // sin grupo, corre libre
    );

    /**
     * Snapshot inmutable del mapping de grupos por proceso. Pensado para el
     * endpoint REST que sincroniza el frontend; las claves son los IDs de
     * proceso y los valores listas de nombres de grupo.
     */
    public Map<String, List<String>> obtenerGruposPorProceso() {
        var out = new java.util.LinkedHashMap<String, List<String>>();
        for (var e : GRUPOS_POR_PROCESO.entrySet()) {
            out.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return out;
    }

    /**
     * Intenta adquirir el lock para un proceso, sin progress tracking.
     * Verifica que ningún proceso activo comparta grupo con el nuevo.
     *
     * @return true si se adquirió, false si hay conflicto de grupo
     */
    public boolean adquirir(String procesoId, String descripcion) {
        return adquirir(procesoId, descripcion, null);
    }

    /**
     * Intenta adquirir el lock para un proceso, registrando un supplier que el
     * SSE consultará para empujar progreso ({@code procesados / total / errores})
     * a los clientes en cada snapshot.
     *
     * <p>El supplier puede devolver {@code null} si todavía no hay datos: en ese
     * caso los campos de progreso del DTO se serializan como null y el cliente
     * solo muestra descripción + tiempo transcurrido.
     */
    public boolean adquirir(String procesoId, String descripcion,
                            Supplier<ProcesoMasivoEstadoDTO> progresoSupplier) {
        boolean adquirido;
        adquirirLock.lock();
        try {
            Set<String> gruposNuevo = GRUPOS_POR_PROCESO.getOrDefault(procesoId, Set.of());

            // Verificar conflictos de grupo con procesos activos
            for (var entry : procesosActivos.entrySet()) {
                ProcesoInfo activo = entry.getValue();
                // Si comparten al menos un grupo, hay conflicto
                for (String grupo : gruposNuevo) {
                    if (activo.grupos.contains(grupo)) {
                        log.warn("Proceso rechazado: {} - conflicto de grupo '{}' con proceso activo: {} ({})",
                                procesoId, grupo, activo.id, activo.descripcion);
                        return false;
                    }
                }
            }

            // Sin conflicto, registrar
            String usuario = obtenerUsuarioActual();
            procesosActivos.put(procesoId, new ProcesoInfo(procesoId, descripcion, gruposNuevo,
                    usuario, LocalDateTime.now(), progresoSupplier));
            log.info("Proceso adquirido: {} ({}) por {} [grupos: {}]", procesoId, descripcion, usuario, gruposNuevo);
            adquirido = true;
        } finally {
            adquirirLock.unlock();
        }
        // Broadcast fuera del lock: si un emitter está lento, no queremos bloquear a
        // otros threads que intentan adquirir/liberar en paralelo.
        broadcastEstado();
        return adquirido;
    }

    /**
     * Libera el lock de un proceso. Antes de remover, hace un último broadcast con
     * el progreso final del proceso para que el cliente tenga snapshot definitivo
     * (errores/exitosos finales) antes de que desaparezca de la lista.
     */
    public void liberar(String procesoId) {
        ProcesoInfo presente = procesosActivos.get(procesoId);
        if (presente != null && presente.progresoSupplier != null) {
            // Snapshot final con el proceso aún listado, para que el cliente pueda
            // emitir la notificación con el resultado correcto (success/warning/error).
            broadcastEstado();
        }
        ProcesoInfo removed = procesosActivos.remove(procesoId);
        if (removed != null) {
            log.info("Proceso liberado: {}", procesoId);
            broadcastEstado();
        }
    }

    /**
     * Construye el snapshot DTO de procesos activos. Usado tanto por el endpoint
     * REST como por el broadcaster SSE. Si un proceso registró un supplier de
     * progreso, lo consulta y embebe los contadores actuales en el item.
     */
    public ProcesoActivoDTO snapshotDTO() {
        var activos = getProcesosActivos();
        if (activos.isEmpty()) return ProcesoActivoDTO.ninguno();
        List<ProcesoActivoDTO.ProcesoItem> items = activos.stream()
                .map(this::toItem)
                .toList();
        return ProcesoActivoDTO.de(items);
    }

    private ProcesoActivoDTO.ProcesoItem toItem(ProcesoInfo p) {
        Integer total = null;
        Integer procesados = null;
        Integer exitosos = null;
        Integer errores = null;
        String mensaje = null;
        if (p.progresoSupplier() != null) {
            try {
                ProcesoMasivoEstadoDTO estado = p.progresoSupplier().get();
                if (estado != null) {
                    total = estado.total();
                    procesados = estado.procesados();
                    exitosos = estado.exitosos();
                    errores = estado.errores();
                    mensaje = estado.mensaje();
                }
            } catch (Exception e) {
                // No queremos que un supplier roto rompa el snapshot global.
                log.warn("Error consultando supplier de progreso para {}: {}", p.id(), e.getMessage());
            }
        }
        return new ProcesoActivoDTO.ProcesoItem(
                p.id(),
                p.descripcion(),
                p.usuario(),
                p.iniciadoEn() != null ? p.iniciadoEn().toString() : null,
                total,
                procesados,
                exitosos,
                errores,
                mensaje);
    }

    private void broadcastEstado() {
        if (sseService != null) {
            try {
                sseService.broadcast(snapshotDTO());
            } catch (Exception e) {
                log.warn("Error al broadcast de procesos activos por SSE: {}", e.getMessage());
            }
        }
    }

    /**
     * Retorna la lista de procesos activos.
     */
    public List<ProcesoInfo> getProcesosActivos() {
        return List.copyOf(procesosActivos.values());
    }

    /**
     * Verifica si hay al menos un proceso corriendo.
     */
    public boolean hayProcesoActivo() {
        return !procesosActivos.isEmpty();
    }

    private String obtenerUsuarioActual() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() != null) {
                return String.valueOf(auth.getPrincipal()).trim();
            }
        } catch (Exception e) {
            log.trace("No se pudo obtener usuario actual del SecurityContext, usando 'sistema'", e);
        }
        return "sistema";
    }

    /**
     * Retorna el proceso que bloquearía al proceso dado, o null si no hay conflicto.
     */
    public ProcesoInfo getConflicto(String procesoId) {
        Set<String> grupos = GRUPOS_POR_PROCESO.getOrDefault(procesoId, Set.of());
        for (var entry : procesosActivos.entrySet()) {
            ProcesoInfo activo = entry.getValue();
            for (String grupo : grupos) {
                if (activo.grupos.contains(grupo)) {
                    return activo;
                }
            }
        }
        return null;
    }
}

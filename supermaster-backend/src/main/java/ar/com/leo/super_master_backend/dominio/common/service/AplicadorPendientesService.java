package ar.com.leo.super_master_backend.dominio.common.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService.PlanRecalculo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Ejecuta el plan de recálculo pendiente en background. Se separa del endpoint para
 * que la request HTTP retorne inmediatamente y el usuario no vea un spinner colgado
 * mientras el motor procesa N productos/canales.
 *
 * <p>Single-flight: solo un plan a la vez. Si llega un segundo Apply mientras hay uno
 * corriendo, el endpoint devuelve 409 y el usuario espera.
 *
 * <p>La iteración con bulk pre-load (que arregla el N+1) vive en
 * {@link RecalculoBulkExecutor}; los flags de obsolescencia se desmarcan ahí mismo
 * tras cada éxito (los items que fallan quedan marcados para reintento natural en
 * el siguiente Apply, sin necesidad de restore explícito).
 */
@Slf4j
@Service
public class AplicadorPendientesService {

    private static final String PROCESO_ID = "recalculo-pendiente-scoped";
    private static final String PROCESO_DESC = "Aplicando recálculo pendiente";

    private final RecalculoBulkExecutor bulkExecutor;
    private final AuditoriaService auditoriaService;
    private final EstadoProcesoMasivo tracker;

    public AplicadorPendientesService(RecalculoBulkExecutor bulkExecutor,
                                      AuditoriaService auditoriaService,
                                      ProcesoGlobalService procesoGlobal) {
        this.bulkExecutor = bulkExecutor;
        this.auditoriaService = auditoriaService;
        this.tracker = new EstadoProcesoMasivo(PROCESO_ID, PROCESO_DESC, procesoGlobal);
    }

    /**
     * Intenta adquirir el lock local Y el lock global del grupo BD.
     * Devuelve true si lo consiguió, false si:
     *  - ya hay otro plan scoped corriendo (lock local), o
     *  - hay otro proceso del grupo BD corriendo (recálculo masivo, importación DUX, etc.).
     */
    public boolean intentarAdquirir() {
        return tracker.adquirir();
    }

    public boolean estaEjecutando() {
        return tracker.estaEjecutando();
    }

    /**
     * Ejecuta el plan en background. Asume que ya se llamó intentarAdquirir() y se
     * obtuvo true. Libera locks al final y registra UNA fila de auditoría con el
     * resultado real (cuántos productos/canales se recalcularon).
     *
     * <p>Si el bulk executor crashea (excepción fatal), los flags de obsolescencia
     * de los items NO procesados quedan {@code obsoleto = TRUE} naturalmente — el
     * próximo Apply los va a ver. No hace falta restore explícito.
     *
     * @param username usuario que disparó el Apply, capturado en el hilo HTTP: el
     *                 {@code SecurityContext} no se propaga a este hilo @Async, así que
     *                 se pasa explícito para que la auditoría registre el usuario correcto.
     */
    @Async
    public void ejecutarPlanScopedAsync(PlanRecalculo plan, String username) {
        int totalProductos = plan.productos().size();
        int totalCanales = plan.canales().size();
        int total = totalProductos + totalCanales;
        long t0 = System.currentTimeMillis();
        log.info("Aplicando plan scoped: productos={}, canales={}", totalProductos, totalCanales);

        try {
            tracker.actualizar(total, 0, 0, 0, "Iniciando...");

            // Dos fases en transacciones separadas, orquestadas desde acá (bean
            // distinto) para que el proxy de Spring aplique el @Transactional de cada
            // una. Fase 1 (productos) commitea y libera sus locks ANTES de la fase 2
            // (canales): sin eso, el REQUIRES_NEW de cada canal se auto-bloqueaba
            // esperando locks que la transacción de productos retenía.
            int[] resProductos = bulkExecutor.ejecutarProductos(plan, (tot, procesados, ok, errs, descripcion) ->
                    tracker.actualizar(tot, procesados, ok, errs, descripcion));
            int[] resultado = bulkExecutor.ejecutarCanales(plan,
                    resProductos[0], resProductos[1], (tot, procesados, ok, errs, descripcion) ->
                            tracker.actualizar(tot, procesados, ok, errs, descripcion));
            int exitosos = resultado[0];
            int errores = resultado[1];
            int productosEnCanales = resultado[2];

            String mensaje = construirMensajeFinal(totalProductos, totalCanales, exitosos, errores);
            tracker.completar(total, exitosos, errores, mensaje);
            log.info("Plan scoped aplicado en {}ms — {}", System.currentTimeMillis() - t0, mensaje);

            auditarResultado(username,
                    construirResumenAuditoria(totalProductos, totalCanales, productosEnCanales, errores));
        } catch (Exception e) {
            log.error("Error fatal en plan scoped — items no procesados quedan marcados para reintento", e);
            tracker.completarConError(e.getMessage());
            auditarResultado(username, "Error: " + e.getMessage());
        } finally {
            // Idempotente: si completar/completarConError ya liberaron, esto es no-op.
            tracker.liberar();
        }
    }

    /** Registra una sola fila de auditoría del recálculo pendiente aplicado. */
    private void auditarResultado(String username, String resumen) {
        // Origen MANUAL: el recálculo pendiente siempre lo dispara un usuario desde el banner.
        auditoriaService.registrarEvento(
                AuditoriaEntidad.RECALCULO, "pendiente-scoped", AuditoriaAccion.CREATE,
                "aplicar_recalculo_pendiente", resumen, username, "MANUAL");
    }

    /** Resumen legible para la auditoría: incluye el conteo real de productos recalculados. */
    private String construirResumenAuditoria(int totalProductos, int totalCanales,
                                             int productosEnCanales, int errores) {
        StringBuilder sb = new StringBuilder();
        if (totalProductos > 0) {
            sb.append(totalProductos).append(totalProductos == 1 ? " producto" : " productos");
        }
        if (totalCanales > 0) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(totalCanales).append(totalCanales == 1 ? " canal" : " canales")
                    .append(" (").append(productosEnCanales).append(" productos recalculados)");
        }
        if (sb.length() == 0) sb.append("sin cambios");
        if (errores > 0) {
            sb.append(", ").append(errores).append(errores == 1 ? " error" : " errores");
        }
        return sb.toString();
    }

    private String construirMensajeFinal(int totalProductos, int totalCanales, int exitosos, int errores) {
        StringBuilder sb = new StringBuilder();
        if (totalProductos > 0) {
            sb.append(totalProductos).append(totalProductos == 1 ? " producto" : " productos");
        }
        if (totalCanales > 0) {
            if (sb.length() > 0) sb.append(" y ");
            sb.append(totalCanales).append(totalCanales == 1 ? " canal" : " canales");
        }
        int total = totalProductos + totalCanales;
        if (errores == 0) {
            sb.append(total == 1 ? " recalculado" : " recalculados");
        } else {
            sb.append(": ").append(exitosos).append(" OK, ")
                    .append(errores).append(errores == 1 ? " error" : " errores");
        }
        return sb.toString();
    }
}

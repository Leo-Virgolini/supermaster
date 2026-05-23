package ar.com.leo.super_master_backend.dominio.common.service;

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
    private final EstadoProcesoMasivo tracker;

    public AplicadorPendientesService(RecalculoBulkExecutor bulkExecutor,
                                      ProcesoGlobalService procesoGlobal) {
        this.bulkExecutor = bulkExecutor;
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
     * obtuvo true. Libera locks al final.
     *
     * <p>Si el bulk executor crashea (excepción fatal), los flags de obsolescencia
     * de los items NO procesados quedan {@code obsoleto = TRUE} naturalmente — el
     * próximo Apply los va a ver. No hace falta restore explícito.
     */
    @Async
    public void ejecutarPlanScopedAsync(PlanRecalculo plan) {
        int totalProductos = plan.productos().size();
        int totalCanales = plan.canales().size();
        int total = totalProductos + totalCanales;
        long t0 = System.currentTimeMillis();
        log.info("Aplicando plan scoped: productos={}, canales={}", totalProductos, totalCanales);

        try {
            tracker.actualizar(total, 0, 0, 0, "Iniciando...");

            int[] resultado = bulkExecutor.ejecutar(plan, (procesados, ok, errs, descripcion) ->
                    tracker.actualizar(total, procesados, ok, errs, descripcion));
            int exitosos = resultado[0];
            int errores = resultado[1];

            String mensaje = construirMensajeFinal(totalProductos, totalCanales, exitosos, errores);
            tracker.completar(total, exitosos, errores, mensaje);
            log.info("Plan scoped aplicado en {}ms — {}", System.currentTimeMillis() - t0, mensaje);
        } catch (Exception e) {
            log.error("Error fatal en plan scoped — items no procesados quedan marcados para reintento", e);
            tracker.completarConError(e.getMessage());
        } finally {
            // Idempotente: si completar/completarConError ya liberaron, esto es no-op.
            tracker.liberar();
        }
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

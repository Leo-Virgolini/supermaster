package ar.com.leo.super_master_backend.dominio.common.service;

import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService.PlanRecalculo;
import ar.com.leo.super_master_backend.dominio.producto.calculo.service.RecalculoPrecioFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Ejecuta el plan de recálculo pendiente en background. Se separa del endpoint para
 * que la request HTTP retorne inmediatamente y el usuario no vea un spinner colgado
 * mientras el motor procesa N productos/canales.
 *
 * Single-flight: solo un plan a la vez. Si llega un segundo Apply mientras hay uno
 * corriendo, el endpoint devuelve 409 y el usuario espera.
 */
@Slf4j
@Service
public class AplicadorPendientesService {

    private static final String PROCESO_ID = "recalculo-pendiente-scoped";
    private static final String PROCESO_DESC = "Aplicando recálculo pendiente";

    private final RecalculoPrecioFacade recalculoFacade;
    private final RecalculoPendienteService recalculoPendienteService;
    private final EstadoProcesoMasivo tracker;

    public AplicadorPendientesService(RecalculoPrecioFacade recalculoFacade,
                                      ProcesoGlobalService procesoGlobal,
                                      RecalculoPendienteService recalculoPendienteService) {
        this.recalculoFacade = recalculoFacade;
        this.recalculoPendienteService = recalculoPendienteService;
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
     * obtuvo true. Libera locks al final y reporta contadores al frontend vía SSE
     * para que el toast de cierre sea informativo (X productos OK, Y errores).
     *
     * Nota: itera secuencialmente, no en paralelo, para evitar conflictos de transacción
     * en BD si dos productos comparten canales/cuotas.
     */
    @Async
    public void ejecutarPlanScopedAsync(PlanRecalculo plan) {
        int totalProductos = plan.productos().size();
        int totalCanales = plan.canales().size();
        int total = totalProductos + totalCanales;
        long t0 = System.currentTimeMillis();
        log.info("Aplicando plan scoped: productos={}, canales={}", totalProductos, totalCanales);

        int procesados = 0;
        int exitosos = 0;
        int errores = 0;
        try {
            tracker.actualizar(total, 0, 0, 0, "Iniciando...");

            for (Integer productoId : plan.productos()) {
                try {
                    recalculoFacade.recalcularProductoEnTodosLosCanales(productoId);
                    exitosos++;
                } catch (Exception e) {
                    errores++;
                    log.warn("Error recalculando producto {}: {}", productoId, e.getMessage());
                }
                procesados++;
                tracker.actualizar(total, procesados, exitosos, errores,
                        "Producto " + procesados + "/" + totalProductos);
            }

            // recalcularCanalCompletoAsync es @Async → fire-and-forget. El lock global del
            // grupo BD se libera apenas se despachan, mientras los canales siguen procesándose
            // en sus propios threads (registran "recalculo-canal" para verse en el header).
            for (Integer canalId : plan.canales()) {
                try {
                    recalculoFacade.recalcularCanalCompletoAsync(canalId);
                    exitosos++;
                } catch (Exception e) {
                    errores++;
                    log.warn("Error despachando canal {}: {}", canalId, e.getMessage());
                }
                procesados++;
                int canalesProcesados = procesados - totalProductos;
                tracker.actualizar(total, procesados, exitosos, errores,
                        "Canal " + canalesProcesados + "/" + totalCanales);
            }

            String mensaje = construirMensajeFinal(totalProductos, totalCanales, exitosos, errores);
            tracker.completar(total, exitosos, errores, mensaje);
            log.info("Plan scoped aplicado en {}ms — {}", System.currentTimeMillis() - t0, mensaje);
        } catch (Exception e) {
            // Falla fatal antes/durante la iteración (ej. NPE, OOM): los pendientes ya se
            // limpiaron en el controller antes de invocarnos, así que sin este restore se
            // perderían silenciosamente. Re-marcamos el plan completo para reintento.
            log.error("Error fatal en plan scoped — restaurando {} productos y {} canales para reintento",
                    totalProductos, totalCanales, e);
            try {
                recalculoPendienteService.marcarProductos("Reintento por error en recálculo", plan.productos());
                recalculoPendienteService.marcarCanales("Reintento por error en recálculo", plan.canales());
            } catch (Exception restoreEx) {
                log.error("No se pudo restaurar el plan scoped tras fallo", restoreEx);
            }
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

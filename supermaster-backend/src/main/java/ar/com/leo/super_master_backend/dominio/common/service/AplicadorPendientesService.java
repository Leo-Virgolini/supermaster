package ar.com.leo.super_master_backend.dominio.common.service;

import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService.PlanRecalculo;
import ar.com.leo.super_master_backend.dominio.producto.calculo.service.RecalculoPrecioFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

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
@RequiredArgsConstructor
public class AplicadorPendientesService {

    private static final String PROCESO_ID = "recalculo-pendiente-scoped";
    private static final String PROCESO_DESC = "Aplicando recálculo pendiente";

    private final RecalculoPrecioFacade recalculoFacade;
    private final ProcesoGlobalService procesoGlobal;

    private final AtomicBoolean enEjecucion = new AtomicBoolean(false);

    /**
     * Intenta adquirir el lock local Y el lock global del grupo BD.
     * Devuelve true si lo consiguió, false si:
     *  - ya hay otro plan scoped corriendo (lock local), o
     *  - hay otro proceso del grupo BD corriendo (recálculo masivo, importación DUX, etc.).
     */
    public boolean intentarAdquirir() {
        if (!enEjecucion.compareAndSet(false, true)) return false;
        if (!procesoGlobal.adquirir(PROCESO_ID, PROCESO_DESC)) {
            // Lock global rechazó (otro proceso BD activo) → soltamos el local también.
            enEjecucion.set(false);
            return false;
        }
        return true;
    }

    public boolean estaEjecutando() {
        return enEjecucion.get();
    }

    /**
     * Ejecuta el plan en background. Asume que ya se llamó intentarAdquirir() y se
     * obtuvo true. Libera ambos locks al final.
     *
     * Nota: itera secuencialmente, no en paralelo, para evitar conflictos de transacción
     * en BD si dos productos comparten canales/cuotas.
     *
     * Mientras corre, el proceso aparece en el badge del header global vía
     * {@link ProcesoGlobalService}, así el usuario ve "Aplicando recálculo pendiente"
     * en lugar de no ver nada.
     */
    @Async
    public void ejecutarPlanScopedAsync(PlanRecalculo plan) {
        long t0 = System.currentTimeMillis();
        log.info("Aplicando plan scoped: productos={}, canales={}",
                plan.productos().size(), plan.canales().size());
        try {
            for (Integer productoId : plan.productos()) {
                try {
                    recalculoFacade.recalcularProductoEnTodosLosCanales(productoId);
                } catch (Exception e) {
                    log.warn("Error recalculando producto {}: {}", productoId, e.getMessage());
                }
            }
            // recalcularCanalCompletoAsync es @Async → fire-and-forget. El lock global del
            // grupo BD se libera apenas se despachan, mientras los canales siguen procesándose
            // en sus propios threads (registran "recalculo-canal" para verse en el header).
            for (Integer canalId : plan.canales()) {
                try {
                    recalculoFacade.recalcularCanalCompletoAsync(canalId);
                } catch (Exception e) {
                    log.warn("Error despachando canal {}: {}", canalId, e.getMessage());
                }
            }
            log.info("Plan scoped aplicado en {}ms", System.currentTimeMillis() - t0);
        } finally {
            procesoGlobal.liberar(PROCESO_ID);
            enEjecucion.set(false);
        }
    }
}

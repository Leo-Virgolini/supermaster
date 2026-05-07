package ar.com.leo.super_master_backend.dominio.producto.calculo.service;

import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.common.service.EstadoProcesoMasivo;
import ar.com.leo.super_master_backend.dominio.common.service.ProcesoGlobalService;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Facade que centraliza el recálculo automático de precios. La superficie pública
 * son cuatro métodos según el scope:
 * <ul>
 *   <li>{@link #recalcularProductoEnTodosLosCanales} — un producto en todos los canales (sync).</li>
 *   <li>{@link #recalcularProductosDelMla} — todos los productos de un MLA en todos sus canales (sync).</li>
 *   <li>{@link #recalcularCanalCompletoAsync} — un canal entero contra todo el catálogo (async, registrado como proceso).</li>
 *   <li>{@link #ejecutarRecalculoAsync} — runner async genérico para recálculos post-commit.</li>
 * </ul>
 *
 * <p>El recálculo de canal usa el grupo de exclusión BD: solo uno corre a la vez.
 * El tracker comparte ese único slot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecalculoPrecioFacade {

    private static final String PROCESO_RECALCULO_CANAL = "recalculo-canal";

    private final CalculoPrecioService calculoPrecioService;
    private final ProductoRepository productoRepository;
    private final CanalRepository canalRepository;
    private final ProcesoGlobalService procesoGlobal;

    private EstadoProcesoMasivo trackerCanal;

    @PostConstruct
    void initTracker() {
        this.trackerCanal = new EstadoProcesoMasivo(PROCESO_RECALCULO_CANAL,
                "Recalculando canal", procesoGlobal);
    }

    /**
     * Recalcula los precios de un producto en TODOS los canales activos.
     * Iterar todos los canales (no solo los que ya tienen precio) es necesario porque
     * un cambio de atributo (ej: tag, costo, iva, margen) puede hacer que el producto
     * pase a aplicar a canales de los que antes estaba excluido por canal_regla.
     * El Impl decide por combinación: crear / actualizar / borrar.
     */
    @Transactional
    public void recalcularProductoEnTodosLosCanales(Integer productoId) {
        log.info("Recalculando producto {} en todos los canales", productoId);
        canalRepository.findAll().forEach(canal ->
                calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(productoId, canal.getId()));
    }

    /**
     * Recalcula los precios de todos los productos asociados a un MLA, en todos
     * sus canales. Usado cuando cambia el precioEnvio o la comisión del MLA.
     */
    @Transactional
    public void recalcularProductosDelMla(Integer mlaId) {
        log.info("Recalculando productos del MLA {} en todos los canales", mlaId);
        productoRepository.findByMlaId(mlaId)
                .forEach(producto -> recalcularProductoEnTodosLosCanales(producto.getId()));
    }

    /**
     * Recalcula un canal iterando TODOS los productos del catálogo (no solo los que
     * ya tienen precio en el canal). Necesario cuando cambia canal_regla, conceptos
     * del canal, cuotas, regla de descuento, canalBase, etc.: una regla puede hacer
     * que productos nunca presentes en el canal ahora apliquen.
     * Async — se registra como proceso global para que el frontend lo vea en el badge.
     */
    @Async
    public void recalcularCanalCompletoAsync(Integer canalId) {
        String nombreCanal = canalRepository.findById(canalId)
                .map(c -> c.getNombre())
                .orElse("ID " + canalId);

        if (!trackerCanal.adquirir("Recalculando todos los productos del canal " + nombreCanal)) {
            log.warn("Recálculo completo del canal {} pospuesto: hay un proceso activo", canalId);
            return;
        }

        try {
            List<Integer> productoIds = productoRepository.findAll().stream()
                    .map(p -> p.getId())
                    .toList();

            int total = productoIds.size();
            String mensaje = "Recalculando " + nombreCanal + " (catálogo completo)";
            log.info("Recálculo completo de canal {} ({}): {} productos", canalId, nombreCanal, total);
            trackerCanal.actualizar(total, 0, 0, 0, mensaje);

            int ok = 0, errores = 0;
            for (int i = 0; i < productoIds.size(); i++) {
                Integer productoId = productoIds.get(i);
                try {
                    calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(productoId, canalId);
                    ok++;
                } catch (Exception e) {
                    errores++;
                    log.warn("Error recalculando producto {} en canal {}: {}", productoId, canalId, e.getMessage());
                }
                int procesados = i + 1;
                if (procesados == total || procesados % 50 == 0) {
                    trackerCanal.actualizar(total, procesados, ok, errores, mensaje);
                }
            }

            log.info("Recálculo completo de canal {} finalizado: {} ok, {} errores", canalId, ok, errores);
            trackerCanal.completar(total, ok, errores,
                    errores > 0
                            ? String.format("%s: %d productos OK, %d errores", nombreCanal, ok, errores)
                            : String.format("%s: %d productos recalculados", nombreCanal, ok));
        } catch (Exception e) {
            log.error("Error en recálculo completo de canal {}: {}", canalId, e.getMessage(), e);
            trackerCanal.completarConError(e.getMessage());
        } finally {
            trackerCanal.liberar();
        }
    }

    /**
     * Ejecuta un Runnable async dentro del lock global de "recalculo-canal".
     * Usado por servicios que necesitan recálculo post-commit sin bloquear la
     * transacción que disparó el cambio. No expone progreso al SSE — el badge
     * solo muestra la descripción + tiempo transcurrido.
     */
    @Async
    public void ejecutarRecalculoAsync(String descripcion, Runnable tarea) {
        if (!procesoGlobal.adquirir(PROCESO_RECALCULO_CANAL, descripcion)) {
            log.warn("Recálculo pospuesto ({}): hay un proceso activo que lo bloquea", descripcion);
            return;
        }
        try {
            log.info("Recálculo async iniciado: {}", descripcion);
            tarea.run();
            log.info("Recálculo async completado: {}", descripcion);
        } catch (Exception e) {
            log.error("Error en recálculo async ({}): {}", descripcion, e.getMessage(), e);
        } finally {
            procesoGlobal.liberar(PROCESO_RECALCULO_CANAL);
        }
    }
}

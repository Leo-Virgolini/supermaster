package ar.com.leo.super_master_backend.dominio.producto.calculo.service;

import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.common.service.ProcesoGlobalService;
import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Facade que centraliza el recálculo automático de precios. La superficie pública
 * son cuatro métodos según el scope:
 * <ul>
 *   <li>{@link #recalcularProductoEnTodosLosCanales} — un producto en todos los canales (sync).</li>
 *   <li>{@link #recalcularProductosDelMla} — todos los productos de un MLA en todos sus canales (sync).</li>
 *   <li>{@link #recalcularCanalCompletoInline} — un canal entero contra todo el catálogo, síncrono,
 *       para correr dentro de un proceso que ya tiene el lock del grupo BD.</li>
 *   <li>{@link #ejecutarRecalculoAsync} — runner async genérico para recálculos post-commit.</li>
 * </ul>
 *
 * <p>El recálculo de canal usa el grupo de exclusión BD: solo uno corre a la vez.
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
    private final RecalculoPendienteService recalculoPendienteService;

    @PersistenceContext
    private EntityManager entityManager;

    // Self-proxy para invocar el camino dependiente @Transactional desde el decididor
    // (que NO es transaccional): la auto-invocación directa no pasaría por el proxy de Spring.
    @Lazy
    @Autowired
    private RecalculoPrecioFacade self;

    /** Cada cuántos productos se reporta progreso / se hace flush+clear en el recálculo de canal. */
    private static final int FLUSH_BATCH = 50;

    /**
     * Recalcula los precios de un producto en TODOS los canales activos.
     * Iterar todos los canales (no solo los que ya tienen precio) es necesario porque
     * un cambio de atributo (ej: tag, costo, iva, margen) puede hacer que el producto
     * pase a aplicar a canales de los que antes estaba excluido por canal_regla.
     * El Impl decide por combinación: crear / actualizar / borrar.
     *
     * <p>Tras éxito desmarca el flag {@code obsoleto} de las filas del producto vía
     * {@link RecalculoPendienteService#desmarcarProductoCompletado}, que además emite
     * broadcast SSE para que el banner del frontend se actualice en tiempo real.
     */
    @Transactional
    public void recalcularProductoEnTodosLosCanales(Integer productoId) {
        log.info("Recalculando producto {} en todos los canales", productoId);
        var canales = canalRepository.findAll();
        // Caché de contexto canal-constante: el producto se recalcula en N canales y cada
        // recalcularYGuardarPrecioCanalTodasCuotas consulta varias veces la config del canal
        // (reglas, conceptos, cuotas). Con el caché, cada canal se carga una vez. Es anidable:
        // si un loop externo (fase de productos) ya lo activó, se reutiliza entre productos.
        calculoPrecioService.iniciarCacheContextoCanal(null);
        try {
            canales.forEach(canal ->
                    calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(productoId, canal.getId()));
        } finally {
            calculoPrecioService.limpiarCacheContextoCanal();
        }
        recalculoPendienteService.desmarcarProductoCompletado(productoId);
        log.info("Producto {} recalculado en {} canales", productoId, canales.size());
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

    /** Overload sin callback (callers que no necesitan reportar progreso). */
    public int recalcularCanalCompletoInline(Integer canalId) {
        return recalcularCanalCompletoInline(canalId, null);
    }

    /**
     * Recalcula un canal completo (todo el catálogo) y al terminar lo desmarca. Decididor
     * NO transaccional: enruta según el tipo de canal y deja que cada camino maneje su
     * propia transaccionalidad (clave para no acumular el L1 ni retener locks largos).
     *
     * <ul>
     *   <li><b>Base</b> (sin canalBase): {@code recalcularCanalCompletoBatch} — sin TX
     *       envolvente, precarga + {@code saveAll} por lote (cada lote commitea). El cálculo
     *       no recursa, así que no hay queries en el loop. Rápido.</li>
     *   <li><b>Dependiente</b> (con canalBase): camino producto-por-producto en su PROPIA
     *       transacción ({@code REQUIRES_NEW} vía self), porque el cálculo recursa al canal
     *       base y necesita una sesión viva (el caché de contexto pre-inicializa el proxy
     *       {@code canalBase}, que sin TX lanzaría LazyInitializationException).</li>
     * </ul>
     *
     * @return cantidad de productos recalculados sin error
     */
    public int recalcularCanalCompletoInline(Integer canalId, CalculoPrecioService.AvanceCanalCallback onAvance) {
        Canal canal = canalRepository.findById(canalId).orElse(null);
        // getCanalBase() != null NO inicializa el proxy lazy (solo chequea la FK), así que es
        // seguro sin transacción.
        boolean esDependiente = canal != null && canal.getCanalBase() != null;
        int ok = esDependiente
                ? self.recalcularCanalDependienteEnTx(canalId, onAvance)
                : calculoPrecioService.recalcularCanalCompletoBatch(canalId, onAvance);
        recalculoPendienteService.desmarcarCanalCompletado(canalId);
        return ok;
    }

    /**
     * Camino producto-por-producto para canales DEPENDIENTES, en su PROPIA transacción.
     * Activa el caché de contexto canal-constante y hace flush+clear periódico para no
     * acumular el catálogo en el L1 (el cálculo del dependiente recursa al canal base y
     * dispara queries; sin el clear el auto-flush degradaría a O(n²)).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recalcularCanalDependienteEnTx(Integer canalId, CalculoPrecioService.AvanceCanalCallback onAvance) {
        calculoPrecioService.iniciarCacheContextoCanal(canalId);
        try {
            List<Integer> productoIds = productoRepository.findAllIds();
            int total = productoIds.size();
            log.info("Recálculo inline de canal {}: {} productos", canalId, total);
            int ok = 0, errores = 0, desdeFlush = 0;
            for (int i = 0; i < total; i++) {
                Integer productoId = productoIds.get(i);
                try {
                    calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(productoId, canalId);
                    ok++;
                } catch (Exception e) {
                    errores++;
                    log.warn("Error recalculando producto {} en canal {}: {}", productoId, canalId, e.getMessage());
                }
                if (++desdeFlush >= FLUSH_BATCH) {
                    entityManager.flush();
                    entityManager.clear();
                    desdeFlush = 0;
                    if (onAvance != null) onAvance.onAvance(i + 1, total, ok, errores);
                    log.info("Recálculo inline de canal {}: {}/{} productos", canalId, i + 1, total);
                }
            }
            entityManager.flush();
            if (onAvance != null) onAvance.onAvance(total, total, ok, errores);
            log.info("Recálculo inline de canal {} finalizado: {} ok, {} errores", canalId, ok, errores);
            return ok;
        } finally {
            calculoPrecioService.limpiarCacheContextoCanal();
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

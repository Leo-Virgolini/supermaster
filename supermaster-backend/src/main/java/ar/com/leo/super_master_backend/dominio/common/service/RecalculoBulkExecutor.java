package ar.com.leo.super_master_backend.dominio.common.service;

import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService.PlanRecalculo;
import ar.com.leo.super_master_backend.dominio.producto.calculo.service.RecalculoPrecioFacade;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Ejecuta un plan de recálculo dentro de una sola transacción "larga" para que el
 * Hibernate L1 cache absorba las queries repetidas que hace el motor de cálculo
 * (producto, canal, conceptos, reglas, cuotas).
 *
 * <p>Beneficio concreto: el método interno del motor hace varios {@code findById(...)}.
 * Cuando se ejecuta dentro de una transacción que ya tiene esos entities cargados,
 * resuelven por L1 cache en lugar de pegarle a BD. Para 100 productos × 10 canales,
 * pasa de ~6000 SELECTs a ~3 SELECTs de bulk load + N saves batched.
 *
 * <p>Separado del {@link AplicadorPendientesService} porque {@code @Transactional}
 * necesita pasar por el proxy de Spring; no se aplica con auto-invocación dentro de
 * la misma clase.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecalculoBulkExecutor {

    private final RecalculoPrecioFacade recalculoFacade;
    private final ProductoRepository productoRepository;
    private final CanalRepository canalRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Callback que reporta progreso de la iteración al caller (típicamente para
     * actualizar el tracker del {@link EstadoProcesoMasivo}).
     */
    @FunctionalInterface
    public interface ProgresoCallback {
        void onProgreso(int procesados, int exitosos, int errores, String descripcion);
    }

    /**
     * Itera el plan con pre-load en bulk y flush periódico para no saturar memoria.
     *
     * @return [exitosos, errores]
     */
    @Transactional
    public int[] ejecutar(PlanRecalculo plan, ProgresoCallback onProgreso) {
        int totalProductos = plan.productos().size();
        int totalCanales = plan.canales().size();

        // Pre-load del contexto en L1 cache.
        if (!plan.productos().isEmpty()) {
            productoRepository.findAllById(plan.productos());
        }
        if (!plan.canales().isEmpty()) {
            canalRepository.findAllById(plan.canales());
        }
        // Si hay productos a recalcular, todos los canales serán consultados por el motor:
        // pre-cargarlos una sola vez en L1 evita N llamadas a canalRepository.findAll().
        if (!plan.productos().isEmpty()) {
            canalRepository.findAll();
        }

        int procesados = 0;
        int exitosos = 0;
        int errores = 0;
        int desdeUltimoFlush = 0;

        for (Integer productoId : plan.productos()) {
            try {
                // El facade desmarca obsoleto al final del recálculo exitoso.
                // Si tira, el flag queda TRUE → reintento natural en el próximo Apply.
                recalculoFacade.recalcularProductoEnTodosLosCanales(productoId);
                exitosos++;
            } catch (Exception e) {
                errores++;
                log.warn("Error recalculando producto {}: {}", productoId, e.getMessage());
            }
            procesados++;
            desdeUltimoFlush++;
            onProgreso.onProgreso(procesados, exitosos, errores, "Producto " + procesados + "/" + totalProductos);
            if (desdeUltimoFlush >= 50) {
                entityManager.flush();
                entityManager.clear();
                desdeUltimoFlush = 0;
            }
        }

        // Flush previo al cambio de scope a canales.
        if (desdeUltimoFlush > 0) {
            entityManager.flush();
            entityManager.clear();
        }

        // Canales: despachados fire-and-forget vía recalcularCanalCompletoAsync.
        // Cada uno corre en su propio thread con su propia transacción — no participa
        // del L1 cache de la transacción actual, pero tampoco lo necesita: el job
        // de canal completo ya carga sus entidades de una vez.
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
            onProgreso.onProgreso(procesados, exitosos, errores, "Canal " + canalesProcesados + "/" + totalCanales);
        }

        return new int[]{exitosos, errores};
    }
}

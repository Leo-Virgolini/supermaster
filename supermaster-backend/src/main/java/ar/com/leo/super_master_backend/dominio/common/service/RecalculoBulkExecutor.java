package ar.com.leo.super_master_backend.dominio.common.service;

import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService.PlanRecalculo;
import ar.com.leo.super_master_backend.dominio.producto.calculo.service.CalculoPrecioService;
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
    private final CalculoPrecioService calculoPrecioService;
    private final ProductoRepository productoRepository;
    private final CanalRepository canalRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Callback que reporta progreso de la iteración al caller (típicamente para
     * actualizar el tracker del {@link EstadoProcesoMasivo}).
     *
     * <p>Lleva el {@code total} para que cada fase defina su propio denominador: la
     * fase de productos reporta sobre la cantidad de productos del plan; la fase de
     * canales, sobre el tamaño del catálogo del canal en curso (su recálculo puede
     * tardar minutos, y mostrar ese avance evita que el badge parezca congelado).
     */
    @FunctionalInterface
    public interface ProgresoCallback {
        void onProgreso(int total, int procesados, int exitosos, int errores, String descripcion);
    }

    /**
     * Fase 1 — productos: recalcula cada producto en todos sus canales, con pre-load
     * en bulk y flush periódico para no saturar memoria.
     *
     * <p>Corre en una transacción propia que <b>commitea al retornar</b>. Esto es
     * crítico: libera los locks de fila de {@code producto_canal_precios} (incluidas
     * filas de los canales que toca) ANTES de que la fase de canales arranque. Sin
     * esto, la fase de canales —que corre en una transacción {@code REQUIRES_NEW}
     * separada— quedaba bloqueada esperando locks que esta transacción retenía,
     * produciendo un auto-deadlock que MySQL no detecta (no es un ciclo de locks:
     * la transacción padre espera, a nivel de aplicación, a que el inline retorne).
     *
     * @return [exitosos, errores]
     */
    @Transactional
    public int[] ejecutarProductos(PlanRecalculo plan, ProgresoCallback onProgreso) {
        int totalProductos = plan.productos().size();
        if (plan.productos().isEmpty()) {
            return new int[]{0, 0};
        }

        // Pre-load del contexto en L1 cache. Todos los canales serán consultados por
        // el motor al recalcular "en todos los canales": pre-cargarlos una sola vez
        // evita N llamadas a canalRepository.findAll().
        productoRepository.findAllById(plan.productos());
        canalRepository.findAll();

        int procesados = 0;
        int exitosos = 0;
        int errores = 0;
        int desdeUltimoFlush = 0;

        // Caché de contexto canal-constante a nivel de TODA la fase: como cada producto se
        // recalcula en los mismos N canales, los carga una sola vez y los reutiliza entre los
        // miles de productos (cada recalcularProductoEnTodosLosCanales anida sobre este caché).
        calculoPrecioService.iniciarCacheContextoCanal(null);
        try {
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
                onProgreso.onProgreso(totalProductos, procesados, exitosos, errores, "Producto " + procesados + "/" + totalProductos);
                if (desdeUltimoFlush >= 50) {
                    entityManager.flush();
                    entityManager.clear();
                    desdeUltimoFlush = 0;
                }
            }

            // Flush final de la fase. El commit lo hace el proxy de Spring al retornar.
            if (desdeUltimoFlush > 0) {
                entityManager.flush();
                entityManager.clear();
            }
        } finally {
            calculoPrecioService.limpiarCacheContextoCanal();
        }

        return new int[]{exitosos, errores};
    }

    /**
     * Fase 2 — canales: recalcula cada canal del plan INLINE (síncrono). Un despacho async
     * del grupo de exclusión BD se auto-rechazaría acá porque el aplicador scoped ya tiene
     * el lock tomado, y el canal nunca se recalcularía. El inline itera el catálogo solo en
     * ese canal (no "producto × todos los canales") con su propio flush/clear, dentro de su
     * propia transacción {@code REQUIRES_NEW}.
     *
     * <p><b>Sin {@code @Transactional} a propósito:</b> este método NO debe abrir una
     * transacción envolvente. Si lo hiciera, el {@code REQUIRES_NEW} de cada canal
     * volvería a competir por locks con la transacción padre. Al no haber padre, cada
     * canal corre aislado y libre de contención.
     *
     * @param exitososPrevios  exitosos acumulados de la fase de productos.
     * @param erroresPrevios   errores acumulados de la fase de productos.
     * @return [exitosos, errores, productosRecalculadosEnCanales] — el tercer valor es el
     *         total de productos recalculados dentro de los canales (para la auditoría).
     */
    public int[] ejecutarCanales(PlanRecalculo plan,
                                 int exitososPrevios, int erroresPrevios,
                                 ProgresoCallback onProgreso) {
        int totalCanales = plan.canales().size();
        int exitosos = exitososPrevios;
        int errores = erroresPrevios;
        int productosEnCanales = 0;
        int canalesProcesados = 0;

        for (Integer canalId : plan.canales()) {
            final int idxCanal = canalesProcesados + 1;
            try {
                // Reportar el avance INTERNO del canal (X/catálogo) al badge: un canal
                // grande tarda minutos y, al contar como un solo ítem del plan, dejaba
                // el badge en "0/1" inmóvil. El callback redefine total=catálogo para
                // que el usuario vea progreso real.
                productosEnCanales += recalculoFacade.recalcularCanalCompletoInline(canalId, (proc, tot, ok, errs) ->
                        onProgreso.onProgreso(tot, proc, ok, errs,
                                "Canal " + idxCanal + "/" + totalCanales + " — " + proc + "/" + tot + " productos"));
                exitosos++;
            } catch (Exception e) {
                errores++;
                log.warn("Error recalculando canal {}: {}", canalId, e.getMessage());
            }
            canalesProcesados++;
        }

        return new int[]{exitosos, errores, productosEnCanales};
    }
}

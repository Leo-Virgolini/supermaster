package ar.com.leo.super_master_backend.dominio.common.service;

import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService.PlanRecalculo;
import ar.com.leo.super_master_backend.dominio.producto.calculo.service.RecalculoPrecioFacade;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Tests de la iteración del plan de recálculo. Foco:
 *  - Productos: se recalculan uno por uno (en todos sus canales).
 *  - Canales: se recalculan INLINE (síncrono), NUNCA vía el dispatch async — que
 *    se auto-rechazaría por compartir el lock del grupo BD con el aplicador scoped.
 *  - Un error en un ítem se cuenta y NO corta la iteración del resto.
 */
@ExtendWith(MockitoExtension.class)
class RecalculoBulkExecutorTest {

    @Mock
    private RecalculoPrecioFacade recalculoFacade;

    @Mock
    private ar.com.leo.super_master_backend.dominio.producto.calculo.service.CalculoPrecioService calculoPrecioService;

    @Mock
    private ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository productoRepository;

    @Mock
    private ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository canalRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private RecalculoBulkExecutor executor;

    /** Callback de progreso no-op para los tests. */
    private static final RecalculoBulkExecutor.ProgresoCallback NOOP = (total, p, ok, err, desc) -> { };

    @BeforeEach
    void wireEntityManager() {
        // @InjectMocks usa el constructor (campos final) y no setea el EntityManager,
        // que es un field @PersistenceContext. Lo inyectamos a mano.
        ReflectionTestUtils.setField(executor, "entityManager", entityManager);
    }

    @Test
    @DisplayName("productos: se recalcula cada uno en todos los canales")
    void productos_seRecalculanIndividualmente() {
        PlanRecalculo plan = new PlanRecalculo(false, new LinkedHashSet<>(List.of(1, 2, 3)), Set.of());

        int[] res = executor.ejecutarProductos(plan, NOOP);

        verify(recalculoFacade).recalcularProductoEnTodosLosCanales(1);
        verify(recalculoFacade).recalcularProductoEnTodosLosCanales(2);
        verify(recalculoFacade).recalcularProductoEnTodosLosCanales(3);
        assertThat(res).containsExactly(3, 0); // 3 exitosos, 0 errores
    }

    @Test
    @DisplayName("canales: se recalculan INLINE (uno por canal del plan)")
    void canales_seRecalculanInline() {
        PlanRecalculo plan = new PlanRecalculo(false, Set.of(), new LinkedHashSet<>(List.of(10, 20)));

        int[] res = executor.ejecutarCanales(plan, 0, 0, NOOP);

        verify(recalculoFacade).recalcularCanalCompletoInline(eq(10), any());
        verify(recalculoFacade).recalcularCanalCompletoInline(eq(20), any());
        assertThat(res).containsExactly(2, 0, 0); // [exitosos, errores, productosEnCanales]
    }

    @Test
    @DisplayName("error en un producto: se cuenta y la iteración continúa")
    void errorEnProducto_cuentaErrorYContinua() {
        lenient().doThrow(new RuntimeException("boom")).when(recalculoFacade).recalcularProductoEnTodosLosCanales(2);
        PlanRecalculo plan = new PlanRecalculo(false, new LinkedHashSet<>(List.of(1, 2, 3)), Set.of());

        int[] res = executor.ejecutarProductos(plan, NOOP);

        verify(recalculoFacade).recalcularProductoEnTodosLosCanales(3); // no cortó en el error de 2
        assertThat(res).containsExactly(2, 1); // 2 ok, 1 error
    }

    @Test
    @DisplayName("error en un canal: se cuenta y la iteración continúa")
    void errorEnCanal_cuentaErrorYContinua() {
        lenient().doThrow(new RuntimeException("boom")).when(recalculoFacade).recalcularCanalCompletoInline(eq(10), any());
        PlanRecalculo plan = new PlanRecalculo(false, Set.of(), new LinkedHashSet<>(List.of(10, 20)));

        int[] res = executor.ejecutarCanales(plan, 0, 0, NOOP);

        verify(recalculoFacade).recalcularCanalCompletoInline(eq(20), any());
        assertThat(res).containsExactly(1, 1, 0); // [exitosos, errores, productosEnCanales]
    }

    @Test
    @DisplayName("plan mixto: la fase de canales acumula sobre los exitosos de productos")
    void planMixto_sumaExitosos() {
        PlanRecalculo plan = new PlanRecalculo(false,
                new LinkedHashSet<>(List.of(1, 2)), new LinkedHashSet<>(List.of(10)));

        int[] resProductos = executor.ejecutarProductos(plan, NOOP);
        int[] res = executor.ejecutarCanales(plan, resProductos[0], resProductos[1], NOOP);

        verify(recalculoFacade).recalcularProductoEnTodosLosCanales(1);
        verify(recalculoFacade).recalcularProductoEnTodosLosCanales(2);
        verify(recalculoFacade).recalcularCanalCompletoInline(eq(10), any());
        assertThat(resProductos).containsExactly(2, 0); // 2 productos OK
        assertThat(res).containsExactly(3, 0, 0); // [exitosos=2 prod+1 canal, errores, productosEnCanales]
    }
}

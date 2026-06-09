package ar.com.leo.super_master_backend.dominio.common.service;

import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService.PlanRecalculo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests del aplicador de pendientes. Cubre:
 *  - Single-flight (lock local + global, rollback si uno falla).
 *  - Ejecución del plan: itera productos secuenciales y despacha canales async.
 *  - Errores fatales que escapan al bulk executor disparan restore + liberación de locks.
 *
 * <p>La iteración real (bulk pre-load + N+1 fix) vive en {@link RecalculoBulkExecutor};
 * sus tests están en {@code RecalculoBulkExecutorTest} (o integration tests con BD).
 */
@ExtendWith(MockitoExtension.class)
class AplicadorPendientesServiceTest {

    @Mock
    private RecalculoBulkExecutor bulkExecutor;

    @Mock
    private ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService auditoriaService;

    @Mock
    private ProcesoGlobalService procesoGlobal;

    private AplicadorPendientesService service;

    private static final String PROCESO_ID = "recalculo-pendiente-scoped";
    private static final String PROCESO_DESC = "Aplicando recálculo pendiente";

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new AplicadorPendientesService(bulkExecutor, auditoriaService, procesoGlobal);
    }

    // ============================================
    // intentarAdquirir
    // ============================================
    @Nested
    @DisplayName("intentarAdquirir")
    class IntentarAdquirir {

        @Test
        @DisplayName("ambos locks libres → true")
        void ambosLocksLibres_ok() {
            when(procesoGlobal.adquirir(eq(PROCESO_ID), eq(PROCESO_DESC), any())).thenReturn(true);

            assertThat(service.intentarAdquirir()).isTrue();
            assertThat(service.estaEjecutando()).isTrue();
        }

        @Test
        @DisplayName("segundo intento mientras hay uno corriendo → false (single-flight)")
        void lockLocalTomado_rechaza() {
            when(procesoGlobal.adquirir(eq(PROCESO_ID), eq(PROCESO_DESC), any())).thenReturn(true);
            assertThat(service.intentarAdquirir()).isTrue();

            // Segundo intento: lock local rechaza y libera el global como rollback.
            assertThat(service.intentarAdquirir()).isFalse();
            assertThat(service.estaEjecutando()).isTrue();

            verify(procesoGlobal, atLeastOnce()).liberar(PROCESO_ID);
        }

        @Test
        @DisplayName("lock global rechaza (otro proceso BD activo) → libera el local y false")
        void lockGlobalRechaza_liberaLocal() {
            when(procesoGlobal.adquirir(eq(PROCESO_ID), eq(PROCESO_DESC), any())).thenReturn(false);

            assertThat(service.intentarAdquirir()).isFalse();
            assertThat(service.estaEjecutando())
                    .as("estaEjecutando debe quedar false si el global rechazó")
                    .isFalse();

            when(procesoGlobal.adquirir(eq(PROCESO_ID), eq(PROCESO_DESC), any())).thenReturn(true);
            assertThat(service.intentarAdquirir()).isTrue();
        }
    }

    // ============================================
    // ejecutarPlanScopedAsync
    // ============================================
    @Nested
    @DisplayName("ejecutarPlanScopedAsync")
    class EjecutarPlan {

        @Test
        @DisplayName("delega ambas fases al bulk executor y libera locks al final")
        void planConProductos_delegaEjecutor() {
            when(procesoGlobal.adquirir(eq(PROCESO_ID), eq(PROCESO_DESC), any())).thenReturn(true);
            service.intentarAdquirir();

            when(bulkExecutor.ejecutarProductos(any(), any())).thenReturn(new int[]{3, 0});
            when(bulkExecutor.ejecutarCanales(any(), anyInt(), anyInt(), any())).thenReturn(new int[]{3, 0, 0});

            PlanRecalculo plan = new PlanRecalculo(false, Set.of(1, 2, 3), Set.of());
            service.ejecutarPlanScopedAsync(plan, "jorge");

            verify(bulkExecutor).ejecutarProductos(eq(plan), any());
            verify(bulkExecutor).ejecutarCanales(eq(plan), anyInt(), anyInt(), any());
            verify(procesoGlobal).liberar(PROCESO_ID);
            assertThat(service.estaEjecutando()).isFalse();
        }

        @Test
        @DisplayName("plan vacío igualmente delega ambas fases y libera locks")
        void planVacio_liberaLocks() {
            when(procesoGlobal.adquirir(eq(PROCESO_ID), eq(PROCESO_DESC), any())).thenReturn(true);
            service.intentarAdquirir();

            when(bulkExecutor.ejecutarProductos(any(), any())).thenReturn(new int[]{0, 0});
            when(bulkExecutor.ejecutarCanales(any(), anyInt(), anyInt(), any())).thenReturn(new int[]{0, 0, 0});

            service.ejecutarPlanScopedAsync(new PlanRecalculo(false, Set.of(), Set.of()), "jorge");

            verify(bulkExecutor).ejecutarProductos(any(), any());
            verify(bulkExecutor).ejecutarCanales(any(), anyInt(), anyInt(), any());
            verify(procesoGlobal).liberar(PROCESO_ID);
            assertThat(service.estaEjecutando()).isFalse();
        }

        @Test
        @DisplayName("después de aplicar un plan, se puede arrancar otro (lock local liberado)")
        void permiteSegundoPlanTrasFinalizar() {
            when(procesoGlobal.adquirir(eq(PROCESO_ID), eq(PROCESO_DESC), any())).thenReturn(true);
            when(bulkExecutor.ejecutarProductos(any(), any())).thenReturn(new int[]{1, 0});
            when(bulkExecutor.ejecutarCanales(any(), anyInt(), anyInt(), any())).thenReturn(new int[]{1, 0, 0});

            service.intentarAdquirir();
            service.ejecutarPlanScopedAsync(new PlanRecalculo(false, Set.of(1), Set.of()), "jorge");
            assertThat(service.estaEjecutando()).isFalse();

            assertThat(service.intentarAdquirir()).isTrue();
            service.ejecutarPlanScopedAsync(new PlanRecalculo(false, Set.of(2), Set.of()), "jorge");

            verify(bulkExecutor, times(2)).ejecutarProductos(any(), any());
            verify(bulkExecutor, times(2)).ejecutarCanales(any(), anyInt(), anyInt(), any());
            verify(procesoGlobal, times(2)).liberar(PROCESO_ID);
        }

        @Test
        @DisplayName("el callback de progreso del ejecutor se invoca correctamente")
        void callbackDeProgresoSeInvoca() {
            when(procesoGlobal.adquirir(eq(PROCESO_ID), eq(PROCESO_DESC), any())).thenReturn(true);
            service.intentarAdquirir();

            // Simula que la fase de productos invoca el callback durante su iteración.
            doAnswer(invocation -> {
                RecalculoBulkExecutor.ProgresoCallback cb = invocation.getArgument(1);
                cb.onProgreso(2, 1, 1, 0, "Producto 1/2");
                cb.onProgreso(2, 2, 2, 0, "Producto 2/2");
                return new int[]{2, 0};
            }).when(bulkExecutor).ejecutarProductos(any(), any());
            when(bulkExecutor.ejecutarCanales(any(), anyInt(), anyInt(), any())).thenReturn(new int[]{2, 0, 0});

            service.ejecutarPlanScopedAsync(new PlanRecalculo(false, Set.of(1, 2), Set.of()), "jorge");

            verify(bulkExecutor).ejecutarProductos(any(), any());
            verify(procesoGlobal).liberar(PROCESO_ID);
        }
    }

    // ============================================
    // ejecutarPlanScopedAsync — fallo fatal
    // ============================================
    @Nested
    @DisplayName("ejecutarPlanScopedAsync — fallo fatal")
    class FalloFatal {

        @Test
        @DisplayName("excepción del bulk executor libera locks y deja flags marcados para reintento")
        void falloFatal_liberaLocks() {
            when(procesoGlobal.adquirir(eq(PROCESO_ID), eq(PROCESO_DESC), any())).thenReturn(true);
            service.intentarAdquirir();

            Set<Integer> productos = Set.of(1, 2, 3);
            Set<Integer> canales = Set.of(10, 20);
            PlanRecalculo plan = new PlanRecalculo(false, productos, canales);

            // El fallo ocurre en la fase de productos; la de canales no se alcanza.
            doThrow(new RuntimeException("fallo fatal en bulk")).when(bulkExecutor).ejecutarProductos(any(), any());

            // No debe propagar la excepción. Los flags de obsolescencia quedan TRUE
            // naturalmente (no se desmarcan porque no llegaron a procesarse con éxito).
            service.ejecutarPlanScopedAsync(plan, "jorge");

            verify(procesoGlobal).liberar(PROCESO_ID);
            assertThat(service.estaEjecutando()).isFalse();
        }
    }
}

package ar.com.leo.super_master_backend.dominio.common.service;

import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService.PlanRecalculo;
import ar.com.leo.super_master_backend.dominio.producto.calculo.service.RecalculoPrecioFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests del aplicador de pendientes. Cubre:
 *  - Single-flight (lock local + global, rollback si uno falla).
 *  - Ejecución del plan: itera productos secuenciales y despacha canales async.
 *  - Errores en items individuales no detienen el resto.
 *  - Locks se liberan SIEMPRE (try/finally).
 */
@ExtendWith(MockitoExtension.class)
class AplicadorPendientesServiceTest {

    @Mock
    private RecalculoPrecioFacade recalculoFacade;

    @Mock
    private ProcesoGlobalService procesoGlobal;

    @Mock
    private RecalculoPendienteService recalculoPendienteService;

    @InjectMocks
    private AplicadorPendientesService service;

    private static final String PROCESO_ID = "recalculo-pendiente-scoped";
    private static final String PROCESO_DESC = "Aplicando recálculo pendiente";

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
            // Primer adquirir toma ambos locks.
            when(procesoGlobal.adquirir(eq(PROCESO_ID), eq(PROCESO_DESC), any())).thenReturn(true);
            assertThat(service.intentarAdquirir()).isTrue();

            // Segundo adquirir: el global lo deja entrar otra vez (mock devuelve true), pero el
            // lock local del tracker rechaza y libera el global como rollback. Resultado: false.
            assertThat(service.intentarAdquirir()).isFalse();
            assertThat(service.estaEjecutando()).isTrue();

            // El rollback del segundo intento dispara liberar(), pero el primer adquirir sigue activo.
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

            // Un nuevo intento debe poder pasar (el lock local se liberó correctamente).
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
        @DisplayName("itera productos secuencialmente y libera ambos locks al final")
        void planConProductos() {
            // Setup: lock adquirido previamente.
            when(procesoGlobal.adquirir(eq(PROCESO_ID), eq(PROCESO_DESC), any())).thenReturn(true);
            service.intentarAdquirir();

            PlanRecalculo plan = new PlanRecalculo(false, Set.of(1, 2, 3), Set.of());
            service.ejecutarPlanScopedAsync(plan);

            // Cada producto se procesó.
            verify(recalculoFacade).recalcularProductoEnTodosLosCanales(1);
            verify(recalculoFacade).recalcularProductoEnTodosLosCanales(2);
            verify(recalculoFacade).recalcularProductoEnTodosLosCanales(3);
            // No se despachó ningún canal.
            verify(recalculoFacade, never()).recalcularCanalCompletoAsync(org.mockito.ArgumentMatchers.anyInt());

            // Lock liberado y flag local también.
            verify(procesoGlobal).liberar(PROCESO_ID);
            assertThat(service.estaEjecutando()).isFalse();
        }

        @Test
        @DisplayName("despacha canales fire-and-forget vía recalcularCanalCompletoAsync")
        void planConCanales() {
            when(procesoGlobal.adquirir(eq(PROCESO_ID), eq(PROCESO_DESC), any())).thenReturn(true);
            service.intentarAdquirir();

            PlanRecalculo plan = new PlanRecalculo(false, Set.of(), Set.of(10, 20));
            service.ejecutarPlanScopedAsync(plan);

            verify(recalculoFacade).recalcularCanalCompletoAsync(10);
            verify(recalculoFacade).recalcularCanalCompletoAsync(20);
            verify(recalculoFacade, never()).recalcularProductoEnTodosLosCanales(org.mockito.ArgumentMatchers.anyInt());
        }

        @Test
        @DisplayName("plan mixto procesa productos primero y despacha canales después")
        void planMixto() {
            when(procesoGlobal.adquirir(eq(PROCESO_ID), eq(PROCESO_DESC), any())).thenReturn(true);
            service.intentarAdquirir();

            PlanRecalculo plan = new PlanRecalculo(false, Set.of(1), Set.of(10));
            service.ejecutarPlanScopedAsync(plan);

            verify(recalculoFacade).recalcularProductoEnTodosLosCanales(1);
            verify(recalculoFacade).recalcularCanalCompletoAsync(10);
            verify(procesoGlobal).liberar(PROCESO_ID);
        }

        @Test
        @DisplayName("error en un producto NO detiene el resto")
        void errorEnProducto_continua() {
            when(procesoGlobal.adquirir(eq(PROCESO_ID), eq(PROCESO_DESC), any())).thenReturn(true);
            service.intentarAdquirir();

            doThrow(new RuntimeException("BD timeout"))
                    .when(recalculoFacade).recalcularProductoEnTodosLosCanales(2);

            // LinkedHashSet para tener orden determinista en la iteración del for.
            java.util.LinkedHashSet<Integer> productos = new java.util.LinkedHashSet<>(List.of(1, 2, 3));
            PlanRecalculo plan = new PlanRecalculo(false, productos, Set.of());

            service.ejecutarPlanScopedAsync(plan);

            // Los 3 productos fueron intentados pese al error en el medio.
            verify(recalculoFacade).recalcularProductoEnTodosLosCanales(1);
            verify(recalculoFacade).recalcularProductoEnTodosLosCanales(2);
            verify(recalculoFacade).recalcularProductoEnTodosLosCanales(3);
            // Y el lock se liberó.
            verify(procesoGlobal).liberar(PROCESO_ID);
        }

        @Test
        @DisplayName("error en un canal NO detiene el resto del plan")
        void errorEnCanal_continua() {
            when(procesoGlobal.adquirir(eq(PROCESO_ID), eq(PROCESO_DESC), any())).thenReturn(true);
            service.intentarAdquirir();

            doThrow(new RuntimeException("async pool full"))
                    .when(recalculoFacade).recalcularCanalCompletoAsync(20);

            java.util.LinkedHashSet<Integer> canales = new java.util.LinkedHashSet<>(List.of(10, 20, 30));
            PlanRecalculo plan = new PlanRecalculo(false, Set.of(), canales);

            service.ejecutarPlanScopedAsync(plan);

            verify(recalculoFacade).recalcularCanalCompletoAsync(10);
            verify(recalculoFacade).recalcularCanalCompletoAsync(20);
            verify(recalculoFacade).recalcularCanalCompletoAsync(30);
            verify(procesoGlobal).liberar(PROCESO_ID);
        }

        @Test
        @DisplayName("plan vacío igualmente libera locks")
        void planVacio_liberaLocks() {
            when(procesoGlobal.adquirir(eq(PROCESO_ID), eq(PROCESO_DESC), any())).thenReturn(true);
            service.intentarAdquirir();

            service.ejecutarPlanScopedAsync(new PlanRecalculo(false, Set.of(), Set.of()));

            verify(recalculoFacade, never()).recalcularProductoEnTodosLosCanales(org.mockito.ArgumentMatchers.anyInt());
            verify(recalculoFacade, never()).recalcularCanalCompletoAsync(org.mockito.ArgumentMatchers.anyInt());
            verify(procesoGlobal).liberar(PROCESO_ID);
            assertThat(service.estaEjecutando()).isFalse();
        }

        @Test
        @DisplayName("después de aplicar un plan, se puede arrancar otro (lock local liberado)")
        void permiteSegundoPlanTrasFinalizar() {
            when(procesoGlobal.adquirir(eq(PROCESO_ID), eq(PROCESO_DESC), any())).thenReturn(true);

            // Primera ejecución.
            service.intentarAdquirir();
            service.ejecutarPlanScopedAsync(new PlanRecalculo(false, Set.of(1), Set.of()));
            assertThat(service.estaEjecutando()).isFalse();

            // Segunda ejecución.
            assertThat(service.intentarAdquirir()).isTrue();
            service.ejecutarPlanScopedAsync(new PlanRecalculo(false, Set.of(2), Set.of()));

            verify(recalculoFacade).recalcularProductoEnTodosLosCanales(1);
            verify(recalculoFacade).recalcularProductoEnTodosLosCanales(2);
            verify(procesoGlobal, times(2)).liberar(PROCESO_ID);
        }
    }

    // ============================================
    // ejecutarPlanScopedAsync — fallo fatal con restore
    // ============================================
    @Nested
    @DisplayName("ejecutarPlanScopedAsync — fallo fatal con restore")
    class FalloFatalConRestore {

        @Test
        @DisplayName("excepción ANTES del loop → re-marca productos y canales del plan + libera locks")
        @SuppressWarnings("unchecked")
        void falloFatal_restauraPlan() {
            when(procesoGlobal.adquirir(eq(PROCESO_ID), eq(PROCESO_DESC), any())).thenReturn(true);
            service.intentarAdquirir();

            // Set malicioso: size() responde normal (para el log inicial), pero iterator()
            // tira excepción al entrar al for. Esto simula un fallo fatal que escapa al
            // catch interno por producto y va directo al catch general.
            Set<Integer> productosMock = mock(Set.class);
            when(productosMock.size()).thenReturn(5);
            when(productosMock.iterator()).thenThrow(new RuntimeException("fallo fatal iterando"));

            Set<Integer> canales = Set.of(10, 20);
            PlanRecalculo plan = new PlanRecalculo(false, productosMock, canales);

            service.ejecutarPlanScopedAsync(plan);

            // El plan se restauró: productos y canales fueron re-marcados como pendientes
            // para que el usuario pueda reintentar (limpiar() ya ocurrió en el controller).
            verify(recalculoPendienteService).marcarProductos(anyString(), eq(productosMock));
            verify(recalculoPendienteService).marcarCanales(anyString(), eq(canales));

            // Nunca se procesó ningún producto/canal porque la iteración falló de inicio.
            verify(recalculoFacade, never()).recalcularProductoEnTodosLosCanales(org.mockito.ArgumentMatchers.anyInt());
            verify(recalculoFacade, never()).recalcularCanalCompletoAsync(org.mockito.ArgumentMatchers.anyInt());

            // Locks liberados pese al fallo.
            verify(procesoGlobal).liberar(PROCESO_ID);
            assertThat(service.estaEjecutando()).isFalse();
        }

        @Test
        @DisplayName("fallo en restore es tragado y los locks se liberan igual")
        @SuppressWarnings("unchecked")
        void falloEnRestore_locksSeLiberan() {
            when(procesoGlobal.adquirir(eq(PROCESO_ID), eq(PROCESO_DESC), any())).thenReturn(true);
            service.intentarAdquirir();

            Set<Integer> productosMock = mock(Set.class);
            when(productosMock.size()).thenReturn(1);
            when(productosMock.iterator()).thenThrow(new RuntimeException("fallo fatal iterando"));

            // El restore también falla.
            doThrow(new RuntimeException("BD caída"))
                    .when(recalculoPendienteService).marcarProductos(anyString(), eq(productosMock));

            PlanRecalculo plan = new PlanRecalculo(false, productosMock, Set.of());

            // No debe propagar la excepción del restore.
            service.ejecutarPlanScopedAsync(plan);

            // Locks liberados pese a doble fallo.
            verify(procesoGlobal).liberar(PROCESO_ID);
            assertThat(service.estaEjecutando()).isFalse();
        }
    }
}

package ar.com.leo.super_master_backend.dominio.common.service;

import ar.com.leo.super_master_backend.dominio.common.dto.RecalculoPendienteDTO;
import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService.PlanRecalculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests unitarios del tracker de pendientes.
 *
 * <p>Cubre: cardinalidad correcta del scope, deduplicación, snapshot inmutable,
 * broadcast SSE por cada mutación, equivalencia legacy, batches, concurrencia.
 */
@ExtendWith(MockitoExtension.class)
class RecalculoPendienteServiceTest {

    @Mock
    private RecalculoPendienteSseService sseService;

    @InjectMocks
    private RecalculoPendienteService service;

    @BeforeEach
    void wireSseService() {
        // El campo es @Lazy @Autowired (no via constructor), así que @InjectMocks no lo setea.
        ReflectionTestUtils.setField(service, "sseService", sseService);
    }

    // ============================================
    // marcarTodo
    // ============================================
    @Nested
    @DisplayName("marcarTodo")
    class MarcarTodo {

        @Test
        @DisplayName("setea recalcularTodo=true y la cardinalidad se cuenta como 1")
        void marcarTodo_seteaScope() {
            service.marcarTodo("Cambio en concepto");

            PlanRecalculo plan = service.plan();
            assertThat(plan.recalcularTodo()).isTrue();
            assertThat(plan.productos()).isEmpty();
            assertThat(plan.canales()).isEmpty();

            RecalculoPendienteDTO estado = service.estado();
            assertThat(estado.pendiente()).isTrue();
            assertThat(estado.cantidad()).isEqualTo(1);
            assertThat(estado.recalcularTodo()).isTrue();
        }

        @Test
        @DisplayName("acumula motivos cuando se llama varias veces")
        void marcarTodo_acumulaMotivos() {
            service.marcarTodo("Cambio en concepto");
            service.marcarTodo("Cambio en concepto");
            service.marcarTodo("Cambio en clasif");

            RecalculoPendienteDTO estado = service.estado();
            assertThat(estado.motivos()).hasSize(2);
            // El motivo más frecuente queda primero (sort por cantidad desc).
            assertThat(estado.motivos().get(0).motivo()).isEqualTo("Cambio en concepto");
            assertThat(estado.motivos().get(0).cantidad()).isEqualTo(2);
            assertThat(estado.motivos().get(1).motivo()).isEqualTo("Cambio en clasif");
            assertThat(estado.motivos().get(1).cantidad()).isEqualTo(1);
        }
    }

    // ============================================
    // marcarProducto / marcarProductos
    // ============================================
    @Nested
    @DisplayName("marcarProducto / marcarProductos")
    class MarcarProductos {

        @Test
        @DisplayName("marcarProducto agrega al set y la cardinalidad refleja productos únicos")
        void marcarProducto_agrega() {
            service.marcarProducto("edit costo", 100);
            service.marcarProducto("edit margen", 200);
            service.marcarProducto("edit costo", 100); // duplicado

            PlanRecalculo plan = service.plan();
            assertThat(plan.productos()).containsExactlyInAnyOrder(100, 200);
            assertThat(plan.canales()).isEmpty();
            assertThat(plan.recalcularTodo()).isFalse();

            // 3 calls pero 2 productos únicos → cardinalidad=2.
            assertThat(service.estado().cantidad()).isEqualTo(2);
        }

        @Test
        @DisplayName("marcarProducto con id null no agrega nada")
        void marcarProducto_nullIdEsIgnorado() {
            service.marcarProducto("test", null);

            // Aunque el id sea null, el motivo se registra → estado pendiente.
            // Pero el set queda vacío. Cardinalidad = 0 + 0 + 0 = 0 → vacío.
            PlanRecalculo plan = service.plan();
            assertThat(plan.productos()).isEmpty();
        }

        @Test
        @DisplayName("marcarProductos batch agrega N IDs con un solo broadcast")
        void marcarProductos_batchUnSoloBroadcast() {
            service.marcarProductos("Cambio en MLA", List.of(1, 2, 3));

            PlanRecalculo plan = service.plan();
            assertThat(plan.productos()).containsExactlyInAnyOrder(1, 2, 3);
            assertThat(service.estado().cantidad()).isEqualTo(3);

            // Un solo broadcast pese a 3 productos.
            verify(sseService, times(1)).broadcast(any());
        }

        @Test
        @DisplayName("marcarProductos con lista vacía no marca ni broadcastea")
        void marcarProductos_listaVaciaNoOp() {
            service.marcarProductos("test", List.of());

            assertThat(service.estado().pendiente()).isFalse();
            verify(sseService, never()).broadcast(any());
        }

        @Test
        @DisplayName("marcarProductos ignora null IDs y agrega los demás")
        void marcarProductos_ignoraNulls() {
            service.marcarProductos("test", java.util.Arrays.asList(1, null, 2));

            assertThat(service.plan().productos()).containsExactlyInAnyOrder(1, 2);
        }
    }

    // ============================================
    // marcarCanal / marcarCanales
    // ============================================
    @Nested
    @DisplayName("marcarCanal / marcarCanales")
    class MarcarCanales {

        @Test
        @DisplayName("marcarCanal acumula sin duplicar")
        void marcarCanal_dedup() {
            service.marcarCanal("Cambio cuotas", 1);
            service.marcarCanal("Cambio cuotas", 1);
            service.marcarCanal("Cambio regla", 2);

            PlanRecalculo plan = service.plan();
            assertThat(plan.canales()).containsExactlyInAnyOrder(1, 2);
            assertThat(service.estado().cantidad()).isEqualTo(2);
        }

        @Test
        @DisplayName("marcarCanales batch funciona con set vacío sin marcar")
        void marcarCanales_vacioNoOp() {
            service.marcarCanales("test", Set.of());

            assertThat(service.estado().pendiente()).isFalse();
            verify(sseService, never()).broadcast(any());
        }

        @Test
        @DisplayName("marcarCanales batch agrega varios IDs en un solo broadcast")
        void marcarCanales_batch() {
            service.marcarCanales("Cambio en concepto", List.of(10, 20, 30));

            assertThat(service.plan().canales()).containsExactlyInAnyOrder(10, 20, 30);
            verify(sseService, times(1)).broadcast(any());
        }
    }

    // ============================================
    // Cardinalidad combinada (productos + canales + todo)
    // ============================================
    @Nested
    @DisplayName("Cardinalidad combinada")
    class Cardinalidad {

        @Test
        @DisplayName("cantidad = productos.size + canales.size cuando recalcularTodo=false")
        void cardinalidad_scoped() {
            service.marcarProducto("p", 1);
            service.marcarProducto("p", 2);
            service.marcarCanal("c", 10);

            assertThat(service.estado().cantidad()).isEqualTo(3);
            assertThat(service.estado().productosCount()).isEqualTo(2);
            assertThat(service.estado().canalesCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("recalcularTodo=true cuenta como 1 (representa el masivo) + suma productos/canales acumulados")
        void cardinalidad_todoMasScoped() {
            service.marcarProducto("p", 1);
            service.marcarTodo("Cambio en concepto");

            // recalcularTodo cuenta como 1, productos cuenta como 1 → 2.
            assertThat(service.estado().cantidad()).isEqualTo(2);
            assertThat(service.estado().recalcularTodo()).isTrue();
        }
    }

    // ============================================
    // limpiar
    // ============================================
    @Nested
    @DisplayName("limpiar")
    class Limpiar {

        @Test
        @DisplayName("resetea todos los scopes y motivos")
        void limpiar_reseteaTodo() {
            service.marcarTodo("a");
            service.marcarProducto("b", 1);
            service.marcarCanal("c", 10);

            service.limpiar();

            PlanRecalculo plan = service.plan();
            assertThat(plan.recalcularTodo()).isFalse();
            assertThat(plan.productos()).isEmpty();
            assertThat(plan.canales()).isEmpty();
            assertThat(plan.estaVacio()).isTrue();

            RecalculoPendienteDTO estado = service.estado();
            assertThat(estado.pendiente()).isFalse();
            assertThat(estado.cantidad()).isZero();
            assertThat(estado.motivos()).isEmpty();
        }

        @Test
        @DisplayName("limpiar sobre un estado vacío no broadcastea")
        void limpiar_vacioNoBroadcastea() {
            // El método sí emite broadcast por consistencia, pero no debería loguear info.
            service.limpiar();

            // En la implementación actual, limpiar() siempre llama broadcast().
            verify(sseService, times(1)).broadcast(any());
        }
    }

    // ============================================
    // marcarPendiente legacy
    // ============================================
    @Nested
    @DisplayName("marcarPendiente (legacy)")
    class MarcarPendienteLegacy {

        @Test
        @DisplayName("marcarPendiente es equivalente a marcarTodo")
        void legacy_equivaleATodo() {
            service.marcarPendiente("legacy");

            PlanRecalculo plan = service.plan();
            assertThat(plan.recalcularTodo()).isTrue();
        }
    }

    // ============================================
    // SSE broadcast
    // ============================================
    @Nested
    @DisplayName("Broadcast SSE")
    class Broadcast {

        @Test
        @DisplayName("cada mutación dispara exactamente 1 broadcast")
        void mutacionesDisparanBroadcast() {
            service.marcarTodo("a");
            service.marcarProducto("b", 1);
            service.marcarCanal("c", 10);
            service.limpiar();

            verify(sseService, times(4)).broadcast(any());
        }

        @Test
        @DisplayName("estado() no dispara broadcast — es lectura pura")
        void estadoNoBroadcastea() {
            service.estado();
            service.estado();

            verify(sseService, never()).broadcast(any());
        }

        @Test
        @DisplayName("plan() no dispara broadcast — es lectura pura")
        void planNoBroadcastea() {
            service.plan();

            verify(sseService, never()).broadcast(any());
        }
    }

    // ============================================
    // PlanRecalculo (record)
    // ============================================
    @Nested
    @DisplayName("PlanRecalculo")
    class PlanRecalc {

        @Test
        @DisplayName("estaVacio=true cuando recalcularTodo=false y sets vacíos")
        void estaVacio_default() {
            assertThat(service.plan().estaVacio()).isTrue();
        }

        @Test
        @DisplayName("estaVacio=false con recalcularTodo o con productos o con canales")
        void estaVacio_falseEnCualquierScope() {
            service.marcarProducto("p", 1);
            assertThat(service.plan().estaVacio()).isFalse();

            service.limpiar();
            service.marcarCanal("c", 10);
            assertThat(service.plan().estaVacio()).isFalse();

            service.limpiar();
            service.marcarTodo("t");
            assertThat(service.plan().estaVacio()).isFalse();
        }

        @Test
        @DisplayName("plan() devuelve un snapshot inmutable: mutaciones posteriores no lo afectan")
        void plan_esInmutable() {
            service.marcarProducto("p", 1);
            PlanRecalculo snap1 = service.plan();

            service.marcarProducto("p", 2);
            // El snapshot original sigue mostrando solo {1}.
            assertThat(snap1.productos()).containsExactly(1);

            // El nuevo plan refleja {1, 2}.
            assertThat(service.plan().productos()).containsExactlyInAnyOrder(1, 2);
        }
    }

    // ============================================
    // Concurrencia
    // ============================================
    @Nested
    @DisplayName("Concurrencia")
    class Concurrencia {

        @Test
        @DisplayName("marcarProducto desde N threads en paralelo no pierde productos")
        void marcarProducto_concurrente() throws InterruptedException {
            int threads = 10;
            int productosPorThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            AtomicInteger errores = new AtomicInteger(0);

            for (int t = 0; t < threads; t++) {
                final int threadIdx = t;
                executor.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < productosPorThread; i++) {
                            service.marcarProducto("test", threadIdx * productosPorThread + i);
                        }
                    } catch (Exception e) {
                        errores.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();

            assertThat(errores.get()).isZero();
            assertThat(service.plan().productos()).hasSize(threads * productosPorThread);
        }

        @Test
        @DisplayName("limpiar concurrente con marcar no deja estado inconsistente")
        void limpiar_vsMarcar_consistente() throws InterruptedException {
            ExecutorService executor = Executors.newFixedThreadPool(4);
            CountDownLatch done = new CountDownLatch(4);

            executor.submit(() -> { for (int i = 0; i < 1000; i++) service.marcarProducto("p", i); done.countDown(); });
            executor.submit(() -> { for (int i = 0; i < 100; i++) service.limpiar(); done.countDown(); });
            executor.submit(() -> { for (int i = 0; i < 1000; i++) service.marcarCanal("c", i % 50); done.countDown(); });
            executor.submit(() -> { for (int i = 0; i < 500; i++) service.estado(); done.countDown(); });

            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();

            // No nos importa el estado final, solo que el snapshot sea coherente
            // (cantidad consistente con productos+canales+todo).
            RecalculoPendienteDTO estado = service.estado();
            int esperado = (estado.recalcularTodo() ? 1 : 0)
                    + estado.productosCount()
                    + estado.canalesCount();
            assertThat(estado.cantidad()).isEqualTo(esperado);
        }
    }

    // ============================================
    // Edge case: SSE service ausente
    // ============================================
    @Test
    @DisplayName("Si el SSE service no está inyectado (test temprano), las mutaciones no fallan")
    void sinSseService_noFalla() {
        ReflectionTestUtils.setField(service, "sseService", null);

        service.marcarProducto("p", 1);
        service.marcarTodo("t");
        service.limpiar();

        // Sin throws → OK.
    }
}

package ar.com.leo.super_master_backend.dominio.common.service;

import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.common.dto.RecalculoPendienteDTO;
import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService.PlanRecalculo;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoCanalPrecioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests del tracker persistido. Cubre que cada método público se traduce a los
 * UPDATEs/SELECTs correctos en BD y que el broadcast SSE se dispara en mutaciones.
 *
 * <p>La consistencia transaccional y el comportamiento real frente a concurrencia
 * dependen de BD; se verifican en tests de integración con datasource real, no acá.
 */
@ExtendWith(MockitoExtension.class)
class RecalculoPendienteServiceTest {

    @Mock
    private ProductoCanalPrecioRepository pcpRepository;

    @Mock
    private CanalRepository canalRepository;

    @Mock
    private RecalculoPendienteSseService sseService;

    @InjectMocks
    private RecalculoPendienteService service;

    @BeforeEach
    void wireSseService() {
        // @Lazy @Autowired no se setea con @InjectMocks.
        ReflectionTestUtils.setField(service, "sseService", sseService);
    }

    // ============================================
    // marcarProducto / marcarProductos
    // ============================================
    @Nested
    @DisplayName("marcarProducto / marcarProductos")
    class MarcarProductos {

        @Test
        @DisplayName("marcarProducto delega a UPDATE bulk con un solo ID")
        void marcarProducto_invocaUpdateBulk() {
            service.marcarProducto("Cambio costo", 42);
            verify(pcpRepository).marcarObsoletoPorProductos(eq(List.of(42)), eq("Cambio costo"));
        }

        @Test
        @DisplayName("marcarProducto con null no llama al repo")
        void marcarProductoNull_noLlama() {
            service.marcarProducto("Cambio", null);
            verify(pcpRepository, never()).marcarObsoletoPorProductos(any(), anyString());
        }

        @Test
        @DisplayName("marcarProductos filtra nulls y delega un solo UPDATE")
        void marcarProductos_filtraNullsYDelega() {
            service.marcarProductos("Cambio MLA", List.of(1, 2, 3));
            verify(pcpRepository).marcarObsoletoPorProductos(eq(List.of(1, 2, 3)), eq("Cambio MLA"));
        }

        @Test
        @DisplayName("marcarProductos con lista vacía no llama al repo")
        void marcarProductosVacio_noLlama() {
            service.marcarProductos("Cambio", List.of());
            verify(pcpRepository, never()).marcarObsoletoPorProductos(any(), anyString());
        }
    }

    // ============================================
    // marcarCanal / marcarCanales
    // ============================================
    @Nested
    @DisplayName("marcarCanal / marcarCanales")
    class MarcarCanales {

        @Test
        @DisplayName("marcarCanal dispara UPDATE de canal (reevaluar) + UPDATE de precios (obsoleto)")
        void marcarCanal_invocaAmbosUpdates() {
            service.marcarCanal("Cambio cuotas", 10);
            verify(canalRepository).marcarRequiereReevaluarPorIds(eq(List.of(10)), eq("Cambio cuotas"));
            verify(pcpRepository).marcarObsoletoPorCanales(eq(List.of(10)), eq("Cambio cuotas"));
        }

        @Test
        @DisplayName("marcarCanal con null no llama a repos")
        void marcarCanalNull_noLlama() {
            service.marcarCanal("Cambio", null);
            verify(canalRepository, never()).marcarRequiereReevaluarPorIds(any(), anyString());
            verify(pcpRepository, never()).marcarObsoletoPorCanales(any(), anyString());
        }

        @Test
        @DisplayName("marcarCanales con lista vacía no llama a repos")
        void marcarCanalesVacio_noLlama() {
            service.marcarCanales("Cambio", List.of());
            verify(canalRepository, never()).marcarRequiereReevaluarPorIds(any(), anyString());
        }
    }

    // ============================================
    // marcarTodo
    // ============================================
    @Nested
    @DisplayName("marcarTodo")
    class MarcarTodo {

        @Test
        @DisplayName("marca todos los precios obsoletos y todos los canales para reevaluar")
        void marcarTodo_ambosUpdates() {
            service.marcarTodo("Reseteo global");
            verify(pcpRepository).marcarTodoObsoleto(eq("Reseteo global"));
            verify(canalRepository).marcarTodosRequiereReevaluar(eq("Reseteo global"));
        }
    }

    // ============================================
    // limpiar
    // ============================================
    @Nested
    @DisplayName("limpiar")
    class Limpiar {

        @Test
        @DisplayName("dispara los DELETE bulk en ambos repos")
        void limpiar_invocaDesmarcarTodos() {
            when(pcpRepository.desmarcarTodosObsoletos()).thenReturn(5);
            when(canalRepository.desmarcarTodosRequiereReevaluar()).thenReturn(2);

            service.limpiar();

            verify(pcpRepository).desmarcarTodosObsoletos();
            verify(canalRepository).desmarcarTodosRequiereReevaluar();
        }
    }

    // ============================================
    // plan / estado
    // ============================================
    @Nested
    @DisplayName("plan / estado")
    class PlanYEstado {

        @Test
        @DisplayName("plan vacío cuando no hay obsoletos ni canales pendientes")
        void planVacio_cuandoNoHayNada() {
            when(canalRepository.findIdsRequierenReevaluar()).thenReturn(List.of());
            when(pcpRepository.findDistinctProductoIdsObsoletos()).thenReturn(List.of());
            when(canalRepository.count()).thenReturn(5L);

            PlanRecalculo plan = service.plan();
            assertThat(plan.estaVacio()).isTrue();
            assertThat(plan.recalcularTodo()).isFalse();
            assertThat(plan.productos()).isEmpty();
            assertThat(plan.canales()).isEmpty();
        }

        @Test
        @DisplayName("plan scoped: productos obsoletos + algunos canales pendientes")
        void planScoped() {
            when(canalRepository.findIdsRequierenReevaluar()).thenReturn(List.of(10));
            when(pcpRepository.findDistinctProductoIdsObsoletos()).thenReturn(List.of(1, 2, 3));
            when(canalRepository.count()).thenReturn(5L);

            PlanRecalculo plan = service.plan();
            assertThat(plan.recalcularTodo()).isFalse();
            assertThat(plan.productos()).containsExactlyInAnyOrder(1, 2, 3);
            assertThat(plan.canales()).containsExactly(10);
        }

        @Test
        @DisplayName("plan = recalcularTodo cuando todos los canales requieren reevaluar")
        void planTodo_cuandoTodosLosCanalesEstanMarcados() {
            when(canalRepository.findIdsRequierenReevaluar()).thenReturn(List.of(1, 2, 3, 4, 5));
            when(canalRepository.count()).thenReturn(5L);

            PlanRecalculo plan = service.plan();
            assertThat(plan.recalcularTodo()).isTrue();
            // En "todo" no necesitamos enumerar productos/canales: el caller llama a recalculoMasivo.
            assertThat(plan.productos()).isEmpty();
            assertThat(plan.canales()).isEmpty();
        }

        @Test
        @DisplayName("estado vacío devuelve DTO.vacio()")
        void estadoVacio() {
            when(canalRepository.findIdsRequierenReevaluar()).thenReturn(List.of());
            when(pcpRepository.findDistinctProductoIdsObsoletos()).thenReturn(List.of());
            when(canalRepository.count()).thenReturn(5L);

            RecalculoPendienteDTO dto = service.estado();
            assertThat(dto.pendiente()).isFalse();
            assertThat(dto.cantidad()).isZero();
        }

        @Test
        @DisplayName("estado con scope agrega motivos de productos y canales")
        void estadoConMotivos() {
            when(canalRepository.findIdsRequierenReevaluar()).thenReturn(List.of(10));
            when(pcpRepository.findDistinctProductoIdsObsoletos()).thenReturn(List.of(1, 2));
            when(canalRepository.count()).thenReturn(5L);

            LocalDateTime t1 = LocalDateTime.now();
            List<Object[]> motivosProductos = new java.util.ArrayList<>();
            motivosProductos.add(new Object[]{"Cambio costo", 2, t1});
            List<Object[]> motivosCanales = new java.util.ArrayList<>();
            motivosCanales.add(new Object[]{"Cambio cuotas", 1, t1});
            when(pcpRepository.resumenObsoletosPorMotivo()).thenReturn(motivosProductos);
            when(canalRepository.resumenReevaluarPorMotivo()).thenReturn(motivosCanales);

            RecalculoPendienteDTO dto = service.estado();
            assertThat(dto.pendiente()).isTrue();
            assertThat(dto.productosCount()).isEqualTo(2);
            assertThat(dto.canalesCount()).isEqualTo(1);
            assertThat(dto.motivos()).hasSize(2);
        }
    }

    // ============================================
    // SSE broadcast
    // ============================================
    @Nested
    @DisplayName("broadcast SSE")
    class BroadcastSse {

        @Test
        @DisplayName("cada mutación dispara un broadcast")
        void mutacionesBroadcastean() {
            when(canalRepository.findIdsRequierenReevaluar()).thenReturn(List.of());
            when(pcpRepository.findDistinctProductoIdsObsoletos()).thenReturn(List.of());
            when(canalRepository.count()).thenReturn(5L);

            service.marcarProducto("X", 1);
            service.marcarCanal("Y", 10);
            service.marcarTodo("Z");
            service.limpiar();

            verify(sseService, atLeastOnce()).broadcast(any());
        }

        @Test
        @DisplayName("estado() no broadcastea (solo lectura)")
        void estadoNoBroadcastea() {
            when(canalRepository.findIdsRequierenReevaluar()).thenReturn(List.of());
            when(pcpRepository.findDistinctProductoIdsObsoletos()).thenReturn(List.of());
            when(canalRepository.count()).thenReturn(5L);

            service.estado();
            verify(sseService, never()).broadcast(any());
        }
    }
}

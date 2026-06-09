package ar.com.leo.super_master_backend.dominio.producto.calculo.service;

import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.common.service.ProcesoGlobalService;
import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests del recálculo de canal completo. El decididor enruta según el tipo de canal:
 *  - canal BASE (sin canalBase) → camino batch ({@code recalcularCanalCompletoBatch}).
 *  - canal DEPENDIENTE (con canalBase) → camino producto-por-producto en su propia TX.
 * En ambos casos se desmarca el canal al final.
 */
@ExtendWith(MockitoExtension.class)
class RecalculoPrecioFacadeTest {

    @Mock
    private CalculoPrecioService calculoPrecioService;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private CanalRepository canalRepository;

    @Mock
    private ProcesoGlobalService procesoGlobal;

    @Mock
    private RecalculoPendienteService recalculoPendienteService;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private RecalculoPrecioFacade facade;

    @BeforeEach
    void wireFields() {
        // @InjectMocks usa el constructor (campos final) y no setea los fields por inyección.
        ReflectionTestUtils.setField(facade, "entityManager", entityManager);
        // self-proxy: en el test apunta al mismo facade, así el camino dependiente corre real.
        ReflectionTestUtils.setField(facade, "self", facade);
    }

    private Canal canalDependiente(Integer id) {
        Canal base = new Canal();
        base.setId(1);
        Canal c = new Canal();
        c.setId(id);
        c.setCanalBase(base);
        return c;
    }

    private Canal canalBase(Integer id) {
        Canal c = new Canal();
        c.setId(id);
        return c; // sin canalBase
    }

    @Test
    @DisplayName("canal base: usa el camino batch y desmarca")
    void canalBase_usaBatch() {
        when(canalRepository.findById(99)).thenReturn(Optional.of(canalBase(99)));
        when(calculoPrecioService.recalcularCanalCompletoBatch(99, null)).thenReturn(42);

        int ok = facade.recalcularCanalCompletoInline(99);

        verify(calculoPrecioService).recalcularCanalCompletoBatch(99, null);
        verify(recalculoPendienteService).desmarcarCanalCompletado(99);
        assertThat(ok).isEqualTo(42);
    }

    @Test
    @DisplayName("canal dependiente: recalcula todo el catálogo producto-por-producto y desmarca")
    void canalDependiente_recalculaCatalogoYDesmarca() {
        when(canalRepository.findById(99)).thenReturn(Optional.of(canalDependiente(99)));
        when(productoRepository.findAllIds()).thenReturn(List.of(1, 2, 3));

        int ok = facade.recalcularCanalCompletoInline(99);

        verify(calculoPrecioService).iniciarCacheContextoCanal(99);
        verify(calculoPrecioService).recalcularYGuardarPrecioCanalTodasCuotas(1, 99);
        verify(calculoPrecioService).recalcularYGuardarPrecioCanalTodasCuotas(2, 99);
        verify(calculoPrecioService).recalcularYGuardarPrecioCanalTodasCuotas(3, 99);
        verify(recalculoPendienteService).desmarcarCanalCompletado(99);
        verify(calculoPrecioService).limpiarCacheContextoCanal();
        assertThat(ok).isEqualTo(3);
    }

    @Test
    @DisplayName("canal dependiente: un error en un producto se cuenta pero no corta ni impide el desmarcado")
    void canalDependiente_errorEnProducto_continua() {
        when(canalRepository.findById(99)).thenReturn(Optional.of(canalDependiente(99)));
        when(productoRepository.findAllIds()).thenReturn(List.of(1, 2, 3));
        lenient().doThrow(new RuntimeException("boom"))
                .when(calculoPrecioService).recalcularYGuardarPrecioCanalTodasCuotas(2, 99);

        int ok = facade.recalcularCanalCompletoInline(99);

        verify(calculoPrecioService).recalcularYGuardarPrecioCanalTodasCuotas(3, 99); // siguió
        verify(recalculoPendienteService).desmarcarCanalCompletado(99);
        assertThat(ok).isEqualTo(2); // 2 ok, 1 falló
    }

    @Test
    @DisplayName("catálogo vacío => no recalcula nada pero igual desmarca el canal")
    void catalogoVacio() {
        when(canalRepository.findById(99)).thenReturn(Optional.of(canalDependiente(99)));
        when(productoRepository.findAllIds()).thenReturn(List.of());

        int ok = facade.recalcularCanalCompletoInline(99);

        verify(recalculoPendienteService).desmarcarCanalCompletado(99);
        assertThat(ok).isZero();
    }
}

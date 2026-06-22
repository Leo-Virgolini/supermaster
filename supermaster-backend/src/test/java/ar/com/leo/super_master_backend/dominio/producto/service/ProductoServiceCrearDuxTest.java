package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.apis.dux.model.Item;
import ar.com.leo.super_master_backend.apis.dux.service.DuxService;
import ar.com.leo.super_master_backend.dominio.common.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceCrearDuxTest {

    @Mock
    DuxService duxService;

    @InjectMocks
    ProductoServiceImpl service;

    @BeforeEach
    void setUp() {
        // @InjectMocks usa el constructor (campos final) y no inyecta campos @Lazy @Autowired.
        ReflectionTestUtils.setField(service, "duxService", duxService);
    }

    @Test
    void skuExisteEnDux_lanzaConflict() {
        when(duxService.obtenerProductoPorCodigo("1234567")).thenReturn(mock(Item.class));
        assertThatThrownBy(() -> service.verificarSkuLibreEnDux("1234567"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya existe en Dux");
    }

    @Test
    void noSePudoVerificar_lanzaConflict() {
        when(duxService.obtenerProductoPorCodigo("1234567")).thenThrow(new RuntimeException("Dux caído"));
        assertThatThrownBy(() -> service.verificarSkuLibreEnDux("1234567"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No se pudo verificar");
    }

    @Test
    void skuLibreEnDux_noLanza() {
        when(duxService.obtenerProductoPorCodigo("1234567")).thenReturn(null);
        assertThatCode(() -> service.verificarSkuLibreEnDux("1234567")).doesNotThrowAnyException();
    }
}

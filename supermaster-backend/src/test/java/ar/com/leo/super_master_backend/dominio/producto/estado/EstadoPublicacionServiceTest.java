package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.dux.service.DuxService;
import ar.com.leo.super_master_backend.apis.ml.service.MercadoLibreService;
import ar.com.leo.super_master_backend.apis.nube.service.TiendaNubeService;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoPublicacionDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoPublicacionUpdateDTO;
import ar.com.leo.super_master_backend.dominio.producto.mla.entity.Mla;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EstadoPublicacionServiceTest {

    private final ProductoRepository repo = mock(ProductoRepository.class);
    private final MercadoLibreService ml = mock(MercadoLibreService.class);
    private final TiendaNubeService nube = mock(TiendaNubeService.class);
    private final DuxService dux = mock(DuxService.class);
    private final EstadoPublicacionService service = new EstadoPublicacionService(repo, ml, nube, dux);

    private static tools.jackson.databind.JsonNode json(String s) {
        return new ObjectMapper().readTree(s);
    }

    private Producto producto(String mlaCode, String sku) {
        Producto p = new Producto();
        p.setSku(sku);
        if (mlaCode != null) {
            Mla mla = new Mla();
            mla.setMla(mlaCode);
            p.setMla(mla);
        }
        return p;
    }

    @Test
    void leer_cruzaMlYLasDosTiendas() {
        Producto p = producto("MLA123", "ABC");
        when(repo.findById(1)).thenReturn(Optional.of(p));
        when(ml.buscarMlaPorSkuCualquierEstado("ABC")).thenReturn(new MercadoLibreService.MlaPorSku("MLA123", null));
        when(ml.leerItemRaw("MLA123")).thenReturn(json("""
            {"status":"active","price":100,"available_quantity":3,"attributes":[]}"""));
        when(ml.leerDescripcionMl("MLA123")).thenReturn(null);
        when(nube.buscarProductoPorSku("ABC", TiendaNubeService.STORE_HOGAR)).thenReturn(json("""
            {"id":5,"published":true,"variants":[{"id":9,"price":"100","stock":2,"weight":"1.0","height":"1","width":"1","depth":"1"}]}"""));
        when(nube.buscarProductoPorSku("ABC", TiendaNubeService.STORE_GASTRO)).thenReturn(null);
        when(dux.obtenerProductoPorCodigo("ABC")).thenThrow(new RuntimeException("no encontrado"));

        EstadoPublicacionDTO dto = service.leer(1);

        assertThat(dto.ml().publicado()).isTrue();
        assertThat(dto.ml().estado()).isEqualTo("active");
        assertThat(dto.hogar().publicado()).isTrue();
        assertThat(dto.hogar().estado()).isEqualTo("visible");
        assertThat(dto.gastro().publicado()).isFalse(); // no encontrado
        // dux lanza excepción → error
        assertThat(dto.dux().error()).isTrue();
        assertThat(dto.dux().publicado()).isFalse();
        // datos: descripcionMl null (stub retorna null), descripcionHogar null (JSON sin campo description)
        assertThat(dto.datos().descripcionMl()).isNull();
        assertThat(dto.datos().descripcionHogar()).isNull();
        assertThat(dto.datos().descripcionGastro()).isNull();
        assertThat(dto.datos().seoHogar()).isNotNull();
        assertThat(dto.datos().seoHogar().title()).isNull();
        assertThat(dto.datos().seoGastro()).isNull(); // gastro no encontrado (product null)
        assertThat(dto.datos().mlaResuelto()).isEqualTo("MLA123"); // MLA real resuelto por SKU
    }

    @Test
    void leer_sinMla_mlNoPublicado() {
        Producto p = producto(null, "ABC");
        when(repo.findById(1)).thenReturn(Optional.of(p));
        when(ml.buscarMlaPorSkuCualquierEstado("ABC")).thenReturn(null); // sin publicación activa en ML
        when(nube.buscarProductoPorSku(anyString(), anyString())).thenReturn(null);
        when(dux.obtenerProductoPorCodigo("ABC")).thenThrow(new RuntimeException("no encontrado"));

        EstadoPublicacionDTO dto = service.leer(1);

        assertThat(dto.ml().publicado()).isFalse();
        assertThat(dto.datos().mlaResuelto()).isNull(); // sin publicación vigente → sin MLA resuelto
        verify(ml, never()).leerItemRaw(any());
    }

    @Test
    void aplicar_soloLosCanalesPresentes() {
        Producto p = producto("MLA123", "ABC");
        when(repo.findById(1)).thenReturn(Optional.of(p));
        when(ml.buscarMlaPorSkuCualquierEstado("ABC")).thenReturn(new MercadoLibreService.MlaPorSku("MLA123", null));
        when(nube.buscarProductoPorSku("ABC", TiendaNubeService.STORE_HOGAR)).thenReturn(json("""
            {"id":5,"published":false,"variants":[{"id":9}]}"""));
        when(ml.updateItemStatus("MLA123", "paused")).thenReturn(true);
        when(nube.actualizarPublished(TiendaNubeService.STORE_HOGAR, 5L, true)).thenReturn(true);

        var res = service.aplicar(1, new EstadoPublicacionUpdateDTO("paused", Boolean.TRUE, null));

        verify(ml).updateItemStatus("MLA123", "paused");
        verify(nube).actualizarPublished(TiendaNubeService.STORE_HOGAR, 5L, true);
        verify(nube, never()).actualizarPublished(eq(TiendaNubeService.STORE_GASTRO), anyLong(), anyBoolean());
        assertThat(res.ml().ok()).isTrue();
        assertThat(res.hogar().ok()).isTrue();
        assertThat(res.gastro()).isNull();
    }
}

package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.dux.service.DuxService;
import ar.com.leo.super_master_backend.apis.ml.service.MercadoLibreService;
import ar.com.leo.super_master_backend.apis.nube.service.TiendaNubeService;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.DuxCanalDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoPublicacionUpdateDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.MlCanalDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.NubeCanalDTO;
import ar.com.leo.super_master_backend.apis.ml.service.MlCategoriaAtributoService;
import ar.com.leo.super_master_backend.dominio.producto.mla.entity.Mla;
import ar.com.leo.super_master_backend.dominio.producto.mla.repository.MlaRepository;
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
    private final MlaRepository mlaRepo = mock(MlaRepository.class);
    private final MlCategoriaAtributoService catAttr = mock(MlCategoriaAtributoService.class);
    private final EstadoPublicacionService service = new EstadoPublicacionService(repo, ml, nube, dux, mlaRepo, catAttr);

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

    // ── leerMl ──────────────────────────────────────────────────────────────

    @Test
    void leerMl_publicado_devuelveDtoConCategoria() {
        Producto p = producto("MLA123", "ABC");
        when(repo.findById(1)).thenReturn(Optional.of(p));
        when(ml.buscarMlaPorSkuCualquierEstado("ABC")).thenReturn(new MercadoLibreService.MlaPorSku("MLA123", null));
        when(ml.leerItemRaw("MLA123")).thenReturn(json("""
            {"status":"active","price":100,"available_quantity":3,"attributes":[
              {"id":"SELLER_PACKAGE_HEIGHT","value_struct":{"number":10,"unit":"cm"}},
              {"id":"SELLER_PACKAGE_WEIGHT","value_struct":{"number":500,"unit":"g"}}
            ]}"""));
        when(ml.leerDescripcionMl("MLA123")).thenReturn("Desc ML");
        when(ml.obtenerCategoriaPath("MLA123CAT")).thenReturn(null); // catId null en este JSON
        when(ml.obtenerCategoriaPath(null)).thenReturn(null);
        when(ml.obtenerCategoriaPath("")).thenReturn(null);

        MlCanalDTO dto = service.leerMl(1);

        assertThat(dto.estado().publicado()).isTrue();
        assertThat(dto.estado().estado()).isEqualTo("active");
        assertThat(dto.mlaResuelto()).isEqualTo("MLA123");
        assertThat(dto.descripcion()).isEqualTo("Desc ML");
        assertThat(dto.mlPaqAlto()).isEqualTo(10.0);
        assertThat(dto.mlPaqPeso()).isEqualTo(0.5); // 500g → 0.5 kg
        assertThat(dto.estado().error()).isFalse();
    }

    @Test
    void leerMl_sinMla_noPublicado() {
        Producto p = producto(null, "ABC");
        when(repo.findById(1)).thenReturn(Optional.of(p));
        when(ml.buscarMlaPorSkuCualquierEstado("ABC")).thenReturn(null);

        MlCanalDTO dto = service.leerMl(1);

        assertThat(dto.estado().publicado()).isFalse();
        assertThat(dto.mlaResuelto()).isNull();
        verify(ml, never()).leerItemRaw(any());
    }

    @Test
    void leerMl_canalLanza_ofError() {
        Producto p = producto("MLA123", "ABC");
        when(repo.findById(1)).thenReturn(Optional.of(p));
        when(ml.buscarMlaPorSkuCualquierEstado("ABC")).thenThrow(new RuntimeException("timeout"));

        MlCanalDTO dto = service.leerMl(1);

        assertThat(dto.estado().error()).isTrue();
        assertThat(dto.estado().publicado()).isFalse();
    }

    // ── leerNube ────────────────────────────────────────────────────────────

    @Test
    void leerNube_hogar_publicado() {
        Producto p = producto(null, "ABC");
        when(repo.findById(1)).thenReturn(Optional.of(p));
        when(nube.buscarProductoPorSku("ABC", TiendaNubeService.STORE_HOGAR)).thenReturn(json("""
            {"id":5,"published":true,
             "name":{"es":"Producto Test"},
             "description":{"es":"Desc hogar"},
             "variants":[{"id":9,"price":"100","promotional_price":"80","stock":2,
                          "weight":"1.5","height":"20","width":"15","depth":"10"}]}"""));

        NubeCanalDTO dto = service.leerNube(1, TiendaNubeService.STORE_HOGAR);

        assertThat(dto.estado().publicado()).isTrue();
        assertThat(dto.estado().estado()).isEqualTo("visible");
        assertThat(dto.titulo()).isEqualTo("Producto Test");
        assertThat(dto.descripcion()).isEqualTo("Desc hogar");
        assertThat(dto.peso()).isEqualTo("1.5");
        assertThat(dto.alto()).isEqualTo("20");
        assertThat(dto.ancho()).isEqualTo("15");
        assertThat(dto.profundidad()).isEqualTo("10");
        assertThat(dto.seo()).isNotNull();
    }

    @Test
    void leerNube_noPublicado_estadoNoPublicado() {
        Producto p = producto(null, "ABC");
        when(repo.findById(1)).thenReturn(Optional.of(p));
        when(nube.buscarProductoPorSku("ABC", TiendaNubeService.STORE_GASTRO)).thenReturn(null);

        NubeCanalDTO dto = service.leerNube(1, TiendaNubeService.STORE_GASTRO);

        assertThat(dto.estado().publicado()).isFalse();
        assertThat(dto.estado().error()).isFalse();
    }

    @Test
    void leerNube_canalLanza_ofError() {
        Producto p = producto(null, "ABC");
        when(repo.findById(1)).thenReturn(Optional.of(p));
        when(nube.buscarProductoPorSku("ABC", TiendaNubeService.STORE_HOGAR)).thenThrow(new RuntimeException("timeout"));

        NubeCanalDTO dto = service.leerNube(1, TiendaNubeService.STORE_HOGAR);

        assertThat(dto.estado().error()).isTrue();
        assertThat(dto.estado().publicado()).isFalse();
    }

    // ── leerDux ─────────────────────────────────────────────────────────────

    @Test
    void leerDux_habilitado() {
        Producto p = producto(null, "ABC");
        when(repo.findById(1)).thenReturn(Optional.of(p));
        ar.com.leo.super_master_backend.apis.dux.model.Item item =
                new ar.com.leo.super_master_backend.apis.dux.model.Item();
        item.setHabilitado("S");
        when(dux.obtenerProductoPorCodigo("ABC")).thenReturn(item);

        DuxCanalDTO dto = service.leerDux(1);

        assertThat(dto.estado().publicado()).isTrue();
        assertThat(dto.estado().estado()).isEqualTo("habilitado");
        assertThat(dto.estado().error()).isFalse();
    }

    @Test
    void leerDux_canalLanza_ofError() {
        Producto p = producto(null, "ABC");
        when(repo.findById(1)).thenReturn(Optional.of(p));
        when(dux.obtenerProductoPorCodigo("ABC")).thenThrow(new RuntimeException("no encontrado"));

        DuxCanalDTO dto = service.leerDux(1);

        assertThat(dto.estado().error()).isTrue();
        assertThat(dto.estado().publicado()).isFalse();
    }

    // ── aplicar ─────────────────────────────────────────────────────────────

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

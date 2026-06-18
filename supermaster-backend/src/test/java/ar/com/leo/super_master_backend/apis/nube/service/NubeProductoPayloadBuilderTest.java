package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NubeProductoPayloadBuilderTest {

    private Producto base() {
        Producto p = new Producto();
        p.setSku("SKU1"); p.setTituloNube("Producto de prueba");
        p.setCosto(new BigDecimal("100"));
        return p;
    }

    @Test
    @SuppressWarnings("unchecked")
    void sinInflado_priceEsPvp_sinPromotional() {
        Map<String, Object> payload = NubeProductoPayloadBuilder.construir(base(), new BigDecimal("1500.00"), null, null);

        assertThat(((Map<String, Object>) payload.get("name")).get("es")).isEqualTo("Producto de prueba");
        assertThat(payload.get("published")).isEqualTo(false);
        assertThat(payload.get("free_shipping")).isEqualTo(false);

        List<Map<String, Object>> variants = (List<Map<String, Object>>) payload.get("variants");
        Map<String, Object> v = variants.get(0);
        assertThat(v.get("sku")).isEqualTo("SKU1");
        assertThat(v.get("price")).isEqualTo("1500.00");
        assertThat(v).doesNotContainKey("promotional_price");
        assertThat(v.get("stock")).isEqualTo("");
        assertThat(v.get("weight")).isEqualTo("0.050");
        assertThat(v.get("depth")).isEqualTo("8.00");
        assertThat(v.get("width")).isEqualTo("5.00");
        assertThat(v.get("height")).isEqualTo("5.00");
    }

    @Test
    @SuppressWarnings("unchecked")
    void conInfladoMayor_priceEsInflado_promotionalEsPvp() {
        Map<String, Object> payload = NubeProductoPayloadBuilder.construir(base(), new BigDecimal("1500.00"), new BigDecimal("2000.00"), null);
        Map<String, Object> v = ((List<Map<String, Object>>) payload.get("variants")).get(0);
        assertThat(v.get("price")).isEqualTo("2000.00");
        assertThat(v.get("promotional_price")).isEqualTo("1500.00");
    }

    @Test
    @SuppressWarnings("unchecked")
    void infladoIgualOMenor_priceEsPvp_sinPromotional() {
        // pvp_inflado == pvp (o menor) NO debe generar precio tachado: cae al else.
        Map<String, Object> payload = NubeProductoPayloadBuilder.construir(base(), new BigDecimal("1500.00"), new BigDecimal("1500.00"), null);
        Map<String, Object> v = ((List<Map<String, Object>>) payload.get("variants")).get(0);
        assertThat(v.get("price")).isEqualTo("1500.00");
        assertThat(v).doesNotContainKey("promotional_price");
    }

    @Test
    @SuppressWarnings("unchecked")
    void conCategorias_incluyeArrayCategories() {
        Map<String, Object> payload = NubeProductoPayloadBuilder.construir(
                base(), new BigDecimal("1500.00"), null, List.of(1L, 2L, 3L));
        assertThat((List<Long>) payload.get("categories")).containsExactly(1L, 2L, 3L);
    }

    @Test
    void sinCategorias_noIncluyeClave() {
        Map<String, Object> payload = NubeProductoPayloadBuilder.construir(
                base(), new BigDecimal("1500.00"), null, null);
        assertThat(payload).doesNotContainKey("categories");
    }
}

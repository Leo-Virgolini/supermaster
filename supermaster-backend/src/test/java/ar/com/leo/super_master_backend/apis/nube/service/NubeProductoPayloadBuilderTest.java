package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.apis.openai.dto.SeoGeneradoDTO;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
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
        Map<String, Object> payload = NubeProductoPayloadBuilder.construir(base(), new BigDecimal("1500.00"), null, null, null);

        assertThat(((Map<String, Object>) payload.get("name")).get("es")).isEqualTo("Producto de prueba");
        // Se sube siempre oculto: published=false (no visible en la tienda).
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
        Map<String, Object> payload = NubeProductoPayloadBuilder.construir(base(), new BigDecimal("1500.00"), new BigDecimal("2000.00"), null, null);
        Map<String, Object> v = ((List<Map<String, Object>>) payload.get("variants")).get(0);
        assertThat(v.get("price")).isEqualTo("2000.00");
        assertThat(v.get("promotional_price")).isEqualTo("1500.00");
    }

    @Test
    @SuppressWarnings("unchecked")
    void infladoIgualOMenor_priceEsPvp_sinPromotional() {
        // pvp_inflado == pvp (o menor) NO debe generar precio tachado: cae al else.
        Map<String, Object> payload = NubeProductoPayloadBuilder.construir(base(), new BigDecimal("1500.00"), new BigDecimal("1500.00"), null, null);
        Map<String, Object> v = ((List<Map<String, Object>>) payload.get("variants")).get(0);
        assertThat(v.get("price")).isEqualTo("1500.00");
        assertThat(v).doesNotContainKey("promotional_price");
    }

    @Test
    @SuppressWarnings("unchecked")
    void conCategorias_incluyeArrayCategories() {
        Map<String, Object> payload = NubeProductoPayloadBuilder.construir(
                base(), new BigDecimal("1500.00"), null, List.of(1L, 2L, 3L), null);
        assertThat((List<Long>) payload.get("categories")).containsExactly(1L, 2L, 3L);
    }

    @Test
    void sinCategorias_noIncluyeClave() {
        Map<String, Object> payload = NubeProductoPayloadBuilder.construir(
                base(), new BigDecimal("1500.00"), null, null, null);
        assertThat(payload).doesNotContainKey("categories");
    }

    @Test
    void conMarca_incluyeBrandComoString() {
        Producto p = base();
        Marca ma = new Marca(); ma.setNombre("Tramontina"); p.setMarca(ma);
        Map<String, Object> payload = NubeProductoPayloadBuilder.construir(p, new BigDecimal("1500.00"), null, null, null);
        assertThat(payload.get("brand")).isEqualTo("Tramontina");
    }

    @Test
    void sinMarca_noIncluyeBrand() {
        Map<String, Object> payload = NubeProductoPayloadBuilder.construir(
                base(), new BigDecimal("1500.00"), null, null, null);
        assertThat(payload).doesNotContainKey("brand");
    }

    @Test
    @SuppressWarnings("unchecked")
    void conEanValido_incluyeBarcodeEnElVariant() {
        Producto p = base();
        p.setEan("1234567890128"); // EAN-13 válido
        Map<String, Object> payload = NubeProductoPayloadBuilder.construir(p, new BigDecimal("1500.00"), null, null, null);
        Map<String, Object> v = ((List<Map<String, Object>>) payload.get("variants")).get(0);
        assertThat(v.get("barcode")).isEqualTo("1234567890128");
    }

    @Test
    @SuppressWarnings("unchecked")
    void conEanInvalidoOVacio_noIncluyeBarcode() {
        Producto p = base();
        p.setEan("1178911569"); // 10 dígitos: inválido
        Map<String, Object> v1 = ((List<Map<String, Object>>) NubeProductoPayloadBuilder
                .construir(p, new BigDecimal("1500.00"), null, null, null).get("variants")).get(0);
        assertThat(v1).doesNotContainKey("barcode");

        Producto sinEan = base(); // ean null
        Map<String, Object> v2 = ((List<Map<String, Object>>) NubeProductoPayloadBuilder
                .construir(sinEan, new BigDecimal("1500.00"), null, null, null).get("variants")).get(0);
        assertThat(v2).doesNotContainKey("barcode");
    }

    @Test
    @SuppressWarnings("unchecked")
    void conSeo_incluyeSeoTitleDescriptionYTags() {
        SeoGeneradoDTO seo = new SeoGeneradoDTO(
                "Olla de acero inoxidable 24cm",
                "La mejor olla de acero inoxidable para tu cocina.",
                "olla, acero, cocina");
        Map<String, Object> payload = NubeProductoPayloadBuilder.construir(
                base(), new BigDecimal("1500.00"), null, null, seo);

        assertThat(((Map<String, Object>) payload.get("seo_title")).get("es"))
                .isEqualTo("Olla de acero inoxidable 24cm");
        assertThat(((Map<String, Object>) payload.get("seo_description")).get("es"))
                .isEqualTo("La mejor olla de acero inoxidable para tu cocina.");
        // Tienda Nube espera `tags` como String separado por comas (no como array JSON).
        assertThat(payload.get("tags")).isEqualTo("olla,acero,cocina");
    }
}

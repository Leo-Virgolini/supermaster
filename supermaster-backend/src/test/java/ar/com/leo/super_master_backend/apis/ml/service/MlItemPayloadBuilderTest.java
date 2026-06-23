package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class MlItemPayloadBuilderTest {

    private Producto base() {
        Producto p = new Producto();
        p.setSku("ABC123"); p.setTituloMl("Olla acero 5L");
        Marca m = new Marca(); m.setNombre("Tramontina"); p.setMarca(m);
        return p;
    }

    @Test
    @SuppressWarnings("unchecked")
    void construir_camposBasicosYAtributos() {
        Map<String, Object> payload = MlItemPayloadBuilder.construir(
                base(), "MLA1234", new BigDecimal("5000"), 0, List.of("PIC1", "PIC2"), "Olla acero 5L");

        assertThat(payload.get("family_name")).isEqualTo("Olla acero 5L");
        assertThat(payload).doesNotContainKey("title");
        assertThat(payload.get("category_id")).isEqualTo("MLA1234");
        assertThat(payload.get("price")).isEqualTo(new BigDecimal("5000"));
        assertThat(payload.get("currency_id")).isEqualTo("ARS");
        assertThat(payload.get("available_quantity")).isEqualTo(0);
        assertThat(payload.get("buying_mode")).isEqualTo("buy_it_now");
        assertThat(payload.get("listing_type_id")).isEqualTo("gold_special");
        assertThat(payload.get("condition")).isEqualTo("new");

        Map<String, Object> shipping = (Map<String, Object>) payload.get("shipping");
        assertThat(shipping.get("mode")).isEqualTo("me2");

        List<Map<String, Object>> pics = (List<Map<String, Object>>) payload.get("pictures");
        assertThat(pics).extracting(m -> m.get("id")).containsExactly("PIC1", "PIC2");

        List<Map<String, Object>> attrs = (List<Map<String, Object>>) payload.get("attributes");
        assertThat(attrs).anySatisfy(a -> {
            assertThat(a.get("id")).isEqualTo("ITEM_CONDITION");
            assertThat(a.get("value_id")).isEqualTo("2230284");
        });
        assertThat(attrs).anySatisfy(a -> {
            assertThat(a.get("id")).isEqualTo("BRAND");
            assertThat(a.get("value_name")).isEqualTo("Tramontina");
        });
        assertThat(attrs).anySatisfy(a -> {
            assertThat(a.get("id")).isEqualTo("SELLER_SKU");
            assertThat(a.get("value_name")).isEqualTo("ABC123");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void construir_sinMarca_noIncluyeBrand() {
        Producto p = base(); p.setMarca(null);
        Map<String, Object> payload = MlItemPayloadBuilder.construir(p, "MLA1", new BigDecimal("100"), 1, List.of("P1"), "fam");
        List<Map<String, Object>> attrs = (List<Map<String, Object>>) payload.get("attributes");
        assertThat(attrs).noneSatisfy(a -> assertThat(a.get("id")).isEqualTo("BRAND"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> attrs(Map<String, Object> payload) {
        return (List<Map<String, Object>>) payload.get("attributes");
    }
    private static Map<String, Object> attr(Map<String, Object> payload, String id) {
        return attrs(payload).stream().filter(a -> id.equals(a.get("id"))).findFirst().orElse(null);
    }

    @Test
    void incluyeDimensionesEnteras_yAtributosFiscales() {
        Producto p = new Producto();
        p.setSku("CUT-001");
        p.setIva(new BigDecimal("21.000"));
        p.setMlPaqAlto(new BigDecimal("6"));
        p.setMlPaqAncho(new BigDecimal("25"));
        p.setMlPaqLargo(new BigDecimal("31"));
        p.setMlPaqPeso(new BigDecimal("0.214")); // 214 g

        var payload = MlItemPayloadBuilder.construir(p, "MLA30083", new BigDecimal("100"), 0, List.of(), "Fam");

        assertEquals("6 cm",   attr(payload, "SELLER_PACKAGE_HEIGHT").get("value_name"));
        assertEquals("25 cm",  attr(payload, "SELLER_PACKAGE_WIDTH").get("value_name"));
        assertEquals("31 cm",  attr(payload, "SELLER_PACKAGE_LENGTH").get("value_name"));
        assertEquals("214 g",  attr(payload, "SELLER_PACKAGE_WEIGHT").get("value_name"));

        var vat = attr(payload, "VALUE_ADDED_TAX");
        assertEquals("48405909", vat.get("value_id"));
        assertEquals("21 %",     vat.get("value_name"));

        var imp = attr(payload, "IMPORT_DUTY");
        assertEquals("49553239", imp.get("value_id"));
        assertEquals("0 %",      imp.get("value_name"));
    }

    @Test
    void omiteDimensiones_siFaltaAlguna_yIva105() {
        Producto p = new Producto();
        p.setSku("X");
        p.setIva(new BigDecimal("10.5"));
        p.setMlPaqAlto(new BigDecimal("6")); // faltan las otras 3
        var payload = MlItemPayloadBuilder.construir(p, "MLA1", new BigDecimal("1"), 0, List.of(), "F");

        assertNull(attr(payload, "SELLER_PACKAGE_HEIGHT"));
        assertEquals("10.5 %", attr(payload, "VALUE_ADDED_TAX").get("value_name"));
        assertEquals("48405908", attr(payload, "VALUE_ADDED_TAX").get("value_id"));
    }
}

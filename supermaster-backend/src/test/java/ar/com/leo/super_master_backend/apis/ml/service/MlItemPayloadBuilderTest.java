package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.material.entity.Material;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoMlAtributo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class MlItemPayloadBuilderTest {

    private Producto base() {
        Producto p = new Producto();
        p.setSku("ABC123"); p.setTituloMl("Olla acero 5L");
        Marca m = new Marca(); m.setNombre("Tramontina"); p.setMarca(m);
        return p;
    }

    private Producto productoBase() {
        return base();
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

        // Mapeo natural 1:1: HEIGHT←Alto(6), WIDTH←Ancho(25), LENGTH←Largo(31).
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

    // ==================== Task 5: retiro, EAN→GTIN, atributos guardados ====================

    @Test
    @SuppressWarnings("unchecked")
    void shipping_ofreceRetiroEnPersona() {
        Map<String, Object> payload = MlItemPayloadBuilder.construir(productoBase(), "MLA1", BigDecimal.TEN, 0, List.of(), "fam");
        Map<String, Object> shipping = (Map<String, Object>) payload.get("shipping");
        assertThat(shipping.get("local_pick_up")).isEqualTo(true);
    }

    @Test
    void atributos_eanComoGtin_siCategoriaDeclaraGtin() {
        Producto p = productoBase(); p.setEan("1234567890128");
        var attrs = MlItemPayloadBuilder.construirAtributos(p, Set.of("GTIN"));
        assertThat(attrs).anySatisfy(a -> {
            assertThat(a.get("id")).isEqualTo("GTIN");
            assertThat(a.get("value_name")).isEqualTo("1234567890128");
        });
    }

    @Test
    void atributos_eanComoEan_siNoHayGtin() {
        Producto p = productoBase(); p.setEan("1234567890128");
        var attrs = MlItemPayloadBuilder.construirAtributos(p, Set.of("EAN"));
        assertThat(attrs).anySatisfy(a -> assertThat(a.get("id")).isEqualTo("EAN"));
    }

    @Test
    void atributos_sinEan_noAgregaIdentificador() {
        var attrs = MlItemPayloadBuilder.construirAtributos(productoBase(), Set.of("GTIN"));
        assertThat(attrs).noneSatisfy(a -> assertThat(a.get("id")).isIn("GTIN", "EAN"));
    }

    @Test
    void esGtinValido_validaLongitudYDigitoVerificador() {
        assertThat(MlItemPayloadBuilder.esGtinValido("96385074")).isTrue();        // GTIN-8 válido
        assertThat(MlItemPayloadBuilder.esGtinValido("1234567890128")).isTrue();   // EAN-13 válido
        assertThat(MlItemPayloadBuilder.esGtinValido("12334578")).isFalse();       // dígito verificador inválido
        assertThat(MlItemPayloadBuilder.esGtinValido("1178911569")).isFalse();     // 10 dígitos: longitud inválida
        assertThat(MlItemPayloadBuilder.esGtinValido("abc")).isFalse();
        assertThat(MlItemPayloadBuilder.esGtinValido(null)).isFalse();
        assertThat(MlItemPayloadBuilder.esGtinValido("")).isFalse();
    }

    @Test
    void atributos_eanInvalido_noSeEnvia_yNoBloqueaElResto() {
        Producto p = productoBase();
        p.setEan("1178911569"); // inválido (10 dígitos)
        ProductoMlAtributo a = new ProductoMlAtributo();
        a.setAttributeId("SHAPE"); a.setValueName("Cuadrada");
        p.getMlAtributos().add(a);

        // La categoría declara GTIN y SHAPE (idsValidos es el superset de atributos de la categoría).
        var attrs = MlItemPayloadBuilder.construirAtributos(p, Set.of("GTIN", "SHAPE"));

        // El identificador inválido se omite, pero las características siguen.
        assertThat(attrs).noneSatisfy(x -> assertThat(x.get("id")).isIn("GTIN", "EAN"));
        assertThat(attrs).anySatisfy(x -> assertThat(x.get("id")).isEqualTo("SHAPE"));
    }

    @Test
    void atributos_eanValido_seEnviaComoGtin() {
        Producto p = productoBase();
        p.setEan("1234567890128"); // EAN-13 válido
        var attrs = MlItemPayloadBuilder.construirAtributos(p, Set.of("GTIN"));
        assertThat(attrs).anySatisfy(x -> {
            assertThat(x.get("id")).isEqualTo("GTIN");
            assertThat(x.get("value_name")).isEqualTo("1234567890128");
        });
    }

    @Test
    void atributos_guardadosSeInyectan_conYsinValueId() {
        Producto p = productoBase();
        ProductoMlAtributo a1 = new ProductoMlAtributo();
        a1.setAttributeId("SALE_FORMAT"); a1.setValueId("1359391"); a1.setValueName("Unidad");
        ProductoMlAtributo a2 = new ProductoMlAtributo();
        a2.setAttributeId("MODEL"); a2.setValueName("X100");
        p.getMlAtributos().addAll(List.of(a1, a2));
        var attrs = MlItemPayloadBuilder.construirAtributos(p, Set.of());
        assertThat(attrs).anySatisfy(a -> {
            assertThat(a.get("id")).isEqualTo("SALE_FORMAT");
            assertThat(a.get("value_id")).isEqualTo("1359391");
            assertThat(a.get("value_name")).isEqualTo("Unidad");
        });
        assertThat(attrs).anySatisfy(a -> {
            assertThat(a.get("id")).isEqualTo("MODEL");
            assertThat(a).doesNotContainKey("value_id");
            assertThat(a.get("value_name")).isEqualTo("X100");
        });
    }

    @Test
    void atributos_brandGuardadoPrevaleceSobreLaMarcaDelProducto() {
        Producto p = productoBase(); // marca = "Tramontina"
        ProductoMlAtributo brand = new ProductoMlAtributo();
        brand.setAttributeId("BRAND"); brand.setValueName("ARCOS");
        p.getMlAtributos().add(brand);

        var attrs = MlItemPayloadBuilder.construirAtributos(p, Set.of());

        var brands = attrs.stream().filter(a -> "BRAND".equals(a.get("id"))).toList();
        assertThat(brands).hasSize(1);
        assertThat(brands.get(0).get("value_name")).isEqualTo("ARCOS");
    }

    @Test
    void atributos_marcaAutoSiNoHayBrandGuardado() {
        Producto p = productoBase(); // marca = "Tramontina", sin BRAND guardado
        var attrs = MlItemPayloadBuilder.construirAtributos(p, Set.of());
        var brands = attrs.stream().filter(a -> "BRAND".equals(a.get("id"))).toList();
        assertThat(brands).hasSize(1);
        assertThat(brands.get(0).get("value_name")).isEqualTo("Tramontina");
    }

    @Test
    void atributos_guardadosFueraDeLaCategoria_seDescartan() {
        Producto p = productoBase();
        ProductoMlAtributo valido = new ProductoMlAtributo();
        valido.setAttributeId("SHAPE"); valido.setValueName("Redonda");
        ProductoMlAtributo stale = new ProductoMlAtributo();
        stale.setAttributeId("VOLTAGE"); stale.setValueName("220V"); // la categoría no lo declara
        p.getMlAtributos().addAll(List.of(valido, stale));

        var attrs = MlItemPayloadBuilder.construirAtributos(p, Set.of("SHAPE"));

        assertThat(attrs).anySatisfy(a -> assertThat(a.get("id")).isEqualTo("SHAPE"));
        assertThat(attrs).noneSatisfy(a -> assertThat(a.get("id")).isEqualTo("VOLTAGE"));
    }

    @Test
    void atributos_sinIdsDeCategoria_noFiltraLosGuardados() {
        Producto p = productoBase();
        ProductoMlAtributo a1 = new ProductoMlAtributo();
        a1.setAttributeId("VOLTAGE"); a1.setValueName("220V");
        p.getMlAtributos().add(a1);

        // categoriaAttrIds vacío = no disponible → fail-open, no se filtra.
        var attrs = MlItemPayloadBuilder.construirAtributos(p, Set.of());

        assertThat(attrs).anySatisfy(a -> assertThat(a.get("id")).isEqualTo("VOLTAGE"));
    }

    @Test
    void atributos_materialAutoSoloSiLaCategoriaLoDeclara() {
        Producto p = productoBase();
        Material mat = new Material(); mat.setNombre("Acero");
        p.setMaterial(mat);

        // La categoría declara MATERIAL: se autocompleta desde el material del producto.
        var conMaterial = MlItemPayloadBuilder.construirAtributos(p, Set.of("MATERIAL"));
        assertThat(conMaterial).anySatisfy(a -> {
            assertThat(a.get("id")).isEqualTo("MATERIAL");
            assertThat(a.get("value_name")).isEqualTo("Acero");
        });

        // La categoría NO declara MATERIAL: no se envía (evita que ML lo rechace).
        var sinMaterial = MlItemPayloadBuilder.construirAtributos(p, Set.of());
        assertThat(sinMaterial).noneSatisfy(a -> assertThat(a.get("id")).isEqualTo("MATERIAL"));
    }

    @Test
    void atributos_materialGuardadoPrevaleceYNoSeDuplica() {
        Producto p = productoBase();
        Material mat = new Material(); mat.setNombre("Acero"); p.setMaterial(mat);
        ProductoMlAtributo guardado = new ProductoMlAtributo();
        guardado.setAttributeId("MATERIAL"); guardado.setValueName("Aluminio");
        p.getMlAtributos().add(guardado);

        var attrs = MlItemPayloadBuilder.construirAtributos(p, Set.of("MATERIAL"));

        var materials = attrs.stream().filter(a -> "MATERIAL".equals(a.get("id"))).toList();
        assertThat(materials).hasSize(1);
        assertThat(materials.get(0).get("value_name")).isEqualTo("Aluminio");
    }

    @Test
    void atributos_materialNoAplicaNoSeAutocompletaDesdeElMaterial() {
        Producto p = productoBase();
        Material mat = new Material(); mat.setNombre("Acero"); p.setMaterial(mat);
        ProductoMlAtributo na = new ProductoMlAtributo();
        na.setAttributeId("MATERIAL"); na.setValueName(""); na.setNoAplica(true);
        p.getMlAtributos().add(na);

        var attrs = MlItemPayloadBuilder.construirAtributos(p, Set.of("MATERIAL"));

        // Un MATERIAL "No aplica" NO debe autocompletarse desde el material: una sola entrada, N/A.
        var materials = attrs.stream().filter(a -> "MATERIAL".equals(a.get("id"))).toList();
        assertThat(materials).hasSize(1);
        assertThat(materials.get(0).get("value_id")).isEqualTo("-1");
        assertThat(materials.get(0).get("value_name")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void construir_incluyeGarantiaSinGarantia() {
        var payload = MlItemPayloadBuilder.construir(base(), "MLA1", new BigDecimal("100"), 0, List.of(), "Fam");
        List<Map<String, Object>> saleTerms = (List<Map<String, Object>>) payload.get("sale_terms");
        assertThat(saleTerms).anySatisfy(t -> {
            assertThat(t.get("id")).isEqualTo("WARRANTY_TYPE");
            assertThat(t.get("value_id")).isEqualTo("6150835");
            assertThat(t.get("value_name")).isEqualTo("Sin garantía");
        });
    }

    @Test
    void atributos_brandNoAplicaNoSeDuplicaConLaMarca() {
        Producto p = productoBase(); // marca = "Tramontina"
        ProductoMlAtributo brand = new ProductoMlAtributo();
        brand.setAttributeId("BRAND"); brand.setValueName(""); brand.setNoAplica(true);
        p.getMlAtributos().add(brand);

        var attrs = MlItemPayloadBuilder.construirAtributos(p, Set.of());

        // Un BRAND guardado como "No aplica" NO debe autocompletarse además desde la marca: una sola entrada, N/A.
        var brands = attrs.stream().filter(a -> "BRAND".equals(a.get("id"))).toList();
        assertThat(brands).hasSize(1);
        assertThat(brands.get(0).get("value_id")).isEqualTo("-1");
        assertThat(brands.get(0).get("value_name")).isNull();
    }

    @Test
    void atributos_noAplicaSeEnviaComoNA() {
        Producto p = productoBase();
        ProductoMlAtributo na = new ProductoMlAtributo();
        na.setAttributeId("SHAPE"); na.setValueName(""); na.setNoAplica(true);
        ProductoMlAtributo ok = new ProductoMlAtributo();
        ok.setAttributeId("MODEL"); ok.setValueName("X100");
        p.getMlAtributos().addAll(List.of(na, ok));

        var attrs = MlItemPayloadBuilder.construirAtributos(p, Set.of());

        // El "No aplica" se envía explícito como N/A (value_id "-1", value_name null).
        var shape = attrs.stream().filter(a -> "SHAPE".equals(a.get("id"))).findFirst().orElseThrow();
        assertThat(shape.get("value_id")).isEqualTo("-1");
        assertThat(shape).containsKey("value_name");
        assertThat(shape.get("value_name")).isNull();
        assertThat(attrs).anySatisfy(a -> assertThat(a.get("id")).isEqualTo("MODEL"));
    }
}

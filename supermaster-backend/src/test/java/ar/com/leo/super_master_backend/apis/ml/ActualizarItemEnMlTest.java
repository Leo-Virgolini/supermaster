package ar.com.leo.super_master_backend.apis.ml;

import ar.com.leo.super_master_backend.apis.ml.dto.ResultadoAltaMl;
import ar.com.leo.super_master_backend.apis.ml.service.MercadoLibreService;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ActualizarItemEnMlTest {

    private Producto producto() {
        Producto p = new Producto();
        p.setSku("1234567");
        p.setTituloMl("Olla acero 24cm premium");
        p.setCosto(new BigDecimal("1000"));
        return p;
    }

    private Producto productoActivo(boolean activo) {
        Producto p = producto();
        p.setActivo(activo);
        return p;
    }

    @Test
    void sinVentas_actualizaTituloDescripcionYPrecio() {
        AtomicReference<String> titulo = new AtomicReference<>();
        AtomicReference<String> desc = new AtomicReference<>();
        double[] precio = new double[1];

        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                producto(), "MLA111",
                mla -> 0,                          // sold_quantity = 0
                (mla, t) -> titulo.set(t),
                (mla, d) -> desc.set(d),
                (mla, p) -> { precio[0] = p; return true; },
                sku -> java.util.List.of(),
                (mla, pics) -> {},
                (mla, status) -> true, (mla, attrs) -> {},
                new BigDecimal("5000"), Set.of());           // precioFinal inyectado

        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);
        assertThat(r.itemId()).isEqualTo("MLA111");
        assertThat(r.advertencia()).isNull();
        assertThat(titulo.get()).isEqualTo("Olla acero 24cm premium");
        assertThat(desc.get()).contains("CARACTERÍSTICAS");
        assertThat(precio[0]).isEqualTo(5000.0);
    }

    @Test
    void conVentas_salteaTituloYAvisa() {
        AtomicReference<String> titulo = new AtomicReference<>();
        double[] precio = new double[1];

        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                producto(), "MLA222",
                mla -> 7,                          // sold_quantity > 0
                (mla, t) -> titulo.set(t),
                (mla, d) -> {},
                (mla, p) -> { precio[0] = p; return true; },
                sku -> java.util.List.of(),
                (mla, pics) -> {},
                (mla, status) -> true, (mla, attrs) -> {},
                new BigDecimal("4500"), Set.of());           // precioFinal inyectado

        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);
        assertThat(titulo.get()).isNull();          // título NO actualizado
        assertThat(r.advertencia()).contains("ventas");
        assertThat(precio[0]).isEqualTo(4500.0);   // precio sí, con el valor inyectado
    }

    @Test
    void fallaTitulo_sigueActualizadoConAdvertencia() {
        // Si ML rechaza el campo de título (p.ej. family_name no editable en User Products),
        // no debe abortar el resto del update: queda como advertencia.
        AtomicReference<String> status = new AtomicReference<>();
        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                productoActivo(true), "MLA1515",
                mla -> 0,
                (mla, t) -> { throw new RuntimeException("family_name: The field family name is invalid"); },
                (mla, d) -> {}, (mla, p) -> true,
                sku -> java.util.List.of(), (mla, pics) -> {},
                (mla, s) -> { status.set(s); return true; }, (mla, attrs) -> {},
                new BigDecimal("5000"), Set.of());

        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);
        assertThat(r.advertencia()).contains("título");
        assertThat(status.get()).isEqualTo("active"); // el resto del update continúa
    }

    @Test
    void faltaTitulo_error() {
        Producto p = producto();
        p.setTituloMl(null);
        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                p, "MLA333", mla -> 0, (a, b) -> {}, (a, b) -> {}, (a, b) -> true,
                sku -> java.util.List.of(), (mla, pics) -> {},
                (mla, status) -> true, (mla, attrs) -> {},
                new BigDecimal("5000"), Set.of());
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ERROR);
    }

    @Test
    void conImagenes_reemplazaPictures() {
        AtomicReference<java.util.List<String>> picsPuestas = new AtomicReference<>();

        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                producto(), "MLA444",
                mla -> 0,
                (mla, t) -> {},
                (mla, d) -> {},
                (mla, p) -> true,
                sku -> java.util.List.of("pic1", "pic2"),
                (mla, pics) -> picsPuestas.set(pics),
                (mla, status) -> true, (mla, attrs) -> {},
                new BigDecimal("5000"), Set.of());

        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);
        assertThat(picsPuestas.get()).containsExactly("pic1", "pic2");
    }

    @Test
    void sinImagenes_noLlamaPutPictures() {
        AtomicReference<java.util.List<String>> picsPuestas = new AtomicReference<>();

        MercadoLibreService.actualizarItemEnMlCore(
                producto(), "MLA555",
                mla -> 0, (mla, t) -> {}, (mla, d) -> {}, (mla, p) -> true,
                sku -> java.util.List.of(),
                (mla, pics) -> picsPuestas.set(pics),
                (mla, status) -> true, (mla, attrs) -> {},
                new BigDecimal("5000"), Set.of());

        assertThat(picsPuestas.get()).isNull(); // no se llamó putPictures
    }

    @Test
    void fallaImagenes_siguenActualizadoConAdvertencia() {
        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                producto(), "MLA666",
                mla -> 0, (mla, t) -> {}, (mla, d) -> {}, (mla, p) -> true,
                sku -> { throw new RuntimeException("fallo subir imagen"); },
                (mla, pics) -> {},
                (mla, status) -> true, (mla, attrs) -> {},
                new BigDecimal("5000"), Set.of());

        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);
        assertThat(r.advertencia()).contains("imágenes");
    }

    @Test
    void fallaPrecio_siguenActualizadoConAdvertencia() {
        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                producto(), "MLA777",
                mla -> 0, (mla, t) -> {}, (mla, d) -> {},
                (mla, p) -> false,                 // updatePrice falla
                sku -> java.util.List.of(), (mla, pics) -> {},
                (mla, status) -> true, (mla, attrs) -> {},
                new BigDecimal("5000"), Set.of());

        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);
        assertThat(r.advertencia()).contains("precio");
    }

    @Test
    void activo_poneStatusActive() {
        AtomicReference<String> statusPuesto = new AtomicReference<>();
        MercadoLibreService.actualizarItemEnMlCore(
                productoActivo(true), "MLA888",
                mla -> 0, (mla, t) -> {}, (mla, d) -> {}, (mla, p) -> true,
                sku -> java.util.List.of(), (mla, pics) -> {},
                (mla, status) -> { statusPuesto.set(status); return true; }, (mla, attrs) -> {},
                new BigDecimal("5000"), Set.of());
        assertThat(statusPuesto.get()).isEqualTo("active");
    }

    @Test
    void inactivo_poneStatusPaused() {
        AtomicReference<String> statusPuesto = new AtomicReference<>();
        MercadoLibreService.actualizarItemEnMlCore(
                productoActivo(false), "MLA999",
                mla -> 0, (mla, t) -> {}, (mla, d) -> {}, (mla, p) -> true,
                sku -> java.util.List.of(), (mla, pics) -> {},
                (mla, status) -> { statusPuesto.set(status); return true; }, (mla, attrs) -> {},
                new BigDecimal("5000"), Set.of());
        assertThat(statusPuesto.get()).isEqualTo("paused");
    }

    @Test
    void fallaStatus_sigueActualizadoConAdvertencia() {
        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                productoActivo(true), "MLA1010",
                mla -> 0, (mla, t) -> {}, (mla, d) -> {}, (mla, p) -> true,
                sku -> java.util.List.of(), (mla, pics) -> {},
                (mla, status) -> false, (mla, attrs) -> {},   // putStatus falla
                new BigDecimal("5000"), Set.of());
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);
        assertThat(r.advertencia()).contains("estado");
    }

    @Test
    void concatAdv_preservaLaAdvertenciaPrevia() {
        assertThat(MercadoLibreService.concatAdv(null, "estado")).isEqualTo("estado");
        assertThat(MercadoLibreService.concatAdv("", "estado")).isEqualTo("estado");
        assertThat(MercadoLibreService.concatAdv("descripción", "estado")).isEqualTo("descripción; estado");
    }

    @Test
    void fallanPrecioYEstado_concatenaAmbasAdvertencias() {
        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                producto(), "MLA1212",
                mla -> 0, (mla, t) -> {}, (mla, d) -> {},
                (mla, p) -> false,                 // precio falla
                sku -> java.util.List.of(), (mla, pics) -> {},
                (mla, status) -> false, (mla, attrs) -> {},           // estado falla
                new BigDecimal("5000"), Set.of());
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);
        assertThat(r.advertencia()).contains("precio").contains("estado").contains("; ");
    }

    @Test
    void actualiza_mandaAtributos() {
        AtomicReference<java.util.List<java.util.Map<String, Object>>> attrs = new AtomicReference<>();
        MercadoLibreService.actualizarItemEnMlCore(
                producto(), "MLA1313",
                mla -> 0, (mla, t) -> {}, (mla, d) -> {}, (mla, p) -> true,
                sku -> java.util.List.of(), (mla, pics) -> {},
                (mla, status) -> true,
                (mla, a) -> attrs.set(a),
                new BigDecimal("5000"), Set.of());
        assertThat(attrs.get()).isNotNull();
        assertThat(attrs.get().stream().anyMatch(m -> "SELLER_SKU".equals(m.get("id")))).isTrue();
        assertThat(attrs.get().stream().anyMatch(m -> "IMPORT_DUTY".equals(m.get("id")))).isTrue();
    }

    @Test
    void fallaAtributos_sigueActualizadoConAdvertencia() {
        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                producto(), "MLA1414",
                mla -> 0, (mla, t) -> {}, (mla, d) -> {}, (mla, p) -> true,
                sku -> java.util.List.of(), (mla, pics) -> {},
                (mla, status) -> true,
                (mla, a) -> { throw new RuntimeException("ml rechaza atributo"); },
                new BigDecimal("5000"), Set.of());
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);
        assertThat(r.advertencia()).contains("atributos");
    }

    @Test
    void fallaAtributos_laAdvertenciaIncluyeElMotivoDeMl() {
        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                producto(), "MLA1414B",
                mla -> 0, (mla, t) -> {}, (mla, d) -> {}, (mla, p) -> true,
                sku -> java.util.List.of(), (mla, pics) -> {},
                (mla, status) -> true,
                (mla, a) -> { throw new RuntimeException("BODY_INVALID_FIELDS: GTIN inválido"); },
                new BigDecimal("5000"), Set.of());
        // La advertencia debe traer el detalle del error para poder corregirlo.
        assertThat(r.advertencia()).contains("atributos no actualizados").contains("GTIN inválido");
    }

    @Test
    void fallaDescripcion_sigueActualizadoConAdvertenciaYMotivo() {
        AtomicReference<String> status = new AtomicReference<>();
        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                productoActivo(true), "MLA1616",
                mla -> 0, (mla, t) -> {},
                (mla, d) -> { throw new RuntimeException("The description must be in plain text"); },
                (mla, p) -> true,
                sku -> java.util.List.of(), (mla, pics) -> {},
                (mla, s) -> { status.set(s); return true; }, (mla, attrs) -> {},
                new BigDecimal("5000"), Set.of());
        // Un error de descripción NO debe abortar el resto del update; queda como advertencia con motivo.
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);
        assertThat(r.advertencia()).contains("descripción").contains("plain text");
        assertThat(status.get()).isEqualTo("active");
    }

    // ==================== Nuevos tests: precio calculado (Task 8) ====================

    @Test
    void precioFinalDado_llamaUpdatePriceConEseValor() {
        double[] precioPasado = new double[1];
        AtomicBoolean updatePriceLlamado = new AtomicBoolean(false);

        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                producto(), "MLA2001",
                mla -> 0, (mla, t) -> {}, (mla, d) -> {},
                (mla, p) -> { precioPasado[0] = p; updatePriceLlamado.set(true); return true; },
                sku -> java.util.List.of(), (mla, pics) -> {},
                (mla, status) -> true, (mla, attrs) -> {},
                new BigDecimal("7500.00"), Set.of());

        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);
        assertThat(r.advertencia()).isNull();
        assertThat(updatePriceLlamado.get()).isTrue();
        assertThat(precioPasado[0]).isEqualTo(7500.0);
    }

    @Test
    void precioFinalNull_noLlamaUpdatePriceYAgregaAdvertencia() {
        AtomicBoolean updatePriceLlamado = new AtomicBoolean(false);

        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                producto(), "MLA2002",
                mla -> 0, (mla, t) -> {}, (mla, d) -> {},
                (mla, p) -> { updatePriceLlamado.set(true); return true; },
                sku -> java.util.List.of(), (mla, pics) -> {},
                (mla, status) -> true, (mla, attrs) -> {},
                null, Set.of());                   // precioFinal = null

        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);   // NO aborta
        assertThat(updatePriceLlamado.get()).isFalse();                          // NO llama updatePrice
        assertThat(r.advertencia()).contains("precio no actualizado");
    }

    @Test
    void campoTituloMl_UPusaFamilyName_clasicoUsaTitle() {
        // Ítem User Products (trae family_name) -> se edita family_name (el title está bloqueado).
        assertThat(MercadoLibreService.campoTituloMl("Olla acero 24cm")).isEqualTo("family_name");
        // Ítem del modelo clásico (sin family_name) -> se edita title.
        assertThat(MercadoLibreService.campoTituloMl(null)).isEqualTo("title");
        assertThat(MercadoLibreService.campoTituloMl("")).isEqualTo("title");
        assertThat(MercadoLibreService.campoTituloMl("   ")).isEqualTo("title");
    }

    @Test
    void precioFinalNull_restoDelUpdateContinua() {
        AtomicReference<String> titulo = new AtomicReference<>();
        AtomicReference<String> desc = new AtomicReference<>();
        AtomicReference<String> status = new AtomicReference<>();

        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                productoActivo(true), "MLA2003",
                mla -> 0,
                (mla, t) -> titulo.set(t),
                (mla, d) -> desc.set(d),
                (mla, p) -> true,
                sku -> java.util.List.of(), (mla, pics) -> {},
                (mla, s) -> { status.set(s); return true; }, (mla, attrs) -> {},
                null, Set.of());                   // precioFinal = null

        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);
        assertThat(titulo.get()).isEqualTo("Olla acero 24cm premium"); // título actualizado
        assertThat(desc.get()).contains("CARACTERÍSTICAS");            // descripción actualizada
        assertThat(status.get()).isEqualTo("active");                  // estado actualizado
        assertThat(r.advertencia()).contains("precio no actualizado");
    }

}

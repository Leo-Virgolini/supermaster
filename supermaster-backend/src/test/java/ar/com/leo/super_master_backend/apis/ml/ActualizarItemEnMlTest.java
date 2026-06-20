package ar.com.leo.super_master_backend.apis.ml;

import ar.com.leo.super_master_backend.apis.ml.dto.ResultadoAltaMl;
import ar.com.leo.super_master_backend.apis.ml.service.MercadoLibreService;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
                (mla, pics) -> {});

        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);
        assertThat(r.itemId()).isEqualTo("MLA111");
        assertThat(r.advertencia()).isNull();
        assertThat(titulo.get()).isEqualTo("Olla acero 24cm premium");
        assertThat(desc.get()).contains("CARACTERÍSTICAS");
        assertThat(precio[0]).isEqualTo(5000.0); // 1000 x 5
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
                (mla, pics) -> {});

        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);
        assertThat(titulo.get()).isNull();          // título NO actualizado
        assertThat(r.advertencia()).contains("ventas");
        assertThat(precio[0]).isEqualTo(5000.0);    // precio sí
    }

    @Test
    void faltaTitulo_error() {
        Producto p = producto();
        p.setTituloMl(null);
        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                p, "MLA333", mla -> 0, (a, b) -> {}, (a, b) -> {}, (a, b) -> true,
                sku -> java.util.List.of(), (mla, pics) -> {});
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
                (mla, pics) -> picsPuestas.set(pics));

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
                (mla, pics) -> picsPuestas.set(pics));

        assertThat(picsPuestas.get()).isNull(); // no se llamó putPictures
    }

    @Test
    void fallaImagenes_siguenActualizadoConAdvertencia() {
        ResultadoAltaMl r = MercadoLibreService.actualizarItemEnMlCore(
                producto(), "MLA666",
                mla -> 0, (mla, t) -> {}, (mla, d) -> {}, (mla, p) -> true,
                sku -> { throw new RuntimeException("fallo subir imagen"); },
                (mla, pics) -> {});

        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);
        assertThat(r.advertencia()).contains("imágenes");
    }
}

package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.apis.ml.dto.ResultadoAltaMl;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class CrearItemEnMlTest {

    private final ObjectMapper om = new ObjectMapper();

    private Producto producto() {
        Producto p = new Producto();
        p.setSku("ABC123"); p.setTituloMl("Olla acero 5L"); p.setCosto(new BigDecimal("1000"));
        Marca m = new Marca(); m.setNombre("Tramontina"); p.setMarca(m);
        return p;
    }

    // Lambdas por defecto (caso feliz).
    private final Function<String, Boolean> noExiste = sku -> false;
    private final Function<String, List<String>> conImagen = sku -> List.of("ABC123.jpg");
    private final Function<String, String> subeOk = filename -> "PIC_" + filename;
    private final Function<String, String> predice = titulo -> "MLA1234";

    @Test
    void sinTitulo_error() {
        Producto p = producto(); p.setTituloMl("  ");
        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                p, om, noExiste, conImagen, subeOk, predice,
                json -> "{\"id\":\"MLA1\"}", (id, txt) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ERROR);
        assertThat(r.motivo()).containsIgnoringCase("título");
    }

    @Test
    void yaExiste_noPostea() {
        AtomicReference<String> posted = new AtomicReference<>();
        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                producto(), om, sku -> true, conImagen, subeOk, predice,
                json -> { posted.set(json); return "{\"id\":\"MLA1\"}"; }, (id, txt) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.YA_EXISTIA);
        assertThat(posted.get()).isNull();
    }

    @Test
    void sinImagenes_error() {
        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                producto(), om, noExiste, sku -> List.of(), subeOk, predice,
                json -> "{\"id\":\"MLA1\"}", (id, txt) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ERROR);
        assertThat(r.motivo()).containsIgnoringCase("imágenes");
    }

    @Test
    void ok_creadoConItemId() {
        AtomicReference<String> descripcion = new AtomicReference<>();
        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                producto(), om, noExiste, conImagen, subeOk, predice,
                json -> "{\"id\":\"MLA999\"}",
                (id, txt) -> { descripcion.set(id + "|" + txt); return "{}"; });
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.CREADO);
        assertThat(r.itemId()).isEqualTo("MLA999");
        assertThat(descripcion.get()).startsWith("MLA999|CARACTERÍSTICAS");
    }

    @Test
    void respuestaConError_devuelveError() {
        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                producto(), om, noExiste, conImagen, subeOk, predice,
                json -> "{\"message\":\"Validation error\",\"cause\":[{\"type\":\"error\",\"message\":\"Attribute [BRAND] is required\"}]}",
                (id, txt) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ERROR);
        assertThat(r.motivo()).contains("BRAND");
    }
}

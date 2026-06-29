package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.apis.ml.dto.ResultadoAltaMl;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
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
    private static final String CATEGORY_ID = "MLA1234";
    private static final BigDecimal PRECIO_FINAL = new BigDecimal("18341");

    /** Llama al core con los parámetros de happy-path por defecto. */
    private ResultadoAltaMl core(Function<String, Boolean> yaExiste,
                                  Function<String, List<String>> archivos,
                                  Function<String, String> subidor,
                                  String categoryId,
                                  BigDecimal precioFinal,
                                  Function<String, String> poster,
                                  BiFunction<String, String, String> posterDesc) {
        return MercadoLibreService.crearItemEnMlCore(
                producto(), om, yaExiste, archivos, subidor,
                categoryId, precioFinal, Set.of(),
                cat -> 60, poster, posterDesc);
    }

    @Test
    void sinTitulo_error() {
        Producto p = producto(); p.setTituloMl("  ");
        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                p, om, noExiste, conImagen, subeOk,
                CATEGORY_ID, PRECIO_FINAL, Set.of(),
                cat -> 60,
                json -> "{\"id\":\"MLA1\"}", (id, txt) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ERROR);
        assertThat(r.motivo()).containsIgnoringCase("título");
    }

    @Test
    void yaExiste_noPostea() {
        AtomicReference<String> posted = new AtomicReference<>();
        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                producto(), om, sku -> true, conImagen, subeOk,
                CATEGORY_ID, PRECIO_FINAL, Set.of(),
                cat -> 60,
                json -> { posted.set(json); return "{\"id\":\"MLA1\"}"; }, (id, txt) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.YA_EXISTIA);
        assertThat(posted.get()).isNull();
    }

    @Test
    void sinImagenes_error() {
        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                producto(), om, noExiste, sku -> List.of(), subeOk,
                CATEGORY_ID, PRECIO_FINAL, Set.of(),
                cat -> 60,
                json -> "{\"id\":\"MLA1\"}", (id, txt) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ERROR);
        assertThat(r.motivo()).containsIgnoringCase("imágenes");
    }

    @Test
    void ok_creadoConItemIdYMlau() {
        AtomicReference<String> descripcion = new AtomicReference<>();
        Producto p = producto();
        p.setDescripcionMl("Descripción ML del producto.");  // passthrough: se envía tal cual
        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                p, om, noExiste, conImagen, subeOk,
                CATEGORY_ID, PRECIO_FINAL, Set.of(),
                cat -> 60,
                json -> "{\"id\":\"MLA999\",\"user_product_id\":\"MLAU99\"}",
                (id, txt) -> { descripcion.set(id + "|" + txt); return "{}"; });
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.CREADO);
        assertThat(r.itemId()).isEqualTo("MLA999");
        assertThat(r.mlau()).isEqualTo("MLAU99");
        assertThat(descripcion.get()).isEqualTo("MLA999|Descripción ML del producto."); // passthrough exacto
    }

    @Test
    void sinDescripcionMl_noLlamaPosterDescripcion() {
        AtomicReference<String> descripcion = new AtomicReference<>();
        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                producto(), om, noExiste, conImagen, subeOk,  // producto() sin descripcionMl -> null
                CATEGORY_ID, PRECIO_FINAL, Set.of(),
                cat -> 60,
                json -> "{\"id\":\"MLA998\",\"user_product_id\":\"MLAU98\"}",
                (id, txt) -> { descripcion.set(id + "|" + txt); return "{}"; });
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.CREADO);
        assertThat(descripcion.get()).isNull(); // posterDescripcion NO fue llamado
    }

    @Test
    void respuestaConError_devuelveError() {
        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                producto(), om, noExiste, conImagen, subeOk,
                CATEGORY_ID, PRECIO_FINAL, Set.of(),
                cat -> 60,
                json -> "{\"message\":\"Validation error\",\"cause\":[{\"type\":\"error\",\"message\":\"Attribute [BRAND] is required\"}]}",
                (id, txt) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ERROR);
        assertThat(r.motivo()).contains("BRAND");
    }

    @Test
    void respuestaConSoloWarnings_creado() {
        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                producto(), om, noExiste, conImagen, subeOk,
                CATEGORY_ID, PRECIO_FINAL, Set.of(),
                cat -> 60,
                json -> "{\"id\":\"MLA555\",\"cause\":[{\"type\":\"warning\",\"message\":\"ME2 adoption is mandatory\"}]}",
                (id, txt) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.CREADO);
        assertThat(r.itemId()).isEqualTo("MLA555");
    }

    // ==================== Nuevos casos: precio calculado ====================

    @Test
    void precioFinalDado_jsonPosteadoLlevaEsePrecio() {
        AtomicReference<String> postedJson = new AtomicReference<>();
        BigDecimal precioEsperado = new BigDecimal("18341");
        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                producto(), om, noExiste, conImagen, subeOk,
                CATEGORY_ID, precioEsperado, Set.of(),
                cat -> 60,
                json -> { postedJson.set(json); return "{\"id\":\"MLA777\"}"; },
                (id, txt) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.CREADO);
        assertThat(postedJson.get()).contains("18341");
        // Verifica que NO usa costo*5 (costo=1000, costo*5=5000)
        assertThat(postedJson.get()).doesNotContain("5000");
    }

    @Test
    void precioFinalNull_errorSinPostear() {
        AtomicReference<String> postedJson = new AtomicReference<>();
        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                producto(), om, noExiste, conImagen, subeOk,
                CATEGORY_ID, null, Set.of(),
                cat -> 60,
                json -> { postedJson.set(json); return "{\"id\":\"MLA888\"}"; },
                (id, txt) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ERROR);
        assertThat(r.motivo()).containsIgnoringCase("precio");
        assertThat(postedJson.get()).isNull();
    }

}

package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube;
import ar.com.leo.super_master_backend.apis.nube.model.NubeCredentials.StoreCredentials;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

class CrearProductoEnNubeTest {

    private final ObjectMapper om = new ObjectMapper();

    private Producto producto() {
        Producto p = new Producto();
        p.setSku("SKU1"); p.setTituloNube("Prod"); p.setCosto(new BigDecimal("100"));
        return p;
    }
    private StoreCredentials store() {
        StoreCredentials s = new StoreCredentials(); s.setStoreId("999"); s.setAccessToken("tok");
        return s;
    }

    private NubeCategoriaArbol arbol() { return new NubeCategoriaArbol(); }

    // creador que simula POST /categories: ids incrementales desde 100.
    private BiFunction<Long, String, Long> creador() {
        AtomicLong seq = new AtomicLong(100);
        return (parentId, nombre) -> seq.incrementAndGet();
    }

    private static final List<String> CLASIF = List.of("Cocina", "Ollas");
    private static final List<String> TIPO = List.of("Acero", "Inoxidable");

    @Test
    void yaExiste_noPostea() {
        AtomicReference<String> posted = new AtomicReference<>();
        ResultadoAltaNube r = TiendaNubeService.crearProductoEnNubeCore(
                store(), producto(), new BigDecimal("1500"), null, om,
                CLASIF, TIPO, arbol(), creador(),
                (sku, token) -> om.createObjectNode().put("id", 1), // existe
                (uri, body) -> { posted.set(body); return "{}"; });
        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.YA_EXISTIA);
        assertThat(posted.get()).isNull();
    }

    @Test
    void sinTitulo_error() {
        Producto p = producto(); p.setTituloNube("  ");
        ResultadoAltaNube r = TiendaNubeService.crearProductoEnNubeCore(
                store(), p, new BigDecimal("1500"), null, om,
                CLASIF, TIPO, arbol(), creador(),
                (sku, token) -> null, (uri, body) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.ERROR);
        assertThat(r.motivo()).containsIgnoringCase("título");
    }

    @Test
    void ok_posteaYDevuelveCreado() {
        AtomicReference<String> posted = new AtomicReference<>();
        ResultadoAltaNube r = TiendaNubeService.crearProductoEnNubeCore(
                store(), producto(), new BigDecimal("1500"), null, om,
                CLASIF, TIPO, arbol(), creador(),
                (sku, token) -> null, // no existe
                (uri, body) -> { posted.set(body); return "{\"id\": 5}"; });
        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.CREADO);
        assertThat(posted.get()).contains("\"sku\":\"SKU1\"").contains("\"published\":false");
    }

    @Test
    void faltaClasif_error() {
        ResultadoAltaNube r = TiendaNubeService.crearProductoEnNubeCore(
                store(), producto(), new BigDecimal("1500"), null, om,
                null, TIPO, arbol(), creador(),
                (sku, token) -> null, (uri, body) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.ERROR);
        assertThat(r.motivo()).containsIgnoringCase("clasif");
    }

    @Test
    void faltaTipo_error() {
        ResultadoAltaNube r = TiendaNubeService.crearProductoEnNubeCore(
                store(), producto(), new BigDecimal("1500"), null, om,
                CLASIF, List.of(), arbol(), creador(),
                (sku, token) -> null, (uri, body) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.ERROR);
        assertThat(r.motivo()).containsIgnoringCase("tipo");
    }

    @Test
    void ok_posteaConCategoriasResueltas() {
        AtomicReference<String> posted = new AtomicReference<>();
        // árbol vacío → se crean las 4: Cocina(101) Ollas(102) Acero(103) Inoxidable(104)
        ResultadoAltaNube r = TiendaNubeService.crearProductoEnNubeCore(
                store(), producto(), new BigDecimal("1500"), null, om,
                CLASIF, TIPO, arbol(), creador(),
                (sku, token) -> null,
                (uri, body) -> { posted.set(body); return "{\"id\": 5}"; });
        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.CREADO);
        assertThat(posted.get()).contains("\"categories\":[101,102,103,104]");
        assertThat(r.productoNubeId()).isEqualTo(5L);
    }
}

package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube;
import ar.com.leo.super_master_backend.apis.nube.model.NubeCredentials.StoreCredentials;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void yaExiste_noPostea() {
        AtomicReference<String> posted = new AtomicReference<>();
        ResultadoAltaNube r = TiendaNubeService.crearProductoEnNubeCore(
                store(), producto(), new BigDecimal("1500"), null, om,
                (sku, token) -> om.createObjectNode().put("id", 1), // buscador: existe
                (uri, body) -> { posted.set(body); return "{}"; });
        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.YA_EXISTIA);
        assertThat(posted.get()).isNull();
    }

    @Test
    void sinTitulo_error() {
        Producto p = producto(); p.setTituloNube("  ");
        ResultadoAltaNube r = TiendaNubeService.crearProductoEnNubeCore(
                store(), p, new BigDecimal("1500"), null, om,
                (sku, token) -> null, (uri, body) -> "{}");
        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.ERROR);
        assertThat(r.motivo()).containsIgnoringCase("título");
    }

    @Test
    void ok_posteaYDevuelveCreado() {
        AtomicReference<String> posted = new AtomicReference<>();
        ResultadoAltaNube r = TiendaNubeService.crearProductoEnNubeCore(
                store(), producto(), new BigDecimal("1500"), null, om,
                (sku, token) -> null, // no existe
                (uri, body) -> { posted.set(body); return "{\"id\": 5}"; });
        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.CREADO);
        assertThat(posted.get()).contains("\"sku\":\"SKU1\"").contains("\"published\":false");
    }
}

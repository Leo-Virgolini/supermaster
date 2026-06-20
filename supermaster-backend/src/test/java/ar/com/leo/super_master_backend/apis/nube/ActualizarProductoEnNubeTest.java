package ar.com.leo.super_master_backend.apis.nube;

import ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube;
import ar.com.leo.super_master_backend.apis.nube.service.TiendaNubeService;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ActualizarProductoEnNubeTest {

    private final ObjectMapper om = new ObjectMapper();

    private Producto producto() {
        Producto p = new Producto();
        p.setSku("1234567");
        p.setTituloNube("Olla acero 24cm");
        return p;
    }

    private JsonNode existente(String json) {
        try { return om.readTree(json); } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void actualiza_armaPatchYPrecio() {
        // producto existente en Nube con id 500 y una variante (id 900) cuyo sku coincide
        JsonNode existente = existente("{\"id\":500,\"variants\":[{\"id\":900,\"sku\":\"1234567\",\"price\":\"100\"}]}");
        AtomicReference<String> patchUri = new AtomicReference<>();
        AtomicReference<String> patchBody = new AtomicReference<>();
        AtomicReference<String> precioPrice = new AtomicReference<>();
        AtomicReference<String> precioPromo = new AtomicReference<>();
        long[] precioIds = new long[2];

        ResultadoAltaNube r = TiendaNubeService.actualizarProductoEnNubeCore(
                producto(), new BigDecimal("150"), new BigDecimal("180"), om, "9",
                sku -> existente,
                (uri, body) -> { patchUri.set(uri); patchBody.set(body); },
                (productId, variantId, price, promo) -> {
                    precioIds[0] = productId; precioIds[1] = variantId;
                    precioPrice.set(price); precioPromo.set(promo); return true;
                });

        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.ACTUALIZADO);
        assertThat(r.productoNubeId()).isEqualTo(500L);
        assertThat(patchUri.get()).isEqualTo("/9/products/500");
        assertThat(patchBody.get()).contains("\"name\"").contains("Olla acero 24cm").contains("\"description\"");
        assertThat(precioIds[0]).isEqualTo(500L);
        assertThat(precioIds[1]).isEqualTo(900L);
        // pvpInflado(180) > pvp(150): price=180 (tachado), promotional=150
        assertThat(precioPrice.get()).isEqualTo("180");
        assertThat(precioPromo.get()).isEqualTo("150");
    }

    @Test
    void actualiza_sinInflado_soloPrice() {
        JsonNode existente = existente("{\"id\":1,\"variants\":[{\"id\":2,\"sku\":\"1234567\"}]}");
        AtomicReference<String> precioPrice = new AtomicReference<>();
        AtomicReference<String> precioPromo = new AtomicReference<>();

        TiendaNubeService.actualizarProductoEnNubeCore(
                producto(), new BigDecimal("150"), null, om, "9",
                sku -> existente, (uri, body) -> {},
                (productId, variantId, price, promo) -> { precioPrice.set(price); precioPromo.set(promo); return true; });

        assertThat(precioPrice.get()).isEqualTo("150");
        assertThat(precioPromo.get()).isNull();
    }

    @Test
    void actualiza_noExiste_error() {
        ResultadoAltaNube r = TiendaNubeService.actualizarProductoEnNubeCore(
                producto(), new BigDecimal("150"), null, om, "9",
                sku -> null, (uri, body) -> {}, (a, b, c, d) -> true);
        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.ERROR);
    }
}

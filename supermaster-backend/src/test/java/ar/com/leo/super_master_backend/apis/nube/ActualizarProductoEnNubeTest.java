package ar.com.leo.super_master_backend.apis.nube;

import ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube;
import ar.com.leo.super_master_backend.apis.nube.service.TiendaNubeService;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoGeneradoDTO;
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

        Producto p = producto();
        p.setDescripcionNube("<p>Descripción Nube passthrough.</p>"); // necesario para incluir "description" en el PATCH

        ResultadoAltaNube r = TiendaNubeService.actualizarProductoEnNubeCore(
                p, new BigDecimal("150"), new BigDecimal("180"), om, "9",
                java.util.List.of(), null,
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
                java.util.List.of(), null,
                sku -> existente, (uri, body) -> {},
                (productId, variantId, price, promo) -> { precioPrice.set(price); precioPromo.set(promo); return true; });

        assertThat(precioPrice.get()).isEqualTo("150");
        assertThat(precioPromo.get()).isNull();
    }

    @Test
    void actualiza_noExiste_error() {
        ResultadoAltaNube r = TiendaNubeService.actualizarProductoEnNubeCore(
                producto(), new BigDecimal("150"), null, om, "9",
                java.util.List.of(), null,
                sku -> null, (uri, body) -> {}, (a, b, c, d) -> true);
        assertThat(r.estado()).isEqualTo(ResultadoAltaNube.Estado.ERROR);
    }

    @Test
    void actualiza_incluyeCategoriasEnElPatch() {
        JsonNode existente = existente("{\"id\":7,\"variants\":[{\"id\":8,\"sku\":\"1234567\"}]}");
        AtomicReference<String> patchBody = new AtomicReference<>();

        TiendaNubeService.actualizarProductoEnNubeCore(
                producto(), new BigDecimal("150"), null, om, "9",
                java.util.List.of(10L, 20L, 30L), null,
                sku -> existente,
                (uri, body) -> patchBody.set(body),
                (productId, variantId, price, promo) -> true);

        assertThat(patchBody.get()).contains("\"categories\"").contains("10").contains("20").contains("30");
    }

    @Test
    void actualiza_sinCategorias_noIncluyeCategories() {
        JsonNode existente = existente("{\"id\":7,\"variants\":[{\"id\":8,\"sku\":\"1234567\"}]}");
        AtomicReference<String> patchBody = new AtomicReference<>();

        TiendaNubeService.actualizarProductoEnNubeCore(
                producto(), new BigDecimal("150"), null, om, "9",
                java.util.List.of(), null,
                sku -> existente,
                (uri, body) -> patchBody.set(body),
                (productId, variantId, price, promo) -> true);

        assertThat(patchBody.get()).doesNotContain("\"categories\"");
    }

    @Test
    void actualiza_noTocaVisibilidad_noMandaPublished() {
        // La edición no debe mandar "published": el alta crea oculto y el PUT parcial
        // no pisa la visibilidad que el usuario haya definido manualmente en Nube.
        JsonNode existente = existente("{\"id\":7,\"variants\":[{\"id\":8,\"sku\":\"1234567\"}]}");
        AtomicReference<String> patchBody = new AtomicReference<>();

        // producto() tiene activo=true por default
        TiendaNubeService.actualizarProductoEnNubeCore(
                producto(), new BigDecimal("150"), null, om, "9",
                java.util.List.of(), null,
                sku -> existente,
                (uri, body) -> patchBody.set(body),
                (productId, variantId, price, promo) -> true);
        assertThat(patchBody.get()).doesNotContain("\"published\"");

        Producto inactivo = producto();
        inactivo.setActivo(false);
        AtomicReference<String> patchBody2 = new AtomicReference<>();
        TiendaNubeService.actualizarProductoEnNubeCore(
                inactivo, new BigDecimal("150"), null, om, "9",
                java.util.List.of(), null,
                sku -> existente,
                (uri, body) -> patchBody2.set(body),
                (productId, variantId, price, promo) -> true);
        assertThat(patchBody2.get()).doesNotContain("\"published\"");
    }

    @Test
    void actualiza_conSeo_incluyeSeoEnElPut() {
        JsonNode existente = existente("{\"id\":7,\"variants\":[{\"id\":8,\"sku\":\"1234567\"}]}");
        AtomicReference<String> patchBody = new AtomicReference<>();
        SeoGeneradoDTO seo = new SeoGeneradoDTO("Titulo SEO", "Descripcion SEO", "uno, dos");

        TiendaNubeService.actualizarProductoEnNubeCore(
                producto(), new BigDecimal("150"), null, om, "9",
                java.util.List.of(), seo,
                sku -> existente,
                (uri, body) -> patchBody.set(body),
                (productId, variantId, price, promo) -> true);

        assertThat(patchBody.get())
                .contains("\"seo_title\"").contains("Titulo SEO")
                .contains("\"seo_description\"").contains("Descripcion SEO")
                .contains("\"tags\"").contains("uno").contains("dos");
    }

    @Test
    void actualiza_conEanValido_mandaBarcodeAlaVariante() {
        JsonNode existente = existente("{\"id\":500,\"variants\":[{\"id\":900,\"sku\":\"1234567\"}]}");
        Producto p = producto();
        p.setEan("1234567890128"); // EAN-13 válido
        java.util.Map<String, String> puts = new java.util.HashMap<>();

        TiendaNubeService.actualizarProductoEnNubeCore(
                p, new BigDecimal("150"), null, om, "9",
                java.util.List.of(), null,
                sku -> existente,
                (uri, body) -> puts.put(uri, body),
                (productId, variantId, price, promo) -> true);

        assertThat(puts).containsKey("/9/products/500/variants/900");
        assertThat(puts.get("/9/products/500/variants/900")).contains("\"barcode\"").contains("1234567890128");
    }
}

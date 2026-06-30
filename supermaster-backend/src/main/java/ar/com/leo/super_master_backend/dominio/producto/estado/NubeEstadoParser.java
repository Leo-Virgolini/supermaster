package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoCanalDTO;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

/** Parsea el JSON de GET /products/sku/{sku} de Nube a un EstadoCanalDTO (puro). */
public final class NubeEstadoParser {

    private NubeEstadoParser() {}

    public static EstadoCanalDTO parse(JsonNode product) {
        if (product == null || product.isMissingNode() || product.isNull()) return EstadoCanalDTO.ofError();
        boolean published = product.path("published").asBoolean(false);
        JsonNode variant = product.path("variants").path(0);

        String precioStr = variant.path("price").asString(null);
        BigDecimal precio = null;
        if (precioStr != null && !precioStr.isBlank()) {
            try { precio = new BigDecimal(precioStr.trim()); } catch (NumberFormatException ignored) {}
        }
        JsonNode stockN = variant.path("stock");
        Integer stock = stockN.isNumber() ? stockN.asInt() : null;
        String pesoStr = variant.path("weight").asString(null);
        String peso = pesoStr != null ? pesoStr + " kg" : null;
        String alto = variant.path("height").asString(null);
        String ancho = variant.path("width").asString(null);
        String largo = variant.path("depth").asString(null);
        String dims = (alto != null && ancho != null && largo != null)
                ? alto + " × " + ancho + " × " + largo + " cm" : null;

        String promoStr = variant.path("promotional_price").asString(null);
        BigDecimal promo = null;
        if (promoStr != null && !promoStr.isBlank()) {
            try {
                BigDecimal p = new BigDecimal(promoStr.trim());
                if (p.signum() > 0) promo = p;   // 0/vacío = sin promo
            } catch (NumberFormatException ignored) {}
        }

        int imagenes = product.path("images").size();
        return new EstadoCanalDTO(true, published ? "visible" : "oculta", precio, promo, stock, peso, dims, false, imagenes);
    }
}

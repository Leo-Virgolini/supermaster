package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoCanalDTO;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

/** Parsea el JSON de GET /items/{id} de ML a un EstadoCanalDTO (puro). */
public final class MlEstadoParser {

    private MlEstadoParser() {}

    public static EstadoCanalDTO parse(JsonNode item) {
        if (item == null || item.isMissingNode() || item.isNull()) return EstadoCanalDTO.ofError();
        String status = item.path("status").asString(null);
        JsonNode precioN = item.path("price");
        BigDecimal precio = precioN.isNumber() ? precioN.decimalValue() : null;
        JsonNode stockN = item.path("available_quantity");
        Integer stock = stockN.isNumber() ? stockN.asInt() : null;

        String alto = atributo(item, "SELLER_PACKAGE_HEIGHT");
        String ancho = atributo(item, "SELLER_PACKAGE_WIDTH");
        String largo = atributo(item, "SELLER_PACKAGE_LENGTH");
        String peso = atributo(item, "SELLER_PACKAGE_WEIGHT");
        String dims = (alto != null && ancho != null && largo != null)
                ? alto + " × " + ancho + " × " + largo : null;

        int imagenes = item.path("pictures").size();
        return new EstadoCanalDTO(true, status, precio, null, stock, peso, dims, false, imagenes);
    }

    /** value_name del atributo con ese id, o null. Usa asString(null) (idiom Jackson 3 del proyecto). */
    private static String atributo(JsonNode item, String id) {
        for (JsonNode a : item.path("attributes")) {
            if (id.equals(a.path("id").asString(null))) return a.path("value_name").asString(null);
        }
        return null;
    }
}

package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDTO;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Extrae categoría y atributos de ficha técnica editable del JSON de GET /items/{id} de ML. */
public final class MlDatosParser {

    private MlDatosParser() {}

    /** Atributos de sistema que arma el payload builder; no son ficha técnica editable por el usuario. */
    private static final Set<String> OMITIR = Set.of(
            "ITEM_CONDITION", "SELLER_SKU",
            "SELLER_PACKAGE_HEIGHT", "SELLER_PACKAGE_WIDTH", "SELLER_PACKAGE_LENGTH", "SELLER_PACKAGE_WEIGHT",
            "IMPORT_DUTY");

    public static String categoryId(JsonNode item) {
        if (item == null) return null;
        return item.path("category_id").asString(null);
    }

    public static List<MlAtributoDTO> atributos(JsonNode item) {
        List<MlAtributoDTO> out = new ArrayList<>();
        if (item == null) return out;
        for (JsonNode a : item.path("attributes")) {
            String id = a.path("id").asString(null);
            if (id == null || OMITIR.contains(id)) continue;
            String valueId = a.path("value_id").asString(null);
            boolean noAplica = "-1".equals(valueId);
            String valueName = noAplica ? null : a.path("value_name").asString(null);
            out.add(new MlAtributoDTO(id, noAplica ? null : valueId, valueName, noAplica));
        }
        return out;
    }
}

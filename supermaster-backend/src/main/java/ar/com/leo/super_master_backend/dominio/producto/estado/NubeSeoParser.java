package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.dominio.producto.estado.dto.SeoCanalDTO;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Extrae el SEO (title/description/tags) del JSON de un producto de Tienda Nube. */
public final class NubeSeoParser {

    private NubeSeoParser() {}

    public static SeoCanalDTO parse(JsonNode product) {
        if (product == null) return null;
        return new SeoCanalDTO(
                i18n(product.path("seo_title")),
                i18n(product.path("seo_description")),
                tags(product.path("tags")));
    }

    /** Campo que puede venir como objeto i18n ({"es": "..."}) o como string plano; null si ausente. */
    private static String i18n(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isObject()) return node.path("es").asString(null);
        return node.asString(null);
    }

    /** Tags: array de strings → unidos por ", "; string → tal cual; null si ausente. */
    private static String tags(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isArray()) {
            List<String> out = new ArrayList<>();
            for (JsonNode t : node) {
                String s = t.asString(null);
                if (s != null && !s.isBlank()) out.add(s.trim());
            }
            return out.isEmpty() ? null : String.join(", ", out);
        }
        return node.asString(null);
    }
}

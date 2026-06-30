package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDTO;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/** Extrae categoría y atributos de ficha técnica editable del JSON de GET /items/{id} de ML. */
public final class MlDatosParser {

    private MlDatosParser() {}

    /** Dimensiones del paquete de envío leídas de ML. cm para alto/ancho/largo; kg para peso. */
    public record PaqueteMl(Double altoCm, Double anchoCm, Double largoCm, Double pesoKg) {}

    /** Regex cacheado para extraer el primer número de value_name (fallback cuando no hay value_struct). */
    private static final Pattern NUM = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)");

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

    public static PaqueteMl paquete(JsonNode item) {
        Double alto = null, ancho = null, largo = null, pesoKg = null;
        if (item != null) {
            for (JsonNode a : item.path("attributes")) {
                String id = a.path("id").asString(null);
                if (id == null) continue;
                Double num = numeroDeAtributo(a);
                if (num == null) continue;
                String unit = unitDeAtributo(a);
                switch (id) {
                    case "SELLER_PACKAGE_HEIGHT" -> alto = convertirACm(num, unit);
                    case "SELLER_PACKAGE_WIDTH" -> ancho = convertirACm(num, unit);
                    case "SELLER_PACKAGE_LENGTH" -> largo = convertirACm(num, unit);
                    case "SELLER_PACKAGE_WEIGHT" -> pesoKg = convertirAKg(num, unit);
                    default -> { /* no es del paquete */ }
                }
            }
        }
        return new PaqueteMl(alto, ancho, largo, pesoKg);
    }

    /** Lee la unidad del atributo desde value_struct.unit; null si no está disponible. */
    private static String unitDeAtributo(JsonNode a) {
        JsonNode struct = a.path("value_struct");
        if (struct.isObject()) {
            return struct.path("unit").asString(null);
        }
        return null;
    }

    /** Convierte una dimensión al valor en cm según la unidad dada. Fallback cm si unit es null/desconocida. */
    private static double convertirACm(double number, String unit) {
        if (unit == null) return number; // fallback: asumir cm
        return switch (unit.toLowerCase()) {
            case "cm" -> number;
            case "mm" -> number / 10.0;
            case "m"  -> number * 100.0;
            default   -> number; // unidad desconocida: asumir cm
        };
    }

    /** Convierte un peso al valor en kg según la unidad dada. Fallback g→kg si unit es null/desconocida. */
    private static double convertirAKg(double number, String unit) {
        if (unit == null) return number / 1000.0; // fallback: asumir g
        return switch (unit.toLowerCase()) {
            case "kg" -> number;
            case "g"  -> number / 1000.0;
            case "mg" -> number / 1_000_000.0;
            default   -> number / 1000.0; // unidad desconocida: asumir g
        };
    }

    /** Número del atributo: primero value_struct.number; fallback al número inicial de value_name. */
    private static Double numeroDeAtributo(JsonNode a) {
        JsonNode struct = a.path("value_struct");
        if (struct.isObject() && struct.path("number").isNumber()) {
            return struct.path("number").asDouble();
        }
        String vn = a.path("value_name").asString(null);
        if (vn != null) {
            Matcher m = NUM.matcher(vn);
            if (m.find()) return Double.parseDouble(m.group(1));
        }
        return null;
    }
}

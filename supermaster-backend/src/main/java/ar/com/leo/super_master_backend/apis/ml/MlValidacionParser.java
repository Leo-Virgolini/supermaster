package ar.com.leo.super_master_backend.apis.ml;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsea respuestas/errores de validación de Mercado Libre para mostrar mensajes legibles,
 * respetando la distinción {@code type: warning} (no bloqueante) vs {@code type: error} (bloqueante).
 *
 * <p>Formato típico de ML:
 * <pre>{ "message": "Validation error", "status": 400,
 *        "cause": [ { "type": "error"|"warning", "code": "...", "message": "..." } ] }</pre>
 */
public final class MlValidacionParser {

    private MlValidacionParser() {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Resume un texto/cuerpo de error de ML a un mensaje legible: junta los {@code message} de las
     * causas que NO son {@code type:"warning"} (los errores). El texto puede traer el JSON embebido
     * (p.ej. "400 Bad Request: { ... }").
     *
     * @return los mensajes de error unidos por "; "; {@code null} si solo hay warnings (no es un
     *         fallo real); o el texto original recortado si no hay JSON parseable.
     */
    public static String resumirError(String texto) {
        if (texto == null || texto.isBlank()) {
            return texto;
        }
        String json = extraerJson(texto);
        if (json == null) {
            return texto.trim();
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode cause = root.path("cause");
            if (cause.isArray() && !cause.isEmpty()) {
                List<String> errores = new ArrayList<>();
                boolean huboWarning = false;
                for (JsonNode c : cause) {
                    String type = c.path("type").asString("error"); // sin type → se trata como error
                    if ("warning".equalsIgnoreCase(type)) {
                        huboWarning = true;
                        continue;
                    }
                    String m = c.path("message").asString(null);
                    if (m != null && !m.isBlank()) {
                        errores.add(m);
                    }
                }
                if (!errores.isEmpty()) {
                    return String.join("; ", errores);
                }
                if (huboWarning) {
                    return null; // solo warnings → no es un fallo
                }
            }
            // Sin array `cause` (ej. {"cause":374,"message":"BODY_INVALID_FIELDS","error":"The field
            // family name is invalid"}): `message` suele ser un código y `error` el texto descriptivo.
            // Preferimos el que parezca una frase (tiene espacios) sobre el código.
            String error = root.path("error").asString(null);
            String message = root.path("message").asString(null);
            if (error != null && error.contains(" ")) {
                return error;
            }
            if (message != null && !message.isBlank()) {
                return message;
            }
            return (error != null && !error.isBlank()) ? error : texto.trim();
        } catch (Exception e) {
            return texto.trim();
        }
    }

    /** Extrae el primer objeto JSON embebido en el texto (de la primera '{' a la última '}'). */
    private static String extraerJson(String s) {
        int i = s.indexOf('{');
        int j = s.lastIndexOf('}');
        return (i >= 0 && j > i) ? s.substring(i, j + 1) : null;
    }
}

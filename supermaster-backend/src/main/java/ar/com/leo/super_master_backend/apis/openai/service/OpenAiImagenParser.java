package ar.com.leo.super_master_backend.apis.openai.service;

import tools.jackson.databind.JsonNode;

/** Lee la respuesta de POST /images/edits de OpenAI (puro). */
public final class OpenAiImagenParser {

    private OpenAiImagenParser() {}

    public static String b64(JsonNode root) {
        JsonNode node = root.path("data").path(0).path("b64_json");
        if (node.isMissingNode() || node.isNull()) return null;
        return node.asString(null);
    }

    public static long tokensEntrada(JsonNode root) {
        return root.path("usage").path("input_tokens").asLong(0);
    }

    public static long tokensSalida(JsonNode root) {
        return root.path("usage").path("output_tokens").asLong(0);
    }
}

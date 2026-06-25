package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.dto.SeoGeneradoDTO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Parsea el JSON devuelto por la IA y trunca a los límites de Nube. Lógica pura, sin red. */
public final class OpenAiSeoParser {

    private OpenAiSeoParser() {}

    static final int MAX_TITLE = 70;
    static final int MAX_DESC = 320;

    /** Parsea {@code {seo_title, seo_description, tags}} y trunca title/description. */
    public static SeoGeneradoDTO parseContenido(String contenidoJson, ObjectMapper om) {
        try {
            JsonNode n = om.readTree(contenidoJson);
            return new SeoGeneradoDTO(
                    truncar(texto(n, "seo_title"), MAX_TITLE),
                    truncar(texto(n, "seo_description"), MAX_DESC),
                    texto(n, "tags").trim()
            );
        } catch (Exception e) {
            throw new IllegalStateException("Respuesta de OpenAI no parseable: " + e.getMessage(), e);
        }
    }

    private static String texto(JsonNode n, String campo) {
        JsonNode v = n.path(campo);
        return v.isMissingNode() || v.isNull() ? "" : v.asString();
    }

    private static String truncar(String s, int max) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() <= max) return s;
        String corte = s.substring(0, max);
        int ultimoEspacio = corte.lastIndexOf(' ');
        // Si hay un espacio razonablemente cerca del límite, cortamos ahí para no partir la última palabra.
        if (ultimoEspacio >= max * 0.6) corte = corte.substring(0, ultimoEspacio);
        return corte.trim();
    }
}

package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.apis.openai.dto.SeoGeneradoDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Agrega los campos de SEO (seo_title/seo_description/tags) al body de Tienda Nube, si el seo trae datos. */
final class NubeSeoPayload {

    private NubeSeoPayload() {}

    /** Si {@code seo != null}, agrega al body los campos no vacíos: seo_title/seo_description (i18n es) y tags (lista). */
    static void aplicar(Map<String, Object> body, SeoGeneradoDTO seo) {
        if (seo == null) return;

        if (seo.seoTitle() != null && !seo.seoTitle().isBlank()) {
            body.put("seo_title", Map.of("es", seo.seoTitle()));
        }
        if (seo.seoDescription() != null && !seo.seoDescription().isBlank()) {
            body.put("seo_description", Map.of("es", seo.seoDescription()));
        }
        if (seo.seoTags() != null && !seo.seoTags().isBlank()) {
            List<String> tags = new ArrayList<>();
            for (String tag : seo.seoTags().split(",")) {
                String t = tag.trim();
                if (!t.isEmpty()) tags.add(t);
            }
            if (!tags.isEmpty()) {
                // La API de Tienda Nube espera `tags` como String separado por comas, NO como
                // array (si se manda un array JSON, Nube lo colapsa a un único tag "Array").
                body.put("tags", String.join(",", tags));
            }
        }
    }
}

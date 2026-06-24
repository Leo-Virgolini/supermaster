package ar.com.leo.super_master_backend.apis.openai.dto;

/** Resultado del SEO generado por la IA (ya truncado a los límites de Nube). */
public record SeoGeneradoDTO(
        String seoTitle,
        String seoDescription,
        String seoTags
) {}

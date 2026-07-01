package ar.com.leo.super_master_backend.apis.ml.dto;

import java.util.List;

/**
 * Definición de un atributo de categoría de ML, filtrado y agrupado,
 * listo para presentar en el formulario de publicación.
 */
public record MlAtributoDefDTO(
        String id,
        String name,
        String valueType,
        List<MlAtributoValorDTO> values,
        List<String> allowedUnits,
        String defaultUnit,
        boolean required,
        boolean conditional,
        boolean multivalued,
        String grupo,
        int relevance,
        Integer valueMaxLength,
        String example,
        String hint,
        boolean allowVariations,
        boolean variationAttribute
) {
}

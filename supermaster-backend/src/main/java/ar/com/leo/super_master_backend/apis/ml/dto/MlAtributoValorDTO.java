package ar.com.leo.super_master_backend.apis.ml.dto;

/**
 * Valor permitido de un atributo de categoría de ML
 * (elemento de {@code values[]} en {@code GET /categories/{id}/attributes}).
 */
public record MlAtributoValorDTO(String id, String name, String rgb) {
}

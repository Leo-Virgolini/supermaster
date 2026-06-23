package ar.com.leo.super_master_backend.apis.ml.dto;

/**
 * Una predicción de categoría de ML (del predictor domain_discovery).
 * categoryPath es la herencia completa "Padre &gt; ... &gt; Hoja" (o el nombre si no se pudo resolver).
 */
public record PrediccionCategoriaMlDTO(String categoryId, String categoryName, String categoryPath) {
}

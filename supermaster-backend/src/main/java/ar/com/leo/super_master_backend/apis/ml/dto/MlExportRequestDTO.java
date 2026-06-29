package ar.com.leo.super_master_backend.apis.ml.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * skus + cuota. Los campos transitorios (categoría/atributos/descripción) solo se mandan
 * en la publicación desde el modal (1 SKU); en lote van null y el publish los omite.
 */
public record MlExportRequestDTO(
        List<String> skus,
        @NotNull(message = "La cuota es obligatoria") Integer cuotas,
        String mlCategoryId,
        List<MlAtributoDTO> mlAtributos,
        String descripcionMl) {}

package ar.com.leo.super_master_backend.dominio.producto.estado.dto;

import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDTO;

import java.util.List;

/** Campos editables leídos del canal para pre-llenar el modal (no persistidos). */
public record DatosCanalDTO(
        String mlCategoryId,
        String mlCategoryNombre,
        List<MlAtributoDTO> mlAtributos,
        String descripcionMl,
        String descripcionHogar,
        String descripcionGastro,
        SeoCanalDTO seoHogar,
        SeoCanalDTO seoGastro,
        /** Código MLA real resuelto por SKU contra la API de ML (publicación vigente); null si no hay. */
        String mlaResuelto
) {
}

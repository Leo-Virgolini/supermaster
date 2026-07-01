package ar.com.leo.super_master_backend.dominio.producto.estado.dto;

import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDTO;

import java.util.List;

/** Estado + datos editables de ML leídos del canal (no persistidos). */
public record MlCanalDTO(
        EstadoCanalDTO estado,
        String categoryId,
        String categoryNombre,
        List<MlAtributoDTO> atributos,
        String descripcion,
        String mlaResuelto,
        Double mlPaqAlto,
        Double mlPaqAncho,
        Double mlPaqLargo,
        Double mlPaqPeso,
        String titulo
) {}

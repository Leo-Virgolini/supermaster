package ar.com.leo.super_master_backend.apis.dux.dto;

import java.util.List;

public record ImportDuxResultDTO(
        int productosActualizados,
        int productosNoEncontrados,
        int proveedoresCreados,
        int totalProductosDux,
        List<String> skusNoEncontrados,
        List<String> errores
) {}

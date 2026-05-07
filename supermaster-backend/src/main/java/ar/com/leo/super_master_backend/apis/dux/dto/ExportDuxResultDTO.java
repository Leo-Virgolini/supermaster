package ar.com.leo.super_master_backend.apis.dux.dto;

import java.util.List;

public record ExportDuxResultDTO(
        int productosEnviados,
        int idProceso,
        List<String> errores
) {}

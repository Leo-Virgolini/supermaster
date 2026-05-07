package ar.com.leo.super_master_backend.apis.ml.dto;

import java.util.List;

public record CostoVentaMasivoResponseDTO(
        int totalProcesados,
        int exitosos,
        int errores,
        int omitidos,
        List<CostoVentaResponseDTO> resultados
) {}

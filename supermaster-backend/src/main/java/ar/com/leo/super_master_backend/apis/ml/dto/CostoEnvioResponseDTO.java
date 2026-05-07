package ar.com.leo.super_master_backend.apis.ml.dto;

import java.math.BigDecimal;

public record CostoEnvioResponseDTO(
        String mla,
        String status,
        BigDecimal precioConsultado,
        BigDecimal costoEnvioConIva,    // Costo de envío original (con IVA)
        BigDecimal costoEnvioSinIva,    // Costo de envío sin IVA (el que se guarda)
        String mensaje
) {}

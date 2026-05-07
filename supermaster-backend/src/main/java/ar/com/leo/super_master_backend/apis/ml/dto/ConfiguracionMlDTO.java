package ar.com.leo.super_master_backend.apis.ml.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ConfiguracionMlDTO(
        Integer id,

        @NotNull(message = "El umbral de envío gratis es requerido")
        @Positive(message = "El umbral de envío gratis debe ser positivo")
        BigDecimal umbralEnvioGratis,

        // Tiers de costo de envío por rangos de PVP
        @Positive(message = "El tier1Hasta debe ser positivo")
        BigDecimal tier1Hasta,

        @Positive(message = "El tier1Costo debe ser positivo")
        BigDecimal tier1Costo,

        @Positive(message = "El tier2Hasta debe ser positivo")
        BigDecimal tier2Hasta,

        @Positive(message = "El tier2Costo debe ser positivo")
        BigDecimal tier2Costo,

        @Positive(message = "El tier3Costo debe ser positivo")
        BigDecimal tier3Costo
) {}

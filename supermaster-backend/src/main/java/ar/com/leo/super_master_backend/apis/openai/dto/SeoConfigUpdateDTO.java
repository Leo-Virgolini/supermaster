package ar.com.leo.super_master_backend.apis.openai.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SeoConfigUpdateDTO(
        @NotBlank(message = "El prompt de KT Hogar es obligatorio") String promptHogar,
        @NotBlank(message = "El prompt de KT Gastro es obligatorio") String promptGastro,
        @NotBlank(message = "El modelo es obligatorio") String model,
        @NotNull(message = "El precio de input es obligatorio") @DecimalMin(value = "0.0", inclusive = false, message = "El precio de input debe ser mayor a 0") BigDecimal precioInput1m,
        @NotNull(message = "El precio de output es obligatorio") @DecimalMin(value = "0.0", inclusive = false, message = "El precio de output debe ser mayor a 0") BigDecimal precioOutput1m) {}

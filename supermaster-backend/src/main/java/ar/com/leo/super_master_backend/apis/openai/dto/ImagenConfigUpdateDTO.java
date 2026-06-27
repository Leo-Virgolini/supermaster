package ar.com.leo.super_master_backend.apis.openai.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record ImagenConfigUpdateDTO(
        @NotBlank(message = "El prompt es obligatorio") String contenido,
        @NotBlank(message = "El modelo es obligatorio") String model,
        @NotBlank(message = "El tamaño es obligatorio") @Pattern(regexp = "1024x1024|1024x1536|1536x1024|auto", message = "Tamaño inválido") String size,
        @NotBlank(message = "El formato es obligatorio") @Pattern(regexp = "png|jpeg|webp", message = "Formato inválido") String outputFormat,
        @NotBlank(message = "La calidad es obligatoria") @Pattern(regexp = "low|medium|high|auto", message = "Calidad inválida") String quality,
        @NotNull(message = "El precio de input es obligatorio") @DecimalMin(value = "0.0", inclusive = false, message = "El precio de input debe ser mayor a 0") BigDecimal precioInput1m,
        @NotNull(message = "El precio de output es obligatorio") @DecimalMin(value = "0.0", inclusive = false, message = "El precio de output debe ser mayor a 0") BigDecimal precioOutput1m) {}

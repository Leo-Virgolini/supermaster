package ar.com.leo.super_master_backend.dominio.catalogo.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CatalogoCreateDTO(
        @NotBlank(message = "El nombre del catálogo es obligatorio")
        @Size(max = 45, message = "El nombre del catálogo no puede exceder 45 caracteres")
        String nombre,
        Boolean exportarConIva,
        @DecimalMin(value = "0.0", inclusive = true, message = "El recargo porcentaje debe ser mayor o igual a 0")
        @DecimalMax(value = "100.0", inclusive = true, message = "El recargo porcentaje debe ser menor o igual a 100")
        BigDecimal recargoPorcentaje
) {}
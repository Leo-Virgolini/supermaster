package ar.com.leo.super_master_backend.dominio.proveedor.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProveedorCreateDTO(
        @NotBlank(message = "El nombre del proveedor es obligatorio")
        @Size(max = 100, message = "El nombre del proveedor no puede exceder 100 caracteres")
        String nombre,
        @NotBlank(message = "El apodo del proveedor es obligatorio")
        @Size(max = 50, message = "El apodo no puede exceder 50 caracteres")
        String apodo,
        @Size(max = 45, message = "El plazo de pago no puede exceder 45 caracteres")
        String plazoPago,
        Boolean entrega,
        @DecimalMin(value = "0.0", inclusive = true, message = "El porcentaje de financiación debe ser mayor o igual a 0")
        @DecimalMax(value = "100.0", inclusive = true, message = "El porcentaje de financiación debe ser menor o igual a 100")
        BigDecimal financiacionPorcentaje,
        @PositiveOrZero(message = "El lead time debe ser mayor o igual a 0")
        Integer leadTimeDias
) {
}
package ar.com.leo.super_master_backend.dominio.producto.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductoMargenDTO(
        Integer id,
        @NotNull(message = "El productoId es requerido")
        Integer productoId,
        @NotNull(message = "El margen minorista es requerido")
        @DecimalMin(value = "0.0", inclusive = true, message = "El margen minorista debe ser mayor o igual a 0")
        @DecimalMax(value = "999.999", inclusive = true, message = "El margen minorista debe ser menor o igual a 999.999")
        BigDecimal margenMinorista,
        @NotNull(message = "El margen mayorista es requerido")
        @DecimalMin(value = "0.0", inclusive = true, message = "El margen mayorista debe ser mayor o igual a 0")
        @DecimalMax(value = "999.999", inclusive = true, message = "El margen mayorista debe ser menor o igual a 999.999")
        BigDecimal margenMayorista,
        @PositiveOrZero(message = "El margen fijo minorista debe ser mayor o igual a 0")
        BigDecimal margenFijoMinorista,
        @PositiveOrZero(message = "El margen fijo mayorista debe ser mayor o igual a 0")
        BigDecimal margenFijoMayorista,
        @Size(max = 300)
        String observaciones
) {
}

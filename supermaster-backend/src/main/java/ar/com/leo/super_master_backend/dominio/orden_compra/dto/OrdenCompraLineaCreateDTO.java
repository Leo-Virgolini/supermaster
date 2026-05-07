package ar.com.leo.super_master_backend.dominio.orden_compra.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record OrdenCompraLineaCreateDTO(
        @NotNull(message = "El ID de producto es obligatorio")
        @Positive(message = "El ID de producto debe ser positivo")
        Integer productoId,

        @NotNull(message = "La cantidad pedida es obligatoria")
        @Positive(message = "La cantidad pedida debe ser mayor a 0")
        Integer cantidadPedida,

        @PositiveOrZero(message = "El costo unitario debe ser mayor o igual a 0")
        BigDecimal costoUnitario
) {
}

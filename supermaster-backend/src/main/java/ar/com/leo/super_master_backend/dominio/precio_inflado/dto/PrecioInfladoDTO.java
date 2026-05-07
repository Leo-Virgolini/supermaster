package ar.com.leo.super_master_backend.dominio.precio_inflado.dto;

import ar.com.leo.super_master_backend.dominio.precio_inflado.entity.TipoPrecioInflado;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PrecioInfladoDTO(
        Integer id,
        @NotNull(message = "El código es obligatorio")
        @Size(max = 20, message = "El código no puede exceder 20 caracteres")
        String codigo,
        @NotNull(message = "El tipo de precio inflado es obligatorio")
        TipoPrecioInflado tipo,
        @NotNull(message = "El valor es obligatorio")
        @PositiveOrZero(message = "El valor debe ser mayor o igual a 0")
        BigDecimal valor
) {
}

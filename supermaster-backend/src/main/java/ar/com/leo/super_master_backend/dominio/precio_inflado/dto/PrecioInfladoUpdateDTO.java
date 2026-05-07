package ar.com.leo.super_master_backend.dominio.precio_inflado.dto;

import ar.com.leo.super_master_backend.dominio.precio_inflado.entity.TipoPrecioInflado;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PrecioInfladoUpdateDTO(
        @Size(max = 20, message = "El código no puede exceder 20 caracteres")
        String codigo,
        TipoPrecioInflado tipo,
        @PositiveOrZero(message = "El valor debe ser mayor o igual a 0")
        BigDecimal valor
) {
}

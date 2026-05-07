package ar.com.leo.super_master_backend.dominio.regla_descuento.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ReglaDescuentoUpdateDTO(
        @Positive(message = "El ID de canal debe ser positivo")
        Integer canalId,
        @Positive(message = "El ID de catálogo debe ser positivo")
        Integer catalogoId,
        @Positive(message = "El ID de clasificación general debe ser positivo")
        Integer clasifGralId,
        @Positive(message = "El ID de clasificación gastro debe ser positivo")
        Integer clasifGastroId,
        @PositiveOrZero(message = "El monto mínimo debe ser mayor o igual a 0")
        BigDecimal montoMinimo,
        @DecimalMin(value = "0.0", inclusive = true, message = "El descuento porcentaje debe ser mayor o igual a 0")
        @DecimalMax(value = "100.0", inclusive = true, message = "El descuento porcentaje debe ser menor o igual a 100")
        BigDecimal descuentoPorcentaje,
        @PositiveOrZero(message = "La prioridad debe ser mayor o igual a 0")
        Integer prioridad,
        Boolean activo,
        @Size(max = 200)
        String descripcion
) {
}
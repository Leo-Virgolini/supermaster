package ar.com.leo.super_master_backend.dominio.canal.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CanalConceptoCuotaCreateDTO(
        @NotNull(message = "El ID del canal es obligatorio")
        @Positive(message = "El ID del canal debe ser positivo")
        Integer canalId,

        @NotNull(message = "El número de cuotas es obligatorio")
        @PositiveOrZero(message = "El número de cuotas debe ser mayor o igual a 0")
        Integer cuotas,

        @NotNull(message = "El porcentaje es obligatorio")
        @DecimalMin(value = "-100.0", inclusive = true, message = "El porcentaje debe ser mayor o igual a -100")
        @DecimalMax(value = "100.0", inclusive = true, message = "El porcentaje debe ser menor o igual a 100")
        BigDecimal porcentaje, // Positivo = interés (aumenta precio), Negativo = descuento (reduce precio)

        @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
        String descripcion
) {
}


package ar.com.leo.super_master_backend.dominio.orden_compra.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record RecepcionDTO(
        @NotEmpty(message = "Debe incluir al menos una línea de recepción")
        @Valid
        List<LineaRecepcionDTO> lineas
) {
    public record LineaRecepcionDTO(
            @NotNull(message = "El ID de línea es obligatorio")
            @Positive(message = "El ID de línea debe ser positivo")
            Integer lineaId,

            @NotNull(message = "La cantidad recibida es obligatoria")
            @PositiveOrZero(message = "La cantidad recibida debe ser mayor o igual a 0")
            Integer cantidadRecibida
    ) {
    }
}

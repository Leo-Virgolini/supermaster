package ar.com.leo.super_master_backend.dominio.producto.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ProductoCanalPrecioInfladoUpdateDTO(
        @Positive(message = "El ID de precio inflado debe ser positivo")
        Integer precioInfladoId,
        Boolean activo,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        @Size(max = 255, message = "Las observaciones no pueden exceder 255 caracteres")
        String observaciones
) {
}

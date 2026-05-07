package ar.com.leo.super_master_backend.dominio.producto.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ProductoCanalPrecioInfladoCreateDTO(
        @NotNull(message = "El ID de producto es obligatorio")
        @Positive(message = "El ID de producto debe ser positivo")
        Integer productoId,
        @NotNull(message = "El ID de canal es obligatorio")
        @Positive(message = "El ID de canal debe ser positivo")
        Integer canalId,
        @NotNull(message = "El ID de precio inflado es obligatorio")
        @Positive(message = "El ID de precio inflado debe ser positivo")
        Integer precioInfladoId,
        Boolean activo,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        @Size(max = 255, message = "Las observaciones no pueden exceder 255 caracteres")
        String observaciones
) {
}

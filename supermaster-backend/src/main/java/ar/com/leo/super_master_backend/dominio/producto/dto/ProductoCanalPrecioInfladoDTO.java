package ar.com.leo.super_master_backend.dominio.producto.dto;

import ar.com.leo.super_master_backend.dominio.precio_inflado.dto.PrecioInfladoDTO;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ProductoCanalPrecioInfladoDTO(
        Integer id,
        Integer productoId,
        Integer canalId,
        @NotNull(message = "El precio inflado es obligatorio")
        PrecioInfladoDTO precioInflado,
        Boolean activo,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        @Size(max = 255, message = "Las observaciones no pueden exceder 255 caracteres")
        String observaciones
) {
}

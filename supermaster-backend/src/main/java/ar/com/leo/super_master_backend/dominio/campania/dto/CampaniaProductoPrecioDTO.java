package ar.com.leo.super_master_backend.dominio.campania.dto;

import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CampaniaProductoPrecioDTO(
        @PositiveOrZero(message = "El precio debe ser mayor o igual a 0")
        BigDecimal precioManual
) {
}

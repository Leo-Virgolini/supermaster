package ar.com.leo.super_master_backend.dominio.producto.mla.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record MlaCreateDTO(
        @NotBlank(message = "El código MLA es obligatorio")
        @Size(max = 20, message = "El código MLA no puede exceder 20 caracteres")
        String mla,
        @Size(max = 20, message = "El código MLAU no puede exceder 20 caracteres")
        String mlau,
        @PositiveOrZero(message = "El precio de envío debe ser mayor o igual a 0")
        BigDecimal precioEnvio,
        @PositiveOrZero(message = "El tope de promoción debe ser mayor o igual a 0")
        Integer topePromocion
) {
}

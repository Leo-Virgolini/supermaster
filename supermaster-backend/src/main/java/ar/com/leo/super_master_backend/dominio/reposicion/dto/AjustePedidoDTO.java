package ar.com.leo.super_master_backend.dominio.reposicion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record AjustePedidoDTO(
        @NotEmpty(message = "Debe incluir al menos un ajuste")
        @Valid
        List<LineaAjusteDTO> ajustes
) {
    public record LineaAjusteDTO(
            @NotNull(message = "El ID de producto es obligatorio")
            @Positive(message = "El ID de producto debe ser positivo")
            Integer productoId,

            @NotNull(message = "La cantidad es obligatoria")
            @PositiveOrZero(message = "La cantidad debe ser mayor o igual a 0")
            Integer pedido
    ) {}
}

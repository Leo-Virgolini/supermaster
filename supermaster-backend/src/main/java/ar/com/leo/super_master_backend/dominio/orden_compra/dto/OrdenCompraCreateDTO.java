package ar.com.leo.super_master_backend.dominio.orden_compra.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrdenCompraCreateDTO(
        @NotNull(message = "El ID de proveedor es obligatorio")
        @Positive(message = "El ID de proveedor debe ser positivo")
        Integer proveedorId,

        @Size(max = 500, message = "Las observaciones no pueden exceder 500 caracteres")
        String observaciones,

        @NotEmpty(message = "Debe incluir al menos una línea")
        @Valid
        List<OrdenCompraLineaCreateDTO> lineas
) {
}

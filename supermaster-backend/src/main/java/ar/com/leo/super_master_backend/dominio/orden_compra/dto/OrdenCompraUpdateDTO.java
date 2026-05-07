package ar.com.leo.super_master_backend.dominio.orden_compra.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrdenCompraUpdateDTO(
        @Size(max = 500, message = "Las observaciones no pueden exceder 500 caracteres")
        String observaciones,

        @Valid
        List<OrdenCompraLineaCreateDTO> lineas
) {
}

package ar.com.leo.super_master_backend.dominio.cliente.dto;

import jakarta.validation.constraints.Size;

public record ClienteUpdateDTO(
        @Size(max = 45, message = "El nombre del cliente no puede exceder 45 caracteres")
        String nombre
) {
}
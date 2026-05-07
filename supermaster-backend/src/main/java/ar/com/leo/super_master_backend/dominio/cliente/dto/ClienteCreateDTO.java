package ar.com.leo.super_master_backend.dominio.cliente.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClienteCreateDTO(
        @NotBlank(message = "El nombre del cliente es obligatorio")
        @Size(max = 45, message = "El nombre del cliente no puede exceder 45 caracteres")
        String nombre
) {}
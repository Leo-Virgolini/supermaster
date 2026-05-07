package ar.com.leo.super_master_backend.dominio.origen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrigenCreateDTO(
        @NotBlank(message = "El nombre del origen es obligatorio")
        @Size(max = 45, message = "El nombre del origen no puede exceder 45 caracteres")
        String nombre
) {
}
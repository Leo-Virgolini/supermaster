package ar.com.leo.super_master_backend.dominio.material.dto;

import jakarta.validation.constraints.Size;

public record MaterialUpdateDTO(
        @Size(max = 45, message = "El nombre del material no puede exceder 45 caracteres")
        String nombre
) {
}
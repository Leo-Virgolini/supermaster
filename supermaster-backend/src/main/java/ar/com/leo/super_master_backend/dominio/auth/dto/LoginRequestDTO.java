package ar.com.leo.super_master_backend.dominio.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @NotBlank(message = "El username es requerido")
        String username,
        @NotBlank(message = "La contraseña es requerida")
        String password
) {}

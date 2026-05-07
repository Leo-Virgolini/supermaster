package ar.com.leo.super_master_backend.dominio.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambioPasswordDTO(
        @NotBlank(message = "La nueva contraseña es requerida")
        @Size(min = 6, max = 100, message = "La contraseña debe tener entre 6 y 100 caracteres")
        String nuevaPassword
) {}

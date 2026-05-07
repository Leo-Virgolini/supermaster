package ar.com.leo.super_master_backend.dominio.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UsuarioCreateDTO(
        @NotBlank(message = "El username es requerido")
        @Size(min = 3, max = 50, message = "El username debe tener entre 3 y 50 caracteres")
        String username,

        @NotBlank(message = "La contraseña es requerida")
        @Size(min = 6, max = 100, message = "La contraseña debe tener entre 6 y 100 caracteres")
        String password,

        @NotBlank(message = "El nombre completo es requerido")
        @Size(max = 150, message = "El nombre completo no puede superar 150 caracteres")
        String nombreCompleto,

        @NotNull(message = "El rol es requerido")
        @Positive(message = "El ID del rol debe ser positivo")
        Integer rolId
) {}

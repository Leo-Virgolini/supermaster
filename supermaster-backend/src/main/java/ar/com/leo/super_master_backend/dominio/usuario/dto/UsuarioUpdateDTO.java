package ar.com.leo.super_master_backend.dominio.usuario.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UsuarioUpdateDTO(
        @Size(max = 150, message = "El nombre completo no puede superar 150 caracteres")
        String nombreCompleto,

        Boolean activo,

        @Positive(message = "El ID del rol debe ser positivo")
        Integer rolId
) {}

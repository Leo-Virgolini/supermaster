package ar.com.leo.super_master_backend.dominio.tipo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TipoCreateDTO(
        @NotBlank(message = "El nombre del tipo es obligatorio")
        @Size(max = 45, message = "El nombre del tipo no puede exceder 45 caracteres")
        String nombre,
        @Positive(message = "El ID del tipo padre debe ser positivo")
        Integer padreId
) {
}
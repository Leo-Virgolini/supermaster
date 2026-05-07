package ar.com.leo.super_master_backend.dominio.marca.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MarcaCreateDTO(
        @NotBlank(message = "El nombre de la marca es obligatorio")
        @Size(max = 45, message = "El nombre de la marca no puede exceder 45 caracteres")
        String nombre,
        @Positive(message = "El ID de la marca padre debe ser positivo")
        Integer padreId
) {
}
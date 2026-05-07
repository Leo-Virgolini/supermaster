package ar.com.leo.super_master_backend.dominio.clasif_gral.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ClasifGralCreateDTO(
        @NotBlank(message = "El nombre de la clasificación general es obligatorio")
        @Size(max = 45, message = "El nombre no puede exceder 45 caracteres")
        String nombre,
        @Positive(message = "El ID del padre debe ser positivo")
        Integer padreId
) {}
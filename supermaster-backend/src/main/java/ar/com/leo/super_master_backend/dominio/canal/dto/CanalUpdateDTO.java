package ar.com.leo.super_master_backend.dominio.canal.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CanalUpdateDTO(
        @Size(max = 45, message = "El nombre del canal no puede exceder 45 caracteres")
        String nombre,
        @Positive(message = "El ID del canal base debe ser positivo")
        Integer canalBaseId
) {
}
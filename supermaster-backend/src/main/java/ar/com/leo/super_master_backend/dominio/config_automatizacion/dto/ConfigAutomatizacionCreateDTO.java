package ar.com.leo.super_master_backend.dominio.config_automatizacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfigAutomatizacionCreateDTO(
        @NotBlank(message = "La clave es obligatoria")
        @Size(max = 50, message = "La clave no puede exceder 50 caracteres")
        String clave,

        @NotBlank(message = "El valor es obligatorio")
        @Size(max = 100, message = "El valor no puede exceder 100 caracteres")
        String valor,

        @Size(max = 255, message = "La descripción no puede exceder 255 caracteres")
        String descripcion
) {}

package ar.com.leo.super_master_backend.dominio.segmento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SegmentoCreateDTO(
        @NotBlank(message = "El nombre del segmento es obligatorio")
        @Size(max = 45, message = "El nombre del segmento no puede exceder 45 caracteres")
        String nombre
) {}

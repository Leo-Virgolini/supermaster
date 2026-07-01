package ar.com.leo.super_master_backend.dominio.segmento.dto;

import jakarta.validation.constraints.Size;

public record SegmentoUpdateDTO(
        @Size(max = 45, message = "El nombre del segmento no puede exceder 45 caracteres")
        String nombre
) {
}

package ar.com.leo.super_master_backend.dominio.sector_deposito.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SectorDepositoUpdateDTO(
        @NotBlank(message = "El código del sector de depósito es obligatorio")
        @Size(max = 20, message = "El código del sector de depósito no puede exceder 20 caracteres")
        String codigo,

        Integer idDux
) {
}

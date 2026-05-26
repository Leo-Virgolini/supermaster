package ar.com.leo.super_master_backend.apis.dux.sincronizacion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DuxHorarioSyncDTO(
        @NotNull @Min(0) @Max(23) Integer hora,
        @NotNull @Min(0) @Max(59) Integer minuto
) {
}

package ar.com.leo.super_master_backend.dominio.campania.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CampaniaUpdateDTO(
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        Boolean activa,
        @Size(max = 255, message = "Las observaciones no pueden exceder 255 caracteres")
        String observaciones
) {
}

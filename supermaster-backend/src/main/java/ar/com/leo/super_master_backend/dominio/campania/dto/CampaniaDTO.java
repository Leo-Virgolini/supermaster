package ar.com.leo.super_master_backend.dominio.campania.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CampaniaDTO(
        Integer id,
        Long tnCategoriaId,
        String nombre,
        Integer canalId,
        String canalNombre,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        Boolean activa,
        LocalDateTime fechaUltimaSync,
        String observaciones,
        long cantidadProductos
) {
}

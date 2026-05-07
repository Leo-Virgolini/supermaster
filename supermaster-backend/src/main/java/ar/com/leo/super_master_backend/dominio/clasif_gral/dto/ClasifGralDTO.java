package ar.com.leo.super_master_backend.dominio.clasif_gral.dto;

public record ClasifGralDTO(
        Integer id,
        String nombre,
        Integer padreId
) {
}
package ar.com.leo.super_master_backend.dominio.clasif_gastro.dto;

public record ClasifGastroDTO(
        Integer id,
        String nombre,
        Boolean esMaquina,
        Integer padreId
) {
}
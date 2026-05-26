package ar.com.leo.super_master_backend.dominio.clasif_gastro.dto;

/**
 * @param nombreCompleto path completo de ancestros + nombre, ej. "ABUELO > PADRE > HIJO".
 */
public record ClasifGastroDTO(
        Integer id,
        String nombre,
        Boolean esMaquina,
        Integer padreId,
        String nombreCompleto
) {
}
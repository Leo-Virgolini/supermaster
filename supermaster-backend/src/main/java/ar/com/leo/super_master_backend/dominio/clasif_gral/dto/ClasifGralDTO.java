package ar.com.leo.super_master_backend.dominio.clasif_gral.dto;

/**
 * @param nombreCompleto path completo de ancestros + nombre, ej. "ABUELO > PADRE > HIJO".
 */
public record ClasifGralDTO(
        Integer id,
        String nombre,
        Integer padreId,
        String nombreCompleto
) {
}
package ar.com.leo.super_master_backend.dominio.marca.dto;

/**
 * @param nombreCompleto path completo de ancestros + nombre, ej. "ELECTRO > REFRIGERACIÓN > HELADERAS".
 *                       Útil para mostrar la jerarquía en selects/tooltips.
 */
public record MarcaDTO(Integer id, String nombre, String codigoDux, Integer padreId, String nombreCompleto) {
}

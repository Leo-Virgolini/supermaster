package ar.com.leo.super_master_backend.dominio.tipo.dto;

/**
 * @param nombreCompleto path completo de ancestros + nombre, ej. "ABUELO > PADRE > HIJO".
 */
public record TipoDTO(Integer id, String nombre, Integer padreId, String nombreCompleto) {
}

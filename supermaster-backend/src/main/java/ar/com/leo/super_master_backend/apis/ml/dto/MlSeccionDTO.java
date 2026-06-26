package ar.com.leo.super_master_backend.apis.ml.dto;

import java.util.List;

/**
 * Sección de la ficha técnica.
 *
 * @param id    "VARIANTE" | "PRINCIPALES" | "SECUNDARIAS"
 * @param label etiqueta a mostrar (ej. "Características principales")
 */
public record MlSeccionDTO(String id, String label, List<MlComponenteDTO> componentes) {
}

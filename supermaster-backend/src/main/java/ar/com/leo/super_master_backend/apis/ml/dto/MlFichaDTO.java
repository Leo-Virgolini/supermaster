package ar.com.leo.super_master_backend.apis.ml.dto;

import java.util.List;

/**
 * Ficha técnica de una categoría de ML, lista para el formulario.
 * Estructura derivada de {@code GET /categories/{id}/technical_specs/input}:
 * secciones (Variante / Principales / Secundarias) → componentes → atributos.
 */
public record MlFichaDTO(List<MlSeccionDTO> secciones) {
}

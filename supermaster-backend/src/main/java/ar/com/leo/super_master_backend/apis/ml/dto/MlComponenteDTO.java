package ar.com.leo.super_master_backend.apis.ml.dto;

import java.util.List;

/**
 * Componente de un grupo de la ficha técnica (un widget de ML).
 *
 * @param tipo            tipo de componente de ML: TEXT_INPUT, COMBO, NUMBER_UNIT_INPUT,
 *                        BOOLEAN_INPUT, COLOR_INPUT, LINKED_BY_CONNECTOR_INPUT, etc.
 * @param hint            texto de ayuda (de ui_config)
 * @param tooltip         tooltip (de ui_config)
 * @param example         ejemplo de valor (de ui_config)
 * @param allowCustomValue permite valor libre además de los sugeridos
 * @param allowFiltering  permite filtrar/buscar entre los valores
 */
public record MlComponenteDTO(
        String tipo,
        String label,
        String hint,
        String tooltip,
        String example,
        boolean allowCustomValue,
        boolean allowFiltering,
        List<MlAtributoDefDTO> atributos
) {
}

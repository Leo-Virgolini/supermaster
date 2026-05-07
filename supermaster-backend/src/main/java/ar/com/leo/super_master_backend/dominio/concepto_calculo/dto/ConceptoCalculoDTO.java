package ar.com.leo.super_master_backend.dominio.concepto_calculo.dto;

import java.math.BigDecimal;

public record ConceptoCalculoDTO(
        Integer id,
        String nombre,
        BigDecimal porcentaje,
        String aplicaSobre,
        String etapa,
        String naturaleza,
        String descripcion
) {
}

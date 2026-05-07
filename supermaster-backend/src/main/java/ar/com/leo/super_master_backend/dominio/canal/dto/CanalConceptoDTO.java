package ar.com.leo.super_master_backend.dominio.canal.dto;

import java.math.BigDecimal;

public record CanalConceptoDTO(
        Integer canalId,
        Integer conceptoId,
        String nombre,
        BigDecimal porcentaje,
        String aplicaSobre,
        String etapa,
        String naturaleza,
        String descripcion
) {
}
package ar.com.leo.super_master_backend.dominio.canal.dto;

import java.math.BigDecimal;

public record CanalConceptoCuotaDTO(
        Long id,
        Integer canalId,
        Integer cuotas,
        BigDecimal porcentaje,
        String descripcion
) {
}


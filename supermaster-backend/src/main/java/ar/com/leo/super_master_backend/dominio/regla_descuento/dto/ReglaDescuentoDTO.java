package ar.com.leo.super_master_backend.dominio.regla_descuento.dto;

import java.math.BigDecimal;

public record ReglaDescuentoDTO(
        Integer id,
        Integer canalId,
        Integer catalogoId,
        Integer clasifGralId,
        Integer clasifGastroId,
        BigDecimal montoMinimo,
        BigDecimal descuentoPorcentaje,
        Integer prioridad,
        Boolean activo,
        String descripcion
) {
}
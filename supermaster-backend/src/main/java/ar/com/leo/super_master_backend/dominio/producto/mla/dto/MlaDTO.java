package ar.com.leo.super_master_backend.dominio.producto.mla.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MlaDTO(
        Integer id,
        String mla,
        String mlau,
        BigDecimal precioEnvio,
        LocalDateTime fechaCalculoEnvio,
        BigDecimal comisionPorcentaje,
        LocalDateTime fechaCalculoComision,
        Integer topePromocion
) {}

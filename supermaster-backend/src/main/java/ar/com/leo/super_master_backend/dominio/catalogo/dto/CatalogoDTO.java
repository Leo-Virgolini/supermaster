package ar.com.leo.super_master_backend.dominio.catalogo.dto;

import java.math.BigDecimal;

public record CatalogoDTO(
        Integer id,
        String nombre,
        Boolean exportarConIva,
        BigDecimal recargoPorcentaje
) {
}
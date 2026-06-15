package ar.com.leo.super_master_backend.dominio.campania.dto;

import java.math.BigDecimal;

public record CampaniaProductoDTO(
        Integer id,
        Integer productoId,
        String sku,
        String descripcion,
        BigDecimal costo,
        BigDecimal precioManual
) {
}

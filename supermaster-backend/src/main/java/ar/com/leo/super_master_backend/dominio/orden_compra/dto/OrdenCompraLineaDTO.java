package ar.com.leo.super_master_backend.dominio.orden_compra.dto;

import java.math.BigDecimal;

public record OrdenCompraLineaDTO(
        Integer id,
        Integer productoId,
        String productoSku,
        String productoDescripcion,
        Integer cantidadPedida,
        Integer cantidadRecibida,
        BigDecimal costoUnitario
) {
}

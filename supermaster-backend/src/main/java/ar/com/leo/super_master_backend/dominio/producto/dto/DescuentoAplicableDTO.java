package ar.com.leo.super_master_backend.dominio.producto.dto;

import java.math.BigDecimal;

/**
 * DTO para mostrar un nivel de descuento aplicable según reglas de descuento del canal.
 */
public record DescuentoAplicableDTO(
        BigDecimal montoMinimo,
        BigDecimal descuentoPorcentaje,
        BigDecimal pvpConDescuento,
        BigDecimal costosVentaConDescuento,
        BigDecimal ingresoNetoConDescuento,
        BigDecimal gananciaConDescuento,
        BigDecimal margenSobreIngresoNetoConDescuento,
        BigDecimal margenSobrePvpConDescuento,
        BigDecimal markupConDescuento
) {}

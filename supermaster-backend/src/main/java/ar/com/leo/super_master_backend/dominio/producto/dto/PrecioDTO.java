package ar.com.leo.super_master_backend.dominio.producto.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO para un precio individual (sin información del canal).
 * Se usa dentro de CanalPreciosDTO para agrupar precios por canal.
 */
public record PrecioDTO(
        Integer cuotas,
        String descripcion,
        BigDecimal pvp,
        BigDecimal pvpInflado,
        BigDecimal costoProducto,
        BigDecimal costosVenta,
        BigDecimal ingresoNetoVendedor,
        BigDecimal ganancia,
        BigDecimal margenSobreIngresoNeto,
        BigDecimal margenSobrePvp,
        BigDecimal markupPorcentaje,
        LocalDateTime fechaUltimoCalculo,
        List<DescuentoAplicableDTO> descuentos,
        String precioInfladoCodigo,
        String precioInfladoTipo,
        BigDecimal precioInfladoValor
) {
    /**
     * Constructor sin descuentos ni inflado (compatibilidad hacia atrás)
     */
    public PrecioDTO(
            Integer cuotas,
            String descripcion,
            BigDecimal pvp,
            BigDecimal pvpInflado,
            BigDecimal costoProducto,
            BigDecimal costosVenta,
            BigDecimal ingresoNetoVendedor,
            BigDecimal ganancia,
            BigDecimal margenSobreIngresoNeto,
            BigDecimal margenSobrePvp,
            BigDecimal markupPorcentaje,
            LocalDateTime fechaUltimoCalculo
    ) {
        this(cuotas, descripcion, pvp, pvpInflado, costoProducto, costosVenta,
                ingresoNetoVendedor, ganancia, margenSobreIngresoNeto, margenSobrePvp,
                markupPorcentaje, fechaUltimoCalculo, null, null, null, null);
    }

    /**
     * Constructor con descuentos pero sin inflado (compatibilidad hacia atrás)
     */
    public PrecioDTO(
            Integer cuotas,
            String descripcion,
            BigDecimal pvp,
            BigDecimal pvpInflado,
            BigDecimal costoProducto,
            BigDecimal costosVenta,
            BigDecimal ingresoNetoVendedor,
            BigDecimal ganancia,
            BigDecimal margenSobreIngresoNeto,
            BigDecimal margenSobrePvp,
            BigDecimal markupPorcentaje,
            LocalDateTime fechaUltimoCalculo,
            List<DescuentoAplicableDTO> descuentos
    ) {
        this(cuotas, descripcion, pvp, pvpInflado, costoProducto, costosVenta,
                ingresoNetoVendedor, ganancia, margenSobreIngresoNeto, margenSobrePvp,
                markupPorcentaje, fechaUltimoCalculo, descuentos, null, null, null);
    }
}

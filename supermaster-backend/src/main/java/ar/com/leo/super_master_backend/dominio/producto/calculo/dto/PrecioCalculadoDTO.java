package ar.com.leo.super_master_backend.dominio.producto.calculo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PrecioCalculadoDTO(
        Integer canalId,
        String canalNombre,
        Integer cuotas,
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
}

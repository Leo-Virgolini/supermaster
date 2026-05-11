package ar.com.leo.super_master_backend.dominio.producto.calculo.dto;

import ar.com.leo.super_master_backend.dominio.producto.dto.DescuentoAplicableDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
        LocalDateTime fechaUltimoCalculo,
        /**
         * Descuentos aplicables al canal (reglas por monto mínimo de compra) con sus
         * indicadores recalculados sobre el PVP descontado. Null si el canal no tiene
         * reglas. Vacío si tiene reglas pero ninguna aplica al producto.
         */
        List<DescuentoAplicableDTO> descuentos
) {
}

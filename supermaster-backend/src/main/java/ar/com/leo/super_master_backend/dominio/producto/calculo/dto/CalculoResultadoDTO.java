package ar.com.leo.super_master_backend.dominio.producto.calculo.dto;

import ar.com.leo.super_master_backend.dominio.producto.dto.CanalPreciosDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO unificado para respuestas de cálculo de precios.
 * Puede representar:
 * - Cálculo de un producto en un canal específico
 * - Cálculo de un producto en todos sus canales
 * - Cálculo masivo de todos los productos
 */
public record CalculoResultadoDTO(
        Integer totalPreciosCalculados,
        LocalDateTime fechaCalculo,
        List<CanalPreciosDTO> canales,
        Integer productosIgnoradosSinCosto,
        Integer productosIgnoradosSinMargen,
        Integer errores,
        List<String> skusSinCosto,
        List<String> skusSinMargen,
        List<String> skusConErrores
) {
    /**
     * Crea resultado para un solo canal.
     */
    public static CalculoResultadoDTO of(CanalPreciosDTO canal) {
        return new CalculoResultadoDTO(
                canal.precios().size(),
                LocalDateTime.now(),
                List.of(canal),
                null, null, null, null, null, null
        );
    }

    /**
     * Crea resultado para múltiples canales.
     */
    public static CalculoResultadoDTO of(List<CanalPreciosDTO> canales) {
        int total = canales.stream().mapToInt(c -> c.precios().size()).sum();
        return new CalculoResultadoDTO(total, LocalDateTime.now(), canales, null, null, null, null, null, null);
    }

    /**
     * Crea resultado para cálculo masivo (sin detalle de canales).
     */
    public static CalculoResultadoDTO masivo(int total) {
        return new CalculoResultadoDTO(total, LocalDateTime.now(), null, null, null, null, null, null, null);
    }

    /**
     * Crea resultado para cálculo masivo con información completa de productos ignorados.
     */
    public static CalculoResultadoDTO masivo(RecalculoMasivoResultDTO resultado) {
        return new CalculoResultadoDTO(
                resultado.totalPreciosCalculados(),
                LocalDateTime.now(),
                null,
                resultado.productosIgnoradosSinCosto(),
                resultado.productosIgnoradosSinMargen(),
                resultado.errores(),
                resultado.skusSinCosto(),
                resultado.skusSinMargen(),
                resultado.skusConErrores()
        );
    }
}

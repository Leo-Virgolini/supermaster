package ar.com.leo.super_master_backend.dominio.producto.calculo.dto;

/**
 * Resultado completo de una simulación de precio: incluye la fórmula paso a paso
 * Y los indicadores calculados (PVP, ganancia, costos venta, márgenes, markup, etc.).
 *
 * Se devuelve desde POST /api/precios/simular para que la calculadora muestre
 * el mismo conjunto de métricas que el Monitor de Precios.
 */
public record SimulacionResultadoDTO(
        FormulaCalculoDTO formula,
        PrecioCalculadoDTO indicadores
) {
}

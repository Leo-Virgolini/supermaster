package ar.com.leo.super_master_backend.dominio.producto.calculo.dto;

import java.util.List;

/**
 * Resultado del recálculo masivo de precios.
 * Incluye contadores de productos procesados e ignorados, y los SKUs afectados.
 */
public record RecalculoMasivoResultDTO(
        int totalPreciosCalculados,
        int productosIgnoradosSinCosto,
        int productosIgnoradosSinMargen,
        int errores,
        List<String> skusSinCosto,
        List<String> skusSinMargen,
        // Desglose de los SKUs sin margen según el tipo que cada canal requiere usar
        List<String> skusSinMargenMayorista,
        List<String> skusSinMargenMinorista,
        List<String> skusConErrores
) {
}

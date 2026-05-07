package ar.com.leo.super_master_backend.dominio.producto.calculo.dto;

import java.math.BigDecimal;
import java.util.List;

public record FormulaCalculoDTO(
        String canalNombre,
        Integer cuotas,
        String descripcionCuotas,
        String formulaGeneral,
        List<PasoCalculo> pasos,
        BigDecimal resultadoFinal
) {
    public record PasoCalculo(
            Integer numeroPaso,
            String descripcion,
            String formula,
            BigDecimal valor,
            String detalle,
            String unidad
    ) {}

    public static final String UNIDAD_MONEDA = "moneda";
    public static final String UNIDAD_PORCENTAJE = "porcentaje";
    public static final String UNIDAD_FACTOR = "factor";
}

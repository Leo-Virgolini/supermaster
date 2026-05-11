package ar.com.leo.super_master_backend.dominio.concepto_calculo.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ConceptoCalculoCreateDTO(
        @NotBlank(message = "El nombre del concepto es obligatorio")
        @Size(max = 45, message = "El nombre del concepto no puede exceder 45 caracteres")
        String nombre,
        @DecimalMin(value = "-100.0", inclusive = true, message = "El porcentaje debe ser mayor o igual a -100")
        @DecimalMax(value = "100.0", inclusive = true, message = "El porcentaje debe ser menor o igual a 100")
        BigDecimal porcentaje,
        @NotBlank(message = "El campo aplicaSobre es obligatorio")
        @Pattern(regexp = "GASTO_SOBRE_COSTO|FLAG_FINANCIACION_PROVEEDOR|AJUSTE_MARGEN_PUNTOS|AJUSTE_MARGEN_PROPORCIONAL|FLAG_USAR_MARGEN_MINORISTA|FLAG_USAR_MARGEN_MAYORISTA|GASTO_POST_GANANCIA|FLAG_APLICAR_IVA|IMPUESTO_EN_FACTOR_IMP|GASTO_POST_IMPUESTOS|FLAG_INCLUIR_ENVIO|COMISION_SOBRE_PVP|FLAG_COMISION_ML|CALCULO_SOBRE_CANAL_BASE|CALCULO_SOBRE_CANAL_BASE_RESELLER|COSTO_OCULTO_PVP|DESCUENTO_PORCENTUAL|INFLACION_DIVISOR_FINAL|GASTO_SIN_INFLAR_PVP|FLAG_APLICAR_PRECIO_INFLADO",
                message = "aplicaSobre debe ser uno de los valores válidos del enum AplicaSobre")
        String aplicaSobre,
        @Pattern(regexp = "COSTO_PRODUCTO|COSTO_VENTA|IMPUESTO|MARKUP|INFLACION|DESCUENTO|BASE|COSMETICO",
                message = "naturaleza debe ser uno de los valores válidos del enum NaturalezaConcepto")
        String naturaleza,
        @Size(max = 255, message = "La descripción no puede exceder 255 caracteres")
        String descripcion
) {
}

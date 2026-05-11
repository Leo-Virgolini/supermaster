package ar.com.leo.super_master_backend.dominio.producto.calculo.dto;

import ar.com.leo.super_master_backend.dominio.precio_inflado.entity.TipoPrecioInflado;
import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Input para simular el cálculo de precio de un producto hipotético contra un canal.
 * No requiere un producto persistido: el motor de cálculo se ejecuta sobre entidades
 * transitorias construidas a partir de estos atributos.
 *
 * Campos obligatorios: canalId, costo, iva, margenMinorista, margenMayorista.
 * El resto son opcionales para acotar qué reglas de excepción se evalúan y qué
 * conceptos especiales (financiación, MLA) entran en el cálculo.
 */
public record SimulacionPrecioInputDTO(
        @NotNull(message = "canalId es requerido")
        @Positive(message = "canalId debe ser positivo")
        Integer canalId,

        // Cuotas: -1=transferencia, 0=contado, >0=cuotas. null = sin cuotas.
        Integer cuotas,

        // ---------- Atributos del producto hipotético ----------
        @NotNull(message = "costo es requerido")
        @PositiveOrZero(message = "costo no puede ser negativo")
        BigDecimal costo,

        @NotNull(message = "iva es requerido")
        @PositiveOrZero(message = "iva no puede ser negativo")
        BigDecimal iva,

        // Clasificaciones (para evaluar reglas de excepción)
        Integer marcaId,
        Integer tipoId,
        Integer clasifGralId,
        Integer clasifGastroId,
        Boolean esMaquina,    // override de clasifGastro.esMaquina
        Tag tag,

        // Financiación de proveedor (para FLAG_FINANCIACION_PROVEEDOR)
        BigDecimal proveedorFinanciacionPorcentaje,

        // Datos de MLA (para FLAG_INCLUIR_ENVIO, FLAG_COMISION_ML)
        BigDecimal mlaPrecioEnvio,
        BigDecimal mlaComisionPorcentaje,

        // ---------- Márgenes del producto hipotético ----------
        @NotNull(message = "margenMinorista es requerido")
        @PositiveOrZero(message = "margenMinorista no puede ser negativo")
        BigDecimal margenMinorista,

        @NotNull(message = "margenMayorista es requerido")
        @PositiveOrZero(message = "margenMayorista no puede ser negativo")
        BigDecimal margenMayorista,

        BigDecimal margenFijoMinorista,
        BigDecimal margenFijoMayorista,

        // ---------- Precio Inflado simulado ----------
        // Si ambos están seteados Y el canal tiene FLAG_APLICAR_PRECIO_INFLADO,
        // se calcula el pvpInflado en la respuesta. Sirve para previsualizar el
        // precio tachado de un producto hipotético o para overridear el de uno real.
        TipoPrecioInflado precioInfladoTipo,
        BigDecimal precioInfladoValor
) {
}

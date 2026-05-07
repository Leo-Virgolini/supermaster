package ar.com.leo.super_master_backend.dominio.precio_inflado.entity;

/**
 * Enum que define el tipo de precio inflado en la tabla precios_inflados.
 *
 * - MULTIPLICADOR: Multiplica el precio por el valor (ej: valor = 1.1 multiplica por 1.1)
 * - DESCUENTO_PORC: Descuento/Incremento porcentual según fórmula Excel: PVP / (1 - PROMO)
 *   Ejemplo: valor = 30 significa PVP / (1 - 0.30) = PVP / 0.70 (incrementa el precio)
 * - DIVISOR: Divide el precio por el valor (ej: valor = 0.9 divide por 0.9, equivalente a multiplicar por 1.11)
 * - PRECIO_FIJO: Establece un precio fijo (ej: valor = 100 significa precio fijo de $100)
 */
public enum TipoPrecioInflado {
    MULTIPLICADOR,
    DESCUENTO_PORC,
    DIVISOR,
    PRECIO_FIJO
}

package ar.com.leo.super_master_backend.dominio.concepto_calculo.entity;

/**
 * Naturaleza contable de un concepto. Independiente del {@link AplicaSobre},
 * que define la posición y operación matemática en la fórmula del PVP.
 * <p>
 * Esta clasificación determina cómo el concepto impacta los INDICADORES
 * (ganancia, márgenes, markup) — no el cálculo del PVP.
 * <p>
 * Cada {@link AplicaSobre} tiene una naturaleza por defecto
 * (ver {@link AplicaSobre#getNaturalezaDefault()}). Los conceptos pueden
 * sobreescribir ese default si la realidad contable lo requiere.
 * Caso típico: dos conceptos con el mismo {@code aplicaSobre} pueden ser
 * uno {@code COSTO_VENTA} (plata real) y otro {@code INFLACION} (markup
 * ficticio que no sale del bolsillo del dueño).
 */
public enum NaturalezaConcepto {

    /**
     * Forma parte del costo del producto (ej: financiación del proveedor).
     * Se suma al {@code costoProductoMetrica} → reduce markup pero no aparece
     * como costo de venta separado.
     */
    COSTO_PRODUCTO,

    /**
     * Plata real que sale del negocio al vender (comisiones, fletes, marketing
     * real, gastos del dueño que no se trasladan al PVP).
     * Se suma a {@code costosVenta} → reduce {@code ingresoNetoVendedor} y
     * por ende {@code ganancia}.
     */
    COSTO_VENTA,

    /**
     * Impuesto que se le paga al estado (IVA, IIBB).
     * Se extrae del PVP para cálculo separado y se resta del ingreso neto.
     */
    IMPUESTO,

    /**
     * Define el porcentaje de ganancia objetivo o lo ajusta (margen minorista,
     * mayorista, ajustes de margen). No es ni costo ni ganancia — es la base
     * que determina cuánta ganancia se persigue.
     */
    MARKUP,

    /**
     * Inflación pura: sube el PVP sin ser plata que sale del negocio.
     * El cliente paga el sobreprecio y el dueño se lo queda como ganancia.
     * Caso de uso: precio tachado de marketing, "markup ficticio".
     */
    INFLACION,

    /**
     * Reduce el PVP final (descuento porcentual). No es plata extra que salga,
     * solo rebaja el precio. No se cuenta como costo de venta.
     */
    DESCUENTO,

    /**
     * Cambia el punto de partida del cálculo (toma PVP de canal base en lugar
     * del costo del producto). Caso especial — no encaja en las otras categorías.
     */
    BASE,

    /**
     * Solo afecta el precio mostrado/tachado (display), no afecta el PVP que
     * paga el cliente ni los indicadores. Caso típico: FLAG_APLICAR_PRECIO_INFLADO.
     */
    COSMETICO
}

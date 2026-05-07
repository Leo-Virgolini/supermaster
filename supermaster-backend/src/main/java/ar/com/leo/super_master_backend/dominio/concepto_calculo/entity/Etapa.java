package ar.com.leo.super_master_backend.dominio.concepto_calculo.entity;

/**
 * Etapas del cálculo de precio. Cada {@link AplicaSobre} pertenece a una etapa
 * (ver {@link AplicaSobre#getEtapa()}). El front las usa para agrupar conceptos
 * en la vista "Fórmula del Canal".
 */
public enum Etapa {
    COSTO,
    MARGEN,
    IMPUESTOS,
    PRECIO,
    POST_PRECIO
}

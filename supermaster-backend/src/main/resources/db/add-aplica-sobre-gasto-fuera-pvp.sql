-- =============================================================
-- Migracion: agregar los valores 'GASTO_FUERA_PVP' e
-- 'INFLACION_SOBRE_PVP' al ENUM de conceptos_calculo.aplica_sobre.
--
--   * GASTO_FUERA_PVP: cuenta como costo del dueno pero NO se
--     traslada al PVP (ej: KG_COMVEND comision interna del vendedor
--     en KT GASTRO MAQUINA).
--
--   * INFLACION_SOBRE_PVP: SUMA al mismo divisor que COMISION_SOBRE_PVP
--     (mismo bucket de "gastos sobre PVP"), pero NO se cuenta como
--     costo del dueno. Variante con porcentaje propio de FLAG_INFLACION_ML
--     (que toma el % del MLA). Ejemplo: KG_MAQ_EMB en KT GASTRO.
--
-- Ejecutar UNA SOLA VEZ antes de correr gastro-conceptos.sql.
--
-- IDEMPOTENTE: ALTER TABLE MODIFY siempre redefine el ENUM completo.
-- Re-ejecutar es seguro.
-- =============================================================

USE supermaster;

ALTER TABLE conceptos_calculo
  MODIFY COLUMN aplica_sobre ENUM(
    'GASTO_SOBRE_COSTO',
    'FLAG_FINANCIACION_PROVEEDOR',
    'AJUSTE_MARGEN_PUNTOS',
    'AJUSTE_MARGEN_PROPORCIONAL',
    'FLAG_USAR_MARGEN_MINORISTA',
    'FLAG_USAR_MARGEN_MAYORISTA',
    'GASTO_POST_GANANCIA',
    'FLAG_APLICAR_IVA',
    'IMPUESTO_ADICIONAL',
    'GASTO_POST_IMPUESTOS',
    'FLAG_INCLUIR_ENVIO',
    'COMISION_SOBRE_PVP',
    'FLAG_COMISION_ML',
    'FLAG_INFLACION_ML',
    'INFLACION_SOBRE_PVP',
    'CALCULO_SOBRE_CANAL_BASE',
    'CALCULO_SOBRE_CANAL_BASE_RESELLER',
    'RECARGO_CUPON',
    'DESCUENTO_PORCENTUAL',
    'INFLACION_DIVISOR',
    'GASTO_FUERA_PVP',
    'FLAG_APLICAR_PRECIO_INFLADO'
  ) NOT NULL;

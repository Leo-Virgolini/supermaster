-- =============================================================
-- Migracion: agregar el valor 'GASTO_SIN_INFLAR_PVP' al ENUM de
-- conceptos_calculo.aplica_sobre.
--
--   * GASTO_SIN_INFLAR_PVP: cuenta como costo del dueno pero NO se
--     traslada al PVP (ej: KG_COMVEND comision interna del vendedor
--     en KT GASTRO MAQUINA).
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
    'IMPUESTO_EN_FACTOR_IMP',
    'GASTO_POST_IMPUESTOS',
    'FLAG_INCLUIR_ENVIO',
    'COMISION_SOBRE_PVP',
    'FLAG_COMISION_ML',
    'CALCULO_SOBRE_CANAL_BASE',
    'CALCULO_SOBRE_CANAL_BASE_RESELLER',
    'COSTO_OCULTO_PVP',
    'DESCUENTO_PORCENTUAL',
    'INFLACION_DIVISOR_FINAL',
    'GASTO_SIN_INFLAR_PVP',
    'FLAG_APLICAR_PRECIO_INFLADO'
  ) NOT NULL;

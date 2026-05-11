-- =============================================================
-- Migracion: unificar FLAG_INFLACION_ML en FLAG_COMISION_ML
--
-- Antes: dos flags separados con la misma matematica (PVP / (1 - mla.comision/100))
-- que solo diferian en si se contaban como costo de venta o no:
--   - FLAG_COMISION_ML   -> naturaleza default COSTO_VENTA (es costo)
--   - FLAG_INFLACION_ML  -> naturaleza default INFLACION   (NO es costo)
--
-- Ahora: un solo flag FLAG_COMISION_ML. La distincion la hace la columna
-- `naturaleza` del concepto (override del default del aplica_sobre).
--
-- Migracion:
--   1. Extender el ENUM para que ambos valores convivan (idempotente).
--   2. Forzar naturaleza='INFLACION' a los conceptos que tenian FLAG_INFLACION_ML
--      (asi conservan su comportamiento de NO contarse como costo).
--   3. Reemplazar FLAG_INFLACION_ML por FLAG_COMISION_ML en esos conceptos.
--   4. Achicar el ENUM eliminando FLAG_INFLACION_ML.
--
-- Reversible: NO. Hacer backup antes.
-- Pre-requisito: haber corrido antes rename-aplica-sobre-valores.sql.
-- =============================================================

USE supermaster;

-- -------------------------------------------------------------
-- PASO 1: Extender el ENUM (mantener ambos valores temporalmente)
-- -------------------------------------------------------------
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
    'FLAG_INFLACION_ML',
    'INFLACION_BUCKET_PVP',
    'CALCULO_SOBRE_CANAL_BASE',
    'CALCULO_SOBRE_CANAL_BASE_RESELLER',
    'COSTO_OCULTO_PVP',
    'DESCUENTO_PORCENTUAL',
    'INFLACION_DIVISOR_FINAL',
    'GASTO_SIN_INFLAR_PVP',
    'FLAG_APLICAR_PRECIO_INFLADO'
  ) NOT NULL;

-- -------------------------------------------------------------
-- PASO 2: Forzar naturaleza=INFLACION a los conceptos con FLAG_INFLACION_ML
-- (asi conservan que NO se cuenten como costo de venta tras el merge).
-- -------------------------------------------------------------
UPDATE conceptos_calculo
   SET naturaleza = 'INFLACION'
 WHERE aplica_sobre = 'FLAG_INFLACION_ML';

-- -------------------------------------------------------------
-- PASO 3: Migrar FLAG_INFLACION_ML -> FLAG_COMISION_ML
-- -------------------------------------------------------------
UPDATE conceptos_calculo
   SET aplica_sobre = 'FLAG_COMISION_ML'
 WHERE aplica_sobre = 'FLAG_INFLACION_ML';

-- -------------------------------------------------------------
-- PASO 4: Achicar el ENUM (eliminar FLAG_INFLACION_ML)
-- -------------------------------------------------------------
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
    'INFLACION_BUCKET_PVP',
    'CALCULO_SOBRE_CANAL_BASE',
    'CALCULO_SOBRE_CANAL_BASE_RESELLER',
    'COSTO_OCULTO_PVP',
    'DESCUENTO_PORCENTUAL',
    'INFLACION_DIVISOR_FINAL',
    'GASTO_SIN_INFLAR_PVP',
    'FLAG_APLICAR_PRECIO_INFLADO'
  ) NOT NULL;

-- -------------------------------------------------------------
-- VERIFICACION (opcional)
--
--   -- No deben quedar conceptos con FLAG_INFLACION_ML:
--   SELECT COUNT(*) FROM conceptos_calculo WHERE aplica_sobre = 'FLAG_INFLACION_ML';
--
--   -- Los conceptos que antes eran FLAG_INFLACION_ML ahora deben ser FLAG_COMISION_ML
--   -- con naturaleza='INFLACION' (verificar nombre, ej: KG_INFLA_ML):
--   SELECT nombre, aplica_sobre, naturaleza
--     FROM conceptos_calculo
--    WHERE aplica_sobre = 'FLAG_COMISION_ML';
-- =============================================================

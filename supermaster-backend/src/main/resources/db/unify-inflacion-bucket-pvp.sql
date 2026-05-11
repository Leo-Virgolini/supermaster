-- =============================================================
-- Migracion: unificar INFLACION_BUCKET_PVP en COMISION_SOBRE_PVP
--
-- Antes: dos valores separados con la misma matematica (PVP / (1 - %/100),
-- mismo bucket de divisor) que solo diferian en la naturaleza default:
--   - COMISION_SOBRE_PVP    -> naturaleza default COSTO_VENTA (es costo)
--   - INFLACION_BUCKET_PVP  -> naturaleza default INFLACION   (NO es costo)
--
-- Ahora: un solo valor COMISION_SOBRE_PVP. La distincion la hace la columna
-- `naturaleza` del concepto (override del default del aplica_sobre). Es el
-- mismo patron que la unificacion previa FLAG_INFLACION_ML -> FLAG_COMISION_ML.
--
-- Migracion:
--   1. Extender el ENUM para que ambos valores convivan (idempotente).
--   2. Forzar naturaleza='INFLACION' a los conceptos que tenian INFLACION_BUCKET_PVP
--      (asi conservan su comportamiento de NO contarse como costo).
--   3. Reemplazar INFLACION_BUCKET_PVP por COMISION_SOBRE_PVP en esos conceptos.
--   4. Achicar el ENUM eliminando INFLACION_BUCKET_PVP.
--
-- Reversible: NO. Hacer backup antes.
-- Pre-requisitos:
--   - rename-aplica-sobre-valores.sql
--   - unify-flag-inflacion-ml.sql
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
-- PASO 2: Forzar naturaleza=INFLACION a los conceptos con INFLACION_BUCKET_PVP
-- (asi conservan que NO se cuenten como costo de venta tras el merge).
-- -------------------------------------------------------------
UPDATE conceptos_calculo
   SET naturaleza = 'INFLACION'
 WHERE aplica_sobre = 'INFLACION_BUCKET_PVP';

-- -------------------------------------------------------------
-- PASO 3: Migrar INFLACION_BUCKET_PVP -> COMISION_SOBRE_PVP
-- -------------------------------------------------------------
UPDATE conceptos_calculo
   SET aplica_sobre = 'COMISION_SOBRE_PVP'
 WHERE aplica_sobre = 'INFLACION_BUCKET_PVP';

-- -------------------------------------------------------------
-- PASO 4: Achicar el ENUM (eliminar INFLACION_BUCKET_PVP)
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
--   -- No deben quedar conceptos con INFLACION_BUCKET_PVP:
--   SELECT COUNT(*) FROM conceptos_calculo WHERE aplica_sobre = 'INFLACION_BUCKET_PVP';
--
--   -- Los conceptos COMISION_SOBRE_PVP que tienen naturaleza='INFLACION' ahora
--   -- son los que antes eran INFLACION_BUCKET_PVP (matematica identica, pero
--   -- la naturaleza marca que NO cuentan como costo de venta):
--   SELECT nombre, aplica_sobre, naturaleza
--     FROM conceptos_calculo
--    WHERE aplica_sobre = 'COMISION_SOBRE_PVP'
--    ORDER BY naturaleza, nombre;
-- =============================================================

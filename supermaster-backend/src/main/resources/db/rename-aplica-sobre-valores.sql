-- =============================================================
-- Migracion: renombrar valores del ENUM aplica_sobre por nombres
-- mas claros que reflejan el uso real.
--
-- Renombres:
--   RECARGO_CUPON       -> COSTO_OCULTO_PVP        (uso real: retencion adicional de la plataforma)
--   INFLACION_SOBRE_PVP -> INFLACION_BUCKET_PVP    (suma al bucket de comisiones)
--   INFLACION_DIVISOR   -> INFLACION_DIVISOR_FINAL (bucket separado al final del calculo)
--   IMPUESTO_ADICIONAL  -> IMPUESTO_EN_FACTOR_IMP  (se suma al factor IMP junto al IVA)
--   GASTO_FUERA_PVP     -> GASTO_SIN_INFLAR_PVP    (cuenta como costo pero NO infla el PVP)
--
-- Estrategia (3 pasos, en una sola transaccion):
--   1. Extender el ENUM agregando los nombres NUEVOS (mantiene los viejos).
--   2. UPDATE de los valores viejos -> nuevos.
--   3. Achicar el ENUM eliminando los nombres viejos.
--
-- Reversible: NO. Hacer backup antes.
-- IDEMPOTENTE: si los valores viejos no existen, el UPDATE no afecta filas
-- y los ALTER simplemente quedan en la lista final.
-- =============================================================

USE supermaster;

-- -------------------------------------------------------------
-- PASO 1: Extender el ENUM (agregar nuevos sin eliminar viejos)
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
    'IMPUESTO_ADICIONAL',
    'IMPUESTO_EN_FACTOR_IMP',
    'GASTO_POST_IMPUESTOS',
    'FLAG_INCLUIR_ENVIO',
    'COMISION_SOBRE_PVP',
    'FLAG_COMISION_ML',
    'FLAG_INFLACION_ML',
    'INFLACION_SOBRE_PVP',
    'INFLACION_BUCKET_PVP',
    'CALCULO_SOBRE_CANAL_BASE',
    'CALCULO_SOBRE_CANAL_BASE_RESELLER',
    'RECARGO_CUPON',
    'COSTO_OCULTO_PVP',
    'DESCUENTO_PORCENTUAL',
    'INFLACION_DIVISOR',
    'INFLACION_DIVISOR_FINAL',
    'GASTO_FUERA_PVP',
    'GASTO_SIN_INFLAR_PVP',
    'FLAG_APLICAR_PRECIO_INFLADO'
  ) NOT NULL;

-- -------------------------------------------------------------
-- PASO 2: Migrar los valores existentes
-- -------------------------------------------------------------
UPDATE conceptos_calculo SET aplica_sobre = 'COSTO_OCULTO_PVP'        WHERE aplica_sobre = 'RECARGO_CUPON';
UPDATE conceptos_calculo SET aplica_sobre = 'INFLACION_BUCKET_PVP'    WHERE aplica_sobre = 'INFLACION_SOBRE_PVP';
UPDATE conceptos_calculo SET aplica_sobre = 'INFLACION_DIVISOR_FINAL' WHERE aplica_sobre = 'INFLACION_DIVISOR';
UPDATE conceptos_calculo SET aplica_sobre = 'IMPUESTO_EN_FACTOR_IMP'  WHERE aplica_sobre = 'IMPUESTO_ADICIONAL';
UPDATE conceptos_calculo SET aplica_sobre = 'GASTO_SIN_INFLAR_PVP'    WHERE aplica_sobre = 'GASTO_FUERA_PVP';

-- -------------------------------------------------------------
-- PASO 3: Achicar el ENUM (eliminar los nombres viejos)
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
-- VERIFICACION (opcional)
--
--   SELECT aplica_sobre, COUNT(*) FROM conceptos_calculo GROUP BY aplica_sobre ORDER BY aplica_sobre;
--
-- No deben aparecer los valores viejos (RECARGO_CUPON, INFLACION_SOBRE_PVP,
-- INFLACION_DIVISOR, IMPUESTO_ADICIONAL, GASTO_FUERA_PVP).
-- =============================================================

-- =============================================================
-- Carga de ConceptoCalculo, CanalConcepto y CanalConceptoRegla
-- para el canal LINEA GE (mayorista) segun la formula del Excel.
--
-- Formula de referencia:
--   PVP = LG-CF * (1 + %GAN) * (1 + LGELOG + LGEMKT) / (1 - LGEFIN) / (1 - LGEINF)
--
-- Mapeo fuente (Excel) -> modelo backend:
--   %GAN     -> FLAG_USAR_MARGEN_MAYORISTA  (MARGEN_MAY, canonico; aqui se crea por primera vez)
--   LGELOG   -> GASTO_POST_GANANCIA         (LG_LOG)
--   LGEMKT   -> GASTO_POST_GANANCIA         (LG_MKT)
--   LGEFIN   -> COMISION_SOBRE_PVP          (LG_FIN, bucket 1: divisor)
--   LGEINF   -> INFLACION_DIVISOR_FINAL     (LG_INF, bucket 3: divisor final)
--
-- Verificacion del orden de cuentas en el calculador:
--   1. costoConGanancia = costoBase * (1 + margen)
--   2. costoConGanancia *= (1 + Sigma GASTO_POST_GANANCIA / 100)
--   3. (sin IMP - LINEA GE NO aplica IVA ni IIBB, es precio mayorista neto)
--   4. / (1 - Sigma COMISION_SOBRE_PVP / 100)
--   5. / (1 - Sigma INFLACION_DIVISOR_FINAL / 100)
--   Coincide exacto con la formula del Excel.
--
-- IMPORTANTE:
--   * Los porcentajes son PLACEHOLDERS. Ajustar con los valores reales del Excel.
--   * LINEA GE NO incluye IVA ni IIBB (precio mayorista neto).
--   * No tiene reglas por TAG: la formula aplica igual a todos los productos.
--
-- PRERREQUISITO:
--   El canal "LINEA GE" debe existir en la tabla canales.
-- =============================================================

USE supermaster;

-- -------------------------------------------------------------
-- 1) ConceptoCalculo (todos nuevos, propios de LINEA GE)
-- -------------------------------------------------------------
-- LG_LOG y LG_MKT son GASTO_POST_GANANCIA (matemática: amplifica costo+ganancia),
-- pero son GASTOS REALES del dueño (logística al transportista, publicidad).
-- Por eso van con naturaleza COSTO_VENTA explícita (no la default INFLACION del enum).
INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, naturaleza, descripcion) VALUES
  -- Canonico nuevo (FLAG, sin prefijo de canal): primera carga
  ('MARGEN_MAY',    NULL,  'FLAG_USAR_MARGEN_MAYORISTA', NULL,         'Usa margen mayorista del producto (%GAN)'),
  -- Conceptos con porcentaje propio del canal LINEA GE
  ('LG_LOG',        5.000, 'GASTO_POST_GANANCIA',        'COSTO_VENTA', 'Logistica LINEA GE: gasto real del dueno (transportista). Amplifica costo+ganancia (LGELOG).'),
  ('LG_MKT',        3.000, 'GASTO_POST_GANANCIA',        'COSTO_VENTA', 'Marketing LINEA GE: gasto real del dueno (publicidad). Amplifica costo+ganancia (LGEMKT).'),
  ('LG_FIN',        5.000, 'COMISION_SOBRE_PVP',         NULL,         'Costo financiero LINEA GE (intereses por pago diferido / medio de pago). Divisor que infla PVP y se cuenta como costo del dueno.'),
  ('LG_INF',        8.000, 'INFLACION_DIVISOR_FINAL',    NULL,         'Inflacion cosmetica del PVP LINEA GE. Divisor separado: el cliente paga el sobrecargo y queda como ganancia del dueno (no es costo).');

-- -------------------------------------------------------------
-- 2) Asignar conceptos al canal LINEA GE (canal_concepto)
-- -------------------------------------------------------------
INSERT INTO canal_concepto (id_canal, id_concepto)
SELECT c.id_canal, cc.id_concepto
FROM canales c
CROSS JOIN conceptos_calculo cc
WHERE c.nombre = 'LINEA GE'
  AND cc.nombre IN (
    'MARGEN_MAY',
    'LG_LOG', 'LG_MKT',
    'LG_FIN',
    'LG_INF'
  );

-- -------------------------------------------------------------
-- 3) Reglas por TAG (canal_concepto_regla)
--
--    La formula LINEA GE no tiene condiciones por tag a nivel concepto.
--    Esta seccion queda vacia.
-- -------------------------------------------------------------

-- -------------------------------------------------------------
-- 4) Cuotas del canal LINEA GE (canal_concepto_cuota) - OPCIONAL
--
--    Si LINEA GE ofrece cuotas con recargo, cargarlas aqui (ajustar):
--
--   INSERT INTO canal_concepto_cuota (id_canal, cuotas, porcentaje, descripcion) VALUES
--     ( (SELECT id_canal FROM canales WHERE nombre='LINEA GE'),  3, 10.00, 'LG_3C'),
--     ( (SELECT id_canal FROM canales WHERE nombre='LINEA GE'),  6, 18.00, 'LG_6C');
-- -------------------------------------------------------------

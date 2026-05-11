-- =============================================================
-- Carga de ConceptoCalculo, CanalConcepto y CanalConceptoRegla
-- para el canal ML (MercadoLibre) segun la formula nueva del Excel.
--
-- Mapeo fuente (Excel) -> modelo backend:
--   GAN.MIN.ML         -> FLAG_USAR_MARGEN_MINORISTA (MARGEN_MIN, canonico compartido)
--   ENVIO              -> FLAG_INCLUIR_ENVIO         (ENVIO_ML, canonico compartido)
--   IMP (1 + IVA + IIBB) -> FLAG_APLICAR_IVA (IVA, canonico) + IMPUESTO_EN_FACTOR_IMP (IIBB, canonico)
--   ML_COMI            -> FLAG_COMISION_ML           (COMISION_ML, canonico; toma el % del MLA del producto)
--   ML_MKT + ML_EMB + ML_SERTEC -> COMISION_SOBRE_PVP (bucket 1 "gastosVenta")
--   ML_CO_MAQCENV / ML_CO_REP / ML_CO_MENAJE -> COSTO_OCULTO_PVP (bucket 2 "costoOculto")
--   ML_PRTACHADO       -> INFLACION_DIVISOR_FINAL (bucket 3 "tachado")
--   Cuotas (ML_3C / 6C / 9C / 12C) -> canal_concepto_cuota (NO es ConceptoCalculo).
--
-- CONVENCION DE NOMBRES:
--   * Conceptos FLAG (porcentaje NULL, solo marcadores): nombre canonico SIN prefijo
--     de canal (IVA, MARGEN_MIN, MARGEN_MAY, ENVIO, PRECIO_INFLADO). Se reusan
--     entre canales asignandolos en canal_concepto.
--   * Conceptos con porcentaje propio del canal: prefijo de canal (ML_COMI, KH_MP,
--     LG_FIN, etc.) porque cada canal tiene un valor distinto.
--   * IIBB y otros impuestos compartidos van sin prefijo cuando el % es el mismo.
--
-- IMPORTANTE:
--   * Los porcentajes son PLACEHOLDERS. Ajustar con los valores reales del Excel.
--   * ML_EMB excluye cuando el producto es MAQUINA y NO tiene envio (precio_envio = 0 / NULL).
--   * ML_CO_MAQCENV aplica solo si es MAQUINA y TIENE envio (precio_envio > 0).
--
-- PRERREQUISITO:
--   La tabla canal_concepto_regla debe tener la columna "tiene_envio" (BOOLEAN NULL).
--   Si no existe, correr antes:
--     ALTER TABLE supermaster.canal_concepto_regla ADD COLUMN tiene_envio BOOLEAN NULL;
-- =============================================================

USE supermaster;

-- -------------------------------------------------------------
-- 0) (Ejecutar una sola vez) Agregar columna tiene_envio si no existe
-- -------------------------------------------------------------
-- ALTER TABLE canal_concepto_regla ADD COLUMN tiene_envio BOOLEAN NULL;

-- -------------------------------------------------------------
-- 1) ConceptoCalculo
-- -------------------------------------------------------------
INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion) VALUES
  -- Conceptos canonicos (FLAG, sin prefijo o con sufijo de canal): se reusan entre canales
  ('IVA',                    NULL, 'FLAG_APLICAR_IVA',            'Habilita la aplicacion del IVA del producto'),
  ('IIBB',                   3.00, 'IMPUESTO_EN_FACTOR_IMP',      'Ingresos Brutos: se suma al factor IMP junto con el IVA'),
  ('MARGEN_MIN',             NULL, 'FLAG_USAR_MARGEN_MINORISTA',  'Usa margen minorista del producto (GAN.MIN.ML)'),
  ('ENVIO_ML',               NULL, 'FLAG_INCLUIR_ENVIO',          'Suma precio_envio del MLA al costo antes de impuestos'),
  ('COMISION_ML',            NULL, 'FLAG_COMISION_ML',            'Habilita la comision de cada producto de Mercado Libre sobre el PVP (toma el % del MLA del producto)'),
  -- Conceptos con porcentaje propio del canal ML
  ('ML_MKT',          5.00, 'COMISION_SOBRE_PVP',         'Gasto de marketing / publicaciones del canal ML (gasto real del dueno)'),
  ('ML_EMB',          2.00, 'COMISION_SOBRE_PVP',         'Gasto de embalaje del envio ML (gasto real del dueno; aplica a todos salvo MAQUINA sin envio)'),
  ('ML_SERTEC',       3.00, 'COMISION_SOBRE_PVP',         'Cargo de servicio tecnico ML que se retiene en ventas de tag=MAQUINA'),
  ('ML_CO_MAQCENV',  10.00, 'COSTO_OCULTO_PVP',           'Costo oculto ML para MAQUINA con envio: retencion adicional. Divisor separado que infla el PVP y reduce ingreso del dueno'),
  ('ML_CO_REP',       5.00, 'COSTO_OCULTO_PVP',           'Costo oculto ML para REPUESTO: retencion adicional. Divisor separado que infla el PVP y reduce ingreso del dueno'),
  ('ML_CO_MENAJE',    8.00, 'COSTO_OCULTO_PVP',           'Costo oculto ML para MENAJE / sin tag: retencion adicional. Divisor separado que infla el PVP y reduce ingreso del dueno'),
  ('ML_PRTACHADO',   10.00, 'INFLACION_DIVISOR_FINAL',    'Inflacion cosmetica del PVP ML para mostrar precio tachado al cliente. ML no retiene esto: el dueno se queda con la plata extra (no es costo)');

-- -------------------------------------------------------------
-- 2) Asignar conceptos al canal ML (canal_concepto)
-- -------------------------------------------------------------
INSERT INTO canal_concepto (id_canal, id_concepto)
SELECT c.id_canal, cc.id_concepto
FROM canales c
CROSS JOIN conceptos_calculo cc
WHERE c.nombre = 'ML'
  AND cc.nombre IN (
    'IVA', 'IIBB', 'MARGEN_MIN', 'ENVIO_ML', 'COMISION_ML',
    'ML_MKT', 'ML_EMB', 'ML_SERTEC',
    'ML_CO_MAQCENV', 'ML_CO_REP', 'ML_CO_MENAJE',
    'ML_PRTACHADO'
  );

-- -------------------------------------------------------------
-- 3) Reglas de excepcion por TAG (canal_concepto_regla)
--    tipo_regla = INCLUIR -> el concepto SOLO aplica si el producto cumple la condicion
-- -------------------------------------------------------------

-- ML_SERTEC aplica solo si tag=MAQUINA
INSERT INTO canal_concepto_regla (id_canal, id_concepto, tipo_regla, tag)
SELECT c.id_canal, cc.id_concepto, 'INCLUIR', 'MAQUINA'
FROM canales c CROSS JOIN conceptos_calculo cc
WHERE c.nombre = 'ML' AND cc.nombre = 'ML_SERTEC';

-- ML_EMB se excluye si tag=MAQUINA Y el producto no tiene envio (precio_envio = 0 / NULL)
INSERT INTO canal_concepto_regla (id_canal, id_concepto, tipo_regla, tag, tiene_envio)
SELECT c.id_canal, cc.id_concepto, 'EXCLUIR', 'MAQUINA', FALSE
FROM canales c CROSS JOIN conceptos_calculo cc
WHERE c.nombre = 'ML' AND cc.nombre = 'ML_EMB';

-- ML_CO_MAQCENV aplica solo si tag=MAQUINA Y el producto tiene envio (precio_envio > 0)
INSERT INTO canal_concepto_regla (id_canal, id_concepto, tipo_regla, tag, tiene_envio)
SELECT c.id_canal, cc.id_concepto, 'INCLUIR', 'MAQUINA', TRUE
FROM canales c CROSS JOIN conceptos_calculo cc
WHERE c.nombre = 'ML' AND cc.nombre = 'ML_CO_MAQCENV';

-- ML_CO_REP aplica solo si tag=REPUESTO
INSERT INTO canal_concepto_regla (id_canal, id_concepto, tipo_regla, tag)
SELECT c.id_canal, cc.id_concepto, 'INCLUIR', 'REPUESTO'
FROM canales c CROSS JOIN conceptos_calculo cc
WHERE c.nombre = 'ML' AND cc.nombre = 'ML_CO_REP';

-- ML_CO_MENAJE aplica solo si tag=MENAJE
INSERT INTO canal_concepto_regla (id_canal, id_concepto, tipo_regla, tag)
SELECT c.id_canal, cc.id_concepto, 'INCLUIR', 'MENAJE'
FROM canales c CROSS JOIN conceptos_calculo cc
WHERE c.nombre = 'ML' AND cc.nombre = 'ML_CO_MENAJE';

-- -------------------------------------------------------------
-- 4) Cuotas del canal ML (canal_concepto_cuota)
--    NOTA: Esto NO es ConceptoCalculo. Las cuotas viven en su propia tabla.
--    Cargar segun corresponda, ejemplos (ajustar porcentajes):
--
--   INSERT INTO canal_concepto_cuota (id_canal, cuotas, porcentaje, descripcion) VALUES
--     ( (SELECT id_canal FROM canales WHERE nombre='ML'),  3, 12.00, 'ML_3C'),
--     ( (SELECT id_canal FROM canales WHERE nombre='ML'),  6, 18.00, 'ML_6C'),
--     ( (SELECT id_canal FROM canales WHERE nombre='ML'),  9, 22.00, 'ML_9C'),
--     ( (SELECT id_canal FROM canales WHERE nombre='ML'), 12, 28.00, 'ML_12C');
-- -------------------------------------------------------------

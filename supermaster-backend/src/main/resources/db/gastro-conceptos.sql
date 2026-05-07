-- =============================================================
-- Carga de ConceptoCalculo, CanalConcepto y CanalConceptoRegla
-- para el canal KT GASTRO segun la formula del Excel.
--
-- Formula de referencia (con simplificacion de envio: NO se suma
-- al baseImp ni se resta al final):
--
--   * Comun a las 3 ramas:
--       relMkUp    = 0  si MAQUINA
--                    KG_RELMKUP_REP si REPUESTO
--                    KG_RELMKUP_MEN si MENAJE (default)
--       costoFinal = COSTO * (1 + GAN.MIN.ML * (1 - relMkUp))
--       baseImp    = costoFinal * IMP   (IMP = 1 + IVA + IIBB)
--
--   * MAQUINA (relMkUp = 0):
--       gastosSobrePVP = mla.comisionPorcentaje + KG_MAQ_MKT + KG_MAQ_EMB(*) + KG_MAQ_SERTEC
--                        (mismo divisor; KG_MAQ_EMB es INFLACION_SOBRE_PVP -> infla pero no costo)
--       costoOculto    = KG_CO_MAQCENV(*)   (INFLACION_DIVISOR, divisor separado)
--                        (*) KG_MAQ_EMB y KG_CO_MAQCENV solo si tiene envio
--       gastoDuenoOff  = KG_COMVEND   (GASTO_FUERA_PVP: cuenta como costo, NO afecta PVP)
--       PVP = baseImp / (1 - gastosSobrePVP) / (1 - costoOculto) * (1 - KG_DESC_5/100)
--       costosVenta MAQUINA = (KG_MAQ_MKT + KG_MAQ_SERTEC + KG_COMVEND)% * PVP
--       Equivale al Excel: PVP_ML * (1 - PRTACHADO) * 0,95
--       (PRTACHADO se cancela; el costoOculto sobrevive; PVP exacto al Excel)
--
--   * REPUESTO (relMkUp = KG_RELMKUP_REP):
--       gastosVenta = KG_MKT + KG_EMB
--       PVP = baseImp / (1 - gastosVenta)
--
--   * MENAJE (relMkUp = KG_RELMKUP_MEN, default si no tiene tag):
--       gastosVenta = KG_COMI + KG_CO + KG_MKT + KG_EMB
--       PVP = baseImp / (1 - gastosVenta)
--
-- Mapeo fuente (Excel) -> modelo backend:
--   GAN.MIN.ML       -> FLAG_USAR_MARGEN_MINORISTA   (MARGEN_MIN, canonico compartido)
--   IVA              -> FLAG_APLICAR_IVA             (IVA, canonico compartido)
--   IIBB             -> IMPUESTO_ADICIONAL           (IIBB, canonico compartido)
--   ML_COMI (13%)    -> FLAG_INFLACION_ML            (KG_INFLA_ML, usa mla.comisionPorcentaje)
--   ML_MKT           -> COMISION_SOBRE_PVP           (KG_MAQ_MKT,    PROPIO, en gastos del dueño)
--   ML_EMB           -> INFLACION_SOBRE_PVP          (KG_MAQ_EMB,    PROPIO, c/regla envio - INFLA EN MISMO DIVISOR, NO COSTO)
--   ML_SERTEC        -> COMISION_SOBRE_PVP           (KG_MAQ_SERTEC, PROPIO, en gastos del dueño)
--   ML_CO_MAQCENV    -> INFLACION_DIVISOR            (KG_CO_MAQCENV, PROPIO, solo MAQUINA c/envio)
--   KG_COMVEND       -> GASTO_FUERA_PVP              (KG_COMVEND,    NO afecta PVP, SI cuenta como costo)
--   "* 0,95" MAQUINA -> DESCUENTO_PORCENTUAL         (KG_DESC_5)
--   relMkUp REPUESTO -> AJUSTE_MARGEN_PROPORCIONAL   (KG_RELMKUP_REP, NEGATIVO)
--   relMkUp MENAJE   -> AJUSTE_MARGEN_PROPORCIONAL   (KG_RELMKUP_MEN, NEGATIVO)
--   KG_MKT, KG_EMB   -> COMISION_SOBRE_PVP           (REPUESTO / MENAJE)
--   KG_COMI, KG_CO   -> COMISION_SOBRE_PVP           (solo MENAJE)
--
-- DECISIONES DE DISEÑO:
--   * KT GASTRO NO comparte conceptos con porcentaje propio con ML.
--     Los canonicos FLAG (IVA, MARGEN_MIN, IIBB) si se comparten
--     porque son globales (mismo % para todos los canales).
--
--   * La comision ML para MAQUINA se modela con FLAG_INFLACION_ML
--     (no con COMISION_SOBRE_PVP/FLAG_COMISION_ML) porque desde la
--     perspectiva del dueño no es un costo neto del canal: el PVP
--     ya esta inflado por el factor de ML, sumarlo de vuelta como
--     costo seria doble. FLAG_INFLACION_ML divide el PVP por
--     (1 - mla.comisionPorcentaje/100) IGUAL que una comision, pero
--     NO se suma a costosVenta.
--
--   * Por el mismo motivo, KG_MAQ_EMB se mapea a INFLACION_SOBRE_PVP:
--     se SUMA al MISMO divisor que las comisiones (matchea Excel exacto)
--     pero NO cuenta como costo del dueno (en el Excel el dueno no
--     incluye EMB en sus gastos para MAQUINA, solo SERTEC + MKT + COMVEND).
--
--   * KG_COMVEND es un GASTO_FUERA_PVP: el dueño paga 1,5% como
--     comision interna del vendedor pero NO infla el precio. Solo
--     reduce el ingresoNetoVendedor / ganancia.
--
--   * Resultado en indicadores MAQUINA (Excel matchea):
--       costosVenta = (KG_MAQ_MKT 5 + KG_MAQ_SERTEC 3 + KG_COMVEND 1,5)% * PVP = 9,5% * PVP
--       ingresoNetoVendedor refleja lo que el dueño ve como ingreso "real".
--
--   * AJUSTE_MARGEN_PROPORCIONAL aplica: gananciaAjustada = margen * (1 + %/100).
--     Excel hace: margen * (1 - relMkUp). Por eso el % se almacena NEGATIVO.
--     Ej: relMkUp=0,20 -> porcentaje = -20.000.
--
--   * SIMPLIFICACION DE ENVIO:
--     El envio NO se suma al baseImp ni se resta al final del calculo.
--     Para MAQUINA con envio el PVP queda levemente mas bajo que el
--     Excel exacto, pero KG_MAQ_EMB sigue aplicando solo a MAQUINA con
--     envio (regla tiene_envio=TRUE), respetando la regla "MAQ S/ENVIO
--     no tiene EMB" del Excel.
--
-- IMPORTANTE - PORCENTAJES PLACEHOLDERS:
--   KG_MAQ_MKT / KG_MAQ_EMB / KG_MAQ_SERTEC / KG_DESC_5 /
--   KG_RELMKUP_REP / KG_RELMKUP_MEN / KG_MKT / KG_EMB / KG_COMI / KG_CO
--   son PLACEHOLDERS. Ajustar con los valores reales del Excel.
--
-- PRERREQUISITOS:
--   * El canal "KT GASTRO" debe existir en la tabla canales.
--   * Los conceptos canonicos IVA, IIBB, MARGEN_MIN deben existir
--     (ml-conceptos.sql los crea).
--   * La tabla canal_concepto_regla debe tener la columna "tiene_envio".
--   * El ENUM aplica_sobre debe incluir 'GASTO_FUERA_PVP'. Si no, correr
--     primero: add-aplica-sobre-gasto-fuera-pvp.sql
--
-- IDEMPOTENTE: usa INSERT IGNORE / WHERE NOT EXISTS para que se pueda
-- re-ejecutar sin duplicar.
-- =============================================================

USE supermaster;

-- -------------------------------------------------------------
-- 1) ConceptoCalculo (idempotente: no re-inserta si el nombre existe)
--    Patron WHERE NOT EXISTS para no depender de UNIQUE constraint en nombre.
-- -------------------------------------------------------------
INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion)
SELECT 'KG_INFLA_ML', NULL, 'FLAG_INFLACION_ML',
       'Infla el PVP de KT GASTRO MAQUINA con la comision MLA (sin contarla como costo del canal)'
WHERE NOT EXISTS (SELECT 1 FROM conceptos_calculo WHERE nombre = 'KG_INFLA_ML');

INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion)
SELECT 'KG_MAQ_MKT', 5.000, 'COMISION_SOBRE_PVP',
       'Marketing / publicaciones para MAQUINA en KT GASTRO'
WHERE NOT EXISTS (SELECT 1 FROM conceptos_calculo WHERE nombre = 'KG_MAQ_MKT');

-- KG_MAQ_EMB: INFLACION_SOBRE_PVP (no COMISION_SOBRE_PVP, no INFLACION_DIVISOR).
-- Suma al MISMO divisor que las comisiones (matchea Excel exacto), pero NO
-- cuenta como costo del dueño (el embalaje no esta en sus gastos para MAQUINA).
INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion)
SELECT 'KG_MAQ_EMB', 2.000, 'INFLACION_SOBRE_PVP',
       'Embalaje para MAQUINA en KT GASTRO (solo si tiene envio; infla PVP en el divisor de gastos, NO cuenta como costo del dueno)'
WHERE NOT EXISTS (SELECT 1 FROM conceptos_calculo WHERE nombre = 'KG_MAQ_EMB');

-- Force-update si ya existia con otro aplica_sobre de una carga previa.
UPDATE conceptos_calculo
   SET aplica_sobre = 'INFLACION_SOBRE_PVP',
       descripcion  = 'Embalaje para MAQUINA en KT GASTRO (solo si tiene envio; infla PVP en el divisor de gastos, NO cuenta como costo del dueno)'
 WHERE nombre = 'KG_MAQ_EMB' AND aplica_sobre <> 'INFLACION_SOBRE_PVP';

INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion)
SELECT 'KG_MAQ_SERTEC', 3.000, 'COMISION_SOBRE_PVP',
       'Servicio tecnico para MAQUINA en KT GASTRO'
WHERE NOT EXISTS (SELECT 1 FROM conceptos_calculo WHERE nombre = 'KG_MAQ_SERTEC');

-- Costo oculto para MAQUINA con envio (Excel: ML_CO_MAQCENV).
-- INFLACION_DIVISOR: divisor sobre PVP, NO suma a costosVenta (consistente con KG_INFLA_ML).
INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion)
SELECT 'KG_CO_MAQCENV', 10.000, 'INFLACION_DIVISOR',
       'Costo oculto KT GASTRO para MAQUINA con envio (infla PVP, no cuenta como costo del canal)'
WHERE NOT EXISTS (SELECT 1 FROM conceptos_calculo WHERE nombre = 'KG_CO_MAQCENV');

INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion)
SELECT 'KG_DESC_5', 5.000, 'DESCUENTO_PORCENTUAL',
       'Descuento 5% sobre PVP en MAQUINA'
WHERE NOT EXISTS (SELECT 1 FROM conceptos_calculo WHERE nombre = 'KG_DESC_5');

-- KG_COMVEND: GASTO_FUERA_PVP (cuenta como costo del dueño pero NO infla el PVP).
-- Excel: KG_COMVEND aparece SOLO en la formula de gastos del dueño para MAQUINA,
-- nunca en la formula de PVP.
INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion)
SELECT 'KG_COMVEND', 1.500, 'GASTO_FUERA_PVP',
       'Comision interna del vendedor para MAQUINA en KT GASTRO (cuenta como costo del dueno, NO afecta el PVP)'
WHERE NOT EXISTS (SELECT 1 FROM conceptos_calculo WHERE nombre = 'KG_COMVEND');

INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion)
SELECT 'KG_RELMKUP_REP', 0, 'AJUSTE_MARGEN_PROPORCIONAL',
       'Reduccion proporcional del margen para REPUESTO en KT GASTRO (margen * (1 - 0,20))'
WHERE NOT EXISTS (SELECT 1 FROM conceptos_calculo WHERE nombre = 'KG_RELMKUP_REP');

INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion)
SELECT 'KG_RELMKUP_MEN', -12.000, 'AJUSTE_MARGEN_PROPORCIONAL',
       'Reduccion proporcional del margen para MENAJE en KT GASTRO (margen * (1 - 0,10))'
WHERE NOT EXISTS (SELECT 1 FROM conceptos_calculo WHERE nombre = 'KG_RELMKUP_MEN');

INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion)
SELECT 'KG_MKT', 5.000, 'COMISION_SOBRE_PVP',
       'Marketing KT GASTRO (REPUESTO / MENAJE)'
WHERE NOT EXISTS (SELECT 1 FROM conceptos_calculo WHERE nombre = 'KG_MKT');

INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion)
SELECT 'KG_EMB', 2.000, 'COMISION_SOBRE_PVP',
       'Embalaje KT GASTRO (REPUESTO / MENAJE)'
WHERE NOT EXISTS (SELECT 1 FROM conceptos_calculo WHERE nombre = 'KG_EMB');

INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion)
SELECT 'KG_COMI', 8.000, 'COMISION_SOBRE_PVP',
       'Comision KT GASTRO (solo MENAJE)'
WHERE NOT EXISTS (SELECT 1 FROM conceptos_calculo WHERE nombre = 'KG_COMI');

INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion)
SELECT 'KG_CO', 5.000, 'COMISION_SOBRE_PVP',
       'Costo oculto KT GASTRO (solo MENAJE)'
WHERE NOT EXISTS (SELECT 1 FROM conceptos_calculo WHERE nombre = 'KG_CO');

-- -------------------------------------------------------------
-- 2) Asignar conceptos al canal KT GASTRO (canal_concepto)
--    Compartidos: IVA, IIBB, MARGEN_MIN (canonicos, sin prefijo)
--    Propios:     KG_INFLA_ML, KG_MAQ_*, KG_DESC_5, KG_RELMKUP_*,
--                 KG_MKT, KG_EMB, KG_COMI, KG_CO
-- -------------------------------------------------------------
INSERT INTO canal_concepto (id_canal, id_concepto)
SELECT c.id_canal, cc.id_concepto
FROM canales c
CROSS JOIN conceptos_calculo cc
WHERE c.nombre = 'KT GASTRO'
  AND cc.nombre IN (
    -- canonicos compartidos
    'IVA', 'IIBB', 'MARGEN_MIN',
    -- MAQUINA
    'KG_INFLA_ML', 'KG_MAQ_MKT', 'KG_MAQ_EMB', 'KG_MAQ_SERTEC', 'KG_CO_MAQCENV', 'KG_DESC_5', 'KG_COMVEND',
    -- REPUESTO / MENAJE
    'KG_RELMKUP_REP', 'KG_RELMKUP_MEN',
    'KG_MKT', 'KG_EMB', 'KG_COMI', 'KG_CO'
  )
  AND NOT EXISTS (
    SELECT 1 FROM canal_concepto x
    WHERE x.id_canal = c.id_canal AND x.id_concepto = cc.id_concepto
  );

-- -------------------------------------------------------------
-- 3) Reglas por TAG (canal_concepto_regla)
--
--    Semantica:
--      - INCLUIR: el concepto SOLO aplica si cumple las condiciones
--      - EXCLUIR: el concepto NO aplica si cumple las condiciones
--      - Producto sin tag se trata como MENAJE (default del "else" del Excel)
-- -------------------------------------------------------------

-- 3.1) MAQUINA: INCLUIR KG_INFLA_ML, KG_MAQ_MKT, KG_MAQ_SERTEC, KG_DESC_5, KG_COMVEND
INSERT INTO canal_concepto_regla (id_canal, id_concepto, tipo_regla, tag)
SELECT c.id_canal, cc.id_concepto, 'INCLUIR', 'MAQUINA'
FROM canales c CROSS JOIN conceptos_calculo cc
WHERE c.nombre = 'KT GASTRO'
  AND cc.nombre IN ('KG_INFLA_ML', 'KG_MAQ_MKT', 'KG_MAQ_SERTEC', 'KG_DESC_5', 'KG_COMVEND')
  AND NOT EXISTS (
    SELECT 1 FROM canal_concepto_regla x
    WHERE x.id_canal = c.id_canal
      AND x.id_concepto = cc.id_concepto
      AND x.tipo_regla = 'INCLUIR'
      AND x.tag = 'MAQUINA'
      AND x.tiene_envio IS NULL
  );

-- 3.2) KG_MAQ_EMB y KG_CO_MAQCENV: INCLUIR solo si MAQUINA Y tiene envio
--      (Excel: la columna "MAQ S/ENVIO" no tiene EMBALAJE ni costo oculto)
INSERT INTO canal_concepto_regla (id_canal, id_concepto, tipo_regla, tag, tiene_envio)
SELECT c.id_canal, cc.id_concepto, 'INCLUIR', 'MAQUINA', TRUE
FROM canales c CROSS JOIN conceptos_calculo cc
WHERE c.nombre = 'KT GASTRO'
  AND cc.nombre IN ('KG_MAQ_EMB', 'KG_CO_MAQCENV')
  AND NOT EXISTS (
    SELECT 1 FROM canal_concepto_regla x
    WHERE x.id_canal = c.id_canal
      AND x.id_concepto = cc.id_concepto
      AND x.tipo_regla = 'INCLUIR'
      AND x.tag = 'MAQUINA'
      AND x.tiene_envio = TRUE
  );

-- 3.3) REPUESTO: INCLUIR KG_RELMKUP_REP
INSERT INTO canal_concepto_regla (id_canal, id_concepto, tipo_regla, tag)
SELECT c.id_canal, cc.id_concepto, 'INCLUIR', 'REPUESTO'
FROM canales c CROSS JOIN conceptos_calculo cc
WHERE c.nombre = 'KT GASTRO'
  AND cc.nombre IN ('KG_RELMKUP_REP')
  AND NOT EXISTS (
    SELECT 1 FROM canal_concepto_regla x
    WHERE x.id_canal = c.id_canal
      AND x.id_concepto = cc.id_concepto
      AND x.tipo_regla = 'INCLUIR'
      AND x.tag = 'REPUESTO'
      AND x.tiene_envio IS NULL
  );

-- 3.4) MENAJE: INCLUIR KG_RELMKUP_MEN, KG_COMI, KG_CO
INSERT INTO canal_concepto_regla (id_canal, id_concepto, tipo_regla, tag)
SELECT c.id_canal, cc.id_concepto, 'INCLUIR', 'MENAJE'
FROM canales c CROSS JOIN conceptos_calculo cc
WHERE c.nombre = 'KT GASTRO'
  AND cc.nombre IN ('KG_RELMKUP_MEN', 'KG_COMI', 'KG_CO')
  AND NOT EXISTS (
    SELECT 1 FROM canal_concepto_regla x
    WHERE x.id_canal = c.id_canal
      AND x.id_concepto = cc.id_concepto
      AND x.tipo_regla = 'INCLUIR'
      AND x.tag = 'MENAJE'
      AND x.tiene_envio IS NULL
  );

-- 3.5) KG_MKT y KG_EMB: EXCLUIR MAQUINA
--      (aplican a REPUESTO y MENAJE; MAQUINA usa KG_MAQ_MKT y KG_MAQ_EMB)
INSERT INTO canal_concepto_regla (id_canal, id_concepto, tipo_regla, tag)
SELECT c.id_canal, cc.id_concepto, 'EXCLUIR', 'MAQUINA'
FROM canales c CROSS JOIN conceptos_calculo cc
WHERE c.nombre = 'KT GASTRO'
  AND cc.nombre IN ('KG_MKT', 'KG_EMB')
  AND NOT EXISTS (
    SELECT 1 FROM canal_concepto_regla x
    WHERE x.id_canal = c.id_canal
      AND x.id_concepto = cc.id_concepto
      AND x.tipo_regla = 'EXCLUIR'
      AND x.tag = 'MAQUINA'
      AND x.tiene_envio IS NULL
  );

-- -------------------------------------------------------------
-- 4) Cuotas del canal KT GASTRO (canal_concepto_cuota) - OPCIONAL
--
--    Si KT GASTRO ofrece cuotas con recargo, cargarlas aqui (ajustar):
--
--   INSERT INTO canal_concepto_cuota (id_canal, cuotas, porcentaje, descripcion) VALUES
--     ( (SELECT id_canal FROM canales WHERE nombre='KT GASTRO'),  3, 10.00, 'KG_3C'),
--     ( (SELECT id_canal FROM canales WHERE nombre='KT GASTRO'),  6, 18.00, 'KG_6C');
-- -------------------------------------------------------------

-- =============================================================
-- POST-EJECUCION
--
-- 1) KT GASTRO marcara recalculo pendiente al detectar el cambio en
--    canal_concepto. Apreta "Aplicar recalculo" en el banner del header.
--
-- 2) Validacion rapida de PVPs esperados (con porcentajes placeholder):
--
--    MAQUINA con envio (mla.comisionPorcentaje = 13%):
--      gastosSobrePVP = 13 (INFLA_ML) + 5 (MAQ_MKT) + 2 (MAQ_EMB INFLACION_SOBRE_PVP) + 3 (MAQ_SERTEC) = 23%
--      costoOculto    = 10 (CO_MAQCENV INFLACION_DIVISOR)                                              = 10%
--      PVP = baseImp / (1 - 0,23) / (1 - 0,10) * (1 - 0,05) ≈ baseImp * 1,371   (== Excel exacto)
--      costosVenta = (5 MAQ_MKT + 3 MAQ_SERTEC + 1,5 COMVEND)% * PVP        = 9,5% * PVP
--
--    MAQUINA sin envio (mla.comisionPorcentaje = 13%):
--      gastosSobrePVP = 13 + 5 + 3 = 21%   (sin EMB ni CO_MAQCENV por regla envio=TRUE)
--      costoOculto    = 0%
--      PVP = baseImp / (1 - 0,21) * 0,95 ≈ baseImp * 1,203
--      costosVenta = (5 + 3 + 1,5)% * PVP = 9,5% * PVP
--
--    REPUESTO:
--      baseImp = COSTO * (1 + GAN.MIN.ML * 0,80) * IMP
--      PVP = baseImp / (1 - 0,07)   (KG_MKT 5 + KG_EMB 2)
--
--    MENAJE / sin tag:
--      baseImp = COSTO * (1 + GAN.MIN.ML * 0,90) * IMP
--      PVP = baseImp / (1 - 0,20)   (KG_COMI 8 + KG_CO 5 + KG_MKT 5 + KG_EMB 2)
-- =============================================================

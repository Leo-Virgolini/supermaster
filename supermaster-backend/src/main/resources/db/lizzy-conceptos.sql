-- =============================================================
-- Carga de ConceptoCalculo y CanalConcepto
-- para los subcanales de LINEA GE: LIZZY GASTRO y LIZZY HUDSON.
--
-- Formula de referencia (Excel):
--   PVP LIZZY GASTRO  = MAYORISTA * 0,72 * 1,5
--   PVP LIZZY HUDSON  = MAYORISTA * 0,65 * 1,7
--
--   donde MAYORISTA = PVP del canal LINEA GE (canal padre)
--
-- Mapeo fuente (Excel) -> modelo backend:
--   * 0,72  -> CALCULO_SOBRE_CANAL_BASE_RESELLER  (LZ_GASTRO_DESC, porcentaje = -28)
--   * 1,5   -> CALCULO_SOBRE_CANAL_BASE           (LZ_GASTRO_MUP,  porcentaje = +50)
--   * 0,65  -> CALCULO_SOBRE_CANAL_BASE_RESELLER  (LZ_HUDSON_DESC, porcentaje = -35)
--   * 1,7   -> CALCULO_SOBRE_CANAL_BASE           (LZ_HUDSON_MUP,  porcentaje = +70)
--
-- DIFERENCIA RESELLER vs no-RESELLER:
--   * CALCULO_SOBRE_CANAL_BASE_RESELLER: factor que afecta el PVP final pero
--     CORTA el ingreso del dueño en este punto. Representa el descuento que
--     el dueño le hace al re-vendedor (lo que LIZZY paga al dueño).
--   * CALCULO_SOBRE_CANAL_BASE: factor que afecta solo el PVP final (markup
--     del re-vendedor). NO contribuye al ingreso del dueño.
--
-- Comportamiento del calculador (CalculoPrecioServiceImpl):
--   PVP_CORTE = PVP_CANAL_BASE * ∏(factores RESELLER)
--   PVP_FINAL = PVP_CORTE * ∏(factores no-RESELLER)
--   INGRESO_DUEÑO = INGRESO_NETO_BASE * (PVP_CORTE / PVP_BASE)
--   GANANCIA_DUEÑO = INGRESO_DUEÑO - costoProducto
--
--   Aplicado a LIZZY GASTRO:
--     PVP_CORTE = PVP_LINEA_GE * 0,72   (ingreso del dueño)
--     PVP_FINAL = PVP_CORTE * 1,5       (precio al consumidor)
--                = PVP_LINEA_GE * 1,08   -> coincide con Excel  OK
--
--   Aplicado a LIZZY HUDSON:
--     PVP_CORTE = PVP_LINEA_GE * 0,65
--     PVP_FINAL = PVP_CORTE * 1,7
--                = PVP_LINEA_GE * 1,105  -> coincide con Excel  OK
--
-- IMPORTANTE:
--   * Estos canales NO usan ProductoMargen ni IVA propio: heredan el PVP
--     ya calculado de LINEA GE y solo lo "re-escalan".
--   * No hay reglas por TAG: el factor es uniforme para todos los productos.
--   * Si en el futuro LIZZY pasa a ser canal propio (no re-vendedor),
--     basta con cambiar el aplica_sobre del concepto _DESC a
--     CALCULO_SOBRE_CANAL_BASE — los indicadores se recalculan solos.
--
-- PRERREQUISITOS:
--   1) El enum aplica_sobre debe tener el valor CALCULO_SOBRE_CANAL_BASE_RESELLER.
--      Si no lo tiene (BD existente), correr antes la migracion:
--        src/main/resources/db/add-aplica-sobre-canal-base-reseller.sql
--   2) El canal "LINEA GE" debe existir y estar configurado (linea-ge-conceptos.sql).
--   3) Los canales "LIZZY GASTRO" y "LIZZY HUDSON" deben existir en la tabla
--      canales CON id_canal_base apuntando a LINEA GE. Ejemplo:
--
--      INSERT INTO canales (nombre, id_canal_base)
--      SELECT 'LIZZY GASTRO', id_canal FROM canales WHERE nombre = 'LINEA GE';
--
--      INSERT INTO canales (nombre, id_canal_base)
--      SELECT 'LIZZY HUDSON', id_canal FROM canales WHERE nombre = 'LINEA GE';
-- =============================================================

USE supermaster;

-- -------------------------------------------------------------
-- 1) ConceptoCalculo (todos nuevos, propios de los subcanales LIZZY)
-- -------------------------------------------------------------
INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion) VALUES
  ('LZ_GASTRO_DESC', -28.000, 'CALCULO_SOBRE_CANAL_BASE_RESELLER', 'Descuento mayorista LIZZY GASTRO (Excel: * 0,72) - corta ingreso del dueño aqui'),
  ('LZ_GASTRO_MUP',  50.000,  'CALCULO_SOBRE_CANAL_BASE',          'Markup LIZZY GASTRO al consumidor final (Excel: * 1,5) - no afecta ingreso del dueño'),
  ('LZ_HUDSON_DESC', -35.000, 'CALCULO_SOBRE_CANAL_BASE_RESELLER', 'Descuento mayorista LIZZY HUDSON (Excel: * 0,65) - corta ingreso del dueño aqui'),
  ('LZ_HUDSON_MUP',  70.000,  'CALCULO_SOBRE_CANAL_BASE',          'Markup LIZZY HUDSON al consumidor final (Excel: * 1,7) - no afecta ingreso del dueño');

-- -------------------------------------------------------------
-- 2) Asignar conceptos a LIZZY GASTRO (canal_concepto)
-- -------------------------------------------------------------
INSERT INTO canal_concepto (id_canal, id_concepto)
SELECT c.id_canal, cc.id_concepto
FROM canales c
CROSS JOIN conceptos_calculo cc
WHERE c.nombre = 'LIZZY GASTRO'
  AND cc.nombre IN ('LZ_GASTRO_DESC', 'LZ_GASTRO_MUP');

-- -------------------------------------------------------------
-- 3) Asignar conceptos a LIZZY HUDSON (canal_concepto)
-- -------------------------------------------------------------
INSERT INTO canal_concepto (id_canal, id_concepto)
SELECT c.id_canal, cc.id_concepto
FROM canales c
CROSS JOIN conceptos_calculo cc
WHERE c.nombre = 'LIZZY HUDSON'
  AND cc.nombre IN ('LZ_HUDSON_DESC', 'LZ_HUDSON_MUP');

-- -------------------------------------------------------------
-- 4) Reglas por TAG (canal_concepto_regla)
--
--    Las formulas LIZZY no tienen condiciones por tag.
--    Esta seccion queda vacia.
-- -------------------------------------------------------------

-- -------------------------------------------------------------
-- 5) Cuotas (canal_concepto_cuota) - OPCIONAL
--
--    Si LIZZY GASTRO / LIZZY HUDSON ofrecen cuotas con recargo, cargarlas:
--
--   INSERT INTO canal_concepto_cuota (id_canal, cuotas, porcentaje, descripcion) VALUES
--     ( (SELECT id_canal FROM canales WHERE nombre='LIZZY GASTRO'),  3, 10.00, 'LZG_3C'),
--     ( (SELECT id_canal FROM canales WHERE nombre='LIZZY HUDSON'),  3, 10.00, 'LZH_3C');
-- -------------------------------------------------------------

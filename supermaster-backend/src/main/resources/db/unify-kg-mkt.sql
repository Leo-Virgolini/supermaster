-- =============================================================
-- Migración: unificar KG_MAQ_MKT y KG_MKT en KG_MKT
-- =============================================================
-- Razón: ambos conceptos tenían el mismo porcentaje (5%) y el mismo aplicaSobre
-- (COMISION_SOBRE_PVP); la única diferencia era la regla de tag (uno INCLUIR
-- tag=MAQUINA, el otro EXCLUIR tag=MAQUINA). El cálculo final daba lo mismo
-- en costosVenta y PVP, así que se unifican en un solo concepto KG_MKT que
-- aplica a los 3 tags (MAQUINA, REPUESTO, MENAJE).
--
-- Resultado esperado: precios calculados idénticos antes y después.
--
-- Reversible: NO. Si querés volver atrás, recreá KG_MAQ_MKT y sus reglas
-- manualmente (o restaurá un backup).
--
-- ANTES de ejecutar:
--   1) Hacer backup de las tablas afectadas:
--        conceptos_calculo, canal_concepto, canal_concepto_regla.
--   2) Verificar que no haya un recálculo masivo en curso.
-- =============================================================

BEGIN;

-- 1) Borrar reglas (canal_concepto_regla) que apuntan a KG_MAQ_MKT
--    (típicamente la regla INCLUIR tag=MAQUINA en KT GASTRO).
DELETE FROM canal_concepto_regla
WHERE id_concepto = (SELECT id_concepto FROM conceptos_calculo WHERE nombre = 'KG_MAQ_MKT');

-- 2) Borrar la regla EXCLUIR tag=MAQUINA en KG_MKT (en KT GASTRO),
--    para que KG_MKT pase a aplicar a los 3 tags.
DELETE FROM canal_concepto_regla
WHERE id_canal = (SELECT id_canal FROM canales WHERE nombre = 'KT GASTRO')
  AND id_concepto = (SELECT id_concepto FROM conceptos_calculo WHERE nombre = 'KG_MKT')
  AND tipo_regla = 'EXCLUIR'
  AND tag = 'MAQUINA';

-- 3) Quitar las asignaciones canal_concepto que apuntan a KG_MAQ_MKT.
DELETE FROM canal_concepto
WHERE id_concepto = (SELECT id_concepto FROM conceptos_calculo WHERE nombre = 'KG_MAQ_MKT');

-- 4) Eliminar el concepto KG_MAQ_MKT.
DELETE FROM conceptos_calculo WHERE nombre = 'KG_MAQ_MKT';

COMMIT;

-- =============================================================
-- POST-MIGRACIÓN:
--   Después de correr este script, disparar un Recálculo Masivo desde la app
--   (POST /api/precios/recalculo-masivo/iniciar) para regenerar los
--   producto_canal_precios. Los nuevos PVPs deben coincidir con los anteriores.
-- =============================================================

-- =============================================================
-- Migracion: reemplazar reglas con es_maquina por reglas con tag
--
-- Mapeo:
--   es_maquina = TRUE  -> tag = 'MAQUINA'   (1:1)
--   es_maquina = FALSE -> tag = 'MENAJE' + tag = 'REPUESTO'  (1:2)
--
-- Ejecutar cada seccion por separado y verificar el resultado antes
-- de continuar. El paso 4 (DELETE) es destructivo, hacerlo al final.
-- =============================================================

USE supermaster;

-- -------------------------------------------------------------
-- 0) Backup previo (opcional pero recomendado)
-- -------------------------------------------------------------
-- CREATE TABLE canal_concepto_regla_backup_esmaquina AS
-- SELECT * FROM canal_concepto_regla WHERE es_maquina IS NOT NULL;

-- -------------------------------------------------------------
-- 1) Inspeccion previa: listar reglas a migrar
-- -------------------------------------------------------------
-- SELECT id, id_canal, id_concepto, tipo_regla, es_maquina,
--        id_tipo, id_clasif_gastro, id_clasif_gral, id_marca, tiene_envio
-- FROM canal_concepto_regla
-- WHERE es_maquina IS NOT NULL
-- ORDER BY id_canal, id_concepto;

-- -------------------------------------------------------------
-- 2) Migrar es_maquina = TRUE  ->  tag = 'MAQUINA'
-- -------------------------------------------------------------
UPDATE canal_concepto_regla
SET tag = 'MAQUINA',
    es_maquina = NULL
WHERE es_maquina = 1
  AND tag IS NULL;

-- -------------------------------------------------------------
-- 3) Migrar es_maquina = FALSE  ->  2 reglas (MENAJE + REPUESTO)
--    Se insertan 2 nuevas reglas clonando cada una, con tag distinto.
--    Luego se borra la original en el paso 4.
-- -------------------------------------------------------------

-- 3a) Clonar como tag=MENAJE
INSERT INTO canal_concepto_regla
  (id_canal, id_concepto, tipo_regla,
   id_tipo, id_clasif_gastro, id_clasif_gral, id_marca,
   es_maquina, tag, tiene_envio)
SELECT id_canal, id_concepto, tipo_regla,
       id_tipo, id_clasif_gastro, id_clasif_gral, id_marca,
       NULL, 'MENAJE', tiene_envio
FROM canal_concepto_regla
WHERE es_maquina = 0
  AND tag IS NULL;

-- 3b) Clonar como tag=REPUESTO
INSERT INTO canal_concepto_regla
  (id_canal, id_concepto, tipo_regla,
   id_tipo, id_clasif_gastro, id_clasif_gral, id_marca,
   es_maquina, tag, tiene_envio)
SELECT id_canal, id_concepto, tipo_regla,
       id_tipo, id_clasif_gastro, id_clasif_gral, id_marca,
       NULL, 'REPUESTO', tiene_envio
FROM canal_concepto_regla
WHERE es_maquina = 0
  AND tag IS NULL;

-- -------------------------------------------------------------
-- 4) Eliminar las reglas originales con es_maquina = FALSE
--    (YA FUERON CLONADAS con tag en el paso 3)
-- -------------------------------------------------------------
DELETE FROM canal_concepto_regla
WHERE es_maquina = 0
  AND tag IS NULL;

-- -------------------------------------------------------------
-- 5) Verificacion post-migracion
--    Deben quedar 0 reglas con es_maquina no nulo.
-- -------------------------------------------------------------
-- SELECT COUNT(*) AS reglas_con_esmaquina_remanentes
-- FROM canal_concepto_regla
-- WHERE es_maquina IS NOT NULL;

-- SELECT id, id_canal, id_concepto, tipo_regla, tag, tiene_envio
-- FROM canal_concepto_regla
-- ORDER BY id_canal, id_concepto;

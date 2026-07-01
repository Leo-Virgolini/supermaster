-- 2026-07-01 — Externalizar el título ML: deja de persistirse (fuente de verdad = el ítem de ML).
-- DESTRUCTIVO de una sola vía: para ítems publicados el título sigue en ML; para no publicados se pierde.
-- Idempotente: el drop se condiciona a que la columna exista.
USE supermaster;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = 'supermaster' AND TABLE_NAME = 'productos' AND COLUMN_NAME = 'titulo_ml');
SET @sql := IF(@col > 0, 'ALTER TABLE productos DROP COLUMN titulo_ml', 'SELECT "titulo_ml ya no existe"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================================
-- Migración: eliminar la columna es_maquina de canal_concepto_regla
-- Fecha: 2026-06-01
--
-- Contexto: "es máquina" en las reglas de cálculo de precios pasa a expresarse
-- exclusivamente por el campo `tag` (Tag.MAQUINA). La condición es_maquina se
-- retiró del motor de cálculo y de las entidades/DTOs.
--
-- Seguridad: al momento de generar este script, 0 reglas usaban es_maquina
-- (todas las 14 reglas existentes ya usan `tag`), por lo que dropear la columna
-- NO cambia ningún precio.
--
-- NO se ejecuta automáticamente. Corré este script manualmente cuando quieras
-- limpiar el esquema:
--   mysql -u root -p supermaster < 2026-06-01-drop-canal_concepto_regla-es_maquina.sql
-- ============================================================================

ALTER TABLE canal_concepto_regla DROP COLUMN es_maquina;

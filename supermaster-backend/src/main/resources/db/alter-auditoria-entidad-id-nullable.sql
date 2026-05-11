-- =============================================================
-- Migración: hacer auditoria_cambios.entidad_id nullable
-- =============================================================
-- Razón: ahora hay eventos de auditoría sin id de entidad específico:
--   - Login fallido (no hay usuario válido)
--   - Recálculo masivo (no es entidad con id)
--   - Relaciones M:N (id es compuesto, ej: producto_apto)
--
-- Para esos casos se usa entidad_codigo como identificador alternativo
-- (ej: "masivo", username del intento fallido, "P5-A3" para producto-apto).
-- =============================================================

USE supermaster;

ALTER TABLE auditoria_cambios
    MODIFY COLUMN entidad_id INT NULL;

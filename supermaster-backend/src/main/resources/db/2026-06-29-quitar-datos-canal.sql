-- 2026-06-29 — Externalizar datos de canal: dejan de persistirse (fuente de verdad = el canal).
-- DESTRUCTIVO de una sola vía: para ítems publicados el dato sigue en ML/Nube; para no publicados se pierde.
-- Cargar con: mysql --default-character-set=utf8mb4 ... (no aplica acá pero se mantiene la convención).
USE supermaster;

ALTER TABLE productos
    DROP COLUMN descripcion,
    DROP COLUMN ml_category_id,
    DROP COLUMN ml_category_nombre;

DROP TABLE IF EXISTS producto_ml_atributo;

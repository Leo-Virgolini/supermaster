-- =============================================================
-- Migración: ampliar precios_inflados.valor a DECIMAL(15,3)
-- =============================================================
-- Razón: la columna era DECIMAL(6,3) (máx 999.999). Para los precios
-- inflados de tipo PRECIO_FIJO (un monto en pesos, no un factor ni un
-- porcentaje) ese rango es insuficiente y MySQL lanzaba
-- "Data truncation: Out of range value for column 'valor'".
--
-- Se mantiene scale=3 (no afecta los valores existentes) y se amplían
-- los dígitos enteros a 12, cubriendo cualquier precio fijo.
-- =============================================================

USE supermaster;

ALTER TABLE precios_inflados
    MODIFY COLUMN valor DECIMAL(15,3) NOT NULL;

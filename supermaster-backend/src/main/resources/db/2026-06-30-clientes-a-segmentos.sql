-- Migración: renombrar maestro genérico "Cliente" a "Segmento"
-- Sin datos: ambas tablas estaban vacías al aplicar (verificado 2026-06-30).
-- IDEMPOTENTE: cada paso se ejecuta solo si el estado viejo todavía existe, así que
--   el script puede re-ejecutarse sin error sobre una base ya (parcial o totalmente) migrada.
-- NOTA: NO afecta clientes reales del ERP Dux (deudas, etc.) — son otra cosa.

SET @schema := 'supermaster';

-- 1a. Renombrar tabla clientes -> segmentos (solo si 'clientes' aún existe)
SET @stmt := IF(
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='clientes') > 0,
    'RENAME TABLE supermaster.clientes TO supermaster.segmentos',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 1b. Renombrar tabla producto_cliente -> producto_segmento (solo si 'producto_cliente' aún existe)
SET @stmt := IF(
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='producto_cliente') > 0,
    'RENAME TABLE supermaster.producto_cliente TO supermaster.producto_segmento',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 2. Renombrar columna PK en segmentos: id_cliente -> id_segmento (solo si aún se llama id_cliente)
SET @stmt := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='segmentos' AND COLUMN_NAME='id_cliente') > 0,
    'ALTER TABLE supermaster.segmentos CHANGE COLUMN `id_cliente` `id_segmento` INT NOT NULL AUTO_INCREMENT',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 3. Renombrar columna FK en producto_segmento (drop FK+índice viejos, CHANGE COLUMN, recrear índice+FK)
--    Se dispara solo si la columna vieja id_cliente aún existe (estado viejo consistente).
SET @stmt := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='producto_segmento' AND COLUMN_NAME='id_cliente') > 0,
    'ALTER TABLE supermaster.producto_segmento DROP FOREIGN KEY `fk_id_cliente`, DROP INDEX `fk_id_cliente_idx`, CHANGE COLUMN `id_cliente` `id_segmento` INT NOT NULL, ADD INDEX `fk_id_segmento_idx` (`id_segmento`), ADD CONSTRAINT `fk_producto_segmento_segmento` FOREIGN KEY (`id_segmento`) REFERENCES supermaster.segmentos (`id_segmento`) ON DELETE CASCADE ON UPDATE CASCADE',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 4. Renombrar índice UNIQUE sobre nombre: cliente_UNIQUE -> segmento_UNIQUE (solo si aún existe el viejo)
SET @stmt := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='segmentos' AND INDEX_NAME='cliente_UNIQUE') > 0,
    'ALTER TABLE supermaster.segmentos RENAME INDEX cliente_UNIQUE TO segmento_UNIQUE',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

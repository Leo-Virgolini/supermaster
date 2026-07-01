-- Migración: renombrar maestro genérico "Cliente" a "Segmento"
-- Sin datos: ambas tablas estaban vacías al aplicar (verificado 2026-06-30).
-- One-shot; idempotencia no requerida.
-- NOTA: NO afecta clientes reales del ERP Dux (deudas, etc.) — son otra cosa.

-- 1. Renombrar tablas
RENAME TABLE supermaster.clientes TO supermaster.segmentos;
RENAME TABLE supermaster.producto_cliente TO supermaster.producto_segmento;

-- 2. Renombrar columna PK en segmentos
ALTER TABLE supermaster.segmentos
    CHANGE COLUMN `id_cliente` `id_segmento` INT NOT NULL AUTO_INCREMENT;

-- 3. Renombrar columna FK en producto_segmento
--    (primero drop FK y el índice que la soporta, luego CHANGE COLUMN, luego recrear FK)
ALTER TABLE supermaster.producto_segmento
    DROP FOREIGN KEY `fk_id_cliente`,
    DROP INDEX `fk_id_cliente_idx`,
    CHANGE COLUMN `id_cliente` `id_segmento` INT NOT NULL,
    ADD INDEX `fk_id_segmento_idx` (`id_segmento`),
    ADD CONSTRAINT `fk_producto_segmento_segmento`
        FOREIGN KEY (`id_segmento`) REFERENCES supermaster.segmentos (`id_segmento`)
        ON DELETE CASCADE ON UPDATE CASCADE;

-- 4. Renombrar índice UNIQUE sobre nombre (limpieza cosmética: nombre viejo era cliente_UNIQUE)
ALTER TABLE supermaster.segmentos RENAME INDEX cliente_UNIQUE TO segmento_UNIQUE;

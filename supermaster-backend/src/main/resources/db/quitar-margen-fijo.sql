-- Quita las columnas de margen fijo (ya no se usan). Ejecutar manualmente (ddl-auto=validate).
ALTER TABLE supermaster.producto_margen
    DROP COLUMN margen_fijo_minorista,
    DROP COLUMN margen_fijo_mayorista;

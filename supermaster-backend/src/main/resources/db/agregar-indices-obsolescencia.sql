-- ============================================================================
-- Migracion incremental: indices compuestos para acelerar UPDATE bulk de
-- obsolescencia. Aplicar DESPUES de agregar-flag-obsolescencia.sql si esa ya
-- corrio (caso contrario, el archivo principal ya incluye estos indices).
--
-- Sin estos indices, los UPDATE marcarObsoletoPorProductos/Canales hacen full
-- table scan filtrando por id_producto/id_canal en tablas que pueden tener
-- cientos de miles de filas.
-- ============================================================================

ALTER TABLE supermaster.producto_canal_precios
    ADD INDEX idx_pcp_producto_obsoleto (id_producto, obsoleto),
    ADD INDEX idx_pcp_canal_obsoleto (id_canal, obsoleto);

-- ============================================================================
-- Migracion: Flag de obsolescencia para precios calculados
--
-- Reemplaza el tracking en memoria de RecalculoPendienteService por columnas
-- persistidas en BD. Sobrevive a reinicios, permite mostrar "precio obsoleto"
-- por fila en el frontend, y habilita bulk SELECT/UPDATE para reducir N+1.
--
-- Granularidad:
--   - producto_canal_precios.obsoleto: precio existente desactualizado.
--   - canales.requiere_reevaluar_catalogo: cambio que puede agregar/quitar
--     productos al canal (regla, concepto, cuota, descuento, canal base).
--     Implica iterar TODO el catalogo contra ese canal.
-- ============================================================================

ALTER TABLE supermaster.producto_canal_precios
    ADD COLUMN obsoleto TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN motivo_obsoleto VARCHAR(255) NULL,
    ADD COLUMN marcado_obsoleto_at DATETIME NULL,
    -- Indice simple para SELECT WHERE obsoleto = TRUE (snapshot del banner).
    ADD INDEX idx_pcp_obsoleto (obsoleto),
    -- Indices compuestos para los UPDATE bulk: marcarObsoletoPorProductos / desmarcarObsoletoPorProducto
    -- usan WHERE id_producto = ? AND obsoleto = ?, idem WHERE id_canal = ? AND obsoleto = ?.
    -- Sin estos, los UPDATE hacen full scan en una tabla potencialmente grande.
    ADD INDEX idx_pcp_producto_obsoleto (id_producto, obsoleto),
    ADD INDEX idx_pcp_canal_obsoleto (id_canal, obsoleto);

ALTER TABLE supermaster.canales
    ADD COLUMN requiere_reevaluar_catalogo TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN motivo_reevaluar VARCHAR(255) NULL,
    ADD COLUMN marcado_reevaluar_at DATETIME NULL;

-- Categoría de Mercado Libre elegida para el producto (predictor): se manda en el alta del item.
-- ml_category_id es el ID que va a ML (ej. "MLA1055"); ml_category_nombre es el nombre denormalizado
-- (solo para mostrar en la UI sin re-llamar al predictor).
ALTER TABLE supermaster.productos
    ADD COLUMN ml_category_id VARCHAR(20) NULL,
    ADD COLUMN ml_category_nombre VARCHAR(255) NULL;

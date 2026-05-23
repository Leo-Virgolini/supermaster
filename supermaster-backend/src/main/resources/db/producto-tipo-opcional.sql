-- =============================================================================
-- Hace opcionales los campos tipo y clasif_gral en productos
-- (id_tipo y id_clasif_gral pasan de NOT NULL a NULL).
--
-- Motivación: la importación de la hoja MASTER de SUPER MASTER no trae datos
-- consistentes para esos campos. Se asignan después manualmente o desde otro
-- flujo (por ejemplo, recargando clasificaciones desde el Excel auxiliar).
-- =============================================================================

ALTER TABLE supermaster.productos
    MODIFY COLUMN id_tipo INT NULL;

ALTER TABLE supermaster.productos
    MODIFY COLUMN id_clasif_gral INT NULL;

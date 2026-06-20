-- Campos de mapeo a Dux.
-- IMPORTANTE: ejecutar DESPUES de sector-deposito.sql (el FK de productos.id_sector_deposito
-- referencia la tabla sectores_deposito creada en ese script).
ALTER TABLE supermaster.productos
    ADD COLUMN id_sector_deposito INT NULL,
    ADD CONSTRAINT fk_productos_sector_deposito
        FOREIGN KEY (id_sector_deposito) REFERENCES supermaster.sectores_deposito (id_sector_deposito);

ALTER TABLE supermaster.marcas
    ADD COLUMN codigo_dux VARCHAR(45) NULL;

ALTER TABLE supermaster.clasif_gral
    ADD COLUMN id_dux INT NULL;

ALTER TABLE supermaster.clasif_gastro
    ADD COLUMN id_dux INT NULL;

ALTER TABLE supermaster.proveedores ADD COLUMN id_dux INT NULL;

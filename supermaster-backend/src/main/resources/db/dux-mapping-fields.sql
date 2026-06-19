-- Campos de mapeo a Dux.
ALTER TABLE supermaster.productos
    ADD COLUMN id_unidad_medida INT NULL,
    ADD CONSTRAINT fk_productos_unidad_medida
        FOREIGN KEY (id_unidad_medida) REFERENCES supermaster.unidades_medida (id_unidad_medida);

ALTER TABLE supermaster.marcas
    ADD COLUMN codigo_dux VARCHAR(45) NULL;

ALTER TABLE supermaster.clasif_gral
    ADD COLUMN id_dux INT NULL;

ALTER TABLE supermaster.clasif_gastro
    ADD COLUMN id_dux INT NULL;

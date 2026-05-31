-- =============================================================================
-- Cambia largo / ancho / alto de DECIMAL(10,2) a VARCHAR(45) en productos.
--
-- Motivación: los valores en la fuente (hoja MASTER del nuevo Excel
-- NUEVO SUPER MASTER.xlsx) vienen como texto con unidades y rangos
-- (ej: "18cm", "31 | 34 cm", "12,5 cm"), imposibles de representar como
-- número. Se almacenan tal cual para preservar la información original.
-- =============================================================================

ALTER TABLE supermaster.productos
    MODIFY COLUMN largo VARCHAR(45) NULL;

ALTER TABLE supermaster.productos
    MODIFY COLUMN ancho VARCHAR(45) NULL;

ALTER TABLE supermaster.productos
    MODIFY COLUMN alto VARCHAR(45) NULL;

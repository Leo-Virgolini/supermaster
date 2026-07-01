-- Familia de User Products (modelo nuevo de variantes ML) a nivel publicacion.
-- ddl-auto=validate: aplicar a mano en la BD antes de arrancar el backend.
ALTER TABLE supermaster.mlas
  ADD COLUMN family_id   VARCHAR(30)  NULL AFTER mlau,
  ADD COLUMN family_name VARCHAR(255) NULL AFTER family_id;

CREATE INDEX idx_mlas_family_id ON supermaster.mlas (family_id);

-- Agrega el flag "No aplica" por atributo de ficha técnica de ML.
-- Permite que el usuario marque un atributo como no-aplicable (se muestra el campo
-- deshabilitado y no se envía a ML), persistiéndolo entre ediciones.
ALTER TABLE supermaster.producto_ml_atributo
  ADD COLUMN no_aplica BOOLEAN NOT NULL DEFAULT FALSE;

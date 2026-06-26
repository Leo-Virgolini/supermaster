-- Descripción manual del producto (texto plano cargado por el usuario).
-- Se combina con la descripción autogenerada al publicar en ML (texto plano) y Nube (HTML).
ALTER TABLE supermaster.productos
  ADD COLUMN descripcion TEXT NULL;

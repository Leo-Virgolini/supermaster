-- Tres títulos por plataforma. Ejecutar manualmente (ddl-auto=validate).
-- CHANGE COLUMN preserva los datos existentes.
ALTER TABLE supermaster.productos
  CHANGE COLUMN descripcion titulo_dux VARCHAR(100) NOT NULL,
  CHANGE COLUMN titulo_web titulo_nube VARCHAR(100) NULL,
  ADD COLUMN titulo_ml VARCHAR(100) NULL AFTER titulo_dux;

-- Se elimina la imagen manual del producto: las imágenes se resuelven siempre por SKU
-- desde la carpeta de imágenes. Aplicar a mano antes de arrancar (ddl-auto=validate).
ALTER TABLE productos DROP COLUMN imagen_url;

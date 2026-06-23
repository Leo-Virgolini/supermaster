-- Dimensiones del paquete de envío para Mercado Libre (alto/ancho/largo en cm, peso en kg).
-- Nullable: solo se cargan para productos que se publican en ML.
ALTER TABLE productos
  ADD COLUMN ml_paq_alto  DECIMAL(6,2) NULL,
  ADD COLUMN ml_paq_ancho DECIMAL(6,2) NULL,
  ADD COLUMN ml_paq_largo DECIMAL(6,2) NULL,
  ADD COLUMN ml_paq_peso  DECIMAL(8,3) NULL;

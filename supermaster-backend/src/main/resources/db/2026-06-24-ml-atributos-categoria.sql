-- ML — Atributos de categoría en la publicación.
-- Columna del código universal (EAN/GTIN) + tabla hija de atributos de categoría guardados.
-- ddl-auto=validate: aplicar a mano en la BD antes de arrancar el backend.

ALTER TABLE supermaster.productos ADD COLUMN ean VARCHAR(20) NULL AFTER ml_category_nombre;

CREATE TABLE supermaster.producto_ml_atributo (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  id_producto  INT          NOT NULL,
  attribute_id VARCHAR(60)  NOT NULL,
  value_id     VARCHAR(60)  NULL,
  value_name   VARCHAR(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_producto_attribute (id_producto, attribute_id),
  CONSTRAINT fk_pma_producto FOREIGN KEY (id_producto)
    REFERENCES supermaster.productos (id_producto) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

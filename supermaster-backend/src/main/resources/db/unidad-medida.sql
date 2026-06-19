-- Catálogo de unidades de medida de Dux. El id DEBE coincidir con el id real
-- de Dux (no autogenerado). Los ids 1..22 de abajo son PROVISORIOS: reemplazar
-- por los ids reales de Dux antes de exportar a producción.
CREATE TABLE IF NOT EXISTS supermaster.unidades_medida (
    id_unidad_medida INT          NOT NULL PRIMARY KEY,
    codigo           VARCHAR(20)  NOT NULL,
    CONSTRAINT uk_unidades_medida_codigo UNIQUE (codigo)
);

INSERT INTO supermaster.unidades_medida (id_unidad_medida, codigo) VALUES
    (1,'T1'),(2,'T3'),(3,'J2-C'),(4,'J2-F'),(5,'J1-D'),(6,'J3-D'),(7,'J3-B'),
    (8,'J2-H'),(9,'J2-G'),(10,'T2'),(11,'J1-E'),(12,'J2-D'),(13,'J2-B'),
    (14,'J1-B'),(15,'J2-I'),(16,'J3-C'),(17,'J3-A'),(18,'COMBOS'),(19,'J2-A'),
    (20,'J1-C'),(21,'J1-A'),(22,'J2-E');

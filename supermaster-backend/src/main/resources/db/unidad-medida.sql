-- Catálogo de unidades de medida de Dux. El PK es autoincremental (interno).
-- id_dux almacena el id real de Dux y debe reemplazarse por los valores reales
-- antes de exportar a producción. Los ids 1..22 de abajo son PROVISORIOS.
CREATE TABLE IF NOT EXISTS supermaster.unidades_medida (
    id_unidad_medida INT         AUTO_INCREMENT PRIMARY KEY,
    codigo           VARCHAR(20) NOT NULL,
    id_dux           INT         NULL,
    CONSTRAINT uk_unidades_medida_codigo UNIQUE (codigo)
);

INSERT INTO supermaster.unidades_medida (codigo, id_dux) VALUES
    ('T1',1),('T3',2),('J2-C',3),('J2-F',4),('J1-D',5),('J3-D',6),('J3-B',7),
    ('J2-H',8),('J2-G',9),('T2',10),('J1-E',11),('J2-D',12),('J2-B',13),
    ('J1-B',14),('J2-I',15),('J3-C',16),('J3-A',17),('COMBOS',18),('J2-A',19),
    ('J1-C',20),('J1-A',21),('J2-E',22);

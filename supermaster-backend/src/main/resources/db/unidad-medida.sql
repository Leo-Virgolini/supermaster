-- Catálogo de unidades de medida de Dux. El PK es autoincremental (interno).
-- id_dux almacena el id real de Dux y debe reemplazarse por los valores reales
-- antes de exportar a producción. Los ids 1..22 de abajo son PROVISORIOS.
--
-- ATENCIÓN: si la tabla `unidades_medida` ya fue creada con la versión anterior
-- de este script (donde el PK era igual al id de Dux y NO existía la columna
-- `id_dux`), el CREATE TABLE IF NOT EXISTS de abajo es un NO-OP y la nueva
-- estructura (PK AUTO_INCREMENT + columna id_dux) NO se aplicará.
-- En ese caso, antes de volver a ejecutar este script hay que eliminar la tabla:
--   DROP TABLE IF EXISTS supermaster.unidades_medida;
-- Es seguro hacerlo porque es un catálogo fijo y todavía no hay FKs de
-- producción que dependan de sus filas.
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

-- =============================================================
-- Migracion: consolidar conceptos FLAG en canonicos compartidos.
--
-- Antes de esta migracion, cada canal tenia su propio concepto FLAG:
--   ML_IVA / KH_IVA           (FLAG_APLICAR_IVA)
--   ML_MARGEN_MIN / KH_MARGEN_MIN  (FLAG_USAR_MARGEN_MINORISTA)
--   LG_MARGEN_MAY              (FLAG_USAR_MARGEN_MAYORISTA)
--   ML_ENVIO                   (FLAG_INCLUIR_ENVIO)
--   KH_PRECIO_INFLADO          (FLAG_APLICAR_PRECIO_INFLADO)
--
-- Como los conceptos FLAG no tienen porcentaje propio (son solo
-- marcadores de comportamiento), tenerlos duplicados por canal es
-- innecesario. Esta migracion los unifica en conceptos canonicos:
--   IVA, MARGEN_MIN, MARGEN_MAY, ENVIO_ML, INFLADO, COMISION_ML, FINANCIACION_PROVEEDOR
--
-- La migracion preserva todas las relaciones canal_concepto y
-- canal_concepto_regla — solo cambia el id_concepto al canonico.
--
-- IDEMPOTENTE: se puede correr varias veces sin efectos no deseados.
--
-- IMPORTANTE: Despues de correr esta migracion, NO es necesario
-- recalcular precios — los flags se evaluan por aplica_sobre, no
-- por nombre, asi que el comportamiento es identico.
-- =============================================================

USE supermaster;

-- -------------------------------------------------------------
-- Paso 1: Asegurar que los conceptos canonicos existen
-- -------------------------------------------------------------
INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion)
SELECT 'IVA', NULL, 'FLAG_APLICAR_IVA', 'Habilita la aplicacion del IVA del producto'
WHERE NOT EXISTS (SELECT 1 FROM conceptos_calculo WHERE nombre = 'IVA');

INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion)
SELECT 'MARGEN_MIN', NULL, 'FLAG_USAR_MARGEN_MINORISTA', 'Usa margen minorista del producto (GAN.MIN.ML)'
WHERE NOT EXISTS (SELECT 1 FROM conceptos_calculo WHERE nombre = 'MARGEN_MIN');

INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion)
SELECT 'MARGEN_MAY', NULL, 'FLAG_USAR_MARGEN_MAYORISTA', 'Usa margen mayorista del producto (%GAN)'
WHERE NOT EXISTS (SELECT 1 FROM conceptos_calculo WHERE nombre = 'MARGEN_MAY');

INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion)
SELECT 'ENVIO_ML', NULL, 'FLAG_INCLUIR_ENVIO', 'Suma precio_envio del MLA al costo antes de impuestos'
WHERE NOT EXISTS (SELECT 1 FROM conceptos_calculo WHERE nombre = 'ENVIO_ML');

INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion)
SELECT 'INFLADO', NULL, 'FLAG_APLICAR_PRECIO_INFLADO', 'Habilita que se calculen los precios inflados si tienen la regla'
WHERE NOT EXISTS (SELECT 1 FROM conceptos_calculo WHERE nombre = 'INFLADO');

INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion)
SELECT 'COMISION_ML', NULL, 'FLAG_COMISION_ML', 'Habilita la comision de cada producto de Mercado Libre sobre el PVP (toma el % del MLA del producto)'
WHERE NOT EXISTS (SELECT 1 FROM conceptos_calculo WHERE nombre = 'COMISION_ML');

INSERT INTO conceptos_calculo (nombre, porcentaje, aplica_sobre, descripcion)
SELECT 'FINANCIACION_PROVEEDOR', NULL, 'FLAG_FINANCIACION_PROVEEDOR', 'Habilita la aplicacion del % de financiacion del proveedor del producto sobre el costo. Concepto canonico reusable entre canales.'
WHERE NOT EXISTS (SELECT 1 FROM conceptos_calculo WHERE nombre = 'FINANCIACION_PROVEEDOR');

-- -------------------------------------------------------------
-- Paso 2: Construir tabla temporal de mapeo (duplicado -> canonico)
-- -------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS _flag_mapeo;
CREATE TEMPORARY TABLE _flag_mapeo (
    id_duplicado INT PRIMARY KEY,
    id_canonico INT NOT NULL
);

INSERT INTO _flag_mapeo (id_duplicado, id_canonico)
SELECT dup.id_concepto, can.id_concepto
FROM conceptos_calculo dup
JOIN conceptos_calculo can ON can.nombre = 'IVA'
WHERE dup.nombre IN ('ML_IVA', 'KH_IVA') AND dup.id_concepto <> can.id_concepto;

INSERT INTO _flag_mapeo (id_duplicado, id_canonico)
SELECT dup.id_concepto, can.id_concepto
FROM conceptos_calculo dup
JOIN conceptos_calculo can ON can.nombre = 'MARGEN_MIN'
WHERE dup.nombre IN ('ML_MARGEN_MIN', 'KH_MARGEN_MIN') AND dup.id_concepto <> can.id_concepto;

INSERT INTO _flag_mapeo (id_duplicado, id_canonico)
SELECT dup.id_concepto, can.id_concepto
FROM conceptos_calculo dup
JOIN conceptos_calculo can ON can.nombre = 'MARGEN_MAY'
WHERE dup.nombre = 'LG_MARGEN_MAY' AND dup.id_concepto <> can.id_concepto;

INSERT INTO _flag_mapeo (id_duplicado, id_canonico)
SELECT dup.id_concepto, can.id_concepto
FROM conceptos_calculo dup
JOIN conceptos_calculo can ON can.nombre = 'ENVIO_ML'
WHERE dup.nombre IN ('ML_ENVIO', 'ENVIO') AND dup.id_concepto <> can.id_concepto;

INSERT INTO _flag_mapeo (id_duplicado, id_canonico)
SELECT dup.id_concepto, can.id_concepto
FROM conceptos_calculo dup
JOIN conceptos_calculo can ON can.nombre = 'INFLADO'
WHERE dup.nombre IN ('KH_PRECIO_INFLADO', 'PRECIO_INFLADO') AND dup.id_concepto <> can.id_concepto;

INSERT INTO _flag_mapeo (id_duplicado, id_canonico)
SELECT dup.id_concepto, can.id_concepto
FROM conceptos_calculo dup
JOIN conceptos_calculo can ON can.nombre = 'COMISION_ML'
WHERE dup.nombre = 'ML_COMI' AND dup.id_concepto <> can.id_concepto;

-- -------------------------------------------------------------
-- Paso 3: Migrar canal_concepto desde duplicados al canonico
--   3a) Insertar relacion canonica donde el canal tenia el duplicado
--       (INSERT IGNORE evita conflictos si la relacion ya existe)
--   3b) Eliminar las relaciones que apuntaban al duplicado
-- -------------------------------------------------------------
INSERT IGNORE INTO canal_concepto (id_canal, id_concepto)
SELECT cc.id_canal, fm.id_canonico
FROM canal_concepto cc
JOIN _flag_mapeo fm ON fm.id_duplicado = cc.id_concepto;

DELETE cc FROM canal_concepto cc
JOIN _flag_mapeo fm ON fm.id_duplicado = cc.id_concepto;

-- -------------------------------------------------------------
-- Paso 4: Migrar canal_concepto_regla (por si algun flag tiene reglas)
--   Los flags normalmente no tienen reglas, pero migramos por las dudas.
-- -------------------------------------------------------------
INSERT INTO canal_concepto_regla (id_canal, id_concepto, tipo_regla, tag, tiene_envio)
SELECT r.id_canal, fm.id_canonico, r.tipo_regla, r.tag, r.tiene_envio
FROM canal_concepto_regla r
JOIN _flag_mapeo fm ON fm.id_duplicado = r.id_concepto
WHERE NOT EXISTS (
    SELECT 1 FROM canal_concepto_regla r2
    WHERE r2.id_canal = r.id_canal
      AND r2.id_concepto = fm.id_canonico
      AND r2.tipo_regla = r.tipo_regla
      AND (r2.tag <=> r.tag)
      AND (r2.tiene_envio <=> r.tiene_envio)
);

DELETE r FROM canal_concepto_regla r
JOIN _flag_mapeo fm ON fm.id_duplicado = r.id_concepto;

-- -------------------------------------------------------------
-- Paso 5: Eliminar los conceptos duplicados
-- -------------------------------------------------------------
DELETE cc FROM conceptos_calculo cc
JOIN _flag_mapeo fm ON fm.id_duplicado = cc.id_concepto;

DROP TEMPORARY TABLE _flag_mapeo;

-- -------------------------------------------------------------
-- Paso 6 (opcional): verificar resultado
-- Descomentar para ver que conceptos FLAG quedaron asignados a cada canal
-- -------------------------------------------------------------
-- SELECT c.nombre AS canal, cc.nombre AS concepto, cc.aplica_sobre
-- FROM canal_concepto kc
-- JOIN canales c ON c.id_canal = kc.id_canal
-- JOIN conceptos_calculo cc ON cc.id_concepto = kc.id_concepto
-- WHERE cc.aplica_sobre LIKE 'FLAG_%'
-- ORDER BY c.nombre, cc.nombre;

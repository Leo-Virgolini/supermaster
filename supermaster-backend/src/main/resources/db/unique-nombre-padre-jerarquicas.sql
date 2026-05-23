-- =============================================================================
-- Reemplaza UNIQUE(nombre) por UNIQUE(nombre, id_padre) en las tablas
-- jerárquicas: marcas, tipos, clasif_gral, clasif_gastro
--
-- Motivación: estas tablas permiten registros con el mismo nombre siempre que
-- cuelguen de padres distintos (por ejemplo, "REPUESTOS" puede aparecer bajo
-- varias categorías). La constraint compuesta evita duplicados reales
-- (mismo nombre + mismo padre) sin romper la jerarquía.
--
-- Paso 1: DROP de los UNIQUE viejos sobre solo `nombre` que dejó Hibernate
--         en versiones anteriores (suelen llamarse `nombre_UNIQUE` o `UK_xxx`).
-- Paso 2: ADD del UNIQUE compuesto (nombre, id_padre).
--
-- Si el índice tiene otro nombre en tu BD, ejecutar primero:
--    SHOW INDEXES FROM supermaster.marcas;
--    SHOW INDEXES FROM supermaster.tipos;
--    SHOW INDEXES FROM supermaster.clasif_gral;
--    SHOW INDEXES FROM supermaster.clasif_gastro;
-- y reemplazar los nombres en los DROP INDEX de abajo.
--
-- Nota MySQL/InnoDB: una columna NULL se considera "distinta de cualquier otra"
-- en un UNIQUE, así que esta constraint NO previene múltiples filas con
-- nombre repetido y id_padre = NULL.
-- =============================================================================

-- Paso 1: eliminar los UNIQUE viejos sobre solo `nombre`
ALTER TABLE supermaster.marcas        DROP INDEX nombre_UNIQUE;
ALTER TABLE supermaster.tipos         DROP INDEX nombre_UNIQUE;
ALTER TABLE supermaster.clasif_gral   DROP INDEX nombre_UNIQUE;
ALTER TABLE supermaster.clasif_gastro DROP INDEX nombre_UNIQUE;

-- Paso 2: agregar el UNIQUE compuesto (nombre, id_padre)
ALTER TABLE supermaster.marcas
    ADD CONSTRAINT uk_marcas_nombre_padre UNIQUE (nombre, id_padre);

ALTER TABLE supermaster.tipos
    ADD CONSTRAINT uk_tipos_nombre_padre UNIQUE (nombre, id_padre);

ALTER TABLE supermaster.clasif_gral
    ADD CONSTRAINT uk_clasif_gral_nombre_padre UNIQUE (nombre, id_padre);

ALTER TABLE supermaster.clasif_gastro
    ADD CONSTRAINT uk_clasif_gastro_nombre_padre UNIQUE (nombre, id_padre);

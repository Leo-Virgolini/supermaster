-- =============================================================
-- Migración: ajustes de descripciones canónicas
-- =============================================================
-- 1) FINANCIACION_PROVEEDOR: era específica de "LINEA GE"; el FLAG es genérico
--    y reusable entre canales, así que se hace genérica.
-- =============================================================

USE supermaster;

UPDATE conceptos_calculo
   SET descripcion = 'Habilita la aplicacion del % de financiacion del proveedor del producto sobre el costo. Concepto canonico reusable entre canales.'
 WHERE nombre = 'FINANCIACION_PROVEEDOR';

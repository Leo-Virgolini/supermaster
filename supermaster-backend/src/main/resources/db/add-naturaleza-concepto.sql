-- =====================================================================
-- FASE 1: Agregar columna `naturaleza` a conceptos_calculo
-- =====================================================================
-- Modela la NATURALEZA CONTABLE del concepto (cómo impacta los indicadores)
-- de forma INDEPENDIENTE de `aplica_sobre` (que define la posición y operación
-- matemática en la fórmula del PVP).
--
-- La columna es nullable: si está NULL, el código deriva la naturaleza
-- del default declarado en el enum AplicaSobre. El override por concepto se
-- usa cuando dos conceptos con el mismo aplica_sobre necesitan tratamiento
-- distinto en los indicadores (ej: "logística real" vs "inflación pura"
-- ambos en GASTO_POST_GANANCIA).
-- =====================================================================

ALTER TABLE supermaster.conceptos_calculo
  ADD COLUMN naturaleza ENUM(
    'COSTO_PRODUCTO',
    'COSTO_VENTA',
    'IMPUESTO',
    'MARKUP',
    'INFLACION',
    'DESCUENTO',
    'BASE',
    'COSMETICO'
  ) NULL AFTER aplica_sobre;

-- =====================================================================
-- Popular `naturaleza` para todos los conceptos existentes según el default
-- declarado en AplicaSobre. Después de esto, cada concepto tiene su naturaleza
-- explícita y vos podés EDITAR la de los sospechosos (LG_LOG, LG_MKT,
-- ML_PRTACHADO, KH_CUPON, LG_INF) si querés que cuenten como COSTO_VENTA
-- en lugar de INFLACION.
-- =====================================================================

-- IMPORTANTE: todos los UPDATE filtran por `naturaleza IS NULL` para NO pisar
-- overrides explícitos seteados desde los seeds o desde la UI. Solo populan
-- los conceptos que aún no tienen naturaleza declarada.

UPDATE supermaster.conceptos_calculo SET naturaleza = 'COSTO_PRODUCTO'
  WHERE naturaleza IS NULL
    AND aplica_sobre = 'FLAG_FINANCIACION_PROVEEDOR';

UPDATE supermaster.conceptos_calculo SET naturaleza = 'COSTO_VENTA'
  WHERE naturaleza IS NULL
    AND aplica_sobre IN (
      'FLAG_INCLUIR_ENVIO',
      'COMISION_SOBRE_PVP',
      'FLAG_COMISION_ML',
      'COSTO_OCULTO_PVP',
      'GASTO_SIN_INFLAR_PVP'
    );

UPDATE supermaster.conceptos_calculo SET naturaleza = 'IMPUESTO'
  WHERE naturaleza IS NULL
    AND aplica_sobre IN (
      'FLAG_APLICAR_IVA',
      'IMPUESTO_EN_FACTOR_IMP'
    );

UPDATE supermaster.conceptos_calculo SET naturaleza = 'MARKUP'
  WHERE naturaleza IS NULL
    AND aplica_sobre IN (
      'FLAG_USAR_MARGEN_MINORISTA',
      'FLAG_USAR_MARGEN_MAYORISTA',
      'AJUSTE_MARGEN_PUNTOS',
      'AJUSTE_MARGEN_PROPORCIONAL'
    );

UPDATE supermaster.conceptos_calculo SET naturaleza = 'INFLACION'
  WHERE naturaleza IS NULL
    AND aplica_sobre IN (
      'GASTO_SOBRE_COSTO',
      'GASTO_POST_GANANCIA',
      'GASTO_POST_IMPUESTOS',
      'INFLACION_DIVISOR_FINAL'
    );

UPDATE supermaster.conceptos_calculo SET naturaleza = 'DESCUENTO'
  WHERE naturaleza IS NULL
    AND aplica_sobre = 'DESCUENTO_PORCENTUAL';

UPDATE supermaster.conceptos_calculo SET naturaleza = 'BASE'
  WHERE naturaleza IS NULL
    AND aplica_sobre IN (
      'CALCULO_SOBRE_CANAL_BASE',
      'CALCULO_SOBRE_CANAL_BASE_RESELLER'
    );

UPDATE supermaster.conceptos_calculo SET naturaleza = 'COSMETICO'
  WHERE naturaleza IS NULL
    AND aplica_sobre = 'FLAG_APLICAR_PRECIO_INFLADO';

-- Verificación: todos los conceptos deberían tener naturaleza después de esto.
-- Si alguno queda en NULL, es porque su aplica_sobre no está en este script
-- (= valor nuevo del enum agregado después). El runtime lo resuelve igual
-- vía AplicaSobre.getNaturalezaDefault().
-- SELECT COUNT(*) FROM supermaster.conceptos_calculo WHERE naturaleza IS NULL;

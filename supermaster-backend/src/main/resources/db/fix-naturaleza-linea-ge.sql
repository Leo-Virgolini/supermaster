-- =============================================================
-- Migración: corregir naturaleza de LG_LOG y LG_MKT (LÍNEA GE)
-- =============================================================
-- Razón: ambos están con aplicaSobre = GASTO_POST_GANANCIA, cuyo default
-- de naturaleza es INFLACION (sube PVP sin plata que sale del dueño).
--
-- Pero en la realidad de LÍNEA GE:
--   - LG_LOG (logística) es plata real al transportista.
--   - LG_MKT (marketing) es plata real en publicidad.
--
-- Si queda como INFLACION, el sistema sobreestima la ganancia (no descuenta
-- esos costos del ingreso neto). Con COSTO_VENTA, los indicadores reflejan
-- la ganancia real del dueño después de pagar logística y marketing.
--
-- Reversible: SÍ. Para volver atrás:
--   UPDATE supermaster.conceptos_calculo SET naturaleza = 'INFLACION'
--   WHERE nombre IN ('LG_LOG', 'LG_MKT');
-- =============================================================

UPDATE supermaster.conceptos_calculo
   SET naturaleza = 'COSTO_VENTA'
 WHERE nombre IN ('LG_LOG', 'LG_MKT');

-- =============================================================
-- POST-MIGRACIÓN:
--   Disparar un Recálculo Masivo desde la app — los PVPs no cambian
--   (la matemática de GASTO_POST_GANANCIA sigue igual), pero los
--   indicadores ganancia / margenSobreIngresoNeto / margenSobrePvp /
--   markupPorcentaje se ajustan a la realidad contable.
-- =============================================================

-- =============================================================
-- Migración: simplificar conceptos MAQUINA en KT GASTRO
-- =============================================================
-- Razón: la fórmula Excel actual de KT GASTRO calcula MAQUINA como
--   PVP = pvpMLSinEnvio * 0,95
-- donde pvpMLSinEnvio usa: ML_COMI + ML_MKT + cuotasML + ML_SERTEC.
-- Y la ganancia usa: KG_SERTEC + KG_MKT + KG_COMVEND.
--
-- Los conceptos KG_MAQ_EMB (COMISION_SOBRE_PVP con naturaleza INFLACION, embalaje) y
-- KG_CO_MAQCENV (INFLACION_DIVISOR_FINAL, costo oculto envío) NO aparecen
-- en ninguna de las dos fórmulas, por lo que se eliminan.
--
-- KG_COMVEND se MANTIENE (es GASTO_SIN_INFLAR_PVP: cuenta como costo del
-- vendedor en cálculo de ganancia, aunque no afecte el PVP).
--
-- Resultado esperado: PVPs idénticos antes y después en MAQUINA, ya que
-- los conceptos eliminados eran COMISION_SOBRE_PVP(INFLACION) / INFLACION_DIVISOR_FINAL
-- y solo se aplicaban con la regla tieneEnvio=TRUE. Si tu instalación
-- nunca tuvo MAQUINAS con envío, no hay impacto. Si las tenías, el PVP
-- de esas máquinas BAJA porque salen los inflators.
--
-- Reversible: NO. Hacer backup antes.
-- =============================================================

BEGIN;

-- 1) Borrar reglas (canal_concepto_regla) que apuntan a KG_MAQ_EMB o KG_CO_MAQCENV.
DELETE FROM canal_concepto_regla
WHERE id_concepto IN (
    SELECT id_concepto FROM conceptos_calculo
    WHERE nombre IN ('KG_MAQ_EMB', 'KG_CO_MAQCENV')
);

-- 2) Quitar las asignaciones canal_concepto que apuntan a esos conceptos.
DELETE FROM canal_concepto
WHERE id_concepto IN (
    SELECT id_concepto FROM conceptos_calculo
    WHERE nombre IN ('KG_MAQ_EMB', 'KG_CO_MAQCENV')
);

-- 3) Eliminar los conceptos.
DELETE FROM conceptos_calculo
WHERE nombre IN ('KG_MAQ_EMB', 'KG_CO_MAQCENV');

COMMIT;

-- =============================================================
-- POST-MIGRACIÓN:
--   Disparar un Recálculo Masivo desde la app para regenerar los
--   producto_canal_precios. Verificá que los PVPs MAQUINA en KT GASTRO
--   coincidan con la fórmula Excel:
--     PVP = baseImp / (1 - 0,21) * 0,95
--     gastosSobrePVP = mla.comision + KG_MKT(5) + KG_MAQ_SERTEC(3) = 21%
-- =============================================================

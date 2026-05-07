package ar.com.leo.super_master_backend.dominio.canal.service;

import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoDTO;

import java.util.List;

public interface CanalConceptoService {

    List<CanalConceptoDTO> listarPorCanal(Integer canalId);

    CanalConceptoDTO asignarConcepto(Integer canalId, Integer conceptoId);

    void eliminarConcepto(Integer canalId, Integer conceptoId);

    /**
     * Copia los conceptos asignados (canal_concepto) y las reglas
     * (canal_concepto_regla) de un canal origen a un canal destino.
     *
     * Si el destino ya tiene asignaciones, las nuevas se agregan sin
     * pisar las existentes (idempotente: si ya existe la asignación,
     * se saltea; las reglas se duplican igual al ser independientes
     * por id auto-incremental).
     *
     * @return cantidad de conceptos copiados al destino.
     */
    int clonarConceptosDesde(Integer targetCanalId, Integer srcCanalId);
}
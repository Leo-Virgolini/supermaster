package ar.com.leo.super_master_backend.dominio.canal.service;

import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoReglaCreateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoReglaDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoReglaUpdateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoReglaPatchDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CanalConceptoReglaService {

    Page<CanalConceptoReglaDTO> listar(Pageable pageable);

    CanalConceptoReglaDTO obtener(Long id);

    CanalConceptoReglaDTO crear(CanalConceptoReglaCreateDTO dto);

    CanalConceptoReglaDTO actualizar(Long id, CanalConceptoReglaUpdateDTO dto);

    CanalConceptoReglaDTO patch(Long id, CanalConceptoReglaPatchDTO patch);

    void eliminar(Long id);

    List<CanalConceptoReglaDTO> listarPorCanal(Integer canalId);

    List<CanalConceptoReglaDTO> listarPorConcepto(Integer conceptoId);
}


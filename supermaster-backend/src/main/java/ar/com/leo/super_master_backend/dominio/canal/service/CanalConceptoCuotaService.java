package ar.com.leo.super_master_backend.dominio.canal.service;

import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoCuotaCreateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoCuotaDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoCuotaUpdateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoCuotaPatchDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CanalConceptoCuotaService {

    Page<CanalConceptoCuotaDTO> listar(String search, Integer canalId, Pageable pageable);

    CanalConceptoCuotaDTO obtener(Long id);

    CanalConceptoCuotaDTO crear(CanalConceptoCuotaCreateDTO dto);

    CanalConceptoCuotaDTO actualizar(Long id, CanalConceptoCuotaUpdateDTO dto);

    CanalConceptoCuotaDTO patch(Long id, CanalConceptoCuotaPatchDTO patch);

    void eliminar(Long id);

    List<CanalConceptoCuotaDTO> listarPorCanal(Integer canalId);

    List<CanalConceptoCuotaDTO> listarPorCanalYCuotas(Integer canalId, Integer cuotas);
}


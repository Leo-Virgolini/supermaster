package ar.com.leo.super_master_backend.dominio.canal.service;

import ar.com.leo.super_master_backend.dominio.canal.dto.CanalCreateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalUpdateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalPatchDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface CanalService {
    Page<CanalDTO> listar(String search, Pageable pageable);

    CanalDTO obtener(Integer id);

    CanalDTO crear(CanalCreateDTO dto);

    CanalDTO actualizar(Integer id, CanalUpdateDTO dto);

    CanalDTO patch(Integer id, CanalPatchDTO patch);

    void eliminar(Integer id);

    // 🔥 nueva regla de negocio
    void actualizarMargen(Integer canalId, BigDecimal nuevoMargen);
}


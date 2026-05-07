package ar.com.leo.super_master_backend.dominio.regla_descuento.service;

import ar.com.leo.super_master_backend.dominio.regla_descuento.dto.ReglaDescuentoCreateDTO;
import ar.com.leo.super_master_backend.dominio.regla_descuento.dto.ReglaDescuentoDTO;
import ar.com.leo.super_master_backend.dominio.regla_descuento.dto.ReglaDescuentoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.regla_descuento.dto.ReglaDescuentoPatchDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ReglaDescuentoService {

    Page<ReglaDescuentoDTO> listar(Pageable pageable);

    List<ReglaDescuentoDTO> listarPorCanal(Integer canalId);

    ReglaDescuentoDTO obtener(Integer id);

    ReglaDescuentoDTO crear(ReglaDescuentoCreateDTO dto);

    ReglaDescuentoDTO actualizar(Integer id, ReglaDescuentoUpdateDTO dto);

    ReglaDescuentoDTO patch(Integer id, ReglaDescuentoPatchDTO patch);

    void eliminar(Integer id);
}


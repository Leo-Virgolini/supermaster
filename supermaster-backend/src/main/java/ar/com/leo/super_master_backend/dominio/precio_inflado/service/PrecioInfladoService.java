package ar.com.leo.super_master_backend.dominio.precio_inflado.service;

import ar.com.leo.super_master_backend.dominio.precio_inflado.dto.PrecioInfladoCreateDTO;
import ar.com.leo.super_master_backend.dominio.precio_inflado.dto.PrecioInfladoDTO;
import ar.com.leo.super_master_backend.dominio.precio_inflado.dto.PrecioInfladoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.precio_inflado.dto.PrecioInfladoPatchDTO;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface PrecioInfladoService {

    Page<PrecioInfladoDTO> listar(String search, Pageable pageable);

    PrecioInfladoDTO obtenerPorId(Integer id);

    PrecioInfladoDTO obtenerPorCodigo(String codigo);

    PrecioInfladoDTO crear(PrecioInfladoCreateDTO dto);

    PrecioInfladoDTO actualizar(Integer id, PrecioInfladoUpdateDTO dto);

    PrecioInfladoDTO patch(Integer id, PrecioInfladoPatchDTO patch);

    void eliminar(Integer id);
}


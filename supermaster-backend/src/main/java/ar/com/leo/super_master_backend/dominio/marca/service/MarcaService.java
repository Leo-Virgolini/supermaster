package ar.com.leo.super_master_backend.dominio.marca.service;

import ar.com.leo.super_master_backend.dominio.marca.dto.MarcaCreateDTO;
import ar.com.leo.super_master_backend.dominio.marca.dto.MarcaDTO;
import ar.com.leo.super_master_backend.dominio.marca.dto.MarcaUpdateDTO;
import ar.com.leo.super_master_backend.dominio.marca.dto.MarcaPatchDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MarcaService {

    Page<MarcaDTO> listar(String search, Pageable pageable);

    MarcaDTO obtener(Integer id);

    MarcaDTO crear(MarcaCreateDTO dto);

    MarcaDTO actualizar(Integer id, MarcaUpdateDTO dto);

    MarcaDTO patch(Integer id, MarcaPatchDTO patch);

    void eliminar(Integer id);

    List<ProductoResumenDTO> listarProductos(Integer marcaId);
}


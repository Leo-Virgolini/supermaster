package ar.com.leo.super_master_backend.dominio.origen.service;

import ar.com.leo.super_master_backend.dominio.origen.dto.OrigenCreateDTO;
import ar.com.leo.super_master_backend.dominio.origen.dto.OrigenDTO;
import ar.com.leo.super_master_backend.dominio.origen.dto.OrigenPatchDTO;
import ar.com.leo.super_master_backend.dominio.origen.dto.OrigenUpdateDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OrigenService {

    Page<OrigenDTO> listar(String search, Pageable pageable);


    OrigenDTO obtener(Integer id);

    OrigenDTO crear(OrigenCreateDTO dto);

    OrigenDTO actualizar(Integer id, OrigenUpdateDTO dto);

    OrigenDTO patch(Integer id, OrigenPatchDTO patch);

    void eliminar(Integer id);

    List<ProductoResumenDTO> listarProductos(Integer origenId);
}


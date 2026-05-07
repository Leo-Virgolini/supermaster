package ar.com.leo.super_master_backend.dominio.material.service;

import ar.com.leo.super_master_backend.dominio.material.dto.MaterialCreateDTO;
import ar.com.leo.super_master_backend.dominio.material.dto.MaterialDTO;
import ar.com.leo.super_master_backend.dominio.material.dto.MaterialUpdateDTO;
import ar.com.leo.super_master_backend.dominio.material.dto.MaterialPatchDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MaterialService {

    Page<MaterialDTO> listar(String search, Pageable pageable);


    MaterialDTO obtener(Integer id);

    MaterialDTO crear(MaterialCreateDTO dto);

    MaterialDTO actualizar(Integer id, MaterialUpdateDTO dto);

    MaterialDTO patch(Integer id, MaterialPatchDTO patch);

    void eliminar(Integer id);

    List<ProductoResumenDTO> listarProductos(Integer materialId);
}


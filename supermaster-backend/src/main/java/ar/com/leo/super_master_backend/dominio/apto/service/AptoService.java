package ar.com.leo.super_master_backend.dominio.apto.service;

import ar.com.leo.super_master_backend.dominio.apto.dto.AptoCreateDTO;
import ar.com.leo.super_master_backend.dominio.apto.dto.AptoDTO;
import ar.com.leo.super_master_backend.dominio.apto.dto.AptoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.apto.dto.AptoPatchDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AptoService {
    Page<AptoDTO> listar(String search, Pageable pageable);


    AptoDTO obtener(Integer id);

    AptoDTO crear(AptoCreateDTO dto);

    AptoDTO actualizar(Integer id, AptoUpdateDTO dto);

    AptoDTO patch(Integer id, AptoPatchDTO patch);

    void eliminar(Integer id);

    List<ProductoResumenDTO> listarProductos(Integer aptoId);
}


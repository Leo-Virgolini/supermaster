package ar.com.leo.super_master_backend.dominio.tipo.service;

import ar.com.leo.super_master_backend.dominio.tipo.dto.TipoCreateDTO;
import ar.com.leo.super_master_backend.dominio.tipo.dto.TipoDTO;
import ar.com.leo.super_master_backend.dominio.tipo.dto.TipoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.tipo.dto.TipoPatchDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TipoService {

    Page<TipoDTO> listar(String search, Pageable pageable);


    TipoDTO obtener(Integer id);

    TipoDTO crear(TipoCreateDTO dto);

    TipoDTO actualizar(Integer id, TipoUpdateDTO dto);

    TipoDTO patch(Integer id, TipoPatchDTO patch);

    void eliminar(Integer id);

    List<ProductoResumenDTO> listarProductos(Integer tipoId);
}


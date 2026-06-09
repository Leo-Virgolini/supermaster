package ar.com.leo.super_master_backend.dominio.clasif_gastro.service;

import ar.com.leo.super_master_backend.dominio.clasif_gastro.dto.ClasifGastroCreateDTO;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.dto.ClasifGastroDTO;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.dto.ClasifGastroPatchDTO;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.dto.ClasifGastroUpdateDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ClasifGastroService {

    Page<ClasifGastroDTO> listar(String search, Pageable pageable);

    ClasifGastroDTO obtener(Integer id);

    ClasifGastroDTO crear(ClasifGastroCreateDTO dto);

    ClasifGastroDTO actualizar(Integer id, ClasifGastroUpdateDTO dto);

    ClasifGastroDTO patch(Integer id, ClasifGastroPatchDTO patch);

    void eliminar(Integer id);

    List<ProductoResumenDTO> listarProductos(Integer clasifGastroId);
}


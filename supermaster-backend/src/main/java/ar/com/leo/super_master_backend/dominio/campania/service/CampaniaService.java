package ar.com.leo.super_master_backend.dominio.campania.service;

import ar.com.leo.super_master_backend.dominio.campania.dto.CampaniaDTO;
import ar.com.leo.super_master_backend.dominio.campania.dto.CampaniaProductoDTO;
import ar.com.leo.super_master_backend.dominio.campania.dto.CampaniaUpdateDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface CampaniaService {

    Page<CampaniaDTO> listar(String search, Pageable pageable);

    CampaniaDTO obtenerPorId(Integer id);

    CampaniaDTO actualizar(Integer id, CampaniaUpdateDTO dto);

    Page<CampaniaProductoDTO> listarProductos(Integer campaniaId, Pageable pageable);

    CampaniaProductoDTO actualizarPrecio(Integer campaniaProductoId, BigDecimal precioManual);
}

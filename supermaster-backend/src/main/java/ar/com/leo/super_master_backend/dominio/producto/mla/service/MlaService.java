package ar.com.leo.super_master_backend.dominio.producto.mla.service;

import ar.com.leo.super_master_backend.dominio.producto.mla.dto.MlaCreateDTO;
import ar.com.leo.super_master_backend.dominio.producto.mla.dto.MlaDTO;
import ar.com.leo.super_master_backend.dominio.producto.mla.dto.MlaTopePromocionDTO;
import ar.com.leo.super_master_backend.dominio.producto.mla.dto.MlaUpdateDTO;
import ar.com.leo.super_master_backend.dominio.producto.mla.dto.MlaPatchDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MlaService {

    Page<MlaDTO> listar(String search, Pageable pageable);

    MlaDTO obtener(Integer id);

    MlaDTO crear(MlaCreateDTO dto);

    MlaDTO actualizar(Integer id, MlaUpdateDTO dto);

    MlaDTO patch(Integer id, MlaPatchDTO patch);

    void eliminar(Integer id);

    List<ProductoResumenDTO> listarProductos(Integer mlaId);

    List<MlaTopePromocionDTO> listarTopesPromocion();

    List<MlaTopePromocionDTO> actualizarTopesPromocion(List<MlaTopePromocionDTO> topes);
}


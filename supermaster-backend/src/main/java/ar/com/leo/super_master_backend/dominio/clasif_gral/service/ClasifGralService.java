package ar.com.leo.super_master_backend.dominio.clasif_gral.service;

import ar.com.leo.super_master_backend.dominio.clasif_gral.dto.ClasifGralCreateDTO;
import ar.com.leo.super_master_backend.dominio.clasif_gral.dto.ClasifGralDTO;
import ar.com.leo.super_master_backend.dominio.clasif_gral.dto.ClasifGralPatchDTO;
import ar.com.leo.super_master_backend.dominio.clasif_gral.dto.ClasifGralUpdateDTO;
import ar.com.leo.super_master_backend.dominio.common.dto.SincronizacionDuxResultDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ClasifGralService {

    Page<ClasifGralDTO> listar(String search, Pageable pageable);

    ClasifGralDTO obtener(Integer id);

    ClasifGralDTO crear(ClasifGralCreateDTO dto);

    ClasifGralDTO actualizar(Integer id, ClasifGralUpdateDTO dto);

    ClasifGralDTO patch(Integer id, ClasifGralPatchDTO patch);

    void eliminar(Integer id);

    List<ProductoResumenDTO> listarProductos(Integer clasifGralId);

    SincronizacionDuxResultDTO sincronizarDuxIds();
}


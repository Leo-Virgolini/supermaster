package ar.com.leo.super_master_backend.dominio.proveedor.service;

import ar.com.leo.super_master_backend.dominio.auditoria.dto.AuditoriaCambioDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import ar.com.leo.super_master_backend.dominio.proveedor.dto.ProveedorCreateDTO;
import ar.com.leo.super_master_backend.dominio.proveedor.dto.ProveedorDTO;
import ar.com.leo.super_master_backend.dominio.proveedor.dto.ProveedorPatchDTO;
import ar.com.leo.super_master_backend.dominio.proveedor.dto.ProveedorUpdateDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProveedorService {

    Page<ProveedorDTO> listar(String search, Pageable pageable);


    ProveedorDTO obtener(Integer id);

    ProveedorDTO crear(ProveedorCreateDTO dto);

    ProveedorDTO actualizar(Integer id, ProveedorUpdateDTO dto);

    ProveedorDTO patch(Integer id, ProveedorPatchDTO patch);

    void eliminar(Integer id);

    List<ProductoResumenDTO> listarProductos(Integer proveedorId);

    Page<AuditoriaCambioDTO> listarAuditoria(Integer proveedorId, Pageable pageable);
}


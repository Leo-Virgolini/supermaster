package ar.com.leo.super_master_backend.dominio.proveedor.service;

import ar.com.leo.super_master_backend.dominio.auditoria.dto.AuditoriaCambioDTO;
import ar.com.leo.super_master_backend.dominio.proveedor.entity.Proveedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface ProveedorAuditoriaService {

    Map<String, String> capturarSnapshot(Proveedor proveedor);

    void registrarCreacion(Proveedor proveedor);

    void registrarActualizacion(Integer proveedorId, Map<String, String> estadoAnterior, Proveedor proveedorActual);

    void registrarEliminacion(Integer proveedorId, Map<String, String> estadoAnterior);

    Page<AuditoriaCambioDTO> listarPorProveedor(Integer proveedorId, Pageable pageable);
}

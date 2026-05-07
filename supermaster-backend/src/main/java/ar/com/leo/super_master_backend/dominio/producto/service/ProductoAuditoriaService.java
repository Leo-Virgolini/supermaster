package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.auditoria.dto.AuditoriaCambioDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface ProductoAuditoriaService {

    Map<String, String> capturarSnapshot(Producto producto);

    void registrarCreacion(Producto producto);

    void registrarActualizacion(Integer productoId, Map<String, String> estadoAnterior, Producto productoActual);

    void registrarEliminacion(Integer productoId, Map<String, String> estadoAnterior);

    Page<AuditoriaCambioDTO> listarPorProducto(Integer productoId, Pageable pageable);

    Page<AuditoriaCambioDTO> listarGlobal(
            String search,
            String accion,
            String campo,
            String origen,
            String usuario,
            Integer productoId,
            Pageable pageable
    );
}

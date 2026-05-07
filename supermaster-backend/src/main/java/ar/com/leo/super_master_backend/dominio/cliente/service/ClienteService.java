package ar.com.leo.super_master_backend.dominio.cliente.service;

import ar.com.leo.super_master_backend.dominio.auditoria.dto.AuditoriaCambioDTO;
import ar.com.leo.super_master_backend.dominio.cliente.dto.ClienteCreateDTO;
import ar.com.leo.super_master_backend.dominio.cliente.dto.ClienteDTO;
import ar.com.leo.super_master_backend.dominio.cliente.dto.ClienteUpdateDTO;
import ar.com.leo.super_master_backend.dominio.cliente.dto.ClientePatchDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ClienteService {

    Page<ClienteDTO> listar(String search, Pageable pageable);

    ClienteDTO obtener(Integer id);

    ClienteDTO crear(ClienteCreateDTO dto);

    ClienteDTO actualizar(Integer id, ClienteUpdateDTO dto);

    ClienteDTO patch(Integer id, ClientePatchDTO patch);

    void eliminar(Integer id);

    List<ProductoResumenDTO> listarProductos(Integer clienteId);

    Page<AuditoriaCambioDTO> listarAuditoria(Integer clienteId, Pageable pageable);
}


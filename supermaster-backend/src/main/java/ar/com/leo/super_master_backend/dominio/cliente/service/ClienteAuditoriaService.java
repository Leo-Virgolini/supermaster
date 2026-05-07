package ar.com.leo.super_master_backend.dominio.cliente.service;

import ar.com.leo.super_master_backend.dominio.auditoria.dto.AuditoriaCambioDTO;
import ar.com.leo.super_master_backend.dominio.cliente.entity.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface ClienteAuditoriaService {

    Map<String, String> capturarSnapshot(Cliente cliente);

    void registrarCreacion(Cliente cliente);

    void registrarActualizacion(Integer clienteId, Map<String, String> estadoAnterior, Cliente clienteActual);

    void registrarEliminacion(Integer clienteId, Map<String, String> estadoAnterior);

    Page<AuditoriaCambioDTO> listarPorCliente(Integer clienteId, Pageable pageable);
}

package ar.com.leo.super_master_backend.dominio.cliente.service;

import ar.com.leo.super_master_backend.dominio.auditoria.dto.AuditoriaCambioDTO;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaEntidadBridgeService;
import ar.com.leo.super_master_backend.dominio.cliente.entity.Cliente;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClienteAuditoriaServiceImpl implements ClienteAuditoriaService {

    private final AuditoriaEntidadBridgeService auditoriaEntidadBridgeService;

    @Override
    public Map<String, String> capturarSnapshot(Cliente cliente) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("nombre", normalizar(cliente.getNombre()));
        return snapshot;
    }

    @Override
    @Transactional
    public void registrarCreacion(Cliente cliente) {
        auditoriaEntidadBridgeService.registrarCreacion(
                AuditoriaEntidad.CLIENTE,
                cliente.getId(),
                cliente.getNombre(),
                capturarSnapshot(cliente)
        );
    }

    @Override
    @Transactional
    public void registrarActualizacion(Integer clienteId, Map<String, String> estadoAnterior, Cliente clienteActual) {
        auditoriaEntidadBridgeService.registrarActualizacion(
                AuditoriaEntidad.CLIENTE,
                clienteId,
                clienteActual.getNombre(),
                estadoAnterior,
                capturarSnapshot(clienteActual)
        );
    }

    @Override
    @Transactional
    public void registrarEliminacion(Integer clienteId, Map<String, String> estadoAnterior) {
        auditoriaEntidadBridgeService.registrarEliminacion(
                AuditoriaEntidad.CLIENTE,
                clienteId,
                estadoAnterior.get("nombre"),
                estadoAnterior
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditoriaCambioDTO> listarPorCliente(Integer clienteId, Pageable pageable) {
        return auditoriaEntidadBridgeService.listarPorEntidad(AuditoriaEntidad.CLIENTE, clienteId, pageable, audit -> audit);
    }

    private String normalizar(Object value) {
        return value == null ? null : String.valueOf(value);
    }

}

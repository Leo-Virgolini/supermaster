package ar.com.leo.super_master_backend.dominio.proveedor.service;

import ar.com.leo.super_master_backend.dominio.auditoria.dto.AuditoriaCambioDTO;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaEntidadBridgeService;
import ar.com.leo.super_master_backend.dominio.proveedor.entity.Proveedor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProveedorAuditoriaServiceImpl implements ProveedorAuditoriaService {

    private final AuditoriaEntidadBridgeService auditoriaEntidadBridgeService;

    @Override
    public Map<String, String> capturarSnapshot(Proveedor proveedor) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("nombre", normalizar(proveedor.getNombre()));
        snapshot.put("apodo", normalizar(proveedor.getApodo()));
        snapshot.put("plazoPago", normalizar(proveedor.getPlazoPago()));
        snapshot.put("entrega", normalizar(proveedor.getEntrega()));
        snapshot.put("financiacionPorcentaje", decimal(proveedor.getFinanciacionPorcentaje()));
        snapshot.put("leadTimeDias", normalizar(proveedor.getLeadTimeDias()));
        snapshot.put("idDux", normalizar(proveedor.getIdDux()));
        return snapshot;
    }

    @Override
    @Transactional
    public void registrarCreacion(Proveedor proveedor) {
        auditoriaEntidadBridgeService.registrarCreacion(
                AuditoriaEntidad.PROVEEDOR,
                proveedor.getId(),
                proveedor.getNombre(),
                capturarSnapshot(proveedor)
        );
    }

    @Override
    @Transactional
    public void registrarActualizacion(Integer proveedorId, Map<String, String> estadoAnterior, Proveedor proveedorActual) {
        auditoriaEntidadBridgeService.registrarActualizacion(
                AuditoriaEntidad.PROVEEDOR,
                proveedorId,
                proveedorActual.getNombre(),
                estadoAnterior,
                capturarSnapshot(proveedorActual)
        );
    }

    @Override
    @Transactional
    public void registrarEliminacion(Integer proveedorId, Map<String, String> estadoAnterior) {
        auditoriaEntidadBridgeService.registrarEliminacion(
                AuditoriaEntidad.PROVEEDOR,
                proveedorId,
                estadoAnterior.get("nombre"),
                estadoAnterior
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditoriaCambioDTO> listarPorProveedor(Integer proveedorId, Pageable pageable) {
        return auditoriaEntidadBridgeService.listarPorEntidad(AuditoriaEntidad.PROVEEDOR, proveedorId, pageable, audit -> audit);
    }

    private String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String normalizar(Object value) {
        return value == null ? null : String.valueOf(value);
    }

}

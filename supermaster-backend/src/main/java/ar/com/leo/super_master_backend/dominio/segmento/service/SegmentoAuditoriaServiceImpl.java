package ar.com.leo.super_master_backend.dominio.segmento.service;

import ar.com.leo.super_master_backend.dominio.auditoria.dto.AuditoriaCambioDTO;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaEntidadBridgeService;
import ar.com.leo.super_master_backend.dominio.segmento.entity.Segmento;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SegmentoAuditoriaServiceImpl implements SegmentoAuditoriaService {

    private final AuditoriaEntidadBridgeService auditoriaEntidadBridgeService;

    @Override
    public Map<String, String> capturarSnapshot(Segmento segmento) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("nombre", normalizar(segmento.getNombre()));
        return snapshot;
    }

    @Override
    @Transactional
    public void registrarCreacion(Segmento segmento) {
        auditoriaEntidadBridgeService.registrarCreacion(
                AuditoriaEntidad.SEGMENTO,
                segmento.getId(),
                segmento.getNombre(),
                capturarSnapshot(segmento)
        );
    }

    @Override
    @Transactional
    public void registrarActualizacion(Integer segmentoId, Map<String, String> estadoAnterior, Segmento segmentoActual) {
        auditoriaEntidadBridgeService.registrarActualizacion(
                AuditoriaEntidad.SEGMENTO,
                segmentoId,
                segmentoActual.getNombre(),
                estadoAnterior,
                capturarSnapshot(segmentoActual)
        );
    }

    @Override
    @Transactional
    public void registrarEliminacion(Integer segmentoId, Map<String, String> estadoAnterior) {
        auditoriaEntidadBridgeService.registrarEliminacion(
                AuditoriaEntidad.SEGMENTO,
                segmentoId,
                estadoAnterior.get("nombre"),
                estadoAnterior
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditoriaCambioDTO> listarPorSegmento(Integer segmentoId, Pageable pageable) {
        return auditoriaEntidadBridgeService.listarPorEntidad(AuditoriaEntidad.SEGMENTO, segmentoId, pageable, audit -> audit);
    }

    private String normalizar(Object value) {
        return value == null ? null : String.valueOf(value);
    }

}

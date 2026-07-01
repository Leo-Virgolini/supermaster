package ar.com.leo.super_master_backend.dominio.segmento.service;

import ar.com.leo.super_master_backend.dominio.auditoria.dto.AuditoriaCambioDTO;
import ar.com.leo.super_master_backend.dominio.segmento.entity.Segmento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public interface SegmentoAuditoriaService {

    Map<String, String> capturarSnapshot(Segmento segmento);

    void registrarCreacion(Segmento segmento);

    void registrarActualizacion(Integer segmentoId, Map<String, String> estadoAnterior, Segmento segmentoActual);

    void registrarEliminacion(Integer segmentoId, Map<String, String> estadoAnterior);

    Page<AuditoriaCambioDTO> listarPorSegmento(Integer segmentoId, Pageable pageable);
}

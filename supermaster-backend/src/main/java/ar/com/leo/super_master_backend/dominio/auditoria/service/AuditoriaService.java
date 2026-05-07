package ar.com.leo.super_master_backend.dominio.auditoria.service;

import ar.com.leo.super_master_backend.dominio.auditoria.dto.AuditoriaCambioDTO;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Map;

public interface AuditoriaService {

    void registrarCambios(
            AuditoriaEntidad entidad,
            Integer entidadId,
            String entidadCodigo,
            AuditoriaAccion accion,
            Map<String, String> estadoAnterior,
            Map<String, String> estadoNuevo
    );

    Page<AuditoriaCambioDTO> listarPorEntidad(AuditoriaEntidad entidad, Integer entidadId, Pageable pageable);

    Page<AuditoriaCambioDTO> listarGlobal(
            String search,
            String entidad,
            String accion,
            String campo,
            String origen,
            String usuario,
            Integer entidadId,
            LocalDateTime fechaDesde,
            LocalDateTime fechaHasta,
            Pageable pageable
    );
}

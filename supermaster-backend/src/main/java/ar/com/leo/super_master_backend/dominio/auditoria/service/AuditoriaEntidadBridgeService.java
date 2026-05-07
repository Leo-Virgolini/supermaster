package ar.com.leo.super_master_backend.dominio.auditoria.service;

import ar.com.leo.super_master_backend.dominio.auditoria.dto.AuditoriaCambioDTO;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class AuditoriaEntidadBridgeService {

    private final AuditoriaService auditoriaService;

    public void registrarCreacion(
            AuditoriaEntidad entidad,
            Integer entidadId,
            String entidadCodigo,
            Map<String, String> estadoNuevo
    ) {
        auditoriaService.registrarCambios(
                entidad,
                entidadId,
                entidadCodigo,
                AuditoriaAccion.CREATE,
                Map.of(),
                estadoNuevo
        );
    }

    public void registrarActualizacion(
            AuditoriaEntidad entidad,
            Integer entidadId,
            String entidadCodigo,
            Map<String, String> estadoAnterior,
            Map<String, String> estadoNuevo
    ) {
        auditoriaService.registrarCambios(
                entidad,
                entidadId,
                entidadCodigo,
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                estadoNuevo
        );
    }

    public void registrarEliminacion(
            AuditoriaEntidad entidad,
            Integer entidadId,
            String entidadCodigo,
            Map<String, String> estadoAnterior
    ) {
        auditoriaService.registrarCambios(
                entidad,
                entidadId,
                entidadCodigo,
                AuditoriaAccion.DELETE,
                estadoAnterior,
                Map.of()
        );
    }

    public <T> Page<T> listarPorEntidad(
            AuditoriaEntidad entidad,
            Integer entidadId,
            Pageable pageable,
            Function<AuditoriaCambioDTO, T> mapper
    ) {
        return auditoriaService.listarPorEntidad(entidad, entidadId, pageable).map(mapper);
    }
}

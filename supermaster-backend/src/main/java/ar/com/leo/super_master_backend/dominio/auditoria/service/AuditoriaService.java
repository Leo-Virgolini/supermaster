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

    /**
     * Igual que {@link #registrarCambios}, pero con el origen indicado EXPLÍCITAMENTE en
     * lugar de resolverlo del header/request. Útil para cambios hechos por un proceso de
     * cálculo (ej. costo de envío/comisión de MLA) que corre en un hilo @Async sin request:
     * sin esto saldrían como "SYSTEM". Si {@code origen} es null/blank, se resuelve del request.
     */
    void registrarCambios(
            AuditoriaEntidad entidad,
            Integer entidadId,
            String entidadCodigo,
            AuditoriaAccion accion,
            Map<String, String> estadoAnterior,
            Map<String, String> estadoNuevo,
            String origen
    );

    /**
     * Registra UN solo evento de auditoría (una fila) con el usuario y origen indicados
     * explícitamente. Pensado para procesos asíncronos donde el {@code SecurityContext} del
     * hilo HTTP original ya no está disponible: el caller captura el {@code username} en el
     * hilo de la request y lo pasa; el usuario se resuelve por query (no por contexto).
     */
    void registrarEvento(
            AuditoriaEntidad entidad,
            String entidadCodigo,
            AuditoriaAccion accion,
            String campo,
            String valorNuevo,
            String username,
            String origen
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

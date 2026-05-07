package ar.com.leo.super_master_backend.dominio.auditoria.dto;

import java.time.LocalDateTime;

public record AuditoriaCambioDTO(
        Integer id,
        String entidad,
        Integer entidadId,
        String entidadCodigo,
        String accion,
        String campo,
        String valorAnterior,
        String valorNuevo,
        Integer usuarioId,
        String usuarioUsername,
        String usuarioNombreCompleto,
        String origen,
        LocalDateTime fechaHora
) {
}

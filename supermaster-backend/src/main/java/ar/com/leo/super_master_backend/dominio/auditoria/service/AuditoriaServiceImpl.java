package ar.com.leo.super_master_backend.dominio.auditoria.service;

import ar.com.leo.super_master_backend.dominio.auditoria.dto.AuditoriaCambioDTO;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaCambio;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.repository.AuditoriaCambioRepository;
import ar.com.leo.super_master_backend.dominio.usuario.entity.Usuario;
import ar.com.leo.super_master_backend.dominio.usuario.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuditoriaServiceImpl implements AuditoriaService {

    private final AuditoriaCambioRepository auditoriaCambioRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional
    public void registrarCambios(
            AuditoriaEntidad entidad,
            Integer entidadId,
            String entidadCodigo,
            AuditoriaAccion accion,
            Map<String, String> estadoAnterior,
            Map<String, String> estadoNuevo
    ) {
        registrarCambios(entidad, entidadId, entidadCodigo, accion, estadoAnterior, estadoNuevo, null);
    }

    @Override
    @Transactional
    public void registrarCambios(
            AuditoriaEntidad entidad,
            Integer entidadId,
            String entidadCodigo,
            AuditoriaAccion accion,
            Map<String, String> estadoAnterior,
            Map<String, String> estadoNuevo,
            String origenExplicito
    ) {
        LinkedHashSet<String> campos = new LinkedHashSet<>();
        campos.addAll(estadoAnterior.keySet());
        campos.addAll(estadoNuevo.keySet());

        AuditoriaUsuario usuarioActual = resolverUsuarioActual();
        String origen = (origenExplicito != null && !origenExplicito.isBlank())
                ? origenExplicito.trim().toUpperCase()
                : resolverOrigen();
        List<AuditoriaCambio> registros = new ArrayList<>();

        for (String campo : campos) {
            String valorAnterior = estadoAnterior.get(campo);
            String valorNuevo = estadoNuevo.get(campo);
            if (Objects.equals(valorAnterior, valorNuevo)) {
                continue;
            }

            AuditoriaCambio auditoria = new AuditoriaCambio();
            auditoria.setEntidad(entidad);
            auditoria.setEntidadId(entidadId);
            auditoria.setEntidadCodigo(entidadCodigo);
            auditoria.setAccion(accion);
            auditoria.setCampo(campo);
            auditoria.setValorAnterior(valorAnterior);
            auditoria.setValorNuevo(valorNuevo);
            auditoria.setUsuarioId(usuarioActual.usuarioId());
            auditoria.setUsuarioUsername(usuarioActual.username());
            auditoria.setUsuarioNombreCompleto(usuarioActual.nombreCompleto());
            auditoria.setOrigen(origen);
            auditoria.setFechaHora(LocalDateTime.now());
            registros.add(auditoria);
        }

        if (!registros.isEmpty()) {
            // Persistir en chunks para evitar OOM y latencias largas cuando una entidad
            // dispara muchos cambios (ej: import masivo o reconciliaciones).
            for (int i = 0; i < registros.size(); i += AUDIT_BATCH_SIZE) {
                int end = Math.min(i + AUDIT_BATCH_SIZE, registros.size());
                auditoriaCambioRepository.saveAll(registros.subList(i, end));
            }
        }
    }

    private static final int AUDIT_BATCH_SIZE = 500;

    @Override
    @Transactional(readOnly = true)
    public Page<AuditoriaCambioDTO> listarPorEntidad(AuditoriaEntidad entidad, Integer entidadId, Pageable pageable) {
        return auditoriaCambioRepository.findByEntidadAndEntidadId(entidad, entidadId, pageable).map(this::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditoriaCambioDTO> listarGlobal(
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
    ) {
        AuditoriaEntidad entidadEnum = null;
        if (entidad != null && !entidad.isBlank()) {
            entidadEnum = AuditoriaEntidad.valueOf(entidad.trim().toUpperCase());
        }

        AuditoriaAccion accionEnum = null;
        if (accion != null && !accion.isBlank()) {
            accionEnum = AuditoriaAccion.valueOf(accion.trim().toUpperCase());
        }

        return auditoriaCambioRepository.buscar(
                normalizarTextoFiltro(search),
                entidadEnum,
                accionEnum,
                normalizarTextoFiltro(campo),
                normalizarTextoFiltro(origen),
                normalizarTextoFiltro(usuario),
                entidadId,
                fechaDesde,
                fechaHasta,
                pageable
        ).map(this::toDTO);
    }

    private AuditoriaCambioDTO toDTO(AuditoriaCambio audit) {
        return new AuditoriaCambioDTO(
                audit.getId(),
                audit.getEntidad().name(),
                audit.getEntidadId(),
                audit.getEntidadCodigo(),
                audit.getAccion().name(),
                audit.getCampo(),
                audit.getValorAnterior(),
                audit.getValorNuevo(),
                audit.getUsuarioId(),
                audit.getUsuarioUsername(),
                audit.getUsuarioNombreCompleto(),
                audit.getOrigen(),
                audit.getFechaHora()
        );
    }

    @Override
    @Transactional
    public void registrarEvento(
            AuditoriaEntidad entidad,
            String entidadCodigo,
            AuditoriaAccion accion,
            String campo,
            String valorNuevo,
            String username,
            String origen
    ) {
        AuditoriaUsuario usuario = resolverUsuarioPorUsername(username);
        AuditoriaCambio auditoria = new AuditoriaCambio();
        auditoria.setEntidad(entidad);
        auditoria.setEntidadCodigo(entidadCodigo);
        auditoria.setAccion(accion);
        auditoria.setCampo(campo);
        auditoria.setValorAnterior(null);
        auditoria.setValorNuevo(valorNuevo);
        auditoria.setUsuarioId(usuario.usuarioId());
        auditoria.setUsuarioUsername(usuario.username());
        auditoria.setUsuarioNombreCompleto(usuario.nombreCompleto());
        auditoria.setOrigen(origen != null && !origen.isBlank() ? origen : "SYSTEM");
        auditoria.setFechaHora(LocalDateTime.now());
        auditoriaCambioRepository.save(auditoria);
    }

    private AuditoriaUsuario resolverUsuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return new AuditoriaUsuario(null, null, null);
        }
        return resolverUsuarioPorUsername(String.valueOf(authentication.getPrincipal()).trim());
    }

    /** Resuelve el usuario por username (query). Devuelve usuario "vacío" si es anónimo o no existe. */
    private AuditoriaUsuario resolverUsuarioPorUsername(String username) {
        if (username == null || username.isBlank() || "anonymousUser".equalsIgnoreCase(username)) {
            return new AuditoriaUsuario(null, null, null);
        }
        Optional<Usuario> usuario = usuarioRepository.findByUsername(username);
        return usuario
                .map(value -> new AuditoriaUsuario(value.getId(), value.getUsername(), value.getNombreCompleto()))
                .orElseGet(() -> new AuditoriaUsuario(null, username, null));
    }

    private String resolverOrigen() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "SYSTEM";
        }

        HttpServletRequest request = attributes.getRequest();
        String origen = request.getHeader("X-Audit-Origin");
        if (origen == null || origen.isBlank()) {
            return "API";
        }
        return origen.trim().toUpperCase();
    }

    private String normalizarTextoFiltro(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record AuditoriaUsuario(Integer usuarioId, String username, String nombreCompleto) {
    }
}

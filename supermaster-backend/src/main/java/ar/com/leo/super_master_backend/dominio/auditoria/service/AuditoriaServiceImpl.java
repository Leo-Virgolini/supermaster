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
        LinkedHashSet<String> campos = new LinkedHashSet<>();
        campos.addAll(estadoAnterior.keySet());
        campos.addAll(estadoNuevo.keySet());

        AuditoriaUsuario usuarioActual = resolverUsuarioActual();
        String origen = resolverOrigen();
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
            auditoriaCambioRepository.saveAll(registros);
        }
    }

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

    private AuditoriaUsuario resolverUsuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return new AuditoriaUsuario(null, null, null);
        }

        String username = String.valueOf(authentication.getPrincipal()).trim();
        if (username.isBlank() || "anonymousUser".equalsIgnoreCase(username)) {
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

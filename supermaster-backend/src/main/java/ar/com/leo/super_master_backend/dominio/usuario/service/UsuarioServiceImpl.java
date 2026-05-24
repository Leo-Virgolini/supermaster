package ar.com.leo.super_master_backend.dominio.usuario.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.common.exception.ConflictException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.usuario.dto.*;
import ar.com.leo.super_master_backend.dominio.usuario.entity.Rol;
import ar.com.leo.super_master_backend.dominio.usuario.entity.Usuario;
import ar.com.leo.super_master_backend.dominio.usuario.mapper.UsuarioMapper;
import ar.com.leo.super_master_backend.dominio.usuario.repository.RolRepository;
import ar.com.leo.super_master_backend.dominio.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final UsuarioMapper usuarioMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional(readOnly = true)
    public Page<UsuarioDTO> listar(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return usuarioRepository
                    .findByUsernameContainingIgnoreCaseOrNombreCompletoContainingIgnoreCase(search, search, pageable)
                    .map(usuarioMapper::toDTO);
        }
        return usuarioRepository.findAll(pageable).map(usuarioMapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioDTO obtener(Integer id) {
        return usuarioMapper.toDTO(findUsuarioById(id));
    }

    @Override
    @Transactional
    public UsuarioDTO crear(UsuarioCreateDTO dto) {
        if (usuarioRepository.existsByUsername(dto.username())) {
            throw new ConflictException("Ya existe un usuario con username: " + dto.username());
        }

        Rol rol = rolRepository.findById(dto.rolId())
                .orElseThrow(() -> new NotFoundException("Rol no encontrado con id: " + dto.rolId()));

        Usuario usuario = new Usuario();
        usuario.setUsername(dto.username());
        usuario.setPasswordHash(passwordEncoder.encode(dto.password()));
        usuario.setNombreCompleto(dto.nombreCompleto());
        usuario.setActivo(true);
        usuario.setRol(rol);

        Usuario saved = usuarioRepository.save(usuario);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.USUARIO,
                saved.getId(),
                saved.getUsername(),
                AuditoriaAccion.CREATE,
                Map.of(),
                capturarSnapshot(saved)
        );
        return usuarioMapper.toDTO(saved);
    }

    @Override
    @Transactional
    public UsuarioDTO actualizar(Integer id, UsuarioUpdateDTO dto) {
        Usuario usuario = findUsuarioById(id);
        Map<String, String> estadoAnterior = capturarSnapshot(usuario);

        if (dto.nombreCompleto() != null) {
            usuario.setNombreCompleto(dto.nombreCompleto());
        }
        if (dto.activo() != null) {
            usuario.setActivo(dto.activo());
        }
        if (dto.rolId() != null) {
            Rol rol = rolRepository.findById(dto.rolId())
                    .orElseThrow(() -> new NotFoundException("Rol no encontrado con id: " + dto.rolId()));
            usuario.setRol(rol);
        }

        Usuario saved = usuarioRepository.save(usuario);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.USUARIO,
                id,
                saved.getUsername(),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(saved)
        );
        return usuarioMapper.toDTO(saved);
    }

    @Override
    @Transactional
    public void cambiarPassword(Integer id, CambioPasswordDTO dto) {
        Usuario usuario = findUsuarioById(id);
        Map<String, String> estadoAnterior = Map.of("password", "Configurada");
        usuario.setPasswordHash(passwordEncoder.encode(dto.nuevaPassword()));
        usuarioRepository.save(usuario);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.USUARIO,
                id,
                usuario.getUsername(),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                Map.of("password", "Actualizada")
        );
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        Usuario usuario = findUsuarioById(id);
        Map<String, String> estadoAnterior = capturarSnapshot(usuario);
        String codigo = usuario.getUsername();
        usuarioRepository.delete(usuario);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.USUARIO,
                id,
                codigo,
                AuditoriaAccion.DELETE,
                estadoAnterior,
                Map.of()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<RolDTO> listarRoles() {
        return rolRepository.findAllConPermisos().stream()
                .map(usuarioMapper::toRolDTO)
                .toList();
    }

    private Usuario findUsuarioById(Integer id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado con id: " + id));
    }

    private Map<String, String> capturarSnapshot(Usuario usuario) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("username", normalizar(usuario.getUsername()));
        snapshot.put("nombreCompleto", normalizar(usuario.getNombreCompleto()));
        snapshot.put("activo", normalizar(usuario.getActivo()));
        snapshot.put("rol", describirRol(usuario.getRol()));
        return snapshot;
    }

    private String describirRol(Rol rol) {
        return rol == null ? null : rol.getId() + " - " + rol.getNombre();
    }

    private String normalizar(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}


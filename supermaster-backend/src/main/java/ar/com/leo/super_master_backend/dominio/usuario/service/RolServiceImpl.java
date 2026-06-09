package ar.com.leo.super_master_backend.dominio.usuario.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.usuario.dto.PermisoDTO;
import ar.com.leo.super_master_backend.dominio.usuario.dto.RolDTO;
import ar.com.leo.super_master_backend.dominio.usuario.entity.Permiso;
import ar.com.leo.super_master_backend.dominio.usuario.entity.Rol;
import ar.com.leo.super_master_backend.dominio.usuario.repository.PermisoRepository;
import ar.com.leo.super_master_backend.dominio.usuario.repository.RolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RolServiceImpl implements RolService {

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional(readOnly = true)
    public List<RolDTO> listarRoles() {
        return rolRepository.findAllConPermisos().stream()
                .map(this::toRolDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermisoDTO> listarPermisos() {
        return permisoRepository.findAllByOrderByNombreAsc().stream()
                .map(p -> new PermisoDTO(p.getId(), p.getNombre(), p.getDescripcion()))
                .toList();
    }

    @Override
    @Transactional
    public RolDTO actualizarPermisos(Integer rolId, List<Integer> permisoIds) {
        Rol rol = rolRepository.findById(rolId)
                .orElseThrow(() -> new NotFoundException("Rol no encontrado: " + rolId));

        Map<String, String> estadoAnterior = capturarSnapshot(rol);

        Set<Permiso> nuevosPermisos = new LinkedHashSet<>();
        for (Integer permisoId : permisoIds) {
            Permiso permiso = permisoRepository.findById(permisoId)
                    .orElseThrow(() -> new NotFoundException("Permiso no encontrado: " + permisoId));
            nuevosPermisos.add(permiso);
        }

        rol.getPermisos().clear();
        rol.getPermisos().addAll(nuevosPermisos);
        rolRepository.save(rol);

        auditoriaService.registrarCambios(
                AuditoriaEntidad.USUARIO,
                rol.getId(),
                rol.getNombre(),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(rol)
        );

        return toRolDTO(rol);
    }

    private RolDTO toRolDTO(Rol rol) {
        List<String> permisos = rol.getPermisos().stream()
                .map(Permiso::getNombre)
                .sorted()
                .toList();
        return new RolDTO(rol.getId(), rol.getNombre(), rol.getDescripcion(), permisos);
    }

    private Map<String, String> capturarSnapshot(Rol rol) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("nombre", rol.getNombre());
        snapshot.put("permisos", rol.getPermisos().stream()
                .map(Permiso::getNombre)
                .sorted()
                .collect(Collectors.joining(", ")));
        return snapshot;
    }
}

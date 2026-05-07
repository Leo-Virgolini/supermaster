package ar.com.leo.super_master_backend.dominio.usuario.service;

import ar.com.leo.super_master_backend.dominio.usuario.dto.PermisoDTO;
import ar.com.leo.super_master_backend.dominio.usuario.dto.RolDTO;

import java.util.List;

public interface RolService {
    List<RolDTO> listarRoles();
    List<PermisoDTO> listarPermisos();
    RolDTO actualizarPermisos(Integer rolId, List<Integer> permisoIds);
}

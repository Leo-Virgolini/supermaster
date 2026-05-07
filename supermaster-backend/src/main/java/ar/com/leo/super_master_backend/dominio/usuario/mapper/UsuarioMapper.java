package ar.com.leo.super_master_backend.dominio.usuario.mapper;

import ar.com.leo.super_master_backend.dominio.usuario.dto.RolDTO;
import ar.com.leo.super_master_backend.dominio.usuario.dto.UsuarioDTO;
import ar.com.leo.super_master_backend.dominio.usuario.entity.Rol;
import ar.com.leo.super_master_backend.dominio.usuario.entity.Usuario;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    @Mapping(target = "rol", source = "rol", qualifiedByName = "toRolDTO")
    @Mapping(target = "permisos", source = "rol", qualifiedByName = "toPermisosList")
    UsuarioDTO toDTO(Usuario usuario);

    @Named("toRolDTO")
    default RolDTO toRolDTO(Rol rol) {
        if (rol == null) return null;
        List<String> permisos = rol.getPermisos().stream()
                .map(p -> p.getNombre())
                .sorted()
                .toList();
        return new RolDTO(rol.getId(), rol.getNombre(), rol.getDescripcion(), permisos);
    }

    @Named("toPermisosList")
    default List<String> toPermisosList(Rol rol) {
        if (rol == null) return List.of();
        return rol.getPermisos().stream()
                .map(p -> p.getNombre())
                .sorted()
                .toList();
    }
}

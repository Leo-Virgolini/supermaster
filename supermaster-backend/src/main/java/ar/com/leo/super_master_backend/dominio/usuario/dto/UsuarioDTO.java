package ar.com.leo.super_master_backend.dominio.usuario.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UsuarioDTO(
        Integer id,
        String username,
        String nombreCompleto,
        Boolean activo,
        RolDTO rol,
        List<String> permisos,
        LocalDateTime fechaModificacion,
        LocalDateTime ultimoLogin
) {}

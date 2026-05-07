package ar.com.leo.super_master_backend.dominio.usuario.dto;

import java.util.List;

public record RolDTO(
        Integer id,
        String nombre,
        String descripcion,
        List<String> permisos
) {}

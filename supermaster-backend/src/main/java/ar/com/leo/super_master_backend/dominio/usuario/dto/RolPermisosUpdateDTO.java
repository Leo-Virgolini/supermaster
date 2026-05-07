package ar.com.leo.super_master_backend.dominio.usuario.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RolPermisosUpdateDTO(
        @NotNull(message = "La lista de permisos es obligatoria")
        List<Integer> permisoIds
) {}

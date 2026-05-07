package ar.com.leo.super_master_backend.dominio.auth.dto;

import java.util.List;

public record LoginResponseDTO(
        String accessToken,
        long expiresIn,
        UsuarioInfoDTO usuario
) {
    public record UsuarioInfoDTO(
            Integer id,
            String username,
            String nombreCompleto,
            String rol,
            List<String> permisos
    ) {}
}

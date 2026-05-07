package ar.com.leo.super_master_backend.dominio.auth.service;

import ar.com.leo.super_master_backend.dominio.auth.dto.LoginRequestDTO;
import ar.com.leo.super_master_backend.dominio.auth.dto.LoginResponseDTO;

public interface AuthService {

    LoginResponseDTO login(LoginRequestDTO request);

    LoginResponseDTO.UsuarioInfoDTO me(String username);
}

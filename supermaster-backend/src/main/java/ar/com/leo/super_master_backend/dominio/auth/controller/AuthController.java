package ar.com.leo.super_master_backend.dominio.auth.controller;

import ar.com.leo.super_master_backend.dominio.auth.dto.LoginRequestDTO;
import ar.com.leo.super_master_backend.dominio.auth.dto.LoginResponseDTO;
import ar.com.leo.super_master_backend.dominio.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<LoginResponseDTO.UsuarioInfoDTO> me(Authentication authentication) {
        return ResponseEntity.ok(authService.me(authentication.getName()));
    }
}

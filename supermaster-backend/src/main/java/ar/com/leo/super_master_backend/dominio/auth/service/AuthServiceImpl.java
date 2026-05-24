package ar.com.leo.super_master_backend.dominio.auth.service;

import ar.com.leo.super_master_backend.config.JwtProperties;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.auth.dto.LoginRequestDTO;
import ar.com.leo.super_master_backend.dominio.auth.dto.LoginResponseDTO;
import ar.com.leo.super_master_backend.dominio.auth.jwt.JwtTokenProvider;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.usuario.entity.Usuario;
import ar.com.leo.super_master_backend.dominio.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional
    public LoginResponseDTO login(LoginRequestDTO request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (DisabledException e) {
            registrarLoginFallido(request.username(), "Cuenta desactivada");
            throw new BadRequestException("La cuenta está desactivada");
        } catch (BadCredentialsException e) {
            registrarLoginFallido(request.username(), "Credenciales inválidas");
            throw new BadRequestException("Credenciales inválidas");
        } catch (AuthenticationException e) {
            registrarLoginFallido(request.username(), "Error de autenticación");
            throw new BadRequestException("Error de autenticación");
        }

        // Fetch con rol+permisos: el JWT incluye los permisos como claim y buildUsuarioInfo
        // los itera. Sin esto se disparan queries adicionales en cada login.
        Usuario usuario = usuarioRepository.findByUsernameConRolYPermisos(request.username())
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        usuario.setUltimoLogin(java.time.LocalDateTime.now());
        usuarioRepository.save(usuario);

        auditoriaService.registrarCambios(
                AuditoriaEntidad.AUTH,
                usuario.getId(),
                usuario.getUsername(),
                AuditoriaAccion.CREATE,
                Map.of(),
                Map.of("evento", "login_exitoso")
        );

        String accessToken = jwtTokenProvider.generarAccessToken(usuario);

        return new LoginResponseDTO(
                accessToken,
                jwtProperties.accessTokenExpirationMs(),
                buildUsuarioInfo(usuario)
        );
    }

    private void registrarLoginFallido(String username, String motivo) {
        try {
            auditoriaService.registrarCambios(
                    AuditoriaEntidad.AUTH,
                    null,
                    username,
                    AuditoriaAccion.CREATE,
                    Map.of(),
                    Map.of("evento", "login_fallido", "motivo", motivo)
            );
        } catch (Exception ignored) {
            // No queremos que un fallo en la auditoría rompa el flujo de login.
        }
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponseDTO.UsuarioInfoDTO me(String username) {
        Usuario usuario = usuarioRepository.findByUsernameConRolYPermisos(username)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        return buildUsuarioInfo(usuario);
    }

    private LoginResponseDTO.UsuarioInfoDTO buildUsuarioInfo(Usuario usuario) {
        return new LoginResponseDTO.UsuarioInfoDTO(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getNombreCompleto(),
                usuario.getRol().getNombre(),
                usuario.getRol().getPermisos().stream()
                        .map(p -> p.getNombre())
                        .sorted()
                        .toList()
        );
    }
}


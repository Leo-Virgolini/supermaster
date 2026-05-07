package ar.com.leo.super_master_backend.dominio.auth.jwt;

import ar.com.leo.super_master_backend.dominio.usuario.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extraerToken(request);

        if (token != null && jwtTokenProvider.validarToken(token)) {
            String username = jwtTokenProvider.getUsername(token);

            // Verificar que el usuario siga activo en DB
            boolean activo = usuarioRepository.existsByUsernameAndActivoTrue(username);
            if (activo) {
                List<String> permisos = jwtTokenProvider.getPermisos(token);

                List<SimpleGrantedAuthority> authorities = permisos.stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Importante: los SseEmitter usan async request processing y Spring re-dispatcha
     * el request por la cadena de filtros. Por defecto OncePerRequestFilter SALTA el
     * dispatch async, lo que deja el SecurityContext vacío en ese leg → cualquier
     * @PreAuthorize evaluado en el dispatch async dispara AccessDenied y, como la
     * response ya está committed (SSE ya envió headers), aparece el error
     * "Unable to handle the Spring Security Exception because the response is already
     * committed". Forzando que el filter corra también en async dispatches, el JWT se
     * re-aplica y la auth queda disponible en todo el ciclo del request.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    private String extraerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}

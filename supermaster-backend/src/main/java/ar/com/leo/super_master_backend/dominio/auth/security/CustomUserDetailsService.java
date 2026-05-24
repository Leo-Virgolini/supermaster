package ar.com.leo.super_master_backend.dominio.auth.security;

import ar.com.leo.super_master_backend.dominio.usuario.entity.Usuario;
import ar.com.leo.super_master_backend.dominio.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Fetch con rol+permisos: este método corre desde el filtro de Spring Security,
        // potencialmente fuera de @Transactional. Rol.permisos es LAZY, así que sin
        // el JOIN FETCH la iteración tira LazyInitializationException.
        Usuario usuario = usuarioRepository.findByUsernameConRolYPermisos(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        List<SimpleGrantedAuthority> authorities = usuario.getRol().getPermisos().stream()
                .map(p -> new SimpleGrantedAuthority(p.getNombre()))
                .toList();

        return new User(
                usuario.getUsername(),
                usuario.getPasswordHash(),
                usuario.getActivo(),
                true, true, true,
                authorities
        );
    }
}

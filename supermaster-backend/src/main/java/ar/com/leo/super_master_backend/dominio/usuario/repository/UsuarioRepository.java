package ar.com.leo.super_master_backend.dominio.usuario.repository;

import ar.com.leo.super_master_backend.dominio.usuario.entity.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByUsername(String username);

    /**
     * Carga el usuario con rol y permisos en una sola query.
     * Usado por CustomUserDetailsService (que corre fuera de @Transactional)
     * para evitar LazyInitializationException al iterar permisos.
     */
    @Query("SELECT u FROM Usuario u " +
           "LEFT JOIN FETCH u.rol r " +
           "LEFT JOIN FETCH r.permisos " +
           "WHERE u.username = :username")
    Optional<Usuario> findByUsernameConRolYPermisos(String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndActivoTrue(String username);

    Page<Usuario> findByUsernameContainingIgnoreCaseOrNombreCompletoContainingIgnoreCase(
            String username, String nombreCompleto, Pageable pageable);
}

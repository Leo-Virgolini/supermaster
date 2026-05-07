package ar.com.leo.super_master_backend.dominio.usuario.repository;

import ar.com.leo.super_master_backend.dominio.usuario.entity.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndActivoTrue(String username);

    Page<Usuario> findByUsernameContainingIgnoreCaseOrNombreCompletoContainingIgnoreCase(
            String username, String nombreCompleto, Pageable pageable);
}

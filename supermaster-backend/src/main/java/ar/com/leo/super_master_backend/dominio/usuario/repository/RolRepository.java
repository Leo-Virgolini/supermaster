package ar.com.leo.super_master_backend.dominio.usuario.repository;

import ar.com.leo.super_master_backend.dominio.usuario.entity.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RolRepository extends JpaRepository<Rol, Integer> {

    Optional<Rol> findByNombre(String nombre);
}

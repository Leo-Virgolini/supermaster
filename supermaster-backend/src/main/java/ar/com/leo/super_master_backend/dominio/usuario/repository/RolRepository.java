package ar.com.leo.super_master_backend.dominio.usuario.repository;

import ar.com.leo.super_master_backend.dominio.usuario.entity.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RolRepository extends JpaRepository<Rol, Integer> {

    Optional<Rol> findByNombre(String nombre);

    /**
     * Listado con permisos en una sola query (evita N+1 al mapear cada rol a DTO).
     */
    @Query("SELECT DISTINCT r FROM Rol r LEFT JOIN FETCH r.permisos")
    List<Rol> findAllConPermisos();
}

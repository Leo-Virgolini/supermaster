package ar.com.leo.super_master_backend.dominio.usuario.repository;

import ar.com.leo.super_master_backend.dominio.usuario.entity.Permiso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermisoRepository extends JpaRepository<Permiso, Integer> {
    List<Permiso> findAllByOrderByNombreAsc();
}

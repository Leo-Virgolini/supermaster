package ar.com.leo.super_master_backend.dominio.config_automatizacion.repository;

import ar.com.leo.super_master_backend.dominio.config_automatizacion.entity.ConfigAutomatizacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfigAutomatizacionRepository extends JpaRepository<ConfigAutomatizacion, Integer> {

    Optional<ConfigAutomatizacion> findByClaveIgnoreCase(String clave);

    Page<ConfigAutomatizacion> findByClaveContainingIgnoreCaseOrDescripcionContainingIgnoreCase(
            String clave, String descripcion, Pageable pageable);

    boolean existsByClaveIgnoreCase(String clave);
}

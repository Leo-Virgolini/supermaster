package ar.com.leo.super_master_backend.dominio.material.repository;

import ar.com.leo.super_master_backend.dominio.material.entity.Material;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MaterialRepository extends JpaRepository<Material, Integer> {
    Optional<Material> findByNombreIgnoreCase(String nombre);

    Page<Material> findByNombreContainingIgnoreCase(String texto, Pageable pageable);
}
package ar.com.leo.super_master_backend.dominio.clasif_gral.repository;

import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

@Repository
public interface ClasifGralRepository extends JpaRepository<ClasifGral, Integer> {
    Optional<ClasifGral> findByNombreIgnoreCase(String nombre);
    Optional<ClasifGral> findByNombreIgnoreCaseAndPadre(String nombre, ClasifGral padre);

    Page<ClasifGral> findByNombreContainingIgnoreCase(String texto, Pageable pageable);
}
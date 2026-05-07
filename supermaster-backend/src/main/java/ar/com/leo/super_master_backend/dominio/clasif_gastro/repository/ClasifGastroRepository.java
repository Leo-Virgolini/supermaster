package ar.com.leo.super_master_backend.dominio.clasif_gastro.repository;

import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

@Repository
public interface ClasifGastroRepository extends JpaRepository<ClasifGastro, Integer> {
    Optional<ClasifGastro> findByNombreIgnoreCase(String nombre);

    Page<ClasifGastro> findByNombreContainingIgnoreCase(String texto, Pageable pageable);
}
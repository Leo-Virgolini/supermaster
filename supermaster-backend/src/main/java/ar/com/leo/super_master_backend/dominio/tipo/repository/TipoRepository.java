package ar.com.leo.super_master_backend.dominio.tipo.repository;

import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

@Repository
public interface TipoRepository extends JpaRepository<Tipo, Integer> {
    Optional<Tipo> findByNombreIgnoreCase(String nombre);
    Optional<Tipo> findByNombreIgnoreCaseAndPadre(String nombre, Tipo padre);

    Page<Tipo> findByNombreContainingIgnoreCase(String texto, Pageable pageable);
}
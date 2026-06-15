package ar.com.leo.super_master_backend.dominio.campania.repository;

import ar.com.leo.super_master_backend.dominio.campania.entity.Campania;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CampaniaRepository extends JpaRepository<Campania, Integer> {

    Optional<Campania> findByTnCategoriaId(Long tnCategoriaId);

    Page<Campania> findByNombreContainingIgnoreCase(String texto, Pageable pageable);
}

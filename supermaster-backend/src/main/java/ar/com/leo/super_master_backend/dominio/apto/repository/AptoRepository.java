package ar.com.leo.super_master_backend.dominio.apto.repository;

import ar.com.leo.super_master_backend.dominio.apto.entity.Apto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AptoRepository extends JpaRepository<Apto, Integer> {

    Page<Apto> findByNombreContainingIgnoreCase(String texto, Pageable pageable);
}
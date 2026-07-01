package ar.com.leo.super_master_backend.dominio.segmento.repository;

import ar.com.leo.super_master_backend.dominio.segmento.entity.Segmento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SegmentoRepository extends JpaRepository<Segmento, Integer> {

    Page<Segmento> findByNombreContainingIgnoreCase(String texto, Pageable pageable);
}

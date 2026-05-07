package ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.repository;

import ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.entity.CatalogoPdfConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CatalogoPdfConfigRepository extends JpaRepository<CatalogoPdfConfig, Integer> {
    List<CatalogoPdfConfig> findAllByActivoTrue();

    Page<CatalogoPdfConfig> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);
}

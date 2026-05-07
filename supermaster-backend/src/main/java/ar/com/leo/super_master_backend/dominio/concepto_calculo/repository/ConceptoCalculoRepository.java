package ar.com.leo.super_master_backend.dominio.concepto_calculo.repository;

import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.ConceptoCalculo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConceptoCalculoRepository extends JpaRepository<ConceptoCalculo, Integer> {

    Page<ConceptoCalculo> findByNombreContainingIgnoreCaseOrDescripcionContainingIgnoreCase(String nombre, String descripcion, Pageable pageable);
}

package ar.com.leo.super_master_backend.dominio.unidad_medida.repository;

import ar.com.leo.super_master_backend.dominio.unidad_medida.entity.UnidadMedida;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UnidadMedidaRepository extends JpaRepository<UnidadMedida, Integer> {
    Page<UnidadMedida> findByCodigoContainingIgnoreCase(String texto, Pageable pageable);
}

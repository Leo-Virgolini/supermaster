package ar.com.leo.super_master_backend.dominio.campania.repository;

import ar.com.leo.super_master_backend.dominio.campania.entity.CampaniaProducto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaniaProductoRepository extends JpaRepository<CampaniaProducto, Integer> {

    List<CampaniaProducto> findByCampaniaId(Integer campaniaId);

    Page<CampaniaProducto> findByCampaniaId(Integer campaniaId, Pageable pageable);

    long countByCampaniaId(Integer campaniaId);
}

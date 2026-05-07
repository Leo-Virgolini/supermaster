package ar.com.leo.super_master_backend.dominio.canal.repository;

import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConceptoRegla;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CanalConceptoReglaRepository extends JpaRepository<CanalConceptoRegla, Long> {

    @Override
    @EntityGraph(attributePaths = {"canal", "concepto", "tipo", "marca", "clasifGral", "clasifGastro"})
    Page<CanalConceptoRegla> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"canal", "concepto", "tipo", "marca", "clasifGral", "clasifGastro"})
    Optional<CanalConceptoRegla> findById(Long id);

    @EntityGraph(attributePaths = {"canal", "concepto", "tipo", "marca", "clasifGral", "clasifGastro"})
    List<CanalConceptoRegla> findByCanalId(Integer canalId);

    @EntityGraph(attributePaths = {"canal", "concepto", "tipo", "marca", "clasifGral", "clasifGastro"})
    List<CanalConceptoRegla> findByConceptoId(Integer conceptoId);

    List<CanalConceptoRegla> findByCanalIdAndConceptoId(Integer canalId, Integer conceptoId);

    /**
     * Obtiene reglas del canal con FETCH JOIN de relaciones para evitar N+1.
     * Usado en obtención de conceptos aplicables durante cálculos de precios.
     */
    @Query("SELECT ccr FROM CanalConceptoRegla ccr " +
           "LEFT JOIN FETCH ccr.concepto " +
           "LEFT JOIN FETCH ccr.tipo " +
           "LEFT JOIN FETCH ccr.marca " +
           "LEFT JOIN FETCH ccr.clasifGral " +
           "LEFT JOIN FETCH ccr.clasifGastro " +
           "WHERE ccr.canal.id = :canalId")
    List<CanalConceptoRegla> findByCanalIdWithRelationsFetch(@Param("canalId") Integer canalId);
}

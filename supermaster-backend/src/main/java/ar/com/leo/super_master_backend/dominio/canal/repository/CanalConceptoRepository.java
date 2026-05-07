package ar.com.leo.super_master_backend.dominio.canal.repository;

import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConcepto;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConceptoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CanalConceptoRepository extends JpaRepository<CanalConcepto, CanalConceptoId> {

    List<CanalConcepto> findByCanalId(Integer canalId);

    List<CanalConcepto> findByConceptoId(Integer conceptoId);

    boolean existsByConceptoId(Integer conceptoId);

    @Query("SELECT cc FROM CanalConcepto cc " +
           "LEFT JOIN FETCH cc.canal " +
           "WHERE cc.concepto.id = :conceptoId")
    List<CanalConcepto> findByConceptoIdWithCanalFetch(@Param("conceptoId") Integer conceptoId);

    void deleteByCanalIdAndConceptoId(Integer canalId, Integer conceptoId);

    /**
     * Obtiene conceptos del canal con FETCH JOIN del concepto para evitar N+1.
     * Usado en verificación de márgenes válidos durante recálculos masivos.
     */
    @Query("SELECT cc FROM CanalConcepto cc " +
           "LEFT JOIN FETCH cc.concepto " +
           "WHERE cc.canal.id = :canalId")
    List<CanalConcepto> findByCanalIdWithConceptoFetch(@Param("canalId") Integer canalId);

}
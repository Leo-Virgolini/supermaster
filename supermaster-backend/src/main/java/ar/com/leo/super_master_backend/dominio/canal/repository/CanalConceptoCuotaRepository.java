package ar.com.leo.super_master_backend.dominio.canal.repository;

import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConceptoCuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CanalConceptoCuotaRepository extends JpaRepository<CanalConceptoCuota, Long>,
        JpaSpecificationExecutor<CanalConceptoCuota> {

    List<CanalConceptoCuota> findByCanalId(Integer canalId);

    List<CanalConceptoCuota> findByCanalIdAndCuotas(Integer canalId, Integer cuotas);

    @Query("SELECT DISTINCT c.cuotas FROM CanalConceptoCuota c WHERE c.canal.id = :canalId ORDER BY c.cuotas")
    List<Integer> findDistinctCuotasByCanalId(Integer canalId);

    List<CanalConceptoCuota> findByCanalIdIn(Collection<Integer> canalIds);

    @Query("SELECT DISTINCT c.canal.id FROM CanalConceptoCuota c ORDER BY c.canal.id")
    List<Integer> findDistinctCanalIds();

    boolean existsByCanalId(Integer canalId);
}


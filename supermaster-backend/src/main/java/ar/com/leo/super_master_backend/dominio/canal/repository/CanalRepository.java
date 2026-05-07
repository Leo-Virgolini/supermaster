package ar.com.leo.super_master_backend.dominio.canal.repository;

import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CanalRepository extends JpaRepository<Canal, Integer> {
    Optional<Canal> findByNombreIgnoreCase(String nombre);

    Page<Canal> findByNombreContainingIgnoreCase(String texto, Pageable pageable);

    /** Subcanales directos de un canal (los que lo tienen como canalBase). */
    List<Canal> findByCanalBaseId(Integer canalBaseId);
}
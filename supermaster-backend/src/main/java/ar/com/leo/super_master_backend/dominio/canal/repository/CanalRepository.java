package ar.com.leo.super_master_backend.dominio.canal.repository;

import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CanalRepository extends JpaRepository<Canal, Integer> {
    Optional<Canal> findByNombreIgnoreCase(String nombre);

    Page<Canal> findByNombreContainingIgnoreCase(String texto, Pageable pageable);

    /** Subcanales directos de un canal (los que lo tienen como canalBase). */
    List<Canal> findByCanalBaseId(Integer canalBaseId);

    // =============================================
    // REEVALUACIÓN PENDIENTE DEL CATÁLOGO POR CANAL
    // (cuando cambia algo que puede agregar/quitar productos al canal)
    // =============================================

    @Modifying
    @Query("UPDATE Canal c SET c.requiereReevaluarCatalogo = TRUE, c.motivoReevaluar = :motivo, c.marcadoReevaluarAt = CURRENT_TIMESTAMP WHERE c.id IN :canalIds")
    int marcarRequiereReevaluarPorIds(@Param("canalIds") Collection<Integer> canalIds, @Param("motivo") String motivo);

    @Modifying
    @Query("UPDATE Canal c SET c.requiereReevaluarCatalogo = TRUE, c.motivoReevaluar = :motivo, c.marcadoReevaluarAt = CURRENT_TIMESTAMP")
    int marcarTodosRequiereReevaluar(@Param("motivo") String motivo);

    @Modifying
    @Query("UPDATE Canal c SET c.requiereReevaluarCatalogo = FALSE, c.motivoReevaluar = NULL, c.marcadoReevaluarAt = NULL WHERE c.id IN :canalIds")
    int desmarcarRequiereReevaluarPorIds(@Param("canalIds") Collection<Integer> canalIds);

    @Modifying
    @Query("UPDATE Canal c SET c.requiereReevaluarCatalogo = FALSE, c.motivoReevaluar = NULL, c.marcadoReevaluarAt = NULL WHERE c.requiereReevaluarCatalogo = TRUE")
    int desmarcarTodosRequiereReevaluar();

    @Query("SELECT c.id FROM Canal c WHERE c.requiereReevaluarCatalogo = TRUE ORDER BY c.id")
    List<Integer> findIdsRequierenReevaluar();

    /**
     * Resumen agrupado por motivo para el snapshot del banner.
     * Devuelve: motivo, cantidad, ultima_fecha.
     */
    @Query("SELECT c.motivoReevaluar, COUNT(c), MAX(c.marcadoReevaluarAt) FROM Canal c " +
           "WHERE c.requiereReevaluarCatalogo = TRUE AND c.motivoReevaluar IS NOT NULL " +
           "GROUP BY c.motivoReevaluar ORDER BY COUNT(c) DESC")
    List<Object[]> resumenReevaluarPorMotivo();
}

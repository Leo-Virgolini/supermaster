package ar.com.leo.super_master_backend.dominio.canal.repository;

import ar.com.leo.super_master_backend.dominio.canal.entity.CanalRegla;
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
public interface CanalReglaRepository extends JpaRepository<CanalRegla, Long> {

    @Override
    @EntityGraph(attributePaths = {"canal", "tipo", "marca", "clasifGral", "clasifGastro", "producto"})
    Page<CanalRegla> findAll(Pageable pageable);

    /**
     * Búsqueda paginada por texto (canal, marca, tipo o producto). Mantiene el
     * @EntityGraph para que el mapeo a DTO no dispare N+1 (a diferencia de un
     * findAll(Specification, ...) que no hereda el graph). El término llega no-nulo.
     */
    @EntityGraph(attributePaths = {"canal", "tipo", "marca", "clasifGral", "clasifGastro", "producto"})
    @Query("""
            SELECT cr FROM CanalRegla cr
            LEFT JOIN cr.marca m
            LEFT JOIN cr.tipo t
            LEFT JOIN cr.producto p
            WHERE LOWER(cr.canal.nombre) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(m.nombre)        LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(t.nombre)        LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(p.sku)           LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(p.descripcion)   LIKE LOWER(CONCAT('%', :search, '%'))
            """)
    Page<CanalRegla> buscar(@Param("search") String search, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"canal", "tipo", "marca", "clasifGral", "clasifGastro", "producto"})
    Optional<CanalRegla> findById(Long id);

    @EntityGraph(attributePaths = {"canal", "tipo", "marca", "clasifGral", "clasifGastro", "producto"})
    List<CanalRegla> findByCanalId(Integer canalId);

    /**
     * Reglas del canal con fetch de relaciones para evaluar exclusiones en el
     * cálculo de precios sin N+1.
     */
    @Query("SELECT cr FROM CanalRegla cr " +
           "LEFT JOIN FETCH cr.tipo " +
           "LEFT JOIN FETCH cr.marca " +
           "LEFT JOIN FETCH cr.clasifGral " +
           "LEFT JOIN FETCH cr.clasifGastro " +
           "LEFT JOIN FETCH cr.producto " +
           "WHERE cr.canal.id = :canalId")
    List<CanalRegla> findByCanalIdWithRelationsFetch(@Param("canalId") Integer canalId);
}

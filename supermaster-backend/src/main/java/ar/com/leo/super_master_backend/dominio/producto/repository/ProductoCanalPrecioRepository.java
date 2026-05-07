package ar.com.leo.super_master_backend.dominio.producto.repository;

import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCanalPrecio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoCanalPrecioRepository extends JpaRepository<ProductoCanalPrecio, Integer>, JpaSpecificationExecutor<ProductoCanalPrecio> {

    List<ProductoCanalPrecio> findByProductoId(Integer productoId);

    List<ProductoCanalPrecio> findByProductoIdOrderByCanalIdAscCuotasAsc(Integer productoId);

    List<ProductoCanalPrecio> findByCanalId(Integer canalId);

    @Query("SELECT p FROM ProductoCanalPrecio p WHERE p.canal.id = :canalId AND ((:cuotas IS NULL AND p.cuotas IS NULL) OR p.cuotas = :cuotas)")
    List<ProductoCanalPrecio> findByCanalIdAndCuotas(@Param("canalId") Integer canalId, @Param("cuotas") Integer cuotas);

    /**
     * Variante con FETCH JOIN de producto y mla para evitar N+1 en sincronizaciones masivas.
     */
    @Query("SELECT DISTINCT p FROM ProductoCanalPrecio p " +
           "LEFT JOIN FETCH p.producto prod " +
           "LEFT JOIN FETCH prod.mla " +
           "WHERE p.canal.id = :canalId AND ((:cuotas IS NULL AND p.cuotas IS NULL) OR p.cuotas = :cuotas)")
    List<ProductoCanalPrecio> findByCanalIdAndCuotasWithProductoAndMla(@Param("canalId") Integer canalId, @Param("cuotas") Integer cuotas);

    Optional<ProductoCanalPrecio> findByProductoIdAndCanalId(Integer productoId, Integer canalId);

    Optional<ProductoCanalPrecio> findByProductoIdAndCanalIdAndCuotas(Integer productoId, Integer canalId, Integer cuotas);

    List<ProductoCanalPrecio> findByProductoIdAndCanalIdOrderByCuotasAsc(Integer productoId, Integer canalId);

    /**
     * Obtiene precios por lista de productos con FETCH JOIN del canal para evitar N+1.
     */
    @Query("SELECT p FROM ProductoCanalPrecio p " +
           "LEFT JOIN FETCH p.canal " +
           "WHERE p.producto.id IN :productoIds " +
           "ORDER BY p.producto.id ASC, p.canal.id ASC, p.cuotas ASC")
    List<ProductoCanalPrecio> findByProductoIdInOrderByProductoIdAscCanalIdAscCuotasAsc(@Param("productoIds") List<Integer> productoIds);

    /**
     * Obtiene todos los precios con FETCH JOIN de canal y producto para estadísticas.
     */
    @Query("SELECT p FROM ProductoCanalPrecio p " +
           "LEFT JOIN FETCH p.canal " +
           "LEFT JOIN FETCH p.producto")
    List<ProductoCanalPrecio> findAllWithCanalAndProducto();

    @Query("DELETE FROM ProductoCanalPrecio p WHERE p.canal.id = :canalId AND ((:cuotas IS NULL AND p.cuotas IS NULL) OR p.cuotas = :cuotas)")
    @org.springframework.data.jpa.repository.Modifying
    int deleteByCanalIdAndCuotas(@Param("canalId") Integer canalId, @Param("cuotas") Integer cuotas);

    boolean existsByCanalIdAndCuotas(Integer canalId, Integer cuotas);

    boolean existsByCanalId(Integer canalId);

    void deleteByProductoIdAndCanalIdAndCuotasNotIn(Integer productoId, Integer canalId, List<Integer> cuotas);

    @Query("SELECT DISTINCT p.canal.id FROM ProductoCanalPrecio p WHERE p.producto.id = :productoId")
    List<Integer> findDistinctCanalIdsByProductoId(@Param("productoId") Integer productoId);

    /**
     * Obtiene precios por canal con FETCH JOIN del producto para evitar N+1.
     * Usado en recálculos masivos donde se itera sobre los precios.
     */
    @Query("SELECT DISTINCT p FROM ProductoCanalPrecio p " +
           "LEFT JOIN FETCH p.producto " +
           "WHERE p.canal.id = :canalId")
    List<ProductoCanalPrecio> findByCanalIdWithProductoFetch(@Param("canalId") Integer canalId);

}
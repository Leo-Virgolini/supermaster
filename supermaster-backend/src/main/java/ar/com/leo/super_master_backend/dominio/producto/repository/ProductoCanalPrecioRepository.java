package ar.com.leo.super_master_backend.dominio.producto.repository;

import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCanalPrecio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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
    @Modifying
    int deleteByCanalIdAndCuotas(@Param("canalId") Integer canalId, @Param("cuotas") Integer cuotas);

    // =============================================
    // OBSOLESCENCIA: marcado bulk de precios desactualizados
    // =============================================

    @Modifying
    @Query("UPDATE ProductoCanalPrecio p SET p.obsoleto = TRUE, p.motivoObsoleto = :motivo, p.marcadoObsoletoAt = CURRENT_TIMESTAMP WHERE p.producto.id IN :productoIds")
    int marcarObsoletoPorProductos(@Param("productoIds") Collection<Integer> productoIds, @Param("motivo") String motivo);

    @Modifying
    @Query("UPDATE ProductoCanalPrecio p SET p.obsoleto = TRUE, p.motivoObsoleto = :motivo, p.marcadoObsoletoAt = CURRENT_TIMESTAMP WHERE p.canal.id IN :canalIds")
    int marcarObsoletoPorCanales(@Param("canalIds") Collection<Integer> canalIds, @Param("motivo") String motivo);

    @Modifying
    @Query("UPDATE ProductoCanalPrecio p SET p.obsoleto = TRUE, p.motivoObsoleto = :motivo, p.marcadoObsoletoAt = CURRENT_TIMESTAMP")
    int marcarTodoObsoleto(@Param("motivo") String motivo);

    @Modifying
    @Query("UPDATE ProductoCanalPrecio p SET p.obsoleto = FALSE, p.motivoObsoleto = NULL, p.marcadoObsoletoAt = NULL WHERE p.producto.id = :productoId AND p.obsoleto = TRUE")
    int desmarcarObsoletoPorProducto(@Param("productoId") Integer productoId);

    @Modifying
    @Query("UPDATE ProductoCanalPrecio p SET p.obsoleto = FALSE, p.motivoObsoleto = NULL, p.marcadoObsoletoAt = NULL WHERE p.canal.id = :canalId AND p.obsoleto = TRUE")
    int desmarcarObsoletoPorCanal(@Param("canalId") Integer canalId);

    @Modifying
    @Query("UPDATE ProductoCanalPrecio p SET p.obsoleto = FALSE, p.motivoObsoleto = NULL, p.marcadoObsoletoAt = NULL WHERE p.obsoleto = TRUE")
    int desmarcarTodosObsoletos();

    @Query("SELECT DISTINCT p.producto.id FROM ProductoCanalPrecio p WHERE p.obsoleto = TRUE ORDER BY p.producto.id")
    List<Integer> findDistinctProductoIdsObsoletos();

    /**
     * Productos con precios obsoletos en canales QUE NO están en la lista dada.
     * Usado por el plan de recálculo: los canales en reevaluación se recalculan
     * completos (todo el catálogo), así que no hace falta recalcular individualmente
     * los productos cuyo único motivo de obsolescencia es uno de esos canales.
     * Evita el doble trabajo "producto × todos los canales" + "canal completo".
     */
    @Query("SELECT DISTINCT p.producto.id FROM ProductoCanalPrecio p WHERE p.obsoleto = TRUE AND p.canal.id NOT IN :canalIds ORDER BY p.producto.id")
    List<Integer> findDistinctProductoIdsObsoletosExcluyendoCanales(@Param("canalIds") Collection<Integer> canalIds);

    /**
     * Resumen agrupado por motivo para construir el snapshot del banner.
     * Devuelve filas con: motivo, cantidad_de_productos_unicos, ultima_fecha.
     *
     * <p>Se cuenta {@code COUNT(DISTINCT producto.id)} (no filas) para que el conteo
     * mostrado al usuario coincida con el contador principal del banner ("N producto
     * pendiente"). Cada producto típicamente tiene varias filas en
     * {@code producto_canal_precios} (canal × cuotas); contarlas todas daría números
     * inflados y confusos (ej.: 16x por 1 solo producto marcado).
     */
    @Query("SELECT p.motivoObsoleto, COUNT(DISTINCT p.producto.id), MAX(p.marcadoObsoletoAt) " +
           "FROM ProductoCanalPrecio p " +
           "WHERE p.obsoleto = TRUE AND p.motivoObsoleto IS NOT NULL " +
           "GROUP BY p.motivoObsoleto ORDER BY COUNT(DISTINCT p.producto.id) DESC")
    List<Object[]> resumenObsoletosPorMotivo();

    boolean existsByCanalIdAndCuotas(Integer canalId, Integer cuotas);

    boolean existsByCanalId(Integer canalId);

    boolean existsByProductoId(Integer productoId);

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
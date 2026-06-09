package ar.com.leo.super_master_backend.dominio.producto.repository;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Integer>, JpaSpecificationExecutor<Producto> {

    Optional<Producto> findBySku(String sku);

    List<Producto> findBySkuIn(List<String> skus);

    /**
     * Devuelve, ordenados ascendentemente, los SKU numéricos existentes dentro de un
     * rango [min, max]. Filtra los SKU no numéricos (REGEXP) y castea a entero para
     * poder buscar el menor hueco disponible. Usado para autocompletar el SKU libre.
     */
    @Query(value = "SELECT CAST(p.sku AS UNSIGNED) FROM productos p " +
            "WHERE p.sku REGEXP '^[0-9]+$' AND CAST(p.sku AS UNSIGNED) BETWEEN :min AND :max " +
            "ORDER BY CAST(p.sku AS UNSIGNED)", nativeQuery = true)
    List<Long> findSkusNumericosEnRango(@Param("min") long min, @Param("max") long max);

    /**
     * Obtiene un producto con sus relaciones necesarias para evaluar reglas de canal_concepto_regla.
     * Usa LEFT JOIN FETCH para cargar marca, clasifGral, clasifGastro y tipo de forma eager.
     */
    @Query("SELECT p FROM Producto p " +
           "LEFT JOIN FETCH p.marca " +
           "LEFT JOIN FETCH p.clasifGral " +
           "LEFT JOIN FETCH p.clasifGastro " +
           "LEFT JOIN FETCH p.tipo " +
           "WHERE p.id = :id")
    Optional<Producto> findByIdConRelacionesParaReglas(Integer id);

    List<Producto> findByMarcaId(Integer marcaId);

    List<Producto> findByOrigenId(Integer origenId);

    List<Producto> findByClasifGralId(Integer clasifGralId);

    List<Producto> findByClasifGastroId(Integer clasifGastroId);

    List<Producto> findByTipoId(Integer tipoId);

    List<Producto> findByProveedorId(Integer proveedorId);

    List<Producto> findByMlaId(Integer mlaId);

    List<Producto> findByMaterialId(Integer materialId);

    @Query("SELECT p FROM Producto p JOIN FETCH p.proveedor WHERE p.activo = true AND p.proveedor IS NOT NULL")
    List<Producto> findActivosConProveedor();

    /**
     * Carga productos con todas las relaciones ManyToOne en un solo query (evita N+1).
     * Usado por listarConPrecios para construir DTOs sin lazy loading.
     */
    @Query("SELECT DISTINCT p FROM Producto p " +
           "LEFT JOIN FETCH p.marca " +
           "LEFT JOIN FETCH p.origen " +
           "LEFT JOIN FETCH p.clasifGral " +
           "LEFT JOIN FETCH p.clasifGastro " +
           "LEFT JOIN FETCH p.tipo " +
           "LEFT JOIN FETCH p.proveedor " +
           "LEFT JOIN FETCH p.material " +
           "LEFT JOIN FETCH p.mla " +
           "WHERE p.id IN :ids")
    List<Producto> findAllByIdWithRelaciones(@Param("ids") List<Integer> ids);

    /**
     * Obtiene todos los productos con FETCH JOIN del proveedor para estadísticas.
     */
    @Query("SELECT p FROM Producto p LEFT JOIN FETCH p.proveedor")
    List<Producto> findAllWithProveedor();

    /**
     * Como {@link #findAllWithProveedor()} pero además hace fetch de la colección
     * productoCatalogos + catalogo. Usado por las estadísticas que agrupan por
     * catálogo: sin esto, iterar producto.getProductoCatalogos() dispara fetches
     * por batches (BatchSize=50) en lugar de una sola query.
     */
    @Query("SELECT DISTINCT p FROM Producto p " +
           "LEFT JOIN FETCH p.proveedor " +
           "LEFT JOIN FETCH p.productoCatalogos pc " +
           "LEFT JOIN FETCH pc.catalogo")
    List<Producto> findAllWithProveedorYCatalogos();

    /**
     * Devuelve solo los IDs de productos. Usado por recálculos masivos que solo
     * necesitan el ID para iterar (evita cargar entidades completas + sus relaciones).
     */
    @Query("SELECT p.id FROM Producto p")
    List<Integer> findAllIds();

    @Modifying
    @Query("UPDATE Producto p SET p.stock = :stock WHERE p.sku = :sku")
    int updateStockBySku(@Param("sku") String sku, @Param("stock") Integer stock);

    @Modifying
    @Query("UPDATE Producto p SET p.stock = :stock, p.costo = :costo, p.fechaUltimoCosto = :fechaUltimoCosto WHERE p.sku = :sku")
    int updateStockAndCostoBySku(@Param("sku") String sku, @Param("stock") Integer stock,
                                  @Param("costo") BigDecimal costo, @Param("fechaUltimoCosto") LocalDateTime fechaUltimoCosto);

}

package ar.com.leo.super_master_backend.dominio.producto.repository;

import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCanalPrecioInflado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoCanalPrecioInfladoRepository extends JpaRepository<ProductoCanalPrecioInflado, Integer> {

    Optional<ProductoCanalPrecioInflado> findByProductoIdAndCanalId(Integer productoId, Integer canalId);

    List<ProductoCanalPrecioInflado> findByProductoId(Integer productoId);

    List<ProductoCanalPrecioInflado> findByCanalId(Integer canalId);

    List<ProductoCanalPrecioInflado> findByActivoTrue();

    List<ProductoCanalPrecioInflado> findByPrecioInfladoId(Integer precioInfladoId);

    long countByPrecioInfladoId(Integer precioInfladoId);

    @Query("SELECT pcpi FROM ProductoCanalPrecioInflado pcpi JOIN FETCH pcpi.precioInflado")
    List<ProductoCanalPrecioInflado> findAllWithPrecioInfladoFetch();

    @Query("SELECT pcpi FROM ProductoCanalPrecioInflado pcpi JOIN FETCH pcpi.precioInflado WHERE pcpi.producto.id IN :productoIds AND pcpi.activo = true")
    List<ProductoCanalPrecioInflado> findByProductoIdInAndActivoTrueWithFetch(List<Integer> productoIds);
}

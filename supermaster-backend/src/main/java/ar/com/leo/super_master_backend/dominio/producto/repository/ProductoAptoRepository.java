package ar.com.leo.super_master_backend.dominio.producto.repository;

import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoApto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoAptoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductoAptoRepository extends JpaRepository<ProductoApto, ProductoAptoId> {

    List<ProductoApto> findByProductoId(Integer productoId);

    @Query("SELECT pa FROM ProductoApto pa " +
           "JOIN FETCH pa.apto " +
           "WHERE pa.producto.id IN :productoIds " +
           "ORDER BY pa.producto.id ASC, pa.apto.nombre ASC")
    List<ProductoApto> findByProductoIdInWithApto(List<Integer> productoIds);

    List<ProductoApto> findByAptoId(Integer aptoId);

    boolean existsByAptoId(Integer aptoId);

    void deleteByProductoIdAndAptoId(Integer productoId, Integer aptoId);
}

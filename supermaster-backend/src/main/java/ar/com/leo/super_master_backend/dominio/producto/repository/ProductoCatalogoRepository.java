package ar.com.leo.super_master_backend.dominio.producto.repository;

import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCatalogo;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCatalogoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductoCatalogoRepository extends JpaRepository<ProductoCatalogo, ProductoCatalogoId> {

    List<ProductoCatalogo> findByCatalogoId(Integer catalogoId);

    boolean existsByCatalogoId(Integer catalogoId);

    List<ProductoCatalogo> findByProductoId(Integer productoId);

    @Query("SELECT pc FROM ProductoCatalogo pc " +
           "JOIN FETCH pc.catalogo " +
           "WHERE pc.producto.id IN :productoIds " +
           "ORDER BY pc.producto.id ASC, pc.catalogo.nombre ASC")
    List<ProductoCatalogo> findByProductoIdInWithCatalogo(List<Integer> productoIds);

    void deleteByProductoIdAndCatalogoId(Integer productoId, Integer catalogoId);

}

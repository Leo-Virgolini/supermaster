package ar.com.leo.super_master_backend.dominio.producto.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoMargen;

@Repository
public interface ProductoMargenRepository extends JpaRepository<ProductoMargen, Integer> {

    Optional<ProductoMargen> findByProductoId(Integer productoId);

    @Query("SELECT pm FROM ProductoMargen pm JOIN FETCH pm.producto WHERE pm.producto.id IN :productoIds")
    List<ProductoMargen> findByProductoIdIn(@Param("productoIds") List<Integer> productoIds);

    void deleteByProductoId(Integer productoId);

    @Query("SELECT pm FROM ProductoMargen pm JOIN FETCH pm.producto p " +
           "LEFT JOIN FETCH p.marca LEFT JOIN FETCH p.origen LEFT JOIN FETCH p.clasifGral " +
           "LEFT JOIN FETCH p.clasifGastro LEFT JOIN FETCH p.tipo LEFT JOIN FETCH p.proveedor " +
           "LEFT JOIN FETCH p.material LEFT JOIN FETCH p.mla")
    List<ProductoMargen> findAllWithProductoFetch();

}

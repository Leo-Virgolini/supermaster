package ar.com.leo.super_master_backend.dominio.producto.repository;

import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoSegmento;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoSegmentoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductoSegmentoRepository extends JpaRepository<ProductoSegmento, ProductoSegmentoId> {

    List<ProductoSegmento> findBySegmentoId(Integer segmentoId);

    boolean existsBySegmentoId(Integer segmentoId);

    List<ProductoSegmento> findByProductoId(Integer productoId);

    @Query("SELECT pc FROM ProductoSegmento pc " +
           "JOIN FETCH pc.segmento " +
           "WHERE pc.producto.id IN :productoIds " +
           "ORDER BY pc.producto.id ASC, pc.segmento.nombre ASC")
    List<ProductoSegmento> findByProductoIdInWithSegmento(List<Integer> productoIds);

    void deleteByProductoIdAndSegmentoId(Integer productoId, Integer segmentoId);

}

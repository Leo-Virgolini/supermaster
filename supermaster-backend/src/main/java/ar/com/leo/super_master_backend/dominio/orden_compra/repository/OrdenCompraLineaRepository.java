package ar.com.leo.super_master_backend.dominio.orden_compra.repository;

import ar.com.leo.super_master_backend.dominio.orden_compra.entity.EstadoOrdenCompra;
import ar.com.leo.super_master_backend.dominio.orden_compra.entity.OrdenCompraLinea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrdenCompraLineaRepository extends JpaRepository<OrdenCompraLinea, Integer> {

    @Query("SELECT l.producto.id, SUM(l.cantidadPedida - l.cantidadRecibida) " +
           "FROM OrdenCompraLinea l " +
           "WHERE l.ordenCompra.estado IN :estados " +
           "AND l.cantidadPedida > l.cantidadRecibida " +
           "GROUP BY l.producto.id")
    List<Object[]> findPendientesPorProducto(@Param("estados") List<EstadoOrdenCompra> estados);

    @Query("SELECT l.producto.id, l.ordenCompra.fechaCreacion, l.cantidadPedida " +
           "FROM OrdenCompraLinea l " +
           "WHERE l.ordenCompra.estado NOT IN :estadosExcluidos " +
           "ORDER BY l.ordenCompra.fechaCreacion DESC")
    List<Object[]> findUltimasCompras(@Param("estadosExcluidos") List<EstadoOrdenCompra> estadosExcluidos);
}

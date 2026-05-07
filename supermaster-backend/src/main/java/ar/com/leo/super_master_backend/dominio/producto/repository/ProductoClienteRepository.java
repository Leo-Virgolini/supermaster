package ar.com.leo.super_master_backend.dominio.producto.repository;

import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCliente;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoClienteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductoClienteRepository extends JpaRepository<ProductoCliente, ProductoClienteId> {

    List<ProductoCliente> findByClienteId(Integer clienteId);

    boolean existsByClienteId(Integer clienteId);

    List<ProductoCliente> findByProductoId(Integer productoId);

    @Query("SELECT pc FROM ProductoCliente pc " +
           "JOIN FETCH pc.cliente " +
           "WHERE pc.producto.id IN :productoIds " +
           "ORDER BY pc.producto.id ASC, pc.cliente.nombre ASC")
    List<ProductoCliente> findByProductoIdInWithCliente(List<Integer> productoIds);

    void deleteByProductoIdAndClienteId(Integer productoId, Integer clienteId);

}

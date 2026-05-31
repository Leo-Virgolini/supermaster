package ar.com.leo.super_master_backend.dominio.orden_compra.repository;

import ar.com.leo.super_master_backend.dominio.orden_compra.entity.EstadoOrdenCompra;
import ar.com.leo.super_master_backend.dominio.orden_compra.entity.OrdenCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface OrdenCompraRepository extends JpaRepository<OrdenCompra, Integer>,
        JpaSpecificationExecutor<OrdenCompra> {

    Page<OrdenCompra> findByProveedorId(Integer proveedorId, Pageable pageable);

    Page<OrdenCompra> findByEstado(EstadoOrdenCompra estado, Pageable pageable);

    Page<OrdenCompra> findByProveedorIdAndEstado(Integer proveedorId, EstadoOrdenCompra estado, Pageable pageable);

    List<OrdenCompra> findByEstadoIn(List<EstadoOrdenCompra> estados);

    boolean existsByProveedorIdAndEstado(Integer proveedorId, EstadoOrdenCompra estado);
}

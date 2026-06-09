package ar.com.leo.super_master_backend.dominio.proveedor.repository;

import ar.com.leo.super_master_backend.dominio.proveedor.entity.Proveedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProveedorRepository extends JpaRepository<Proveedor, Integer> {

    Optional<Proveedor> findByNombreIgnoreCase(String nombre);

    Page<Proveedor> findByNombreContainingIgnoreCaseOrApodoContainingIgnoreCase(String nombre, String apodo, Pageable pageable);
}
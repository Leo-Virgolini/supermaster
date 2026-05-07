package ar.com.leo.super_master_backend.dominio.cliente.repository;

import ar.com.leo.super_master_backend.dominio.cliente.entity.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    Page<Cliente> findByNombreContainingIgnoreCase(String texto, Pageable pageable);
}
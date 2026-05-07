package ar.com.leo.super_master_backend.dominio.precio_inflado.repository;

import ar.com.leo.super_master_backend.dominio.precio_inflado.entity.PrecioInflado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

@Repository
public interface PrecioInfladoRepository extends JpaRepository<PrecioInflado, Integer> {

    Optional<PrecioInflado> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    Page<PrecioInflado> findByCodigoContainingIgnoreCase(String texto, Pageable pageable);
}

package ar.com.leo.super_master_backend.dominio.sector_deposito.repository;

import ar.com.leo.super_master_backend.dominio.sector_deposito.entity.SectorDeposito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SectorDepositoRepository extends JpaRepository<SectorDeposito, Integer> {
    Page<SectorDeposito> findByCodigoContainingIgnoreCase(String texto, Pageable pageable);
}

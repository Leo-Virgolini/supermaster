package ar.com.leo.super_master_backend.dominio.sector_deposito.service;

import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import ar.com.leo.super_master_backend.dominio.sector_deposito.dto.SectorDepositoCreateDTO;
import ar.com.leo.super_master_backend.dominio.sector_deposito.dto.SectorDepositoDTO;
import ar.com.leo.super_master_backend.dominio.sector_deposito.dto.SectorDepositoPatchDTO;
import ar.com.leo.super_master_backend.dominio.sector_deposito.dto.SectorDepositoUpdateDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SectorDepositoService {

    Page<SectorDepositoDTO> listar(String search, Pageable pageable);

    SectorDepositoDTO obtener(Integer id);

    SectorDepositoDTO crear(SectorDepositoCreateDTO dto);

    SectorDepositoDTO actualizar(Integer id, SectorDepositoUpdateDTO dto);

    SectorDepositoDTO patch(Integer id, SectorDepositoPatchDTO patch);

    void eliminar(Integer id);

    List<ProductoResumenDTO> listarProductos(Integer sectorDepositoId);
}

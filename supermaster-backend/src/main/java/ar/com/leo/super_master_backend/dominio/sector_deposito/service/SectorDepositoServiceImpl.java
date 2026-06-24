package ar.com.leo.super_master_backend.dominio.sector_deposito.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import ar.com.leo.super_master_backend.dominio.producto.mapper.ProductoMapper;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import ar.com.leo.super_master_backend.dominio.sector_deposito.dto.SectorDepositoCreateDTO;
import ar.com.leo.super_master_backend.dominio.sector_deposito.dto.SectorDepositoDTO;
import ar.com.leo.super_master_backend.dominio.sector_deposito.dto.SectorDepositoPatchDTO;
import ar.com.leo.super_master_backend.dominio.sector_deposito.dto.SectorDepositoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.sector_deposito.entity.SectorDeposito;
import ar.com.leo.super_master_backend.dominio.sector_deposito.mapper.SectorDepositoMapper;
import ar.com.leo.super_master_backend.dominio.sector_deposito.repository.SectorDepositoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static ar.com.leo.super_master_backend.dominio.common.util.JsonNullableFields.*;

@Service
@RequiredArgsConstructor
public class SectorDepositoServiceImpl implements SectorDepositoService {

    private final SectorDepositoRepository repo;
    private final SectorDepositoMapper mapper;
    private final ProductoRepository productoRepository;
    private final ProductoMapper productoMapper;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional(readOnly = true)
    public Page<SectorDepositoDTO> listar(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return repo.findByCodigoContainingIgnoreCase(search, pageable)
                    .map(mapper::toDTO);
        }
        return repo.findAll(pageable)
                .map(mapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public SectorDepositoDTO obtener(Integer id) {
        return repo.findById(id)
                .map(mapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Sector de depósito no encontrado"));
    }

    @Override
    @Transactional
    public SectorDepositoDTO crear(SectorDepositoCreateDTO dto) {
        SectorDeposito entity = mapper.toEntity(dto);
        repo.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.SECTOR_DEPOSITO, entity.getId(), entity.getCodigo(), AuditoriaAccion.CREATE, Map.of(), capturarSnapshot(entity));
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public SectorDepositoDTO actualizar(Integer id, SectorDepositoUpdateDTO dto) {
        SectorDeposito entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Sector de depósito no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        mapper.updateEntityFromDTO(dto, entity);

        repo.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.SECTOR_DEPOSITO, id, entity.getCodigo(), AuditoriaAccion.UPDATE, estadoAnterior, capturarSnapshot(entity));

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public SectorDepositoDTO patch(Integer id, SectorDepositoPatchDTO patchDto) {
        if (!presente(patchDto.getCodigo()) && !presente(patchDto.getIdDux())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }

        SectorDeposito entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Sector de depósito no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        if (presente(patchDto.getCodigo())) {
            entity.setCodigo(leerStringRequerido(patchDto.getCodigo(), "codigo", 20));
        }
        if (presente(patchDto.getIdDux())) {
            entity.setIdDux(leerIntegerOpcional(patchDto.getIdDux(), "idDux"));
        }

        repo.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.SECTOR_DEPOSITO, id, entity.getCodigo(), AuditoriaAccion.UPDATE, estadoAnterior, capturarSnapshot(entity));
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        SectorDeposito entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Sector de depósito no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);
        String codigo = entity.getCodigo();
        repo.delete(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.SECTOR_DEPOSITO, id, codigo, AuditoriaAccion.DELETE, estadoAnterior, Map.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResumenDTO> listarProductos(Integer sectorDepositoId) {
        if (!repo.existsById(sectorDepositoId)) {
            throw new NotFoundException("Sector de depósito no encontrado");
        }
        return productoRepository.findBySectorDepositoId(sectorDepositoId)
                .stream()
                .map(productoMapper::toResumenDTO)
                .toList();
    }


    private Map<String, String> capturarSnapshot(SectorDeposito entity) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("codigo", normalizar(entity.getCodigo()));
        snapshot.put("idDux", normalizar(entity.getIdDux()));
        return snapshot;
    }
}

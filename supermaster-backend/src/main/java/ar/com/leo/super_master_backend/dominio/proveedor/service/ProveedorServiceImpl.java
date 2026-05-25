package ar.com.leo.super_master_backend.dominio.proveedor.service;

import ar.com.leo.super_master_backend.dominio.auditoria.dto.AuditoriaCambioDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import ar.com.leo.super_master_backend.dominio.producto.mapper.ProductoMapper;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService;
import static ar.com.leo.super_master_backend.dominio.common.util.JsonNullableFields.*;
import ar.com.leo.super_master_backend.dominio.proveedor.dto.ProveedorCreateDTO;
import ar.com.leo.super_master_backend.dominio.proveedor.dto.ProveedorDTO;
import ar.com.leo.super_master_backend.dominio.proveedor.dto.ProveedorUpdateDTO;
import ar.com.leo.super_master_backend.dominio.proveedor.dto.ProveedorPatchDTO;
import ar.com.leo.super_master_backend.dominio.proveedor.entity.Proveedor;
import ar.com.leo.super_master_backend.dominio.proveedor.mapper.ProveedorMapper;
import ar.com.leo.super_master_backend.dominio.proveedor.repository.ProveedorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProveedorServiceImpl implements ProveedorService {

    private final ProveedorRepository repo;
    private final ProveedorMapper mapper;
    private final RecalculoPendienteService recalculoPendienteService;
    private final ProductoRepository productoRepository;
    private final ProductoMapper productoMapper;
    private final ProveedorAuditoriaService proveedorAuditoriaService;

    @Override
    @Transactional(readOnly = true)
    public Page<ProveedorDTO> listar(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return repo.findByNombreContainingIgnoreCaseOrApodoContainingIgnoreCase(search, search, pageable)
                    .map(mapper::toDTO);
        }
        return repo.findAll(pageable)
                .map(mapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public ProveedorDTO obtener(Integer id) {
        return repo.findById(id)
                .map(mapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Proveedor no encontrado"));
    }

    @Override
    @Transactional
    public ProveedorDTO crear(ProveedorCreateDTO dto) {
        Proveedor entity = mapper.toEntity(dto);
        repo.save(entity);
        proveedorAuditoriaService.registrarCreacion(entity);
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public ProveedorDTO actualizar(Integer id, ProveedorUpdateDTO dto) {
        Proveedor entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Proveedor no encontrado"));
        var estadoAnterior = proveedorAuditoriaService.capturarSnapshot(entity);

        BigDecimal porcentajeAnterior = entity.getFinanciacionPorcentaje();

        mapper.updateEntityFromDTO(dto, entity);
        repo.save(entity);
        proveedorAuditoriaService.registrarActualizacion(id, estadoAnterior, entity);

        // Recalcular precios si cambió el porcentaje de financiación
        if (cambioPorcentaje(porcentajeAnterior, entity.getFinanciacionPorcentaje())) {
            // Solo los productos del proveedor — no recalcular los 5500+ productos del catálogo.
            marcarProductosDelProveedor(id, "Cambio en proveedor");
        }

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public ProveedorDTO patch(Integer id, ProveedorPatchDTO patchDto) {
        if (!presente(patchDto.getNombre())
                && !presente(patchDto.getApodo())
                && !presente(patchDto.getPlazoPago())
                && !presente(patchDto.getEntrega())
                && !presente(patchDto.getFinanciacionPorcentaje())
                && !presente(patchDto.getLeadTimeDias())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }


        Proveedor entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Proveedor no encontrado"));
        var estadoAnterior = proveedorAuditoriaService.capturarSnapshot(entity);

        BigDecimal porcentajeAnterior = entity.getFinanciacionPorcentaje();

        if (presente(patchDto.getNombre())) {
            entity.setNombre(leerStringRequerido(patchDto.getNombre(), "nombre", 100));
        }
        if (presente(patchDto.getApodo())) {
            entity.setApodo(leerStringRequerido(patchDto.getApodo(), "apodo", 50));
        }
        if (presente(patchDto.getPlazoPago())) {
            entity.setPlazoPago(leerStringOpcional(patchDto.getPlazoPago(), "plazoPago", 45));
        }
        if (presente(patchDto.getEntrega())) {
            entity.setEntrega(leerBooleanOpcional(patchDto.getEntrega(), "entrega"));
        }
        if (presente(patchDto.getFinanciacionPorcentaje())) {
            entity.setFinanciacionPorcentaje(leerPorcentajeOpcional(patchDto.getFinanciacionPorcentaje(), "financiacionPorcentaje"));
        }
        if (presente(patchDto.getLeadTimeDias())) {
            entity.setLeadTimeDias(leerIntegerNoNegativoOpcional(patchDto.getLeadTimeDias(), "leadTimeDias"));
        }

        repo.save(entity);
        proveedorAuditoriaService.registrarActualizacion(id, estadoAnterior, entity);

        if (cambioPorcentaje(porcentajeAnterior, entity.getFinanciacionPorcentaje())) {
            // Solo los productos del proveedor — no recalcular los 5500+ productos del catálogo.
            marcarProductosDelProveedor(id, "Cambio en proveedor");
        }

        return mapper.toDTO(entity);
    }

    private boolean cambioPorcentaje(BigDecimal anterior, BigDecimal nuevo) {
        if (anterior == null && nuevo == null) return false;
        if (anterior == null || nuevo == null) return true;
        return anterior.compareTo(nuevo) != 0;
    }

    private void marcarProductosDelProveedor(Integer proveedorId, String motivo) {
        List<Integer> productoIds = productoRepository.findByProveedorId(proveedorId).stream()
                .map(p -> p.getId())
                .toList();
        recalculoPendienteService.marcarProductos(motivo, productoIds);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        Proveedor entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Proveedor no encontrado"));
        var estadoAnterior = proveedorAuditoriaService.capturarSnapshot(entity);
        repo.delete(entity);
        proveedorAuditoriaService.registrarEliminacion(id, estadoAnterior);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResumenDTO> listarProductos(Integer proveedorId) {
        if (!repo.existsById(proveedorId)) {
            throw new NotFoundException("Proveedor no encontrado");
        }
        return productoRepository.findByProveedorId(proveedorId)
                .stream()
                .map(productoMapper::toResumenDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditoriaCambioDTO> listarAuditoria(Integer proveedorId, Pageable pageable) {
        if (!repo.existsById(proveedorId)) {
            throw new NotFoundException("Proveedor no encontrado");
        }
        return proveedorAuditoriaService.listarPorProveedor(proveedorId, pageable);
    }


}







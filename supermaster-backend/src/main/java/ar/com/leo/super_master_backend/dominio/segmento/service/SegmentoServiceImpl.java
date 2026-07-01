package ar.com.leo.super_master_backend.dominio.segmento.service;

import ar.com.leo.super_master_backend.dominio.auditoria.dto.AuditoriaCambioDTO;
import ar.com.leo.super_master_backend.dominio.segmento.dto.SegmentoCreateDTO;
import ar.com.leo.super_master_backend.dominio.segmento.dto.SegmentoDTO;
import ar.com.leo.super_master_backend.dominio.segmento.dto.SegmentoPatchDTO;
import ar.com.leo.super_master_backend.dominio.segmento.dto.SegmentoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.segmento.entity.Segmento;
import ar.com.leo.super_master_backend.dominio.segmento.mapper.SegmentoMapper;
import ar.com.leo.super_master_backend.dominio.segmento.repository.SegmentoRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import ar.com.leo.super_master_backend.dominio.producto.mapper.ProductoMapper;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoSegmentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static ar.com.leo.super_master_backend.dominio.common.util.JsonNullableFields.leerStringRequerido;
import static ar.com.leo.super_master_backend.dominio.common.util.JsonNullableFields.presente;

@Service
@RequiredArgsConstructor
public class SegmentoServiceImpl implements SegmentoService {

    private final SegmentoRepository repo;
    private final SegmentoMapper mapper;
    private final ProductoSegmentoRepository productoSegmentoRepository;
    private final ProductoMapper productoMapper;
    private final SegmentoAuditoriaService segmentoAuditoriaService;

    @Override
    @Transactional(readOnly = true)
    public Page<SegmentoDTO> listar(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return repo.findByNombreContainingIgnoreCase(search, pageable)
                    .map(mapper::toDTO);
        }
        return repo.findAll(pageable)
                .map(mapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public SegmentoDTO obtener(Integer id) {
        return repo.findById(id)
                .map(mapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Segmento no encontrado"));
    }

    @Override
    @Transactional
    public SegmentoDTO crear(SegmentoCreateDTO dto) {
        Segmento entity = mapper.toEntity(dto);
        repo.save(entity);
        segmentoAuditoriaService.registrarCreacion(entity);
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public SegmentoDTO actualizar(Integer id, SegmentoUpdateDTO dto) {
        Segmento entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Segmento no encontrado"));
        var estadoAnterior = segmentoAuditoriaService.capturarSnapshot(entity);

        mapper.updateEntityFromDTO(dto, entity);

        repo.save(entity);
        segmentoAuditoriaService.registrarActualizacion(id, estadoAnterior, entity);

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public SegmentoDTO patch(Integer id, SegmentoPatchDTO patchDto) {
        if (!presente(patchDto.getNombre())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }


        Segmento entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Segmento no encontrado"));
        var estadoAnterior = segmentoAuditoriaService.capturarSnapshot(entity);

        entity.setNombre(leerStringRequerido(patchDto.getNombre(), "nombre", 45));

        repo.save(entity);
        segmentoAuditoriaService.registrarActualizacion(id, estadoAnterior, entity);
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        Segmento entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Segmento no encontrado"));
        var estadoAnterior = segmentoAuditoriaService.capturarSnapshot(entity);

        repo.delete(entity);
        segmentoAuditoriaService.registrarEliminacion(id, estadoAnterior);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResumenDTO> listarProductos(Integer segmentoId) {
        if (!repo.existsById(segmentoId)) {
            throw new NotFoundException("Segmento no encontrado");
        }
        return productoSegmentoRepository.findBySegmentoId(segmentoId)
                .stream()
                .map(ps -> productoMapper.toResumenDTO(ps.getProducto()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditoriaCambioDTO> listarAuditoria(Integer segmentoId, Pageable pageable) {
        if (!repo.existsById(segmentoId)) {
            throw new NotFoundException("Segmento no encontrado");
        }
        return segmentoAuditoriaService.listarPorSegmento(segmentoId, pageable);
    }


}

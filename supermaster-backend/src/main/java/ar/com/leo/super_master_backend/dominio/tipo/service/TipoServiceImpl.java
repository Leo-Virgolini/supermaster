package ar.com.leo.super_master_backend.dominio.tipo.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import ar.com.leo.super_master_backend.dominio.producto.mapper.ProductoMapper;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import ar.com.leo.super_master_backend.dominio.tipo.dto.TipoCreateDTO;
import ar.com.leo.super_master_backend.dominio.tipo.dto.TipoDTO;
import ar.com.leo.super_master_backend.dominio.tipo.dto.TipoPatchDTO;
import ar.com.leo.super_master_backend.dominio.tipo.dto.TipoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import ar.com.leo.super_master_backend.dominio.tipo.mapper.TipoMapper;
import ar.com.leo.super_master_backend.dominio.tipo.repository.TipoRepository;
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
public class TipoServiceImpl implements TipoService {

    private final TipoRepository repo;
    private final TipoMapper mapper;
    private final ProductoRepository productoRepository;
    private final ProductoMapper productoMapper;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional(readOnly = true)
    public Page<TipoDTO> listar(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return repo.findByNombreContainingIgnoreCase(search, pageable)
                    .map(mapper::toDTO);
        }
        return repo.findAll(pageable)
                .map(mapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public TipoDTO obtener(Integer id) {
        return repo.findById(id)
                .map(mapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Tipo no encontrado"));
    }

    @Override
    @Transactional
    public TipoDTO crear(TipoCreateDTO dto) {
        Tipo entity = mapper.toEntity(dto);
        repo.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.TIPO, entity.getId(), entity.getNombre(), AuditoriaAccion.CREATE, Map.of(), capturarSnapshot(entity));
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public TipoDTO actualizar(Integer id, TipoUpdateDTO dto) {
        Tipo entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Tipo no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        mapper.updateEntityFromDTO(dto, entity);

        repo.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.TIPO, id, entity.getNombre(), AuditoriaAccion.UPDATE, estadoAnterior, capturarSnapshot(entity));

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public TipoDTO patch(Integer id, TipoPatchDTO patchDto) {
        if (!presente(patchDto.getNombre()) && !presente(patchDto.getPadreId())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }


        Tipo entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Tipo no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        if (presente(patchDto.getNombre())) {
            entity.setNombre(leerStringRequerido(patchDto.getNombre(), "nombre", 45));
        }
        if (presente(patchDto.getPadreId())) {
            Integer padreId = leerIdOpcional(patchDto.getPadreId(), "padreId");
            entity.setPadre(padreId != null ? new Tipo(padreId) : null);
        }

        repo.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.TIPO, id, entity.getNombre(), AuditoriaAccion.UPDATE, estadoAnterior, capturarSnapshot(entity));
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        Tipo entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Tipo no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);
        String codigo = entity.getNombre();
        repo.delete(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.TIPO, id, codigo, AuditoriaAccion.DELETE, estadoAnterior, Map.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResumenDTO> listarProductos(Integer tipoId) {
        if (!repo.existsById(tipoId)) {
            throw new NotFoundException("Tipo no encontrado");
        }
        return productoRepository.findByTipoId(tipoId)
                .stream()
                .map(productoMapper::toResumenDTO)
                .toList();
    }


    private Map<String, String> capturarSnapshot(Tipo entity) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("nombre", normalizar(entity.getNombre()));
        snapshot.put("padreId", entity.getPadre() != null ? normalizar(entity.getPadre().getId()) : null);
        snapshot.put("padre", entity.getPadre() != null ? normalizar(entity.getPadre().getNombre()) : null);
        return snapshot;
    }
}







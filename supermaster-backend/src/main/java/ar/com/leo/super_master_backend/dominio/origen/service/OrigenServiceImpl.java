package ar.com.leo.super_master_backend.dominio.origen.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.origen.dto.OrigenCreateDTO;
import ar.com.leo.super_master_backend.dominio.origen.dto.OrigenDTO;
import ar.com.leo.super_master_backend.dominio.origen.dto.OrigenUpdateDTO;
import ar.com.leo.super_master_backend.dominio.origen.dto.OrigenPatchDTO;
import ar.com.leo.super_master_backend.dominio.origen.entity.Origen;
import ar.com.leo.super_master_backend.dominio.origen.mapper.OrigenMapper;
import ar.com.leo.super_master_backend.dominio.origen.repository.OrigenRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import ar.com.leo.super_master_backend.dominio.producto.mapper.ProductoMapper;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrigenServiceImpl implements OrigenService {

    private final OrigenRepository repo;
    private final OrigenMapper mapper;
    private final ProductoRepository productoRepository;
    private final ProductoMapper productoMapper;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional(readOnly = true)
    public Page<OrigenDTO> listar(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return repo.findByNombreContainingIgnoreCase(search, pageable)
                    .map(mapper::toDTO);
        }
        return repo.findAll(pageable)
                .map(mapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public OrigenDTO obtener(Integer id) {
        return repo.findById(id)
                .map(mapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Origen no encontrado"));
    }

    @Override
    @Transactional
    public OrigenDTO crear(OrigenCreateDTO dto) {
        Origen entity = mapper.toEntity(dto);
        repo.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.ORIGEN, entity.getId(), entity.getNombre(), AuditoriaAccion.CREATE, Map.of(), capturarSnapshot(entity));
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public OrigenDTO actualizar(Integer id, OrigenUpdateDTO dto) {
        Origen entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Origen no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        mapper.updateEntityFromDTO(dto, entity);

        repo.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.ORIGEN, id, entity.getNombre(), AuditoriaAccion.UPDATE, estadoAnterior, capturarSnapshot(entity));

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public OrigenDTO patch(Integer id, OrigenPatchDTO patchDto) {
        if (!presente(patchDto.getNombre())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }

        Origen entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Origen no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        entity.setNombre(leerStringRequerido(patchDto.getNombre(), "nombre", 45));

        repo.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.ORIGEN, id, entity.getNombre(), AuditoriaAccion.UPDATE, estadoAnterior, capturarSnapshot(entity));
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        Origen entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Origen no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);
        String codigo = entity.getNombre();
        repo.delete(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.ORIGEN, id, codigo, AuditoriaAccion.DELETE, estadoAnterior, Map.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResumenDTO> listarProductos(Integer origenId) {
        if (!repo.existsById(origenId)) {
            throw new NotFoundException("Origen no encontrado");
        }
        return productoRepository.findByOrigenId(origenId)
                .stream()
                .map(productoMapper::toResumenDTO)
                .toList();
    }


    private String leerStringRequerido(JsonNullable<String> campo, String field, int maxLength) {
        Object value = valor(campo);
        if (!(value instanceof String text)) {
            throw new BadRequestException("El campo '" + field + "' es requerido y debe ser texto");
        }
        if (text.length() > maxLength) {
            throw new BadRequestException("El campo '" + field + "' no puede exceder " + maxLength + " caracteres");
        }
        return text;
    }


    private boolean presente(JsonNullable<?> campo) {
        return campo == null || campo.isPresent();
    }

    private Object valor(JsonNullable<?> campo) {
        return campo == null ? null : campo.orElse(null);
    }

    private Map<String, String> capturarSnapshot(Origen entity) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("nombre", normalizar(entity.getNombre()));
        return snapshot;
    }

    private String normalizar(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}







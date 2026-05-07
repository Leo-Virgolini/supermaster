package ar.com.leo.super_master_backend.dominio.marca.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.marca.dto.MarcaCreateDTO;
import ar.com.leo.super_master_backend.dominio.marca.dto.MarcaDTO;
import ar.com.leo.super_master_backend.dominio.marca.dto.MarcaUpdateDTO;
import ar.com.leo.super_master_backend.dominio.marca.dto.MarcaPatchDTO;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.marca.mapper.MarcaMapper;
import ar.com.leo.super_master_backend.dominio.marca.repository.MarcaRepository;
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
public class MarcaServiceImpl implements MarcaService {

    private final MarcaRepository repo;
    private final MarcaMapper mapper;
    private final ProductoRepository productoRepository;
    private final ProductoMapper productoMapper;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional(readOnly = true)
    public Page<MarcaDTO> listar(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return repo.findByNombreContainingIgnoreCase(search, pageable)
                    .map(mapper::toDTO);
        }
        return repo.findAll(pageable)
                .map(mapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public MarcaDTO obtener(Integer id) {
        return repo.findById(id)
                .map(mapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Marca no encontrada"));
    }

    @Override
    @Transactional
    public MarcaDTO crear(MarcaCreateDTO dto) {
        Marca entity = mapper.toEntity(dto);
        repo.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.MARCA, entity.getId(), entity.getNombre(), AuditoriaAccion.CREATE, Map.of(), capturarSnapshot(entity));
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public MarcaDTO actualizar(Integer id, MarcaUpdateDTO dto) {
        Marca entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Marca no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        mapper.updateEntityFromDTO(dto, entity);

        repo.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.MARCA, id, entity.getNombre(), AuditoriaAccion.UPDATE, estadoAnterior, capturarSnapshot(entity));

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public MarcaDTO patch(Integer id, MarcaPatchDTO patchDto) {
        if (!presente(patchDto.getNombre()) && !presente(patchDto.getPadreId())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }


        Marca entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Marca no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        if (presente(patchDto.getNombre())) {
            entity.setNombre(leerStringRequerido(patchDto.getNombre(), "nombre", 45));
        }
        if (presente(patchDto.getPadreId())) {
            Integer padreId = leerIdOpcional(patchDto.getPadreId(), "padreId");
            entity.setPadre(padreId != null ? new Marca(padreId) : null);
        }

        repo.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.MARCA, id, entity.getNombre(), AuditoriaAccion.UPDATE, estadoAnterior, capturarSnapshot(entity));
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        Marca entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Marca no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);
        String codigo = entity.getNombre();
        repo.delete(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.MARCA, id, codigo, AuditoriaAccion.DELETE, estadoAnterior, Map.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResumenDTO> listarProductos(Integer marcaId) {
        if (!repo.existsById(marcaId)) {
            throw new NotFoundException("Marca no encontrada");
        }
        return productoRepository.findByMarcaId(marcaId)
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

    private Integer leerIdOpcional(JsonNullable<Integer> campo, String field) {
        Object value = valor(campo);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new BadRequestException("El campo '" + field + "' debe ser numérico");
        }
        int id = number.intValue();
        if (id <= 0) {
            throw new BadRequestException("El campo '" + field + "' debe ser positivo");
        }
        return id;
    }


    private boolean presente(JsonNullable<?> campo) {
        return campo == null || campo.isPresent();
    }

    private Object valor(JsonNullable<?> campo) {
        return campo == null ? null : campo.orElse(null);
    }

    private Map<String, String> capturarSnapshot(Marca entity) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("nombre", normalizar(entity.getNombre()));
        snapshot.put("padreId", entity.getPadre() != null ? normalizar(entity.getPadre().getId()) : null);
        snapshot.put("padre", entity.getPadre() != null ? normalizar(entity.getPadre().getNombre()) : null);
        return snapshot;
    }

    private String normalizar(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}







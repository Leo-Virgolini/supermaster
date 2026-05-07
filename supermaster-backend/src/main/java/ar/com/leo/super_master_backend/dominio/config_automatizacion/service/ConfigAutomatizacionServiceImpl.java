package ar.com.leo.super_master_backend.dominio.config_automatizacion.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.ConflictException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.dto.ConfigAutomatizacionCreateDTO;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.dto.ConfigAutomatizacionDTO;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.dto.ConfigAutomatizacionUpdateDTO;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.dto.ConfigAutomatizacionPatchDTO;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.entity.ConfigAutomatizacion;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.mapper.ConfigAutomatizacionMapper;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.repository.ConfigAutomatizacionRepository;
import lombok.RequiredArgsConstructor;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConfigAutomatizacionServiceImpl implements ConfigAutomatizacionService {

    private final ConfigAutomatizacionRepository repo;
    private final ConfigAutomatizacionMapper mapper;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional(readOnly = true)
    public Page<ConfigAutomatizacionDTO> listar(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return repo.findByClaveContainingIgnoreCaseOrDescripcionContainingIgnoreCase(
                    search, search, pageable).map(mapper::toDTO);
        }
        return repo.findAll(pageable).map(mapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public ConfigAutomatizacionDTO obtener(Integer id) {
        return repo.findById(id)
                .map(mapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Configuración no encontrada"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConfigAutomatizacionDTO> obtenerPorClave(String clave) {
        return repo.findByClaveIgnoreCase(clave).map(mapper::toDTO);
    }

    @Override
    @Transactional
    public ConfigAutomatizacionDTO crear(ConfigAutomatizacionCreateDTO dto) {
        if (repo.existsByClaveIgnoreCase(dto.clave())) {
            throw new ConflictException("Ya existe una configuración con la clave: " + dto.clave());
        }
        ConfigAutomatizacion entity = mapper.toEntity(dto);
        repo.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CONFIG_AUTOMATIZACION,
                entity.getId(),
                entity.getClave(),
                AuditoriaAccion.CREATE,
                Map.of(),
                capturarSnapshot(entity)
        );
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public ConfigAutomatizacionDTO actualizar(Integer id, ConfigAutomatizacionUpdateDTO dto) {
        ConfigAutomatizacion entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Configuración no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        // Verificar clave duplicada si se está cambiando
        if (dto.clave() != null && !dto.clave().equalsIgnoreCase(entity.getClave())) {
            if (repo.existsByClaveIgnoreCase(dto.clave())) {
                throw new ConflictException("Ya existe una configuración con la clave: " + dto.clave());
            }
        }

        mapper.updateEntityFromDTO(dto, entity);
        repo.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CONFIG_AUTOMATIZACION,
                id,
                entity.getClave(),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(entity)
        );
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public ConfigAutomatizacionDTO patch(Integer id, ConfigAutomatizacionPatchDTO patchDto) {
        if (!presente(patchDto.getClave())
                && !presente(patchDto.getValor())
                && !presente(patchDto.getDescripcion())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }

        ConfigAutomatizacion entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Configuración no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        if (presente(patchDto.getClave())) {
            String clave = leerStringRequerido(patchDto.getClave(), "clave", 50);
            if (!clave.equalsIgnoreCase(entity.getClave()) && repo.existsByClaveIgnoreCase(clave)) {
                throw new ConflictException("Ya existe una configuración con la clave: " + clave);
            }
            entity.setClave(clave);
        }
        if (presente(patchDto.getValor())) {
            entity.setValor(leerStringRequerido(patchDto.getValor(), "valor", 100));
        }
        if (presente(patchDto.getDescripcion())) {
            entity.setDescripcion(leerStringOpcional(patchDto.getDescripcion(), "descripcion", 255));
        }

        repo.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CONFIG_AUTOMATIZACION,
                id,
                entity.getClave(),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(entity)
        );
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        ConfigAutomatizacion entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Configuración no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);
        String codigo = entity.getClave();
        repo.delete(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CONFIG_AUTOMATIZACION,
                id,
                codigo,
                AuditoriaAccion.DELETE,
                estadoAnterior,
                Map.of()
        );
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

    private String leerStringOpcional(JsonNullable<String> campo, String field, int maxLength) {
        Object value = valor(campo);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new BadRequestException("El campo '" + field + "' debe ser texto");
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

    private Map<String, String> capturarSnapshot(ConfigAutomatizacion entity) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("clave", normalizar(entity.getClave()));
        snapshot.put("valor", normalizar(entity.getValor()));
        snapshot.put("descripcion", normalizar(entity.getDescripcion()));
        return snapshot;
    }

    private String normalizar(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}







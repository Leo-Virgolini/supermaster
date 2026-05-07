package ar.com.leo.super_master_backend.dominio.canal.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoReglaCreateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoReglaDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoReglaUpdateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoReglaPatchDTO;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConceptoRegla;
import ar.com.leo.super_master_backend.dominio.canal.entity.TipoRegla;
import ar.com.leo.super_master_backend.dominio.canal.mapper.CanalConceptoReglaMapper;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalConceptoReglaRepository;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.repository.ClasifGastroRepository;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.clasif_gral.repository.ClasifGralRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.ConceptoCalculo;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.repository.ConceptoCalculoRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.marca.repository.MarcaRepository;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import ar.com.leo.super_master_backend.dominio.tipo.repository.TipoRepository;
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
public class CanalConceptoReglaServiceImpl implements CanalConceptoReglaService {

    private final CanalConceptoReglaRepository repository;
    private final CanalRepository canalRepository;
    private final ConceptoCalculoRepository conceptoCalculoRepository;
    private final TipoRepository tipoRepository;
    private final ClasifGastroRepository clasifGastroRepository;
    private final ClasifGralRepository clasifGralRepository;
    private final MarcaRepository marcaRepository;
    private final CanalConceptoReglaMapper mapper;
    private final AuditoriaService auditoriaService;
    private final ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService recalculoPendienteService;
    private final CanalScopeService canalScopeService;

    @Override
    @Transactional(readOnly = true)
    public Page<CanalConceptoReglaDTO> listar(Pageable pageable) {
        return repository.findAll(pageable)
                .map(mapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public CanalConceptoReglaDTO obtener(Long id) {
        return repository.findById(id)
                .map(mapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Regla no encontrada"));
    }

    @Override
    @Transactional
    public CanalConceptoReglaDTO crear(CanalConceptoReglaCreateDTO dto) {
        // Validar que el canal existe
        if (!canalRepository.existsById(dto.canalId())) {
            throw new NotFoundException("Canal no encontrado");
        }

        // Validar que el concepto existe
        if (!conceptoCalculoRepository.existsById(dto.conceptoId())) {
            throw new NotFoundException("Concepto no encontrado");
        }

        // Validar relaciones opcionales si están presentes
        if (dto.tipoId() != null && !tipoRepository.existsById(dto.tipoId())) {
            throw new NotFoundException("Tipo no encontrado");
        }
        if (dto.clasifGastroId() != null && !clasifGastroRepository.existsById(dto.clasifGastroId())) {
            throw new NotFoundException("Clasificación gastro no encontrada");
        }
        if (dto.clasifGralId() != null && !clasifGralRepository.existsById(dto.clasifGralId())) {
            throw new NotFoundException("Clasificación general no encontrada");
        }
        if (dto.marcaId() != null && !marcaRepository.existsById(dto.marcaId())) {
            throw new NotFoundException("Marca no encontrada");
        }

        CanalConceptoRegla entity = mapper.toEntity(dto);
        repository.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CANAL_CONCEPTO_REGLA,
                Math.toIntExact(entity.getId()),
                construirCodigoEntidad(entity),
                AuditoriaAccion.CREATE,
                Map.of(),
                capturarSnapshot(entity)
        );
        programarRecalculoPostCommit(dto.canalId());
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public CanalConceptoReglaDTO actualizar(Long id, CanalConceptoReglaUpdateDTO dto) {
        CanalConceptoRegla entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Regla no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        // Validar relaciones si están presentes en el DTO
        if (dto.canalId() != null && !canalRepository.existsById(dto.canalId())) {
            throw new NotFoundException("Canal no encontrado");
        }
        if (dto.conceptoId() != null && !conceptoCalculoRepository.existsById(dto.conceptoId())) {
            throw new NotFoundException("Concepto no encontrado");
        }
        if (dto.tipoId() != null && !tipoRepository.existsById(dto.tipoId())) {
            throw new NotFoundException("Tipo no encontrado");
        }
        if (dto.clasifGastroId() != null && !clasifGastroRepository.existsById(dto.clasifGastroId())) {
            throw new NotFoundException("Clasificación gastro no encontrada");
        }
        if (dto.clasifGralId() != null && !clasifGralRepository.existsById(dto.clasifGralId())) {
            throw new NotFoundException("Clasificación general no encontrada");
        }
        if (dto.marcaId() != null && !marcaRepository.existsById(dto.marcaId())) {
            throw new NotFoundException("Marca no encontrada");
        }

        mapper.updateEntityFromDTO(dto, entity);
        repository.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CANAL_CONCEPTO_REGLA,
                Math.toIntExact(id),
                construirCodigoEntidad(entity),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(entity)
        );
        programarRecalculoPostCommit(entity.getCanal().getId());
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public CanalConceptoReglaDTO patch(Long id, CanalConceptoReglaPatchDTO patchDto) {
        if (!presente(patchDto.getCanalId())
                && !presente(patchDto.getConceptoId())
                && !presente(patchDto.getTipoRegla())
                && !presente(patchDto.getTipoId())
                && !presente(patchDto.getClasifGastroId())
                && !presente(patchDto.getClasifGralId())
                && !presente(patchDto.getMarcaId())
                && !presente(patchDto.getEsMaquina())
                && !presente(patchDto.getTag())
                && !presente(patchDto.getTieneEnvio())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }

        CanalConceptoRegla entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Regla no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        if (presente(patchDto.getCanalId())) {
            Integer canalId = leerIdRequerido(patchDto.getCanalId(), "canalId");
            if (!canalRepository.existsById(canalId)) {
                throw new NotFoundException("Canal no encontrado");
            }
            entity.setCanal(new Canal(canalId));
        }
        if (presente(patchDto.getConceptoId())) {
            Integer conceptoId = leerIdRequerido(patchDto.getConceptoId(), "conceptoId");
            if (!conceptoCalculoRepository.existsById(conceptoId)) {
                throw new NotFoundException("Concepto no encontrado");
            }
            entity.setConcepto(new ConceptoCalculo(conceptoId));
        }
        if (presente(patchDto.getTipoRegla())) {
            entity.setTipoRegla(leerEnumRequerido(patchDto.getTipoRegla(), "tipoRegla", TipoRegla.class));
        }
        if (presente(patchDto.getTipoId())) {
            Integer tipoId = leerIdOpcional(patchDto.getTipoId(), "tipoId");
            if (tipoId != null && !tipoRepository.existsById(tipoId)) {
                throw new NotFoundException("Tipo no encontrado");
            }
            entity.setTipo(tipoId != null ? new Tipo(tipoId) : null);
        }
        if (presente(patchDto.getClasifGastroId())) {
            Integer clasifGastroId = leerIdOpcional(patchDto.getClasifGastroId(), "clasifGastroId");
            if (clasifGastroId != null && !clasifGastroRepository.existsById(clasifGastroId)) {
                throw new NotFoundException("Clasificación gastro no encontrada");
            }
            entity.setClasifGastro(clasifGastroId != null ? new ClasifGastro(clasifGastroId) : null);
        }
        if (presente(patchDto.getClasifGralId())) {
            Integer clasifGralId = leerIdOpcional(patchDto.getClasifGralId(), "clasifGralId");
            if (clasifGralId != null && !clasifGralRepository.existsById(clasifGralId)) {
                throw new NotFoundException("Clasificación general no encontrada");
            }
            entity.setClasifGral(clasifGralId != null ? new ClasifGral(clasifGralId) : null);
        }
        if (presente(patchDto.getMarcaId())) {
            Integer marcaId = leerIdOpcional(patchDto.getMarcaId(), "marcaId");
            if (marcaId != null && !marcaRepository.existsById(marcaId)) {
                throw new NotFoundException("Marca no encontrada");
            }
            entity.setMarca(marcaId != null ? new Marca(marcaId) : null);
        }
        if (presente(patchDto.getEsMaquina())) {
            entity.setEsMaquina(leerBooleanOpcional(patchDto.getEsMaquina(), "esMaquina"));
        }
        if (presente(patchDto.getTag())) {
            entity.setTag(patchDto.getTag().orElse(null));
        }
        if (presente(patchDto.getTieneEnvio())) {
            entity.setTieneEnvio(leerBooleanOpcional(patchDto.getTieneEnvio(), "tieneEnvio"));
        }

        repository.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CANAL_CONCEPTO_REGLA,
                Math.toIntExact(id),
                construirCodigoEntidad(entity),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(entity)
        );
        programarRecalculoPostCommit(entity.getCanal().getId());
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        CanalConceptoRegla entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Regla no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);
        String codigo = construirCodigoEntidad(entity);
        Integer canalId = entity.getCanal().getId();
        repository.delete(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CANAL_CONCEPTO_REGLA,
                Math.toIntExact(id),
                codigo,
                AuditoriaAccion.DELETE,
                estadoAnterior,
                Map.of()
        );
        programarRecalculoPostCommit(canalId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CanalConceptoReglaDTO> listarPorCanal(Integer canalId) {
        return repository.findByCanalId(canalId)
                .stream()
                .map(mapper::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CanalConceptoReglaDTO> listarPorConcepto(Integer conceptoId) {
        return repository.findByConceptoId(conceptoId)
                .stream()
                .map(mapper::toDTO)
                .toList();
    }


    private Integer leerIdRequerido(JsonNullable<Integer> campo, String field) {
        Integer value = leerIdOpcional(campo, field);
        if (value == null) {
            throw new BadRequestException("El campo '" + field + "' es requerido");
        }
        return value;
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

    private Boolean leerBooleanOpcional(JsonNullable<Boolean> campo, String field) {
        Object value = valor(campo);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Boolean bool)) {
            throw new BadRequestException("El campo '" + field + "' debe ser booleano");
        }
        return bool;
    }

    private <E extends Enum<E>> E leerEnumRequerido(JsonNullable<String> campo, String field, Class<E> enumClass) {
        Object value = valor(campo);
        if (!(value instanceof String text)) {
            throw new BadRequestException("El campo '" + field + "' es requerido y debe ser texto");
        }
        try {
            return Enum.valueOf(enumClass, text);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Valor inválido para '" + field + "': " + text);
        }
    }

    private boolean presente(JsonNullable<?> campo) {
        return campo == null || campo.isPresent();
    }

    private Object valor(JsonNullable<?> campo) {
        return campo == null ? null : campo.orElse(null);
    }

    private Map<String, String> capturarSnapshot(CanalConceptoRegla entity) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("canal", describirCanal(entity.getCanal()));
        snapshot.put("concepto", describirConcepto(entity.getConcepto()));
        snapshot.put("tipoRegla", normalizar(entity.getTipoRegla()));
        snapshot.put("tipo", describirRelacion(entity.getTipo()));
        snapshot.put("clasifGastro", describirRelacion(entity.getClasifGastro()));
        snapshot.put("clasifGral", describirRelacion(entity.getClasifGral()));
        snapshot.put("marca", describirRelacion(entity.getMarca()));
        snapshot.put("esMaquina", normalizar(entity.getEsMaquina()));
        snapshot.put("tag", entity.getTag() != null ? entity.getTag().name() : null);
        snapshot.put("tieneEnvio", normalizar(entity.getTieneEnvio()));
        return snapshot;
    }

    private String construirCodigoEntidad(CanalConceptoRegla entity) {
        return (entity.getCanal() != null ? entity.getCanal().getNombre() : "Sin canal")
                + " / "
                + (entity.getConcepto() != null ? entity.getConcepto().getNombre() : "Sin concepto");
    }

    private String describirCanal(Canal canal) {
        return canal == null ? null : canal.getId() + " - " + canal.getNombre();
    }

    private String describirConcepto(ConceptoCalculo concepto) {
        return concepto == null ? null : concepto.getId() + " - " + concepto.getNombre();
    }

    private String describirRelacion(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Tipo tipo) {
            return tipo.getId() + " - " + tipo.getNombre();
        }
        if (value instanceof ClasifGastro clasifGastro) {
            return clasifGastro.getId() + " - " + clasifGastro.getNombre();
        }
        if (value instanceof ClasifGral clasifGral) {
            return clasifGral.getId() + " - " + clasifGral.getNombre();
        }
        if (value instanceof Marca marca) {
            return marca.getId() + " - " + marca.getNombre();
        }
        return normalizar(value);
    }

    private String normalizar(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void programarRecalculoPostCommit(Integer canalId) {
        // Incluye subcanales: heredan el efecto de las reglas del padre vía PVP base.
        recalculoPendienteService.marcarCanales("Cambio en regla de excepción",
                canalScopeService.idsConSubcanales(canalId));
    }
}


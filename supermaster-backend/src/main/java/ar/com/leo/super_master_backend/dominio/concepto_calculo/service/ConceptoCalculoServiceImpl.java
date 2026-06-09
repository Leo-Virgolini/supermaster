package ar.com.leo.super_master_backend.dominio.concepto_calculo.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalConceptoRepository;
import ar.com.leo.super_master_backend.dominio.canal.service.CanalScopeService;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.ConflictException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.dto.ConceptoCalculoCreateDTO;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.dto.ConceptoCalculoDTO;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.dto.ConceptoCalculoPatchDTO;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.dto.ConceptoCalculoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.AplicaSobre;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.ConceptoCalculo;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.mapper.ConceptoCalculoMapper;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.repository.ConceptoCalculoRepository;
import lombok.RequiredArgsConstructor;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

import static ar.com.leo.super_master_backend.dominio.common.util.JsonNullableFields.*;

@Service
@RequiredArgsConstructor
public class ConceptoCalculoServiceImpl implements ConceptoCalculoService {

    private final ConceptoCalculoRepository conceptoRepository;
    private final RecalculoPendienteService recalculoPendienteService;
    private final ConceptoCalculoMapper mapper;
    private final AuditoriaService auditoriaService;
    private final CanalConceptoRepository canalConceptoRepository;
    private final CanalScopeService canalScopeService;

    @Override
    @Transactional(readOnly = true)
    public Page<ConceptoCalculoDTO> listar(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return conceptoRepository.findByNombreContainingIgnoreCaseOrDescripcionContainingIgnoreCase(search, search, pageable)
                    .map(mapper::toDTO);
        }
        return conceptoRepository.findAll(pageable)
                .map(mapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public ConceptoCalculoDTO obtener(Integer id) {
        return conceptoRepository.findById(id)
                .map(mapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Concepto no encontrado"));
    }

    @Override
    @Transactional
    public ConceptoCalculoDTO crear(ConceptoCalculoCreateDTO dto) {
        ConceptoCalculo entity = mapper.toEntity(dto);
        normalizarPorcentajeSiEsFlag(entity);
        conceptoRepository.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.CONCEPTO_CALCULO, entity.getId(), entity.getNombre(), AuditoriaAccion.CREATE, Map.of(), capturarSnapshot(entity));
        return mapper.toDTO(entity);
    }

    /**
     * Si el aplicaSobre es un flag (FLAG_*), forzamos porcentaje=null porque
     * los flags ignoran el porcentaje. Cualquier valor numérico que se haya
     * pasado en el DTO se descarta — evita inconsistencias del tipo "flag con
     * 5%" que confunden al usuario y al motor de cálculo.
     */
    private void normalizarPorcentajeSiEsFlag(ConceptoCalculo entity) {
        if (entity.getAplicaSobre() != null && entity.getAplicaSobre().esFlag()) {
            entity.setPorcentaje(null);
        }
    }

    @Override
    @Transactional
    public ConceptoCalculoDTO actualizar(Integer id, ConceptoCalculoUpdateDTO dto) {

        ConceptoCalculo entity = conceptoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Concepto no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        BigDecimal porcentajeAnterior = entity.getPorcentaje();
        var aplicaSobreAnterior = entity.getAplicaSobre();

        mapper.updateEntityFromDTO(dto, entity);
        normalizarPorcentajeSiEsFlag(entity);
        conceptoRepository.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.CONCEPTO_CALCULO, id, entity.getNombre(), AuditoriaAccion.UPDATE, estadoAnterior, capturarSnapshot(entity));

        boolean cambioPorcentaje = dto.porcentaje() != null && (porcentajeAnterior == null || dto.porcentaje().compareTo(porcentajeAnterior) != 0);
        boolean cambioAplicaSobre = dto.aplicaSobre() != null && !dto.aplicaSobre().equals(aplicaSobreAnterior);
        if (cambioPorcentaje || cambioAplicaSobre) {
            // Solo los canales que tienen este concepto asignado — no recalcular todo el catálogo.
            // Cada canal afectado se recalcula completo (todos sus productos) en el Apply.
            marcarCanalesDelConcepto(id, "Cambio en concepto de cálculo");
        }

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public ConceptoCalculoDTO patch(Integer id, ConceptoCalculoPatchDTO patchDto) {
        if (!presente(patchDto.getNombre())
                && !presente(patchDto.getPorcentaje())
                && !presente(patchDto.getAplicaSobre())
                && !presente(patchDto.getDescripcion())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }

        ConceptoCalculo entity = conceptoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Concepto no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        BigDecimal porcentajeAnterior = entity.getPorcentaje();
        var aplicaSobreAnterior = entity.getAplicaSobre();

        if (presente(patchDto.getNombre())) {
            entity.setNombre(leerStringRequerido(patchDto.getNombre(), "nombre", 45));
        }
        if (presente(patchDto.getPorcentaje())) {
            entity.setPorcentaje(leerPorcentajeOpcional(patchDto.getPorcentaje(), "porcentaje"));
        }
        if (presente(patchDto.getAplicaSobre())) {
            entity.setAplicaSobre(leerEnumRequerido(patchDto.getAplicaSobre(), "aplicaSobre", AplicaSobre.class));
        }
        if (presente(patchDto.getDescripcion())) {
            entity.setDescripcion(leerStringOpcional(patchDto.getDescripcion(), "descripcion", 255));
        }

        normalizarPorcentajeSiEsFlag(entity);
        conceptoRepository.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.CONCEPTO_CALCULO, id, entity.getNombre(), AuditoriaAccion.UPDATE, estadoAnterior, capturarSnapshot(entity));

        boolean cambioPorcentaje = presente(patchDto.getPorcentaje()) && !Objects.equals(porcentajeAnterior, entity.getPorcentaje());
        boolean cambioAplicaSobre = presente(patchDto.getAplicaSobre()) && !Objects.equals(aplicaSobreAnterior, entity.getAplicaSobre());
        if (cambioPorcentaje || cambioAplicaSobre) {
            marcarCanalesDelConcepto(id, "Cambio en concepto de cálculo");
        }

        return mapper.toDTO(entity);
    }

    private void marcarCanalesDelConcepto(Integer conceptoId, String motivo) {
        // Canales que usan el concepto + sus subcanales (heredan vía PVP base).
        LinkedHashSet<Integer> canalIds = new LinkedHashSet<>();
        canalConceptoRepository.findByConceptoId(conceptoId).stream()
                .map(cc -> cc.getCanal().getId())
                .distinct()
                .forEach(id -> canalIds.addAll(canalScopeService.idsConSubcanales(id)));
        recalculoPendienteService.marcarCanales(motivo, canalIds);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        ConceptoCalculo entity = conceptoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Concepto no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);
        String codigo = entity.getNombre();

        if (canalConceptoRepository.existsByConceptoId(id)) {
            throw new ConflictException("No se puede eliminar porque está asignado a canales. Quite la asignación primero.");
        }

        conceptoRepository.delete(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.CONCEPTO_CALCULO, id, codigo, AuditoriaAccion.DELETE, estadoAnterior, Map.of());
    }


    /** Específico: porcentaje bidireccional (-100, 100), no [0, 100] como el común. */
    private BigDecimal leerPorcentajeOpcional(JsonNullable<BigDecimal> campo, String field) {
        BigDecimal decimal = leerDecimalOpcional(campo, field);
        if (decimal != null && (decimal.compareTo(BigDecimal.valueOf(-100)) < 0 || decimal.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new BadRequestException("El campo '" + field + "' debe estar entre -100 y 100");
        }
        return decimal;
    }

    /** Específico: deserializa enum desde String (no desde JsonNullable&lt;E&gt; tipado). */
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

    private Map<String, String> capturarSnapshot(ConceptoCalculo concepto) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("nombre", normalizar(concepto.getNombre()));
        snapshot.put("porcentaje", concepto.getPorcentaje() == null ? null : concepto.getPorcentaje().stripTrailingZeros().toPlainString());
        snapshot.put("aplicaSobre", concepto.getAplicaSobre() == null ? null : concepto.getAplicaSobre().name());
        snapshot.put("descripcion", normalizar(concepto.getDescripcion()));
        return snapshot;
    }

}







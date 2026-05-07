package ar.com.leo.super_master_backend.dominio.canal.service;

import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoCuotaCreateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoCuotaDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoCuotaUpdateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoCuotaPatchDTO;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConceptoCuota;
import ar.com.leo.super_master_backend.dominio.canal.mapper.CanalConceptoCuotaMapper;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalConceptoCuotaRepository;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.ConflictException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoCanalPrecioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class CanalConceptoCuotaServiceImpl implements CanalConceptoCuotaService {

    private final CanalConceptoCuotaRepository repository;
    private final CanalRepository canalRepository;
    private final CanalConceptoCuotaMapper mapper;
    private final ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService recalculoPendienteService;
    private final CanalScopeService canalScopeService;
    private final ProductoCanalPrecioRepository productoCanalPrecioRepository;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional(readOnly = true)
    public Page<CanalConceptoCuotaDTO> listar(Pageable pageable) {
        return repository.findAll(pageable)
                .map(mapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public CanalConceptoCuotaDTO obtener(Long id) {
        return repository.findById(id)
                .map(mapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Cuota no encontrada"));
    }

    @Override
    @Transactional
    public CanalConceptoCuotaDTO crear(CanalConceptoCuotaCreateDTO dto) {
        // Validar que el canal existe y cargarlo completo para auditoría
        var canal = canalRepository.findById(dto.canalId())
                .orElseThrow(() -> new NotFoundException("Canal no encontrado"));

        // Verificar si ya existe una cuota con la misma combinación (canal, cuotas)
        if (!repository.findByCanalIdAndCuotas(dto.canalId(), dto.cuotas()).isEmpty()) {
            throw new ConflictException("Ya existe una cuota con estos parámetros");
        }

        CanalConceptoCuota entity = mapper.toEntity(dto);
        entity.setCanal(canal);
        repository.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CANAL_CONCEPTO_CUOTA,
                entity.getId().intValue(),
                codigoCuota(entity),
                AuditoriaAccion.CREATE,
                Map.of(),
                capturarSnapshot(entity)
        );

        // Recalcular precios del canal
        programarRecalculoPostCommit(dto.canalId());

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public CanalConceptoCuotaDTO actualizar(Long id, CanalConceptoCuotaUpdateDTO dto) {
        CanalConceptoCuota entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Cuota no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        // Si se actualizan las cuotas, verificar que no exista otra con la misma combinación
        if (dto.cuotas() != null) {
            Integer nuevasCuotas = dto.cuotas();
            Integer canalId = entity.getCanal().getId();

            // Verificar si existe otra cuota con la misma combinación (excluyendo la actual)
            List<CanalConceptoCuota> existentes = repository.findByCanalIdAndCuotas(canalId, nuevasCuotas);
            for (CanalConceptoCuota existente : existentes) {
                if (!existente.getId().equals(id)) {
                    throw new ConflictException("Ya existe otra cuota con estos parámetros");
                }
            }
        }

        BigDecimal porcentajeAnterior = entity.getPorcentaje();
        Integer canalId = entity.getCanal().getId();

        mapper.updateEntityFromDTO(dto, entity);
        repository.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CANAL_CONCEPTO_CUOTA,
                entity.getId().intValue(),
                codigoCuota(entity),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(entity)
        );

        // Recalcular si cambió el porcentaje
        if (dto.porcentaje() != null && (porcentajeAnterior == null || porcentajeAnterior.compareTo(dto.porcentaje()) != 0)) {
            programarRecalculoPostCommit(canalId);
        }

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public CanalConceptoCuotaDTO patch(Long id, CanalConceptoCuotaPatchDTO patchDto) {
        if (!presente(patchDto.getCuotas())
                && !presente(patchDto.getPorcentaje())
                && !presente(patchDto.getDescripcion())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }

        CanalConceptoCuota entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Cuota no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        Integer canalId = entity.getCanal().getId();
        BigDecimal porcentajeAnterior = entity.getPorcentaje();

        if (presente(patchDto.getCuotas())) {
            Integer nuevasCuotas = leerIntegerNoNegativoRequerido(patchDto.getCuotas(), "cuotas");
            List<CanalConceptoCuota> existentes = repository.findByCanalIdAndCuotas(canalId, nuevasCuotas);
            for (CanalConceptoCuota existente : existentes) {
                if (!existente.getId().equals(id)) {
                    throw new ConflictException("Ya existe otra cuota con estos parámetros");
                }
            }
            entity.setCuotas(nuevasCuotas);
        }
        if (presente(patchDto.getPorcentaje())) {
            entity.setPorcentaje(leerPorcentajeRequerido(patchDto.getPorcentaje(), "porcentaje"));
        }
        if (presente(patchDto.getDescripcion())) {
            entity.setDescripcion(leerStringOpcional(patchDto.getDescripcion(), "descripcion", 255));
        }

        repository.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CANAL_CONCEPTO_CUOTA,
                entity.getId().intValue(),
                codigoCuota(entity),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(entity)
        );

        boolean cambioPorcentaje = presente(patchDto.getPorcentaje()) && !Objects.equals(porcentajeAnterior, entity.getPorcentaje());
        if (cambioPorcentaje) {
            programarRecalculoPostCommit(canalId);
        }

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        CanalConceptoCuota entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Cuota no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);
        String codigo = codigoCuota(entity);

        Integer canalId = entity.getCanal().getId();
        Integer cuotas = entity.getCuotas();

        repository.deleteById(id);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CANAL_CONCEPTO_CUOTA,
                entity.getId().intValue(),
                codigo,
                AuditoriaAccion.DELETE,
                estadoAnterior,
                Map.of()
        );

        // Eliminar los precios calculados para esa cuota (no necesita recalcular las demás)
        int eliminados = productoCanalPrecioRepository.deleteByCanalIdAndCuotas(canalId, cuotas);
        log.info("Eliminados {} precios de canal {} para {} cuotas", eliminados, canalId, cuotas);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CanalConceptoCuotaDTO> listarPorCanal(Integer canalId) {
        return repository.findByCanalId(canalId)
                .stream()
                .map(mapper::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CanalConceptoCuotaDTO> listarPorCanalYCuotas(Integer canalId, Integer cuotas) {
        return repository.findByCanalIdAndCuotas(canalId, cuotas)
                .stream()
                .map(mapper::toDTO)
                .toList();
    }


    private Integer leerIntegerNoNegativoRequerido(JsonNullable<Integer> campo, String field) {
        Object value = valor(campo);
        if (!(value instanceof Number number)) {
            throw new BadRequestException("El campo '" + field + "' es requerido y debe ser numérico");
        }
        int result = number.intValue();
        if (result < 0) {
            throw new BadRequestException("El campo '" + field + "' debe ser mayor o igual a 0");
        }
        return result;
    }

    private BigDecimal leerPorcentajeRequerido(JsonNullable<BigDecimal> campo, String field) {
        Object value = valor(campo);
        if (!(value instanceof Number number)) {
            throw new BadRequestException("El campo '" + field + "' es requerido y debe ser numérico");
        }
        BigDecimal decimal = new BigDecimal(number.toString());
        if (decimal.compareTo(BigDecimal.valueOf(-100)) < 0 || decimal.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BadRequestException("El campo '" + field + "' debe estar entre -100 y 100");
        }
        return decimal;
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

    private Map<String, String> capturarSnapshot(CanalConceptoCuota cuota) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("canal", cuota.getCanal() == null ? null : cuota.getCanal().getId() + " - " + cuota.getCanal().getNombre());
        snapshot.put("cuotas", cuota.getCuotas() == null ? null : String.valueOf(cuota.getCuotas()));
        snapshot.put("porcentaje", cuota.getPorcentaje() == null ? null : cuota.getPorcentaje().stripTrailingZeros().toPlainString());
        snapshot.put("descripcion", cuota.getDescripcion());
        return snapshot;
    }

    private String codigoCuota(CanalConceptoCuota cuota) {
        String canal = cuota.getCanal() == null ? "Canal" : cuota.getCanal().getNombre();
        String cuotas = cuota.getCuotas() == null ? "?" : String.valueOf(cuota.getCuotas());
        return canal + " - " + cuotas + " cuotas";
    }

    /**
     * Marca el canal como pendiente de recálculo. El usuario lo aplica desde el
     * banner global con un solo click, evitando recálculos repetidos.
     */
    private void programarRecalculoPostCommit(Integer canalId) {
        // Incluye subcanales: sus PVPs dependen del canal padre, así que también deben recalcularse.
        recalculoPendienteService.marcarCanales("Cambio en cuotas del canal",
                canalScopeService.idsConSubcanales(canalId));
    }
}







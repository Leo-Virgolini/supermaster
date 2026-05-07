package ar.com.leo.super_master_backend.dominio.precio_inflado.service;

import ar.com.leo.super_master_backend.dominio.common.exception.ConflictException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.precio_inflado.dto.PrecioInfladoCreateDTO;
import ar.com.leo.super_master_backend.dominio.precio_inflado.dto.PrecioInfladoDTO;
import ar.com.leo.super_master_backend.dominio.precio_inflado.dto.PrecioInfladoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.precio_inflado.dto.PrecioInfladoPatchDTO;
import ar.com.leo.super_master_backend.dominio.precio_inflado.entity.PrecioInflado;
import ar.com.leo.super_master_backend.dominio.precio_inflado.entity.TipoPrecioInflado;
import ar.com.leo.super_master_backend.dominio.precio_inflado.mapper.PrecioInfladoMapper;
import ar.com.leo.super_master_backend.dominio.precio_inflado.repository.PrecioInfladoRepository;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCanalPrecioInflado;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoCanalPrecioInfladoRepository;
import lombok.RequiredArgsConstructor;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PrecioInfladoServiceImpl implements PrecioInfladoService {

    private final PrecioInfladoRepository repository;
    private final PrecioInfladoMapper mapper;
    private final ProductoCanalPrecioInfladoRepository asignacionRepository;
    private final ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService recalculoPendienteService;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional(readOnly = true)
    public Page<PrecioInfladoDTO> listar(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return repository.findByCodigoContainingIgnoreCase(search, pageable)
                    .map(mapper::toDTO);
        }
        return repository.findAll(pageable)
                .map(mapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public PrecioInfladoDTO obtenerPorId(Integer id) {
        PrecioInflado precioInflado = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Precio inflado no encontrado con ID: " + id));
        return mapper.toDTO(precioInflado);
    }

    @Override
    @Transactional(readOnly = true)
    public PrecioInfladoDTO obtenerPorCodigo(String codigo) {
        PrecioInflado precioInflado = repository.findByCodigo(codigo)
                .orElseThrow(() -> new NotFoundException("Precio inflado no encontrado con código: " + codigo));
        return mapper.toDTO(precioInflado);
    }

    @Override
    @Transactional
    public PrecioInfladoDTO crear(PrecioInfladoCreateDTO dto) {
        if (repository.existsByCodigo(dto.codigo())) {
            throw new ConflictException("Ya existe un precio inflado con el código: " + dto.codigo());
        }

        PrecioInflado precioInflado = mapper.toEntity(dto);
        precioInflado = repository.save(precioInflado);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.PRECIO_INFLADO,
                precioInflado.getId(),
                precioInflado.getCodigo(),
                AuditoriaAccion.CREATE,
                Map.of(),
                capturarSnapshot(precioInflado)
        );
        return mapper.toDTO(precioInflado);
    }

    @Override
    @Transactional
    public PrecioInfladoDTO actualizar(Integer id, PrecioInfladoUpdateDTO dto) {
        PrecioInflado precioInflado = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Precio inflado no encontrado con ID: " + id));
        Map<String, String> estadoAnterior = capturarSnapshot(precioInflado);

        if (dto.codigo() != null && !dto.codigo().equals(precioInflado.getCodigo())) {
            if (repository.existsByCodigo(dto.codigo())) {
                throw new ConflictException("Ya existe un precio inflado con el código: " + dto.codigo());
            }
        }

        boolean cambioValor = (dto.tipo() != null && dto.tipo() != precioInflado.getTipo())
                || (dto.valor() != null && dto.valor().compareTo(precioInflado.getValor()) != 0);

        mapper.updateEntityFromDTO(dto, precioInflado);
        precioInflado = repository.save(precioInflado);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.PRECIO_INFLADO,
                id,
                precioInflado.getCodigo(),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(precioInflado)
        );

        if (cambioValor) {
            recalcularAsignaciones(id);
        }

        return mapper.toDTO(precioInflado);
    }

    @Override
    @Transactional
    public PrecioInfladoDTO patch(Integer id, PrecioInfladoPatchDTO patchDto) {
        if (!presente(patchDto.getCodigo())
                && !presente(patchDto.getTipo())
                && !presente(patchDto.getValor())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }

        PrecioInflado precioInflado = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Precio inflado no encontrado con ID: " + id));
        Map<String, String> estadoAnterior = capturarSnapshot(precioInflado);

        if (presente(patchDto.getCodigo())) {
            String codigo = leerStringRequerido(patchDto.getCodigo(), "codigo", 20);
            if (!codigo.equals(precioInflado.getCodigo()) && repository.existsByCodigo(codigo)) {
                throw new ConflictException("Ya existe un precio inflado con el código: " + codigo);
            }
            precioInflado.setCodigo(codigo);
        }

        boolean cambioValor = false;
        if (presente(patchDto.getTipo())) {
            var nuevoTipo = leerEnumRequerido(patchDto.getTipo(), "tipo", TipoPrecioInflado.class);
            cambioValor = cambioValor || nuevoTipo != precioInflado.getTipo();
            precioInflado.setTipo(nuevoTipo);
        }
        if (presente(patchDto.getValor())) {
            BigDecimal nuevoValor = leerDecimalNoNegativoRequerido(patchDto.getValor(), "valor");
            cambioValor = cambioValor || nuevoValor.compareTo(precioInflado.getValor()) != 0;
            precioInflado.setValor(nuevoValor);
        }

        precioInflado = repository.save(precioInflado);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.PRECIO_INFLADO,
                id,
                precioInflado.getCodigo(),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(precioInflado)
        );

        if (cambioValor) {
            recalcularAsignaciones(id);
        }

        return mapper.toDTO(precioInflado);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        PrecioInflado precioInflado = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Precio inflado no encontrado con ID: " + id));
        Map<String, String> estadoAnterior = capturarSnapshot(precioInflado);
        String codigo = precioInflado.getCodigo();

        // Verificar si hay productos con esta regla asignada
        long asignaciones = asignacionRepository.countByPrecioInfladoId(id);
        if (asignaciones > 0) {
            throw new ConflictException("No se puede eliminar: hay " + asignaciones
                    + " producto(s) con esta regla de precio inflado asignada. Quite las asignaciones primero.");
        }

        repository.delete(precioInflado);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.PRECIO_INFLADO,
                id,
                codigo,
                AuditoriaAccion.DELETE,
                estadoAnterior,
                Map.of()
        );
    }

    private void recalcularAsignaciones(Integer precioInfladoId) {
        // Cada asignación afecta a UN producto específico → marcamos scope por producto.
        // Batcheamos con marcarProductos para emitir un solo broadcast SSE y registrar
        // un único motivo (en lugar de N broadcasts y N entradas con el mismo texto).
        List<Integer> productoIds = asignacionRepository.findByPrecioInfladoId(precioInfladoId).stream()
                .map(a -> a.getProducto().getId())
                .toList();
        recalculoPendienteService.marcarProductos("Cambio en precio inflado", productoIds);
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

    private BigDecimal leerDecimalNoNegativoRequerido(JsonNullable<BigDecimal> campo, String field) {
        Object value = valor(campo);
        if (!(value instanceof Number number)) {
            throw new BadRequestException("El campo '" + field + "' es requerido y debe ser numérico");
        }
        BigDecimal decimal = new BigDecimal(number.toString());
        if (decimal.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("El campo '" + field + "' debe ser mayor o igual a 0");
        }
        return decimal;
    }

    private <E extends Enum<E>> E leerEnumRequerido(JsonNullable<E> campo, String field, Class<E> enumClass) {
        Object value = valor(campo);
        if (value == null) {
            throw new BadRequestException("El campo '" + field + "' es requerido");
        }
        if (!enumClass.isInstance(value)) {
            throw new BadRequestException("Valor inválido para '" + field + "'");
        }
        return enumClass.cast(value);
    }

    private boolean presente(JsonNullable<?> campo) {
        return campo == null || campo.isPresent();
    }

    private Object valor(JsonNullable<?> campo) {
        return campo == null ? null : campo.orElse(null);
    }

    private Map<String, String> capturarSnapshot(PrecioInflado entity) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("codigo", normalizar(entity.getCodigo()));
        snapshot.put("tipo", normalizar(entity.getTipo()));
        snapshot.put("valor", normalizar(entity.getValor()));
        return snapshot;
    }

    private String normalizar(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}


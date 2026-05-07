package ar.com.leo.super_master_backend.dominio.canal.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.com.leo.super_master_backend.dominio.canal.dto.CanalCreateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalUpdateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalPatchDTO;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConcepto;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.canal.mapper.CanalMapper;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalConceptoCuotaRepository;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalConceptoRepository;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.AplicaSobre;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.ConflictException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCanalPrecio;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoMargen;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoCanalPrecioRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoMargenRepository;
import lombok.RequiredArgsConstructor;
import org.openapitools.jackson.nullable.JsonNullable;

@Service
@RequiredArgsConstructor
public class CanalServiceImpl implements CanalService {

    private final CanalRepository canalRepository;
    private final CanalMapper canalMapper;
    private final CanalConceptoRepository canalConceptoRepository;
    private final CanalConceptoCuotaRepository canalConceptoCuotaRepository;
    private final AuditoriaService auditoriaService;

    private final ProductoMargenRepository productoMargenRepository;
    private final ProductoCanalPrecioRepository productoCanalPrecioRepository;
    private final ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService recalculoPendienteService;
    private final CanalScopeService canalScopeService;

    // =======================================
    // CRUD + DTOs
    // =======================================
    @Override
    @Transactional(readOnly = true)
    public Page<CanalDTO> listar(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return canalRepository.findByNombreContainingIgnoreCase(search, pageable)
                    .map(canalMapper::toDTO);
        }
        return canalRepository.findAll(pageable)
                .map(canalMapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public CanalDTO obtener(Integer id) {
        return canalRepository.findById(id)
                .map(canalMapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Canal no encontrado"));
    }

    @Override
    @Transactional
    public CanalDTO crear(CanalCreateDTO dto) {
        Canal entity = canalMapper.toEntity(dto);
        canalRepository.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.CANAL, entity.getId(), entity.getNombre(), AuditoriaAccion.CREATE, Map.of(), capturarSnapshot(entity));
        return canalMapper.toDTO(entity);
    }

    @Override
    @Transactional
    public CanalDTO actualizar(Integer id, CanalUpdateDTO dto) {
        Canal entity = canalRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Canal no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        // Guardar canalBase anterior para detectar cambios
        Integer canalBaseIdAnterior = entity.getCanalBase() != null
                ? entity.getCanalBase().getId()
                : null;

        canalMapper.updateEntityFromDTO(dto, entity);
        canalRepository.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.CANAL, id, entity.getNombre(), AuditoriaAccion.UPDATE, estadoAnterior, capturarSnapshot(entity));

        // Detectar cambio en canalBase y recalcular si es necesario
        Integer canalBaseIdNuevo = entity.getCanalBase() != null
                ? entity.getCanalBase().getId()
                : null;

        if (!java.util.Objects.equals(canalBaseIdAnterior, canalBaseIdNuevo)) {
            // El canal cambió de canalBase → su PVP cambia, y los subcanales que dependían
            // del canal modificado también se ven afectados vía la cadena.
            recalculoPendienteService.marcarCanales("Cambio en canal base",
                    canalScopeService.idsConSubcanales(id));
        }

        return canalMapper.toDTO(entity);
    }

    @Override
    @Transactional
    public CanalDTO patch(Integer id, CanalPatchDTO patchDto) {
        if (!presente(patchDto.getNombre()) && !presente(patchDto.getCanalBaseId())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }

        Canal entity = canalRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Canal no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        Integer canalBaseIdAnterior = entity.getCanalBase() != null ? entity.getCanalBase().getId() : null;

        if (presente(patchDto.getNombre())) {
            entity.setNombre(leerStringRequerido(patchDto.getNombre(), "nombre", 45));
        }
        if (presente(patchDto.getCanalBaseId())) {
            Integer canalBaseId = leerIdOpcional(patchDto.getCanalBaseId(), "canalBaseId");
            entity.setCanalBase(canalBaseId != null ? new Canal(canalBaseId) : null);
        }

        canalRepository.save(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.CANAL, id, entity.getNombre(), AuditoriaAccion.UPDATE, estadoAnterior, capturarSnapshot(entity));

        Integer canalBaseIdNuevo = entity.getCanalBase() != null ? entity.getCanalBase().getId() : null;
        if (!Objects.equals(canalBaseIdAnterior, canalBaseIdNuevo)) {
            // El canal cambió de canalBase → su PVP cambia, y los subcanales que dependían
            // del canal modificado también se ven afectados vía la cadena.
            recalculoPendienteService.marcarCanales("Cambio en canal base",
                    canalScopeService.idsConSubcanales(id));
        }

        return canalMapper.toDTO(entity);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        Canal entity = canalRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Canal no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);
        String codigo = entity.getNombre();

        if (productoCanalPrecioRepository.existsByCanalId(id)) {
            throw new ConflictException("No se puede eliminar porque tiene precios calculados. Elimine los precios del canal primero.");
        }
        if (canalConceptoCuotaRepository.existsByCanalId(id)) {
            throw new ConflictException("No se puede eliminar porque tiene cuotas configuradas.");
        }

        canalRepository.delete(entity);
        auditoriaService.registrarCambios(AuditoriaEntidad.CANAL, id, codigo, AuditoriaAccion.DELETE, estadoAnterior, Map.of());
    }

    // ===================================================
    // LÓGICA DE NEGOCIO: CAMBIO DE MARGEN DEL CANAL
    // ===================================================
    @Override
    @Transactional
    public void actualizarMargen(Integer canalId, BigDecimal nuevoMargen) {

        // 0) Validar canal
        if (!canalRepository.existsById(canalId)) {
            throw new NotFoundException("Canal no encontrado");
        }

        // Determinar qué tipo de margen usar según el concepto del canal
        List<CanalConcepto> conceptosCanal = canalConceptoRepository.findByCanalId(canalId);
        boolean esMayorista = conceptosCanal.stream()
                .anyMatch(cc -> cc.getConcepto() != null
                        && cc.getConcepto().getAplicaSobre() == AplicaSobre.FLAG_USAR_MARGEN_MAYORISTA);
        boolean esMinorista = conceptosCanal.stream()
                .anyMatch(cc -> cc.getConcepto() != null
                        && cc.getConcepto().getAplicaSobre() == AplicaSobre.FLAG_USAR_MARGEN_MINORISTA);

        if (!esMayorista && !esMinorista) {
            throw new NotFoundException("El canal no tiene configurado concepto FLAG_USAR_MARGEN_MINORISTA ni FLAG_USAR_MARGEN_MAYORISTA");
        }

        // 1) Obtener IDs de productos únicos que tienen precios calculados para este canal
        List<Integer> productoIds = productoCanalPrecioRepository.findByCanalIdWithProductoFetch(canalId)
                .stream()
                .map(precio -> precio.getProducto().getId())
                .distinct()
                .toList();

        // 2) Cargar todos los márgenes en una sola query y actualizar
        List<ProductoMargen> margenes = productoMargenRepository.findByProductoIdIn(productoIds);
        for (ProductoMargen productoMargen : margenes) {
            if (esMayorista) {
                productoMargen.setMargenMayorista(nuevoMargen);
            } else {
                productoMargen.setMargenMinorista(nuevoMargen);
            }
        }
        productoMargenRepository.saveAll(margenes);

        // 3) Recalcular precios de todos los productos del canal (y subcanales).
        recalculoPendienteService.marcarCanales("Recálculo de canal",
                canalScopeService.idsConSubcanales(canalId));
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

    private Map<String, String> capturarSnapshot(Canal canal) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("nombre", normalizar(canal.getNombre()));
        snapshot.put(
                "canalBase",
                canal.getCanalBase() != null
                        ? canal.getCanalBase().getId() + " - " + canal.getCanalBase().getNombre()
                        : null
        );
        return snapshot;
    }

    private String normalizar(Object value) {
        return value == null ? null : String.valueOf(value);
    }

}







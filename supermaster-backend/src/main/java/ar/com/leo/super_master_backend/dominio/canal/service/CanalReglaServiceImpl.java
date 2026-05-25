package ar.com.leo.super_master_backend.dominio.canal.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalReglaCreateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalReglaDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalReglaPatchDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalReglaUpdateDTO;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalRegla;
import ar.com.leo.super_master_backend.dominio.canal.entity.TipoRegla;
import ar.com.leo.super_master_backend.dominio.canal.mapper.CanalReglaMapper;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalReglaRepository;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.repository.ClasifGastroRepository;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.clasif_gral.repository.ClasifGralRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService;
import static ar.com.leo.super_master_backend.dominio.common.util.JsonNullableFields.*;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.marca.repository.MarcaRepository;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import ar.com.leo.super_master_backend.dominio.tipo.repository.TipoRepository;
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
public class CanalReglaServiceImpl implements CanalReglaService {

    private final CanalReglaRepository repository;
    private final CanalRepository canalRepository;
    private final TipoRepository tipoRepository;
    private final MarcaRepository marcaRepository;
    private final ClasifGralRepository clasifGralRepository;
    private final ClasifGastroRepository clasifGastroRepository;
    private final ProductoRepository productoRepository;
    private final CanalReglaMapper mapper;
    private final AuditoriaService auditoriaService;
    private final RecalculoPendienteService recalculoPendienteService;
    private final CanalScopeService canalScopeService;

    @Override
    @Transactional(readOnly = true)
    public Page<CanalReglaDTO> listar(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public CanalReglaDTO obtener(Long id) {
        return repository.findById(id)
                .map(mapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Regla de canal no encontrada"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CanalReglaDTO> listarPorCanal(Integer canalId) {
        return repository.findByCanalId(canalId).stream().map(mapper::toDTO).toList();
    }

    @Override
    @Transactional
    public CanalReglaDTO crear(CanalReglaCreateDTO dto) {
        if (!canalRepository.existsById(dto.canalId())) {
            throw new NotFoundException("Canal no encontrado");
        }
        validarRelacionesOpcionales(dto.tipoId(), dto.marcaId(), dto.clasifGralId(),
                dto.clasifGastroId(), dto.productoId());

        CanalRegla entity = mapper.toEntity(dto);
        repository.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CANAL_REGLA,
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
    public CanalReglaDTO actualizar(Long id, CanalReglaUpdateDTO dto) {
        CanalRegla entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Regla de canal no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        if (dto.canalId() != null && !canalRepository.existsById(dto.canalId())) {
            throw new NotFoundException("Canal no encontrado");
        }
        validarRelacionesOpcionales(dto.tipoId(), dto.marcaId(), dto.clasifGralId(),
                dto.clasifGastroId(), dto.productoId());

        mapper.updateEntityFromDTO(dto, entity);
        repository.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CANAL_REGLA,
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
    public CanalReglaDTO patch(Long id, CanalReglaPatchDTO patchDto) {
        if (!presente(patchDto.getCanalId())
                && !presente(patchDto.getTipoRegla())
                && !presente(patchDto.getTag())
                && !presente(patchDto.getTipoId())
                && !presente(patchDto.getMarcaId())
                && !presente(patchDto.getClasifGralId())
                && !presente(patchDto.getClasifGastroId())
                && !presente(patchDto.getProductoId())
                && !presente(patchDto.getTieneEnvio())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }

        CanalRegla entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Regla de canal no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        if (presente(patchDto.getCanalId())) {
            Integer canalId = leerIdRequerido(patchDto.getCanalId(), "canalId");
            if (!canalRepository.existsById(canalId)) {
                throw new NotFoundException("Canal no encontrado");
            }
            entity.setCanal(new Canal(canalId));
        }
        if (presente(patchDto.getTipoRegla())) {
            entity.setTipoRegla(leerEnumRequerido(patchDto.getTipoRegla(), "tipoRegla", TipoRegla.class));
        }
        if (presente(patchDto.getTag())) {
            entity.setTag(patchDto.getTag().orElse(null));
        }
        if (presente(patchDto.getTipoId())) {
            Integer tipoId = leerIdOpcional(patchDto.getTipoId(), "tipoId");
            if (tipoId != null && !tipoRepository.existsById(tipoId)) {
                throw new NotFoundException("Tipo no encontrado");
            }
            entity.setTipo(tipoId != null ? new Tipo(tipoId) : null);
        }
        if (presente(patchDto.getMarcaId())) {
            Integer marcaId = leerIdOpcional(patchDto.getMarcaId(), "marcaId");
            if (marcaId != null && !marcaRepository.existsById(marcaId)) {
                throw new NotFoundException("Marca no encontrada");
            }
            entity.setMarca(marcaId != null ? new Marca(marcaId) : null);
        }
        if (presente(patchDto.getClasifGralId())) {
            Integer clasifGralId = leerIdOpcional(patchDto.getClasifGralId(), "clasifGralId");
            if (clasifGralId != null && !clasifGralRepository.existsById(clasifGralId)) {
                throw new NotFoundException("Clasificación general no encontrada");
            }
            entity.setClasifGral(clasifGralId != null ? new ClasifGral(clasifGralId) : null);
        }
        if (presente(patchDto.getClasifGastroId())) {
            Integer clasifGastroId = leerIdOpcional(patchDto.getClasifGastroId(), "clasifGastroId");
            if (clasifGastroId != null && !clasifGastroRepository.existsById(clasifGastroId)) {
                throw new NotFoundException("Clasificación gastro no encontrada");
            }
            entity.setClasifGastro(clasifGastroId != null ? new ClasifGastro(clasifGastroId) : null);
        }
        if (presente(patchDto.getProductoId())) {
            Integer productoId = leerIdOpcional(patchDto.getProductoId(), "productoId");
            if (productoId != null && !productoRepository.existsById(productoId)) {
                throw new NotFoundException("Producto no encontrado");
            }
            entity.setProducto(productoId != null ? new Producto(productoId) : null);
        }
        if (presente(patchDto.getTieneEnvio())) {
            entity.setTieneEnvio(leerBooleanOpcional(patchDto.getTieneEnvio(), "tieneEnvio"));
        }

        repository.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CANAL_REGLA,
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
        CanalRegla entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Regla de canal no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);
        String codigo = construirCodigoEntidad(entity);
        Integer canalId = entity.getCanal().getId();
        repository.delete(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CANAL_REGLA,
                Math.toIntExact(id),
                codigo,
                AuditoriaAccion.DELETE,
                estadoAnterior,
                Map.of()
        );
        programarRecalculoPostCommit(canalId);
    }

    // =========================
    // EVALUADOR
    // =========================
    @Override
    @Transactional(readOnly = true)
    public boolean productoAplicaAlCanal(Integer canalId, Producto producto) {
        List<CanalRegla> reglas = repository.findByCanalIdWithRelationsFetch(canalId);
        return evaluarReglas(reglas, producto);
    }

    /**
     * Evalúa la lista de reglas contra el producto.
     *   - Si hay reglas INCLUIR: el producto debe cumplir al menos una.
     *   - Cualquier regla EXCLUIR que cumpla deja al producto fuera.
     */
    public static boolean evaluarReglas(List<CanalRegla> reglas, Producto producto) {
        if (reglas == null || reglas.isEmpty()) {
            return true;
        }
        boolean hayIncluir = false;
        boolean cumpleAlgunaIncluir = false;
        for (CanalRegla regla : reglas) {
            boolean cumple = cumpleCondiciones(regla, producto);
            if (regla.getTipoRegla() == TipoRegla.INCLUIR) {
                hayIncluir = true;
                if (cumple) {
                    cumpleAlgunaIncluir = true;
                }
            } else if (regla.getTipoRegla() == TipoRegla.EXCLUIR) {
                if (cumple) {
                    return false;
                }
            }
        }
        if (hayIncluir && !cumpleAlgunaIncluir) {
            return false;
        }
        return true;
    }

    private static boolean cumpleCondiciones(CanalRegla regla, Producto producto) {
        boolean tieneCondiciones = regla.getTag() != null
                || regla.getTipo() != null
                || regla.getMarca() != null
                || regla.getClasifGral() != null
                || regla.getClasifGastro() != null
                || regla.getProducto() != null
                || regla.getTieneEnvio() != null;
        if (!tieneCondiciones) {
            return true;
        }
        if (regla.getTag() != null) {
            Tag productoTag = producto.getTag() != null ? producto.getTag() : Tag.MENAJE;
            if (!regla.getTag().equals(productoTag)) {
                return false;
            }
        }
        if (regla.getTipo() != null) {
            if (producto.getTipo() == null
                    || !regla.getTipo().getId().equals(producto.getTipo().getId())) {
                return false;
            }
        }
        if (regla.getMarca() != null) {
            if (producto.getMarca() == null
                    || !regla.getMarca().getId().equals(producto.getMarca().getId())) {
                return false;
            }
        }
        if (regla.getClasifGral() != null) {
            if (producto.getClasifGral() == null
                    || !regla.getClasifGral().getId().equals(producto.getClasifGral().getId())) {
                return false;
            }
        }
        if (regla.getClasifGastro() != null) {
            if (producto.getClasifGastro() == null
                    || !regla.getClasifGastro().getId().equals(producto.getClasifGastro().getId())) {
                return false;
            }
        }
        if (regla.getProducto() != null) {
            if (producto.getId() == null
                    || !regla.getProducto().getId().equals(producto.getId())) {
                return false;
            }
        }
        if (regla.getTieneEnvio() != null) {
            BigDecimal precioEnvio = producto.getMla() != null ? producto.getMla().getPrecioEnvio() : null;
            boolean productoTieneEnvio = precioEnvio != null && precioEnvio.compareTo(BigDecimal.ZERO) > 0;
            if (regla.getTieneEnvio() != productoTieneEnvio) {
                return false;
            }
        }
        return true;
    }

    // =========================
    // VALIDACIÓN / HELPERS
    // =========================
    private void validarRelacionesOpcionales(Integer tipoId, Integer marcaId, Integer clasifGralId,
                                             Integer clasifGastroId, Integer productoId) {
        if (tipoId != null && !tipoRepository.existsById(tipoId)) {
            throw new NotFoundException("Tipo no encontrado");
        }
        if (marcaId != null && !marcaRepository.existsById(marcaId)) {
            throw new NotFoundException("Marca no encontrada");
        }
        if (clasifGralId != null && !clasifGralRepository.existsById(clasifGralId)) {
            throw new NotFoundException("Clasificación general no encontrada");
        }
        if (clasifGastroId != null && !clasifGastroRepository.existsById(clasifGastroId)) {
            throw new NotFoundException("Clasificación gastro no encontrada");
        }
        if (productoId != null && !productoRepository.existsById(productoId)) {
            throw new NotFoundException("Producto no encontrado");
        }
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

    private Map<String, String> capturarSnapshot(CanalRegla entity) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("canal", describirCanal(entity.getCanal()));
        snapshot.put("tipoRegla", normalizar(entity.getTipoRegla()));
        snapshot.put("tag", entity.getTag() != null ? entity.getTag().name() : null);
        snapshot.put("tipo", describirRelacion(entity.getTipo()));
        snapshot.put("marca", describirRelacion(entity.getMarca()));
        snapshot.put("clasifGral", describirRelacion(entity.getClasifGral()));
        snapshot.put("clasifGastro", describirRelacion(entity.getClasifGastro()));
        snapshot.put("producto", describirProducto(entity.getProducto()));
        snapshot.put("tieneEnvio", normalizar(entity.getTieneEnvio()));
        return snapshot;
    }

    private String construirCodigoEntidad(CanalRegla entity) {
        String canal = entity.getCanal() != null ? entity.getCanal().getNombre() : "Sin canal";
        String tipoRegla = entity.getTipoRegla() != null ? entity.getTipoRegla().name() : "";
        return canal + " / " + tipoRegla;
    }

    private String describirCanal(Canal canal) {
        return canal == null ? null : canal.getId() + " - " + canal.getNombre();
    }

    private String describirProducto(Producto producto) {
        return producto == null ? null : producto.getId() + " - " + (producto.getSku() != null ? producto.getSku() : "");
    }

    private String describirRelacion(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Tipo tipo) {
            return tipo.getId() + " - " + tipo.getNombre();
        }
        if (value instanceof Marca marca) {
            return marca.getId() + " - " + marca.getNombre();
        }
        if (value instanceof ClasifGral clasifGral) {
            return clasifGral.getId() + " - " + clasifGral.getNombre();
        }
        if (value instanceof ClasifGastro clasifGastro) {
            return clasifGastro.getId() + " - " + clasifGastro.getNombre();
        }
        return normalizar(value);
    }

    private void programarRecalculoPostCommit(Integer canalId) {
        // Incluye subcanales: las reglas de canal (filtros de qué productos aplican) cambian
        // qué SKUs tienen precio en el canal, lo que se propaga vía PVP base.
        recalculoPendienteService.marcarCanales("Cambio en regla de canal",
                canalScopeService.idsConSubcanales(canalId));
    }
}

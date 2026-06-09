package ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.catalogo.entity.Catalogo;
import ar.com.leo.super_master_backend.dominio.catalogo.repository.CatalogoRepository;
import ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.dto.CatalogoPdfConfigCreateDTO;
import ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.dto.CatalogoPdfConfigDTO;
import ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.dto.CatalogoPdfConfigUpdateDTO;
import ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.entity.CatalogoPdfConfig;
import ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.entity.CatalogoPdfEstetica;
import ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.entity.CatalogoPdfTipoDocumento;
import ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.repository.CatalogoPdfConfigRepository;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.clasif_gral.repository.ClasifGralRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.marca.repository.MarcaRepository;
import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import ar.com.leo.super_master_backend.dominio.tipo.repository.TipoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CatalogoPdfConfigServiceImpl implements CatalogoPdfConfigService {

    private final CatalogoPdfConfigRepository repository;
    private final CanalRepository canalRepository;
    private final CatalogoRepository catalogoRepository;
    private final ClasifGralRepository clasifGralRepository;
    private final TipoRepository tipoRepository;
    private final MarcaRepository marcaRepository;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional(readOnly = true)
    public Page<CatalogoPdfConfigDTO> listar(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return repository
                    .findByNombreContainingIgnoreCase(search, pageable)
                    .map(this::toDto);
        }
        return repository.findAll(pageable).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogoPdfConfigDTO obtener(Integer id) {
        return repository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new NotFoundException("Configuración de catálogo PDF no encontrada"));
    }

    @Override
    @Transactional
    public CatalogoPdfConfigDTO crear(CatalogoPdfConfigCreateDTO dto) {
        CatalogoPdfConfig entity = new CatalogoPdfConfig();
        apply(entity, dto.nombre(), dto.canalId(), dto.catalogoId(), dto.cuotas(), dto.ordenarPor(), dto.clasifGralId(), dto.tipoId(), dto.marcaId(), dto.tag(), dto.caratula(), dto.titulo(), dto.estetica(), dto.tipoDocumento(), dto.productosPorPagina(), dto.ubicacionSalida(), dto.activo());
        repository.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CATALOGO_PDF_CONFIG,
                entity.getId(),
                entity.getNombre(),
                AuditoriaAccion.CREATE,
                Map.of(),
                capturarSnapshot(entity)
        );
        return toDto(entity);
    }

    @Override
    @Transactional
    public CatalogoPdfConfigDTO actualizar(Integer id, CatalogoPdfConfigUpdateDTO dto) {
        CatalogoPdfConfig entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Configuración de catálogo PDF no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        apply(entity, dto.nombre(), dto.canalId(), dto.catalogoId(), dto.cuotas(), dto.ordenarPor(), dto.clasifGralId(), dto.tipoId(), dto.marcaId(), dto.tag(), dto.caratula(), dto.titulo(), dto.estetica(), dto.tipoDocumento(), dto.productosPorPagina(), dto.ubicacionSalida(), dto.activo());
        repository.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CATALOGO_PDF_CONFIG,
                id,
                entity.getNombre(),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(entity)
        );
        return toDto(entity);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        CatalogoPdfConfig entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Configuración de catálogo PDF no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);
        String codigo = entity.getNombre();
        repository.delete(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.CATALOGO_PDF_CONFIG,
                id,
                codigo,
                AuditoriaAccion.DELETE,
                estadoAnterior,
                Map.of()
        );
    }

    private void apply(CatalogoPdfConfig entity, String nombre, Integer canalId, Integer catalogoId, Integer cuotas,
                       List<String> ordenarPor, Integer clasifGralId, Integer tipoId, Integer marcaId, String tag,
                       Boolean caratula, String titulo, String estetica,
                       String tipoDocumento, Integer productosPorPagina, String ubicacionSalida, Boolean activo) {
        if (canalId == null) {
            throw new BadRequestException("El canal es obligatorio");
        }
        if (catalogoId == null) {
            throw new BadRequestException("El catálogo es obligatorio");
        }
        Canal canal = canalRepository.findById(canalId)
                .orElseThrow(() -> new NotFoundException("Canal no encontrado: " + canalId));
        Catalogo catalogo = catalogoRepository.findById(catalogoId)
                .orElseThrow(() -> new NotFoundException("Catálogo no encontrado: " + catalogoId));
        ClasifGral clasifGral = clasifGralId != null
                ? clasifGralRepository.findById(clasifGralId).orElseThrow(() -> new NotFoundException("Clasificación general no encontrada: " + clasifGralId))
                : null;
        Tipo tipo = tipoId != null
                ? tipoRepository.findById(tipoId).orElseThrow(() -> new NotFoundException("Tipo no encontrado: " + tipoId))
                : null;
        Marca marca = marcaId != null
                ? marcaRepository.findById(marcaId).orElseThrow(() -> new NotFoundException("Marca no encontrada: " + marcaId))
                : null;

        entity.setNombre(trimToNull(nombre));
        entity.setCanalId(canal.getId());
        entity.setCatalogoId(catalogo.getId());
        entity.setCuotas(cuotas);
        entity.setOrdenarPor(normalizeOrdenarPor(ordenarPor));
        entity.setClasifGralId(clasifGral != null ? clasifGral.getId() : null);
        entity.setTipoId(tipo != null ? tipo.getId() : null);
        entity.setMarcaId(marca != null ? marca.getId() : null);
        entity.setTag(parseTag(tag));
        entity.setCaratula(caratula);
        entity.setTitulo(trimToNull(titulo));
        entity.setEstetica(parseEstetica(estetica));
        entity.setTipoDocumento(parseTipoDocumento(tipoDocumento));
        entity.setProductosPorPagina(productosPorPagina);
        entity.setUbicacionSalida(trimToNull(ubicacionSalida));
        entity.setActivo(activo);
    }

    private CatalogoPdfConfigDTO toDto(CatalogoPdfConfig entity) {
        // Lookup tolerante a IDs null y a registros borrados (las FKs son Integer
        // planos sin constraint, así que una limpieza de canales/catalogos/clasif
        // puede dejar referencias huérfanas). El listado no debe romperse por eso;
        // el nombre queda en null y la UI muestra el dato como vacío.
        String canalNombre = entity.getCanalId() != null
                ? canalRepository.findById(entity.getCanalId()).map(Canal::getNombre).orElse(null)
                : null;
        String catalogoNombre = entity.getCatalogoId() != null
                ? catalogoRepository.findById(entity.getCatalogoId()).map(Catalogo::getNombre).orElse(null)
                : null;
        String clasificacion = entity.getClasifGralId() != null
                ? clasifGralRepository.findById(entity.getClasifGralId()).map(ClasifGral::getNombre).orElse(null)
                : null;
        String tipoNombre = entity.getTipoId() != null
                ? tipoRepository.findById(entity.getTipoId()).map(Tipo::getNombre).orElse(null)
                : null;
        String marcaNombre = entity.getMarcaId() != null
                ? marcaRepository.findById(entity.getMarcaId()).map(Marca::getNombre).orElse(null)
                : null;

        return new CatalogoPdfConfigDTO(
                entity.getId(),
                entity.getNombre(),
                entity.getCanalId(),
                canalNombre,
                entity.getCatalogoId(),
                catalogoNombre,
                entity.getCuotas(),
                splitOrdenarPor(entity.getOrdenarPor()),
                entity.getClasifGralId(),
                clasificacion,
                entity.getTipoId(),
                tipoNombre,
                entity.getMarcaId(),
                marcaNombre,
                entity.getTag() != null ? entity.getTag().name() : null,
                entity.getCaratula(),
                entity.getTitulo(),
                entity.getEstetica() != null ? entity.getEstetica().name().replace('_', ' ') : null,
                entity.getTipoDocumento() != null ? entity.getTipoDocumento().name() : null,
                entity.getProductosPorPagina(),
                entity.getUbicacionSalida(),
                entity.getActivo(),
                entity.getFechaModificacion()
        );
    }

    private CatalogoPdfEstetica parseEstetica(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        String enumValue = normalized.toUpperCase(Locale.ROOT).replace(' ', '_');
        return CatalogoPdfEstetica.valueOf(enumValue);
    }

    private CatalogoPdfTipoDocumento parseTipoDocumento(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return CatalogoPdfTipoDocumento.valueOf(normalized.toUpperCase(Locale.ROOT));
    }

    private Tag parseTag(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Tag.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Tag inválido: " + value);
        }
    }

    private String normalizeOrdenarPor(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed == null) {
                continue;
            }
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (!normalized.contains(lower)) {
                normalized.add(lower);
            }
        }
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }

    private List<String> splitOrdenarPor(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = trimToNull(part);
            if (trimmed == null) {
                continue;
            }
            values.add(trimmed);
        }
        return List.copyOf(values);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Map<String, String> capturarSnapshot(CatalogoPdfConfig entity) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("nombre", normalizar(entity.getNombre()));
        snapshot.put("canalId", normalizarNumero(entity.getCanalId()));
        snapshot.put("canal", obtenerCanalNombre(entity.getCanalId()));
        snapshot.put("catalogoId", normalizarNumero(entity.getCatalogoId()));
        snapshot.put("catalogo", obtenerCatalogoNombre(entity.getCatalogoId()));
        snapshot.put("cuotas", normalizarNumero(entity.getCuotas()));
        snapshot.put("ordenarPor", normalizar(entity.getOrdenarPor()));
        snapshot.put("clasifGralId", normalizarNumero(entity.getClasifGralId()));
        snapshot.put("clasifGral", obtenerClasifGralNombre(entity.getClasifGralId()));
        snapshot.put("tipoId", normalizarNumero(entity.getTipoId()));
        snapshot.put("marcaId", normalizarNumero(entity.getMarcaId()));
        snapshot.put("tag", entity.getTag() != null ? entity.getTag().name() : null);
        snapshot.put("caratula", normalizarBoolean(entity.getCaratula()));
        snapshot.put("titulo", normalizar(entity.getTitulo()));
        snapshot.put("estetica", entity.getEstetica() != null ? entity.getEstetica().name() : null);
        snapshot.put("tipoDocumento", entity.getTipoDocumento() != null ? entity.getTipoDocumento().name() : null);
        snapshot.put("productosPorPagina", normalizarNumero(entity.getProductosPorPagina()));
        snapshot.put("ubicacionSalida", normalizar(entity.getUbicacionSalida()));
        snapshot.put("activo", normalizarBoolean(entity.getActivo()));
        return snapshot;
    }

    private String obtenerCanalNombre(Integer canalId) {
        if (canalId == null) return null;
        return canalRepository.findById(canalId).map(Canal::getNombre).orElse(null);
    }

    private String obtenerCatalogoNombre(Integer catalogoId) {
        if (catalogoId == null) return null;
        return catalogoRepository.findById(catalogoId).map(Catalogo::getNombre).orElse(null);
    }

    private String obtenerClasifGralNombre(Integer clasifGralId) {
        if (clasifGralId == null) return null;
        return clasifGralRepository.findById(clasifGralId).map(ClasifGral::getNombre).orElse(null);
    }

    private String normalizar(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizarNumero(Number value) {
        return value != null ? String.valueOf(value) : null;
    }

    private String normalizarBoolean(Boolean value) {
        return Objects.equals(value, Boolean.TRUE) ? "true" : Objects.equals(value, Boolean.FALSE) ? "false" : null;
    }
}

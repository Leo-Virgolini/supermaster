package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.auditoria.dto.AuditoriaCambioDTO;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConceptoCuota;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalConceptoCuotaRepository;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.apis.dux.service.DuxService;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.ConflictException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.material.entity.Material;
import ar.com.leo.super_master_backend.dominio.origen.entity.Origen;
import ar.com.leo.super_master_backend.dominio.producto.dto.*;
import ar.com.leo.super_master_backend.dominio.producto.entity.*;
import ar.com.leo.super_master_backend.dominio.producto.mapper.ProductoMapper;
import ar.com.leo.super_master_backend.dominio.producto.mla.entity.Mla;
import ar.com.leo.super_master_backend.dominio.producto.repository.*;
import ar.com.leo.super_master_backend.dominio.proveedor.entity.Proveedor;
import ar.com.leo.super_master_backend.dominio.regla_descuento.entity.ReglaDescuento;
import ar.com.leo.super_master_backend.dominio.regla_descuento.repository.ReglaDescuentoRepository;
import ar.com.leo.super_master_backend.dominio.reposicion.entity.TagReposicion;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import ar.com.leo.super_master_backend.dominio.sector_deposito.entity.SectorDeposito;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ar.com.leo.super_master_backend.dominio.common.util.JsonNullableFields.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductoServiceImpl implements ProductoService {

    private final ProductoRepository productoRepository;
    private final ProductoMapper productoMapper;
    private final ProductoCanalPrecioRepository productoCanalPrecioRepository;
    private final ProductoMargenRepository productoMargenRepository;
    private final ProductoCanalPrecioInfladoRepository productoCanalPrecioInfladoRepository;
    private final RecalculoPendienteService recalculoPendienteService;
    private final CanalRepository canalRepository;
    private final CanalConceptoCuotaRepository canalConceptoCuotaRepository;
    private final ReglaDescuentoRepository reglaDescuentoRepository;
    private final ProductoAptoRepository productoAptoRepository;
    private final ProductoCatalogoRepository productoCatalogoRepository;
    private final ProductoClienteRepository productoClienteRepository;
    private final ProductoAuditoriaService productoAuditoriaService;

    // @Lazy para romper el ciclo de dependencias (ProductoServiceImpl -> DuxService -> RecalculoPrecioFacade -> ...).
    @Lazy
    @Autowired
    private DuxService duxService;

    private static final int PRECISION_RESULTADO = 2;

    // ============================
    // LISTAR
    // ============================
    @Override
    @Transactional(readOnly = true)
    public Page<ProductoDTO> listar(Pageable pageable) {
        Page<Producto> page = productoRepository.findAll(pageable);
        return mapProductosPage(page, pageable);
    }

    // ============================
    // OBTENER
    // ============================
    @Override
    @Transactional(readOnly = true)
    public ProductoDTO obtener(Integer id) {
        return productoRepository.findById(id)
                .map(productoMapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
    }

    // ============================
    // CREAR
    // ============================
    @Override
    @Transactional
    public ProductoDTO crear(ProductoCreateDTO dto) {
        // Validar SKU único
        if (productoRepository.findBySku(dto.sku()).isPresent()) {
            throw new ConflictException("Ya existe un producto con el SKU: " + dto.sku());
        }
        // Validar que el SKU no exista ya en Dux (no pisar un ítem existente con el upsert ciego).
        verificarSkuLibreEnDux(dto.sku());

        Producto entity = productoMapper.toEntity(dto);
        validarAlMenosUnaClasificacion(entity);
        validarProductoSimpleCompleto(entity);
        productoRepository.save(entity);
        productoAuditoriaService.registrarCreacion(entity);
        programarRecalculoPostCommit("Producto creado", entity.getId());
        return productoMapper.toDTO(entity);
    }

    /**
     * Bloquea el alta si el SKU ya existe en Dux. Fail-closed: si no se puede verificar
     * (Dux caído/no configurado/timeout), también bloquea, con un mensaje distinto.
     */
    void verificarSkuLibreEnDux(String sku) {
        boolean existeEnDux;
        try {
            existeEnDux = duxService.obtenerProductoPorCodigo(sku) != null;
        } catch (Exception e) {
            log.warn("No se pudo verificar el SKU '{}' en Dux: {}", sku, e.getMessage());
            throw new ConflictException("No se pudo verificar el SKU en Dux (¿Dux no disponible?). Intentá de nuevo en un momento.");
        }
        if (existeEnDux) {
            throw new ConflictException("El SKU ya existe en Dux: " + sku);
        }
    }

    // ============================
    // ACTUALIZAR
    // ============================
    @Override
    @Transactional
    public ProductoDTO actualizar(Integer id, ProductoUpdateDTO dto) {
        Producto entity = productoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        Map<String, String> estadoAnterior = productoAuditoriaService.capturarSnapshot(entity);

        // Guardar valores anteriores para detectar cambios
        BigDecimal costoAnterior = entity.getCosto();
        BigDecimal ivaAnterior = entity.getIva();
        Integer clasifGastroIdAnterior = entity.getClasifGastro() != null ? entity.getClasifGastro().getId() : null;
        Integer proveedorIdAnterior = entity.getProveedor() != null ? entity.getProveedor().getId() : null;
        Integer tipoIdAnterior = entity.getTipo() != null ? entity.getTipo().getId() : null;
        Integer marcaIdAnterior = entity.getMarca() != null ? entity.getMarca().getId() : null;
        Integer clasifGralIdAnterior = entity.getClasifGral() != null ? entity.getClasifGral().getId() : null;
        Integer mlaIdAnterior = entity.getMla() != null ? entity.getMla().getId() : null;
        Tag tagAnterior = entity.getTag();

        productoMapper.updateEntityFromDTO(dto, entity);
        validarAlMenosUnaClasificacion(entity);
        validarProductoSimpleCompleto(entity);
        productoRepository.save(entity);
        productoAuditoriaService.registrarActualizacion(id, estadoAnterior, entity);

        // Recalcular precios si cambió algún atributo que afecte el cálculo o las reglas de conceptos
        boolean cambioCosto = dto.costo() != null && (costoAnterior == null || costoAnterior.compareTo(dto.costo()) != 0);
        boolean cambioIva = dto.iva() != null && (ivaAnterior == null || ivaAnterior.compareTo(dto.iva()) != 0);
        boolean cambioClasifGastro = dto.clasifGastroId() != null && !Objects.equals(clasifGastroIdAnterior, dto.clasifGastroId());
        boolean cambioProveedor = dto.proveedorId() != null && !Objects.equals(proveedorIdAnterior, dto.proveedorId());
        boolean cambioTipo = dto.tipoId() != null && !Objects.equals(tipoIdAnterior, dto.tipoId());
        boolean cambioMarca = dto.marcaId() != null && !Objects.equals(marcaIdAnterior, dto.marcaId());
        boolean cambioClasifGral = dto.clasifGralId() != null && !Objects.equals(clasifGralIdAnterior, dto.clasifGralId());
        boolean cambioMla = dto.mlaId() != null && !Objects.equals(mlaIdAnterior, dto.mlaId());
        boolean cambioTag = dto.tag() != null && !Objects.equals(tagAnterior, dto.tag());

        if (cambioCosto || cambioIva || cambioClasifGastro || cambioProveedor
                || cambioTipo || cambioMarca || cambioClasifGral || cambioMla || cambioTag) {
            programarRecalculoPostCommit("Cambio en producto", id);
        }

        return productoMapper.toDTO(entity);
    }

    @Override
    @Transactional
    public ProductoDTO patch(Integer id, ProductoPatchDTO patchDto) {
        if (isPatchVacio(patchDto)) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }

        Producto entity = productoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        Map<String, String> estadoAnterior = productoAuditoriaService.capturarSnapshot(entity);


        BigDecimal costoAnterior = entity.getCosto();
        BigDecimal ivaAnterior = entity.getIva();
        Integer clasifGastroIdAnterior = entity.getClasifGastro() != null ? entity.getClasifGastro().getId() : null;
        Integer proveedorIdAnterior = entity.getProveedor() != null ? entity.getProveedor().getId() : null;
        Integer tipoIdAnterior = entity.getTipo() != null ? entity.getTipo().getId() : null;
        Integer marcaIdAnterior = entity.getMarca() != null ? entity.getMarca().getId() : null;
        Integer clasifGralIdAnterior = entity.getClasifGral() != null ? entity.getClasifGral().getId() : null;
        Integer mlaIdAnterior = entity.getMla() != null ? entity.getMla().getId() : null;
        Tag tagAnterior = entity.getTag();

        aplicarPatch(entity, patchDto);
        validarAlMenosUnaClasificacion(entity);
        validarProductoSimpleCompleto(entity);
        productoRepository.save(entity);
        productoAuditoriaService.registrarActualizacion(id, estadoAnterior, entity);

        boolean cambioCosto = presente(patchDto.getCosto()) && !Objects.equals(costoAnterior, entity.getCosto());
        boolean cambioIva = presente(patchDto.getIva()) && !Objects.equals(ivaAnterior, entity.getIva());
        boolean cambioClasifGastro = presente(patchDto.getClasifGastroId()) && !Objects.equals(clasifGastroIdAnterior, entity.getClasifGastro() != null ? entity.getClasifGastro().getId() : null);
        boolean cambioProveedor = presente(patchDto.getProveedorId()) && !Objects.equals(proveedorIdAnterior, entity.getProveedor() != null ? entity.getProveedor().getId() : null);
        boolean cambioTipo = presente(patchDto.getTipoId()) && !Objects.equals(tipoIdAnterior, entity.getTipo() != null ? entity.getTipo().getId() : null);
        boolean cambioMarca = presente(patchDto.getMarcaId()) && !Objects.equals(marcaIdAnterior, entity.getMarca() != null ? entity.getMarca().getId() : null);
        boolean cambioClasifGral = presente(patchDto.getClasifGralId()) && !Objects.equals(clasifGralIdAnterior, entity.getClasifGral() != null ? entity.getClasifGral().getId() : null);
        boolean cambioMla = presente(patchDto.getMlaId()) && !Objects.equals(mlaIdAnterior, entity.getMla() != null ? entity.getMla().getId() : null);
        boolean cambioTag = presente(patchDto.getTag()) && !Objects.equals(tagAnterior, entity.getTag());

        if (cambioCosto || cambioIva || cambioClasifGastro || cambioProveedor
                || cambioTipo || cambioMarca || cambioClasifGral || cambioMla || cambioTag) {
            programarRecalculoPostCommit("Cambio en producto", id);
        }

        return productoMapper.toDTO(entity);
    }

    // ============================
    // ELIMINAR
    // ============================
    @Override
    @Transactional
    public void eliminar(Integer id) {
        Producto entity = productoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        Map<String, String> estadoAnterior = productoAuditoriaService.capturarSnapshot(entity);
        productoRepository.delete(entity);
        productoAuditoriaService.registrarEliminacion(id, estadoAnterior);
    }

    // ============================
    // OBTENER POR SKU
    // ============================
    @Override
    @Transactional(readOnly = true)
    public ProductoDTO obtenerPorSku(String sku) {
        return productoRepository.findBySku(sku)
                .map(productoMapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeSku(String sku) {
        return sku != null && !sku.isBlank() && productoRepository.existsBySku(sku.trim());
    }

    @Override
    @Transactional(readOnly = true)
    public String siguienteSkuLibre(boolean esCombo) {
        long min = esCombo ? 5_000_000L : 1_000_000L;
        long max = esCombo ? 5_999_999L : 1_999_999L;

        // Atajo para el caso común: si el mínimo del rango está libre, ya es el menor
        // disponible. Es una consulta por SKU exacto (usa índice) y evita el scan del
        // rango (CAST + REGEXP, sin índice) en cada apertura del modal / toggle de combo.
        if (productoRepository.findBySku(String.valueOf(min)).isEmpty()) {
            return String.valueOf(min);
        }

        // SKU usados del rango, ordenados ascendentemente. Buscamos el primer
        // entero que no esté ocupado empezando desde el mínimo del rango.
        List<Long> usados = productoRepository.findSkusNumericosEnRango(min, max);
        long esperado = min;
        for (Long n : usados) {
            if (n == null || n < esperado) continue;
            if (n == esperado) {
                esperado++;
            } else {
                break; // hueco encontrado en 'esperado'
            }
        }
        return esperado <= max ? String.valueOf(esperado) : null;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditoriaCambioDTO> listarAuditoria(Integer productoId, Pageable pageable) {
        if (!productoRepository.existsById(productoId)) {
            throw new NotFoundException("Producto no encontrado");
        }
        return productoAuditoriaService.listarPorProducto(productoId, pageable);
    }

    // ======================================================
    // BUSQUEDA / FILTRADO PROFESIONAL
    // ======================================================
    @Override
    @Transactional(readOnly = true)
    public Page<ProductoDTO> filtrar(ProductoFilter filter, Pageable pageable) {

        Specification<Producto> spec = Specification.allOf(

                // =======================
                // 1) TEXTO
                // =======================
                ProductoSpecifications.textoLike(filter.search()),

                // =======================
                // 1.1) FILTROS DE TEXTO DEDICADOS
                // =======================
                ProductoSpecifications.sku(filter.sku()),
                ProductoSpecifications.codExt(filter.codExt()),
                ProductoSpecifications.tituloDux(filter.tituloDux()),
                ProductoSpecifications.tituloNube(filter.tituloNube()),

                // =======================
                // 2) BOOLEANOS / NÚMEROS
                // =======================
                ProductoSpecifications.esCombo(filter.esCombo()),
                ProductoSpecifications.uxb(filter.uxb()),
                ProductoSpecifications.esMaquina(filter.esMaquina()),
                ProductoSpecifications.tieneMla(filter.tieneMla()),
                ProductoSpecifications.activo(filter.activo()),
                ProductoSpecifications.tagReposicion(filter.tagReposicion()),
                ProductoSpecifications.tags(filter.tags()),

                // =======================
                // 2.1) FILTROS MLA
                // =======================
                ProductoSpecifications.mla(filter.mla()),
                ProductoSpecifications.mlau(filter.mlau()),
                ProductoSpecifications.precioEnvioMin(filter.precioEnvioMin()),
                ProductoSpecifications.precioEnvioMax(filter.precioEnvioMax()),
                ProductoSpecifications.comisionPorcentajeMin(filter.comisionPorcentajeMin()),
                ProductoSpecifications.comisionPorcentajeMax(filter.comisionPorcentajeMax()),
                ProductoSpecifications.tieneComision(filter.tieneComision()),
                ProductoSpecifications.tienePrecioEnvio(filter.tienePrecioEnvio()),

                // =======================
                // 3) MANY-TO-ONE (multi-valor)
                // =======================
                ProductoSpecifications.marcaIds(filter.marcaIds()),
                ProductoSpecifications.origenIds(filter.origenIds()),
                ProductoSpecifications.tipoIds(filter.tipoIds()),
                ProductoSpecifications.clasifGralIds(filter.clasifGralIds()),
                ProductoSpecifications.clasifGastroIds(filter.clasifGastroIds()),
                ProductoSpecifications.proveedorIds(filter.proveedorIds()),
                ProductoSpecifications.materialIds(filter.materialIds()),

                // =======================
                // 4) RANGOS (costo / iva / stock)
                // =======================
                ProductoSpecifications.costoMin(filter.costoMin()),
                ProductoSpecifications.costoMax(filter.costoMax()),
                ProductoSpecifications.ivaMin(filter.ivaMin()),
                ProductoSpecifications.ivaMax(filter.ivaMax()),
                ProductoSpecifications.stockMin(filter.stockMin()),
                ProductoSpecifications.stockMax(filter.stockMax()),

                // =======================
                // 5) RANGO PVP
                // =======================
                ProductoSpecifications.pvpEnRango(filter.pvpMin(), filter.pvpMax(), filter.pvpCanalId()),

                // =======================
                // 6) FECHAS
                // =======================
                ProductoSpecifications.desdeFechaUltimoCosto(filter.desdeFechaUltimoCosto()),
                ProductoSpecifications.hastaFechaUltimoCosto(filter.hastaFechaUltimoCosto()),
                ProductoSpecifications.desdeFechaCreacion(filter.desdeFechaCreacion()),
                ProductoSpecifications.hastaFechaCreacion(filter.hastaFechaCreacion()),
                ProductoSpecifications.desdeFechaModificacion(filter.desdeFechaModificacion()),
                ProductoSpecifications.hastaFechaModificacion(filter.hastaFechaModificacion()),

                // =======================
                // 7) MANY-TO-MANY
                // =======================
                ProductoSpecifications.aptoIds(filter.aptoIds()),
                ProductoSpecifications.canalIds(filter.canalIds()),
                ProductoSpecifications.catalogoIds(filter.catalogoIds()),
                ProductoSpecifications.clienteIds(filter.clienteIds()),
                ProductoSpecifications.mlaIds(filter.mlaIds())
        );

        // Algunos campos de sort no son columnas directas de Producto y necesitan un
        // ORDER BY especial dentro de la Specification:
        //  - márgenes: viven en la relación productoMargenes -> LEFT JOIN (no excluye
        //    productos sin margen, cosa que sí haría el INNER join implícito del sort).
        //  - catálogo: relación many-to-many -> subconsulta escalar (MIN del nombre) para
        //    no duplicar filas. Se ordena por el 1er catálogo alfabético; los productos
        //    sin catálogo quedan al final.
        boolean ordenEspecial = pageable.getSort().stream()
                .anyMatch(o -> esSortEspecial(o.getProperty()));
        Pageable pageableEfectivo = pageable;
        if (ordenEspecial) {
            spec = spec.and(ordenarPorRelacion(pageable.getSort()));
            // El orden lo aplica la Specification; quitamos el sort del Pageable para que
            // SimpleJpaRepository no lo pise con su propio (INNER) join.
            pageableEfectivo = org.springframework.data.domain.PageRequest.of(
                    pageable.getPageNumber(), pageable.getPageSize());
        }

        Page<Producto> page = productoRepository.findAll(spec, pageableEfectivo);
        return mapProductosPage(page, pageable);
    }

    /** Sort del front -> nombre real del campo en la entidad ProductoMargen. */
    private static final Map<String, String> CAMPOS_MARGEN_SORT = Map.of(
            "margenminorista", "margenMinorista",
            "margenmayorista", "margenMayorista");

    private boolean esSortEspecial(String property) {
        String key = property.toLowerCase();
        return CAMPOS_MARGEN_SORT.containsKey(key)
                || key.equals("catalogo") || key.equals("catalogos")
                || key.equals("apto") || key.equals("aptos")
                || key.equals("cliente") || key.equals("clientes");
    }

    /**
     * Specification que aplica el ORDER BY completo del pageable resolviendo los campos
     * que no son columnas directas de Producto (márgenes vía LEFT JOIN, catálogo vía
     * subconsulta escalar) y el path directo para el resto. No aplica orden en el count
     * query (resultType Long).
     */
    private Specification<Producto> ordenarPorRelacion(Sort sort) {
        return (root, query, cb) -> {
            Class<?> rt = query.getResultType();
            if (rt != Long.class && rt != long.class) {
                Join<Object, Object> margenJoin = null;
                List<Order> orders = new ArrayList<>();
                for (Sort.Order o : sort) {
                    String key = o.getProperty().toLowerCase();
                    if (CAMPOS_MARGEN_SORT.containsKey(key)) {
                        if (margenJoin == null) {
                            margenJoin = root.join("productoMargenes", JoinType.LEFT);
                        }
                        Expression<?> expr = margenJoin.get(CAMPOS_MARGEN_SORT.get(key));
                        orders.add(o.isAscending() ? cb.asc(expr) : cb.desc(expr));
                    } else if (key.equals("catalogo") || key.equals("catalogos")) {
                        // Catálogo es many-to-many: ordenamos con subconsultas escalares
                        // correlacionadas (no joins, que duplicarían filas). Usamos dos
                        // subconsultas independientes para no reusar la misma instancia.
                        // 1) ¿tiene catálogos? -> los productos sin catálogo van al final
                        //    en ambas direcciones (ASC y DESC).
                        Subquery<Long> cuenta = query.subquery(Long.class);
                        Root<ProductoCatalogo> pcCount =
                                cuenta.from(ProductoCatalogo.class);
                        cuenta.select(cb.count(pcCount));
                        cuenta.where(cb.equal(pcCount.get("producto"), root));
                        orders.add(cb.asc(cb.<Integer>selectCase().when(cb.equal(cuenta, 0L), 1).otherwise(0)));
                        // 2) menor nombre de catálogo (1er alfabético) del producto.
                        Subquery<String> menorNombre = query.subquery(String.class);
                        Root<ProductoCatalogo> pcNombre =
                                menorNombre.from(ProductoCatalogo.class);
                        menorNombre.select(cb.least(pcNombre.get("catalogo").<String>get("nombre")));
                        menorNombre.where(cb.equal(pcNombre.get("producto"), root));
                        orders.add(o.isAscending() ? cb.asc(menorNombre) : cb.desc(menorNombre));
                    } else if (key.equals("apto") || key.equals("aptos")) {
                        // Apto es many-to-many: mismo enfoque que catálogo (subconsultas
                        // escalares correlacionadas). Sin aptos -> al final; se ordena por
                        // el 1er apto alfabético.
                        Subquery<Long> cuenta = query.subquery(Long.class);
                        Root<ProductoApto> paCount = cuenta.from(ProductoApto.class);
                        cuenta.select(cb.count(paCount));
                        cuenta.where(cb.equal(paCount.get("producto"), root));
                        orders.add(cb.asc(cb.<Integer>selectCase().when(cb.equal(cuenta, 0L), 1).otherwise(0)));
                        Subquery<String> menorNombre = query.subquery(String.class);
                        Root<ProductoApto> paNombre = menorNombre.from(ProductoApto.class);
                        menorNombre.select(cb.least(paNombre.get("apto").<String>get("nombre")));
                        menorNombre.where(cb.equal(paNombre.get("producto"), root));
                        orders.add(o.isAscending() ? cb.asc(menorNombre) : cb.desc(menorNombre));
                    } else if (key.equals("cliente") || key.equals("clientes")) {
                        // Cliente es many-to-many: mismo enfoque que catálogo. Sin clientes
                        // -> al final; se ordena por el 1er cliente alfabético.
                        Subquery<Long> cuenta = query.subquery(Long.class);
                        Root<ProductoCliente> pclCount = cuenta.from(ProductoCliente.class);
                        cuenta.select(cb.count(pclCount));
                        cuenta.where(cb.equal(pclCount.get("producto"), root));
                        orders.add(cb.asc(cb.<Integer>selectCase().when(cb.equal(cuenta, 0L), 1).otherwise(0)));
                        Subquery<String> menorNombre = query.subquery(String.class);
                        Root<ProductoCliente> pclNombre = menorNombre.from(ProductoCliente.class);
                        menorNombre.select(cb.least(pclNombre.get("cliente").<String>get("nombre")));
                        menorNombre.where(cb.equal(pclNombre.get("producto"), root));
                        orders.add(o.isAscending() ? cb.asc(menorNombre) : cb.desc(menorNombre));
                    } else {
                        // path directo (soporta anidados con punto, ej. "marca.nombre")
                        Path<?> path = root;
                        for (String part : o.getProperty().split("\\.")) {
                            path = path.get(part);
                        }
                        orders.add(o.isAscending() ? cb.asc(path) : cb.desc(path));
                    }
                }
                query.orderBy(orders);
            }
            return cb.conjunction();
        };
    }

    // ======================================================
    // LISTAR CON PRECIOS (PAGINADO)
    // Pagina sobre filas de precio (producto+canal+cuota),
    // no sobre productos. Cada fila = 1 producto con 1 precio.
    // ======================================================
    @Override
    @Transactional(readOnly = true)
    public Page<ProductoConPreciosDTO> listarConPrecios(ProductoFilter filter, Pageable pageable) {

        // Validar que el canal tenga las cuotas especificadas
        validarCanalConCuotas(filter.canalId(), filter.cuotas());

        // Traducir sort de campos especiales a campos de ProductoCanalPrecio
        Pageable sortedPageable = traducirSort(pageable);

        // 1) Construir Specification sobre ProductoCanalPrecio
        Specification<ProductoCanalPrecio> spec = Specification.allOf(
                // Filtros de precio (canal/cuotas)
                PrecioSpecifications.canalId(filter.canalId()),
                PrecioSpecifications.cuotas(filter.cuotas()),
                // ID
                PrecioSpecifications.productoId(filter.productoId()),
                // Texto
                PrecioSpecifications.textoLike(filter.search()),
                // Filtros de texto dedicados
                PrecioSpecifications.sku(filter.sku()),
                PrecioSpecifications.codExt(filter.codExt()),
                PrecioSpecifications.tituloDux(filter.tituloDux()),
                PrecioSpecifications.tituloNube(filter.tituloNube()),
                // Booleanos/Numéricos
                PrecioSpecifications.esCombo(filter.esCombo()),
                PrecioSpecifications.uxb(filter.uxb()),
                PrecioSpecifications.esMaquina(filter.esMaquina()),
                PrecioSpecifications.tieneMla(filter.tieneMla()),
                PrecioSpecifications.activo(filter.activo()),
                PrecioSpecifications.tagReposicion(filter.tagReposicion()),
                PrecioSpecifications.tags(filter.tags()),
                // Filtros MLA
                PrecioSpecifications.mla(filter.mla()),
                PrecioSpecifications.mlau(filter.mlau()),
                PrecioSpecifications.precioEnvioMin(filter.precioEnvioMin()),
                PrecioSpecifications.precioEnvioMax(filter.precioEnvioMax()),
                PrecioSpecifications.comisionPorcentajeMin(filter.comisionPorcentajeMin()),
                PrecioSpecifications.comisionPorcentajeMax(filter.comisionPorcentajeMax()),
                PrecioSpecifications.tieneComision(filter.tieneComision()),
                PrecioSpecifications.tienePrecioEnvio(filter.tienePrecioEnvio()),
                // Many-to-One (multi-valor)
                PrecioSpecifications.marcaIds(filter.marcaIds()),
                PrecioSpecifications.origenIds(filter.origenIds()),
                PrecioSpecifications.tipoIds(filter.tipoIds()),
                PrecioSpecifications.clasifGralIds(filter.clasifGralIds()),
                PrecioSpecifications.clasifGastroIds(filter.clasifGastroIds()),
                PrecioSpecifications.proveedorIds(filter.proveedorIds()),
                PrecioSpecifications.materialIds(filter.materialIds()),
                // Rangos
                PrecioSpecifications.costoMin(filter.costoMin()),
                PrecioSpecifications.costoMax(filter.costoMax()),
                PrecioSpecifications.ivaMin(filter.ivaMin()),
                PrecioSpecifications.ivaMax(filter.ivaMax()),
                PrecioSpecifications.stockMin(filter.stockMin()),
                PrecioSpecifications.stockMax(filter.stockMax()),
                // Rango PVP (directo sobre precio)
                PrecioSpecifications.pvpMin(filter.pvpMin()),
                PrecioSpecifications.pvpMax(filter.pvpMax()),
                // Fechas
                PrecioSpecifications.desdeFechaUltimoCosto(filter.desdeFechaUltimoCosto()),
                PrecioSpecifications.hastaFechaUltimoCosto(filter.hastaFechaUltimoCosto()),
                PrecioSpecifications.desdeFechaCreacion(filter.desdeFechaCreacion()),
                PrecioSpecifications.hastaFechaCreacion(filter.hastaFechaCreacion()),
                PrecioSpecifications.desdeFechaModificacion(filter.desdeFechaModificacion()),
                PrecioSpecifications.hastaFechaModificacion(filter.hastaFechaModificacion()),
                // Many-to-Many
                PrecioSpecifications.aptoIds(filter.aptoIds()),
                PrecioSpecifications.canalIds(filter.canalIds()),
                PrecioSpecifications.catalogoIds(filter.catalogoIds()),
                PrecioSpecifications.clienteIds(filter.clienteIds()),
                PrecioSpecifications.mlaIds(filter.mlaIds())
        );

        // 2) Paginar sobre filas de precio (producto+canal+cuota)
        Page<ProductoCanalPrecio> preciosPage = productoCanalPrecioRepository.findAll(spec, sortedPageable);

        if (preciosPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        // 3) Extraer productos únicos de esta página de precios
        List<ProductoCanalPrecio> preciosEnPagina = preciosPage.getContent();
        List<Integer> productoIds = preciosEnPagina.stream()
                .map(pcp -> pcp.getProducto().getId())
                .distinct()
                .toList();

        // 4) Cargar productos con todas las relaciones ManyToOne (1 query en vez de N+1)
        Map<Integer, Producto> productosMap = productoRepository.findAllByIdWithRelaciones(productoIds).stream()
                .collect(Collectors.toMap(Producto::getId, p -> p));

        // 4.1) Cargar márgenes
        Map<Integer, ProductoMargen> margenesPorProducto = productoMargenRepository.findByProductoIdIn(productoIds).stream()
                .collect(Collectors.toMap(pm -> pm.getProducto().getId(), pm -> pm));

        // 4.2) Cargar nombres de canales
        Set<Integer> canalIdsEnPagina = preciosEnPagina.stream()
                .map(p -> p.getCanal().getId())
                .collect(Collectors.toSet());
        Map<Integer, String> nombresPorCanal = canalRepository.findAllById(canalIdsEnPagina).stream()
                .collect(Collectors.toMap(Canal::getId, Canal::getNombre));

        // 4.3) Cargar descripciones de cuotas
        Map<String, String> descripcionesCuotas = canalConceptoCuotaRepository.findByCanalIdIn(canalIdsEnPagina).stream()
                .filter(c -> c.getDescripcion() != null)
                .collect(Collectors.toMap(
                        c -> c.getCanal().getId() + "_" + c.getCuotas(),
                        CanalConceptoCuota::getDescripcion,
                        (a, b) -> a
                ));

        // 4.4) Cargar reglas de descuento
        Map<Integer, List<ReglaDescuento>> reglasPorCanal = reglaDescuentoRepository
                .findByCanalIdInAndActivoTrueOrderByCanalIdAscPrioridadAsc(new ArrayList<>(canalIdsEnPagina))
                .stream()
                .collect(Collectors.groupingBy(regla -> regla.getCanal().getId(), LinkedHashMap::new, Collectors.toList()));

        // 4.5) Cargar reglas de precio inflado activas para los productos de esta página
        // Clave: "productoId_canalId" -> ProductoCanalPrecioInflado
        Map<String, ProductoCanalPrecioInflado> infladosPorProductoCanal = new HashMap<>();
        if (!productoIds.isEmpty()) {
            List<ProductoCanalPrecioInflado> inflados = productoCanalPrecioInfladoRepository
                    .findByProductoIdInAndActivoTrueWithFetch(productoIds);
            for (ProductoCanalPrecioInflado pcpi : inflados) {
                String key = pcpi.getProducto().getId() + "_" + pcpi.getCanal().getId();
                infladosPorProductoCanal.put(key, pcpi);
            }
        }

        // 5) Mapear cada fila de precio a un ProductoConPreciosDTO (1 producto, 1 canal, 1 cuota)
        List<ProductoConPreciosDTO> dtos = preciosEnPagina.stream()
                .map(pcp -> {
                    Producto producto = productosMap.get(pcp.getProducto().getId());
                    ProductoMargen productoMargen = margenesPorProducto.get(producto.getId());

                    // Calcular descuentos para este canal si hay reglas
                    Map<Integer, List<DescuentoAplicableDTO>> descuentosPorCanal = null;
                    if (!reglasPorCanal.isEmpty()) {
                        descuentosPorCanal = calcularDescuentosPorCanal(List.of(pcp), reglasPorCanal);
                    }

                    return productoMapper.toProductoConPreciosDTO(
                            producto, productoMargen, List.of(pcp),
                            descripcionesCuotas, descuentosPorCanal, nombresPorCanal, infladosPorProductoCanal);
                })
                .toList();

        // 6) Retornar Page con metadata de filas de precio
        return new PageImpl<>(dtos, pageable, preciosPage.getTotalElements());
    }

    /**
     * Traduce nombres de sort del frontend a campos de ProductoCanalPrecio.
     * Campos de producto se prefijan con "producto.", campos de precio se usan directo.
     */
    private Pageable traducirSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }

        List<Sort.Order> translated = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            String campo = order.getProperty().toLowerCase();
            // Campos con sort en memoria: no enviar a JPA
            if (CAMPOS_SORT_ESPECIAL.contains(campo)) continue;
            String mappedField = switch (campo) {
                case "pvp" -> "pvp";
                case "pvpinflado" -> "pvpInflado";
                case "costoproducto" -> "costoProducto";
                case "costosventa" -> "costosVenta";
                case "ingresonetovendedor" -> "ingresoNetoVendedor";
                case "ganancia" -> "ganancia";
                case "margensobreingreso" -> "margenSobreIngresoNeto";
                case "margensobrepvp" -> "margenSobrePvp";
                case "markup" -> "markupPorcentaje";
                case "cuotas" -> "cuotas";
                case "canal" -> "canal.nombre";
                case "canalid" -> "canal.id";
                case "mla" -> "producto.mla.mla";
                case "mlau" -> "producto.mla.mlau";
                case "precioenvio" -> "producto.mla.precioEnvio";
                case "comisionporcentaje" -> "producto.mla.comisionPorcentaje";
                case "margenminorista" -> "producto.productoMargenes.margenMinorista";
                case "margenmayorista" -> "producto.productoMargenes.margenMayorista";
                // Precio inflado (regla asignada)
                case "precioinflado", "precioinfladocodigo" -> "precioInfladoAsignacion.precioInflado.codigo";
                case "precioinfladotipo" -> "precioInfladoAsignacion.precioInflado.tipo";
                case "precioinfladovalor" -> "precioInfladoAsignacion.precioInflado.valor";
                // Campos de relaciones
                case "esmaquina" -> "producto.clasifGastro.esMaquina";
                // Campos de producto con camelCase
                case "fechaultimocosto" -> "producto.fechaUltimoCosto";
                case "fechaultimocalculo" -> "fechaUltimoCalculo";
                default -> "producto." + order.getProperty();
            };
            translated.add(new Sort.Order(order.getDirection(), mappedField));
        }

        return org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by(translated));
    }

    /**
     * Lista de campos que requieren ordenamiento especial (en memoria).
     * Actualmente vacía: todos los campos se ordenan via SQL en traducirSort,
     * ya que la paginación es por fila de precio (producto+canal+cuotas)
     * y cada fila tiene un valor único para cada campo.
     */
    private static final Set<String> CAMPOS_SORT_ESPECIAL = Set.of();

    /**
     * Aplica ordenamiento especial si el sort del Pageable contiene un campo especial.
     */
    private void aplicarOrdenamientoEspecial(
            List<ProductoConPreciosDTO> dtos,
            Sort sort,
            Integer canalId,
            Integer cuotas,
            Map<Integer, List<ProductoCanalPrecio>> preciosPorProducto) {

        if (sort == null || sort.isUnsorted()) {
            return;
        }

        // Buscar el primer campo de ordenamiento especial
        for (Sort.Order order : sort) {
            String campo = order.getProperty().toLowerCase();
            if (CAMPOS_SORT_ESPECIAL.contains(campo)) {
                Comparator<ProductoConPreciosDTO> comparator = getComparator(campo, canalId, cuotas, preciosPorProducto);
                if (comparator != null) {
                    boolean asc = order.isAscending();
                    dtos.sort(asc ? comparator : comparator.reversed());
                }
                break; // Solo aplicar el primer campo especial encontrado
            }
        }
    }

    /**
     * Obtiene un comparador basado en el campo de ordenamiento.
     * Para campos de precio: usa los filtros canalId/cuotas, o MAX de todos si no se especifican.
     */
    private Comparator<ProductoConPreciosDTO> getComparator(
            String sortBy,
            Integer canalId,
            Integer cuotas,
            Map<Integer, List<ProductoCanalPrecio>> preciosPorProducto) {

        return switch (sortBy.toLowerCase()) {
            // Campos de MLA
            case "mla" -> Comparator.comparing(
                    ProductoConPreciosDTO::mla,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "mlau" -> Comparator.comparing(
                    ProductoConPreciosDTO::mlau,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "comisionporcentaje" -> Comparator.comparing(
                    ProductoConPreciosDTO::comisionPorcentaje,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "precioenvio" -> Comparator.comparing(
                    ProductoConPreciosDTO::precioEnvio,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            // Campos de relaciones
            case "esmaquina" -> Comparator.comparing(
                    ProductoConPreciosDTO::esMaquina,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            // Campos de precios calculados
            case "pvp" -> crearComparadorPrecio(canalId, cuotas, preciosPorProducto, ProductoCanalPrecio::getPvp);
            case "pvpinflado" -> crearComparadorPrecio(canalId, cuotas, preciosPorProducto, ProductoCanalPrecio::getPvpInflado);
            case "costoproducto" -> crearComparadorPrecio(canalId, cuotas, preciosPorProducto, ProductoCanalPrecio::getCostoProducto);
            case "costosventa" -> crearComparadorPrecio(canalId, cuotas, preciosPorProducto, ProductoCanalPrecio::getCostosVenta);
            case "ingresonetovendedor" -> crearComparadorPrecio(canalId, cuotas, preciosPorProducto, ProductoCanalPrecio::getIngresoNetoVendedor);
            case "ganancia" -> crearComparadorPrecio(canalId, cuotas, preciosPorProducto, ProductoCanalPrecio::getGanancia);
            case "margensobreingreso" -> crearComparadorPrecio(canalId, cuotas, preciosPorProducto, ProductoCanalPrecio::getMargenSobreIngresoNeto);
            case "margensobrepvp" -> crearComparadorPrecio(canalId, cuotas, preciosPorProducto, ProductoCanalPrecio::getMargenSobrePvp);
            case "markup" -> crearComparadorPrecio(canalId, cuotas, preciosPorProducto, ProductoCanalPrecio::getMarkupPorcentaje);
            case "fechaultimocosto" -> Comparator.comparing(
                    ProductoConPreciosDTO::fechaUltimoCosto,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "fechaultimocalculo" -> crearComparadorPrecioTemporal(canalId, cuotas, preciosPorProducto, ProductoCanalPrecio::getFechaUltimoCalculo);
            default -> null;
        };
    }

    /**
     * Crea un comparador para un campo de precio.
     * Si canalId/cuotas se especifican, filtra por ellos.
     * Si no, usa el valor MAX de todos los canales/cuotas.
     */
    private Comparator<ProductoConPreciosDTO> crearComparadorPrecio(
            Integer canalId,
            Integer cuotas,
            Map<Integer, List<ProductoCanalPrecio>> preciosPorProducto,
            Function<ProductoCanalPrecio, BigDecimal> extractor) {

        return Comparator.comparing(
                (ProductoConPreciosDTO dto) -> {
                    List<ProductoCanalPrecio> precios = preciosPorProducto.get(dto.id());
                    if (precios == null || precios.isEmpty()) return null;

                    // Filtrar por canal si se especifica
                    if (canalId != null) {
                        precios = precios.stream()
                                .filter(p -> p.getCanal().getId().equals(canalId))
                                .toList();
                    }

                    // Filtrar por cuotas si se especifica
                    if (cuotas != null) {
                        precios = precios.stream()
                                .filter(p -> cuotas.equals(p.getCuotas()))
                                .toList();
                    }

                    // Obtener el MAX del campo especificado
                    return precios.stream()
                            .map(extractor)
                            .filter(v -> v != null)
                            .max(Comparator.naturalOrder())
                            .orElse(null);
                },
                Comparator.nullsLast(Comparator.naturalOrder())
        );
    }

    private Comparator<ProductoConPreciosDTO> crearComparadorPrecioTemporal(
            Integer canalId,
            Integer cuotas,
            Map<Integer, List<ProductoCanalPrecio>> preciosPorProducto,
            Function<ProductoCanalPrecio, LocalDateTime> extractor) {

        return Comparator.comparing(
                (ProductoConPreciosDTO dto) -> {
                    List<ProductoCanalPrecio> precios = preciosPorProducto.get(dto.id());
                    if (precios == null || precios.isEmpty()) return null;

                    if (canalId != null) {
                        precios = precios.stream()
                                .filter(p -> p.getCanal().getId().equals(canalId))
                                .toList();
                    }

                    if (cuotas != null) {
                        precios = precios.stream()
                                .filter(p -> cuotas.equals(p.getCuotas()))
                                .toList();
                    }

                    return precios.stream()
                            .map(extractor)
                            .filter(v -> v != null)
                            .max(Comparator.naturalOrder())
                            .orElse(null);
                },
                Comparator.nullsLast(Comparator.naturalOrder())
        );
    }

    // ============================
    // LISTAR SIN PAGINACIÓN (PARA EXPORTACIÓN)
    // ============================
    @Override
    @Transactional(readOnly = true)
    public List<ProductoConPreciosDTO> listarConPreciosSinPaginar(ProductoFilter filter, Sort sort) {

        // Validar que el canal tenga las cuotas especificadas
        validarCanalConCuotas(filter.canalId(), filter.cuotas());

        Specification<Producto> spec = Specification.allOf(
                ProductoSpecifications.productoId(filter.productoId()),
                ProductoSpecifications.textoLike(filter.search()),
                // Filtros de texto dedicados
                ProductoSpecifications.sku(filter.sku()),
                ProductoSpecifications.codExt(filter.codExt()),
                ProductoSpecifications.tituloDux(filter.tituloDux()),
                ProductoSpecifications.tituloNube(filter.tituloNube()),
                // Booleanos/Numéricos
                ProductoSpecifications.esCombo(filter.esCombo()),
                ProductoSpecifications.uxb(filter.uxb()),
                ProductoSpecifications.esMaquina(filter.esMaquina()),
                ProductoSpecifications.tieneMla(filter.tieneMla()),
                ProductoSpecifications.activo(filter.activo()),
                ProductoSpecifications.tagReposicion(filter.tagReposicion()),
                ProductoSpecifications.tags(filter.tags()),
                // Filtros MLA
                ProductoSpecifications.mla(filter.mla()),
                ProductoSpecifications.mlau(filter.mlau()),
                ProductoSpecifications.precioEnvioMin(filter.precioEnvioMin()),
                ProductoSpecifications.precioEnvioMax(filter.precioEnvioMax()),
                ProductoSpecifications.comisionPorcentajeMin(filter.comisionPorcentajeMin()),
                ProductoSpecifications.comisionPorcentajeMax(filter.comisionPorcentajeMax()),
                ProductoSpecifications.tieneComision(filter.tieneComision()),
                ProductoSpecifications.tienePrecioEnvio(filter.tienePrecioEnvio()),
                // Many-to-One (multi-valor)
                ProductoSpecifications.marcaIds(filter.marcaIds()),
                ProductoSpecifications.origenIds(filter.origenIds()),
                ProductoSpecifications.tipoIds(filter.tipoIds()),
                ProductoSpecifications.clasifGralIds(filter.clasifGralIds()),
                ProductoSpecifications.clasifGastroIds(filter.clasifGastroIds()),
                ProductoSpecifications.proveedorIds(filter.proveedorIds()),
                ProductoSpecifications.materialIds(filter.materialIds()),
                ProductoSpecifications.costoMin(filter.costoMin()),
                ProductoSpecifications.costoMax(filter.costoMax()),
                ProductoSpecifications.ivaMin(filter.ivaMin()),
                ProductoSpecifications.ivaMax(filter.ivaMax()),
                ProductoSpecifications.stockMin(filter.stockMin()),
                ProductoSpecifications.stockMax(filter.stockMax()),
                ProductoSpecifications.pvpEnRango(filter.pvpMin(), filter.pvpMax(), filter.pvpCanalId()),
                ProductoSpecifications.desdeFechaUltimoCosto(filter.desdeFechaUltimoCosto()),
                ProductoSpecifications.hastaFechaUltimoCosto(filter.hastaFechaUltimoCosto()),
                ProductoSpecifications.desdeFechaCreacion(filter.desdeFechaCreacion()),
                ProductoSpecifications.hastaFechaCreacion(filter.hastaFechaCreacion()),
                ProductoSpecifications.desdeFechaModificacion(filter.desdeFechaModificacion()),
                ProductoSpecifications.hastaFechaModificacion(filter.hastaFechaModificacion()),
                ProductoSpecifications.aptoIds(filter.aptoIds()),
                ProductoSpecifications.canalIds(filter.canalIds()),
                ProductoSpecifications.catalogoIds(filter.catalogoIds()),
                ProductoSpecifications.clienteIds(filter.clienteIds()),
                ProductoSpecifications.mlaIds(filter.mlaIds()),
                // Filtro SQL: solo productos que tengan precios para este canal/cuotas
                ProductoSpecifications.tienePreciosEnCanalCuotas(filter.canalId(), filter.cuotas())
        );

        // Obtener todos los productos (sin paginación)
        List<Producto> productos = productoRepository.findAll(spec);

        if (productos.isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> productoIds = productos.stream()
                .map(Producto::getId)
                .toList();

        // Obtener todos los precios
        List<ProductoCanalPrecio> todosPrecios = productoCanalPrecioRepository
                .findByProductoIdInOrderByProductoIdAscCanalIdAscCuotasAsc(productoIds);

        Map<Integer, List<ProductoCanalPrecio>> preciosPorProducto = todosPrecios.stream()
                .collect(Collectors.groupingBy(pcp -> pcp.getProducto().getId()));

        // Obtener todos los márgenes
        List<ProductoMargen> todosMargenes = productoMargenRepository.findByProductoIdIn(productoIds);
        Map<Integer, ProductoMargen> margenesPorProducto = todosMargenes.stream()
                .collect(Collectors.toMap(pm -> pm.getProducto().getId(), pm -> pm));

        // Obtener IDs de canales y sus nombres
        Set<Integer> canalIds = todosPrecios.stream()
                .map(p -> p.getCanal().getId())
                .collect(Collectors.toSet());

        // Cargar nombres de canales explícitamente (evita problemas de lazy loading)
        Map<Integer, String> nombresPorCanal = canalRepository.findAllById(canalIds).stream()
                .collect(Collectors.toMap(Canal::getId, Canal::getNombre));

        // Obtener descripciones de cuotas por canal (para PrecioDTO.descripcion)
        Map<String, String> descripcionesCuotas = canalConceptoCuotaRepository.findByCanalIdIn(canalIds).stream()
                .filter(c -> c.getDescripcion() != null)
                .collect(Collectors.toMap(
                        c -> c.getCanal().getId() + "_" + c.getCuotas(),
                        CanalConceptoCuota::getDescripcion,
                        (a, b) -> a
                ));

        // Obtener reglas de descuento por canal (siempre se cargan si existen)
        Map<Integer, List<ReglaDescuento>> reglasPorCanal = new HashMap<>();
        for (Integer canalId : canalIds) {
            List<ReglaDescuento> reglas = reglaDescuentoRepository
                    .findByCanalIdAndActivoTrueOrderByPrioridadAsc(canalId);
            if (!reglas.isEmpty()) {
                reglasPorCanal.put(canalId, reglas);
            }
        }

        // Mapear a DTOs
        List<ProductoConPreciosDTO> dtos = productos.stream()
                .map(producto -> {
                    ProductoMargen productoMargen = margenesPorProducto.get(producto.getId());
                    List<ProductoCanalPrecio> precios = preciosPorProducto
                            .getOrDefault(producto.getId(), Collections.emptyList());

                    if (filter.canalId() != null) {
                        precios = precios.stream()
                                .filter(p -> p.getCanal().getId().equals(filter.canalId()))
                                .toList();
                    } else if (filter.canalIds() != null && !filter.canalIds().isEmpty()) {
                        precios = precios.stream()
                                .filter(p -> filter.canalIds().contains(p.getCanal().getId()))
                                .toList();
                    }

                    // Filtrar por cuotas si se especifica
                    if (filter.cuotas() != null) {
                        precios = precios.stream()
                                .filter(p -> filter.cuotas().equals(p.getCuotas()))
                                .toList();
                    }

                    // Calcular descuentos aplicables por canal (siempre si hay reglas)
                    Map<Integer, List<DescuentoAplicableDTO>> descuentosPorCanal = null;
                    if (!precios.isEmpty() && !reglasPorCanal.isEmpty()) {
                        descuentosPorCanal = calcularDescuentosPorCanal(precios, reglasPorCanal);
                    }

                    return productoMapper.toProductoConPreciosDTO(producto, productoMargen, precios, descripcionesCuotas, descuentosPorCanal, nombresPorCanal);
                })
                .collect(Collectors.toCollection(ArrayList::new));

        // Aplicar ordenamiento especial si se solicita
        aplicarOrdenamientoEspecial(dtos, sort, filter.canalId(), filter.cuotas(), preciosPorProducto);

        return dtos;
    }

    // ============================
    // ACTUALIZAR COSTO + RECALCULAR PRECIOS
    // ============================
    @Override
    @Transactional
    public void actualizarCosto(Integer productoId, BigDecimal nuevoCosto) {

        // 1) Actualizar costo del producto
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));

        producto.setCosto(nuevoCosto);
        productoRepository.save(producto);

        // 2) Recalcular precios en todos los canales
        programarRecalculoPostCommit("Cambio en producto", productoId);
    }

    // ============================
    // VALIDACIÓN DE CANAL CON CUOTAS
    // ============================
    private void validarCanalConCuotas(Integer canalId, Integer cuotas) {
        if (canalId != null && cuotas != null) {
            boolean existenPrecios = productoCanalPrecioRepository.existsByCanalIdAndCuotas(canalId, cuotas);
            if (!existenPrecios) {
                String nombreCanal = canalRepository.findById(canalId)
                        .map(c -> c.getNombre())
                        .orElse("ID " + canalId);
                throw new IllegalArgumentException(
                        String.format("El canal '%s' no tiene precios configurados para %d cuotas", nombreCanal, cuotas));
            }
        }
    }

    private Page<ProductoDTO> mapProductosPage(Page<Producto> page, Pageable pageable) {
        if (page.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        List<Integer> ids = page.getContent().stream()
                .map(Producto::getId)
                .toList();

        Map<Integer, Producto> productosConRelaciones = productoRepository.findAllByIdWithRelaciones(ids).stream()
                .collect(Collectors.toMap(Producto::getId, p -> p));

        Map<Integer, List<String>> aptosPorProducto = productoAptoRepository.findByProductoIdInWithApto(ids).stream()
                .collect(Collectors.groupingBy(
                        pa -> pa.getProducto().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(pa -> pa.getApto().getNombre(), Collectors.toList())
                ));

        Map<Integer, List<String>> catalogosPorProducto = productoCatalogoRepository.findByProductoIdInWithCatalogo(ids).stream()
                .collect(Collectors.groupingBy(
                        pc -> pc.getProducto().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(pc -> pc.getCatalogo().getNombre(), Collectors.toList())
                ));

        Map<Integer, List<String>> clientesPorProducto = productoClienteRepository.findByProductoIdInWithCliente(ids).stream()
                .collect(Collectors.groupingBy(
                        pc -> pc.getProducto().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(pc -> pc.getCliente().getNombre(), Collectors.toList())
                ));

        Map<Integer, ProductoMargen> margenesPorProducto = productoMargenRepository.findByProductoIdIn(ids).stream()
                .collect(Collectors.toMap(pm -> pm.getProducto().getId(), pm -> pm, (a, b) -> a));

        List<ProductoDTO> dtos = ids.stream()
                .map(productosConRelaciones::get)
                .filter(Objects::nonNull)
                .map(producto -> productoMapper.toDTO(
                        producto,
                        aptosPorProducto.getOrDefault(producto.getId(), List.of()),
                        catalogosPorProducto.getOrDefault(producto.getId(), List.of()),
                        clientesPorProducto.getOrDefault(producto.getId(), List.of()),
                        margenesPorProducto.get(producto.getId())
                ))
                .toList();

        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    private boolean isPatchVacio(ProductoPatchDTO patchDto) {
        return !presente(patchDto.getSku())
                && !presente(patchDto.getCodExt())
                && !presente(patchDto.getTituloDux())
                && !presente(patchDto.getTituloMl())
                && !presente(patchDto.getTituloNube())
                && !presente(patchDto.getEsCombo())
                && !presente(patchDto.getUxb())
                && !presente(patchDto.getMoq())
                && !presente(patchDto.getImagenUrl())
                && !presente(patchDto.getStock())
                && !presente(patchDto.getActivo())
                && !presente(patchDto.getMarcaId())
                && !presente(patchDto.getOrigenId())
                && !presente(patchDto.getClasifGralId())
                && !presente(patchDto.getClasifGastroId())
                && !presente(patchDto.getTipoId())
                && !presente(patchDto.getProveedorId())
                && !presente(patchDto.getMaterialId())
                && !presente(patchDto.getMlaId())
                && !presente(patchDto.getSectorDepositoId())
                && !presente(patchDto.getCapacidad())
                && !presente(patchDto.getLargo())
                && !presente(patchDto.getAncho())
                && !presente(patchDto.getAlto())
                && !presente(patchDto.getDiamboca())
                && !presente(patchDto.getDiambase())
                && !presente(patchDto.getEspesor())
                && !presente(patchDto.getCosto())
                && !presente(patchDto.getIva())
                && !presente(patchDto.getTagReposicion())
                && !presente(patchDto.getTag());
    }

    /**
     * Regla de negocio: un producto debe tener al menos una clasificación
     * (general o gastronómica). Se valida sobre la entidad ya armada, cubriendo
     * crear, actualizar y patch.
     */
    private void validarAlMenosUnaClasificacion(Producto entity) {
        if (entity.getClasifGral() == null && entity.getClasifGastro() == null) {
            throw new BadRequestException(
                    "El producto debe tener al menos una clasificación: general o gastronómica.");
        }
    }

    /**
     * Regla de negocio: un producto SIMPLE (no combo) debe tener marca, origen,
     * proveedor, material y tag. En combos siguen siendo opcionales.
     */
    private void validarProductoSimpleCompleto(Producto entity) {
        if (Boolean.TRUE.equals(entity.getEsCombo())) {
            return; // los combos no exigen estos campos
        }
        if (entity.getMarca() == null) throw new BadRequestException("La marca es obligatoria para productos simples.");
        if (entity.getOrigen() == null) throw new BadRequestException("El origen es obligatorio para productos simples.");
        if (entity.getProveedor() == null) throw new BadRequestException("El proveedor es obligatorio para productos simples.");
        if (entity.getMaterial() == null) throw new BadRequestException("El material es obligatorio para productos simples.");
        if (entity.getTag() == null) throw new BadRequestException("El tag es obligatorio para productos simples.");
    }

    private void aplicarPatch(Producto entity, ProductoPatchDTO patchDto) {
        if (presente(patchDto.getSku())) {
            entity.setSku(leerStringRequerido(patchDto.getSku(), "sku", 45));
        }
        if (presente(patchDto.getCodExt())) {
            entity.setCodExt(leerStringOpcional(patchDto.getCodExt(), "codExt", 45));
        }
        if (presente(patchDto.getTituloDux())) {
            entity.setTituloDux(leerStringRequerido(patchDto.getTituloDux(), "tituloDux", 100));
        }
        if (presente(patchDto.getTituloMl())) {
            entity.setTituloMl(leerStringOpcional(patchDto.getTituloMl(), "tituloMl", 100));
        }
        if (presente(patchDto.getMlCategoryId())) {
            entity.setMlCategoryId(leerStringOpcional(patchDto.getMlCategoryId(), "mlCategoryId", 20));
        }
        if (presente(patchDto.getMlCategoryNombre())) {
            entity.setMlCategoryNombre(leerStringOpcional(patchDto.getMlCategoryNombre(), "mlCategoryNombre", 255));
        }
        if (presente(patchDto.getTituloNube())) {
            entity.setTituloNube(leerStringOpcional(patchDto.getTituloNube(), "tituloNube", 100));
        }
        if (presente(patchDto.getEsCombo())) {
            entity.setEsCombo(leerBooleanOpcional(patchDto.getEsCombo(), "esCombo"));
        }
        if (presente(patchDto.getUxb())) {
            entity.setUxb(leerIntegerPositivoOpcional(patchDto.getUxb(), "uxb"));
        }
        if (presente(patchDto.getMoq())) {
            entity.setMoq(leerIntegerPositivoOpcional(patchDto.getMoq(), "moq"));
        }
        if (presente(patchDto.getImagenUrl())) {
            entity.setImagenUrl(leerStringOpcional(patchDto.getImagenUrl(), "imagenUrl", 500));
        }
        if (presente(patchDto.getStock())) {
            entity.setStock(leerIntegerNoNegativoOpcional(patchDto.getStock(), "stock"));
        }
        if (presente(patchDto.getActivo())) {
            entity.setActivo(leerBooleanRequerido(patchDto.getActivo(), "activo"));
        }
        if (presente(patchDto.getMarcaId())) {
            Integer marcaId = leerIdOpcional(patchDto.getMarcaId(), "marcaId");
            entity.setMarca(marcaId != null ? new Marca(marcaId) : null);
        }
        if (presente(patchDto.getOrigenId())) {
            Integer origenId = leerIdOpcional(patchDto.getOrigenId(), "origenId");
            entity.setOrigen(origenId != null ? new Origen(origenId) : null);
        }
        if (presente(patchDto.getClasifGralId())) {
            Integer clasifGralId = leerIdOpcional(patchDto.getClasifGralId(), "clasifGralId");
            entity.setClasifGral(clasifGralId != null ? new ClasifGral(clasifGralId) : null);
        }
        if (presente(patchDto.getClasifGastroId())) {
            Integer clasifGastroId = leerIdOpcional(patchDto.getClasifGastroId(), "clasifGastroId");
            entity.setClasifGastro(clasifGastroId != null ? new ClasifGastro(clasifGastroId) : null);
        }
        if (presente(patchDto.getTipoId())) {
            entity.setTipo(new Tipo(leerIdRequerido(patchDto.getTipoId(), "tipoId")));
        }
        if (presente(patchDto.getProveedorId())) {
            Integer proveedorId = leerIdOpcional(patchDto.getProveedorId(), "proveedorId");
            entity.setProveedor(proveedorId != null ? new Proveedor(proveedorId) : null);
        }
        if (presente(patchDto.getMaterialId())) {
            Integer materialId = leerIdOpcional(patchDto.getMaterialId(), "materialId");
            entity.setMaterial(materialId != null ? new Material(materialId) : null);
        }
        if (presente(patchDto.getMlaId())) {
            Integer mlaId = leerIdOpcional(patchDto.getMlaId(), "mlaId");
            entity.setMla(crearReferenciaMla(mlaId));
        }
        if (presente(patchDto.getSectorDepositoId())) {
            Integer sectorDepositoId = leerIdOpcional(patchDto.getSectorDepositoId(), "sectorDepositoId");
            entity.setSectorDeposito(sectorDepositoId != null ? new SectorDeposito(sectorDepositoId) : null);
        }
        if (presente(patchDto.getCapacidad())) {
            entity.setCapacidad(leerStringOpcional(patchDto.getCapacidad(), "capacidad", 45));
        }
        if (presente(patchDto.getLargo())) {
            entity.setLargo(leerStringOpcional(patchDto.getLargo(), "largo", 45));
        }
        if (presente(patchDto.getAncho())) {
            entity.setAncho(leerStringOpcional(patchDto.getAncho(), "ancho", 45));
        }
        if (presente(patchDto.getAlto())) {
            entity.setAlto(leerStringOpcional(patchDto.getAlto(), "alto", 45));
        }
        if (presente(patchDto.getDiamboca())) {
            entity.setDiamboca(leerStringOpcional(patchDto.getDiamboca(), "diamboca", 45));
        }
        if (presente(patchDto.getDiambase())) {
            entity.setDiambase(leerStringOpcional(patchDto.getDiambase(), "diambase", 45));
        }
        if (presente(patchDto.getEspesor())) {
            entity.setEspesor(leerStringOpcional(patchDto.getEspesor(), "espesor", 45));
        }
        if (presente(patchDto.getCosto())) {
            entity.setCosto(leerDecimalNoNegativoOpcional(patchDto.getCosto(), "costo"));
        }
        if (presente(patchDto.getIva())) {
            entity.setIva(leerIvaRequerido(patchDto.getIva(), "iva"));
        }
        if (presente(patchDto.getTagReposicion())) {
            entity.setTagReposicion(leerEnumOpcional(patchDto.getTagReposicion(), "tagReposicion", TagReposicion.class));
        }
        if (presente(patchDto.getTag())) {
            entity.setTag(leerEnumOpcional(patchDto.getTag(), "tag", Tag.class));
        }
    }

    private Mla crearReferenciaMla(Integer mlaId) {
        if (mlaId == null) {
            return null;
        }
        Mla mla = new Mla();
        mla.setId(mlaId);
        return mla;
    }

    /** Específico: igual al porcentaje [0, 100] pero requerido (no opcional), usado para el IVA del producto. */
    private BigDecimal leerIvaRequerido(JsonNullable<BigDecimal> campo, String field) {
        return leerPorcentajeRequerido(campo, field);
    }

    // ============================
    // CÁLCULO DE DESCUENTOS APLICABLES
    // ============================

    /**
     * Calcula los descuentos aplicables por canal basado en las reglas de descuento.
     * Para cada canal con reglas, calcula cómo quedaría el PVP y la ganancia con cada nivel de descuento.
     *
     * @param precios Lista de precios del producto
     * @param reglasPorCanal Mapa de canalId -> reglas de descuento activas ordenadas por prioridad
     * @return Mapa de canalId -> lista de descuentos aplicables
     */
    private Map<Integer, List<DescuentoAplicableDTO>> calcularDescuentosPorCanal(
            List<ProductoCanalPrecio> precios,
            Map<Integer, List<ReglaDescuento>> reglasPorCanal) {

        if (reglasPorCanal.isEmpty()) {
            return null;
        }

        Map<Integer, List<DescuentoAplicableDTO>> resultado = new HashMap<>();

        // Agrupar precios por canal
        Map<Integer, List<ProductoCanalPrecio>> preciosPorCanal = precios.stream()
                .collect(Collectors.groupingBy(p -> p.getCanal().getId()));

        for (Map.Entry<Integer, List<ReglaDescuento>> entry : reglasPorCanal.entrySet()) {
            Integer canalId = entry.getKey();
            List<ReglaDescuento> reglas = entry.getValue();

            // Obtener el primer precio del canal para calcular descuentos
            // (usamos el precio base, típicamente contado cuotas=0)
            List<ProductoCanalPrecio> preciosCanal = preciosPorCanal.get(canalId);
            if (preciosCanal == null || preciosCanal.isEmpty()) {
                continue;
            }

            // Buscar precio de contado (cuotas=0) o el primero disponible
            ProductoCanalPrecio precioBase = preciosCanal.stream()
                    .filter(p -> p.getCuotas() != null && p.getCuotas() == 0)
                    .findFirst()
                    .orElse(preciosCanal.get(0));

            BigDecimal pvp = precioBase.getPvp();
            BigDecimal costoProducto = precioBase.getCostoProducto();

            if (pvp == null || pvp.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            List<DescuentoAplicableDTO> descuentos = new ArrayList<>();

            for (ReglaDescuento regla : reglas) {
                BigDecimal descuentoPct = regla.getDescuentoPorcentaje();
                BigDecimal montoMinimo = regla.getMontoMinimo();

                // Calcular PVP con descuento (descuento real = resta)
                // pvpConDescuento = pvp * (1 - descuento/100)
                BigDecimal factorDescuento = BigDecimal.ONE.subtract(
                        descuentoPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
                BigDecimal pvpConDescuento = pvp.multiply(factorDescuento)
                        .setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);

                // Calcular métricas con descuento
                BigDecimal gananciaOriginal = precioBase.getGanancia();
                BigDecimal costosVentaConDescuento = BigDecimal.ZERO;
                BigDecimal ingresoNetoConDescuento = BigDecimal.ZERO;
                BigDecimal gananciaConDescuento = BigDecimal.ZERO;
                BigDecimal margenSobreIngresoNetoConDescuento = BigDecimal.ZERO;
                BigDecimal margenSobrePvpConDescuento = BigDecimal.ZERO;
                BigDecimal markupConDescuento = BigDecimal.ZERO;

                if (gananciaOriginal != null && costoProducto != null) {
                    BigDecimal ingresoNetoOriginal = precioBase.getIngresoNetoVendedor();
                    if (ingresoNetoOriginal != null && ingresoNetoOriginal.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal proporcionIngreso = ingresoNetoOriginal.divide(pvp, 6, RoundingMode.HALF_UP);
                        ingresoNetoConDescuento = pvpConDescuento.multiply(proporcionIngreso)
                                .setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);
                        costosVentaConDescuento = pvpConDescuento.subtract(ingresoNetoConDescuento)
                                .setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);
                        gananciaConDescuento = ingresoNetoConDescuento.subtract(costoProducto)
                                .setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);

                        if (ingresoNetoConDescuento.compareTo(BigDecimal.ZERO) > 0) {
                            margenSobreIngresoNetoConDescuento = gananciaConDescuento.multiply(BigDecimal.valueOf(100))
                                    .divide(ingresoNetoConDescuento, PRECISION_RESULTADO, RoundingMode.HALF_UP);
                        }
                        if (pvpConDescuento.compareTo(BigDecimal.ZERO) > 0) {
                            margenSobrePvpConDescuento = gananciaConDescuento.multiply(BigDecimal.valueOf(100))
                                    .divide(pvpConDescuento, PRECISION_RESULTADO, RoundingMode.HALF_UP);
                        }
                        if (costoProducto.compareTo(BigDecimal.ZERO) > 0) {
                            markupConDescuento = gananciaConDescuento.multiply(BigDecimal.valueOf(100))
                                    .divide(costoProducto, PRECISION_RESULTADO, RoundingMode.HALF_UP);
                        }
                    }
                }

                descuentos.add(new DescuentoAplicableDTO(
                        montoMinimo,
                        descuentoPct,
                        pvpConDescuento,
                        costosVentaConDescuento,
                        ingresoNetoConDescuento,
                        gananciaConDescuento,
                        margenSobreIngresoNetoConDescuento,
                        margenSobrePvpConDescuento,
                        markupConDescuento
                ));
            }

            if (!descuentos.isEmpty()) {
                resultado.put(canalId, descuentos);
            }
        }

        return resultado.isEmpty() ? null : resultado;
    }

    private void programarRecalculoPostCommit(String descripcion, Integer productoId) {
        // marcarProductoOCalcularInicial: si el producto aún no tiene precios calculados
        // pero ahora tiene los datos mínimos (costo + iva + margen), recalcula inmediatamente
        // para crear las filas iniciales en producto_canal_precios. Sin esto, el cambio
        // que justamente desbloquea el cálculo (e.g. primer costo, primer iva) marcaría
        // 0 filas obsoletas y el banner quedaría vacío.
        recalculoPendienteService.marcarProductoOCalcularInicial(descripcion, productoId);
    }

}







package ar.com.leo.super_master_backend.dominio.producto.calculo.service;

import ar.com.leo.super_master_backend.dominio.canal.entity.*;
import ar.com.leo.super_master_backend.dominio.canal.repository.*;
import ar.com.leo.super_master_backend.dominio.canal.service.CanalReglaServiceImpl;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.common.dto.ProcesoMasivoEstadoDTO;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.common.service.EstadoProcesoMasivo;
import ar.com.leo.super_master_backend.dominio.common.service.ProcesoGlobalService;
import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.AplicaSobre;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.ConceptoCalculo;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.NaturalezaConcepto;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.precio_inflado.entity.PrecioInflado;
import ar.com.leo.super_master_backend.dominio.precio_inflado.entity.TipoPrecioInflado;
import ar.com.leo.super_master_backend.dominio.producto.calculo.dto.*;
import ar.com.leo.super_master_backend.dominio.producto.dto.CanalPreciosDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.DescuentoAplicableDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.PrecioDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.*;
import ar.com.leo.super_master_backend.dominio.producto.mla.entity.Mla;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoCanalPrecioInfladoRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoCanalPrecioRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoMargenRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import ar.com.leo.super_master_backend.dominio.proveedor.entity.Proveedor;
import ar.com.leo.super_master_backend.dominio.regla_descuento.entity.ReglaDescuento;
import ar.com.leo.super_master_backend.dominio.regla_descuento.repository.ReglaDescuentoRepository;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalculoPrecioServiceImpl implements CalculoPrecioService {

    private final ProductoRepository productoRepository;
    private final ProductoMargenRepository productoMargenRepository;
    private final ProductoCanalPrecioRepository productoCanalPrecioRepository;
    private final ProductoCanalPrecioInfladoRepository productoCanalPrecioInfladoRepository;
    private final CanalRepository canalRepository;
    private final CanalConceptoRepository canalConceptoRepository;
    private final CanalConceptoReglaRepository canalConceptoReglaRepository;
    private final CanalReglaRepository canalReglaRepository;
    private final CanalConceptoCuotaRepository canalConceptoCuotaRepository;
    private final ReglaDescuentoRepository reglaDescuentoRepository;
    private final ProcesoGlobalService procesoGlobal;

    @PersistenceContext
    private EntityManager entityManager;

    @Lazy @Autowired
    private CalculoPrecioServiceImpl self;

    @Lazy @Autowired
    private RecalculoPendienteService recalculoPendienteService;

    // Control de recálculo masivo async (estado + locks vía tracker reusable)
    private static final String PROCESO_ID = "recalculo-precios";
    private static final String PROCESO_DESC = "Recálculo masivo de precios";
    private EstadoProcesoMasivo tracker;
    private volatile RecalculoMasivoResultDTO resultadoRecalculo = null;

    @PostConstruct
    void initTracker() {
        this.tracker = new EstadoProcesoMasivo(PROCESO_ID, PROCESO_DESC, procesoGlobal);
    }

    // ====================================================
    // CONSTANTES
    // ====================================================
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final int PRECISION_CALCULO = 6;
    private static final int PRECISION_RESULTADO = 2;

    // Rango representable por las columnas DECIMAL(6,2) de margen/markup en producto_canal_precios.
    // Casos degenerados (ingreso neto o costo casi nulos, productos a pérdida) producen
    // porcentajes enormes que no caben en la columna y rompían el guardado con data truncation
    // (SQLState 22001). Se acotan a este rango para no perder el resto del recálculo.
    private static final BigDecimal MARGEN_MAX = new BigDecimal("9999.99");
    private static final BigDecimal MARGEN_MIN = new BigDecimal("-9999.99");

    /** Acota un porcentaje de margen/markup al rango DECIMAL(6,2) de la columna. */
    private static BigDecimal clampMargen(BigDecimal valor) {
        if (valor == null) return null;
        if (valor.compareTo(MARGEN_MAX) > 0) return MARGEN_MAX;
        if (valor.compareTo(MARGEN_MIN) < 0) return MARGEN_MIN;
        return valor;
    }

    // ====================================================
    // CACHE PARA RECÁLCULO MASIVO (ThreadLocal para evitar problemas de concurrencia)
    // ====================================================
    private static final ThreadLocal<Map<String, BigDecimal>> CACHE_PORCENTAJE_CUOTAS = new ThreadLocal<>();
    // Cache de precios calculados para canales base (clave: "productoId-canalId-cuotas")
    private static final ThreadLocal<Map<String, PrecioCalculadoDTO>> CACHE_PRECIOS_BASE = new ThreadLocal<>();
    // Cache de precios inflados por producto-canal (clave: "productoId-canalId")
    private static final ThreadLocal<Map<String, ProductoCanalPrecioInflado>> CACHE_PRECIOS_INFLADOS = new ThreadLocal<>();
    // Cache de reglas de descuento por canal (recálculo masivo): evita re-consultar las
    // reglas del canal en cada calcularDescuentosAplicables (una por producto y cuota).
    private static final ThreadLocal<Map<Integer, List<ReglaDescuento>>> CACHE_REGLAS_DESCUENTO = new ThreadLocal<>();

    // ====================================================
    // CACHE PARA RECÁLCULO INLINE DE UN CANAL (ThreadLocal)
    // ====================================================
    // Memoiza las queries CANAL-CONSTANTES (reglas, conceptos, cuotas, canalBase, reglas de
    // descuento) durante el recálculo de un canal completo: el canalId es fijo para los ~5650
    // productos del catálogo, así que estas listas se cargan UNA vez en lugar de re-consultarse
    // por producto y por cuota.
    // Clave: canalId (el canal directo y los de su cadena de canalBase se memoizan on-demand).
    // Solo lo setea el flujo inline; los demás callers ven null y caen al camino normal a BD.
    private static final ThreadLocal<Map<Integer, ContextoCanalCache>> CACHE_CONTEXTO_CANAL = new ThreadLocal<>();
    // Profundidad de anidamiento de iniciar/limpiar: permite que un loop externo (fase de
    // productos) mantenga el caché vivo mientras cada recalcularProductoEnTodosLosCanales
    // anidado lo "abre" y "cierra" sin destruirlo. El caché real se crea en depth 0→1 y se
    // libera recién en 1→0.
    private static final ThreadLocal<Integer> CACHE_CONTEXTO_CANAL_DEPTH = new ThreadLocal<>();

    /**
     * Snapshot canal-constante para el recálculo inline. Guarda entidades ya cargadas con
     * JOIN FETCH (seguras de leer aunque queden detached tras el flush/clear periódico del
     * recálculo) y {@code canalBaseId} plano para no tocar el proxy lazy {@code Canal.canalBase}.
     * El {@code canal} se guarda con su {@code canalBase} pre-inicializado (ver cargarContextoCanal).
     */
    private record ContextoCanalCache(
            Integer canalId,
            Canal canal,
            Integer canalBaseId,
            List<CanalRegla> reglasCanal,
            List<CanalConcepto> conceptosCanal,
            List<CanalConceptoRegla> reglasConcepto,
            List<CanalConceptoCuota> cuotas,
            List<ReglaDescuento> reglasDescuento) {}

    // ====================================================
    // API PÚBLICA
    // ====================================================

    /**
     * Contexto cargado una sola vez para alimentar {@link #calcularPrecioUnificado}.
     * Si {@code canal} tiene canalBase o algún concepto aplica SOBRE_PVP_BASE,
     * {@code productoMargen} queda null porque ese flujo no lo necesita.
     */
    private record ContextoCalculo(
            Producto producto,
            Canal canal,
            List<CanalConcepto> conceptosCanal,
            ProductoMargen productoMargen) {}

    private ContextoCalculo prepararContexto(Producto producto, Integer canalId, Integer numeroCuotas) {
        return prepararContexto(producto, canalId, numeroCuotas, null);
    }

    /**
     * @param margenPreCargado margen ya consultado por el caller (para evitar una 2da query
     *        del mismo margen en el flujo de recálculo); {@code null} = no precargado (se
     *        consulta acá). Se mantiene la semántica original: si el canal usa margen y el
     *        producto no lo tiene, se lanza {@link NotFoundException}.
     */
    private ContextoCalculo prepararContexto(Producto producto, Integer canalId, Integer numeroCuotas,
                                             Optional<ProductoMargen> margenPreCargado) {
        List<ConceptoCalculo> conceptos = obtenerConceptosAplicables(canalId, numeroCuotas, producto);
        List<CanalConcepto> conceptosCanal = convertirConceptosACanalConcepto(conceptos, canalId);

        ContextoCanalCache ctxCanal = obtenerContextoCanal(canalId);
        Canal canal = ctxCanal != null ? ctxCanal.canal() : canalRepository.findById(canalId).orElse(null);
        boolean tieneCanalBase = ctxCanal != null
                ? ctxCanal.canalBaseId() != null
                : (canal != null && canal.getCanalBase() != null);
        boolean usaSobrePvpBase = tieneCanalBase || conceptos.stream()
                .anyMatch(c -> c.getAplicaSobre() != null && c.getAplicaSobre().esCalculoSobreCanalBase());

        ProductoMargen productoMargen;
        if (usaSobrePvpBase) {
            productoMargen = null;
        } else if (margenPreCargado != null) {
            productoMargen = margenPreCargado.orElseThrow(
                    () -> new NotFoundException("No existe configuración de márgenes para este producto"));
        } else {
            productoMargen = obtenerProductoMargen(producto.getId());
        }

        return new ContextoCalculo(producto, canal, conceptosCanal, productoMargen);
    }

    // ====================================================
    // CACHE DE CONTEXTO DE CANAL (recálculo inline)
    // ====================================================

    /**
     * Devuelve el contexto canal-constante si el caché está activo (flujo inline), o
     * {@code null} para que el caller use el camino normal a BD. Memoiza por canalId;
     * los canales de la cadena de canalBase se cargan on-demand cuando el cálculo los pide.
     */
    private ContextoCanalCache obtenerContextoCanal(Integer canalId) {
        Map<Integer, ContextoCanalCache> cache = CACHE_CONTEXTO_CANAL.get();
        if (cache == null || canalId == null) return null;
        return cache.computeIfAbsent(canalId, this::cargarContextoCanal);
    }

    /**
     * Carga (una vez) las queries canal-constantes. El proxy {@code canalBase} se inicializa
     * en sesión viva para que sobreviva a los em.clear() del recálculo.
     */
    private ContextoCanalCache cargarContextoCanal(Integer canalId) {
        Canal canal = canalRepository.findById(canalId).orElse(null);
        Integer canalBaseId = null;
        if (canal != null && canal.getCanalBase() != null) {
            canalBaseId = canal.getCanalBase().getId();
            canal.getCanalBase().getNombre(); // fuerza init del proxy lazy mientras hay sesión
        }
        return new ContextoCanalCache(
                canalId,
                canal,
                canalBaseId,
                canalReglaRepository.findByCanalIdWithRelationsFetch(canalId),
                canalConceptoRepository.findByCanalIdWithConceptoFetch(canalId),
                canalConceptoReglaRepository.findByCanalId(canalId),
                canalConceptoCuotaRepository.findByCanalId(canalId),
                reglaDescuentoRepository.findByCanalIdAndActivoTrueOrderByPrioridadAsc(canalId));
    }

    @Override
    public void iniciarCacheContextoCanal(Integer canalId) {
        int depth = CACHE_CONTEXTO_CANAL_DEPTH.get() == null ? 0 : CACHE_CONTEXTO_CANAL_DEPTH.get();
        if (depth == 0) {
            CACHE_CONTEXTO_CANAL.set(new HashMap<>());
        }
        CACHE_CONTEXTO_CANAL_DEPTH.set(depth + 1);
        obtenerContextoCanal(canalId); // precarga el canal directo (no-op si canalId es null)
    }

    @Override
    public void limpiarCacheContextoCanal() {
        Integer actual = CACHE_CONTEXTO_CANAL_DEPTH.get();
        int depth = (actual == null ? 0 : actual) - 1;
        if (depth <= 0) {
            CACHE_CONTEXTO_CANAL.remove();
            CACHE_CONTEXTO_CANAL_DEPTH.remove();
        } else {
            CACHE_CONTEXTO_CANAL_DEPTH.set(depth);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PrecioCalculadoDTO calcularPrecioCanal(Integer productoId, Integer canalId, Integer numeroCuotas) {
        Producto producto = obtenerProducto(productoId);

        if (!productoAplicaAlCanal(producto, canalId)) {
            throw new BadRequestException("El producto no aplica al canal según sus reglas de exclusión");
        }

        ContextoCalculo ctx = prepararContexto(producto, canalId, numeroCuotas);
        return calcularPrecioUnificado(ctx.producto(), ctx.productoMargen(), ctx.conceptosCanal(),
                numeroCuotas, canalId, ctx.canal(), null);
    }

    /**
     * {@code noRollbackFor}: callers como {@code MercadoLibreService.calcularCostoEnvioGratis}
     * iteran este método en un bucle dentro de su propia @Transactional y atrapan estas
     * excepciones esperadas (producto sin margen, sin costo, excluido por canal_regla) para
     * traducirlas a un DTO con mensaje legible. Sin {@code noRollbackFor}, esta TX interna
     * marcaría la TX compartida como rollback-only y el commit del método externo tiraría
     * UnexpectedRollbackException, opacando el mensaje original.
     */
    @Override
    @Transactional(readOnly = true, noRollbackFor = {NotFoundException.class, BadRequestException.class})
    public PrecioCalculadoDTO calcularPrecioCanalConEnvio(Integer productoId, Integer canalId, Integer numeroCuotas, BigDecimal precioEnvioOverride) {
        Producto producto = obtenerProducto(productoId);

        if (!productoAplicaAlCanal(producto, canalId)) {
            throw new BadRequestException("El producto no aplica al canal según sus reglas de exclusión");
        }

        ContextoCalculo ctx = prepararContexto(producto, canalId, numeroCuotas);
        return calcularPrecioUnificado(ctx.producto(), ctx.productoMargen(), ctx.conceptosCanal(),
                numeroCuotas, canalId, ctx.canal(), precioEnvioOverride);
    }

    /**
     * {@code noRollbackFor}: las validaciones esperadas (producto sin costo/iva/margen,
     * canal sin conceptos) tiran estas excepciones ANTES de cualquier escritura crítica.
     * Sin {@code noRollbackFor}, callers en bulk (ExcelImport, DuxImport, MercadoLibre,
     * RecalculoBulkExecutor) que atrapan la excepción y continúan al siguiente item
     * reciben {@code UnexpectedRollbackException} al commit, opacando el motivo real.
     */
    @Override
    @Transactional(noRollbackFor = {NotFoundException.class, BadRequestException.class})
    public PrecioCalculadoDTO recalcularYGuardarPrecioCanal(Integer productoId, Integer canalId, Integer numeroCuotas) {
        Producto producto = obtenerProducto(productoId);

        // Excluir el canal cuando: (a) el producto no aplica por canal_regla, o (b) el canal
        // requiere un margen mayorista/minorista que el producto no tiene (null o ≤ 0).
        // En ambos casos, borrar el precio existente si hay y no calcular.
        boolean excluido = !productoAplicaAlCanal(producto, canalId);
        Optional<ProductoMargen> margenOpt = Optional.empty();
        if (!excluido) {
            margenOpt = productoMargenRepository.findByProductoId(productoId);
            if (!tieneMargenParaCadenaCanal(margenOpt.orElse(null), canalId)) {
                excluido = true;
            }
        }
        if (excluido) {
            productoCanalPrecioRepository
                    .findByProductoIdAndCanalIdAndCuotas(productoId, canalId, numeroCuotas)
                    .ifPresent(productoCanalPrecioRepository::delete);
            return null;
        }

        // Reutiliza el margen ya consultado para el chequeo (evita una 2da query en prepararContexto).
        ContextoCalculo ctx = prepararContexto(producto, canalId, numeroCuotas, margenOpt);
        return recalcularYGuardarPrecioCanalInterno(producto, ctx, canalId, numeroCuotas);
    }

    /**
     * Calcula y persiste el precio de UNA cuota reutilizando el producto y el contexto del
     * canal ya cargados (sin re-consultar producto, margen ni conceptos). Asume que la
     * exclusión por canal_regla / margen ya fue evaluada por el caller. Corre en la TX del
     * método público que lo invoca (con su {@code noRollbackFor}); propaga las excepciones
     * de cálculo tal cual. Usa {@code getReference} para el FK del canal: proxy managed en
     * la sesión actual, sin SELECT y sin riesgo de asociar un Canal detached al persistir.
     */
    private PrecioCalculadoDTO recalcularYGuardarPrecioCanalInterno(
            Producto producto, ContextoCalculo ctx, Integer canalId, Integer numeroCuotas) {
        PrecioCalculadoDTO dto = calcularPrecioUnificado(ctx.producto(), ctx.productoMargen(),
                ctx.conceptosCanal(), numeroCuotas, canalId, ctx.canal(), null);

        // Persistimos/actualizamos en producto_canal_precios (por producto, canal y cuotas)
        ProductoCanalPrecio pcp = productoCanalPrecioRepository
                .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canalId, numeroCuotas)
                .orElseGet(ProductoCanalPrecio::new);

        if (pcp.getId() == null) {
            pcp.setProducto(producto);
            pcp.setCanal(entityManager.getReference(Canal.class, canalId));
            pcp.setCuotas(numeroCuotas);
        }

        pcp.setPvp(dto.pvp());
        pcp.setPvpInflado(dto.pvpInflado());
        pcp.setCostoProducto(dto.costoProducto());
        pcp.setCostosVenta(dto.costosVenta());
        pcp.setIngresoNetoVendedor(dto.ingresoNetoVendedor());
        pcp.setGanancia(dto.ganancia());
        pcp.setMargenSobreIngresoNeto(dto.margenSobreIngresoNeto());
        pcp.setMargenSobrePvp(dto.margenSobrePvp());
        pcp.setMarkupPorcentaje(dto.markupPorcentaje());
        pcp.setFechaUltimoCalculo(LocalDateTime.now());

        productoCanalPrecioRepository.save(pcp);

        return dto;
    }

    /**
     * Evalúa las reglas a nivel canal (canal_regla) para determinar si el producto
     * aplica al canal. Si hay reglas INCLUIR, el producto debe cumplir al menos una;
     * si cumple alguna regla EXCLUIR queda fuera.
     */
    private boolean productoAplicaAlCanal(Producto producto, Integer canalId) {
        if (canalId == null || producto == null) {
            return true;
        }
        ContextoCanalCache ctx = obtenerContextoCanal(canalId);
        List<CanalRegla> reglas = ctx != null
                ? ctx.reglasCanal()
                : canalReglaRepository.findByCanalIdWithRelationsFetch(canalId);
        return CanalReglaServiceImpl.evaluarReglas(reglas, producto);
    }

    // ====================================================
    // LÓGICA DE CÁLCULO
    // ====================================================

    /**
     * Calcula el precio de un producto para cualquier canal usando una fórmula unificada.
     * Esta fórmula reemplaza los métodos específicos por canal y elimina todo hardcodeo,
     * manejando todas las diferencias entre canales dinámicamente a través de conceptos y reglas.
     * <p>
     * Fórmula unificada:
     * 1. COSTO: Costo base del producto
     * 2. Gastos sobre COSTO: Conceptos con aplica_sobre='COSTO' (multiplican el costo)
     * 2.5. PROVEEDOR_FIN: Obtiene porcentaje de proveedores.porcentaje y aplica COSTO * (1 + %FIN/100)
     * 3. Ganancia: (GAN.MIN.ML + MARGEN_PTS) * (1 + MARGEN_PROP/100) - donde el signo del porcentaje determina si aumenta o reduce
     * 4. Costo con ganancia: COSTO_CON_GASTOS * (1 + GANANCIA/100)
     * 4.5. COSTO_GANANCIA: Conceptos con aplica_sobre='COSTO_GANANCIA' (multiplican después de ganancia, antes de IMP)
     * 5. Envío: Si existe concepto con aplica_sobre='ENVIO', buscar mlas.precio_envio y sumarlo
     * 7. Impuestos (IMP): 1 + IVA/100 + sum(conceptos IMP)/100
     * 8. Gastos sobre COSTO_IVA: Conceptos con aplica_sobre='COSTO_IVA' (multiplican después de IMP)
     * 9. Gastos sobre PVP: Conceptos con aplica_sobre='PVP' + %CUOTAS (si aplica) - se aplican como divisores
     * 10. COSTO_OCULTO_PVP/DESCUENTO: Conceptos con aplica_sobre='COSTO_OCULTO_PVP' (divisor que aumenta precio) o 'DESCUENTO' (multiplicador que reduce precio)
     * 11. INFLACION: Conceptos con aplica_sobre='INFLACION' (divisor: PVP / (1 - INFLACION/100))
     * 12. Precio inflado: Precios inflados de producto_canal_precio_inflado
     *
     * @param producto       El producto para calcular el precio
     * @param productoMargen La relación producto-canal con configuración específica
     * @param conceptos      Lista de conceptos ya filtrados por reglas según el producto y canal
     * @param numeroCuotas   Número de cuotas (null si pago contado)
     * @param canalId        ID del canal para obtener información específica del canal
     * @return DTO con el precio calculado y métricas relacionadas
     */
    private PrecioCalculadoDTO calcularPrecioUnificado(
            Producto producto,
            ProductoMargen productoMargen,
            List<CanalConcepto> conceptos,
            Integer numeroCuotas,
            Integer canalId,
            Canal canalActual,
            BigDecimal precioEnvioOverride) {
        if (producto.getCosto() == null || producto.getCosto().compareTo(BigDecimal.ZERO) == 0) {
            throw new BadRequestException("El producto no tiene costo cargado");
        }

        if (producto.getIva() == null) {
            throw new BadRequestException("El producto no tiene IVA cargado");
        }

        // Validar que el producto tenga el margen requerido por el canal
        validarMargenRequerido(productoMargen, conceptos);

        // ============================================
        // CASO ESPECIAL: CANAL CON CANAL_BASE
        // Si el canal tiene canalBase configurado, el cálculo se basa en el PVP del canal padre
        // ============================================
        boolean tieneCanalBase = canalActual != null && canalActual.getCanalBase() != null;

        if (tieneCanalBase) {
            // Defensivo: si la cadena canal→canalBase tiene ciclos, evita StackOverflow en
            // la recursión de calcularPrecioSobrePvpBase. Validar al inicio falla rápido
            // con un mensaje claro en lugar de colgar el thread.
            validarCadenaCanalSinCiclos(canalActual);
            // Pasar todos los conceptos para verificar PROVEEDOR_FIN y ENVIO
            return calcularPrecioSobrePvpBase(producto, productoMargen, conceptos, canalId, numeroCuotas);
        }

        BigDecimal costo = producto.getCosto();
        BigDecimal iva = producto.getIva();

        // ============================================
        // OPTIMIZACIÓN: Agrupar conceptos por AplicaSobre en un solo pase
        // Antes: 11+ iteraciones sobre la lista conceptos (O(n×11))
        // Después: 1 iteración con groupingBy (O(n))
        // ============================================
        Map<AplicaSobre, List<CanalConcepto>> conceptosPorTipo = conceptos.stream()
                .filter(cc -> cc.getConcepto() != null && cc.getConcepto().getAplicaSobre() != null)
                .collect(Collectors.groupingBy(cc -> cc.getConcepto().getAplicaSobre()));

        // ============================================
        // PASO 1: COSTO BASE
        // ============================================
        BigDecimal costoBase = costo;

        // ============================================
        // PASO 2: Gastos sobre COSTO
        // ============================================
        List<CanalConcepto> gastosSobreCosto = conceptosPorTipo.getOrDefault(AplicaSobre.GASTO_SOBRE_COSTO, List.of());
        BigDecimal gastosSobreCostoTotal = calcularGastosPorcentaje(gastosSobreCosto);
        BigDecimal costoConGastos = costoBase.multiply(
                BigDecimal.ONE.add(gastosSobreCostoTotal.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP)));

        // ============================================
        // PASO 2.5: PROVEEDOR_FIN (financiación del proveedor)
        // ============================================
        List<CanalConcepto> conceptosProveedorFin = conceptosPorTipo.getOrDefault(AplicaSobre.FLAG_FINANCIACION_PROVEEDOR, List.of());

        BigDecimal porcentajeFin = BigDecimal.ZERO;
        if (!conceptosProveedorFin.isEmpty()) {
            // Obtener porcentaje de financiación del proveedor
            porcentajeFin = producto.getProveedor() != null
                    && producto.getProveedor().getFinanciacionPorcentaje() != null
                    ? producto.getProveedor().getFinanciacionPorcentaje()
                    : BigDecimal.ZERO;

            if (porcentajeFin.compareTo(BigDecimal.ZERO) > 0) {
                // Aplicar: COSTO * (1 + %FIN/100)
                costoConGastos = costoConGastos.multiply(
                        BigDecimal.ONE.add(porcentajeFin.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP)));
            }
        }

        // ============================================
        // PASO 3: Calcular ganancia ajustada
        // ============================================
        BigDecimal margenPorcentaje = obtenerMargenPorcentaje(productoMargen, conceptos);

        // Obtener MARGEN_PTS: suma de todos los porcentajes (positivos aumentan, negativos reducen)
        List<CanalConcepto> conceptosMargenPts = conceptosPorTipo.getOrDefault(AplicaSobre.AJUSTE_MARGEN_PUNTOS, List.of());
        BigDecimal ajusteMargenPts = calcularGastosPorcentaje(conceptosMargenPts);

        // Obtener MARGEN_PROP: suma de todos los porcentajes (positivos aumentan, negativos reducen)
        List<CanalConcepto> conceptosMargenProp = conceptosPorTipo.getOrDefault(AplicaSobre.AJUSTE_MARGEN_PROPORCIONAL, List.of());
        BigDecimal ajusteMargenProp = calcularGastosPorcentaje(conceptosMargenProp);

        // Calcular ganancia ajustada: GAN.MIN.ML + MARGEN_PTS
        BigDecimal gananciaAjustada = margenPorcentaje.add(ajusteMargenPts);

        // Aplicar ajuste proporcional: ganancia * (1 + MARGEN_PROP/100)
        if (ajusteMargenProp.compareTo(BigDecimal.ZERO) != 0) {
            gananciaAjustada = gananciaAjustada.multiply(
                    BigDecimal.ONE.add(ajusteMargenProp.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP)));
        }

        // Si GAN.MIN.ML = 0 y no hay ajustes, usar 0
        BigDecimal gananciaUsar = gananciaAjustada;

        // ============================================
        // PASO 4: Calcular costo con ganancia
        // ============================================
        BigDecimal gananciaFrac = gananciaUsar.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP);
        BigDecimal costoConGanancia = costoConGastos.multiply(BigDecimal.ONE.add(gananciaFrac));

        // ============================================
        // PASO 4.5: COSTO_GANANCIA (conceptos después de ganancia, antes de IMP)
        // ============================================
        // Snapshot ANTES del ajuste post-ganancia: base para el costo real de los
        // GASTO_POST_GANANCIA con naturaleza override COSTO_VENTA (ver calcularCostosVenta).
        BigDecimal costoConGananciaBase = costoConGanancia;
        List<CanalConcepto> gastosSobreCostoGanancia = conceptosPorTipo.getOrDefault(AplicaSobre.GASTO_POST_GANANCIA, List.of());
        BigDecimal gastosSobreCostoGananciaTotal = calcularGastosPorcentaje(gastosSobreCostoGanancia);
        if (gastosSobreCostoGananciaTotal.compareTo(BigDecimal.ZERO) > 0) {
            // Aplicar: COSTO_CON_GANANCIA * (1 + concepto/100)
            costoConGanancia = costoConGanancia.multiply(
                    BigDecimal.ONE.add(gastosSobreCostoGananciaTotal.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP)));
        }

        // ============================================
        // PASO 5: Envío (si existe concepto ENVIO)
        // ============================================
        BigDecimal costoConGananciaYEnvio = costoConGanancia;
        BigDecimal precioEnvio = BigDecimal.ZERO;
        List<CanalConcepto> conceptosEnvio = conceptosPorTipo.getOrDefault(AplicaSobre.FLAG_INCLUIR_ENVIO, List.of());

        if (!conceptosEnvio.isEmpty()) {
            // Usar override si se proporciona (incluso si es 0), sino buscar precio_envio del MLA
            if (precioEnvioOverride != null) {
                precioEnvio = precioEnvioOverride;
            } else if (producto.getMla() != null && producto.getMla().getPrecioEnvio() != null
                    && producto.getMla().getPrecioEnvio().compareTo(BigDecimal.ZERO) > 0) {
                precioEnvio = producto.getMla().getPrecioEnvio();
            }

            if (precioEnvio.compareTo(BigDecimal.ZERO) > 0) {
                costoConGananciaYEnvio = costoConGanancia.add(precioEnvio);
            }
        }

        // ============================================
        // PASO 7: Calcular factor de impuestos (IMP)
        // ============================================
        // El IVA del producto solo se aplica si existe un concepto con aplicaSobre=IVA para el canal
        boolean aplicaIva = conceptosPorTipo.containsKey(AplicaSobre.FLAG_APLICAR_IVA);
        BigDecimal ivaAplicar = aplicaIva ? iva : BigDecimal.ZERO;

        // Los precios inflados solo se aplican si existe un concepto con FLAG_APLICAR_PRECIO_INFLADO para el canal
        boolean usaPrecioInflado = conceptosPorTipo.containsKey(AplicaSobre.FLAG_APLICAR_PRECIO_INFLADO);

        List<CanalConcepto> gastosSobreImp = conceptosPorTipo.getOrDefault(AplicaSobre.IMPUESTO_EN_FACTOR_IMP, List.of());
        BigDecimal gastosSobreImpTotal = calcularGastosPorcentaje(gastosSobreImp);
        BigDecimal imp = BigDecimal.ONE
                .add(ivaAplicar.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP))
                .add(gastosSobreImpTotal.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP));

        // ============================================
        // PASO 8: Aplicar impuestos
        // ============================================
        BigDecimal costoConImpuestos = costoConGananciaYEnvio.multiply(imp);

        // ============================================
        // PASO 9: Gastos sobre COSTO_IVA
        // ============================================
        // Snapshot ANTES del ajuste post-impuestos: base para el costo real de los
        // GASTO_POST_IMPUESTOS con naturaleza override COSTO_VENTA (ver calcularCostosVenta).
        BigDecimal costoConImpuestosBase = costoConImpuestos;
        List<CanalConcepto> gastosSobreCostoIva = conceptosPorTipo.getOrDefault(AplicaSobre.GASTO_POST_IMPUESTOS, List.of());
        BigDecimal gastosSobreCostoIvaTotal = calcularGastosPorcentaje(gastosSobreCostoIva);
        if (gastosSobreCostoIvaTotal.compareTo(BigDecimal.ZERO) > 0) {
            costoConImpuestos = costoConImpuestos.multiply(
                    BigDecimal.ONE.add(gastosSobreCostoIvaTotal.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP)));
        }

        // ============================================
        // PASO 10: Gastos sobre PVP y cuotas
        // ============================================
        // Lógica especial: Si hay cuotas y aplicaCuotas=true, los gastos PVP se incluyen en el cálculo de cuotas
        // Si no hay cuotas, se aplican directamente como divisor
        BigDecimal gastosSobrePVPTotal = BigDecimal.ZERO;
        BigDecimal porcentajeCuota = BigDecimal.ZERO;

        // Obtener gastos PVP. La naturaleza del concepto (COSTO_VENTA default o
        // INFLACION override) decide si cada uno se cuenta como costo de venta;
        // en el bucket del divisor entran todos por igual.
        List<CanalConcepto> gastosSobrePVP = conceptosPorTipo.getOrDefault(AplicaSobre.COMISION_SOBRE_PVP, List.of());
        gastosSobrePVPTotal = calcularGastosPorcentaje(gastosSobrePVP);

        // FLAG_COMISION_ML: si existe, sumar comisionPorcentaje del MLA al divisor del PVP.
        // La naturaleza (COSTO_VENTA por default o INFLACION si se sobreescribe) decide si
        // se cuenta como costo de venta en los indicadores — eso se evalúa en calcularCostosVenta.
        List<CanalConcepto> conceptosComisionMl = conceptosPorTipo.getOrDefault(AplicaSobre.FLAG_COMISION_ML, List.of());
        if (!conceptosComisionMl.isEmpty() && producto.getMla() != null
                && producto.getMla().getComisionPorcentaje() != null
                && producto.getMla().getComisionPorcentaje().compareTo(BigDecimal.ZERO) > 0) {
            gastosSobrePVPTotal = gastosSobrePVPTotal.add(producto.getMla().getComisionPorcentaje());
        }

        // Obtener porcentaje de cuotas si aplica (ahora siempre aplica si hay cuotas)
        boolean aplicarCuotas = numeroCuotas != null;

        if (aplicarCuotas) {
            porcentajeCuota = obtenerPorcentajeCuota(canalId, numeroCuotas);

            // Usar gastos PVP de los conceptos ya filtrados (con reglas aplicadas)
            BigDecimal porcentajeConceptosCanal = gastosSobrePVPTotal;

            if (porcentajeCuota.compareTo(BigDecimal.ZERO) >= 0) {
                // CUOTA >= 0 (interés o sin cambio): sumar a gastos PVP y aplicar juntos como divisor
                // Fórmula: pvp = costo / (1 - (gastos_pvp% + cuota%)/100)
                BigDecimal porcentajeCuotasTotal = porcentajeConceptosCanal.add(porcentajeCuota);

                if (porcentajeCuotasTotal.compareTo(BigDecimal.ZERO) > 0) {
                    if (porcentajeCuotasTotal.compareTo(CIEN) >= 0) {
                        log.warn("Gastos PVP + cuotas {}% >= 100% para canal {}. Se limita a 99%.", porcentajeCuotasTotal, canalId);
                        porcentajeCuotasTotal = new BigDecimal("99");
                    }
                    BigDecimal cuotasFrac = porcentajeCuotasTotal.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP);
                    BigDecimal divisorCuotas = BigDecimal.ONE.subtract(cuotasFrac);
                    costoConImpuestos = costoConImpuestos.divide(divisorCuotas, PRECISION_CALCULO, RoundingMode.HALF_UP);
                }
            } else {
                // CUOTA < 0 (descuento): aplicar gastos PVP primero, luego descuento como multiplicador
                // Fórmula: pvp = (costo / (1 - gastos_pvp%/100)) * (1 - |descuento|%/100)

                // Primero gastos PVP
                if (porcentajeConceptosCanal.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal gastosFrac = porcentajeConceptosCanal.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP);
                    BigDecimal divisorGastos = BigDecimal.ONE.subtract(gastosFrac);
                    if (divisorGastos.compareTo(BigDecimal.ZERO) > 0) {
                        costoConImpuestos = costoConImpuestos.divide(divisorGastos, PRECISION_CALCULO, RoundingMode.HALF_UP);
                    }
                }

                // Luego descuento como multiplicador
                BigDecimal descuentoAbs = porcentajeCuota.abs();
                if (descuentoAbs.compareTo(CIEN) >= 0) {
                    log.warn("Descuento de cuota {}% >= 100% para canal {}. Se limita a 99%.", descuentoAbs, canalId);
                    descuentoAbs = new BigDecimal("99");
                }
                BigDecimal descuentoFrac = descuentoAbs.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP);
                BigDecimal factorDescuento = BigDecimal.ONE.subtract(descuentoFrac);
                costoConImpuestos = costoConImpuestos.multiply(factorDescuento);
            }

            // En este caso, no aplicamos gastos PVP como divisor separado (ya se aplicaron arriba)
            gastosSobrePVPTotal = BigDecimal.ZERO;
        } else {
            // Si no hay cuotas, aplicar gastos PVP como divisor
            if (gastosSobrePVPTotal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal gastosSobrePVPFrac = gastosSobrePVPTotal.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP);
                BigDecimal denominador = BigDecimal.ONE.subtract(gastosSobrePVPFrac);
                if (denominador.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BadRequestException("Los gastos sobre PVP son >= 100%, lo cual es inválido");
                }
                costoConImpuestos = costoConImpuestos.divide(denominador, PRECISION_CALCULO, RoundingMode.HALF_UP);
            }
        }

        BigDecimal pvpBase = costoConImpuestos;

        // ============================================
        // PASO 11: COSTO_OCULTO_PVP y DESCUENTO (conceptos)
        // ============================================
        // Obtener COSTO_OCULTO_PVP (usando mapa pre-agrupado)
        List<CanalConcepto> conceptosCostoOcultoPvp = conceptosPorTipo.getOrDefault(AplicaSobre.COSTO_OCULTO_PVP, List.of());
        BigDecimal porcentajeCostoOcultoPvp = calcularGastosPorcentaje(conceptosCostoOcultoPvp);

        // Obtener DESCUENTO (conceptos con aplica_sobre='DESCUENTO')
        BigDecimal descuentoConceptos = obtenerDescuentoMaquina(conceptos);

        BigDecimal pvp = pvpBase;

        // Aplicar COSTO_OCULTO_PVP como divisor: / (1 - COSTO_OCULTO_PVP/100) - aumenta el precio
        if (porcentajeCostoOcultoPvp.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal costoOcultoFrac = porcentajeCostoOcultoPvp.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP);
            BigDecimal denominadorCostoOculto = BigDecimal.ONE.subtract(costoOcultoFrac);
            if (denominadorCostoOculto.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("COSTO_OCULTO_PVP es >= 100%, lo cual es inválido");
            }
            pvp = pvp.divide(denominadorCostoOculto, PRECISION_CALCULO, RoundingMode.HALF_UP);
        }

        // Aplicar DESCUENTO como multiplicador: * (1 - DESCUENTO/100)
        if (descuentoConceptos.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal descuentoFrac = descuentoConceptos.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP);
            BigDecimal factorDescuento = BigDecimal.ONE.subtract(descuentoFrac);
            pvp = pvp.multiply(factorDescuento);
        }

        // ============================================
        // PASO 13: INFLACION_DIVISOR_FINAL (concepto) - usando mapa pre-agrupado
        // ============================================
        List<CanalConcepto> conceptosInflacion = conceptosPorTipo.getOrDefault(AplicaSobre.INFLACION_DIVISOR_FINAL, List.of());

        if (!conceptosInflacion.isEmpty()) {
            BigDecimal porcentajeInflacion = calcularGastosPorcentaje(conceptosInflacion);
            if (porcentajeInflacion.compareTo(BigDecimal.ZERO) > 0) {
                if (porcentajeInflacion.compareTo(CIEN) >= 0) {
                    log.warn("Inflación {}% >= 100% para canal {}. Se limita a 99%.", porcentajeInflacion, canalId);
                    porcentajeInflacion = new BigDecimal("99");
                }
                BigDecimal divisor = BigDecimal.ONE.subtract(porcentajeInflacion.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP));
                pvp = pvp.divide(divisor, PRECISION_CALCULO, RoundingMode.HALF_UP);
            }
        }

        BigDecimal pvpSinInflar = pvp.setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);

        // ============================================
        // PASO 15: Precio inflado (solo si el canal tiene concepto FLAG_APLICAR_PRECIO_INFLADO habilitado)
        // ============================================
        BigDecimal pvpInfladoCalc = aplicarPrecioInflado(producto.getId(), canalId,
                pvpSinInflar, usaPrecioInflado);
        pvpInfladoCalc = pvpInfladoCalc.setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);
        // Si no hubo precio inflado, pvpInflado queda null (no tiene sentido mostrar el mismo valor que pvp)
        BigDecimal pvpInflado = pvpInfladoCalc.compareTo(pvpSinInflar) != 0 ? pvpInfladoCalc : null;

        // ============================================
        // PASO 16: Calcular nuevas métricas contables
        // ============================================

        // costoProductoMetrica = costoBase × (1 + financiacionProveedor/100)
        BigDecimal costoProductoMetrica = costoBase
                .multiply(BigDecimal.ONE.add(porcentajeFin.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP)))
                .setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);

        // costosVenta = Σ(COMISION_SOBRE_PVP, COMISION_ML, COSTO_OCULTO_PVP, ENVIO, GASTO_SIN_INFLAR_PVP,
        //                  GASTO_* con override COSTO_VENTA) + cuotas
        // (DESCUENTO_PORCENTUAL NO se incluye: ya está aplicado al PVP, no es plata que salga.)
        BigDecimal comisionMlParaMetricas = (producto.getMla() != null && producto.getMla().getComisionPorcentaje() != null)
                ? producto.getMla().getComisionPorcentaje() : BigDecimal.ZERO;
        BasesCalculoCosto basesCosto = new BasesCalculoCosto(
                precioEnvio, costoBase, costoConGananciaBase, costoConImpuestosBase);
        BigDecimal costosVenta = calcularCostosVenta(
                pvpSinInflar, conceptos, numeroCuotas, canalId, comisionMlParaMetricas, basesCosto);

        // montoIva = PVP × IVA / (100 + IVA + IMP) -- extrae IVA incluido en PVP
        // El divisor debe incluir IIBB y otros IMPUESTO_EN_FACTOR_IMP para que IVA + IMP
        // sumen correctamente y no se sobreestime ninguno.
        BigDecimal porcentajeImpuestosAdic = sumaImpuestosAdicionales(conceptos);
        BigDecimal montoIva = BigDecimal.ZERO;
        if (ivaAplicar.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal divisorIva = CIEN.add(ivaAplicar).add(porcentajeImpuestosAdic);
            montoIva = pvpSinInflar.multiply(ivaAplicar)
                    .divide(divisorIva, PRECISION_CALCULO, RoundingMode.HALF_UP);
        }

        // montoImpuestos = Σ conceptos con AplicaSobre = IMP (IIBB, etc.)
        BigDecimal montoImpuestos = calcularMontoImpuestos(pvpSinInflar, conceptos, ivaAplicar);

        // ingresoNetoVendedor = PVP - IVA - impuestos - costosVenta
        BigDecimal ingresoNetoVendedor = pvpSinInflar
                .subtract(montoIva)
                .subtract(montoImpuestos)
                .subtract(costosVenta)
                .setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);

        // ganancia = ingresoNetoVendedor - costoProductoMetrica
        BigDecimal ganancia = ingresoNetoVendedor.subtract(costoProductoMetrica)
                .setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);

        // margenSobreIngresoNeto = (ganancia / ingresoNetoVendedor) × 100
        BigDecimal margenSobreIngresoNeto = BigDecimal.ZERO;
        if (ingresoNetoVendedor.compareTo(BigDecimal.ZERO) > 0) {
            margenSobreIngresoNeto = clampMargen(ganancia.multiply(CIEN)
                    .divide(ingresoNetoVendedor, PRECISION_RESULTADO, RoundingMode.HALF_UP));
        }

        // margenSobrePvp = (ganancia / pvp) × 100
        BigDecimal margenSobrePvp = BigDecimal.ZERO;
        if (pvpSinInflar.compareTo(BigDecimal.ZERO) > 0) {
            margenSobrePvp = clampMargen(ganancia.multiply(CIEN)
                    .divide(pvpSinInflar, PRECISION_RESULTADO, RoundingMode.HALF_UP));
        }

        // markupPorcentaje = (ganancia / costoProductoMetrica) × 100
        BigDecimal markupPorcentaje = BigDecimal.ZERO;
        if (costoProductoMetrica.compareTo(BigDecimal.ZERO) > 0) {
            markupPorcentaje = clampMargen(ganancia.multiply(CIEN)
                    .divide(costoProductoMetrica, PRECISION_RESULTADO, RoundingMode.HALF_UP));
        }

        // Descuentos aplicables del canal (reglas por monto mínimo). Se recalculan
        // aquí porque tenemos las bases y los conceptos en scope — así costosVenta
        // con descuento queda exacto en lugar de la proyección lineal.
        List<DescuentoAplicableDTO> descuentosCanal = calcularDescuentosAplicables(
                canalId, pvpSinInflar, costoProductoMetrica, ingresoNetoVendedor,
                montoIva, montoImpuestos, conceptos, numeroCuotas,
                comisionMlParaMetricas, basesCosto);

        return new PrecioCalculadoDTO(
                canalId,
                canalActual != null ? canalActual.getNombre() : null,
                numeroCuotas,
                pvpSinInflar,
                pvpInflado,
                costoProductoMetrica,
                costosVenta,
                ingresoNetoVendedor,
                ganancia,
                margenSobreIngresoNeto,
                margenSobrePvp,
                markupPorcentaje,
                LocalDateTime.now(),
                descuentosCanal);
    }

    /**
     * Genera la fórmula de cálculo de precio para cualquier canal usando una fórmula unificada.
     * Esta fórmula reemplaza los métodos específicos por canal y muestra todos los pasos dinámicamente.
     *
     * @param producto       El producto para calcular la fórmula
     * @param productoMargen La relación producto-canal con configuración específica
     * @param conceptos      Lista de conceptos ya filtrados por reglas según el producto y canal
     * @param numeroCuotas   Número de cuotas (null si pago contado)
     * @param canalId        ID del canal para obtener información específica del canal
     * @return DTO con los pasos de la fórmula y el resultado final
     */
    private FormulaCalculoDTO generarFormulaUnificado(
            Producto producto,
            ProductoMargen productoMargen,
            List<CanalConcepto> conceptos,
            Integer numeroCuotas,
            Integer canalId) {
        List<FormulaCalculoDTO.PasoCalculo> pasos = new ArrayList<>();
        int pasoNumero = 1;

        // ============================================
        // CASO ESPECIAL: CANAL CON CANAL_BASE
        // Si el canal tiene canalBase configurado, la fórmula se basa en el PVP del canal padre
        // ============================================
        Canal canalActual = canalRepository.findById(canalId).orElse(null);
        boolean tieneCanalBase = canalActual != null && canalActual.getCanalBase() != null;

        if (tieneCanalBase) {
            List<CanalConcepto> conceptosSobrePvpBase = conceptos.stream()
                    .filter(cc -> cc.getConcepto() != null
                            && cc.getConcepto().getAplicaSobre() != null
                            && cc.getConcepto().getAplicaSobre().esCalculoSobreCanalBase())
                    .collect(Collectors.toList());
            return generarFormulaSobrePvpBase(producto, conceptosSobrePvpBase, canalId, numeroCuotas);
        }

        // Paso 1: COSTO BASE
        BigDecimal costo = producto.getCosto();
        pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++, "Costo base del producto",
                "COSTO", rd(costo), String.format("Costo: $%s", fmt(costo)), FormulaCalculoDTO.UNIDAD_MONEDA));

        // Paso 2: Gastos sobre COSTO
        List<CanalConcepto> gastosSobreCosto = conceptos.stream()
                .filter(cc -> cc.getConcepto().getAplicaSobre() == AplicaSobre.GASTO_SOBRE_COSTO)
                .collect(Collectors.toList());
        BigDecimal gastosSobreCostoTotal = calcularGastosPorcentaje(gastosSobreCosto);
        BigDecimal costoConGastos = costo;
        if (gastosSobreCostoTotal.compareTo(BigDecimal.ZERO) > 0) {
            costoConGastos = costo.multiply(
                    BigDecimal.ONE.add(gastosSobreCostoTotal.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP)));
            List<String> nombresConceptosCosto = obtenerNombresConceptos(conceptos, AplicaSobre.GASTO_SOBRE_COSTO);
            String nombresCostoFormateados = formatearNombresConceptos(nombresConceptosCosto);
            String detalleConceptosCosto = formatearDetalleConceptos(gastosSobreCosto);
            pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++,
                    "Gastos sobre costo",
                    String.format("COSTO_CON_GASTOS = COSTO * (1 + (%s)/100)", nombresCostoFormateados),
                    rd(costoConGastos),
                    String.format("%s * (1 + (%s)/100) = %s", fmt(costo),
                            detalleConceptosCosto.isEmpty() ? fmt(gastosSobreCostoTotal) : detalleConceptosCosto,
                            fmt(costoConGastos)),
                    FormulaCalculoDTO.UNIDAD_MONEDA));
        }

        // Paso 2.5: PROVEEDOR_FIN (financiación del proveedor)
        List<CanalConcepto> conceptosProveedorFin = conceptos.stream()
                .filter(cc -> cc.getConcepto() != null
                        && cc.getConcepto().getAplicaSobre() == AplicaSobre.FLAG_FINANCIACION_PROVEEDOR)
                .collect(Collectors.toList());

        if (!conceptosProveedorFin.isEmpty()) {
            BigDecimal porcentajeFin = producto.getProveedor() != null
                    && producto.getProveedor().getFinanciacionPorcentaje() != null
                    ? producto.getProveedor().getFinanciacionPorcentaje()
                    : BigDecimal.ZERO;

            if (porcentajeFin.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal costoAntesFin = costoConGastos;
                costoConGastos = costoConGastos.multiply(
                        BigDecimal.ONE.add(porcentajeFin.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP)));
                List<String> nombresProveedorFin = obtenerNombresConceptos(conceptos, AplicaSobre.FLAG_FINANCIACION_PROVEEDOR);
                String nombresProveedorFinFormateados = formatearNombresConceptos(nombresProveedorFin);
                pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++,
                        "Financiación del proveedor",
                        String.format("COSTO_CON_FINANCIACION = COSTO_CON_GASTOS * (1 + %s/100)",
                                nombresProveedorFinFormateados),
                        rd(costoConGastos),
                        String.format("%s * (1 + %s/100) = %s (%%FIN obtenido de proveedor: %s%%)",
                                fmt(costoAntesFin),
                                fmt(porcentajeFin),
                                fmt(costoConGastos),
                                fmt(porcentajeFin)),
                        FormulaCalculoDTO.UNIDAD_MONEDA));
            }
        }

        // Paso 3: Ganancia ajustada
        BigDecimal margenPorcentaje = obtenerMargenPorcentaje(productoMargen, conceptos);

        // Determinar tipo de margen para mostrar en fórmula
        boolean usaMayorista = conceptos.stream()
                .anyMatch(cc -> cc.getConcepto() != null
                        && cc.getConcepto().getAplicaSobre() == AplicaSobre.FLAG_USAR_MARGEN_MAYORISTA);
        String tipoMargenNombre = usaMayorista ? "MARGEN_MAYORISTA" : "MARGEN_MINORISTA";

        // MARGEN_PTS: suma de porcentajes (positivos aumentan, negativos reducen)
        List<CanalConcepto> conceptosMargenPts = conceptos.stream()
                .filter(cc -> cc.getConcepto() != null
                        && cc.getConcepto().getAplicaSobre() == AplicaSobre.AJUSTE_MARGEN_PUNTOS)
                .collect(Collectors.toList());
        BigDecimal ajusteMargenPts = calcularGastosPorcentaje(conceptosMargenPts);

        // MARGEN_PROP: suma de porcentajes (positivos aumentan, negativos reducen)
        List<CanalConcepto> conceptosMargenProp = conceptos.stream()
                .filter(cc -> cc.getConcepto() != null
                        && cc.getConcepto().getAplicaSobre() == AplicaSobre.AJUSTE_MARGEN_PROPORCIONAL)
                .collect(Collectors.toList());
        BigDecimal ajusteMargenProp = calcularGastosPorcentaje(conceptosMargenProp);

        // Calcular ganancia ajustada: MARGEN + MARGEN_PTS
        BigDecimal gananciaAjustada = margenPorcentaje.add(ajusteMargenPts);

        // Aplicar ajuste proporcional: ganancia * (1 + MARGEN_PROP/100)
        if (ajusteMargenProp.compareTo(BigDecimal.ZERO) != 0) {
            gananciaAjustada = gananciaAjustada.multiply(
                    BigDecimal.ONE.add(ajusteMargenProp.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP)));
        }

        BigDecimal gananciaUsar = gananciaAjustada;

        List<String> nombresMargenPts = obtenerNombresConceptos(conceptos, AplicaSobre.AJUSTE_MARGEN_PUNTOS);
        List<String> nombresMargenProp = obtenerNombresConceptos(conceptos, AplicaSobre.AJUSTE_MARGEN_PROPORCIONAL);
        String nombresMargenPtsFormateados = formatearNombresConceptos(nombresMargenPts);
        String nombresMargenPropFormateados = formatearNombresConceptos(nombresMargenProp);
        String detalleMargenPts = formatearDetalleConceptos(conceptosMargenPts);
        String detalleMargenProp = formatearDetalleConceptos(conceptosMargenProp);

        String formulaGanancia = "GANANCIA = " + tipoMargenNombre;
        String detalleGanancia = String.format("%s: %s%%", tipoMargenNombre, fmt(margenPorcentaje));
        if (ajusteMargenPts.compareTo(BigDecimal.ZERO) != 0) {
            String signo = ajusteMargenPts.compareTo(BigDecimal.ZERO) > 0 ? " + " : " ";
            formulaGanancia += signo + nombresMargenPtsFormateados;
            detalleGanancia += signo + detalleMargenPts;
        }
        if (ajusteMargenProp.compareTo(BigDecimal.ZERO) != 0) {
            formulaGanancia += String.format(" * (1 + %s/100)", nombresMargenPropFormateados);
            detalleGanancia += String.format(" * (1 + %s/100)", detalleMargenProp);
        }
        detalleGanancia += String.format(" → GANANCIA = %s%%", fmt(gananciaUsar));

        pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++, "Ganancia ajustada",
                formulaGanancia, rd(gananciaUsar), detalleGanancia, FormulaCalculoDTO.UNIDAD_PORCENTAJE));

        // Paso 4: Costo con ganancia
        BigDecimal gananciaFrac = gananciaUsar.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP);
        BigDecimal costoConGanancia = costoConGastos.multiply(BigDecimal.ONE.add(gananciaFrac));
        pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++, "Costo con ganancia",
                "COSTO_CON_GANANCIA = COSTO_CON_GASTOS * (1 + GANANCIA/100)",
                rd(costoConGanancia),
                String.format("%s * (1 + %s/100) = %s", fmt(costoConGastos), fmt(gananciaUsar), fmt(costoConGanancia)),
                FormulaCalculoDTO.UNIDAD_MONEDA));

        // Paso 4.5: Gastos sobre COSTO_GANANCIA (después de ganancia, antes de IMP)
        List<CanalConcepto> gastosSobreCostoGanancia = conceptos.stream()
                .filter(cc -> cc.getConcepto() != null
                        && cc.getConcepto().getAplicaSobre() == AplicaSobre.GASTO_POST_GANANCIA)
                .collect(Collectors.toList());
        BigDecimal gastosSobreCostoGananciaTotal = calcularGastosPorcentaje(gastosSobreCostoGanancia);
        if (gastosSobreCostoGananciaTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal costoAntesCostoGanancia = costoConGanancia;
            costoConGanancia = costoConGanancia.multiply(
                    BigDecimal.ONE.add(gastosSobreCostoGananciaTotal.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP)));
            List<String> nombresCostoGanancia = obtenerNombresConceptos(conceptos, AplicaSobre.GASTO_POST_GANANCIA);
            String nombresCostoGananciaFormateados = formatearNombresConceptos(nombresCostoGanancia);
            String detalleCostoGanancia = formatearDetalleConceptos(gastosSobreCostoGanancia);
            pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++,
                    "Gastos post-ganancia",
                    String.format("COSTO_CON_GANANCIA = COSTO_CON_GANANCIA * (1 + (%s)/100)",
                            nombresCostoGananciaFormateados),
                    rd(costoConGanancia),
                    String.format("%s * (1 + (%s)/100) = %s",
                            fmt(costoAntesCostoGanancia),
                            detalleCostoGanancia.isEmpty() ? fmt(gastosSobreCostoGananciaTotal) : detalleCostoGanancia,
                            fmt(costoConGanancia)),
                    FormulaCalculoDTO.UNIDAD_MONEDA));
        }

        // Paso 5: Envío (si existe concepto ENVIO)
        BigDecimal costoConGananciaYEnvio = costoConGanancia;
        List<CanalConcepto> conceptosEnvio = conceptos.stream()
                .filter(cc -> cc.getConcepto() != null
                        && cc.getConcepto().getAplicaSobre() == AplicaSobre.FLAG_INCLUIR_ENVIO)
                .collect(Collectors.toList());

        if (!conceptosEnvio.isEmpty()) {
            BigDecimal precioEnvio = BigDecimal.ZERO;
            if (producto.getMla() != null && producto.getMla().getPrecioEnvio() != null
                    && producto.getMla().getPrecioEnvio().compareTo(BigDecimal.ZERO) > 0) {
                precioEnvio = producto.getMla().getPrecioEnvio();
            }

            if (precioEnvio.compareTo(BigDecimal.ZERO) > 0) {
                costoConGananciaYEnvio = costoConGanancia.add(precioEnvio);
                pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++, "Sumar envío",
                        "COSTO_CON_GANANCIA_Y_ENVIO = COSTO_CON_GANANCIA + ENVIO",
                        rd(costoConGananciaYEnvio),
                        String.format("Envío: $%s → %s + %s = %s", fmt(precioEnvio), fmt(costoConGanancia), fmt(precioEnvio),
                                fmt(costoConGananciaYEnvio)),
                        FormulaCalculoDTO.UNIDAD_MONEDA));
            }
        }

        // Paso 6: Factor de impuestos (IMP)
        // El IVA del producto solo se aplica si existe un concepto con aplicaSobre=IVA para el canal
        BigDecimal ivaProducto = producto.getIva();
        boolean aplicaIva = conceptos.stream()
                .anyMatch(cc -> cc.getConcepto() != null
                        && cc.getConcepto().getAplicaSobre() == AplicaSobre.FLAG_APLICAR_IVA);
        BigDecimal ivaAplicar = aplicaIva ? ivaProducto : BigDecimal.ZERO;

        // Los precios inflados solo se aplican si existe un concepto con FLAG_APLICAR_PRECIO_INFLADO para el canal
        boolean usaPrecioInflado = conceptos.stream()
                .anyMatch(cc -> cc.getConcepto() != null
                        && cc.getConcepto().getAplicaSobre() == AplicaSobre.FLAG_APLICAR_PRECIO_INFLADO);

        List<CanalConcepto> gastosSobreImp = conceptos.stream()
                .filter(cc -> cc.getConcepto().getAplicaSobre() == AplicaSobre.IMPUESTO_EN_FACTOR_IMP)
                .collect(Collectors.toList());
        BigDecimal gastosSobreImpTotal = calcularGastosPorcentaje(gastosSobreImp);
        BigDecimal imp = BigDecimal.ONE
                .add(ivaAplicar.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP))
                .add(gastosSobreImpTotal.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP));

        List<String> nombresConceptosImp = obtenerNombresConceptos(conceptos, AplicaSobre.IMPUESTO_EN_FACTOR_IMP);
        String nombresImpFormateados = formatearNombresConceptos(nombresConceptosImp);
        String detalleConceptosImp = formatearDetalleConceptos(gastosSobreImp);
        String formulaImp = nombresImpFormateados.isEmpty()
                ? "IMP = 1 + IVA/100"
                : String.format("IMP = 1 + IVA/100 + %s/100", nombresImpFormateados);

        String fuenteIva = aplicaIva
                ? String.format("IVA (producto): %s%%", fmt(ivaAplicar))
                : "IVA: 0% (canal sin concepto IVA)";
        String detalleImp = fuenteIva;
        if (gastosSobreImpTotal.compareTo(BigDecimal.ZERO) > 0 && !detalleConceptosImp.isEmpty()) {
            detalleImp += String.format(" + %s", detalleConceptosImp);
        }
        detalleImp += String.format(" → IMP = 1 + %s/100", fmt(ivaAplicar));
        if (gastosSobreImpTotal.compareTo(BigDecimal.ZERO) > 0) {
            detalleImp += String.format(" + %s/100 = %s", fmt(gastosSobreImpTotal), fmt(imp));
        } else {
            detalleImp += String.format(" = %s", fmt(imp));
        }
        pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++, "Factor de impuestos",
                formulaImp, rd(imp), detalleImp, FormulaCalculoDTO.UNIDAD_FACTOR));

        // Paso 7: Aplicar impuestos
        BigDecimal costoConImpuestos = costoConGananciaYEnvio.multiply(imp);
        pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++, "Costo con impuestos",
                "COSTO_CON_IMPUESTOS = COSTO_CON_GANANCIA_Y_ENVIO * IMP",
                rd(costoConImpuestos),
                String.format("%s * %s = %s", fmt(costoConGananciaYEnvio), fmt(imp), fmt(costoConImpuestos)),
                FormulaCalculoDTO.UNIDAD_MONEDA));

        // Paso 8: Gastos sobre COSTO_IVA
        List<CanalConcepto> gastosSobreCostoIva = conceptos.stream()
                .filter(cc -> cc.getConcepto().getAplicaSobre() == AplicaSobre.GASTO_POST_IMPUESTOS)
                .collect(Collectors.toList());
        BigDecimal gastosSobreCostoIvaTotal = calcularGastosPorcentaje(gastosSobreCostoIva);
        if (gastosSobreCostoIvaTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal costoConImpuestosAntes = costoConImpuestos;
            costoConImpuestos = costoConImpuestos.multiply(
                    BigDecimal.ONE.add(gastosSobreCostoIvaTotal.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP)));
            List<String> nombresConceptosCostoIva = obtenerNombresConceptos(conceptos, AplicaSobre.GASTO_POST_IMPUESTOS);
            String nombresCostoIvaFormateados = formatearNombresConceptos(nombresConceptosCostoIva);
            String detalleConceptosCostoIva = formatearDetalleConceptos(gastosSobreCostoIva);
            pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++,
                    "Gastos post-impuestos",
                    String.format("COSTO_CON_IMPUESTOS = COSTO_CON_IMPUESTOS * (1 + (%s)/100)",
                            nombresCostoIvaFormateados),
                    rd(costoConImpuestos),
                    String.format("%s * (1 + (%s)/100) = %s", fmt(costoConImpuestosAntes),
                            detalleConceptosCostoIva.isEmpty() ? fmt(gastosSobreCostoIvaTotal) : detalleConceptosCostoIva,
                            fmt(costoConImpuestos)),
                    FormulaCalculoDTO.UNIDAD_MONEDA));
        }

        // Paso 9: Gastos sobre PVP y cuotas. La naturaleza del concepto (COSTO_VENTA o
        // INFLACION override) decide si se cuenta como costo de venta; en el bucket
        // del divisor entran todos por igual.
        List<CanalConcepto> gastosSobrePVP = conceptos.stream()
                .filter(cc -> cc.getConcepto().getAplicaSobre() == AplicaSobre.COMISION_SOBRE_PVP)
                .collect(Collectors.toList());
        BigDecimal gastosSobrePVPTotal = calcularGastosPorcentaje(gastosSobrePVP);

        // FLAG_COMISION_ML: si existe, sumar comisionPorcentaje del MLA
        BigDecimal comisionMl = BigDecimal.ZERO;
        boolean tieneComisionMl = conceptos.stream()
                .anyMatch(cc -> cc.getConcepto().getAplicaSobre() == AplicaSobre.FLAG_COMISION_ML);
        if (tieneComisionMl && producto.getMla() != null
                && producto.getMla().getComisionPorcentaje() != null
                && producto.getMla().getComisionPorcentaje().compareTo(BigDecimal.ZERO) > 0) {
            comisionMl = producto.getMla().getComisionPorcentaje();
            gastosSobrePVPTotal = gastosSobrePVPTotal.add(comisionMl);
        }

        BigDecimal porcentajeCuota = BigDecimal.ZERO;

        boolean aplicarCuotas = numeroCuotas != null;

        // Preparar detalle de comisión ML para mostrar en fórmula
        String detalleComisionMl = "";
        if (tieneComisionMl && comisionMl.compareTo(BigDecimal.ZERO) > 0) {
            detalleComisionMl = String.format("COMISION_ML(MLA)=%s%%", fmt(comisionMl));
        }

        if (aplicarCuotas) {
            porcentajeCuota = obtenerPorcentajeCuota(canalId, numeroCuotas);

            // Usar los conceptos ya filtrados (con reglas aplicadas) en lugar de buscar todos
            BigDecimal porcentajeConceptosCanal = gastosSobrePVPTotal;

            if (porcentajeCuota.compareTo(BigDecimal.ZERO) >= 0) {
                // CUOTA >= 0 (interés o sin cambio): gastos PVP + cuota como divisor único.
                BigDecimal porcentajeCuotasTotal = porcentajeConceptosCanal.add(porcentajeCuota);

                if (porcentajeCuotasTotal.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal cuotasFrac = porcentajeCuotasTotal.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP);
                    BigDecimal divisorCuotas = BigDecimal.ONE.subtract(cuotasFrac);
                    if (divisorCuotas.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal costoConImpuestosAntesCuotas = costoConImpuestos;
                        costoConImpuestos = costoConImpuestos.divide(divisorCuotas, PRECISION_CALCULO, RoundingMode.HALF_UP);
                        List<String> nombresConceptosPVPCuotas = new ArrayList<>(obtenerNombresConceptos(conceptos, AplicaSobre.COMISION_SOBRE_PVP));
                        if (tieneComisionMl) nombresConceptosPVPCuotas.add("COMISION_ML");
                        String nombresPVPCuotasFormateados = formatearNombresConceptos(nombresConceptosPVPCuotas);
                        String detalleConceptosPVPCuotas = formatearDetalleConceptos(gastosSobrePVP);
                        if (!detalleComisionMl.isEmpty()) {
                            detalleConceptosPVPCuotas = detalleConceptosPVPCuotas.isEmpty()
                                    ? detalleComisionMl
                                    : detalleConceptosPVPCuotas + " + " + detalleComisionMl;
                        }
                        pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++,
                                "Aplicar comisiones y cuotas",
                                String.format("PVP = COSTO_CON_IMPUESTOS / (1 - (%s + %s cuotas)/100)",
                                        nombresPVPCuotasFormateados, numeroCuotas),
                                rd(costoConImpuestos),
                                String.format("Comisiones + cuotas = (%s) + %s%% = %s%% → %s / (1 - %s/100) = %s",
                                        detalleConceptosPVPCuotas.isEmpty() ? fmt(porcentajeConceptosCanal) + "%"
                                                : detalleConceptosPVPCuotas,
                                        fmt(porcentajeCuota), fmt(porcentajeCuotasTotal),
                                        fmt(costoConImpuestosAntesCuotas), fmt(porcentajeCuotasTotal), fmt(costoConImpuestos)),
                                FormulaCalculoDTO.UNIDAD_MONEDA));
                    }
                }
            } else {
                // CUOTA < 0 (descuento, p. ej. transferencia): primero los gastos PVP como
                // divisor y luego el descuento como multiplicador. Mismo orden que el motor
                // (calcularPrecioUnificado) para que el PVP final coincida con el indicador.
                if (porcentajeConceptosCanal.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal gastosFrac = porcentajeConceptosCanal.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP);
                    BigDecimal divisorGastos = BigDecimal.ONE.subtract(gastosFrac);
                    if (divisorGastos.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal costoConImpuestosAntesGastos = costoConImpuestos;
                        costoConImpuestos = costoConImpuestos.divide(divisorGastos, PRECISION_CALCULO, RoundingMode.HALF_UP);
                        List<String> nombresConceptosPVP = new ArrayList<>(obtenerNombresConceptos(conceptos, AplicaSobre.COMISION_SOBRE_PVP));
                        if (tieneComisionMl) nombresConceptosPVP.add("COMISION_ML");
                        String nombresPVPFormateados = formatearNombresConceptos(nombresConceptosPVP);
                        String detalleConceptosPVP = formatearDetalleConceptos(gastosSobrePVP);
                        if (!detalleComisionMl.isEmpty()) {
                            detalleConceptosPVP = detalleConceptosPVP.isEmpty()
                                    ? detalleComisionMl
                                    : detalleConceptosPVP + " + " + detalleComisionMl;
                        }
                        pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++,
                                "Aplicar comisiones",
                                String.format("PVP = COSTO_CON_IMPUESTOS / (1 - %s/100)", nombresPVPFormateados),
                                rd(costoConImpuestos),
                                String.format("Comisiones = (%s) = %s%% → %s / (1 - %s/100) = %s",
                                        detalleConceptosPVP.isEmpty() ? fmt(porcentajeConceptosCanal) + "%"
                                                : detalleConceptosPVP,
                                        fmt(porcentajeConceptosCanal),
                                        fmt(costoConImpuestosAntesGastos), fmt(porcentajeConceptosCanal), fmt(costoConImpuestos)),
                                FormulaCalculoDTO.UNIDAD_MONEDA));
                    }
                }

                BigDecimal descuentoAbs = porcentajeCuota.abs();
                if (descuentoAbs.compareTo(CIEN) >= 0) {
                    descuentoAbs = new BigDecimal("99");
                }
                BigDecimal descuentoFrac = descuentoAbs.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP);
                BigDecimal factorDescuento = BigDecimal.ONE.subtract(descuentoFrac);
                BigDecimal costoConImpuestosAntesDescuento = costoConImpuestos;
                costoConImpuestos = costoConImpuestos.multiply(factorDescuento);
                pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++,
                        "Aplicar descuento por cuota",
                        String.format("PVP = PVP × (1 - %s cuotas/100)", numeroCuotas),
                        rd(costoConImpuestos),
                        String.format("Descuento %s%% → %s × (1 - %s/100) = %s",
                                fmt(descuentoAbs), fmt(costoConImpuestosAntesDescuento), fmt(descuentoAbs), fmt(costoConImpuestos)),
                        FormulaCalculoDTO.UNIDAD_MONEDA));
            }
            gastosSobrePVPTotal = BigDecimal.ZERO;
        } else {
            if (gastosSobrePVPTotal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal gastosSobrePVPFrac = gastosSobrePVPTotal.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP);
                BigDecimal denominador = BigDecimal.ONE.subtract(gastosSobrePVPFrac);
                if (denominador.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BadRequestException("Los gastos sobre PVP son >= 100%, lo cual es inválido");
                }
                BigDecimal costoConImpuestosAntesPVP = costoConImpuestos;
                costoConImpuestos = costoConImpuestos.divide(denominador, PRECISION_CALCULO, RoundingMode.HALF_UP);
                List<String> nombresConceptosPVP = new ArrayList<>(obtenerNombresConceptos(conceptos, AplicaSobre.COMISION_SOBRE_PVP));
                if (tieneComisionMl) nombresConceptosPVP.add("COMISION_ML");
                String nombresPVPFormateados = formatearNombresConceptos(nombresConceptosPVP);
                String detalleConceptosPVP = formatearDetalleConceptos(gastosSobrePVP);
                if (!detalleComisionMl.isEmpty()) {
                    detalleConceptosPVP = detalleConceptosPVP.isEmpty()
                            ? detalleComisionMl
                            : detalleConceptosPVP + " + " + detalleComisionMl;
                }
                pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++,
                        "Comisiones sobre PVP",
                        String.format("PVP = COSTO_CON_IMPUESTOS / (1 - (%s)/100)", nombresPVPFormateados),
                        rd(costoConImpuestos),
                        String.format("%s / (1 - (%s)/100) = %s",
                                fmt(costoConImpuestosAntesPVP),
                                detalleConceptosPVP.isEmpty() ? fmt(gastosSobrePVPTotal) : detalleConceptosPVP,
                                fmt(costoConImpuestos)),
                        FormulaCalculoDTO.UNIDAD_MONEDA));
            }
        }

        BigDecimal pvpBase = costoConImpuestos;

        // Paso 10: COSTO_OCULTO_PVP y DESCUENTO (conceptos)
        List<CanalConcepto> conceptosCostoOcultoPvp = conceptos.stream()
                .filter(cc -> cc.getConcepto().getAplicaSobre() == AplicaSobre.COSTO_OCULTO_PVP)
                .collect(Collectors.toList());
        BigDecimal porcentajeCostoOcultoPvp = calcularGastosPorcentaje(conceptosCostoOcultoPvp);
        BigDecimal descuentoConceptos = obtenerDescuentoMaquina(conceptos);

        BigDecimal pvp = pvpBase;

        if (porcentajeCostoOcultoPvp.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal costoOcultoFrac = porcentajeCostoOcultoPvp.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP);
            BigDecimal denominadorCostoOculto = BigDecimal.ONE.subtract(costoOcultoFrac);
            if (denominadorCostoOculto.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("COSTO_OCULTO_PVP es >= 100%, lo cual es inválido");
            }
            pvp = pvp.divide(denominadorCostoOculto, PRECISION_CALCULO, RoundingMode.HALF_UP);
            List<String> nombresConceptosCostoOculto = obtenerNombresConceptos(conceptos, AplicaSobre.COSTO_OCULTO_PVP);
            String nombresCostoOcultoFormateados = formatearNombresConceptos(nombresConceptosCostoOculto);
            String detalleConceptosCostoOculto = formatearDetalleConceptos(conceptosCostoOcultoPvp);
            pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++,
                    "Aplicar costo oculto sobre PVP",
                    String.format("PVP = PVP / (1 - (%s)/100)", nombresCostoOcultoFormateados),
                    rd(pvp),
                    String.format("%s / (1 - (%s)/100) = %s", fmt(pvpBase),
                            detalleConceptosCostoOculto.isEmpty() ? fmt(porcentajeCostoOcultoPvp)
                                    : detalleConceptosCostoOculto,
                            fmt(pvp)),
                    FormulaCalculoDTO.UNIDAD_MONEDA));
        }

        if (descuentoConceptos.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal descuentoFrac = descuentoConceptos.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP);
            BigDecimal factorDescuento = BigDecimal.ONE.subtract(descuentoFrac);
            BigDecimal pvpAntesDescuento = pvp;
            pvp = pvp.multiply(factorDescuento);
            List<CanalConcepto> conceptosDescuento = conceptos.stream()
                    .filter(cc -> cc.getConcepto().getAplicaSobre() == AplicaSobre.DESCUENTO_PORCENTUAL)
                    .collect(Collectors.toList());
            List<String> nombresConceptosDescuento = obtenerNombresConceptos(conceptos, AplicaSobre.DESCUENTO_PORCENTUAL);
            String nombresDescuentoFormateados = formatearNombresConceptos(nombresConceptosDescuento);
            String detalleConceptosDescuento = formatearDetalleConceptos(conceptosDescuento);
            pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++,
                    "Aplicar descuento",
                    String.format("PVP = PVP * (1 - (%s)/100)", nombresDescuentoFormateados),
                    rd(pvp),
                    String.format("%s * (1 - (%s)/100) = %s", fmt(pvpAntesDescuento),
                            detalleConceptosDescuento.isEmpty() ? fmt(descuentoConceptos) : detalleConceptosDescuento,
                            fmt(pvp)),
                    FormulaCalculoDTO.UNIDAD_MONEDA));
        }


        // Paso 13: INFLACION_DIVISOR_FINAL (concepto)
        List<CanalConcepto> conceptosInflacion = conceptos.stream()
                .filter(cc -> cc.getConcepto() != null
                        && cc.getConcepto().getAplicaSobre() == AplicaSobre.INFLACION_DIVISOR_FINAL)
                .collect(Collectors.toList());

        BigDecimal pvpSinInflar = pvp.setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);

        if (!conceptosInflacion.isEmpty()) {
            BigDecimal porcentajeInflacion = calcularGastosPorcentaje(conceptosInflacion);
            if (porcentajeInflacion.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal divisor = BigDecimal.ONE.subtract(porcentajeInflacion.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP));
                if (divisor.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal pvpAntesInflacion = pvpSinInflar;
                    pvpSinInflar = pvpSinInflar.divide(divisor, PRECISION_CALCULO, RoundingMode.HALF_UP)
                            .setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);
                    List<String> nombresConceptosInflacion = obtenerNombresConceptos(conceptos, AplicaSobre.INFLACION_DIVISOR_FINAL);
                    String nombresInflacionFormateados = formatearNombresConceptos(nombresConceptosInflacion);
                    String detalleConceptosInflacion = formatearDetalleConceptos(conceptosInflacion);
                    pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++,
                            "Aplicar inflación",
                            String.format("PVP = PVP / (1 - (%s)/100)", nombresInflacionFormateados),
                            rd(pvpSinInflar),
                            String.format("%s / (1 - (%s)/100) = %s", fmt(pvpAntesInflacion),
                                    detalleConceptosInflacion.isEmpty() ? fmt(porcentajeInflacion)
                                            : detalleConceptosInflacion,
                                    fmt(pvpSinInflar)),
                            FormulaCalculoDTO.UNIDAD_MONEDA));
                }
            }
        } else {
            pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++,
                    "PVP sin inflar",
                    "PVP_SIN_INFLAR",
                    rd(pvpSinInflar),
                    String.format("PVP sin inflar: $%s", fmt(pvpSinInflar)),
                    FormulaCalculoDTO.UNIDAD_MONEDA));
        }

        // Paso 14: Precio inflado (solo si el canal tiene concepto FLAG_APLICAR_PRECIO_INFLADO habilitado)
        BigDecimal pvpInflado = aplicarPrecioInflado(producto.getId(), canalId,
                pvpSinInflar, usaPrecioInflado);
        pvpInflado = pvpInflado.setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);

        if (pvpInflado.compareTo(pvpSinInflar) != 0) {
            Optional<ProductoCanalPrecioInflado> precioInfladoOpt = productoCanalPrecioInfladoRepository
                    .findByProductoIdAndCanalId(producto.getId(), canalId);

            if (precioInfladoOpt.isPresent() && precioInfladoOpt.get().getActivo() != null && precioInfladoOpt.get().getActivo()) {
                PrecioInflado precioInfladoMaestro = precioInfladoOpt.get().getPrecioInflado();
                if (precioInfladoMaestro != null) {
                    TipoPrecioInflado tipo = precioInfladoMaestro.getTipo();
                    BigDecimal valor = precioInfladoMaestro.getValor();
                    String tipoPrecioInflado = tipo.toString();
                    pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++,
                            "Aplicar precio inflado",
                            String.format("PVP_INFLADO = aplicarPrecioInflado(PVP_SIN_INFLAR, tipo=%s, valor=%s)",
                                    tipoPrecioInflado, fmt(valor)),
                            rd(pvpInflado),
                            String.format("Precio inflado %s: %s → %s", tipoPrecioInflado, fmt(pvpSinInflar), fmt(pvpInflado)),
                            FormulaCalculoDTO.UNIDAD_MONEDA));
                }
            }
        }

        // Construir fórmula general dinámicamente
        StringBuilder formulaGeneral = new StringBuilder("PVP = ");
        formulaGeneral.append("((COSTO");
        if (gastosSobreCostoTotal.compareTo(BigDecimal.ZERO) > 0) {
            List<String> nombresCosto = obtenerNombresConceptos(conceptos, AplicaSobre.GASTO_SOBRE_COSTO);
            formulaGeneral.append(" * (1 + ").append(formatearNombresConceptos(nombresCosto)).append("/100)");
        }
        formulaGeneral.append(" * (1 + GANANCIA/100)");
        if (!conceptosEnvio.isEmpty()) {
            formulaGeneral.append(" + ENVIO");
        }
        formulaGeneral.append(") * IMP");
        if (gastosSobreCostoIvaTotal.compareTo(BigDecimal.ZERO) > 0) {
            List<String> nombresCostoIva = obtenerNombresConceptos(conceptos, AplicaSobre.GASTO_POST_IMPUESTOS);
            formulaGeneral.append(" * (1 + ").append(formatearNombresConceptos(nombresCostoIva)).append("/100)");
        }
        if (aplicarCuotas) {
            formulaGeneral.append(" / (1 - COMISIONES_Y_CUOTAS/100)");
        } else if (gastosSobrePVPTotal.compareTo(BigDecimal.ZERO) > 0) {
            List<String> nombresPVP = obtenerNombresConceptos(conceptos, AplicaSobre.COMISION_SOBRE_PVP);
            formulaGeneral.append(" / (1 - ").append(formatearNombresConceptos(nombresPVP)).append("/100)");
        }
        if (porcentajeCostoOcultoPvp.compareTo(BigDecimal.ZERO) > 0) {
            List<String> nombresCostoOculto = obtenerNombresConceptos(conceptos, AplicaSobre.COSTO_OCULTO_PVP);
            formulaGeneral.append(" / (1 - ").append(formatearNombresConceptos(nombresCostoOculto)).append("/100)");
        }
        if (descuentoConceptos.compareTo(BigDecimal.ZERO) > 0) {
            List<String> nombresDescuento = obtenerNombresConceptos(conceptos, AplicaSobre.DESCUENTO_PORCENTUAL);
            formulaGeneral.append(" * (1 - ").append(formatearNombresConceptos(nombresDescuento)).append("/100)");
        }
        if (!conceptosInflacion.isEmpty()) {
            List<String> nombresInflacion = obtenerNombresConceptos(conceptos, AplicaSobre.INFLACION_DIVISOR_FINAL);
            formulaGeneral.append(" / (1 - ").append(formatearNombresConceptos(nombresInflacion)).append("/100)");
        }
        formulaGeneral.append(" + PRECIO_INFLADO");

        Canal canal = canalRepository.findById(canalId)
                .orElseThrow(() -> new NotFoundException("Canal no encontrado"));

        String descripcionCuotas = canalConceptoCuotaRepository.findByCanalIdAndCuotas(canalId, numeroCuotas).stream()
                .map(CanalConceptoCuota::getDescripcion)
                .findFirst()
                .orElse(null);

        return new FormulaCalculoDTO(
                canal.getNombre(),
                numeroCuotas,
                descripcionCuotas,
                formulaGeneral.toString(),
                pasos,
                pvpInflado);
    }

    /**
     * Calcula el precio basándose en el PVP del canal base.
     * Se usa cuando el canal tiene un canalBase configurado.
     * Si hay conceptos SOBRE_PVP_BASE, se aplican como multiplicadores.
     * Si no hay conceptos, el PVP es igual al del canal base.
     * <p>
     * Fórmula: PVP = PVP_CANAL_BASE * (1 + porcentaje1/100) * (1 + porcentaje2/100) * ...
     * <p>
     * El costoProducto se hereda directamente del canal base.
     * Los costosVenta se escalan proporcionalmente con el nuevo PVP.
     *
     * @param producto       El producto
     * @param productoMargen La relación producto-canal (no usado, pero mantenido por consistencia)
     * @param conceptos      Lista de conceptos del canal (se filtran SOBRE_PVP_BASE)
     * @param canalId        ID del canal actual
     * @param numeroCuotas   Número de cuotas (null si contado)
     * @return DTO con el precio calculado
     */
    private PrecioCalculadoDTO calcularPrecioSobrePvpBase(
            Producto producto,
            ProductoMargen productoMargen,
            List<CanalConcepto> conceptos,
            Integer canalId,
            Integer numeroCuotas) {
        Canal canalActual = canalRepository.findById(canalId)
                .orElseThrow(() -> new NotFoundException("Canal no encontrado: " + canalId));
        return calcularPrecioSobrePvpBase(producto, productoMargen, conceptos, canalId, numeroCuotas, canalActual);
    }

    /**
     * Versión optimizada que recibe el Canal pre-cargado.
     */
    private PrecioCalculadoDTO calcularPrecioSobrePvpBase(
            Producto producto,
            ProductoMargen productoMargen,
            List<CanalConcepto> conceptos,
            Integer canalId,
            Integer numeroCuotas,
            Canal canalActual) {

        if (canalActual.getCanalBase() == null) {
            throw new BadRequestException(
                    "El canal '" + canalActual.getNombre() + "' no tiene canal base configurado");
        }

        Integer canalBaseId = canalActual.getCanalBase().getId();

        // Intentar obtener del cache primero (optimización para recálculo masivo)
        PrecioCalculadoDTO precioBase = null;
        Map<String, PrecioCalculadoDTO> cachePrecios = CACHE_PRECIOS_BASE.get();
        if (cachePrecios != null) {
            // Buscar en cache: primero contado (null o 0), luego transferencia (-1)
            String claveContado = producto.getId() + "-" + canalBaseId + "-0";
            String claveNull = producto.getId() + "-" + canalBaseId + "-null";
            precioBase = cachePrecios.get(claveContado);
            if (precioBase == null) {
                precioBase = cachePrecios.get(claveNull);
            }
        }

        // Si no está en cache, calcular (fallback para llamadas individuales).
        // Vía self para que se aplique el @Transactional(readOnly=true) del proxy
        // y los proxies lazy del producto recargado (Mla, etc.) se inicialicen
        // en sesión.
        if (precioBase == null) {
            precioBase = self.calcularPrecioCanal(producto.getId(), canalBaseId, null);
        }

        BigDecimal pvpCanalBase = precioBase.pvp();
        if (pvpCanalBase == null || pvpCanalBase.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("El PVP del canal base es inválido (null o <= 0)");
        }

        // Las métricas de costo se heredan del canal base (defensivo ante null)
        BigDecimal costoProducto = precioBase.costoProducto() != null ? precioBase.costoProducto() : BigDecimal.ZERO;
        BigDecimal costosVentaBase = precioBase.costosVenta() != null ? precioBase.costosVenta() : BigDecimal.ZERO;

        // Separar conceptos en dos listas:
        //   - RESELLER: definen el "corte" del ingreso del dueño
        //   - no-RESELLER (canal propio): solo afectan el PVP final, el dueño captura todo
        List<CanalConcepto> conceptosReseller = new ArrayList<>();
        List<CanalConcepto> conceptosCanalPropio = new ArrayList<>();
        for (CanalConcepto cc : conceptos) {
            if (cc.getConcepto() == null || cc.getConcepto().getAplicaSobre() == null) {
                continue;
            }
            AplicaSobre aplicaSobre = cc.getConcepto().getAplicaSobre();
            if (aplicaSobre == AplicaSobre.CALCULO_SOBRE_CANAL_BASE_RESELLER) {
                conceptosReseller.add(cc);
            } else if (aplicaSobre == AplicaSobre.CALCULO_SOBRE_CANAL_BASE) {
                conceptosCanalPropio.add(cc);
            }
        }
        boolean tieneFactoresReseller = !conceptosReseller.isEmpty();

        // pvpCorte = PVP_BASE * ∏(factores RESELLER)
        // Hasta acá llega el ingreso del dueño cuando hay re-vendedor.
        BigDecimal pvpCorte = pvpCanalBase;
        for (CanalConcepto cc : conceptosReseller) {
            BigDecimal porcentaje = cc.getConcepto().getPorcentaje();
            if (porcentaje != null) {
                BigDecimal factor = BigDecimal.ONE.add(porcentaje.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP));
                pvpCorte = pvpCorte.multiply(factor);
            }
        }

        // pvp = pvpCorte * ∏(factores no-RESELLER)
        // Es el precio final al consumidor (incluye el markup del re-vendedor si aplica).
        BigDecimal pvp = pvpCorte;
        for (CanalConcepto cc : conceptosCanalPropio) {
            BigDecimal porcentaje = cc.getConcepto().getPorcentaje();
            if (porcentaje != null) {
                BigDecimal factor = BigDecimal.ONE.add(porcentaje.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP));
                pvp = pvp.multiply(factor);
            }
        }

        pvp = pvp.setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);
        pvpCorte = pvpCorte.setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);

        // Factor con el que se escalan costosVenta e ingresoNeto del canal base:
        //   - Sin RESELLER  -> escalar con pvp (el dueño captura todo el PVP)
        //   - Con RESELLER  -> escalar con pvpCorte (el dueño solo cobra hasta el corte)
        // costoProducto NO se escala (el costo del producto es el mismo).
        BigDecimal pvpEscala = tieneFactoresReseller ? pvpCorte : pvp;
        BigDecimal costosVenta = costosVentaBase;
        BigDecimal ingresoNetoVendedor;
        if (pvpCanalBase.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal factorEscala = pvpEscala.divide(pvpCanalBase, PRECISION_CALCULO, RoundingMode.HALF_UP);
            costosVenta = costosVentaBase.multiply(factorEscala)
                    .setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);
            BigDecimal ingresoNetoBase = precioBase.ingresoNetoVendedor() != null
                    ? precioBase.ingresoNetoVendedor()
                    : BigDecimal.ZERO;
            ingresoNetoVendedor = ingresoNetoBase.multiply(factorEscala)
                    .setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);
        } else {
            ingresoNetoVendedor = pvpEscala.subtract(costosVenta)
                    .setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);
        }

        // Calcular ganancia: ingresoNetoVendedor - costoProducto
        BigDecimal ganancia = ingresoNetoVendedor.subtract(costoProducto)
                .setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);

        // Calcular margenSobreIngresoNeto: (ganancia / ingresoNetoVendedor) × 100
        BigDecimal margenSobreIngresoNeto = BigDecimal.ZERO;
        if (ingresoNetoVendedor.compareTo(BigDecimal.ZERO) > 0) {
            margenSobreIngresoNeto = clampMargen(ganancia.multiply(CIEN)
                    .divide(ingresoNetoVendedor, PRECISION_RESULTADO, RoundingMode.HALF_UP));
        }

        // margenSobrePvp:
        //   - Sin RESELLER  -> ganancia / pvp (consistente con otros canales)
        //   - Con RESELLER  -> ganancia / pvpCorte (sobre lo que el dueño realmente
        //     cobra; el PVP final infla por el markup del re-vendedor y daría un
        //     margen artificialmente bajo)
        BigDecimal pvpParaMargen = tieneFactoresReseller ? pvpCorte : pvp;
        BigDecimal margenSobrePvp = BigDecimal.ZERO;
        if (pvpParaMargen.compareTo(BigDecimal.ZERO) > 0) {
            margenSobrePvp = clampMargen(ganancia.multiply(CIEN)
                    .divide(pvpParaMargen, PRECISION_RESULTADO, RoundingMode.HALF_UP));
        }

        // Calcular markupPorcentaje: (ganancia / costoProducto) × 100
        BigDecimal markupPorcentaje = BigDecimal.ZERO;
        if (costoProducto.compareTo(BigDecimal.ZERO) > 0) {
            markupPorcentaje = clampMargen(ganancia.multiply(CIEN)
                    .divide(costoProducto, PRECISION_RESULTADO, RoundingMode.HALF_UP));
        }

        // Aplicar precio inflado si existe
        boolean usaPrecioInflado = conceptos.stream()
                .anyMatch(cc -> cc.getConcepto() != null
                        && cc.getConcepto().getAplicaSobre() == AplicaSobre.FLAG_APLICAR_PRECIO_INFLADO);
        BigDecimal pvpInfladoCalc = aplicarPrecioInflado(producto.getId(), canalId, pvp, usaPrecioInflado)
                .setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);
        BigDecimal pvpInflado = pvpInfladoCalc.compareTo(pvp) != 0 ? pvpInfladoCalc : null;

        // Descuentos: para el canal base no recalculamos (no tenemos las bases de
        // costo locales — el PVP viene escalado del padre). Se podría heredar las
        // del canal base padre si más adelante hace falta.
        return new PrecioCalculadoDTO(
                canalId,
                canalActual.getNombre(),
                numeroCuotas,
                pvp,
                pvpInflado,
                costoProducto,
                costosVenta,
                ingresoNetoVendedor,
                ganancia,
                margenSobreIngresoNeto,
                margenSobrePvp,
                markupPorcentaje,
                LocalDateTime.now(),
                null);
    }

    /**
     * Genera la fórmula de cálculo para canales basados en PVP del canal base.
     * Si hay conceptos SOBRE_PVP_BASE, se muestran como multiplicadores.
     * Si no hay conceptos, muestra que el PVP es igual al del canal base.
     *
     * @param producto              El producto
     * @param conceptosSobrePvpBase Conceptos con SOBRE_PVP_BASE (puede ser vacío)
     * @param canalId               ID del canal actual
     * @param numeroCuotas          Número de cuotas (-1=transferencia, 0=contado, >0=cuotas)
     * @return DTO con los pasos de la fórmula
     */
    private FormulaCalculoDTO generarFormulaSobrePvpBase(
            Producto producto,
            List<CanalConcepto> conceptosSobrePvpBase,
            Integer canalId,
            Integer numeroCuotas) {

        List<FormulaCalculoDTO.PasoCalculo> pasos = new ArrayList<>();
        int pasoNumero = 1;

        // Obtener el canal actual y su canal base
        Canal canalActual = canalRepository.findById(canalId).orElse(null);
        String nombreCanalBase = "CANAL_BASE";
        Integer canalBaseId = null;

        if (canalActual != null && canalActual.getCanalBase() != null) {
            nombreCanalBase = canalActual.getCanalBase().getNombre();
            canalBaseId = canalActual.getCanalBase().getId();
        }

        // Paso 1: Calcular PVP del canal base en tiempo real
        BigDecimal pvpCanalBase = BigDecimal.ZERO;
        if (canalBaseId != null) {
            PrecioCalculadoDTO precioBase = calcularPrecioCanal(producto.getId(), canalBaseId, null);
            pvpCanalBase = precioBase.pvp();
        }

        pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++,
                "PVP del canal base",
                String.format("PVP_%s", nombreCanalBase),
                rd(pvpCanalBase),
                String.format("Calculado del canal base: $%s", fmt(pvpCanalBase)),
                FormulaCalculoDTO.UNIDAD_MONEDA));

        // Separar conceptos: RESELLER van primero (definen el corte del ingreso del dueño)
        List<CanalConcepto> conceptosReseller = new ArrayList<>();
        List<CanalConcepto> conceptosCanalPropio = new ArrayList<>();
        for (CanalConcepto cc : conceptosSobrePvpBase) {
            if (cc.getConcepto() == null || cc.getConcepto().getAplicaSobre() == null) {
                continue;
            }
            if (cc.getConcepto().getAplicaSobre() == AplicaSobre.CALCULO_SOBRE_CANAL_BASE_RESELLER) {
                conceptosReseller.add(cc);
            } else {
                conceptosCanalPropio.add(cc);
            }
        }
        boolean tieneFactoresReseller = !conceptosReseller.isEmpty();

        // Paso 2: Aplicar factores RESELLER (si los hay)
        BigDecimal pvp = pvpCanalBase;
        for (CanalConcepto cc : conceptosReseller) {
            String nombreConcepto = cc.getConcepto().getNombre();
            BigDecimal porcentaje = cc.getConcepto().getPorcentaje();
            if (porcentaje != null) {
                BigDecimal factor = BigDecimal.ONE.add(porcentaje.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP));
                BigDecimal pvpAnterior = pvp;
                pvp = pvp.multiply(factor);

                String signo = porcentaje.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++,
                        String.format("Aplicar %s [RESELLER] (%s%s%%)", nombreConcepto, signo, fmt(porcentaje)),
                        String.format("PVP = PVP * (1 + %s/100)", fmt(porcentaje)),
                        rd(pvp),
                        String.format("%s * %s = %s", fmt(pvpAnterior), fmt(factor), fmt(pvp)),
                        FormulaCalculoDTO.UNIDAD_MONEDA));
            }
        }

        // Paso intermedio: corte del ingreso del vendedor (sólo si hay RESELLER)
        if (tieneFactoresReseller) {
            pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++,
                    "Corte de ingreso del vendedor",
                    "PVP_CORTE",
                    rd(pvp),
                    String.format("Hasta acá llega el ingreso del dueño: $%s", fmt(pvp)),
                    FormulaCalculoDTO.UNIDAD_MONEDA));
        }

        // Paso 3: Aplicar factores no-RESELLER (markup del canal hijo)
        for (CanalConcepto cc : conceptosCanalPropio) {
            String nombreConcepto = cc.getConcepto().getNombre();
            BigDecimal porcentaje = cc.getConcepto().getPorcentaje();
            if (porcentaje != null) {
                BigDecimal factor = BigDecimal.ONE.add(porcentaje.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP));
                BigDecimal pvpAnterior = pvp;
                pvp = pvp.multiply(factor);

                String signo = porcentaje.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++,
                        String.format("Aplicar %s (%s%s%%)", nombreConcepto, signo, fmt(porcentaje)),
                        String.format("PVP = PVP * (1 + %s/100)", fmt(porcentaje)),
                        rd(pvp),
                        String.format("%s * %s = %s", fmt(pvpAnterior), fmt(factor), fmt(pvp)),
                        FormulaCalculoDTO.UNIDAD_MONEDA));
            }
        }

        pvp = pvp.setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);

        // Paso final: PVP resultante
        pasos.add(new FormulaCalculoDTO.PasoCalculo(pasoNumero++,
                "PVP Final",
                "PVP",
                rd(pvp),
                String.format("$%s", fmt(pvp)),
                FormulaCalculoDTO.UNIDAD_MONEDA));

        // Fórmula general depende de si hay factores o no
        String formulaGeneral = conceptosSobrePvpBase.isEmpty()
                ? String.format("PVP = PVP_%s", nombreCanalBase)
                : String.format("PVP = PVP_%s * factores", nombreCanalBase);

        String descripcionCuotas = canalConceptoCuotaRepository.findByCanalIdAndCuotas(canalId, numeroCuotas).stream()
                .map(CanalConceptoCuota::getDescripcion)
                .findFirst()
                .orElse(null);

        return new FormulaCalculoDTO(
                canalActual != null ? canalActual.getNombre() : "Canal",
                numeroCuotas,
                descripcionCuotas,
                formulaGeneral,
                pasos,
                pvp);
    }

    private BigDecimal calcularGastosPorcentaje(List<CanalConcepto> conceptos) {
        return conceptos.stream()
                .map(cc -> cc.getConcepto().getPorcentaje())
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Obtiene el porcentaje de cuota aplicable.
     *
     * @param canalId      ID del canal
     * @param numeroCuotas Número de cuotas
     * @return Porcentaje de cuota o ZERO si no se encuentra
     */
    private BigDecimal obtenerPorcentajeCuota(Integer canalId, Integer numeroCuotas) {
        // Usar cache si está disponible (recálculo masivo)
        Map<String, BigDecimal> cache = CACHE_PORCENTAJE_CUOTAS.get();
        if (cache != null) {
            String clave = canalId + "-" + numeroCuotas;
            return cache.getOrDefault(clave, BigDecimal.ZERO);
        }

        // Caché de contexto inline: filtrar las cuotas ya cargadas (sin pegar a BD).
        ContextoCanalCache ctx = obtenerContextoCanal(canalId);
        List<CanalConceptoCuota> cuotasCanal = ctx != null
                ? ctx.cuotas().stream().filter(c -> numeroCuotas != null && numeroCuotas.equals(c.getCuotas())).toList()
                : canalConceptoCuotaRepository.findByCanalIdAndCuotas(canalId, numeroCuotas);

        return cuotasCanal.stream()
                .findFirst()
                .map(CanalConceptoCuota::getPorcentaje)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Obtiene el descuento de máquina desde los conceptos. Busca dinámicamente
     * conceptos con aplica_sobre='DESCUENTO' en la lista ya filtrada por reglas
     * EXCLUIR.
     * <p>
     * NOTA: Los conceptos ya vienen filtrados por canal_concepto_regla desde
     * obtenerConceptosAplicables(). Para productos no-máquina, los conceptos con
     * regla INCLUIR (tag=MAQUINA) ya fueron excluidos de la lista. Este
     * método busca dinámicamente cualquier concepto con
     * aplica_sobre='DESCUENTO' en la lista filtrada, sin hardcodear nombres de
     * conceptos específicos. Si no existe (porque fue excluido), retorna cero.
     *
     * @param conceptos Lista de CanalConcepto ya filtrada por reglas EXCLUIR
     *                  (solo incluye conceptos aplicables)
     * @return Porcentaje total de descuento, o BigDecimal.ZERO si no hay
     * conceptos con aplica_sobre='DESCUENTO'
     */
    private BigDecimal obtenerDescuentoMaquina(List<CanalConcepto> conceptos) {
        return conceptos.stream()
                .filter(cc -> cc.getConcepto() != null
                        && cc.getConcepto().getAplicaSobre() == AplicaSobre.DESCUENTO_PORCENTUAL)
                .map(cc -> cc.getConcepto().getPorcentaje())
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ====================================================
    // HELPERS
    // ====================================================
    private Producto obtenerProducto(Integer productoId) {
        // Usa JOIN FETCH para cargar relaciones necesarias para evaluar reglas de canal_concepto_regla
        return productoRepository.findByIdConRelacionesParaReglas(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
    }

    private ProductoMargen obtenerProductoMargen(Integer productoId) {
        return productoMargenRepository
                .findByProductoId(productoId)
                .orElseThrow(() -> new NotFoundException("No existe configuración de márgenes para este producto"));
    }

    /** Qué margen del producto exige el canal según sus conceptos FLAG_USAR_MARGEN_*. */
    private enum TipoMargenCanal { MAYORISTA, MINORISTA, NINGUNO }

    /**
     * Determina qué margen requiere el canal: el primer concepto FLAG_USAR_MARGEN_MAYORISTA
     * o FLAG_USAR_MARGEN_MINORISTA que aparezca gana; si no hay ninguno, NINGUNO.
     * Centraliza el barrido que antes estaba duplicado en validar/tiene/obtener margen.
     */
    private TipoMargenCanal detectarTipoMargenRequerido(List<CanalConcepto> conceptos) {
        for (CanalConcepto cc : conceptos) {
            if (cc.getConcepto() != null) {
                AplicaSobre aplicaSobre = cc.getConcepto().getAplicaSobre();
                if (aplicaSobre == AplicaSobre.FLAG_USAR_MARGEN_MAYORISTA) return TipoMargenCanal.MAYORISTA;
                if (aplicaSobre == AplicaSobre.FLAG_USAR_MARGEN_MINORISTA) return TipoMargenCanal.MINORISTA;
            }
        }
        return TipoMargenCanal.NINGUNO;
    }

    /**
     * Valida que el producto tenga el margen requerido por el canal.
     * Lanza BadRequestException si el canal requiere un margen que el producto no tiene.
     */
    private void validarMargenRequerido(ProductoMargen productoMargen, List<CanalConcepto> conceptos) {
        TipoMargenCanal tipo = detectarTipoMargenRequerido(conceptos);
        if (tipo == TipoMargenCanal.MAYORISTA) {
            BigDecimal margen = productoMargen != null ? productoMargen.getMargenMayorista() : null;
            if (margen == null || margen.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("El producto no tiene margen mayorista cargado");
            }
        } else if (tipo == TipoMargenCanal.MINORISTA) {
            BigDecimal margen = productoMargen != null ? productoMargen.getMargenMinorista() : null;
            if (margen == null || margen.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("El producto no tiene margen minorista cargado");
            }
        }
    }

    /**
     * Verifica que el producto tenga el margen requerido por la cadena completa de canales:
     * el canal indicado + su canalBase + el canalBase del canalBase, etc. Necesario porque
     * el cálculo de un canal con canalBase invoca recursivamente el cálculo del padre, que
     * puede requerir margen aunque el canal directo no lo haga.
     */
    private boolean tieneMargenParaCadenaCanal(ProductoMargen productoMargen, Integer canalId) {
        return tieneMargenParaCadenaCanal(productoMargen, canalId, new HashSet<>());
    }

    /**
     * Recorre la cadena canal → canalBase → canalBase... y lanza BadRequestException
     * si detecta un ciclo. Sin esta verificación, la recursión en
     * calcularPrecioSobrePvpBase causaría StackOverflowError.
     */
    private void validarCadenaCanalSinCiclos(Canal canal) {
        Set<Integer> visitados = new HashSet<>();
        Canal actual = canal;
        while (actual != null && actual.getCanalBase() != null) {
            if (!visitados.add(actual.getId())) {
                throw new BadRequestException(
                        "Ciclo detectado en la cadena de canales (canalBase). Revise la configuración del canal '"
                                + canal.getNombre() + "'");
            }
            Integer baseId = actual.getCanalBase().getId();
            actual = canalRepository.findById(baseId).orElse(null);
        }
    }

    private boolean tieneMargenParaCadenaCanal(ProductoMargen productoMargen, Integer canalId, Set<Integer> visitados) {
        if (canalId == null || !visitados.add(canalId)) return true;
        ContextoCanalCache ctx = obtenerContextoCanal(canalId);
        Integer canalBaseId;
        List<CanalConcepto> conceptos;
        if (ctx != null) {
            if (ctx.canal() == null) return true;
            canalBaseId = ctx.canalBaseId();
            conceptos = ctx.conceptosCanal();
        } else {
            Canal canal = canalRepository.findById(canalId).orElse(null);
            if (canal == null) return true;
            canalBaseId = canal.getCanalBase() != null ? canal.getCanalBase().getId() : null;
            conceptos = canalConceptoRepository.findByCanalIdWithConceptoFetch(canalId);
        }
        if (!tieneMargenRequerido(productoMargen, conceptos)) return false;
        if (canalBaseId != null) {
            return tieneMargenParaCadenaCanal(productoMargen, canalBaseId, visitados);
        }
        return true;
    }

    /**
     * Verifica si el producto tiene el margen requerido por los conceptos del canal.
     * Retorna true si tiene margen válido, false si no lo tiene.
     * Si el canal no requiere margen específico (no hay conceptos con FLAG_USAR_MARGEN_*),
     * se considera válido aunque productoMargen sea null.
     */
    private boolean tieneMargenRequerido(ProductoMargen productoMargen, List<CanalConcepto> conceptos) {
        TipoMargenCanal tipo = detectarTipoMargenRequerido(conceptos);
        if (tipo == TipoMargenCanal.MAYORISTA) {
            BigDecimal margen = productoMargen != null ? productoMargen.getMargenMayorista() : null;
            return margen != null && margen.compareTo(BigDecimal.ZERO) > 0;
        }
        if (tipo == TipoMargenCanal.MINORISTA) {
            BigDecimal margen = productoMargen != null ? productoMargen.getMargenMinorista() : null;
            return margen != null && margen.compareTo(BigDecimal.ZERO) > 0;
        }
        return true;
    }

    /**
     * Obtiene el margen porcentual según los conceptos del canal.
     * - Si tiene MARGEN_MAYORISTA → usa margenMayorista
     * - Si tiene MARGEN_MINORISTA → usa margenMinorista
     */
    private BigDecimal obtenerMargenPorcentaje(ProductoMargen productoMargen, List<CanalConcepto> conceptos) {
        TipoMargenCanal tipo = detectarTipoMargenRequerido(conceptos);
        if (tipo == TipoMargenCanal.MAYORISTA) {
            return productoMargen.getMargenMayorista() != null ? productoMargen.getMargenMayorista() : BigDecimal.ZERO;
        }
        if (tipo == TipoMargenCanal.MINORISTA) {
            return productoMargen.getMargenMinorista() != null ? productoMargen.getMargenMinorista() : BigDecimal.ZERO;
        }
        // Si el canal no exige ningún margen específico, retorna ZERO
        return BigDecimal.ZERO;
    }

    /**
     * Obtiene todos los conceptos de gasto que aplican al canal según los
     * filtros.
     * <p>
     * Sistema unificado: todos los conceptos se asocian a canales a través de
     * canal_concepto.
     * <p>
     * Lógica de filtrado: - Canal: Un concepto aplica a un canal si está
     * asociado en canal_concepto - Jerarquía de canales: Si un concepto está
     * asignado al canal padre (ej: NUBE), también aplica a todos sus canales
     * hijos (ej: KT HOGAR, KT GASTRO) Nota: Las cuotas ahora se manejan a
     * través de canal_concepto_cuota, no a través del campo cuotas en
     * conceptos_calculo
     * <p>
     * REGLAS DE CANAL_CONCEPTO_REGLA: - Si tipo_regla = INCLUIR: el concepto
     * SOLO aplica si el producto cumple TODAS las condiciones - Si tipo_regla =
     * EXCLUIR: el concepto NO aplica si el producto cumple ALGUNA condición -
     * Condiciones disponibles: id_tipo, id_clasif_gral, id_clasif_gastro,
     * id_marca, tag, tiene_envio - tag: filtra por tag del producto
     * (MAQUINA / REPUESTO / MENAJE; NULL=no filtra; sin tag se trata como MENAJE)
     * <p>
     * ENFOQUE RECOMENDADO PARA KT GASTRO (usar EXCLUIR):
     * <p>
     * 1. Conceptos comunes (no requieren reglas): - MARKETING, EMBALAJE,
     * GASTONUBE, COMISION ML (aplica_sobre='PVP') - IIBB (aplica_sobre='IMP')
     * Estos conceptos aplican a todos los productos del canal.
     * <p>
     * 2. DESCUENTO_MAQUINA (solo para máquinas): - Debe estar en canal_concepto
     * para KT GASTRO - Debe tener regla INCLUIR con tag=MAQUINA (incluir solo
     * cuando es máquina) Ejemplo: tipo_regla='INCLUIR', tag=MAQUINA
     * Resultado: Solo aplica cuando producto.tag = MAQUINA
     * <p>
     * NOTA: REL_ML_KTG se usa para KT GASTRO NO MÁQUINA como concepto con
     * aplica_sobre='MARGEN_PTS' y porcentaje negativo que reduce la ganancia.
     * Debe tener regla EXCLUIR con tag=MAQUINA para que solo aplique a no máquinas.
     * <p>
     * NOTA: Los valores de aplica_sobre para ajuste de margen:
     * - MARGEN_PTS: Suma/resta puntos porcentuales al margen (GAN.MIN.ML + porcentaje)
     * El signo del porcentaje determina si aumenta (+) o reduce (-).
     * Ejemplo: Si GAN.MIN.ML = 60% y porcentaje = +25%, entonces ganancia = 85%
     * Ejemplo: Si GAN.MIN.ML = 60% y porcentaje = -20%, entonces ganancia = 40%
     * - MARGEN_PROP: Ajusta el margen proporcionalmente (MARGEN * (1 + porcentaje/100))
     * El signo del porcentaje determina si aumenta (+) o reduce (-).
     * Ejemplo: Si GAN.MIN.ML = 60% y porcentaje = +10%, entonces ganancia = 66%
     * Ejemplo: Si GAN.MIN.ML = 60% y porcentaje = -10%, entonces ganancia = 54%
     * NOTA: Esta aplicación es consistente para todos los canales.
     * <p>
     * NOTA: Todos los conceptos (comunes y específicos) deben estar en
     * canal_concepto. Las reglas EXCLUIR filtran cuáles conceptos NO aplican
     * según las condiciones del producto. Los métodos de cálculo buscan
     * dinámicamente conceptos por aplica_sobre sin hardcodear qué buscar.
     *
     * @param canalId      ID del canal para filtrar conceptos
     * @param numeroCuotas Número de cuotas (parámetro mantenido por
     *                     compatibilidad, pero ya no se usa para filtrar conceptos)
     * @param producto     El producto para aplicar las reglas de
     *                     canal_concepto_regla
     * @return Lista de conceptos de gasto que aplican según los filtros
     */
    private List<ConceptoCalculo> obtenerConceptosAplicables(Integer canalId, Integer numeroCuotas,
                                                           Producto producto) {
        // Caché de contexto inline si está activo; si no, queries normales a BD.
        // (cada canal tiene sus propios conceptos: NO se heredan del canalBase.)
        ContextoCanalCache ctx = obtenerContextoCanal(canalId);
        List<CanalConcepto> conceptosPorCanal;
        List<CanalConceptoRegla> reglasCanal;
        if (ctx != null) {
            conceptosPorCanal = ctx.conceptosCanal();
            reglasCanal = ctx.reglasConcepto();
        } else {
            conceptosPorCanal = canalConceptoRepository.findByCanalIdWithConceptoFetch(canalId);
            reglasCanal = canalConceptoReglaRepository.findByCanalId(canalId);
        }

        // Aplica las reglas canal_concepto_regla (INCLUIR/EXCLUIR). Reutiliza la MISMA lógica
        // que el recálculo masivo para no mantener dos implementaciones que puedan divergir.
        return filtrarConceptosPorReglas(conceptosPorCanal, reglasCanal, producto);
    }

    /**
     * Verifica si un producto cumple las condiciones de una regla.
     *
     * @param regla    La regla a verificar
     * @param producto El producto a evaluar
     * @return true si el producto cumple TODAS las condiciones especificadas en
     * la regla
     */
    private boolean cumpleCondicionesRegla(CanalConceptoRegla regla, Producto producto) {
        // Si la regla no tiene condiciones, no se aplica (retorna false)
        boolean tieneCondiciones = regla.getTipo() != null
                || regla.getClasifGral() != null
                || regla.getClasifGastro() != null
                || regla.getMarca() != null
                || regla.getTag() != null
                || regla.getTieneEnvio() != null;

        if (!tieneCondiciones) {
            return false;
        }

        // Verificar tipo
        if (regla.getTipo() != null) {
            if (producto.getTipo() == null || !producto.getTipo().getId().equals(regla.getTipo().getId())) {
                return false;
            }
        }

        // Verificar clasificación general
        if (regla.getClasifGral() != null) {
            if (producto.getClasifGral() == null
                    || !producto.getClasifGral().getId().equals(regla.getClasifGral().getId())) {
                return false;
            }
        }

        // Verificar clasificación gastro
        if (regla.getClasifGastro() != null) {
            if (producto.getClasifGastro() == null
                    || !producto.getClasifGastro().getId().equals(regla.getClasifGastro().getId())) {
                return false;
            }
        }

        // Verificar marca
        if (regla.getMarca() != null) {
            if (producto.getMarca() == null || !producto.getMarca().getId().equals(regla.getMarca().getId())) {
                return false;
            }
        }

        // Verificar tag si está especificado en la regla.
        // Si el producto no tiene tag cargado, se trata como MENAJE (default del "else" del Excel).
        if (regla.getTag() != null) {
            Tag productoTag =
                    producto.getTag() != null
                            ? producto.getTag()
                            : Tag.MENAJE;
            if (!regla.getTag().equals(productoTag)) {
                return false;
            }
        }

        // Verificar tieneEnvio: true = producto con precioEnvio > 0, false = sin envío
        if (regla.getTieneEnvio() != null) {
            BigDecimal precioEnvio = producto.getMla() != null ? producto.getMla().getPrecioEnvio() : null;
            boolean productoTieneEnvio = precioEnvio != null && precioEnvio.compareTo(BigDecimal.ZERO) > 0;
            if (regla.getTieneEnvio() != productoTieneEnvio) {
                return false;
            }
        }

        // Si llegamos aquí, el producto cumple TODAS las condiciones especificadas
        return true;
    }

    /**
     * Convierte una lista de ConceptoCalculo a CanalConcepto para mantener
     * compatibilidad con el método calcularPrecioInterno que espera
     * List<CanalConcepto>.
     *
     * @param conceptos Lista de ConceptoCalculo
     * @param canalId   ID del canal para establecer en los objetos CanalConcepto
     *                  temporales
     * @return Lista de CanalConcepto (objetos temporales para compatibilidad)
     */
    private List<CanalConcepto> convertirConceptosACanalConcepto(List<ConceptoCalculo> conceptos, Integer canalId) {
        // Obtener las relaciones canal_concepto para estos conceptos y el canal.
        // Caché inline: reutiliza las relaciones ya cargadas (con concepto fetcheado).
        ContextoCanalCache ctx = obtenerContextoCanal(canalId);
        List<CanalConcepto> relacionesExistentes = ctx != null
                ? ctx.conceptosCanal()
                : canalConceptoRepository.findByCanalId(canalId);
        Map<Integer, CanalConcepto> mapaPorConceptoId = relacionesExistentes.stream()
                .collect(Collectors.toMap(
                        cc -> cc.getConcepto().getId(),
                        cc -> cc,
                        (existing, replacement) -> existing));

        return conceptos.stream()
                .map(concepto -> {
                    // Si ya existe una relación en canal_concepto, usarla
                    CanalConcepto cc = mapaPorConceptoId.get(concepto.getId());
                    if (cc != null) {
                        return cc;
                    }
                    // Si no existe, crear una temporal para compatibilidad con
                    // calcularPrecioInterno
                    CanalConcepto ccTemp = new CanalConcepto();
                    ccTemp.setConcepto(concepto);
                    Canal canal = new Canal();
                    canal.setId(canalId);
                    ccTemp.setCanal(canal);
                    return ccTemp;
                })
                .collect(Collectors.toList());
    }

    /**
     * Obtiene los nombres de los conceptos filtrados por aplicaSobre.
     *
     * @param conceptos   Lista de CanalConcepto
     * @param aplicaSobre Tipo de aplicación sobre el cual filtrar
     * @return Lista de nombres de conceptos (campo concepto de ConceptoCalculo)
     */
    private List<String> obtenerNombresConceptos(List<CanalConcepto> conceptos, AplicaSobre aplicaSobre) {
        return conceptos.stream()
                .filter(cc -> cc.getConcepto() != null
                        && cc.getConcepto().getAplicaSobre() == aplicaSobre)
                .map(cc -> cc.getConcepto().getNombre())
                .filter(nombre -> nombre != null && !nombre.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Formatea una lista de nombres de conceptos concatenándolos con " + ".
     *
     * @param nombres Lista de nombres de conceptos
     * @return String con los nombres concatenados, o cadena vacía si la lista
     * está vacía
     */
    private String formatearNombresConceptos(List<String> nombres) {
        if (nombres == null || nombres.isEmpty()) {
            return "";
        }
        return String.join(" + ", nombres);
    }

    /**
     * Formatea el detalle de conceptos mostrando cada concepto con su porcentaje individual.
     *
     * @param conceptos Lista de conceptos con sus porcentajes
     * @return String con el detalle formateado (ej: "NUBE: 5.00% + MARKETING: 2.50%")
     */
    private String formatearDetalleConceptos(List<CanalConcepto> conceptos) {
        if (conceptos == null || conceptos.isEmpty()) {
            return "";
        }
        return conceptos.stream()
                .filter(cc -> cc.getConcepto() != null && cc.getConcepto().getNombre() != null)
                .map(cc -> String.format("%s: %s%%",
                        cc.getConcepto().getNombre(),
                        fmt(cc.getConcepto().getPorcentaje())))
                .collect(Collectors.joining(" + "));
    }

    /**
     * Redondea un BigDecimal a 2 decimales para mostrar en la fórmula.
     */
    private BigDecimal rd(BigDecimal value) {
        if (value == null) return null;
        return value.setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);
    }

    /**
     * Formatea un BigDecimal a string con 2 decimales.
     */
    private String fmt(BigDecimal value) {
        if (value == null) return "null";
        return value.setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP).toString();
    }

    /**
     * Aplica precio inflado sin incluir porcentaje_inflacion (que ahora es un concepto).
     * Solo aplica precios inflados de producto_canal_precio_inflado si el canal tiene habilitado FLAG_APLICAR_PRECIO_INFLADO.
     *
     * @param productoId     ID del producto
     * @param canalId        ID del canal
     * @param pvp            Precio calculado antes de aplicar precio inflado
     * @param usaPrecioInflado true si el canal tiene un concepto con aplicaSobre=FLAG_APLICAR_PRECIO_INFLADO
     * @return Precio con precio inflado aplicado (o el mismo pvp si no usa precio inflado)
     */
    private BigDecimal aplicarPrecioInflado(Integer productoId, Integer canalId, BigDecimal pvp, boolean usaPrecioInflado) {
        BigDecimal resultado = pvp;

        // Si el canal no tiene habilitado el precio inflado, retornar el pvp sin cambios
        if (!usaPrecioInflado) {
            return resultado;
        }

        // Producto hipotético (simulación): sin id no hay precio inflado asignado, devolver pvp tal cual
        if (productoId == null) {
            return resultado;
        }

        // Aplicar precio inflado de producto_canal_precio_inflado (si existe y está activo)
        Optional<ProductoCanalPrecioInflado> precioInfladoOpt = Optional.empty();
        if (canalId != null) {
            Map<String, ProductoCanalPrecioInflado> cache = CACHE_PRECIOS_INFLADOS.get();
            if (cache != null) {
                ProductoCanalPrecioInflado cached = cache.get(productoId + "-" + canalId);
                precioInfladoOpt = Optional.ofNullable(cached);
            } else {
                precioInfladoOpt = productoCanalPrecioInfladoRepository
                        .findByProductoIdAndCanalId(productoId, canalId);
            }
        }

        if (precioInfladoOpt.isPresent()) {
            ProductoCanalPrecioInflado precioInfladoAsignado = precioInfladoOpt.get();

            // Verificar que el precio inflado esté activo
            if (precioInfladoAsignado.getActivo() != null && precioInfladoAsignado.getActivo()) {
                // Verificar rango de fechas si está configurado
                LocalDate hoy = LocalDate.now();
                boolean fechaValida = true;
                if (precioInfladoAsignado.getFechaDesde() != null && hoy.isBefore(precioInfladoAsignado.getFechaDesde())) {
                    fechaValida = false; // Precio inflado aún no iniciado
                }
                if (precioInfladoAsignado.getFechaHasta() != null && hoy.isAfter(precioInfladoAsignado.getFechaHasta())) {
                    fechaValida = false; // Precio inflado ya expiró
                }

                if (fechaValida) {
                    // Obtener el precio inflado de la tabla maestra
                    PrecioInflado precioInfladoMaestro = precioInfladoAsignado.getPrecioInflado();
                    if (precioInfladoMaestro != null) {
                        TipoPrecioInflado tipo = precioInfladoMaestro.getTipo();
                        BigDecimal valor = precioInfladoMaestro.getValor();

                        switch (tipo) {
                            case MULTIPLICADOR:
                                // Multiplicador: precio * valor
                                // Ejemplo: valor = 1.1 multiplica el precio por 1.1 (aumenta 10%)
                                if (valor.compareTo(BigDecimal.ZERO) > 0) {
                                    resultado = resultado.multiply(valor);
                                }
                                break;

                            case DESCUENTO_PORC:
                                // Descuento/Incremento porcentual según fórmula Excel: PVP / (1 - PROMO)
                                // Si valor = 30 (30%): precio / (1 - 0.30) = precio / 0.70 (incrementa ~42.86%)
                                // Si valor = 10 (10%): precio / (1 - 0.10) = precio / 0.90 (incrementa ~11.11%)
                                // Fórmula: resultado = resultado / (1 - valor/100)
                                if (valor.compareTo(BigDecimal.ZERO) > 0 && valor.compareTo(CIEN) < 0) {
                                    BigDecimal precioInfladoFrac = valor.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP);
                                    BigDecimal denominador = BigDecimal.ONE.subtract(precioInfladoFrac);
                                    if (denominador.compareTo(BigDecimal.ZERO) > 0) {
                                        resultado = resultado.divide(denominador, PRECISION_CALCULO, RoundingMode.HALF_UP);
                                    }
                                }
                                break;

                            case DIVISOR:
                                // Divisor: precio / valor
                                // Ejemplo: valor = 0.9 divide el precio por 0.9 (equivalente a multiplicar por 1.11)
                                if (valor.compareTo(BigDecimal.ZERO) > 0) {
                                    resultado = resultado.divide(valor, PRECISION_CALCULO, RoundingMode.HALF_UP);
                                }
                                break;

                            case PRECIO_FIJO:
                                // Precio fijo: establecer precio directamente
                                // Ejemplo: valor = 100 significa precio fijo de $100
                                if (valor.compareTo(BigDecimal.ZERO) > 0) {
                                    resultado = valor;
                                }
                                break;

                            default:
                                // Tipo desconocido, no modificar resultado
                                break;
                        }
                    }
                }
            }
        }

        return resultado;
    }

    @Override
    @Transactional(readOnly = true)
    public FormulaCalculoDTO obtenerFormulaCalculo(Integer productoId, Integer canalId, Integer numeroCuotas) {
        Producto producto = obtenerProducto(productoId);
        ProductoMargen productoMargen = obtenerProductoMargen(productoId);

        Canal canal = canalRepository.findById(canalId)
                .orElseThrow(() -> new NotFoundException("Canal no encontrado con ID: " + canalId));

        // Validar que las cuotas existen para este canal (si se especificaron)
        if (numeroCuotas != null) {
            List<Integer> cuotasDisponibles = canalConceptoCuotaRepository.findDistinctCuotasByCanalId(canalId);
            if (!cuotasDisponibles.contains(numeroCuotas)) {
                throw new NotFoundException(
                        "Cuotas " + numeroCuotas + " no configuradas para el canal '" + canal.getNombre() + "'. " +
                                "Cuotas disponibles: " + cuotasDisponibles
                );
            }
        }

        // Obtener todos los conceptos que aplican al canal según los filtros
        List<ConceptoCalculo> conceptos = obtenerConceptosAplicables(canalId, numeroCuotas, producto);

        List<CanalConcepto> conceptosCanal = convertirConceptosACanalConcepto(conceptos, canalId);

        return generarFormulaUnificado(producto, productoMargen, conceptosCanal, numeroCuotas, canalId);
    }

    // ====================================================
    // SIMULACIÓN: PRODUCTO HIPOTÉTICO (sin persistir)
    // ====================================================

    @Override
    @Transactional(readOnly = true)
    public FormulaCalculoDTO simularFormulaCalculo(SimulacionPrecioInputDTO input) {
        Canal canal = canalRepository.findById(input.canalId())
                .orElseThrow(() -> new NotFoundException("Canal no encontrado con ID: " + input.canalId()));

        // Limitación: canales con canalBase requieren calcular el PVP del canal padre
        // recursivamente para el producto. El motor de canal base resuelve esto vía
        // self.calcularPrecioCanal(producto.getId(), ...), que falla con id=null porque
        // intenta recargar el producto desde la BD. La simulación no puede soportarlo
        // sin refactorizar el engine para aceptar productos transitorios en cascada.
        if (canal.getCanalBase() != null) {
            throw new BadRequestException(
                    "El canal '" + canal.getNombre() + "' usa canal base ('" + canal.getCanalBase().getNombre() +
                            "'). La simulación de precios para canales con canal base no está soportada porque " +
                            "requiere calcular el precio en el canal padre con un producto real."
            );
        }

        // Validar cuotas si se especificaron (mismo check que el endpoint real)
        if (input.cuotas() != null) {
            List<Integer> cuotasDisponibles = canalConceptoCuotaRepository.findDistinctCuotasByCanalId(input.canalId());
            if (!cuotasDisponibles.contains(input.cuotas())) {
                throw new NotFoundException(
                        "Cuotas " + input.cuotas() + " no configuradas para el canal '" + canal.getNombre() + "'. " +
                                "Cuotas disponibles: " + cuotasDisponibles
                );
            }
        }

        Producto producto = construirProductoHipotetico(input);
        ProductoMargen productoMargen = construirMargenHipotetico(input);

        if (!productoAplicaAlCanal(producto, input.canalId())) {
            throw new BadRequestException(
                    "El producto hipotético no aplica al canal '" + canal.getNombre() +
                            "' según las reglas de canal configuradas. Ajustá los atributos (marca, tipo, tag, etc.) o las reglas del canal."
            );
        }

        List<ConceptoCalculo> conceptos = obtenerConceptosAplicables(input.canalId(), input.cuotas(), producto);
        List<CanalConcepto> conceptosCanal = convertirConceptosACanalConcepto(conceptos, input.canalId());

        return generarFormulaUnificado(producto, productoMargen, conceptosCanal, input.cuotas(), input.canalId());
    }

    @Override
    @Transactional(readOnly = true)
    public SimulacionResultadoDTO simularPrecioCompleto(SimulacionPrecioInputDTO input) {
        Canal canal = canalRepository.findById(input.canalId())
                .orElseThrow(() -> new NotFoundException("Canal no encontrado con ID: " + input.canalId()));

        if (canal.getCanalBase() != null) {
            throw new BadRequestException(
                    "El canal '" + canal.getNombre() + "' usa canal base ('" + canal.getCanalBase().getNombre() +
                            "'). La simulación de precios para canales con canal base no está soportada porque " +
                            "requiere calcular el precio en el canal padre con un producto real."
            );
        }

        if (input.cuotas() != null) {
            List<Integer> cuotasDisponibles = canalConceptoCuotaRepository.findDistinctCuotasByCanalId(input.canalId());
            if (!cuotasDisponibles.contains(input.cuotas())) {
                throw new NotFoundException(
                        "Cuotas " + input.cuotas() + " no configuradas para el canal '" + canal.getNombre() + "'. " +
                                "Cuotas disponibles: " + cuotasDisponibles
                );
            }
        }

        Producto producto = construirProductoHipotetico(input);
        ProductoMargen productoMargen = construirMargenHipotetico(input);

        if (!productoAplicaAlCanal(producto, input.canalId())) {
            throw new BadRequestException(
                    "El producto hipotético no aplica al canal '" + canal.getNombre() +
                            "' según las reglas de canal configuradas. Ajustá los atributos (marca, tipo, tag, etc.) o las reglas del canal."
            );
        }

        List<ConceptoCalculo> conceptos = obtenerConceptosAplicables(input.canalId(), input.cuotas(), producto);
        List<CanalConcepto> conceptosCanal = convertirConceptosACanalConcepto(conceptos, input.canalId());

        // Ejecutamos AMBOS sobre el mismo producto+conceptos para que la fórmula y los
        // indicadores estén sincronizados (mismo PVP, mismos pasos).
        FormulaCalculoDTO formula = generarFormulaUnificado(producto, productoMargen, conceptosCanal, input.cuotas(), input.canalId());
        PrecioCalculadoDTO indicadores = calcularPrecioUnificado(producto, productoMargen, conceptosCanal, input.cuotas(), input.canalId(), canal, null);

        // Si el usuario pasó una regla de precio inflado simulada Y el canal tiene FLAG_APLICAR_PRECIO_INFLADO,
        // calculamos el pvpInflado y lo reemplazamos en los indicadores. El motor real lo busca en
        // producto_canal_precio_inflado por (productoId, canalId), pero como acá el producto es transitorio,
        // recibimos los datos por input.
        boolean canalUsaInflado = conceptosCanal.stream()
                .anyMatch(cc -> cc.getConcepto() != null
                        && cc.getConcepto().getAplicaSobre() == AplicaSobre.FLAG_APLICAR_PRECIO_INFLADO);
        if (canalUsaInflado && input.precioInfladoTipo() != null && input.precioInfladoValor() != null) {
            BigDecimal pvpInfladoSim = aplicarInfladoSimulacion(indicadores.pvp(), input.precioInfladoTipo(), input.precioInfladoValor());
            if (pvpInfladoSim != null && pvpInfladoSim.compareTo(indicadores.pvp()) != 0) {
                indicadores = new PrecioCalculadoDTO(
                        indicadores.canalId(),
                        indicadores.canalNombre(),
                        indicadores.cuotas(),
                        indicadores.pvp(),
                        pvpInfladoSim,
                        indicadores.costoProducto(),
                        indicadores.costosVenta(),
                        indicadores.ingresoNetoVendedor(),
                        indicadores.ganancia(),
                        indicadores.margenSobreIngresoNeto(),
                        indicadores.margenSobrePvp(),
                        indicadores.markupPorcentaje(),
                        indicadores.fechaUltimoCalculo(),
                        indicadores.descuentos()
                );
            }
        }

        return new SimulacionResultadoDTO(formula, indicadores);
    }

    // Replica la lógica de aplicarPrecioInflado pero recibiendo tipo+valor directamente
    // (sin lookup en producto_canal_precio_inflado). Usado por simularPrecioCompleto.
    private BigDecimal aplicarInfladoSimulacion(BigDecimal pvp, TipoPrecioInflado tipo, BigDecimal valor) {
        if (pvp == null || tipo == null || valor == null) return pvp;
        switch (tipo) {
            case MULTIPLICADOR:
                if (valor.compareTo(BigDecimal.ZERO) > 0) {
                    return pvp.multiply(valor).setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);
                }
                return pvp;
            case DESCUENTO_PORC:
                if (valor.compareTo(BigDecimal.ZERO) > 0 && valor.compareTo(CIEN) < 0) {
                    BigDecimal frac = valor.divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP);
                    BigDecimal denom = BigDecimal.ONE.subtract(frac);
                    if (denom.compareTo(BigDecimal.ZERO) > 0) {
                        return pvp.divide(denom, PRECISION_CALCULO, RoundingMode.HALF_UP)
                                .setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);
                    }
                }
                return pvp;
            case DIVISOR:
                if (valor.compareTo(BigDecimal.ZERO) > 0) {
                    return pvp.divide(valor, PRECISION_CALCULO, RoundingMode.HALF_UP)
                            .setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);
                }
                return pvp;
            case PRECIO_FIJO:
                if (valor.compareTo(BigDecimal.ZERO) > 0) {
                    return valor.setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);
                }
                return pvp;
            default:
                return pvp;
        }
    }

    // Construye un Producto transitorio (id=null) con stubs de relaciones para que el motor
    // de cálculo y la evaluación de reglas funcionen sin tocar la BD.
    private Producto construirProductoHipotetico(SimulacionPrecioInputDTO input) {
        Producto p = new Producto();
        p.setCosto(input.costo());
        p.setIva(input.iva());
        p.setTag(input.tag());

        if (input.marcaId() != null) {
            Marca m = new Marca();
            m.setId(input.marcaId());
            p.setMarca(m);
        }
        if (input.tipoId() != null) {
            Tipo t = new Tipo();
            t.setId(input.tipoId());
            p.setTipo(t);
        }
        if (input.clasifGralId() != null) {
            ClasifGral cg = new ClasifGral();
            cg.setId(input.clasifGralId());
            p.setClasifGral(cg);
        }
        if (input.clasifGastroId() != null) {
            ClasifGastro cga = new ClasifGastro();
            cga.setId(input.clasifGastroId());
            p.setClasifGastro(cga);
        }
        if (input.proveedorFinanciacionPorcentaje() != null) {
            Proveedor pr = new Proveedor();
            pr.setFinanciacionPorcentaje(input.proveedorFinanciacionPorcentaje());
            p.setProveedor(pr);
        }
        if (input.mlaPrecioEnvio() != null || input.mlaComisionPorcentaje() != null) {
            Mla mla = new Mla();
            mla.setPrecioEnvio(input.mlaPrecioEnvio());
            mla.setComisionPorcentaje(input.mlaComisionPorcentaje());
            p.setMla(mla);
        }

        return p;
    }

    private ProductoMargen construirMargenHipotetico(SimulacionPrecioInputDTO input) {
        ProductoMargen m = new ProductoMargen();
        m.setMargenMinorista(input.margenMinorista());
        m.setMargenMayorista(input.margenMayorista());
        return m;
    }

    // ====================================================
    // CÁLCULO PARA TODAS LAS CUOTAS
    // ====================================================

    /** Misma justificación que {@link #recalcularYGuardarPrecioCanal} — ver javadoc allí. */
    @Override
    @Transactional(noRollbackFor = {NotFoundException.class, BadRequestException.class})
    public CanalPreciosDTO recalcularYGuardarPrecioCanalTodasCuotas(Integer productoId, Integer canalId) {
        // Excluir el canal cuando: (a) el producto no aplica por canal_regla, o
        // (b) el canal requiere margen mayorista/minorista y el producto no lo tiene
        // (margen null o ≤ 0). En ambos casos borramos precios existentes y devolvemos
        // CanalPreciosDTO vacío sin calcular nada.
        Producto productoParaChequeo = obtenerProducto(productoId);
        boolean excluido = !productoAplicaAlCanal(productoParaChequeo, canalId);
        Optional<ProductoMargen> margenOpt = Optional.empty();
        if (!excluido) {
            margenOpt = productoMargenRepository.findByProductoId(productoId);
            if (!tieneMargenParaCadenaCanal(margenOpt.orElse(null), canalId)) {
                excluido = true;
            }
        }
        if (excluido) {
            List<ProductoCanalPrecio> preciosExistentes =
                    productoCanalPrecioRepository.findByProductoIdAndCanalIdOrderByCuotasAsc(productoId, canalId);
            if (!preciosExistentes.isEmpty()) {
                productoCanalPrecioRepository.deleteAll(preciosExistentes);
            }
            String canalNombre = canalRepository.findById(canalId)
                    .map(Canal::getNombre)
                    .orElse(null);
            return new CanalPreciosDTO(canalId, canalNombre, List.of());
        }

        // OPTIMIZACIÓN: Una sola query en lugar de dos
        // Antes: findDistinctCuotasByCanalId + findByCanalId (2 queries)
        // Después: findByCanalId (1 query) y procesar en memoria.
        // Caché inline: reutiliza las cuotas ya cargadas del contexto del canal.
        ContextoCanalCache ctxCanal = obtenerContextoCanal(canalId);
        List<CanalConceptoCuota> todasLasCuotas = ctxCanal != null
                ? ctxCanal.cuotas()
                : canalConceptoCuotaRepository.findByCanalId(canalId);

        // Extraer cuotas únicas y descripciones en un solo pase
        List<Integer> cuotasCanal = todasLasCuotas.stream()
                .map(CanalConceptoCuota::getCuotas)
                .distinct()
                .toList();

        Map<Integer, String> descripcionesCuotas = todasLasCuotas.stream()
                .collect(Collectors.toMap(
                        CanalConceptoCuota::getCuotas,
                        c -> c.getDescripcion() != null ? c.getDescripcion() : "",
                        (a, b) -> a
                ));

        // Eliminar precios de cuotas que ya no existen en el canal
        if (!cuotasCanal.isEmpty()) {
            productoCanalPrecioRepository.deleteByProductoIdAndCanalIdAndCuotasNotIn(productoId, canalId, cuotasCanal);
        }

        // Preparar el contexto del canal UNA sola vez: producto, margen y conceptos no
        // dependen de la cuota, así evitamos recargarlos por cada cuota (antes el loop
        // delegaba al público recalcularYGuardarPrecioCanal, que rehacía esas queries).
        // La exclusión ya se evaluó arriba, así que el interno calcula sin re-chequear.
        // Reutiliza el margen ya consultado (evita una 2da query en prepararContexto).
        ContextoCalculo ctx = prepararContexto(productoParaChequeo, canalId, null, margenOpt);

        // Solo calcular las cuotas configuradas en canal_concepto_cuota
        List<PrecioCalculadoDTO> preciosCalculados = new ArrayList<>();

        for (Integer cuotas : cuotasCanal) {
            preciosCalculados.add(recalcularYGuardarPrecioCanalInterno(productoParaChequeo, ctx, canalId, cuotas));
        }

        // Obtener info del canal del primer precio
        String canalNombre = preciosCalculados.isEmpty() ? null : preciosCalculados.get(0).canalNombre();

        // Los descuentos aplicables ya vienen calculados en cada PrecioCalculadoDTO
        // (calcularPrecioUnificado los arma con costosVenta exacto). Tomamos los del
        // primer precio (contado) que es la base canónica del canal.
        List<DescuentoAplicableDTO> descuentosCanal = preciosCalculados.isEmpty()
                ? null
                : preciosCalculados.get(0).descuentos();

        // Convertir a PrecioDTO (sin canalId y canalNombre repetidos) + agregar descripcion + descuentos
        final List<DescuentoAplicableDTO> descuentosFinal = descuentosCanal;
        List<PrecioDTO> precios = preciosCalculados.stream()
                .map(p -> new PrecioDTO(
                        p.cuotas(),
                        descripcionesCuotas.getOrDefault(p.cuotas(), ""),
                        p.pvp(),
                        p.pvpInflado(),
                        p.costoProducto(),
                        p.costosVenta(),
                        p.ingresoNetoVendedor(),
                        p.ganancia(),
                        p.margenSobreIngresoNeto(),
                        p.margenSobrePvp(),
                        p.markupPorcentaje(),
                        p.fechaUltimoCalculo(),
                        descuentosFinal
                ))
                .toList();

        return new CanalPreciosDTO(canalId, canalNombre, precios);
    }

    /**
     * Recálculo rápido de un canal completo reutilizando precarga + persistencia diferida
     * (modelo del masivo) restringido a UN canal. Pensado para canales BASE: el cálculo no
     * recursa al canalBase, así que el loop no ejecuta ninguna query (todo sale de los datos
     * precargados y los caches), y los UPDATE/INSERT se agrupan en el flush del commit.
     *
     * <p>Corre dentro de la transacción del caller (el facade, {@code REQUIRES_NEW}): las
     * entidades precargadas quedan managed; modificar las existentes las marca dirty (UPDATE
     * al commit) y las nuevas se persisten. No hace flush/clear intermedio: como no hay
     * queries en el loop, no se dispara auto-flush y el dirty-check ocurre una sola vez.
     *
     * <p>Equivalencia con el camino producto-por-producto: usa el MISMO motor
     * {@code calcularPrecioUnificado}, las MISMAS cuotas (las configuradas, sin la cuota
     * contado=null) y la MISMA lógica de exclusión/borrado del inline
     * ({@code productoAplicaAlCanal} + {@code tieneMargenParaCadenaCanal} → borra los precios
     * del producto excluido; borra las cuotas que ya no existen en el canal).
     */
    @Override
    public int recalcularCanalCompletoBatch(Integer canalId, AvanceCanalCallback onAvance) {
        Canal canal = canalRepository.findById(canalId).orElse(null);
        if (canal == null) return 0;
        validarCadenaCanalSinCiclos(canal);

        // Activa el caché de contexto canal-constante (reglas/conceptos/cuotas) para el canal.
        iniciarCacheContextoCanal(canalId);
        try {
            // Caché de precios inflados (todos): transparente al cálculo, evita una query por
            // producto en aplicarPrecioInflado. Igual que el masivo.
            Map<String, ProductoCanalPrecioInflado> cacheInflados = new HashMap<>();
            for (ProductoCanalPrecioInflado pcpi : productoCanalPrecioInfladoRepository.findAllWithPrecioInfladoFetch()) {
                cacheInflados.put(pcpi.getProducto().getId() + "-" + pcpi.getCanal().getId(), pcpi);
            }
            CACHE_PRECIOS_INFLADOS.set(cacheInflados);

            // Productos + márgenes + snapshot MLA (reutiliza el helper del masivo).
            PrecargaMlaResult precarga = self.precargarProductosConMlaSnapshot();
            List<ProductoMargen> productosConMargenes = precarga.productosConMargenes();
            Map<Integer, Mla> mlaSnapshot = precarga.snapshotPorProductoId();

            // Precios existentes SOLO de este canal, agrupados por producto → (cuotas → precio).
            Map<Integer, Map<Integer, ProductoCanalPrecio>> preciosPorProducto = new HashMap<>();
            for (ProductoCanalPrecio p : productoCanalPrecioRepository.findByCanalIdWithProductoFetch(canalId)) {
                preciosPorProducto
                        .computeIfAbsent(p.getProducto().getId(), k -> new HashMap<>())
                        .put(p.getCuotas(), p);
            }

            // Cuotas configuradas del canal (sin la cuota contado=null — igual que el inline).
            ContextoCanalCache ctxCanal = obtenerContextoCanal(canalId);
            List<Integer> cuotasCanal = ctxCanal.cuotas().stream()
                    .map(CanalConceptoCuota::getCuotas)
                    .distinct()
                    .toList();
            Set<Integer> cuotasSet = new HashSet<>(cuotasCanal);

            int total = productosConMargenes.size();
            int ok = 0, errores = 0, procesados = 0;
            // Entidades a persistir (existentes detached → merge/UPDATE; nuevas → INSERT) y a borrar.
            // Se guardan por lotes de BATCH_SIZE: sin transacción envolvente, cada saveAll commitea
            // en su propia sesión efímera, así el L1 no acumula el catálogo entero (modelo masivo).
            final int BATCH_SIZE = 500;
            List<ProductoCanalPrecio> batch = new ArrayList<>();
            List<ProductoCanalPrecio> paraBorrar = new ArrayList<>();
            log.info("Recálculo batch de canal {}: {} productos", canalId, total);

            for (ProductoMargen productoMargen : productosConMargenes) {
                Producto producto = productoMargen.getProducto();
                Integer productoId = producto.getId();
                Mla snap = mlaSnapshot.get(productoId);
                if (snap != null) producto.setMla(snap);
                Map<Integer, ProductoCanalPrecio> existentes =
                        preciosPorProducto.getOrDefault(productoId, Collections.emptyMap());

                try {
                    // Exclusión idéntica al inline.
                    boolean excluido = !productoAplicaAlCanal(producto, canalId)
                            || !tieneMargenParaCadenaCanal(productoMargen, canalId);
                    if (excluido) {
                        paraBorrar.addAll(existentes.values()); // borra todos los precios del producto en el canal
                        ok++;
                    } else {
                        // Borrar precios de cuotas que ya no existen en el canal.
                        for (Map.Entry<Integer, ProductoCanalPrecio> e : existentes.entrySet()) {
                            if (!cuotasSet.contains(e.getKey())) paraBorrar.add(e.getValue());
                        }
                        // Contexto del canal una sola vez (conceptos no dependen de la cuota).
                        ContextoCalculo ctx = prepararContexto(producto, canalId, null,
                                Optional.of(productoMargen));
                        for (Integer cuotas : cuotasCanal) {
                            PrecioCalculadoDTO dto = calcularPrecioUnificado(ctx.producto(), ctx.productoMargen(),
                                    ctx.conceptosCanal(), cuotas, canalId, ctx.canal(), null);
                            ProductoCanalPrecio pcp = existentes.get(cuotas);
                            if (pcp == null) {
                                pcp = new ProductoCanalPrecio();
                                pcp.setProducto(producto);
                                pcp.setCanal(canal);
                                pcp.setCuotas(cuotas);
                            }
                            aplicarMetricasAPrecio(pcp, dto);
                            batch.add(pcp);
                            if (batch.size() >= BATCH_SIZE) {
                                productoCanalPrecioRepository.saveAll(batch);
                                batch.clear();
                            }
                        }
                        ok++;
                    }
                } catch (Exception e) {
                    errores++;
                    log.warn("Error recalculando producto {} en canal {}: {}", productoId, canalId, e.getMessage());
                }

                procesados++;
                if (procesados % 200 == 0) {
                    if (onAvance != null) onAvance.onAvance(procesados, total, ok, errores);
                    log.info("Recálculo batch de canal {}: {}/{} productos", canalId, procesados, total);
                }
            }

            if (!batch.isEmpty()) productoCanalPrecioRepository.saveAll(batch);
            if (!paraBorrar.isEmpty()) productoCanalPrecioRepository.deleteAll(paraBorrar);

            if (onAvance != null) onAvance.onAvance(total, total, ok, errores);
            log.info("Recálculo batch de canal {} finalizado: {} ok, {} errores", canalId, ok, errores);
            return ok;
        } finally {
            limpiarCacheContextoCanal();
            limpiarCachesRecalculoMasivo();
        }
    }

    /** Copia las métricas del DTO calculado a la entidad de precio (los 10 campos + fecha). */
    private void aplicarMetricasAPrecio(ProductoCanalPrecio pcp, PrecioCalculadoDTO dto) {
        pcp.setPvp(dto.pvp());
        pcp.setPvpInflado(dto.pvpInflado());
        pcp.setCostoProducto(dto.costoProducto());
        pcp.setCostosVenta(dto.costosVenta());
        pcp.setIngresoNetoVendedor(dto.ingresoNetoVendedor());
        pcp.setGanancia(dto.ganancia());
        pcp.setMargenSobreIngresoNeto(dto.margenSobreIngresoNeto());
        pcp.setMargenSobrePvp(dto.margenSobrePvp());
        pcp.setMarkupPorcentaje(dto.markupPorcentaje());
        pcp.setFechaUltimoCalculo(LocalDateTime.now());
    }

    @Override
    public CanalPreciosDTO recalcularYGuardar(Integer productoId, Integer canalId, Integer cuotas) {
        // Validar que el canal existe
        Canal canal = canalRepository.findById(canalId)
                .orElseThrow(() -> new NotFoundException("Canal no encontrado con ID: " + canalId));

        // Validar que las cuotas existen para este canal (si se especificaron)
        if (cuotas != null) {
            List<Integer> cuotasDisponibles = canalConceptoCuotaRepository.findDistinctCuotasByCanalId(canalId);
            if (!cuotasDisponibles.contains(cuotas)) {
                throw new NotFoundException(
                        "Cuotas " + cuotas + " no configuradas para el canal '" + canal.getNombre() + "'. " +
                                "Cuotas disponibles: " + cuotasDisponibles
                );
            }
        }

        if (cuotas == null) {
            return recalcularYGuardarPrecioCanalTodasCuotas(productoId, canalId);
        }

        // Calcular solo para las cuotas especificadas
        PrecioCalculadoDTO precioCalculado = recalcularYGuardarPrecioCanal(productoId, canalId, cuotas);

        // Si el producto no aplica al canal (canal_regla), no hay precio para devolver
        if (precioCalculado == null) {
            return new CanalPreciosDTO(canalId, canal.getNombre(), List.of());
        }

        // Obtener descripción de la cuota
        String descripcion = canalConceptoCuotaRepository.findByCanalId(canalId).stream()
                .filter(c -> c.getCuotas().equals(cuotas))
                .map(c -> c.getDescripcion() != null ? c.getDescripcion() : "")
                .findFirst()
                .orElse("");

        // Los descuentos aplicables ya vienen recalculados dentro de calcularPrecioUnificado
        // con costosVenta exacto sobre el PVP descontado.
        List<DescuentoAplicableDTO> descuentos = precioCalculado.descuentos();

        PrecioDTO precioDTO = new PrecioDTO(
                precioCalculado.cuotas(),
                descripcion,
                precioCalculado.pvp(),
                precioCalculado.pvpInflado(),
                precioCalculado.costoProducto(),
                precioCalculado.costosVenta(),
                precioCalculado.ingresoNetoVendedor(),
                precioCalculado.ganancia(),
                precioCalculado.margenSobreIngresoNeto(),
                precioCalculado.margenSobrePvp(),
                precioCalculado.markupPorcentaje(),
                precioCalculado.fechaUltimoCalculo(),
                descuentos
        );

        return new CanalPreciosDTO(canalId, precioCalculado.canalNombre(), List.of(precioDTO));
    }

    @Override
    @Transactional
    public List<CanalPreciosDTO> recalcularProductoTodosCanales(Integer productoId) {
        // Validar que el producto existe
        productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado con ID: " + productoId));

        // Obtener todos los canales con cuotas configuradas
        List<Integer> canalIds = canalConceptoCuotaRepository.findDistinctCanalIds();

        if (canalIds.isEmpty()) {
            throw new NotFoundException("No hay canales con cuotas configuradas en el sistema");
        }

        // Recalcular para cada canal (todas las cuotas)
        List<CanalPreciosDTO> resultado = canalIds.stream()
                .map(canalId -> recalcularYGuardarPrecioCanalTodasCuotas(productoId, canalId))
                .toList();

        // Recálculo completo y persistido: el precio ya no está desactualizado. Desmarcamos el
        // flag `obsoleto` de las filas del producto (igual que RecalculoPrecioFacade), porque la
        // exportación a canales lo valida —p. ej. NubeExportService rechaza con "precio
        // desactualizado (recalcular antes de subir)" si isObsoleto()—. Sin esto, el front
        // recalculaba antes de subir pero el flag quedaba en true y Nube/ML rechazaban igual.
        recalculoPendienteService.desmarcarProductoCompletado(productoId);

        return resultado;
    }

    @Override
    @Transactional
    public List<CanalPreciosDTO> recalcularProductoTodosCanales(Integer productoId, Integer cuotas) {
        // Validar que el producto existe
        productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado con ID: " + productoId));

        // Obtener todos los canales con cuotas configuradas
        List<Integer> canalIds = canalConceptoCuotaRepository.findDistinctCanalIds();

        if (canalIds.isEmpty()) {
            throw new NotFoundException("No hay canales con cuotas configuradas en el sistema");
        }

        // Recalcular para cada canal solo las cuotas indicadas
        List<CanalPreciosDTO> resultados = new ArrayList<>();
        for (Integer canalId : canalIds) {
            // Verificar si este canal tiene configuradas esas cuotas
            List<Integer> cuotasDisponibles = canalConceptoCuotaRepository.findDistinctCuotasByCanalId(canalId);
            if (cuotasDisponibles.contains(cuotas)) {
                resultados.add(recalcularYGuardar(productoId, canalId, cuotas));
            }
        }

        if (resultados.isEmpty()) {
            throw new NotFoundException("Ningún canal tiene configuradas " + cuotas + " cuotas");
        }

        return resultados;
    }

    // No usar @Transactional porque realentiza considerablemente
    @Override
    public RecalculoMasivoResultDTO recalcularTodos() {
        if (tracker.estaEjecutando()) {
            throw new BadRequestException("Ya hay un recálculo masivo en ejecución");
        }
        log.info("Iniciando recálculo masivo optimizado (sincrónico)...");
        long inicio = System.currentTimeMillis();
        try {
            return ejecutarRecalculoMasivo(inicio);
        } finally {
            limpiarCachesRecalculoMasivo();
        }
    }

    @Override
    public boolean iniciarRecalculoMasivo() {
        if (!tracker.adquirir()) return false;
        resultadoRecalculo = null;
        self.ejecutarRecalculoMasivoAsync();
        return true;
    }

    /**
     * Limpia las ThreadLocals que se setean al inicio de ejecutarRecalculoMasivo.
     * Necesario llamarla SIEMPRE en finally porque el thread (pool @Async o el del
     * request sincrónico) puede reusarse y arrastrar caches stale o memoria viva.
     */
    private void limpiarCachesRecalculoMasivo() {
        CACHE_PORCENTAJE_CUOTAS.remove();
        CACHE_PRECIOS_BASE.remove();
        CACHE_PRECIOS_INFLADOS.remove();
        CACHE_REGLAS_DESCUENTO.remove();
    }

    @Async
    public void ejecutarRecalculoMasivoAsync() {
        try {
            long inicioMs = System.currentTimeMillis();
            RecalculoMasivoResultDTO resultado = ejecutarRecalculoMasivo(inicioMs);
            resultadoRecalculo = resultado;
            String mensaje;
            if (tracker.estaCancelado()) {
                mensaje = "Cancelado por el usuario";
            } else {
                StringBuilder sb = new StringBuilder("Completado: ")
                        .append(resultado.totalPreciosCalculados())
                        .append(" precios calculados");
                if (resultado.errores() > 0) sb.append(", ").append(resultado.errores()).append(" errores");
                if (resultado.productosIgnoradosSinCosto() > 0)
                    sb.append(", ").append(resultado.productosIgnoradosSinCosto()).append(" sin costo");
                if (resultado.productosIgnoradosSinMargen() > 0)
                    sb.append(", ").append(resultado.productosIgnoradosSinMargen()).append(" sin margen");
                mensaje = sb.toString();
            }
            // El masivo recalcula TODO el catálogo: tras éxito, todos los flags de obsolescencia
            // quedan obsoletos en sentido inverso. Limpiar acá (en lugar del controller, ANTES
            // del async) cierra la ventana donde el frontend leería precios "frescos" sin
            // haberse recalculado. Si el masivo crashea, queda marcado para reintento.
            if (!tracker.estaCancelado()) {
                recalculoPendienteService.limpiar();
            }
            tracker.completar(0, resultado.totalPreciosCalculados(), resultado.errores(), mensaje);
        } catch (Exception e) {
            log.error("Error fatal en recálculo masivo async", e);
            tracker.completarConError(e.getMessage());
        } finally {
            // Limpiar ThreadLocals SIEMPRE: el pool @Async reusa threads y dejarlos
            // contaminados arrastra caches stale al próximo recálculo en este thread.
            limpiarCachesRecalculoMasivo();
            // Safety: idempotente. completar/completarConError ya liberaron.
            tracker.liberar();
        }
    }

    @Override
    public ProcesoMasivoEstadoDTO obtenerEstadoRecalculo() {
        return tracker.obtener();
    }

    @Override
    public void cancelarRecalculo() {
        tracker.cancelar();
    }

    @Override
    public RecalculoMasivoResultDTO obtenerResultadoRecalculo() {
        return resultadoRecalculo;
    }

    /**
     * Precarga los productos con MLA y construye un Map<productoId, Mla> con
     * snapshots POJO (id + precioEnvio + comisionPorcentaje). El método masivo
     * no es transaccional, así que los saveAll cierran la sesión Hibernate y
     * los proxies lazy de Mla quedarían desconectados ("Could not initialize
     * proxy [Mla#X] - no session"). Además, el merge implícito durante saveAll
     * puede sobrescribir el campo mla del producto detached con un proxy fresco
     * desde DB. Por eso retornamos un Map separado y reasignamos el snapshot
     * al producto antes de cada cálculo (defensa contra cualquier reset).
     */
    @Transactional(readOnly = true)
    public PrecargaMlaResult precargarProductosConMlaSnapshot() {
        List<ProductoMargen> productosConMargenes = productoMargenRepository.findAllWithProductoFetch();
        Map<Integer, Mla> snapshotPorProductoId = new HashMap<>();
        int conMla = 0;
        for (ProductoMargen pm : productosConMargenes) {
            Producto p = pm.getProducto();
            Mla mlaOrig = p.getMla();
            if (mlaOrig != null) {
                Mla snapshot = new Mla();
                snapshot.setId(mlaOrig.getId());
                snapshot.setPrecioEnvio(mlaOrig.getPrecioEnvio());
                snapshot.setComisionPorcentaje(mlaOrig.getComisionPorcentaje());
                snapshotPorProductoId.put(p.getId(), snapshot);
                p.setMla(snapshot);
                conMla++;
            }
        }
        log.info("Snapshot de Mla aplicado a {} productos (de {} totales)", conMla, productosConMargenes.size());
        return new PrecargaMlaResult(productosConMargenes, snapshotPorProductoId);
    }

    public record PrecargaMlaResult(
            List<ProductoMargen> productosConMargenes,
            Map<Integer, Mla> snapshotPorProductoId) {}

    private RecalculoMasivoResultDTO ejecutarRecalculoMasivo(long inicio) {

        // =====================================================
        // PASO 1: PRECARGAR TODOS LOS DATOS EN MEMORIA
        // =====================================================
        log.info("Precargando datos en memoria...");

        // Productos con márgenes (con snapshot de Mla para sobrevivir a los flush)
        PrecargaMlaResult precarga = self.precargarProductosConMlaSnapshot();
        List<ProductoMargen> productosConMargenes = precarga.productosConMargenes();
        Map<Integer, Mla> mlaSnapshotPorProductoId = precarga.snapshotPorProductoId();
        List<Canal> todosLosCanales = canalRepository.findAll();

        // Cache de conceptos por canal (evita N consultas)
        Map<Integer, List<CanalConcepto>> conceptosPorCanal = new HashMap<>();
        for (Canal canal : todosLosCanales) {
            conceptosPorCanal.put(canal.getId(), canalConceptoRepository.findByCanalIdWithConceptoFetch(canal.getId()));
        }

        // Cache de cuotas por canal
        Map<Integer, List<Integer>> cuotasPorCanal = new HashMap<>();
        Map<Integer, List<CanalConceptoCuota>> cuotasConfigPorCanal = new HashMap<>();
        for (Canal canal : todosLosCanales) {
            cuotasPorCanal.put(canal.getId(), canalConceptoCuotaRepository.findDistinctCuotasByCanalId(canal.getId()));
            cuotasConfigPorCanal.put(canal.getId(), canalConceptoCuotaRepository.findByCanalId(canal.getId()));
        }

        // Cache de reglas por canal
        Map<Integer, List<CanalConceptoRegla>> reglasPorCanal = new HashMap<>();
        for (Canal canal : todosLosCanales) {
            reglasPorCanal.put(canal.getId(), canalConceptoReglaRepository.findByCanalIdWithRelationsFetch(canal.getId()));
        }

        // Cache de reglas a nivel canal (canal_regla) para decidir si el producto aplica al canal
        Map<Integer, List<CanalRegla>> canalReglasPorCanal = new HashMap<>();
        for (Canal canal : todosLosCanales) {
            canalReglasPorCanal.put(canal.getId(), canalReglaRepository.findByCanalIdWithRelationsFetch(canal.getId()));
        }

        // Cache de reglas de descuento por canal: las consume calcularDescuentosAplicables
        // (vía CACHE_REGLAS_DESCUENTO) para no re-consultarlas por cada producto y cuota.
        Map<Integer, List<ReglaDescuento>> reglasDescuentoPorCanal = new HashMap<>();
        for (Canal canal : todosLosCanales) {
            reglasDescuentoPorCanal.put(canal.getId(),
                    reglaDescuentoRepository.findByCanalIdAndActivoTrueOrderByPrioridadAsc(canal.getId()));
        }
        CACHE_REGLAS_DESCUENTO.set(reglasDescuentoPorCanal);

        // Determinar qué tipo de margen usa cada canal
        Map<Integer, AplicaSobre> tipoMargenPorCanal = new HashMap<>();
        for (Canal canal : todosLosCanales) {
            List<CanalConcepto> conceptos = conceptosPorCanal.get(canal.getId());
            for (CanalConcepto cc : conceptos) {
                if (cc.getConcepto() != null) {
                    AplicaSobre aplicaSobre = cc.getConcepto().getAplicaSobre();
                    if (aplicaSobre == AplicaSobre.FLAG_USAR_MARGEN_MAYORISTA || aplicaSobre == AplicaSobre.FLAG_USAR_MARGEN_MINORISTA) {
                        tipoMargenPorCanal.put(canal.getId(), aplicaSobre);
                        break;
                    }
                }
            }
        }

        // Precargar TODOS los precios existentes (evita ~100k consultas individuales)
        log.info("Precargando precios existentes...");
        List<ProductoCanalPrecio> todosLosPrecios = productoCanalPrecioRepository.findAll();
        Map<String, ProductoCanalPrecio> preciosExistentes = todosLosPrecios.stream()
                .collect(Collectors.toMap(
                        p -> p.getProducto().getId() + "-" + p.getCanal().getId() + "-" + p.getCuotas(),
                        p -> p,
                        (a, b) -> a
                ));
        log.info("Precios existentes cargados: {}", preciosExistentes.size());

        // Cache de porcentajes de cuotas (evita ~100k consultas)
        Map<String, BigDecimal> cachePorcentajeCuotas = new HashMap<>();
        for (Map.Entry<Integer, List<CanalConceptoCuota>> entry : cuotasConfigPorCanal.entrySet()) {
            Integer canalId = entry.getKey();
            for (CanalConceptoCuota cuotaConfig : entry.getValue()) {
                String clave = canalId + "-" + cuotaConfig.getCuotas();
                cachePorcentajeCuotas.put(clave, cuotaConfig.getPorcentaje() != null ? cuotaConfig.getPorcentaje() : BigDecimal.ZERO);
            }
        }

        log.info("Datos precargados: {} productos, {} canales, {} conceptos, {} precios existentes, {} config cuotas",
                productosConMargenes.size(), todosLosCanales.size(),
                conceptosPorCanal.values().stream().mapToInt(List::size).sum(),
                preciosExistentes.size(), cachePorcentajeCuotas.size());

        // Cache de precios inflados (evita ~33k consultas individuales)
        Map<String, ProductoCanalPrecioInflado> cachePreciosInflados = new HashMap<>();
        List<ProductoCanalPrecioInflado> todosLosPreciosInflados = productoCanalPrecioInfladoRepository.findAllWithPrecioInfladoFetch();
        for (ProductoCanalPrecioInflado pcpi : todosLosPreciosInflados) {
            String clave = pcpi.getProducto().getId() + "-" + pcpi.getCanal().getId();
            cachePreciosInflados.put(clave, pcpi);
        }
        log.info("Precios inflados cargados: {}", cachePreciosInflados.size());

        // Setear caches en ThreadLocal
        CACHE_PRECIOS_INFLADOS.set(cachePreciosInflados);
        CACHE_PORCENTAJE_CUOTAS.set(cachePorcentajeCuotas);

        // Separar canales en "base" (sin canalBase) y "dependientes" (con canalBase)
        List<Canal> canalesBase = todosLosCanales.stream()
                .filter(c -> c.getCanalBase() == null)
                .collect(Collectors.toList());
        List<Canal> canalesDependientes = todosLosCanales.stream()
                .filter(c -> c.getCanalBase() != null)
                .collect(Collectors.toList());

        log.info("Canales base: {}, canales dependientes: {}", canalesBase.size(), canalesDependientes.size());

        // Inicializar cache de precios calculados para canales base
        Map<String, PrecioCalculadoDTO> cachePrecios = new HashMap<>();
        CACHE_PRECIOS_BASE.set(cachePrecios);

        // =====================================================
        // PASO 2: CALCULAR PRECIOS (dos pasadas)
        // =====================================================
        int totalProductos = productosConMargenes.size();
        int totalRecalculados = 0;
        int productosCalculados = 0;
        int errores = 0;
        int productosSinCosto = 0;
        int productosSinMargen = 0;
        Set<Integer> productosYaContadosSinMargen = new HashSet<>();
        Set<Integer> productosYaContadosConError = new HashSet<>();
        // Desglose por tipo de margen requerido: un producto puede faltar de ambos
        // si distintos canales base usan mayorista y minorista respectivamente.
        Set<Integer> productosSinMargenMayorista = new HashSet<>();
        Set<Integer> productosSinMargenMinorista = new HashSet<>();
        List<String> skusSinMargenMayorista = new ArrayList<>();
        List<String> skusSinMargenMinorista = new ArrayList<>();
        List<String> skusSinCosto = new ArrayList<>();
        List<String> skusSinMargen = new ArrayList<>();
        List<String> skusConErrores = new ArrayList<>();
        List<ProductoCanalPrecio> batchParaGuardar = new ArrayList<>();
        List<ProductoCanalPrecio> preciosParaBorrar = new ArrayList<>();
        int BATCH_SIZE = 500;

        // ==============================================================
        // PASADA 1: Calcular precios de canales BASE y guardar en cache
        // ==============================================================
        log.info("Pasada 1: Calculando canales base...");
        for (ProductoMargen productoMargen : productosConMargenes) {
            Producto producto = productoMargen.getProducto();

            // Restaurar snapshot de Mla por si saveAll del batch anterior lo desconectó
            Mla mlaSnap = mlaSnapshotPorProductoId.get(producto.getId());
            if (mlaSnap != null) {
                producto.setMla(mlaSnap);
            }

            // Ignorar productos sin costo
            BigDecimal costo = producto.getCosto();
            if (costo == null || costo.compareTo(BigDecimal.ZERO) <= 0) {
                productosSinCosto++;
                skusSinCosto.add(producto.getSku());
                continue;
            }

            Integer productoId = producto.getId();

            for (Canal canal : canalesBase) {
                Integer canalId = canal.getId();

                // Evaluar reglas a nivel canal (canal_regla): si el producto no aplica,
                // saltar el cálculo y programar la eliminación de precios existentes.
                if (!CanalReglaServiceImpl.evaluarReglas(canalReglasPorCanal.get(canalId), producto)) {
                    List<Integer> cuotasDelCanalExc = cuotasPorCanal.get(canalId);
                    if (cuotasDelCanalExc != null) {
                        for (Integer cuotasExc : cuotasDelCanalExc) {
                            ProductoCanalPrecio existente = preciosExistentes.get(productoId + "-" + canalId + "-" + cuotasExc);
                            if (existente != null && existente.getId() != null) {
                                preciosParaBorrar.add(existente);
                            }
                        }
                    }
                    continue;
                }

                // Verificar si el canal tiene margen válido para este producto
                AplicaSobre tipoMargen = tipoMargenPorCanal.get(canalId);
                if (tipoMargen == null) {
                    continue;
                }

                BigDecimal margen = (tipoMargen == AplicaSobre.FLAG_USAR_MARGEN_MAYORISTA)
                        ? productoMargen.getMargenMayorista()
                        : productoMargen.getMargenMinorista();

                if (margen == null || margen.compareTo(BigDecimal.ZERO) <= 0) {
                    // Desglosar según el margen que ESTE canal realmente necesita,
                    // así solo se reporta el faltante que efectivamente se debe usar.
                    if (tipoMargen == AplicaSobre.FLAG_USAR_MARGEN_MAYORISTA) {
                        if (productosSinMargenMayorista.add(productoId)) {
                            skusSinMargenMayorista.add(producto.getSku());
                        }
                    } else {
                        if (productosSinMargenMinorista.add(productoId)) {
                            skusSinMargenMinorista.add(producto.getSku());
                        }
                    }
                    // Contar solo una vez por producto (total combinado)
                    if (productosYaContadosSinMargen.add(productoId)) {
                        productosSinMargen++;
                        skusSinMargen.add(producto.getSku());
                    }
                    continue;
                }

                List<Integer> cuotasDelCanal = cuotasPorCanal.get(canalId);
                if (cuotasDelCanal == null || cuotasDelCanal.isEmpty()) {
                    continue;
                }

                List<CanalConcepto> conceptosCanal = conceptosPorCanal.get(canalId);
                List<CanalConceptoRegla> reglasCanal = reglasPorCanal.get(canalId);
                List<ConceptoCalculo> conceptosFiltrados = filtrarConceptosPorReglas(conceptosCanal, reglasCanal, producto);
                List<CanalConcepto> conceptosParaCalculo = convertirConceptosACanalConceptoOptimizado(conceptosFiltrados, conceptosCanal);

                // Calculamos el precio "contado" (cuotas=null) además de las cuotas configuradas:
                // los canales dependientes lo necesitan en cache para evitar el fallback a
                // calcularPrecioCanal (que self-invoca, pierde @Transactional y dispara
                // LazyInitializationException sobre Mla).
                List<Integer> cuotasParaCalcular = new ArrayList<>(cuotasDelCanal.size() + 1);
                cuotasParaCalcular.add(null);
                cuotasParaCalcular.addAll(cuotasDelCanal);

                for (Integer cuotas : cuotasParaCalcular) {
                    try {
                        PrecioCalculadoDTO precioCalculado = calcularPrecioUnificado(
                                producto, productoMargen, conceptosParaCalculo, cuotas, canalId, canal, null);

                        // Guardar en cache para uso por canales dependientes (incluye -null)
                        String claveCache = productoId + "-" + canalId + "-" + cuotas;
                        cachePrecios.put(claveCache, precioCalculado);

                        // El cálculo con cuotas=null es solo para el cache: no se persiste.
                        if (cuotas == null) {
                            continue;
                        }

                        // Buscar precio existente y actualizar
                        String clavePrecio = productoId + "-" + canalId + "-" + cuotas;
                        ProductoCanalPrecio pcp = preciosExistentes.get(clavePrecio);
                        if (pcp == null) {
                            pcp = new ProductoCanalPrecio();
                            pcp.setProducto(producto);
                            pcp.setCanal(canal);
                            pcp.setCuotas(cuotas);
                        }

                        pcp.setPvp(precioCalculado.pvp());
                        pcp.setPvpInflado(precioCalculado.pvpInflado());
                        pcp.setCostoProducto(precioCalculado.costoProducto());
                        pcp.setCostosVenta(precioCalculado.costosVenta());
                        pcp.setIngresoNetoVendedor(precioCalculado.ingresoNetoVendedor());
                        pcp.setGanancia(precioCalculado.ganancia());
                        pcp.setMargenSobreIngresoNeto(precioCalculado.margenSobreIngresoNeto());
                        pcp.setMargenSobrePvp(precioCalculado.margenSobrePvp());
                        pcp.setMarkupPorcentaje(precioCalculado.markupPorcentaje());
                        pcp.setFechaUltimoCalculo(LocalDateTime.now());

                        batchParaGuardar.add(pcp);
                        totalRecalculados++;

                        if (batchParaGuardar.size() >= BATCH_SIZE) {
                            productoCanalPrecioRepository.saveAll(batchParaGuardar);
                            batchParaGuardar.clear();
                        }

                    } catch (Exception e) {
                        errores++;
                        // Registrar SKU con error solo una vez por producto
                        if (!productosYaContadosConError.contains(productoId)) {
                            productosYaContadosConError.add(productoId);
                            skusConErrores.add(producto.getSku());
                        }
                        if (errores <= 10) {
                            log.warn("Error calculando producto {} en canal base {} cuotas {}: {}",
                                    productoId, canalId, cuotas, e.getMessage());
                        }
                    }
                }
            }

            productosCalculados++;
            if (productosCalculados % 500 == 0) {
                long tiempoTranscurrido = (System.currentTimeMillis() - inicio) / 1000;
                double velocidad = productosCalculados / (double) Math.max(1, tiempoTranscurrido);
                log.info("Pasada 1: {}/{} productos, {} precios - {} prod/seg",
                        productosCalculados, totalProductos, totalRecalculados, String.format("%.1f", velocidad));
                tracker.actualizar(totalProductos * 2, productosCalculados, totalRecalculados, errores,
                        "Pasada 1: " + productosCalculados + "/" + totalProductos);
                if (tracker.estaCancelado()) {
                    log.info("Recálculo masivo cancelado por el usuario en pasada 1");
                    break;
                }
            }
        }

        // Guardar batch pendiente de pasada 1
        if (!batchParaGuardar.isEmpty()) {
            log.info("Guardando batch final de {} precios en BD (Pasada 1)", batchParaGuardar.size());
            productoCanalPrecioRepository.saveAll(batchParaGuardar);
            batchParaGuardar.clear();
        }

        log.info("Pasada 1 completada: {} precios en cache", cachePrecios.size());

        // ==============================================================
        // PASADA 2: Calcular precios de canales DEPENDIENTES usando cache
        // ==============================================================
        // Si el usuario canceló durante pasada 1, saltamos pasada 2 entera para
        // no seguir quemando CPU en cálculos que ya no se quieren.
        // (Los proxies lazy de Mla ya están protegidos por el snapshot que se
        // hizo en precargarProductosConMlaSnapshot().)
        if (tracker.estaCancelado()) {
            log.info("Pasada 2 saltada por cancelación previa");
        } else {
        log.info("Pasada 2: Calculando canales dependientes...");
        productosCalculados = 0;

        for (ProductoMargen productoMargen : productosConMargenes) {
            Producto producto = productoMargen.getProducto();

            // Restaurar snapshot de Mla por si saveAll del batch anterior lo desconectó
            Mla mlaSnap2 = mlaSnapshotPorProductoId.get(producto.getId());
            if (mlaSnap2 != null) {
                producto.setMla(mlaSnap2);
            }

            BigDecimal costo = producto.getCosto();
            if (costo == null || costo.compareTo(BigDecimal.ZERO) <= 0) {
                productosCalculados++;
                continue;
            }

            Integer productoId = producto.getId();

            for (Canal canal : canalesDependientes) {
                Integer canalId = canal.getId();

                // Evaluar reglas a nivel canal (canal_regla): si el producto no aplica,
                // saltar el cálculo y programar la eliminación de precios existentes.
                if (!CanalReglaServiceImpl.evaluarReglas(canalReglasPorCanal.get(canalId), producto)) {
                    List<Integer> cuotasDelCanalExc = cuotasPorCanal.get(canalId);
                    if (cuotasDelCanalExc != null) {
                        for (Integer cuotasExc : cuotasDelCanalExc) {
                            ProductoCanalPrecio existente = preciosExistentes.get(productoId + "-" + canalId + "-" + cuotasExc);
                            if (existente != null && existente.getId() != null) {
                                preciosParaBorrar.add(existente);
                            }
                        }
                    }
                    continue;
                }

                // Los canales dependientes no usan tipoMargen directamente, heredan del base.
                // Si el canal base (o cualquier ancestro en la cadena) requiere un margen que
                // el producto no tiene, saltar — el calculo recursivo del base fallaria igual
                // y generaria un error inutil. El producto ya esta contado como "sin margen"
                // en pasada 1, no se re-cuenta aqui.
                AplicaSobre tipoMargenCadena = null;
                Canal ancestro = canal.getCanalBase();
                while (ancestro != null && tipoMargenCadena == null) {
                    tipoMargenCadena = tipoMargenPorCanal.get(ancestro.getId());
                    ancestro = ancestro.getCanalBase();
                }
                if (tipoMargenCadena != null) {
                    BigDecimal margenCadena = (tipoMargenCadena == AplicaSobre.FLAG_USAR_MARGEN_MAYORISTA)
                            ? productoMargen.getMargenMayorista()
                            : productoMargen.getMargenMinorista();
                    if (margenCadena == null || margenCadena.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                }

                List<Integer> cuotasDelCanal = cuotasPorCanal.get(canalId);
                if (cuotasDelCanal == null || cuotasDelCanal.isEmpty()) {
                    continue;
                }

                List<CanalConcepto> conceptosCanal = conceptosPorCanal.get(canalId);
                List<CanalConceptoRegla> reglasCanal = reglasPorCanal.get(canalId);
                List<ConceptoCalculo> conceptosFiltrados = filtrarConceptosPorReglas(conceptosCanal, reglasCanal, producto);
                List<CanalConcepto> conceptosParaCalculo = convertirConceptosACanalConceptoOptimizado(conceptosFiltrados, conceptosCanal);

                for (Integer cuotas : cuotasDelCanal) {
                    try {
                        PrecioCalculadoDTO precioCalculado = calcularPrecioUnificado(
                                producto, productoMargen, conceptosParaCalculo, cuotas, canalId, canal, null);

                        String clavePrecio = productoId + "-" + canalId + "-" + cuotas;
                        ProductoCanalPrecio pcp = preciosExistentes.get(clavePrecio);
                        if (pcp == null) {
                            pcp = new ProductoCanalPrecio();
                            pcp.setProducto(producto);
                            pcp.setCanal(canal);
                            pcp.setCuotas(cuotas);
                        }

                        pcp.setPvp(precioCalculado.pvp());
                        pcp.setPvpInflado(precioCalculado.pvpInflado());
                        pcp.setCostoProducto(precioCalculado.costoProducto());
                        pcp.setCostosVenta(precioCalculado.costosVenta());
                        pcp.setIngresoNetoVendedor(precioCalculado.ingresoNetoVendedor());
                        pcp.setGanancia(precioCalculado.ganancia());
                        pcp.setMargenSobreIngresoNeto(precioCalculado.margenSobreIngresoNeto());
                        pcp.setMargenSobrePvp(precioCalculado.margenSobrePvp());
                        pcp.setMarkupPorcentaje(precioCalculado.markupPorcentaje());
                        pcp.setFechaUltimoCalculo(LocalDateTime.now());

                        batchParaGuardar.add(pcp);
                        totalRecalculados++;

                        if (batchParaGuardar.size() >= BATCH_SIZE) {
                            productoCanalPrecioRepository.saveAll(batchParaGuardar);
                            batchParaGuardar.clear();
                        }

                    } catch (Exception e) {
                        errores++;
                        // Registrar SKU con error solo una vez por producto
                        if (!productosYaContadosConError.contains(productoId)) {
                            productosYaContadosConError.add(productoId);
                            skusConErrores.add(producto.getSku());
                        }
                        if (errores <= 10) {
                            log.warn("Error calculando producto {} en canal dependiente {} cuotas {}: {}",
                                    productoId, canalId, cuotas, e.getMessage());
                        }
                    }
                }
            }

            productosCalculados++;
            if (productosCalculados % 500 == 0) {
                long tiempoTranscurrido = (System.currentTimeMillis() - inicio) / 1000;
                double velocidad = productosCalculados / (double) Math.max(1, tiempoTranscurrido);
                int restantes = totalProductos - productosCalculados;
                long tiempoEstimado = (long) (restantes / Math.max(0.1, velocidad));
                log.info("Pasada 2: {}/{} productos ({} precios, {} errores) - {} prod/seg - ETA: {}s",
                        productosCalculados, totalProductos, totalRecalculados, errores,
                        String.format("%.1f", velocidad), tiempoEstimado);
                tracker.actualizar(totalProductos * 2, totalProductos + productosCalculados,
                        totalRecalculados, errores,
                        "Pasada 2: " + productosCalculados + "/" + totalProductos);
                if (tracker.estaCancelado()) {
                    log.info("Recálculo masivo cancelado por el usuario en pasada 2");
                    break;
                }
            }
        }

        // Guardar el último batch
        if (!batchParaGuardar.isEmpty()) {
            log.info("Guardando batch final de {} precios en BD (Pasada 2)", batchParaGuardar.size());
            productoCanalPrecioRepository.saveAll(batchParaGuardar);
        }
        } // fin de else (no cancelado antes de pasada 2)

        // Borrar precios de combinaciones (producto, canal) excluidas por canal_regla
        if (!preciosParaBorrar.isEmpty()) {
            log.info("Borrando {} precios de combinaciones excluidas por canal_regla", preciosParaBorrar.size());
            productoCanalPrecioRepository.deleteAll(preciosParaBorrar);
            preciosParaBorrar.clear();
        }

        // ThreadLocals se limpian en finally del caller (recalcularTodos / ejecutarRecalculoMasivoAsync)
        // para que también queden limpias ante excepciones.

        long tiempoTotal = (System.currentTimeMillis() - inicio) / 1000;
        log.info("Recálculo masivo completado en {}s: {}/{} productos, {} precios calculados, {} errores, {} sin costo, {} sin margen",
                tiempoTotal, productosCalculados, totalProductos, totalRecalculados, errores, productosSinCosto, productosSinMargen);

        return new RecalculoMasivoResultDTO(
                totalRecalculados,
                productosSinCosto,
                productosSinMargen,
                errores,
                skusSinCosto,
                skusSinMargen,
                skusSinMargenMayorista,
                skusSinMargenMinorista,
                skusConErrores
        );
    }

    /**
     * Filtra conceptos según las reglas del canal (versión optimizada sin consultas).
     */
    private List<ConceptoCalculo> filtrarConceptosPorReglas(
            List<CanalConcepto> conceptosCanal,
            List<CanalConceptoRegla> reglasCanal,
            Producto producto) {

        return conceptosCanal.stream()
                .map(CanalConcepto::getConcepto)
                .filter(concepto -> {
                    List<CanalConceptoRegla> reglasConcepto = reglasCanal.stream()
                            .filter(regla -> regla.getConcepto().getId().equals(concepto.getId()))
                            .collect(Collectors.toList());

                    for (CanalConceptoRegla regla : reglasConcepto) {
                        boolean cumpleCondiciones = cumpleCondicionesRegla(regla, producto);

                        if (regla.getTipoRegla() == TipoRegla.INCLUIR && !cumpleCondiciones) {
                            return false;
                        }
                        if (regla.getTipoRegla() == TipoRegla.EXCLUIR && cumpleCondiciones) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    /**
     * Convierte conceptos a CanalConcepto usando datos ya cargados (sin consultas).
     */
    private List<CanalConcepto> convertirConceptosACanalConceptoOptimizado(
            List<ConceptoCalculo> conceptos,
            List<CanalConcepto> relacionesExistentes) {

        Map<Integer, CanalConcepto> mapaPorConceptoId = relacionesExistentes.stream()
                .collect(Collectors.toMap(
                        cc -> cc.getConcepto().getId(),
                        cc -> cc,
                        (a, b) -> a
                ));

        return conceptos.stream()
                .map(concepto -> mapaPorConceptoId.get(concepto.getId()))
                .filter(cc -> cc != null)
                .collect(Collectors.toList());
    }

    // ====================================================
    // MÉTODOS AUXILIARES PARA CÁLCULO DE MÉTRICAS CONTABLES
    // ====================================================

    /**
     * Suma los porcentajes de todos los conceptos IMPUESTO_EN_FACTOR_IMP (ej: IIBB)
     * presentes en la lista de conceptos del canal. Se usa para calcular el
     * divisor correcto al extraer el monto del IVA del PVP.
     */
    private BigDecimal sumaImpuestosAdicionales(List<CanalConcepto> conceptos) {
        BigDecimal total = BigDecimal.ZERO;
        for (CanalConcepto cc : conceptos) {
            if (cc.getConcepto() != null
                    && cc.getConcepto().getAplicaSobre() == AplicaSobre.IMPUESTO_EN_FACTOR_IMP) {
                BigDecimal porc = cc.getConcepto().getPorcentaje();
                if (porc != null) {
                    total = total.add(porc);
                }
            }
        }
        return total;
    }

    /**
     * Bases de costo de cada etapa del cálculo de PVP. Se pasan a
     * {@link #calcularCostosVenta} para que cada concepto con override
     * COSTO_VENTA use la base correcta (no el PVP) al calcular su monto.
     *
     * @param precioEnvio       monto absoluto del envío MLA (FLAG_INCLUIR_ENVIO)
     * @param costoBase         costo del producto sin gastos (base para GASTO_SOBRE_COSTO)
     * @param costoConGanancia  costo después de ganancia, antes de gastos post-ganancia
     *                          (base para GASTO_POST_GANANCIA con override COSTO_VENTA)
     * @param costoConImpuestos costo después de impuestos, antes de gastos post-impuestos
     *                          (base para GASTO_POST_IMPUESTOS con override COSTO_VENTA)
     */
    private record BasesCalculoCosto(
            BigDecimal precioEnvio,
            BigDecimal costoBase,
            BigDecimal costoConGanancia,
            BigDecimal costoConImpuestos
    ) {}

    /**
     * Calcula los costos de venta sumando todos los conceptos cuya naturaleza
     * contable resuelta es {@link NaturalezaConcepto#COSTO_VENTA}.
     * <p>
     * Por default, esto incluye conceptos con aplicaSobre = COMISION_SOBRE_PVP,
     * FLAG_COMISION_ML, COSTO_OCULTO_PVP, FLAG_INCLUIR_ENVIO, GASTO_SIN_INFLAR_PVP. Pero
     * usar la naturaleza permite overrides por concepto: un GASTO_POST_GANANCIA
     * (default INFLACION) marcado explícitamente como COSTO_VENTA en su columna
     * `naturaleza` también se contará acá. Esto resuelve casos donde el nombre
     * "GASTO_*" representa plata real que sale del negocio (logística, marketing
     * pago al exterior) y no solo un factor de inflación.
     * <p>
     * NO incluye DESCUENTO_PORCENTUAL (default DESCUENTO): el descuento ya rebaja
     * el PVP, no es plata adicional que salga. Tampoco INFLACION_DIVISOR_FINAL
     * (default INFLACION): es un factor de precio sin costo asociado, salvo
     * override explícito a COSTO_VENTA.
     * <p>
     * <b>Base aplicada al porcentaje:</b> cada aplicaSobre usa la base que
     * corresponde a la etapa en la que actuó en el cálculo del PVP — así el
     * costo real del dueño coincide con lo que sale del negocio:
     * <ul>
     *   <li>{@code COMISION_SOBRE_PVP}, {@code COSTO_OCULTO_PVP}, {@code GASTO_SIN_INFLAR_PVP}: PVP × %/100</li>
     *   <li>{@code FLAG_COMISION_ML}: PVP × mlaComisionPorcentaje/100</li>
     *   <li>{@code FLAG_INCLUIR_ENVIO}: monto absoluto {@code precioEnvio}</li>
     *   <li>{@code GASTO_SOBRE_COSTO} (override): costoBase × %/100</li>
     *   <li>{@code GASTO_POST_GANANCIA} (override): costoConGanancia × %/100</li>
     *   <li>{@code GASTO_POST_IMPUESTOS} (override): costoConImpuestos × %/100</li>
     * </ul>
     *
     * @param pvp         PVP calculado
     * @param conceptos   Lista de conceptos del canal
     * @param cuotas      Número de cuotas (null si contado)
     * @param canalId     ID del canal
     * @param comisionMl  Porcentaje de comisión ML del MLA (para FLAG_COMISION_ML)
     * @param bases       Bases de costo de cada etapa
     * @return Monto total de costos de venta en pesos
     */
    private BigDecimal calcularCostosVenta(BigDecimal pvp, List<CanalConcepto> conceptos,
                                           Integer cuotas, Integer canalId, BigDecimal comisionMl,
                                           BasesCalculoCosto bases) {
        BigDecimal total = BigDecimal.ZERO;

        for (CanalConcepto cc : conceptos) {
            ConceptoCalculo concepto = cc.getConcepto();
            if (concepto == null) continue;

            // Solo contamos como costo si la naturaleza resuelta es COSTO_VENTA
            // (override de la columna `naturaleza` o default del aplicaSobre).
            if (concepto.getNaturalezaResolved() != NaturalezaConcepto.COSTO_VENTA) {
                continue;
            }

            AplicaSobre as = concepto.getAplicaSobre();

            // FLAG_INCLUIR_ENVIO: monto absoluto del envío (no es porcentaje)
            if (as == AplicaSobre.FLAG_INCLUIR_ENVIO) {
                if (bases.precioEnvio() != null && bases.precioEnvio().compareTo(BigDecimal.ZERO) > 0) {
                    total = total.add(bases.precioEnvio());
                }
                continue;
            }

            // FLAG_COMISION_ML: usar comisionPorcentaje del MLA en lugar del % del concepto
            if (as == AplicaSobre.FLAG_COMISION_ML) {
                if (comisionMl != null && comisionMl.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal monto = pvp.multiply(comisionMl)
                            .divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP);
                    total = total.add(monto);
                }
                continue;
            }

            // Resto de conceptos: usar porcentaje del concepto sobre la base que
            // corresponde a la etapa en la que se aplicó al PVP.
            BigDecimal porcentaje = concepto.getPorcentaje();
            if (porcentaje == null || porcentaje.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal base = baseParaCostoVenta(as, pvp, bases);
            BigDecimal monto = base.multiply(porcentaje)
                    .divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP);
            total = total.add(monto);
        }

        // Agregar costo de cuotas si aplica
        if (cuotas != null && cuotas > 0) {
            BigDecimal porcentajeCuotas = obtenerPorcentajeCuota(canalId, cuotas);
            if (porcentajeCuotas != null && porcentajeCuotas.compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(pvp.multiply(porcentajeCuotas)
                        .divide(CIEN, PRECISION_CALCULO, RoundingMode.HALF_UP));
            }
        }

        return total.setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);
    }

    /**
     * Selecciona la base correcta para multiplicar el porcentaje del concepto
     * según la etapa donde actúa en el cálculo del PVP. Si la base resultante
     * es null/cero (canal sin contexto de cálculo, ej: tests), cae a PVP.
     * Si pvp también es null/cero, retorna ZERO para evitar NPE en el caller.
     */
    private BigDecimal baseParaCostoVenta(AplicaSobre as, BigDecimal pvp, BasesCalculoCosto bases) {
        BigDecimal base = switch (as) {
            case GASTO_SOBRE_COSTO -> bases.costoBase();
            case GASTO_POST_GANANCIA -> bases.costoConGanancia();
            case GASTO_POST_IMPUESTOS -> bases.costoConImpuestos();
            // Todos los demás (COMISION_SOBRE_PVP, COSTO_OCULTO_PVP, GASTO_SIN_INFLAR_PVP, etc.)
            // actuan como divisor sobre el PVP, asi que el costo del dueno es % × PVP.
            default -> pvp;
        };
        if (base != null && base.compareTo(BigDecimal.ZERO) > 0) {
            return base;
        }
        // Fallback a PVP; si tampoco es válido, devolvemos ZERO para evitar NPE.
        return (pvp != null && pvp.compareTo(BigDecimal.ZERO) > 0) ? pvp : BigDecimal.ZERO;
    }

    /**
     * Calcula el monto de impuestos (conceptos con AplicaSobre = IMP).
     * Usa la fórmula para extraer el impuesto incluido en el PVP.
     *
     * @param pvp       PVP calculado (incluye impuestos)
     * @param conceptos Lista de conceptos del canal
     * @param iva       Porcentaje de IVA aplicado
     * @return Monto total de impuestos en pesos
     */
    private BigDecimal calcularMontoImpuestos(BigDecimal pvp, List<CanalConcepto> conceptos, BigDecimal iva) {
        BigDecimal total = BigDecimal.ZERO;

        // Sumar todos los conceptos con AplicaSobre = IMP
        BigDecimal porcentajeImpuestos = BigDecimal.ZERO;
        for (CanalConcepto cc : conceptos) {
            if (cc.getConcepto() != null && cc.getConcepto().getAplicaSobre() == AplicaSobre.IMPUESTO_EN_FACTOR_IMP) {
                BigDecimal porcentaje = cc.getConcepto().getPorcentaje();
                if (porcentaje != null) {
                    porcentajeImpuestos = porcentajeImpuestos.add(porcentaje);
                }
            }
        }

        // Calcular el monto de impuestos usando la fórmula para extraer impuestos incluidos
        // montoImp = PVP × (IMP% / (100 + IVA% + IMP%))
        if (porcentajeImpuestos.compareTo(BigDecimal.ZERO) > 0 && pvp != null) {
            BigDecimal ivaSeguro = iva != null ? iva : BigDecimal.ZERO;
            BigDecimal divisor = CIEN.add(ivaSeguro).add(porcentajeImpuestos);
            if (divisor.compareTo(BigDecimal.ZERO) > 0) {
                total = pvp.multiply(porcentajeImpuestos)
                        .divide(divisor, PRECISION_CALCULO, RoundingMode.HALF_UP);
            }
        }

        return total.setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);
    }

    // ====================================================
    // CÁLCULO DE DESCUENTOS APLICABLES
    // ====================================================

    /**
     * Calcula los descuentos aplicables para un precio dado basándose en las reglas de descuento del canal.
     * @param canalId ID del canal
     * @param pvp Precio de venta al público
     * @param costoProducto Costo del producto
     * @param gananciaOriginal Ganancia original calculada
     * @param ingresoNetoOriginal Ingreso neto original (PVP − IVA − impuestos − costosVenta)
     * @param montoIvaOriginal    Monto del IVA contenido en el PVP original
     * @param montoImpuestosOriginal Monto de impuestos (IIBB, etc.) contenidos en el PVP original
     * @param conceptos           Conceptos aplicables del canal (post-reglas)
     * @param cuotas              Número de cuotas (null si contado)
     * @param canalId             ID del canal
     * @param comisionMl          Porcentaje de comisión ML del MLA (para FLAG_COMISION_ML)
     * @param bases               Bases de costo de cada etapa del cálculo del PVP
     * @return Lista de descuentos aplicables o null si no hay reglas
     */
    private List<DescuentoAplicableDTO> calcularDescuentosAplicables(
            Integer canalId,
            BigDecimal pvp,
            BigDecimal costoProducto,
            BigDecimal ingresoNetoOriginal,
            BigDecimal montoIvaOriginal,
            BigDecimal montoImpuestosOriginal,
            List<CanalConcepto> conceptos,
            Integer cuotas,
            BigDecimal comisionMl,
            BasesCalculoCosto bases) {

        if (pvp == null || pvp.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        // Las reglas de descuento dependen solo del canal, así que durante un recálculo completo
        // se reutilizan en vez de re-consultarlas por cada producto y cuota. Prioridad:
        //   1) caché de contexto inline (recálculo de un canal: batch/inline),
        //   2) caché del recálculo masivo (todos los canales),
        //   3) consulta a BD (cálculos puntuales).
        ContextoCanalCache ctxCanal = obtenerContextoCanal(canalId);
        Map<Integer, List<ReglaDescuento>> cacheMasivo = CACHE_REGLAS_DESCUENTO.get();
        List<ReglaDescuento> reglas;
        if (ctxCanal != null) {
            reglas = ctxCanal.reglasDescuento();
        } else if (cacheMasivo != null) {
            reglas = cacheMasivo.getOrDefault(canalId, Collections.emptyList());
        } else {
            reglas = reglaDescuentoRepository.findByCanalIdAndActivoTrueOrderByPrioridadAsc(canalId);
        }

        if (reglas.isEmpty()) {
            return null;
        }

        List<DescuentoAplicableDTO> descuentos = new ArrayList<>();

        for (ReglaDescuento regla : reglas) {
            BigDecimal descuentoPct = regla.getDescuentoPorcentaje();
            BigDecimal montoMinimo = regla.getMontoMinimo();

            // PVP con descuento aplicado.
            BigDecimal factorDescuento = BigDecimal.ONE.subtract(
                    descuentoPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            BigDecimal pvpConDescuento = pvp.multiply(factorDescuento)
                    .setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);

            BigDecimal costosVentaConDescuento = BigDecimal.ZERO;
            BigDecimal ingresoNetoConDescuento = BigDecimal.ZERO;
            BigDecimal gananciaConDescuento = BigDecimal.ZERO;
            BigDecimal margenSobreIngresoNetoConDescuento = BigDecimal.ZERO;
            BigDecimal margenSobrePvpConDescuento = BigDecimal.ZERO;
            BigDecimal markupConDescuento = BigDecimal.ZERO;

            if (costoProducto != null && pvpConDescuento.compareTo(BigDecimal.ZERO) > 0) {
                // Recalcular costos de venta usando el PVP descontado: los conceptos
                // basados en costo (envío fijo, GASTO_* con override COSTO_VENTA) no
                // cambian; los basados en PVP escalan con el nuevo PVP.
                costosVentaConDescuento = calcularCostosVenta(
                        pvpConDescuento, conceptos, cuotas, canalId, comisionMl, bases);

                // IVA e impuestos: escalan linealmente con el PVP (son % del precio
                // de venta neto). Si el PVP cae, el IVA contenido cae en la misma
                // proporción. Esta proporcionalidad es exacta porque ambos son
                // fracciones fijas del PVP.
                BigDecimal montoIvaConDescuento = (montoIvaOriginal != null && montoIvaOriginal.compareTo(BigDecimal.ZERO) > 0)
                        ? montoIvaOriginal.multiply(factorDescuento).setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                BigDecimal montoImpuestosConDescuento = (montoImpuestosOriginal != null && montoImpuestosOriginal.compareTo(BigDecimal.ZERO) > 0)
                        ? montoImpuestosOriginal.multiply(factorDescuento).setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                ingresoNetoConDescuento = pvpConDescuento
                        .subtract(montoIvaConDescuento)
                        .subtract(montoImpuestosConDescuento)
                        .subtract(costosVentaConDescuento)
                        .setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);

                gananciaConDescuento = ingresoNetoConDescuento.subtract(costoProducto)
                        .setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);

                // Métricas
                if (ingresoNetoConDescuento.compareTo(BigDecimal.ZERO) > 0) {
                    margenSobreIngresoNetoConDescuento = gananciaConDescuento.multiply(BigDecimal.valueOf(100))
                            .divide(ingresoNetoConDescuento, PRECISION_RESULTADO, RoundingMode.HALF_UP);
                }
                margenSobrePvpConDescuento = gananciaConDescuento.multiply(BigDecimal.valueOf(100))
                        .divide(pvpConDescuento, PRECISION_RESULTADO, RoundingMode.HALF_UP);
                if (costoProducto.compareTo(BigDecimal.ZERO) > 0) {
                    markupConDescuento = gananciaConDescuento.multiply(BigDecimal.valueOf(100))
                            .divide(costoProducto, PRECISION_RESULTADO, RoundingMode.HALF_UP);
                }
            } else if (ingresoNetoOriginal != null) {
                // Fallback defensivo: si no tenemos info para recalcular, caemos a
                // la proyección lineal antigua (aproximada pero no rompe la UI).
                BigDecimal proporcionIngreso = ingresoNetoOriginal.divide(pvp, 6, RoundingMode.HALF_UP);
                ingresoNetoConDescuento = pvpConDescuento.multiply(proporcionIngreso)
                        .setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);
                costosVentaConDescuento = pvpConDescuento.subtract(ingresoNetoConDescuento)
                        .setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);
                if (costoProducto != null) {
                    gananciaConDescuento = ingresoNetoConDescuento.subtract(costoProducto)
                            .setScale(PRECISION_RESULTADO, RoundingMode.HALF_UP);
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

        return descuentos.isEmpty() ? null : descuentos;
    }

}
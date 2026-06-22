package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.apis.ml.MlRetryHandler;
import ar.com.leo.super_master_backend.apis.ml.config.MercadoLibreProperties;
import ar.com.leo.super_master_backend.apis.ml.dto.CostoEnvioMasivoResponseDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.CostoEnvioResponseDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.CostoVentaMasivoResponseDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.CostoVentaResponseDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.PrediccionCategoriaMlDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.ResultadoAltaMl;
import ar.com.leo.super_master_backend.apis.ml.entity.ConfiguracionMl;
import ar.com.leo.super_master_backend.apis.ml.model.MLCredentials;
import ar.com.leo.super_master_backend.apis.ml.model.Producto;
import ar.com.leo.super_master_backend.apis.ml.model.TokensML;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.common.dto.ProcesoMasivoEstadoDTO;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.common.exception.ServiceNotConfiguredException;
import ar.com.leo.super_master_backend.dominio.common.service.EstadoProcesoMasivo;
import ar.com.leo.super_master_backend.dominio.common.service.ProcesoGlobalService;
import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService;
import ar.com.leo.super_master_backend.dominio.producto.calculo.dto.PrecioCalculadoDTO;
import ar.com.leo.super_master_backend.dominio.producto.calculo.service.CalculoPrecioService;
import ar.com.leo.super_master_backend.dominio.producto.calculo.service.RecalculoPrecioFacade;
import ar.com.leo.super_master_backend.dominio.producto.mla.entity.Mla;
import ar.com.leo.super_master_backend.dominio.producto.mla.repository.MlaRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

@Slf4j
@Service
public class MercadoLibreService {

    private static final String CANAL_ML = "ML";
    private static final java.math.BigDecimal MULTIPLICADOR_PRECIO_ML = java.math.BigDecimal.valueOf(5);
    private static final Set<String> EXT_ML = Set.of("jpg", "jpeg", "png");

    /** Resultado interno del cálculo de envío: costo con IVA y motivo si falló (null si ok). */
    private record ResultadoEnvio(BigDecimal costo, String motivoFallo) {
        static ResultadoEnvio ok(BigDecimal costo) { return new ResultadoEnvio(costo, null); }
        static ResultadoEnvio fallo(String motivo) { return new ResultadoEnvio(BigDecimal.ZERO, motivo); }
    }

    private final ObjectMapper objectMapper;
    private final MlaRepository mlaRepository;
    private final ProductoRepository productoRepository;
    private final CanalRepository canalRepository;
    private final ConfiguracionMlService configuracionMlService;
    private final CalculoPrecioService calculoPrecioService;
    private final RecalculoPrecioFacade recalculoPrecioFacade;
    private final AuditoriaService auditoriaService;
    private final RestClient restClient;
    private final MercadoLibreProperties properties;
    private final ProcesoGlobalService procesoGlobal;
    private final ImagenService imagenService;
    // ReentrantLock en lugar de synchronized: el bloque protegido hace I/O HTTP
    // (refreshAccessToken) y synchronized pinea el carrier thread con virtual threads.
    private final ReentrantLock tokenLock = new ReentrantLock();

    // Trackers reusables (estado + locks + supplier de progreso al SSE).
    private EstadoProcesoMasivo trackerCostoEnvio;
    private EstadoProcesoMasivo trackerCostoVenta;

    // Resultados de las últimas ejecuciones (consultables post-finalización).
    private volatile CostoEnvioMasivoResponseDTO resultadoProcesoMasivo = null;
    private volatile CostoVentaMasivoResponseDTO resultadoProcesoMasivoCostoVenta = null;

    // Auto-inyección para que las llamadas internas pasen por el proxy de Spring
    // y respeten @Transactional
    @Lazy
    @Autowired
    private MercadoLibreService self;

    @org.springframework.beans.factory.annotation.Value("${app.secrets-dir}")
    private String secretsDir;

    private MlRetryHandler retryHandler;
    private MLCredentials credentials;
    private volatile TokensML tokens;
    private String cachedUserId;

    public MercadoLibreService(ObjectMapper objectMapper,
                               MlaRepository mlaRepository,
                               ProductoRepository productoRepository,
                               CanalRepository canalRepository,
                               ConfiguracionMlService configuracionMlService,
                               CalculoPrecioService calculoPrecioService,
                               RecalculoPrecioFacade recalculoPrecioFacade,
                               AuditoriaService auditoriaService,
                               RestClient mercadoLibreRestClient,
                               MercadoLibreProperties properties,
                               ProcesoGlobalService procesoGlobal,
                               ImagenService imagenService) {
        this.objectMapper = objectMapper;
        this.mlaRepository = mlaRepository;
        this.productoRepository = productoRepository;
        this.canalRepository = canalRepository;
        this.configuracionMlService = configuracionMlService;
        this.calculoPrecioService = calculoPrecioService;
        this.recalculoPrecioFacade = recalculoPrecioFacade;
        this.auditoriaService = auditoriaService;
        this.restClient = mercadoLibreRestClient;
        this.properties = properties;
        this.procesoGlobal = procesoGlobal;
        this.imagenService = imagenService;
    }

    @PostConstruct
    public void init() {
        this.retryHandler = new MlRetryHandler(
                restClient,
                properties.retryBaseWaitMs(),
                properties.rateLimitPerSecond(),
                this::verificarTokens
        );
        this.trackerCostoEnvio = new EstadoProcesoMasivo(
                "costo-envio", "Cálculo masivo de costo de envío ML", procesoGlobal);
        this.trackerCostoVenta = new EstadoProcesoMasivo(
                "costo-venta", "Cálculo masivo de costo de venta ML", procesoGlobal);
        cargarCredentials();
        cargarTokens();
    }

    /**
     * Calcula el costo de envío para el vendedor de un producto de ML.
     * <p>
     * - PVP >= umbral: consulta API ML para obtener el costo real de envío gratis.
     * - PVP < umbral: usa los tiers configurados en BD.
     * <p>
     * Umbral y tiers se leen de {@link ConfiguracionMl} (tabla configuracion_ml),
     * configurable desde la UI sin redeploy.
     * <p>
     * El cálculo es iterativo: al agregar el costo de envío, el PVP puede cambiar
     * de tier, requiriendo recalcular hasta estabilizar.
     *
     * <p>{@code noRollbackFor}: el motor de cálculo ({@code calcularPrecioCanalConEnvio}
     * es {@code @Transactional(readOnly=true)}) puede tirar {@link NotFoundException}
     * (sin márgenes/canal/conceptos) o {@link BadRequestException} (sin costo/iva o
     * producto no aplica al canal). Atrapamos la excepción y la transformamos en un DTO
     * con error, pero sin {@code noRollbackFor} la transacción quedaría marcada
     * rollback-only y Spring tiraría {@code UnexpectedRollbackException} al commit.
     */
    @Transactional(noRollbackFor = {NotFoundException.class, BadRequestException.class})
    public CostoEnvioResponseDTO calcularCostoEnvioGratis(String mlaCode) {
        final int MAX_ITERACIONES = 10;

        // Obtener configuración
        ConfiguracionMl config = configuracionMlService.obtenerEntidad();
        BigDecimal umbralEnvioGratis = config.getUmbralEnvioGratis();

        // Buscar el MLA y su producto asociado
        Optional<Mla> mlaOpt = mlaRepository.findFirstByMla(mlaCode);
        if (mlaOpt.isEmpty()) {
            log.warn("ML - MLA {} no encontrado en la base de datos", mlaCode);
            return new CostoEnvioResponseDTO(mlaCode, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                    "MLA no encontrado en la base de datos");
        }

        Mla mla = mlaOpt.get();
        ar.com.leo.super_master_backend.dominio.producto.entity.Producto productoDb =
                mla.getProductos().stream().findFirst().orElse(null);

        if (productoDb == null) {
            log.warn("ML - MLA {} no tiene producto asociado", mlaCode);
            return new CostoEnvioResponseDTO(mlaCode, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                    "MLA sin producto asociado");
        }

        // Si no tiene comisionPorcentaje, obtenerla primero (es necesaria para calcular el PVP correctamente)
        if (mla.getComisionPorcentaje() == null) {
            log.info("ML - MLA {} no tiene comisión, obteniendo primero costo de venta...", mlaCode);
            CostoVentaResponseDTO costoVentaResult = obtenerCostoVenta(mlaCode);
            if (costoVentaResult.porcentajeTotal() != null && costoVentaResult.porcentajeTotal().compareTo(BigDecimal.ZERO) > 0) {
                log.info("ML - Comisión obtenida para MLA {}: {}%", mlaCode, costoVentaResult.porcentajeTotal());
                // Recargar el MLA para tener la comisión actualizada
                mla = mlaRepository.findFirstByMla(mlaCode).orElse(mla);
            } else {
                log.warn("ML - No se pudo obtener la comisión para MLA {}: {}", mlaCode, costoVentaResult.mensaje());
            }
        }

        // Buscar el canal ML
        Canal canalMl = canalRepository.findByNombreIgnoreCase(CANAL_ML)
                .orElseThrow(() -> new IllegalStateException("No se encontró el canal " + CANAL_ML));

        // Variables para API ML (se inicializan solo si es necesario)
        Producto productoMl = null;
        String userId = null;
        String status = null;

        // Divisor de IVA para convertir el costo de envío "con IVA" (lo que devuelven
        // la API ML y los tiers configurados — siempre 21% del servicio de envío)
        // al valor neto que se guarda y se usa en la fórmula de precio.
        // Usamos el IVA del PRODUCTO porque al final del cálculo el motor multiplica
        // la base por (1 + iva_producto/100). Así el cliente termina pagando exactamente
        // lo que ML cobra al vendedor por el envío, independiente del IVA del producto
        // (21%, 10.5%, exento, etc.).
        BigDecimal ivaProducto = productoDb.getIva() != null ? productoDb.getIva() : new BigDecimal("21");
        BigDecimal divisorIva = BigDecimal.ONE.add(
                ivaProducto.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));

        // CÁLCULO ITERATIVO
        // costoEnvioActual: valor SIN IVA que se pasa al cálculo de precio
        // costoEnvioConIvaActual: valor CON IVA para comparar con API/tiers
        BigDecimal costoEnvioActual = BigDecimal.ZERO;
        BigDecimal costoEnvioConIvaActual = BigDecimal.ZERO;
        BigDecimal pvpActual = BigDecimal.ZERO;
        int iteracion = 0;
        String tipoCalculo = "";
        String motivoFallo = null;
        // Valores CON IVA ya vistos, para detectar oscilación entre tiers (ciclo que nunca estabiliza).
        List<BigDecimal> valoresVistos = new ArrayList<>();

        while (iteracion < MAX_ITERACIONES) {
            iteracion++;

            // Calcular PVP con el costo de envío actual
            PrecioCalculadoDTO precioCalculado;
            try {
                precioCalculado = calculoPrecioService.calcularPrecioCanalConEnvio(
                        productoDb.getId(), canalMl.getId(), 0, costoEnvioActual);
            } catch (Exception e) {
                log.warn("ML - MLA {} - Error calculando PVP en iteración {}: {}",
                        mlaCode, iteracion, e.getMessage());
                return new CostoEnvioResponseDTO(mlaCode, status, pvpActual, BigDecimal.ZERO, BigDecimal.ZERO,
                        "Error calculando PVP: " + e.getMessage());
            }

            pvpActual = precioCalculado.pvp();

            // Determinar costo de envío según el PVP
            BigDecimal nuevoCostoEnvio;

            if (pvpActual.compareTo(umbralEnvioGratis) >= 0) {
                // PVP >= umbral: consultar API ML
                tipoCalculo = "API ML";

                // Inicializar conexión con ML si es la primera vez
                if (productoMl == null) {
                    verificarTokens();
                    productoMl = getItemByMLA(mlaCode);
                    if (productoMl == null) {
                        log.warn("ML - No se pudo obtener el producto con MLA: {}", mlaCode);
                        return new CostoEnvioResponseDTO(mlaCode, null, pvpActual, BigDecimal.ZERO, BigDecimal.ZERO,
                                "No se pudo obtener el producto de MercadoLibre");
                    }
                    status = productoMl.status;
                    if (!"active".equals(status)) {
                        log.warn("ML - El producto id: {} se encuentra en estado: '{}'", productoMl.id, status);
                    }
                    try {
                        userId = getUserId();
                    } catch (IOException e) {
                        log.error("Error al obtener userId de ML", e);
                        return new CostoEnvioResponseDTO(mlaCode, status, pvpActual, BigDecimal.ZERO, BigDecimal.ZERO,
                                "Error al obtener userId de MercadoLibre");
                    }
                }

                ResultadoEnvio resultado = calcularCostoEnvioInterno(userId, productoMl, pvpActual);
                nuevoCostoEnvio = resultado.costo();
                if (resultado.motivoFallo() != null) {
                    motivoFallo = resultado.motivoFallo();
                }
            } else {
                // PVP < umbral: usar tiers fijos
                nuevoCostoEnvio = configuracionMlService.obtenerCostoEnvioPorPvp(pvpActual);
                if (nuevoCostoEnvio == null) {
                    log.warn("ML - MLA {} - Tiers no configurados", mlaCode);
                    return new CostoEnvioResponseDTO(mlaCode, status, pvpActual, BigDecimal.ZERO, BigDecimal.ZERO,
                            "Tiers de costo de envío no configurados");
                }
                tipoCalculo = "Tier";
            }

            log.info("ML - MLA {} - Iteración {}: costoEnvioSinIva=${}, PVP=${}, nuevoCostoEnvioConIva=${} ({})",
                    mlaCode, iteracion, costoEnvioActual, pvpActual, nuevoCostoEnvio, tipoCalculo);

            // Verificar si se estabilizó (comparar valores CON IVA)
            if (nuevoCostoEnvio.compareTo(costoEnvioConIvaActual) == 0) {
                log.info("ML - MLA {} - Estabilizado en iteración {}: PVP=${}, costoEnvioConIva=${}, costoEnvioSinIva=${} ({})",
                        mlaCode, iteracion, pvpActual, nuevoCostoEnvio, costoEnvioActual, tipoCalculo);
                break;
            }

            // Detección de oscilación: el nuevo costo ya apareció en una iteración previa,
            // así que el cálculo está rebotando entre tiers y nunca va a estabilizar. Cortamos
            // y tomamos el mayor de los dos últimos (conservador: no subvalúa el envío).
            boolean oscila = valoresVistos.stream().anyMatch(v -> v.compareTo(nuevoCostoEnvio) == 0);
            if (oscila) {
                BigDecimal costoCiclo = nuevoCostoEnvio.max(costoEnvioConIvaActual);
                log.warn("ML - MLA {} - Oscilación detectada en iteración {} entre ${} y ${}. Se toma el mayor (${}).",
                        mlaCode, iteracion, costoEnvioConIvaActual, nuevoCostoEnvio, costoCiclo);
                costoEnvioConIvaActual = costoCiclo;
                costoEnvioActual = costoCiclo.compareTo(BigDecimal.ZERO) > 0
                        ? costoCiclo.divide(divisorIva, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                break;
            }
            valoresVistos.add(nuevoCostoEnvio);

            // Guardar el valor CON IVA para comparación
            costoEnvioConIvaActual = nuevoCostoEnvio;
            // Convertir a base neta usando el IVA del PRODUCTO (no el 21% del servicio
            // de envío). Así cuando el motor multiplique al final por (1 + iva_producto),
            // se reconstruye exactamente el valor que ML cobra.
            costoEnvioActual = nuevoCostoEnvio.compareTo(BigDecimal.ZERO) > 0
                    ? nuevoCostoEnvio.divide(divisorIva, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        }

        if (iteracion >= MAX_ITERACIONES) {
            log.warn("ML - MLA {} - No convergió después de {} iteraciones. Último PVP=${}, costoEnvioConIva=${}",
                    mlaCode, MAX_ITERACIONES, pvpActual, costoEnvioConIvaActual);
        }

        // Los valores finales
        BigDecimal costoEnvioConIva = costoEnvioConIvaActual;
        BigDecimal costoEnvioSinIva = costoEnvioActual;

        // Guardar resultado (se guarda el costo SIN IVA)
        String mensaje;
        if (costoEnvioSinIva.compareTo(BigDecimal.ZERO) > 0) {
            mensaje = String.format("Costo envío: $%.2f (sin IVA: $%.2f) - %s (iteraciones: %d)",
                    costoEnvioConIva, costoEnvioSinIva, tipoCalculo, iteracion);
            guardarCostoEnvio(mlaCode, costoEnvioSinIva);
        } else {
            mensaje = motivoFallo != null ? motivoFallo : "No se pudo calcular el costo de envío";
            log.warn("ML - MLA {} - {}", mlaCode, mensaje);
        }

        return new CostoEnvioResponseDTO(mlaCode, status, pvpActual, costoEnvioConIva, costoEnvioSinIva, mensaje);
    }

    /**
     * Calcula el costo de envío para un producto a partir de su ID.
     * Busca el MLA asociado al producto y delega al método principal.
     *
     * @param productoId ID del producto
     * @return DTO con el costo de envío calculado
     */
    @Transactional
    public CostoEnvioResponseDTO calcularCostoEnvioPorProducto(Integer productoId) {
        Optional<ar.com.leo.super_master_backend.dominio.producto.entity.Producto> productoOpt =
                productoRepository.findById(productoId);

        if (productoOpt.isEmpty()) {
            log.warn("ML - Producto con ID {} no encontrado", productoId);
            return new CostoEnvioResponseDTO(null, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                    "Producto no encontrado con ID: " + productoId);
        }

        ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto = productoOpt.get();
        Mla mla = producto.getMla();

        if (mla == null) {
            log.warn("ML - Producto {} no tiene MLA asociado", productoId);
            return new CostoEnvioResponseDTO(null, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                    "El producto no tiene MLA asociado");
        }

        return self.calcularCostoEnvioGratis(mla.getMla());
    }

    // =====================================================
    // COSTO DE VENTA (COMISIONES ML)
    // =====================================================

    /**
     * Obtiene los costos de venta (comisiones) de un producto en MercadoLibre
     * y guarda el porcentaje de comisión (meli_percentage_fee) en la base de datos.
     *
     * @param mlaCode Código MLA del producto
     * @return DTO con los costos de venta
     */
    @Transactional
    public CostoVentaResponseDTO obtenerCostoVenta(String mlaCode) {
        verificarTokens();

        // Obtener producto de ML
        Producto productoMl = getItemByMLA(mlaCode);
        if (productoMl == null) {
            log.warn("ML - No se pudo obtener el producto con MLA: {}", mlaCode);
            return new CostoVentaResponseDTO(mlaCode, null, null, null, null, null, null, null,
                    null, null, "No se pudo obtener el producto de MercadoLibre");
        }

        String status = productoMl.status;
        BigDecimal precio = BigDecimal.valueOf(productoMl.price).setScale(2, RoundingMode.HALF_UP);

        // Consultar API de costos de venta
        String uri = String.format(
                "/sites/%s/listing_prices?category_id=%s&price=%s&currency_id=%s&logistic_type=%s",
                productoMl.siteId,
                productoMl.categoryId,
                productoMl.price,
                "ARS",
                productoMl.shipping.logisticType);

        String responseBody = retryHandler.get(uri, () -> tokens.accessToken);

        if (responseBody == null) {
            log.warn("ML - Error al obtener costos de venta para MLA {}", mlaCode);
            return new CostoVentaResponseDTO(mlaCode, status, precio, null, null, null, null, null,
                    productoMl.listingTypeId, null, "Error al consultar costos de venta");
        }

        try {
            JsonNode json = objectMapper.readTree(responseBody);
            log.debug("ML - Costos de venta para MLA {}: {}", mlaCode, responseBody);

            // Buscar el listing_type correspondiente
            BigDecimal comisionVentaTotal = BigDecimal.ZERO;
            BigDecimal costoFijo = BigDecimal.ZERO;
            BigDecimal cargoFinanciacion = BigDecimal.ZERO;
            BigDecimal porcentajeMeli = BigDecimal.ZERO;
            BigDecimal porcentajeTotal = BigDecimal.ZERO;
            String listingTypeName = null;

            for (JsonNode listing : json) {
                if (productoMl.listingTypeId.equals(listing.path("listing_type_id").asString())) {
                    // Total de comisión de venta
                    comisionVentaTotal = BigDecimal.valueOf(listing.path("sale_fee_amount").asDouble(0));
                    listingTypeName = listing.path("listing_type_name").asString(null);

                    // Detalles desglosados de sale_fee_details
                    JsonNode details = listing.path("sale_fee_details");
                    if (!details.isMissingNode()) {
                        costoFijo = BigDecimal.valueOf(details.path("fixed_fee").asDouble(0));
                        cargoFinanciacion = BigDecimal.valueOf(details.path("financing_add_on_fee").asDouble(0));
                        porcentajeMeli = BigDecimal.valueOf(details.path("meli_percentage_fee").asDouble(0));
                        porcentajeTotal = BigDecimal.valueOf(details.path("percentage_fee").asDouble(0));
                    }
                    break;
                }
            }

            // Guardar meli_percentage_fee como comisionPorcentaje
            if (porcentajeMeli.compareTo(BigDecimal.ZERO) > 0) {
                guardarComisionPorcentaje(mlaCode, porcentajeMeli);
            }

            return new CostoVentaResponseDTO(
                    mlaCode,
                    status,
                    precio,
                    comisionVentaTotal,
                    costoFijo,
                    cargoFinanciacion,
                    porcentajeMeli,
                    porcentajeTotal,
                    productoMl.listingTypeId,
                    listingTypeName,
                    String.format("Comisión total: $%.2f (Fijo: $%.2f + Financiación: $%.2f), Porcentaje ML: %.2f%%",
                            comisionVentaTotal, costoFijo, cargoFinanciacion, porcentajeMeli)
            );

        } catch (Exception e) {
            log.error("Error parseando respuesta de costos de venta", e);
            return new CostoVentaResponseDTO(mlaCode, status, precio, null, null, null, null, null,
                    productoMl.listingTypeId, null, "Error parseando respuesta: " + e.getMessage());
        }
    }

    /**
     * Obtiene los costos de venta de un producto a partir de su ID
     * y guarda el porcentaje de comisión en la base de datos.
     *
     * @param productoId ID del producto
     * @return DTO con los costos de venta
     */
    @Transactional
    public CostoVentaResponseDTO obtenerCostoVentaPorProducto(Integer productoId) {
        Optional<ar.com.leo.super_master_backend.dominio.producto.entity.Producto> productoOpt =
                productoRepository.findById(productoId);

        if (productoOpt.isEmpty()) {
            log.warn("ML - Producto con ID {} no encontrado", productoId);
            return new CostoVentaResponseDTO(null, null, null, null, null, null, null, null,
                    null, null, "Producto no encontrado con ID: " + productoId);
        }

        ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto = productoOpt.get();
        Mla mla = producto.getMla();

        if (mla == null) {
            log.warn("ML - Producto {} no tiene MLA asociado", productoId);
            return new CostoVentaResponseDTO(null, null, null, null, null, null, null, null,
                    null, null, "El producto no tiene MLA asociado");
        }

        return obtenerCostoVenta(mla.getMla());
    }

    // =====================================================
    // PROCESO MASIVO - COSTO DE VENTA
    // =====================================================

    /**
     * Inicia el cálculo de costo de venta para todos los MLAs de forma asincrónica.
     *
     * @return true si se inició el proceso, false si ya había uno en ejecución
     */
    public boolean iniciarCalculoCostoVentaTodos() {
        if (!trackerCostoVenta.adquirir()) {
            log.warn("ML - No se pudo iniciar costo de venta: ya en ejecución u otro proceso ML activo");
            return false;
        }
        resultadoProcesoMasivoCostoVenta = null;
        self.calcularCostoVentaTodosAsync();
        return true;
    }

    /**
     * Calcula el costo de venta para todos los MLAs en la base de datos de forma asincrónica.
     * Este método se ejecuta en un thread separado.
     */
    @Async
    public void calcularCostoVentaTodosAsync() {
        List<Mla> mlas = mlaRepository.findAll();
        List<CostoVentaResponseDTO> resultados = new ArrayList<>();
        int exitosos = 0;
        int errores = 0;
        int omitidos = 0;

        log.info("ML - Iniciando cálculo masivo de costos de venta para {} MLAs", mlas.size());

        // Activamos el batch: durante el masivo, los recálculos por cambio de MLA
        // que se programen se acumulan acá y los despachamos UNO al final.
        Set<Integer> batch = ConcurrentHashMap.newKeySet();
        masivoMlaBatch.set(batch);
        try {
            for (Mla mla : mlas) {
                if (trackerCostoVenta.estaCancelado()) {
                    omitidos = mlas.size() - resultados.size();
                    log.info("ML - Proceso masivo de costo de venta cancelado. Procesados: {}, Omitidos: {}",
                            resultados.size(), omitidos);
                    break;
                }

                try {
                    CostoVentaResponseDTO resultado = obtenerCostoVenta(mla.getMla());
                    resultados.add(resultado);

                    if (resultado.comisionVentaTotal() != null && resultado.comisionVentaTotal().compareTo(BigDecimal.ZERO) > 0) {
                        exitosos++;
                    } else {
                        errores++;
                    }

                } catch (Exception e) {
                    log.error("ML - Error procesando costo de venta MLA {}: {}", mla.getMla(), e.getMessage());
                    CostoVentaResponseDTO error = new CostoVentaResponseDTO(
                            mla.getMla(), null, null, null, null, null, null, null,
                            null, null, "Error: " + e.getMessage());
                    resultados.add(error);
                    errores++;
                }

                trackerCostoVenta.actualizar(mlas.size(), resultados.size(), exitosos, errores,
                        String.format("Procesando %d/%d", resultados.size(), mlas.size()));
            }

            String tag = trackerCostoVenta.estaCancelado() ? "cancelado" : "completado";
            log.info("ML - Cálculo masivo de costo de venta {}. Exitosos: {}, Errores: {}, Omitidos: {}",
                    tag, exitosos, errores, omitidos);

            resultadoProcesoMasivoCostoVenta = new CostoVentaMasivoResponseDTO(
                    resultados.size(), exitosos, errores, omitidos, resultados);

            trackerCostoVenta.completar(mlas.size(), exitosos, errores,
                    String.format("Proceso %s. %d MLAs OK, %d errores, %d omitidos",
                            tag, exitosos, errores, omitidos));

        } catch (Exception e) {
            log.error("ML - Error fatal en proceso masivo de costo de venta: {}", e.getMessage(), e);
            resultadoProcesoMasivoCostoVenta = null;
            trackerCostoVenta.completarConError(e.getMessage());
        } finally {
            trackerCostoVenta.liberar();
            masivoMlaBatch.remove();
            // Despachamos el recálculo batched DESPUÉS de liberar el lock del
            // masivo: ejecutarRecalculoAsync usa el grupo BD (recalculo-canal),
            // que es distinto al grupo ML del masivo, así que no compiten.
            dispatchBatchRecalculoMla(batch);
        }
    }

    /**
     * Cancela el proceso masivo de cálculo de costos de venta en ejecución.
     *
     * @return true si había un proceso en ejecución que fue marcado para cancelar
     */
    public boolean cancelarProcesoMasivoCostoVenta() {
        if (trackerCostoVenta.estaEjecutando()) {
            trackerCostoVenta.cancelar();
            log.info("ML - Solicitud de cancelación de proceso masivo de costo de venta recibida");
            return true;
        }
        log.info("ML - No hay proceso masivo de costo de venta en ejecución para cancelar");
        return false;
    }

    /**
     * Obtiene el estado actual del proceso masivo de costo de venta.
     *
     * @return DTO con el estado del proceso
     */
    public ProcesoMasivoEstadoDTO obtenerEstadoProcesoMasivoCostoVenta() {
        return trackerCostoVenta.obtener();
    }

    /**
     * Obtiene el resultado del último proceso masivo de costo de venta completado.
     *
     * @return DTO con los resultados o null si no hay resultados disponibles
     */
    public CostoVentaMasivoResponseDTO obtenerResultadoProcesoMasivoCostoVenta() {
        return resultadoProcesoMasivoCostoVenta;
    }

    /**
     * Verifica si hay un proceso masivo de costo de venta en ejecución.
     *
     * @return true si hay un proceso en ejecución
     */
    public boolean isProcesoMasivoCostoVentaEnEjecucion() {
        return trackerCostoVenta.estaEjecutando();
    }

    // =====================================================
    // PROCESO MASIVO - COSTO DE ENVÍO
    // =====================================================

    /**
     * Inicia el cálculo de costo de envío para todos los MLAs de forma asincrónica.
     *
     * @return true si se inició el proceso, false si ya había uno en ejecución
     */
    public boolean iniciarCalculoCostoEnvioTodos() {
        if (!trackerCostoEnvio.adquirir()) {
            log.warn("ML - No se pudo iniciar costo de envío: ya en ejecución u otro proceso ML activo");
            return false;
        }
        resultadoProcesoMasivo = null;
        self.calcularCostoEnvioTodosAsync();
        return true;
    }

    /**
     * Calcula el costo de envío para todos los MLAs en la base de datos de forma asincrónica.
     * Este método se ejecuta en un thread separado.
     */
    @Async
    public void calcularCostoEnvioTodosAsync() {
        List<Mla> mlas = mlaRepository.findAll();
        List<CostoEnvioResponseDTO> resultados = new ArrayList<>();
        int exitosos = 0;
        int errores = 0;
        int omitidos = 0;

        log.info("ML - Iniciando cálculo masivo de costos de envío para {} MLAs", mlas.size());

        Set<Integer> batch = ConcurrentHashMap.newKeySet();
        masivoMlaBatch.set(batch);
        try {
            for (Mla mla : mlas) {
                if (trackerCostoEnvio.estaCancelado()) {
                    omitidos = mlas.size() - resultados.size();
                    log.info("ML - Proceso masivo cancelado. Procesados: {}, Omitidos: {}",
                            resultados.size(), omitidos);
                    break;
                }

                try {
                    // Llamar via self para que pase por el proxy y respete @Transactional
                    CostoEnvioResponseDTO resultado = self.calcularCostoEnvioGratis(mla.getMla());
                    resultados.add(resultado);

                    if (resultado.costoEnvioSinIva().compareTo(BigDecimal.ZERO) > 0) {
                        exitosos++;
                    } else {
                        errores++;
                    }

                } catch (Exception e) {
                    log.error("ML - Error procesando MLA {}: {}", mla.getMla(), e.getMessage());
                    CostoEnvioResponseDTO error = new CostoEnvioResponseDTO(
                            mla.getMla(), null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                            "Error: " + e.getMessage());
                    resultados.add(error);
                    errores++;
                }

                trackerCostoEnvio.actualizar(mlas.size(), resultados.size(), exitosos, errores,
                        String.format("Procesando %d/%d", resultados.size(), mlas.size()));
            }

            String tag = trackerCostoEnvio.estaCancelado() ? "cancelado" : "completado";
            log.info("ML - Cálculo masivo {}. Exitosos: {}, Errores: {}, Omitidos: {}",
                    tag, exitosos, errores, omitidos);

            resultadoProcesoMasivo = new CostoEnvioMasivoResponseDTO(
                    resultados.size(), exitosos, errores, omitidos, resultados);

            trackerCostoEnvio.completar(mlas.size(), exitosos, errores,
                    String.format("Proceso %s. %d MLAs OK, %d errores, %d omitidos",
                            tag, exitosos, errores, omitidos));

        } catch (Exception e) {
            log.error("ML - Error fatal en proceso masivo de costo de envío: {}", e.getMessage(), e);
            resultadoProcesoMasivo = null;
            trackerCostoEnvio.completarConError(e.getMessage());
        } finally {
            trackerCostoEnvio.liberar();
            masivoMlaBatch.remove();
            dispatchBatchRecalculoMla(batch);
        }
    }

    /**
     * Cancela el proceso masivo de cálculo de costos de envío en ejecución.
     *
     * @return true si había un proceso en ejecución que fue marcado para cancelar
     */
    public boolean cancelarProcesoMasivo() {
        if (trackerCostoEnvio.estaEjecutando()) {
            trackerCostoEnvio.cancelar();
            log.info("ML - Solicitud de cancelación de proceso masivo recibida");
            return true;
        }
        log.info("ML - No hay proceso masivo en ejecución para cancelar");
        return false;
    }

    /**
     * Obtiene el estado actual del proceso masivo.
     *
     * @return DTO con el estado del proceso
     */
    public ProcesoMasivoEstadoDTO obtenerEstadoProcesoMasivo() {
        return trackerCostoEnvio.obtener();
    }

    /**
     * Obtiene el resultado del último proceso masivo completado.
     *
     * @return DTO con los resultados o null si no hay resultados disponibles
     */
    public CostoEnvioMasivoResponseDTO obtenerResultadoProcesoMasivo() {
        return resultadoProcesoMasivo;
    }

    /**
     * Verifica si hay un proceso masivo en ejecución.
     *
     * @return true si hay un proceso en ejecución
     */
    public boolean isProcesoMasivoEnEjecucion() {
        return trackerCostoEnvio.estaEjecutando();
    }

    /**
     * Guarda el porcentaje de comisión en la entidad Mla.
     */
    private void guardarComisionPorcentaje(String mlaCode, BigDecimal porcentaje) {
        List<Mla> mlas = mlaRepository.findByMla(mlaCode);
        if (mlas.isEmpty()) {
            log.warn("ML - No se encontró el MLA {} en la base de datos para guardar la comisión", mlaCode);
            return;
        }

        // La columna comision_porcentaje es DECIMAL(5,2): normalizamos a 2 decimales.
        BigDecimal porcentajeNormalizado = porcentaje.setScale(2, RoundingMode.HALF_UP);

        LocalDateTime ahora = LocalDateTime.now();
        for (Mla mla : mlas) {
            BigDecimal comisionAnterior = mla.getComisionPorcentaje();

            mla.setComisionPorcentaje(porcentajeNormalizado);
            mla.setFechaCalculoComision(ahora);
            mlaRepository.save(mla);

            Map<String, String> anterior = new LinkedHashMap<>();
            anterior.put("comisionPorcentaje", comisionAnterior != null ? comisionAnterior.toPlainString() : null);
            Map<String, String> nuevo = new LinkedHashMap<>();
            nuevo.put("comisionPorcentaje", porcentajeNormalizado.toPlainString());
            // Origen PROCESS: el valor lo calcula el motor de costos de ML (no es edición
            // manual). Explícito porque el cálculo masivo corre en @Async (sin request).
            auditoriaService.registrarCambios(AuditoriaEntidad.MLA, mla.getId(), mlaCode, AuditoriaAccion.UPDATE, anterior, nuevo, "PROCESS");

            if (comisionAnterior == null || comisionAnterior.compareTo(porcentajeNormalizado) != 0) {
                programarRecalculoMlaPostCommit(mla.getId());
            }
        }
        log.info("ML - Porcentaje de comisión guardado para MLA {} ({} registros): {}%", mlaCode, mlas.size(), porcentajeNormalizado);
    }

    /**
     * Guarda el costo de envío en la entidad Mla.
     */
    private void guardarCostoEnvio(String mlaCode, BigDecimal costoEnvio) {
        List<Mla> mlas = mlaRepository.findByMla(mlaCode);
        if (mlas.isEmpty()) {
            log.warn("ML - No se encontró el MLA {} en la base de datos para guardar el costo", mlaCode);
            return;
        }

        LocalDateTime ahora = LocalDateTime.now();
        for (Mla mla : mlas) {
            BigDecimal precioAnterior = mla.getPrecioEnvio();

            mla.setPrecioEnvio(costoEnvio);
            mla.setFechaCalculoEnvio(ahora);
            mlaRepository.save(mla);

            Map<String, String> anterior = new LinkedHashMap<>();
            anterior.put("precioEnvio", precioAnterior != null ? precioAnterior.toPlainString() : null);
            Map<String, String> nuevo = new LinkedHashMap<>();
            nuevo.put("precioEnvio", costoEnvio.toPlainString());
            // Origen PROCESS: el valor lo calcula el motor de costos de ML (no es edición
            // manual). Explícito porque el cálculo masivo corre en @Async (sin request).
            auditoriaService.registrarCambios(AuditoriaEntidad.MLA, mla.getId(), mlaCode, AuditoriaAccion.UPDATE, anterior, nuevo, "PROCESS");

            if (precioAnterior == null || precioAnterior.compareTo(costoEnvio) != 0) {
                programarRecalculoMlaPostCommit(mla.getId());
            }
        }
        log.info("ML - Costo de envío (sin IVA) guardado para MLA {} ({} registros): ${}", mlaCode, mlas.size(), costoEnvio);
    }

    /**
     * Calcula el costo de envío gratis usando el endpoint
     * {@code GET /users/{userId}/shipping_options/free} de la API de Mercado Libre.
     *
     * <p>Doc oficial: <a href="https://developers.mercadolibre.com.ar/es_ar/costos-de-envio">
     * Costos de envío</a> (rev. 13/02/2026).
     *
     * <p>Solo aplica para modo {@code me2}. Si el ítem no ofrece envío gratis
     * ({@code shipping.free_shipping = false}), no tiene sentido pedir el costo —
     * en ese caso el comprador paga el envío y no afecta al vendedor.
     *
     * <p>Envía siempre {@code free_shipping=true}: el contexto del negocio es calcular
     * cuánto le cuesta al vendedor ofrecer envío gratis. La opción {@code free_shipping=false}
     * (costo a cargo del comprador) no es relevante para nuestro cálculo de PVP.
     *
     * <p>Fallback: si la consulta con {@code item_id} falla, reintenta con {@code dimensions}.
     */
    private ResultadoEnvio calcularCostoEnvioInterno(String userId, Producto producto, BigDecimal precioEnvioGratis) {
        verificarTokens();

        final String itemId = producto.id;
        final String itemPrice = String.format(Locale.forLanguageTag("en-US"), "%.2f", precioEnvioGratis);
        final String listingType = producto.listingTypeId;
        final String mode = producto.shipping != null ? producto.shipping.mode : null;
        final String condition = producto.condition;
        final String logisticType = producto.shipping != null ? producto.shipping.logisticType : null;
        final String zipCode = producto.sellerAddress != null ? producto.sellerAddress.zipCode : null;
        final String categoryId = producto.categoryId;
        final Boolean freeShippingHabilitado = producto.shipping != null ? producto.shipping.freeShipping : null;
        final List<String> shippingTags = producto.shipping != null ? producto.shipping.tags : null;

        if (!"me2".equals(mode)) {
            String motivo = String.format("modo de envío no es ME2 (mode='%s')", mode);
            log.warn("ML - No se puede calcular costo de envío para {}: {}", itemId, motivo);
            return ResultadoEnvio.fallo(motivo);
        }

        // Si el ítem explícitamente NO ofrece envío gratis, el costo lo paga el comprador
        // y no impacta al vendedor: no tiene sentido consultar la API.
        if (Boolean.FALSE.equals(freeShippingHabilitado)) {
            String motivo = "el ítem no ofrece envío gratis (shipping.free_shipping=false)";
            log.info("ML - No se calcula envío para {}: {}", itemId, motivo);
            return ResultadoEnvio.fallo(motivo);
        }

        if (zipCode == null || zipCode.isBlank()
                || logisticType == null || logisticType.isBlank()
                || condition == null || condition.isBlank()
                || listingType == null || listingType.isBlank()) {
            String motivo = String.format("faltan datos (zip=%s, logistic=%s, cond=%s, listing=%s)",
                    zipCode, logisticType, condition, listingType);
            log.warn("ML - No se puede calcular envío para {}: {}", itemId, motivo);
            return ResultadoEnvio.fallo(motivo);
        }

        // Log informativo: si el ítem tiene mandatory_free_shipping, el vendedor está OBLIGADO
        // a ofrecer envío gratis (PVP por encima del umbral de ML).
        boolean mandatorio = shippingTags != null && shippingTags.contains("mandatory_free_shipping");
        if (mandatorio) {
            log.debug("ML - Ítem {} tiene mandatory_free_shipping (envío gratis obligatorio)", itemId);
        }

        String baseParams = String.format("&item_price=%s&listing_type_id=%s&mode=%s&condition=%s&logistic_type=%s&zip_code=%s&verbose=true&free_shipping=true",
                itemPrice, listingType, mode, condition, logisticType, zipCode);
        if (categoryId != null && !categoryId.isBlank()) {
            baseParams += "&category_id=" + categoryId;
        }

        // Intentar primero con item_id
        String uri = String.format("/users/%s/shipping_options/free?item_id=%s%s", userId, itemId, baseParams);
        String responseBody = retryHandler.get(uri, () -> tokens.accessToken);

        if (responseBody == null) {
            // Si falló con item_id, intentar con dimensions como fallback
            String dimensions = producto.getDimensions();
            if (dimensions != null && !dimensions.isBlank()) {
                log.info("ML - Reintentando cálculo de envío para {} usando dimensions...", itemId);
                String dimUri = String.format("/users/%s/shipping_options/free?dimensions=%s%s",
                        userId, URLEncoder.encode(dimensions, StandardCharsets.UTF_8), baseParams);
                responseBody = retryHandler.get(dimUri, () -> tokens.accessToken);
            }

            if (responseBody == null) {
                String motivo = "la API de ML no respondió (item_id y dimensions fallaron)";
                log.warn("ML - {} para {}", motivo, itemId);
                return ResultadoEnvio.fallo(motivo);
            }
        }

        try {
            JsonNode json = objectMapper.readTree(responseBody);
            JsonNode allCountry = json.path("coverage").path("all_country");
            double cost = allCountry.path("list_cost").asDouble(0);

            JsonNode discount = json.path("coverage").path("discount");
            if (!discount.isMissingNode() && discount.path("rate").asDouble(0) > 0) {
                double rate = discount.path("rate").asDouble(0);
                double promotedAmount = discount.path("promoted_amount").asDouble(0);
                String type = discount.path("type").asString("");
                log.info("ML - Envío {}: costo ${} (descuento {}% tipo '{}', costo original ${})",
                        itemId, String.format("%.2f", cost), String.format("%.0f", rate * 100), type, String.format("%.2f", promotedAmount));
            }

            if (cost <= 0) {
                return ResultadoEnvio.fallo("la API de ML devolvió costo 0");
            }
            return ResultadoEnvio.ok(BigDecimal.valueOf(cost));
        } catch (Exception e) {
            log.error("Error parseando respuesta de costo de envío", e);
            return ResultadoEnvio.fallo("error parseando respuesta de ML: " + e.getMessage());
        }
    }

    /**
     * Obtiene un producto de ML por su MLA.
     */
    public Producto getItemByMLA(String itemId) {
        verificarTokens();

        try {
            return retryHandler.get("/items/" + itemId, () -> tokens.accessToken, Producto.class);
        } catch (Exception e) {
            log.warn("ML - No se pudo obtener item {}: {}", itemId, e.getMessage());
            return null;
        }
    }

    /**
     * Obtiene el userId del usuario autenticado.
     */
    public String getUserId() throws IOException {
        // Usar cache si está disponible
        if (cachedUserId != null) {
            return cachedUserId;
        }

        verificarTokens();

        String responseBody = retryHandler.get("/users/me", () -> tokens.accessToken);

        if (responseBody == null) {
            throw new IOException("Error al obtener el user ID de ML");
        }

        try {
            cachedUserId = objectMapper.readTree(responseBody).get("id").asString();
            return cachedUserId;
        } catch (Exception e) {
            throw new IOException("Error parseando userId de ML", e);
        }
    }

    /**
     * Busca el código MLA de una publicación a partir del SKU del vendedor
     * (campo {@code seller_sku}) consultando la API de búsqueda de items del seller.
     * Devuelve null si no hay coincidencias.
     */
    public MlaPorSku buscarMlaPorSku(String sku) {
        if (sku == null || sku.isBlank()) {
            return null;
        }
        verificarTokens();
        final String userId;
        try {
            userId = getUserId();
        } catch (IOException e) {
            throw new BadRequestException("No se pudo obtener el usuario de MercadoLibre: " + e.getMessage());
        }
        // status=active: ignoramos publicaciones pausadas/cerradas que reusen el SKU.
        String path = "/users/" + userId + "/items/search?status=active&seller_sku="
                + URLEncoder.encode(sku.trim(), StandardCharsets.UTF_8);
        String body = retryHandler.get(path, () -> tokens.accessToken);
        if (body == null) {
            return null;
        }
        JsonNode results = objectMapper.readTree(body).path("results");
        if (!results.isArray() || results.isEmpty()) {
            return null;
        }
        // Solo nos quedamos con publicaciones TRADICIONALES (no de catálogo). Si el SKU
        // está en varias, devolvemos la primera tradicional con su MLAU. Una sola
        // consulta por candidato trae el tipo de publicación y el MLAU juntos.
        for (JsonNode node : results) {
            String mla = textoOpcional(node);
            if (mla == null) {
                continue;
            }
            JsonNode item = obtenerItemNode(mla, "id,catalog_listing,user_product_id,variations");
            if (item == null) {
                continue; // ante la duda no la tomamos
            }
            boolean esCatalogo = item.path("catalog_listing").asBoolean(false);
            if (esCatalogo) {
                continue;
            }
            return new MlaPorSku(mla, extraerMlau(item));
        }
        return null;
    }

    /** Resultado de la búsqueda por SKU: el código MLA tradicional y su MLAU (puede ser null). */
    public record MlaPorSku(String mla, String mlau) {
    }

    /** Consulta un ítem de ML pidiendo solo los attributes indicados. Devuelve null ante error. */
    private JsonNode obtenerItemNode(String mlaCode, String attributes) {
        try {
            verificarTokens();
            String itemBody = retryHandler.get("/items/" + mlaCode + "?attributes=" + attributes,
                    () -> tokens.accessToken);
            return itemBody == null ? null : objectMapper.readTree(itemBody);
        } catch (Exception e) {
            log.warn("ML - No se pudo consultar el ítem {}: {}", mlaCode, e.getMessage());
            return null;
        }
    }

    /** Extrae el MLAU ({@code user_product_id}) de un ítem: a nivel ítem o de su primera variación. */
    private String extraerMlau(JsonNode item) {
        String mlau = textoOpcional(item.path("user_product_id"));
        if (mlau != null) {
            return mlau;
        }
        JsonNode variations = item.path("variations");
        if (variations.isArray()) {
            for (JsonNode v : variations) {
                String vMlau = textoOpcional(v.path("user_product_id"));
                if (vMlau != null) {
                    return vMlau;
                }
            }
        }
        return null;
    }

    /** Devuelve el texto de un nodo, o null si está ausente, es null o queda vacío. */
    private static String textoOpcional(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asString();
        return (value == null || value.isBlank()) ? null : value;
    }

    // ==================== MANEJO DE CREDENCIALES ====================

    private void cargarCredentials() {
        try {
            File credFile = Paths.get(secretsDir).resolve("ml_credentials.json").toFile();
            if (credFile.exists()) {
                credentials = objectMapper.readValue(credFile, MLCredentials.class);
                log.info("ML - Credenciales cargadas desde {}", credFile.getAbsolutePath());
            } else {
                log.warn("ML - No se encontraron credenciales en {}", credFile.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("Error cargando credenciales ML: {}", e.getMessage());
        }
    }

    // ==================== MANEJO DE TOKENS ====================

    private void verificarTokens() {
        if (tokens == null) {
            log.warn("ML - Tokens no inicializados. Intentando cargar...");
            cargarTokens();
            if (tokens == null) {
                throw new ServiceNotConfiguredException("MercadoLibre",
                        "No hay tokens disponibles. Debe generar los tokens de autenticación primero.");
            }
            return;
        }

        if (!tokens.isExpired()) {
            return;
        }

        tokenLock.lock();
        try {
            if (tokens == null || !tokens.isExpired()) {
                return;
            }

            if (credentials == null) {
                throw new ServiceNotConfiguredException("MercadoLibre",
                        "No hay credenciales configuradas para renovar el token expirado. " +
                                "Verifique el archivo ml_credentials.json");
            }

            log.info("ML - Access token expirado, renovando...");
            try {
                tokens = refreshAccessToken(tokens.refreshToken);
                tokens.issuedAt = System.currentTimeMillis();
                guardarTokens(tokens);
                // Limpiar cache de userId al renovar tokens
                cachedUserId = null;
                log.info("ML - Token renovado correctamente.");
            } catch (Exception e) {
                log.error("ML - Error al renovar token", e);
                throw new ServiceNotConfiguredException("MercadoLibre",
                        "Error al renovar el token: " + e.getMessage() + ". " +
                                "Es posible que el refresh_token haya expirado y necesite re-autenticarse.");
            }
        } finally {
            tokenLock.unlock();
        }
    }

    private void cargarTokens() {
        try {
            File file = Paths.get(secretsDir).resolve("ml_tokens.json").toFile();
            if (file.exists()) {
                tokens = objectMapper.readValue(file, TokensML.class);
                log.info("ML - Tokens cargados desde {}", file.getAbsolutePath());
            } else {
                log.warn("ML - Archivo de tokens no encontrado en {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("Error cargando tokens ML", e);
        }
    }

    private void guardarTokens(TokensML tokens) {
        try {
            File file = Paths.get(secretsDir).resolve("ml_tokens.json").toFile();
            file.getParentFile().mkdirs();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, tokens);
            log.info("ML - Tokens guardados en {}", file.getAbsolutePath());
        } catch (Exception e) {
            log.error("Error guardando tokens ML", e);
        }
    }

    private TokensML refreshAccessToken(String refreshToken) {
        if (credentials == null) {
            throw new ServiceNotConfiguredException("MercadoLibre",
                    "No hay credenciales configuradas para renovar el token. " +
                            "Verifique el archivo ml_credentials.json");
        }

        String formBody = String.format(
                "grant_type=refresh_token&client_id=%s&client_secret=%s&refresh_token=%s",
                credentials.clientId, credentials.clientSecret, refreshToken);

        String responseBody = retryHandler.postForm("/oauth/token", formBody);

        if (responseBody == null) {
            throw new RuntimeException("Error al refrescar access_token");
        }

        try {
            TokensML newTokens = objectMapper.readValue(responseBody, TokensML.class);
            newTokens.issuedAt = System.currentTimeMillis();
            return newTokens;
        } catch (Exception e) {
            throw new RuntimeException("Error parseando tokens de ML", e);
        }
    }

    /**
     * Verifica si el servicio tiene configuración completa.
     */
    public boolean isConfigured() {
        return tokens != null && credentials != null;
    }

    // ==================== PROMOCIONES Y PRECIOS (para Automatización de Precios) ====================

    /**
     * Obtiene las promociones activas del vendedor.
     * Retorna el JSON crudo como String para que el orquestador lo parsee.
     */
    public String obtenerPromocionesDelItem(String itemId) {
        verificarTokens();
        try {
            return retryHandler.get(
                    "/seller-promotions/items/" + itemId + "?app_version=v2",
                    () -> tokens.accessToken
            );
        } catch (Exception e) {
            log.warn("ML - Error obteniendo promociones del item {}: {}", itemId, e.getMessage());
            return null;
        }
    }

    /**
     * Obtiene TODAS las promociones del seller en una sola request.
     * <p>
     * Útil como short-circuit antes de iterar item por item: si el seller no tiene
     * ninguna promo en estado started/pending soportada, evita N consultas por item
     * (gran ahorro en runs con muchos MLAs sin promos activas).
     *
     * @param userId id del usuario seller (de {@link #getUserId()})
     * @return JSON crudo como String con campo {@code results} (array), o null si hubo error
     */
    public String obtenerPromocionesDelSeller(String userId) {
        verificarTokens();
        try {
            return retryHandler.get(
                    "/seller-promotions/users/" + userId + "?app_version=v2",
                    () -> tokens.accessToken
            );
        } catch (Exception e) {
            log.warn("ML - Error obteniendo promociones del seller {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * Elimina un item de una promoción específica.
     */
    public boolean removeItemFromPromotion(String promotionId, String itemId, String promotionType) {
        verificarTokens();
        try {
            retryHandler.delete(
                    "/seller-promotions/" + promotionId + "/items/" + itemId + "?promotion_type=" + promotionType,
                    () -> tokens.accessToken
            );
            return true;
        } catch (Exception e) {
            log.warn("ML - Error removiendo item {} de promoción {}: {}", itemId, promotionId, e.getMessage());
            return false;
        }
    }

    /**
     * Elimina al item de TODAS las promociones en las que esté (independiente del status).
     * Una sola llamada al endpoint bulk de ML.
     */
    public boolean removeAllItemPromotions(String itemId) {
        verificarTokens();
        try {
            retryHandler.delete(
                    "/seller-promotions/items/" + itemId + "?app_version=v2",
                    () -> tokens.accessToken
            );
            return true;
        } catch (Exception e) {
            log.warn("ML - Error excluyendo todas las promociones del item {}: {}", itemId, e.getMessage());
            return false;
        }
    }

    /**
     * Resultado de un intento de inclusión en promoción.
     * <ul>
     *   <li>{@code INCLUDED}: ML aceptó el item (POST 2xx).</li>
     *   <li>{@code REJECTED}: rechazo de negocio (4xx: credibilidad, fuera de rango, no elegible).
     *       No tiene sentido reintentar.</li>
     *   <li>{@code API_ERROR}: falla transitoria de la API (5xx / conexión) tras agotar reintentos.
     *       El item podría haber entrado en otra corrida.</li>
     * </ul>
     */
    public enum IncludeResult { INCLUDED, REJECTED, API_ERROR }

    /**
     * Incluye un item en una promoción (endpoint v2 de ML).
     * SMART / PRICE_MATCHING / MARKETPLACE_CAMPAIGN → requieren offer_id.
     * DEAL / SELLER_CAMPAIGN → requieren deal_price.
     *
     * @return {@link IncludeResult} según el desenlace (incluido / rechazado de negocio / error de API).
     */
    public IncludeResult addItemToPromotion(String promotionId, String itemId, String promotionType,
                                            double price, String offerId) {
        verificarTokens();
        try {
            String body;
            if (offerId != null) {
                body = String.format(
                        "{\"promotion_id\":\"%s\",\"promotion_type\":\"%s\",\"offer_id\":\"%s\"}",
                        promotionId, promotionType, offerId);
            } else {
                body = String.format(Locale.US,
                        "{\"promotion_id\":\"%s\",\"promotion_type\":\"%s\",\"deal_price\":%.2f}",
                        promotionId, promotionType, price);
            }
            retryHandler.postJson(
                    "/seller-promotions/items/" + itemId + "?app_version=v2",
                    () -> tokens.accessToken,
                    body
            );
            return IncludeResult.INCLUDED;
        } catch (HttpClientErrorException e) {
            // 4xx: rechazo de negocio (no reintentable). Caso típico: precio no creíble.
            String responseBody = e.getResponseBodyAsString();
            if (responseBody != null && responseBody.contains("ERROR_CREDIBILITY_DISCOUNTED_PRICE")) {
                log.info("ML - Precio no creíble (ignorado) al incluir item {} en promoción {} ({})", itemId, promotionId, promotionType);
            } else {
                log.warn("ML - Item {} rechazado en promoción {} ({}): {}", itemId, promotionId, promotionType, e.getStatusCode());
            }
            return IncludeResult.REJECTED;
        } catch (HttpServerErrorException | ResourceAccessException e) {
            // 5xx / conexión tras agotar reintentos: error transitorio de la API.
            log.warn("ML - Error de API al incluir item {} en promoción {} ({}): {}", itemId, promotionId, promotionType, e.getMessage());
            return IncludeResult.API_ERROR;
        } catch (Exception e) {
            log.warn("ML - Error inesperado al incluir item {} en promoción {} ({}): {}", itemId, promotionId, promotionType, e.getMessage());
            return IncludeResult.API_ERROR;
        }
    }

    /**
     * Actualiza el precio base de un item en ML (redondeado a entero, HALF_UP).
     */
    public boolean updateItemPrice(String itemId, double price) {
        if (price <= 0) {
            log.warn("ML - Se ignora actualización de precio para item {}: precio inválido ({})", itemId, price);
            return false;
        }
        verificarTokens();
        try {
            long precioEntero = Math.round(price);
            String body = "{\"price\":" + precioEntero + "}";
            retryHandler.putJson("/items/" + itemId, () -> tokens.accessToken, body);
            return true;
        } catch (Exception e) {
            log.warn("ML - Error actualizando precio de item {}: {}", itemId, e.getMessage());
            return false;
        }
    }

    /**
     * Actualiza el precio de todas las variaciones de un item en un solo PUT.
     * ML requiere que todas las variaciones vengan con el mismo precio en el request.
     * Si no se envían todos los IDs, las faltantes se borran, por eso es obligatorio
     * pasar la lista completa de variationIds del item.
     */
    public boolean updateItemPriceConVariaciones(String itemId, List<Long> variationIds, double price) {
        if (price <= 0) {
            log.warn("ML - Se ignora actualización de precio para item {} (con variaciones): precio inválido ({})", itemId, price);
            return false;
        }
        verificarTokens();
        try {
            long precioEntero = Math.round(price);
            StringBuilder variationsJson = new StringBuilder("[");
            for (int i = 0; i < variationIds.size(); i++) {
                if (i > 0) variationsJson.append(",");
                variationsJson.append("{\"id\":").append(variationIds.get(i))
                        .append(",\"price\":").append(precioEntero).append("}");
            }
            variationsJson.append("]");
            String body = "{\"variations\":" + variationsJson + "}";
            retryHandler.putJson("/items/" + itemId, () -> tokens.accessToken, body);
            return true;
        } catch (Exception e) {
            log.warn("ML - Error actualizando precio con variaciones de item {}: {}", itemId, e.getMessage());
            return false;
        }
    }

    /**
     * Actualiza el precio de un item respetando sus variaciones: si el item tiene variaciones,
     * ML exige enviar el precio dentro de cada variación (un PUT con todas al mismo precio); si no,
     * va en el {@code price} raíz. Mismo criterio que {@code AutomatizacionPreciosService}.
     */
    public boolean actualizarPrecioItemConDeteccionVariaciones(String mla, double price) {
        List<Long> variationIds = obtenerVariationIds(mla);
        if (!variationIds.isEmpty()) {
            return updateItemPriceConVariaciones(mla, variationIds, price);
        }
        return updateItemPrice(mla, price);
    }

    /**
     * Cambia el status de publicación de un item (active/paused). Devuelve false si no se pudo
     * (p. ej. el item está closed y no se puede reactivar) — el caller lo reporta como advertencia.
     */
    public boolean updateItemStatus(String mla, String status) {
        verificarTokens();
        try {
            retryHandler.putJson("/items/" + mla, () -> tokens.accessToken,
                    objectMapper.writeValueAsString(Map.of("status", status)));
            return true;
        } catch (Exception e) {
            log.warn("ML - Error actualizando status de item {} a {}: {}", mla, status, e.getMessage());
            return false;
        }
    }

    /** Ids de las variaciones de un item (lista vacía si no tiene o no se pudo leer). */
    private List<Long> obtenerVariationIds(String mla) {
        List<Long> ids = new ArrayList<>();
        try {
            String body = getItemVariations(mla);
            if (body == null) return ids;
            JsonNode variations = objectMapper.readTree(body).path("variations");
            if (variations.isArray()) {
                for (JsonNode v : variations) {
                    long id = v.path("id").asLong(0);
                    if (id > 0) ids.add(id);
                }
            }
        } catch (Exception e) {
            log.warn("ML - No se pudieron leer variaciones de {}: {}", mla, e.getMessage());
        }
        return ids;
    }

    /**
     * Multiget batch de price + variations para múltiples items (máx 20 por request).
     * Retorna mapa de MLA → body (con id, price, variations).
     */
    public Map<String, JsonNode> obtenerDatosItemsPorLote(List<String> mlaIds) {
        verificarTokens();
        Map<String, JsonNode> resultado = new LinkedHashMap<>();
        if (mlaIds == null || mlaIds.isEmpty()) return resultado;

        for (int i = 0; i < mlaIds.size(); i += 20) {
            List<String> batch = mlaIds.subList(i, Math.min(i + 20, mlaIds.size()));
            String ids = String.join(",", batch);

            try {
                String response = retryHandler.get(
                        "/items?ids=" + ids + "&attributes=id,price,variations",
                        () -> tokens.accessToken
                );
                if (response == null) continue;

                JsonNode array = objectMapper.readTree(response);
                for (JsonNode item : array) {
                    if (item.path("code").asInt(0) != 200) continue;
                    JsonNode body = item.path("body");
                    String id = body.path("id").asString(null);
                    if (id != null) {
                        resultado.put(id, body);
                    }
                }
            } catch (Exception e) {
                log.warn("ML - Error obteniendo datos de items batch: {}", e.getMessage());
            }
        }
        return resultado;
    }

    /**
     * Obtiene las variaciones de un item.
     * Retorna el JSON crudo como String.
     */
    public String getItemVariations(String itemId) {
        verificarTokens();
        try {
            return retryHandler.get("/items/" + itemId + "?attributes=variations", () -> tokens.accessToken);
        } catch (Exception e) {
            log.warn("ML - Error obteniendo variaciones de item {}: {}", itemId, e.getMessage());
            return null;
        }
    }

    /**
     * Obtiene el status de múltiples items en batch (máx 20 por request).
     * Retorna mapa de MLA → status (active, paused, closed, etc.)
     */
    public Map<String, String> obtenerStatusItems(List<String> mlaIds) {
        verificarTokens();
        Map<String, String> statusMap = new LinkedHashMap<>();

        for (int i = 0; i < mlaIds.size(); i += 20) {
            List<String> batch = mlaIds.subList(i, Math.min(i + 20, mlaIds.size()));
            String ids = String.join(",", batch);

            try {
                String response = retryHandler.get(
                        "/items?ids=" + ids + "&attributes=id,status",
                        () -> tokens.accessToken
                );
                if (response == null) continue;

                JsonNode array = objectMapper.readTree(response);
                for (JsonNode item : array) {
                    if (item.path("code").asInt(0) == 200) {
                        String id = item.path("body").path("id").asString(null);
                        String status = item.path("body").path("status").asString(null);
                        if (id != null && status != null) {
                            statusMap.put(id, status);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("ML - Error obteniendo status de items batch: {}", e.getMessage());
            }
        }
        return statusMap;
    }

    /**
     * Obtiene los items activos del vendedor usando scroll.
     * Retorna lista de MLA IDs.
     */
    public List<String> obtenerItemsActivos() {
        verificarTokens();
        try {
            String userId = getUserId();
            List<String> items = new ArrayList<>();
            String scrollId = null;

            while (true) {
                String uri = "/users/" + userId + "/items/search?status=active&limit=100&search_type=scan"
                        + (scrollId != null ? "&scroll_id=" + URLEncoder.encode(scrollId, StandardCharsets.UTF_8) : "");
                String response = retryHandler.get(uri, () -> tokens.accessToken);
                if (response == null) break;

                JsonNode root = objectMapper.readTree(response);
                JsonNode results = root.get("results");
                if (results == null || results.isEmpty()) break;

                for (JsonNode item : results) {
                    items.add(item.asString());
                }

                scrollId = root.path("scroll_id").asString(null);
                if (scrollId == null) break;
            }
            return items;
        } catch (Exception e) {
            log.error("ML - Error obteniendo items activos: {}", e.getMessage());
            return List.of();
        }
    }

    private static final String PENDING_MLA_RECALC_KEY = "MercadoLibreService.pendingMlaRecalc";

    /**
     * Acumulador de mlaIds por thread para los procesos masivos. Cuando está
     * activo (no-null), {@link #programarRecalculoMlaPostCommit} agrega el id
     * acá en vez de despachar un recálculo individual. Al final del masivo se
     * dispara UN solo recálculo batched. Evita la avalancha de N×2 toasts
     * "iniciado/finalizado" cuando se procesan cientos de MLAs.
     */
    private final ThreadLocal<Set<Integer>> masivoMlaBatch = new ThreadLocal<>();

    /**
     * Registra un recálculo por MLA para ejecutarse post-commit, deduplicado por mlaId
     * dentro de la transacción actual. Evita doble recálculo cuando en el mismo flujo
     * se actualizan comisión y envío del mismo MLA.
     *
     * <p>Si hay un masivo activo en el thread (ver {@link #masivoMlaBatch}), solo
     * acumula el id en el batch — el masivo se encarga de dispatch al final.
     */
    private void programarRecalculoMlaPostCommit(Integer mlaId) {
        if (mlaId == null) {
            return;
        }
        // Si el masivo está activo en este thread, sumamos al batch y salimos.
        // El dispatch lo hace el masivo en su finally.
        Set<Integer> batchMasivo = masivoMlaBatch.get();
        if (batchMasivo != null) {
            batchMasivo.add(mlaId);
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            recalculoPrecioFacade.ejecutarRecalculoAsync(
                    "Recálculo por cambio en MLA " + describirMla(mlaId),
                    () -> recalculoPrecioFacade.recalcularProductosDelMla(mlaId));
            return;
        }
        @SuppressWarnings("unchecked")
        Set<Integer> pending = (Set<Integer>) TransactionSynchronizationManager.getResource(PENDING_MLA_RECALC_KEY);
        if (pending == null) {
            final Set<Integer> nuevos = new LinkedHashSet<>();
            TransactionSynchronizationManager.bindResource(PENDING_MLA_RECALC_KEY, nuevos);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // UNA sola tarea async para TODOS los MLAs de la transacción. Despachar
                    // una tarea por MLA fallaba: ejecutarRecalculoAsync adquiere el lock del
                    // grupo BD, así que solo la primera corría y el resto se descartaba
                    // ("recálculo pospuesto"), dejando esos productos sin recalcular.
                    dispatchBatchRecalculoMla(nuevos);
                }

                @Override
                public void afterCompletion(int status) {
                    if (TransactionSynchronizationManager.hasResource(PENDING_MLA_RECALC_KEY)) {
                        TransactionSynchronizationManager.unbindResource(PENDING_MLA_RECALC_KEY);
                    }
                }
            });
            pending = nuevos;
        }
        pending.add(mlaId);
    }

    /**
     * Devuelve el código del MLA (ej. "MLA1234567890") a partir de su id en BD.
     * Si no se encuentra, devuelve "#id" como fallback. Usado solo para armar
     * el string de descripción del proceso async — fuera de hot path.
     */
    private String describirMla(Integer mlaId) {
        if (mlaId == null) return "?";
        return mlaRepository.findById(mlaId)
                .map(Mla::getMla)
                .filter(s -> s != null && !s.isBlank())
                .orElse("#" + mlaId);
    }

    /**
     * Despacha en una sola tarea async el recálculo de los productos de todos
     * los MLAs del batch. Una sola entrada en el badge del header y un solo
     * toast al finalizar — en vez de N toasts individuales.
     */
    private void dispatchBatchRecalculoMla(Set<Integer> mlaIds) {
        if (mlaIds == null || mlaIds.isEmpty()) return;
        Set<Integer> snapshot = Set.copyOf(mlaIds);
        String descripcion = "Recálculo post-cambio masivo ML: " + snapshot.size() + " MLAs";
        recalculoPrecioFacade.ejecutarRecalculoAsync(descripcion, () -> {
            for (Integer id : snapshot) {
                try {
                    recalculoPrecioFacade.recalcularProductosDelMla(id);
                } catch (Exception e) {
                    log.warn("Error recalculando productos del MLA id={}: {}", id, e.getMessage());
                }
            }
        });
    }

    // ==================== ACTUALIZACIÓN DE ITEM EN ML ====================

    /** Actualiza el precio de un item (mla, price)->ok. */
    @FunctionalInterface
    public interface ActualizadorPrecioItem {
        boolean actualizar(String mla, double price);
    }

    /** Cambia el estado de publicación de un item (mla, "active"|"paused") -> ok. */
    @FunctionalInterface
    public interface ActualizadorEstadoItem {
        boolean actualizar(String mla, String status);
    }

    /** Concatena un mensaje de advertencia best-effort al acumulado (separador "; "); null-safe (no pisa la previa). */
    public static String concatAdv(String previa, String mensaje) {
        return (previa == null || previa.isBlank()) ? mensaje : previa + "; " + mensaje;
    }

    /** Si el alta/actualización fue exitosa y hubo imágenes omitidas, las agrega como advertencia. */
    static ResultadoAltaMl aplicarRechazadasImagenes(ResultadoAltaMl r, List<ImagenService.ImagenRechazada> rechazadas) {
        if (rechazadas == null || rechazadas.isEmpty()) return r;
        if (r.estado() != ResultadoAltaMl.Estado.CREADO && r.estado() != ResultadoAltaMl.Estado.ACTUALIZADO) return r;
        return r.conAdvertencia(concatAdv(r.advertencia(), ImagenService.describirRechazadas(rechazadas)));
    }

    /**
     * Núcleo testeable de la actualización de un item ML (sin red).
     *  - soldQtyFn(mla) → unidades vendidas (para decidir si se puede cambiar el título).
     *  - putTitle(mla, title) → PUT del título (solo se llama si sold_quantity == 0).
     *  - putDesc(mla, plainText) → PUT de la descripción.
     *  - updatePrice(mla, price) → actualiza el precio (price = costo × 5).
     *  - resolverPictureIds(sku) → lista de picture IDs ya subidos (vacía = sin imágenes).
     *  - putPictures(mla, pictureIds) → PUT del array de imágenes (solo si no vacío).
     *  - putStatus(mla, status) → cambia el estado de publicación ("active"/"paused", best-effort).
     */
    public static ResultadoAltaMl actualizarItemEnMlCore(
            ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto, String mla,
            Function<String, Integer> soldQtyFn,
            BiConsumer<String, String> putTitle,
            BiConsumer<String, String> putDesc,
            ActualizadorPrecioItem updatePrice,
            Function<String, List<String>> resolverPictureIds,
            BiConsumer<String, List<String>> putPictures,
            ActualizadorEstadoItem putStatus) {
        try {
            if (producto.getTituloMl() == null || producto.getTituloMl().isBlank())
                return ResultadoAltaMl.error("falta título ML");
            if (producto.getCosto() == null)
                return ResultadoAltaMl.error("falta costo");

            String advertencia = null;
            int soldQty = soldQtyFn.apply(mla);
            if (soldQty == 0) {
                putTitle.accept(mla, producto.getTituloMl());
            } else {
                advertencia = "título no actualizado (la publicación tuvo ventas)";
            }

            putDesc.accept(mla, MlDescripcionBuilder.construir(producto));

            double price = producto.getCosto().multiply(MULTIPLICADOR_PRECIO_ML).doubleValue();
            if (!updatePrice.actualizar(mla, price)) {
                advertencia = concatAdv(advertencia, "precio no actualizado");
            }

            try {
                List<String> pictureIds = resolverPictureIds.apply(producto.getSku());
                if (pictureIds != null && !pictureIds.isEmpty()) {
                    putPictures.accept(mla, pictureIds);
                }
            } catch (Exception e) {
                advertencia = concatAdv(advertencia, "imágenes no actualizadas");
            }

            String estadoTarget = Boolean.TRUE.equals(producto.getActivo()) ? "active" : "paused";
            if (!putStatus.actualizar(mla, estadoTarget)) {
                advertencia = concatAdv(advertencia, "estado de publicación no actualizado");
            }

            ResultadoAltaMl r = ResultadoAltaMl.actualizado(mla);
            return advertencia == null ? r : r.conAdvertencia(advertencia);
        } catch (Exception e) {
            return ResultadoAltaMl.error(e.getMessage());
        }
    }

    /** Actualiza una publicación existente en ML (título si sin ventas, descripción, precio, imágenes). Delega al core. */
    public ResultadoAltaMl actualizarItemEnMl(ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto, String mla) {
        if (!isConfigured()) return ResultadoAltaMl.error("Mercado Libre no configurado");
        verificarTokens();
        ImagenService.FiltroImagenes filtro = imagenService.filtrarParaCanal(producto.getSku(), EXT_ML);
        ResultadoAltaMl r = actualizarItemEnMlCore(
                producto, mla,
                this::leerSoldQuantity,
                (m, title) -> {
                    try { retryHandler.putJson("/items/" + m, () -> tokens.accessToken,
                            objectMapper.writeValueAsString(Map.of("title", title))); }
                    catch (Exception e) { throw new RuntimeException("título: " + e.getMessage(), e); }
                },
                (m, plainText) -> {
                    try { retryHandler.putJson("/items/" + m + "/description", () -> tokens.accessToken,
                            objectMapper.writeValueAsString(Map.of("plain_text", plainText))); }
                    catch (Exception e) { throw new RuntimeException("descripción: " + e.getMessage(), e); }
                },
                this::actualizarPrecioItemConDeteccionVariaciones,
                sku -> {
                    List<String> ids = new ArrayList<>();
                    for (String filename : filtro.validas()) {
                        String picId = subirImagenItem(filename);
                        if (picId != null && !picId.isBlank()) ids.add(picId);
                    }
                    return ids;
                },
                (m, pictureIds) -> {
                    try {
                        List<Map<String, Object>> pics = new ArrayList<>();
                        for (String id : pictureIds) pics.add(Map.of("id", id));
                        retryHandler.putJson("/items/" + m, () -> tokens.accessToken,
                                objectMapper.writeValueAsString(Map.of("pictures", pics)));
                    } catch (Exception e) { throw new RuntimeException("imágenes: " + e.getMessage(), e); }
                },
                this::updateItemStatus);
        return aplicarRechazadasImagenes(r, filtro.rechazadas());
    }

    /** Lee sold_quantity de un item (0 si no se puede determinar). */
    private int leerSoldQuantity(String mla) {
        try {
            String body = retryHandler.get("/items/" + mla + "?attributes=sold_quantity", () -> tokens.accessToken);
            if (body == null) return 0;
            return objectMapper.readTree(body).path("sold_quantity").asInt(0);
        } catch (Exception e) {
            log.warn("ML - No se pudo leer sold_quantity de {}: {}", mla, e.getMessage());
            return 0;
        }
    }

    // ==================== ALTA DE ITEM EN ML ====================

    /**
     * Núcleo testeable del alta a ML. Las lambdas aíslan la red:
     *  - yaExiste(sku) → true si ya hay publicación (no duplicar).
     *  - archivosResolver(sku) → nombres de archivo de imagen del SKU.
     *  - subidorImagen(filename) → pictureId subido (o null si falló esa imagen).
     *  - predictor(titulo) → category_id (o null).
     *  - poster(json) → respuesta de POST /items (éxito con "id" o body de error con "cause").
     *  - posterDescripcion(itemId, plainText) → respuesta (se ignora salvo excepción).
     */
    static ResultadoAltaMl crearItemEnMlCore(
            ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto, ObjectMapper om,
            Function<String, Boolean> yaExiste,
            Function<String, List<String>> archivosResolver,
            Function<String, String> subidorImagen,
            Function<String, String> predictor,
            Function<String, String> poster,
            BiFunction<String, String, String> posterDescripcion) {
        try {
            String sku = producto.getSku();
            if (producto.getTituloMl() == null || producto.getTituloMl().isBlank())
                return ResultadoAltaMl.error("falta título ML");
            if (producto.getCosto() == null)
                return ResultadoAltaMl.error("falta costo");
            if (Boolean.TRUE.equals(yaExiste.apply(sku)))
                return ResultadoAltaMl.yaExistia();

            List<String> archivos = archivosResolver.apply(sku);
            if (archivos == null || archivos.isEmpty())
                return ResultadoAltaMl.error("sin imágenes (obligatorias para publicación clásica)");
            List<String> pictureIds = new ArrayList<>();
            for (String filename : archivos) {
                String picId = subidorImagen.apply(filename);
                if (picId != null && !picId.isBlank()) pictureIds.add(picId);
            }
            if (pictureIds.isEmpty())
                return ResultadoAltaMl.error("no se pudieron subir las imágenes");

            String categoryId = predictor.apply(producto.getTituloMl());
            if (categoryId == null || categoryId.isBlank())
                return ResultadoAltaMl.error("no se pudo predecir la categoría");

            // Precio de alta en ML: costo x 5 (regla de negocio de la Fase C1).
            BigDecimal price = producto.getCosto().multiply(MULTIPLICADOR_PRECIO_ML);

            // Crear con stock 0: queda out_of_stock; si el producto está inactivo, crearItemEnMl
            // lo pausa explícitamente (paused_by_seller) después del alta.
            String respuesta = poster.apply(om.writeValueAsString(
                    MlItemPayloadBuilder.construir(producto, categoryId, price, 0, pictureIds)));
            String error = extraerErrorMl(om, respuesta);
            if (error != null) return ResultadoAltaMl.error(error);

            JsonNode creado = om.readTree(respuesta);
            String itemId = creado.path("id").asString("");
            if (itemId.isBlank()) return ResultadoAltaMl.error("ML no devolvió id del ítem");
            String mlau = creado.path("user_product_id").asString("");

            String advertencia = null;
            try {
                posterDescripcion.apply(itemId, MlDescripcionBuilder.construir(producto));
            } catch (Exception e) {
                advertencia = "ítem creado pero falló la descripción";
            }

            ResultadoAltaMl r = ResultadoAltaMl.creado(itemId, mlau.isBlank() ? null : mlau);
            return advertencia == null ? r : r.conAdvertencia(advertencia);
        } catch (Exception e) {
            return ResultadoAltaMl.error(e.getMessage());
        }
    }

    /** Si la respuesta de ML es un error (tiene "cause" con type:error o un "error"/"message" de validación), devuelve el texto; si no, null. */
    private static String extraerErrorMl(ObjectMapper om, String respuesta) {
        if (respuesta == null || respuesta.isBlank()) return null;
        JsonNode root = om.readTree(respuesta);
        JsonNode cause = root.path("cause");
        if (cause.isArray() && !cause.isEmpty()) {
            List<String> errores = new ArrayList<>();
            for (JsonNode c : cause) {
                if ("error".equals(c.path("type").asString(""))) errores.add(c.path("message").asString(""));
            }
            if (!errores.isEmpty()) return String.join("; ", errores);
        }
        // Sin "id" y con "error"/"message" → fallo.
        if (root.path("id").asString("").isBlank() && !root.path("error").asString("").isBlank())
            return root.path("message").asString(root.path("error").asString("Error de Mercado Libre"));
        return null;
    }

    /**
     * Da de alta un producto en Mercado Libre (sitio MLA). Resuelve las dependencias de red y delega al núcleo.
     * La verificación de existencia previa es responsabilidad del caller (MlExportService hace upsert y solo
     * llega aquí tras confirmar que buscarMlaPorSku devolvió null), por lo que se pasa yaExiste = false
     * para evitar repetir la búsqueda costosa.
     */
    public ResultadoAltaMl crearItemEnMl(ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto) {
        if (!isConfigured()) return ResultadoAltaMl.error("Mercado Libre no configurado");
        verificarTokens();
        ImagenService.FiltroImagenes filtro = imagenService.filtrarParaCanal(producto.getSku(), EXT_ML);
        ResultadoAltaMl r = crearItemEnMlCore(
                producto, objectMapper,
                sku -> false,  // existencia ya verificada por el caller (upsert en MlExportService)
                sku -> filtro.validas(),
                filename -> subirImagenItem(filename),
                titulo -> resolverCategoriaMl(producto.getMlCategoryId(), titulo, this::predecirCategoria),
                json -> postearItem(json),
                (itemId, plainText) -> retryHandler.postJson("/items/" + itemId + "/description",
                        () -> tokens.accessToken, objectMapper.writeValueAsString(Map.of("plain_text", plainText))));
        r = aplicarRechazadasImagenes(r, filtro.rechazadas());

        // Producto inactivo: dejar la publicación recién creada en paused (best-effort).
        // concatAdv preserva una advertencia previa del alta (descripción / imágenes omitidas).
        if (r.estado() == ResultadoAltaMl.Estado.CREADO
                && r.itemId() != null
                && !Boolean.TRUE.equals(producto.getActivo())
                && !updateItemStatus(r.itemId(), "paused")) {
            return r.conAdvertencia(MercadoLibreService.concatAdv(r.advertencia(), "estado de publicación no actualizado (no se pudo pausar)"));
        }
        return r;
    }

    /** Sube una imagen a ML por multipart y devuelve su picture_id (o null si falla). */
    private String subirImagenItem(String filename) {
        try {
            byte[] bytes = imagenService.leerBytes(filename);
            String resp = retryHandler.postMultipart("/pictures/items/upload", () -> tokens.accessToken, filename, bytes);
            String id = objectMapper.readTree(resp).path("id").asString("");
            return id.isBlank() ? null : id;
        } catch (Exception e) {
            log.warn("ML - Falló subir imagen {}: {}", filename, e.getMessage());
            return null;
        }
    }

    /** Parsea la respuesta del predictor (domain_discovery) a la lista de categorías predichas. Testeable sin red. */
    public static List<PrediccionCategoriaMlDTO> parsePredicciones(JsonNode arr) {
        List<PrediccionCategoriaMlDTO> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                String id = n.path("category_id").asString("");
                if (!id.isBlank()) {
                    out.add(new PrediccionCategoriaMlDTO(id, n.path("category_name").asString("")));
                }
            }
        }
        return out;
    }

    /** Predictor de categorías de ML: top-N predicciones a partir del título. Lista vacía si falla. */
    public List<PrediccionCategoriaMlDTO> predecirCategorias(String titulo, int limit) {
        verificarTokens();
        try {
            String uri = "/sites/MLA/domain_discovery/search?limit=" + limit + "&q="
                    + URLEncoder.encode(titulo, StandardCharsets.UTF_8);
            String resp = retryHandler.get(uri, () -> tokens.accessToken);
            return parsePredicciones(objectMapper.readTree(resp));
        } catch (Exception e) {
            log.warn("ML - Falló predecir categorías para '{}': {}", titulo, e.getMessage());
            return List.of();
        }
    }

    /** Categoría de mayor probabilidad (o null) — fallback automático del alta. */
    private String predecirCategoria(String titulo) {
        List<PrediccionCategoriaMlDTO> preds = predecirCategorias(titulo, 1);
        return preds.isEmpty() ? null : preds.get(0).categoryId();
    }

    /** Categoría a usar en el alta de ML: la guardada en el producto si existe, sino la del predictor automático. */
    public static String resolverCategoriaMl(String categoriaGuardada, String tituloMl, Function<String, String> autoPredictor) {
        if (categoriaGuardada != null && !categoriaGuardada.isBlank()) return categoriaGuardada;
        return autoPredictor.apply(tituloMl);
    }

    /** POST /items devolviendo el body (éxito o, ante 4xx, el body de error de ML). */
    private String postearItem(String json) {
        try {
            return retryHandler.postJson("/items", () -> tokens.accessToken, json);
        } catch (HttpClientErrorException e) {
            return e.getResponseBodyAsString();
        }
    }

}

package ar.com.leo.super_master_backend.apis.dux.service;

import ar.com.leo.super_master_backend.apis.dux.DuxRetryHandler;
import ar.com.leo.super_master_backend.apis.dux.config.DuxProperties;
import ar.com.leo.super_master_backend.apis.dux.dto.DeudaClienteDuxDTO;
import ar.com.leo.super_master_backend.apis.dux.dto.ExportDuxResultDTO;
import ar.com.leo.super_master_backend.apis.dux.dto.ImportDuxResultDTO;
import ar.com.leo.super_master_backend.dominio.common.dto.ExportCanalResultDTO;
import ar.com.leo.super_master_backend.apis.dux.model.*;
import ar.com.leo.super_master_backend.dominio.common.dto.ProcesoMasivoEstadoDTO;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.common.exception.ServiceNotConfiguredException;
import ar.com.leo.super_master_backend.dominio.common.service.EstadoProcesoMasivo;
import ar.com.leo.super_master_backend.dominio.common.service.ProcesoGlobalService;
import ar.com.leo.super_master_backend.dominio.producto.calculo.service.RecalculoPrecioFacade;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import ar.com.leo.super_master_backend.dominio.proveedor.entity.Proveedor;
import ar.com.leo.super_master_backend.dominio.proveedor.repository.ProveedorRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class DuxService {

    private static final int MAX_INTENTOS_VACIOS = 3;
    private static final long BASE_WAIT_MS = 5000L; // DUX es lento, esperar más
    private static final BigDecimal COSTO_MAXIMO = new BigDecimal("99999999.99");

    private final RestClient restClient;
    private final DuxProperties properties;
    private final ObjectMapper objectMapper;

    @Value("${app.secrets-dir}")
    private String secretsDir;
    private final ProductoRepository productoRepository;
    private final ProveedorRepository proveedorRepository;
    private final RecalculoPrecioFacade recalculoPrecioFacade;
    private final ProcesoGlobalService procesoGlobal;
    private final DuxItemBuilder duxItemBuilder;

    private DuxRetryHandler retryHandler;
    private TokensDux tokens;

    private final Map<String, Proveedor> cacheProveedores = new HashMap<>();

    // Auto-inyección para proxy de Spring (@Async)
    @Lazy
    @Autowired
    private DuxService self;

    // Trackers reusables: estado + locks + supplier de progreso al SSE.
    private EstadoProcesoMasivo trackerImportacion;
    private EstadoProcesoMasivo trackerDeudas;

    // Resultados de las últimas ejecuciones (consultables post-finalización).
    private volatile ImportDuxResultDTO resultadoImportacion = null;
    private volatile Map<String, Object> resultadoDeudas = null;
    private volatile DeudasConsultaParams paramsDeudasActual = null;

    public record DeudasConsultaParams(
            String fechaDesde, String fechaHasta, int idEmpresa,
            List<Integer> idsSucursal, Boolean conCobro,
            String cliente, Boolean anuladas) {
    }

    // =====================================================
    // PARSEO / MAPEO DE PROCESO DUX
    // =====================================================

    public record EstadoProceso(String estado, List<String> errores) {
        boolean finalizado() { return "FINALIZADO".equalsIgnoreCase(estado); }
    }

    static EstadoProceso parsearEstadoProceso(String json, ObjectMapper om) {
        try {
            JsonNode root = om.readTree(json);
            String estado = root.path("estado").asString();
            List<String> errores = new java.util.ArrayList<>();
            JsonNode errs = root.path("errores");
            if (errs.isArray()) for (JsonNode e : errs) errores.add(e.asString());
            return new EstadoProceso(estado, errores);
        } catch (Exception ex) {
            return new EstadoProceso("", List.of());
        }
    }

    // Mapeo del proceso FINALIZADO a resultado de canal.
    static ExportCanalResultDTO mapearResultadoProceso(EstadoProceso e, int cantidadProductos) {
        if (!e.errores().isEmpty()) {
            return new ExportCanalResultDTO(0, List.of(), List.of(), List.copyOf(e.errores()), List.of());
        }
        return new ExportCanalResultDTO(cantidadProductos, List.of(), List.of(), List.of(), List.of());
    }

    static ExportCanalResultDTO resultadoSinConfirmar(int idProceso) {
        return new ExportCanalResultDTO(0, List.of(), List.of(), List.of(),
                List.of("encolado sin confirmar (proceso #" + idProceso + ")"));
    }

    public DuxService(RestClient duxRestClient, DuxProperties properties, ObjectMapper objectMapper,
                      ProductoRepository productoRepository, ProveedorRepository proveedorRepository,
                      RecalculoPrecioFacade recalculoPrecioFacade,
                      ProcesoGlobalService procesoGlobal,
                      DuxItemBuilder duxItemBuilder) {
        this.restClient = duxRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.productoRepository = productoRepository;
        this.proveedorRepository = proveedorRepository;
        this.recalculoPrecioFacade = recalculoPrecioFacade;
        this.procesoGlobal = procesoGlobal;
        this.duxItemBuilder = duxItemBuilder;
    }

    @PostConstruct
    public void init() {
        this.retryHandler = new DuxRetryHandler(
                restClient,
                BASE_WAIT_MS,
                properties.rateLimitPerSecond()
        );
        this.trackerImportacion = new EstadoProcesoMasivo(
                "dux-importacion", "Importación de productos DUX", procesoGlobal);
        this.trackerDeudas = new EstadoProcesoMasivo(
                "dux-deudas", "Consulta de deudas de clientes DUX", procesoGlobal);
        cargarTokens();
    }

    public DuxRetryHandler getRetryHandler() {
        return retryHandler;
    }

    public double getRateLimitSegundos() {
        return 1.0 / retryHandler.getRate();
    }

    public void setRateLimitSegundos(double segundos) {
        if (segundos < 5) segundos = 5;
        if (segundos > 60) segundos = 60;
        retryHandler.setRate(1.0 / segundos);
    }

    // =====================================================
    // LISTAS DE PRECIOS
    // =====================================================

    /**
     * Obtiene todas las listas de precios de venta.
     */
    public JsonNode obtenerListasPrecios() {
        verificarTokens();

        String response = retryHandler.get("/listaprecioventa", tokens.token);
        return objectMapper.readTree(response);
    }

    /**
     * Obtiene el ID de una lista de precios por su nombre.
     */
    public long obtenerIdListaPrecio(String nombreLista) {
        verificarTokens();

        String response = retryHandler.get("/listaprecioventa", tokens.token);
        JsonNode root = objectMapper.readTree(response);

        if (!root.isArray()) {
            throw new IllegalStateException("La respuesta no es un array de listas de precios");
        }

        for (JsonNode node : root) {
            String nombre = node.get("lista_precio_venta").asString("");
            if (nombre.equalsIgnoreCase(nombreLista)) {
                return node.get("id_lista_precio_venta").asLong();
            }
        }

        throw new IllegalArgumentException("No se encontró la lista de precios: " + nombreLista);
    }

    /**
     * Modifica precios de productos en una lista de precios.
     *
     * <p><b>IMPORTANTE:</b> Es obligatorio enviar el tipo de producto (SIMPLE o COMBO) junto con el precio.
     * Si no se envía el tipo correcto, DUX desconfigura el producto y puede perder su configuración
     * de componentes (en caso de combos) u otras propiedades.</p>
     *
     * @param productos     Mapa de SKU -> datos del producto (tipo, precio)
     * @param idListaPrecio ID de la lista de precios en DUX
     * @return ID del proceso de importación, o 0 si falló
     */
    public int modificarListaPrecios(Map<String, ProductoPrecioData> productos, long idListaPrecio) {
        verificarTokens();

        List<Map<String, Object>> productosJson = new ArrayList<>();

        for (Map.Entry<String, ProductoPrecioData> entry : productos.entrySet()) {
            String sku = entry.getKey();
            ProductoPrecioData data = entry.getValue();

            if (sku != null && data.precio() > 0 &&
                    ("SIMPLE".equals(data.tipo()) || "COMBO".equals(data.tipo()))) {

                Map<String, Object> producto = Map.of(
                        "cod_item", sku,
                        "tipo_producto", data.tipo(),
                        "precios", List.of(Map.of(
                                "importe", data.precio(),
                                "id_lista_precio_venta", idListaPrecio,
                                "id_moneda", 1 // ARS
                        ))
                );
                productosJson.add(producto);
            } else {
                log.warn("DUX - Producto inválido, SKU: {}, Tipo: {}, Precio: {}",
                        sku, data.tipo(), data.precio());
            }
        }

        if (productosJson.isEmpty()) {
            log.warn("DUX - No hay productos válidos para modificar");
            return 0;
        }

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(Map.of("productos", productosJson));
        } catch (Exception e) {
            log.error("DUX - Error serializando productos", e);
            throw new RuntimeException("Error preparando datos para DUX", e);
        }

        String response = retryHandler.postJson("/item/nuevoItem", tokens.token, jsonBody);

        if (response == null) {
            log.warn("DUX - No se recibió respuesta al modificar lista de precios");
            return 0;
        }

        Pattern pattern = Pattern.compile("ID de proceso:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            int idProceso = Integer.parseInt(matcher.group(1));
            log.info("DUX - Proceso iniciado con ID: {}", idProceso);
            return idProceso;
        }

        log.warn("DUX - No se encontró ID de proceso en respuesta: {}", response);
        return 0;
    }

    /**
     * Obtiene el estado de un proceso DUX (importación o exportación).
     */
    public String obtenerEstadoProceso(int idProceso) {
        verificarTokens();
        return retryHandler.get("/obtenerEstadoItem?idProceso=" + idProceso, tokens.token);
    }

    /** Estado del proceso ya parseado ({@code estado} + {@code errores}) para los consumidores que necesitan los campos. */
    public EstadoProceso obtenerEstadoProcesoParseado(int idProceso) {
        return parsearEstadoProceso(obtenerEstadoProceso(idProceso), objectMapper);
    }

    // =====================================================
    // PRODUCTOS
    // =====================================================

    /**
     * Obtiene todos los productos de DUX con paginación automática.
     * Los campos UNIDAD MEDIDA, TIPO DE PRODUCTO, FECHA_ULT_COSTO y UNIDADES POR BULTO no
     * están en la respuesta de la API de DUX.
     * Solo permite obtener de maximo 50 productos por request cada 5 segundos.
     */
    public List<Item> obtenerProductos() {
        return obtenerProductos(null, null);
    }

    /**
     * Obtiene productos de DUX, opcionalmente filtrados por fecha.
     *
     * @param cancelFlag Flag para cancelar la operación
     * @param desde      Fecha desde la cual filtrar movimientos de stock o cambios de precio.
     *                   Si es null, trae todos los productos.
     */
    public List<Item> obtenerProductos(AtomicBoolean cancelFlag, LocalDateTime desde) {
        return obtenerProductos(cancelFlag, desde, null);
    }

    /**
     * Obtiene productos de DUX con callback de progreso por página.
     *
     * @param cancelFlag Flag para cancelar la operación
     * @param desde      Fecha desde la cual filtrar (null = todos)
     * @param onProgress Callback (páginaActual, totalPáginas). totalPáginas=0 si desconocido.
     */
    public List<Item> obtenerProductos(AtomicBoolean cancelFlag, LocalDateTime desde,
                                       BiConsumer<Integer, Integer> onProgress) {
        verificarTokens();

        List<Item> allItems = new ArrayList<>();
        int offset = 0;
        int total = Integer.MAX_VALUE;
        int limit = properties.itemsPerPage();
        int intentosVacios = 0;

        // Construir query base con filtro de fecha opcional
        String fechaParam = "";
        if (desde != null) {
            DateTimeFormatter duxFmt = DateTimeFormatter.ofPattern("ddMMyyyy HH:mm");
            fechaParam = "&fecha=" + desde.format(duxFmt);
            log.info("DUX - Filtrando items modificados desde {}", desde);
        }

        while (offset < total) {
            if (cancelFlag != null && cancelFlag.get()) {
                log.info("DUX - Obtención de productos cancelada. Obtenidos: {}", allItems.size());
                break;
            }
            String response = retryHandler.get(
                    "/items?offset=" + offset + "&limit=" + limit + fechaParam,
                    tokens.token
            );

            if (response == null) {
                log.error("DUX - Error obteniendo productos en offset {}", offset);
                break;
            }

            DuxResponse duxResponse;
            try {
                duxResponse = objectMapper.readValue(response, DuxResponse.class);
            } catch (Exception e) {
                log.error("DUX - Error parseando respuesta", e);
                break;
            }

            // Actualizar total
            if (duxResponse.getPaging() != null) {
                int nuevoTotal = duxResponse.getPaging().getTotal();
                if (total == Integer.MAX_VALUE || nuevoTotal != total) {
                    total = nuevoTotal;
                    log.info("DUX - Total de productos: {}", total);
                }
            }

            // Verificar resultados
            if (duxResponse.getResults() == null || duxResponse.getResults().isEmpty()) {
                if (offset >= total) {
                    log.info("DUX - Fin de paginación (offset >= total)");
                    break;
                }

                intentosVacios++;
                log.warn("DUX - Respuesta vacía en offset {} (intento {}/{}). Reintentando mismo offset.",
                        offset, intentosVacios, MAX_INTENTOS_VACIOS);

                if (intentosVacios >= MAX_INTENTOS_VACIOS) {
                    log.warn("DUX - Terminando después de {} intentos vacíos en offset {}. Obtenidos: {}/{}",
                            MAX_INTENTOS_VACIOS, offset, allItems.size(), total);
                    break;
                }

                // No avanzar el offset: reintentar el mismo bloque para evitar
                // saltarse items si la página vacía es un blip transitorio.
                continue;
            }

            intentosVacios = 0;
            allItems.addAll(duxResponse.getResults());

            log.info("DUX - Obtenidos: {}/{} (offset: {})", allItems.size(), total, offset);

            if (onProgress != null) {
                int currentPage = offset / limit + 1;
                int totalPages = (total != Integer.MAX_VALUE) ? (int) Math.ceil((double) total / limit) : 0;
                onProgress.accept(currentPage, totalPages);
            }

            offset += limit;

            if (allItems.size() >= total) {
                log.info("DUX - Todos los productos obtenidos");
                break;
            }
        }

        // Dedupe por cod_item: cuando se filtra por `fecha` y hay escrituras concurrentes,
        // el paging.total puede variar entre páginas y el mismo item aparecer dos veces.
        // Conservamos la última ocurrencia (la más reciente en la iteración).
        int antesDedupe = allItems.size();
        Map<String, Item> dedupePorCodigo = new LinkedHashMap<>();
        for (Item it : allItems) {
            String key = (it.getCodItem() != null) ? it.getCodItem().trim() : null;
            if (key == null || key.isEmpty()) {
                // Sin código no podemos deduplicar; lo conservamos con clave única.
                dedupePorCodigo.put("__sinCod_" + System.identityHashCode(it), it);
            } else {
                dedupePorCodigo.put(key, it);
            }
        }
        List<Item> resultado = new ArrayList<>(dedupePorCodigo.values());
        if (resultado.size() != antesDedupe) {
            log.info("DUX - Dedupe aplicado: {} → {} items ({} duplicados removidos)",
                    antesDedupe, resultado.size(), antesDedupe - resultado.size());
        }

        log.info("DUX - Descarga completa: {} productos", resultado.size());
        return resultado;
    }

    /**
     * Obtiene un producto por su código.
     */
    public Item obtenerProductoPorCodigo(String codItem) {
        verificarTokens();

        String response = retryHandler.get("/items?codigoItem=" + URLEncoder.encode(codItem, StandardCharsets.UTF_8), tokens.token);

        if (response == null) {
            throw new RuntimeException("No se pudo consultar el ítem en Dux (sin respuesta)");
        }

        try {
            DuxResponse duxResponse = objectMapper.readValue(response, DuxResponse.class);
            if (duxResponse.getResults() != null) {
                // Match EXACTO por código: el endpoint de Dux puede devolver coincidencias parciales.
                return duxResponse.getResults().stream()
                        .filter(i -> codItem.equals(i.getCodItem()))
                        .findFirst().orElse(null);
            }
            return null; // no encontrado
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo parsear la respuesta de Dux: " + e.getMessage(), e);
        }
    }

    /** Diagnóstico: JSON CRUDO de la consulta por código (todos los campos, incluidos los que el modelo Item no mapea). */
    public String obtenerProductoPorCodigoRaw(String codItem) {
        verificarTokens();
        return retryHandler.get("/items?codigoItem=" + URLEncoder.encode(codItem, StandardCharsets.UTF_8), tokens.token);
    }

    // =====================================================
    // IMPORT: DUX → Local (async)
    // =====================================================

    /**
     * Inicia la importación de productos desde DUX en background.
     *
     * @return true si se inició, false si ya hay una importación en ejecución
     */
    public boolean iniciarImportacion() {
        if (!trackerImportacion.adquirir()) {
            log.warn("DUX - No se pudo iniciar importación: ya en ejecución u otro proceso DUX activo");
            return false;
        }
        resultadoImportacion = null;
        self.importarProductosDesdeDuxAsync();
        return true;
    }

    /**
     * Importa productos desde DUX de forma asincrónica.
     * Se ejecuta en un thread separado del pool async de Spring.
     */
    @Async
    public void importarProductosDesdeDuxAsync() {
        try {
            log.info("DUX - Iniciando importación de productos...");

            // Fase 1: Obtener productos de DUX API
            trackerImportacion.actualizar("Obteniendo productos de DUX...");

            List<Item> itemsDux = obtenerProductos(trackerImportacion.getFlagCancelado(), null);
            int totalDux = itemsDux.size();
            log.info("DUX - {} productos obtenidos para importar", totalDux);

            // Fase 2: Procesar items
            List<String> skusNoEncontrados = new ArrayList<>();
            List<String> errores = new ArrayList<>();
            List<Integer> productosARecalcular = new ArrayList<>();
            int productosActualizados = 0;
            int procesados = 0;

            cacheProveedores.clear();
            int proveedoresAntes = (int) proveedorRepository.count();

            for (Item item : itemsDux) {
                if (trackerImportacion.estaCancelado()) {
                    log.info("DUX - Importación cancelada. Procesados: {}/{}", procesados, totalDux);
                    break;
                }

                try {
                    String sku = item.getCodItem();
                    if (sku == null || sku.isBlank()) {
                        procesados++;
                        continue;
                    }

                    Optional<Producto> productoOpt = productoRepository.findBySku(sku.trim());
                    if (productoOpt.isEmpty()) {
                        skusNoEncontrados.add(sku);
                        procesados++;
                        continue;
                    }

                    Producto producto = productoOpt.get();
                    boolean actualizado = false;

                    BigDecimal costoAnterior = producto.getCosto();
                    BigDecimal ivaAnterior = producto.getIva();
                    Integer proveedorIdAnterior = producto.getProveedor() != null ? producto.getProveedor().getId() : null;

                    // descripcion ← item.item
                    if (item.getItem() != null && !item.getItem().isBlank()) {
                        String desc = item.getItem().trim();
                        producto.setTituloDux(desc.length() > 100 ? desc.substring(0, 100) : desc);
                        actualizado = true;
                    }

                    // costo ← item.costo
                    if (item.getCosto() != null && !item.getCosto().isBlank()) {
                        try {
                            BigDecimal costo = new BigDecimal(item.getCosto().replace(",", "."))
                                    .setScale(2, RoundingMode.HALF_UP);
                            if (costo.compareTo(COSTO_MAXIMO) > 0) {
                                errores.add("SKU " + sku + ": COSTO excede límite (" + item.getCosto() + " > 99,999,999.99)");
                            } else if (costo.compareTo(BigDecimal.ZERO) >= 0) {
                                producto.setCosto(costo);
                                actualizado = true;
                            }
                        } catch (NumberFormatException e) {
                            errores.add("SKU " + sku + ": COSTO inválido '" + item.getCosto() + "'");
                        }
                    }

                    // codExt ← item.codigoExterno
                    if (item.getCodigoExterno() != null && !item.getCodigoExterno().isBlank()) {
                        String codExt = item.getCodigoExterno().trim();
                        producto.setCodExt(codExt.length() > 45 ? codExt.substring(0, 45) : codExt);
                        actualizado = true;
                    }

                    // proveedor ← item.proveedor.proveedor
                    if (item.getProveedor() != null && item.getProveedor().getProveedor() != null
                            && !item.getProveedor().getProveedor().isBlank()) {
                        Proveedor proveedor = buscarOCrearProveedor(item.getProveedor().getProveedor().trim());
                        if (proveedor != null) {
                            producto.setProveedor(proveedor);
                            actualizado = true;
                        }
                    }

                    // iva ← item.porcIva
                    if (item.getPorcIva() != null && !item.getPorcIva().isBlank()) {
                        try {
                            BigDecimal iva = new BigDecimal(item.getPorcIva().replace(",", "."));
                            if (iva.compareTo(BigDecimal.ZERO) >= 0 && iva.compareTo(new BigDecimal("100")) <= 0) {
                                producto.setIva(iva);
                                actualizado = true;
                            }
                        } catch (NumberFormatException e) {
                            errores.add("SKU " + sku + ": IVA inválido '" + item.getPorcIva() + "'");
                        }
                    }

                    // activo ← item.habilitado ("S" → true, otro → false)
                    if (item.getHabilitado() != null) {
                        producto.setActivo("S".equalsIgnoreCase(item.getHabilitado().trim()));
                        actualizado = true;
                    }

                    if (actualizado) {
                        productoRepository.save(producto);
                        productosActualizados++;

                        boolean cambioCosto = !bigDecimalEquals(costoAnterior, producto.getCosto());
                        boolean cambioIva = !bigDecimalEquals(ivaAnterior, producto.getIva());
                        Integer proveedorIdNuevo = producto.getProveedor() != null ? producto.getProveedor().getId() : null;
                        boolean cambioProveedor = !Objects.equals(proveedorIdAnterior, proveedorIdNuevo);

                        if (cambioCosto || cambioIva || cambioProveedor) {
                            productosARecalcular.add(producto.getId());
                        }
                    }

                } catch (Exception e) {
                    String sku = item.getCodItem() != null ? item.getCodItem() : "desconocido";
                    errores.add("SKU " + sku + ": Error inesperado - " + e.getMessage());
                    log.warn("Error procesando item DUX {}: {}", sku, e.getMessage());
                }

                procesados++;
                trackerImportacion.actualizar(totalDux, procesados, productosActualizados, errores.size(),
                        String.format("Procesando %d/%d", procesados, totalDux));
            }

            // Fase 3: Recalcular precios
            int proveedoresDespues = (int) proveedorRepository.count();
            int proveedoresCreados = proveedoresDespues - proveedoresAntes;

            if (trackerImportacion.estaCancelado()) {
                // Si se canceló durante Fase 2, saltamos Fase 3: el usuario ya no quiere
                // que se gasten recursos recalculando, y los productos modificados parcialmente
                // se recalcularán cuando el siguiente costo cambie (programarRecalculoPostCommit).
                log.info("DUX - Fase 3 (recálculo de precios) saltada por cancelación previa: {} productos pendientes",
                        productosARecalcular.size());
            } else if (!productosARecalcular.isEmpty()) {
                trackerImportacion.actualizar(totalDux, procesados, productosActualizados, errores.size(),
                        String.format("Recalculando precios (%d productos)...", productosARecalcular.size()));

                log.info("DUX - Recalculando precios para {} productos...", productosARecalcular.size());
                for (Integer productoId : productosARecalcular) {
                    if (trackerImportacion.estaCancelado()) {
                        log.info("DUX - Recálculo cancelado en producto {} de {}", productoId, productosARecalcular.size());
                        break;
                    }
                    try {
                        recalculoPrecioFacade.recalcularProductoEnTodosLosCanales(productoId);
                    } catch (Exception e) {
                        log.warn("Error recalculando precios para producto {}: {}", productoId, e.getMessage());
                    }
                }
            }

            // Resultado final
            String tag = trackerImportacion.estaCancelado() ? "cancelado" : "completado";

            log.info("DUX - Importación {}. Actualizados: {}, No encontrados: {}, Proveedores creados: {}, Recalculados: {}, Errores: {}",
                    tag, productosActualizados, skusNoEncontrados.size(), proveedoresCreados,
                    productosARecalcular.size(), errores.size());

            resultadoImportacion = new ImportDuxResultDTO(
                    productosActualizados,
                    skusNoEncontrados.size(),
                    proveedoresCreados,
                    totalDux,
                    skusNoEncontrados,
                    errores);

            trackerImportacion.completar(totalDux, productosActualizados, errores.size(),
                    String.format("Proceso %s. Actualizados: %d, No encontrados: %d, Errores: %d",
                            tag, productosActualizados, skusNoEncontrados.size(), errores.size()));

        } catch (Exception e) {
            log.error("DUX - Error fatal en importación: {}", e.getMessage(), e);
            resultadoImportacion = null;
            trackerImportacion.completarConError(e.getMessage());
        } finally {
            trackerImportacion.liberar();
        }
    }

    /**
     * Cancela la importación en ejecución.
     */
    public boolean cancelarImportacion() {
        if (trackerImportacion.estaEjecutando()) {
            trackerImportacion.cancelar();
            log.info("DUX - Solicitud de cancelación de importación recibida");
            return true;
        }
        return false;
    }

    public ProcesoMasivoEstadoDTO obtenerEstadoImportacion() {
        return trackerImportacion.obtener();
    }

    public ImportDuxResultDTO obtenerResultadoImportacion() {
        return resultadoImportacion;
    }

    /**
     * Importa productos desde DUX de forma sincrónica (para uso dentro de otro proceso).
     * No adquiere lock global ni maneja estado async propio.
     *
     * @param cancelFlag  flag externo de cancelación
     * @param logCallback callback para emitir líneas de log al proceso padre
     * @return resultado de la importación
     */
    // noRollbackFor: el motor de cálculo (recalcularProductoEnTodosLosCanales) es @Transactional
    // y puede tirar NotFoundException (sin márgenes/canal) o BadRequestException (sin costo/iva).
    // El catch del loop atrapa la excepción pero sin noRollbackFor la tx outer quedaría
    // marcada rollback-only y al commit se tiraría UnexpectedRollbackException.
    @Transactional(noRollbackFor = {NotFoundException.class, BadRequestException.class})
    public ImportDuxResultDTO importarProductosSincrono(AtomicBoolean cancelFlag,
                                                        Consumer<String> logCallback,
                                                        LocalDateTime desde) {
        if (desde != null) {
            logCallback.accept("Obteniendo productos de DUX modificados desde " +
                    desde.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + "...");
        } else {
            logCallback.accept("Obteniendo todos los productos de DUX (primera ejecución)...");
        }
        List<Item> itemsDux = obtenerProductos(cancelFlag, desde);
        int totalDux = itemsDux.size();
        logCallback.accept("DUX: " + totalDux + " productos obtenidos");

        List<String> skusNoEncontrados = new ArrayList<>();
        List<String> erroresImport = new ArrayList<>();
        List<Integer> productosARecalcular = new ArrayList<>();
        int productosActualizados = 0;

        cacheProveedores.clear();
        int proveedoresAntes = (int) proveedorRepository.count();

        for (int i = 0; i < itemsDux.size(); i++) {
            if (cancelFlag.get()) break;
            Item item = itemsDux.get(i);

            try {
                String sku = item.getCodItem();
                if (sku == null || sku.isBlank()) continue;

                Optional<Producto> productoOpt = productoRepository.findBySku(sku.trim());
                if (productoOpt.isEmpty()) {
                    skusNoEncontrados.add(sku);
                    continue;
                }

                Producto producto = productoOpt.get();
                boolean actualizado = false;

                BigDecimal costoAnterior = producto.getCosto();
                BigDecimal ivaAnterior = producto.getIva();
                Integer proveedorIdAnterior = producto.getProveedor() != null ? producto.getProveedor().getId() : null;

                if (item.getItem() != null && !item.getItem().isBlank()) {
                    String desc = item.getItem().trim();
                    producto.setTituloDux(desc.length() > 100 ? desc.substring(0, 100) : desc);
                    actualizado = true;
                }

                if (item.getCosto() != null && !item.getCosto().isBlank()) {
                    try {
                        BigDecimal costo = new BigDecimal(item.getCosto().replace(",", "."))
                                .setScale(2, RoundingMode.HALF_UP);
                        if (costo.compareTo(COSTO_MAXIMO) > 0) {
                            erroresImport.add("SKU " + sku + ": COSTO excede límite");
                        } else if (costo.compareTo(BigDecimal.ZERO) >= 0) {
                            producto.setCosto(costo);
                            actualizado = true;
                        }
                    } catch (NumberFormatException e) {
                        erroresImport.add("SKU " + sku + ": COSTO inválido '" + item.getCosto() + "'");
                    }
                }

                if (item.getCodigoExterno() != null && !item.getCodigoExterno().isBlank()) {
                    String codExt = item.getCodigoExterno().trim();
                    producto.setCodExt(codExt.length() > 45 ? codExt.substring(0, 45) : codExt);
                    actualizado = true;
                }

                if (item.getProveedor() != null && item.getProveedor().getProveedor() != null
                        && !item.getProveedor().getProveedor().isBlank()) {
                    Proveedor proveedor = buscarOCrearProveedor(item.getProveedor().getProveedor().trim());
                    if (proveedor != null) {
                        producto.setProveedor(proveedor);
                        actualizado = true;
                    }
                }

                if (item.getPorcIva() != null && !item.getPorcIva().isBlank()) {
                    try {
                        BigDecimal iva = new BigDecimal(item.getPorcIva().replace(",", "."));
                        if (iva.compareTo(BigDecimal.ZERO) >= 0 && iva.compareTo(new BigDecimal("100")) <= 0) {
                            producto.setIva(iva);
                            actualizado = true;
                        }
                    } catch (NumberFormatException e) {
                        erroresImport.add("SKU " + sku + ": IVA inválido '" + item.getPorcIva() + "'");
                    }
                }

                if (item.getHabilitado() != null) {
                    producto.setActivo("S".equalsIgnoreCase(item.getHabilitado().trim()));
                    actualizado = true;
                }

                if (actualizado) {
                    productoRepository.save(producto);
                    productosActualizados++;

                    boolean cambioCosto = !bigDecimalEquals(costoAnterior, producto.getCosto());
                    boolean cambioIva = !bigDecimalEquals(ivaAnterior, producto.getIva());
                    Integer proveedorIdNuevo = producto.getProveedor() != null ? producto.getProveedor().getId() : null;
                    boolean cambioProveedor = !Objects.equals(proveedorIdAnterior, proveedorIdNuevo);

                    if (cambioCosto || cambioIva || cambioProveedor) {
                        productosARecalcular.add(producto.getId());
                    }
                }
            } catch (Exception e) {
                String sku = item.getCodItem() != null ? item.getCodItem() : "desconocido";
                erroresImport.add("SKU " + sku + ": " + e.getMessage());
            }

            if ((i + 1) % 100 == 0 || i + 1 == totalDux) {
                logCallback.accept("DUX importación: " + (i + 1) + "/" + totalDux);
            }
        }

        // Recalcular precios
        if (!productosARecalcular.isEmpty()) {
            logCallback.accept("Recalculando precios para " + productosARecalcular.size() + " productos...");
            for (Integer productoId : productosARecalcular) {
                try {
                    recalculoPrecioFacade.recalcularProductoEnTodosLosCanales(productoId);
                } catch (Exception e) {
                    log.warn("Error recalculando precios para producto {}: {}", productoId, e.getMessage());
                }
            }
        }

        int proveedoresCreados = (int) proveedorRepository.count() - proveedoresAntes;

        return new ImportDuxResultDTO(
                productosActualizados,
                skusNoEncontrados.size(),
                proveedoresCreados,
                totalDux,
                skusNoEncontrados,
                erroresImport);
    }

    // =====================================================
    // REPOSICIÓN: Facturas, Pedidos pendientes, Stock
    // =====================================================

    /**
     * Obtiene facturas de DUX en un rango de fechas con paginación automática.
     *
     * @param desde      Fecha inicio (yyyy-MM-dd)
     * @param hasta      Fecha fin (yyyy-MM-dd)
     * @param idEmpresa  ID de empresa DUX
     * @param idSucursal ID de sucursal DUX
     * @param cancelFlag Flag para cancelar la operación
     * @return Lista de facturas con sus detalles
     */
    public List<FacturaDux> obtenerFacturas(
            String desde, String hasta, int idEmpresa, int idSucursal, AtomicBoolean cancelFlag,
            BiConsumer<Integer, Integer> onProgress) {
        verificarTokens();

        List<FacturaDux> allFacturas = new ArrayList<>();
        int offset = 0;
        int limit = properties.itemsPerPage();
        int intentosVacios = 0;

        while (true) {
            if (cancelFlag != null && cancelFlag.get()) {
                log.info("DUX - Obtención de facturas cancelada. Obtenidas: {}", allFacturas.size());
                break;
            }

            String uri = String.format("/facturas?fechaDesde=%s&fechaHasta=%s&idEmpresa=%d&idSucursal=%d&limit=%d&offset=%d",
                    desde, hasta, idEmpresa, idSucursal, limit, offset);

            String response = retryHandler.get(uri, tokens.token);
            if (response == null) {
                log.error("DUX - Error obteniendo facturas en offset {}", offset);
                break;
            }

            try {
                JsonNode root = objectMapper.readTree(response);
                tools.jackson.databind.JsonNode results = root.isArray() ? root : root.get("results");

                if (results == null || !results.isArray() || results.isEmpty()) {
                    intentosVacios++;
                    if (intentosVacios >= MAX_INTENTOS_VACIOS) break;
                    offset += limit;
                    continue;
                }

                intentosVacios = 0;
                int count = 0;
                for (tools.jackson.databind.JsonNode node : results) {
                    FacturaDux factura =
                            objectMapper.treeToValue(node, FacturaDux.class);
                    allFacturas.add(factura);
                    count++;
                }

                if (onProgress != null) {
                    onProgress.accept(offset / limit + 1, 0);
                }

                if (count < limit) break; // Última página
                offset += limit;

            } catch (Exception e) {
                log.error("DUX - Error parseando facturas: {}", e.getMessage());
                break;
            }
        }

        log.info("DUX - {} facturas obtenidas ({} a {})", allFacturas.size(), desde, hasta);
        return allFacturas;
    }

    /**
     * Obtiene comprobantes de venta de DUX con filtro de cobro pendiente y paginación automática.
     *
     * @param desde      Fecha inicio (yyyy-MM-dd)
     * @param hasta      Fecha fin (yyyy-MM-dd)
     * @param idEmpresa  ID de empresa DUX
     * @param idSucursal ID de sucursal DUX
     * @param conCobro   true = cobrados, false = pendientes de cobro, null = todos
     * @return Lista de comprobantes
     */
    public List<FacturaDux> obtenerComprobantes(
            String desde, String hasta, int idEmpresa, int idSucursal, Boolean conCobro,
            String cliente, Boolean anuladas) {
        verificarTokens();

        List<FacturaDux> allComprobantes = new ArrayList<>();
        int offset = 0;
        int limit = properties.itemsPerPage();
        int intentosVacios = 0;

        while (true) {
            StringBuilder uri = new StringBuilder(String.format(
                    "/facturas?fechaDesde=%s&fechaHasta=%s&idEmpresa=%d&idSucursal=%d&limit=%d&offset=%d",
                    desde, hasta, idEmpresa, idSucursal, limit, offset));

            if (conCobro != null) {
                uri.append("&conCobro=").append(conCobro);
            }
            if (cliente != null && !cliente.isBlank()) {
                uri.append("&cliente=").append(URLEncoder.encode(cliente, StandardCharsets.UTF_8));
            }
            if (anuladas != null) {
                uri.append("&anuladas=").append(anuladas);
            }

            String response = retryHandler.get(uri.toString(), tokens.token);
            if (response == null) {
                log.error("DUX - Error obteniendo comprobantes en offset {}", offset);
                break;
            }

            try {
                JsonNode root = objectMapper.readTree(response);
                JsonNode results = root.isArray() ? root : root.get("results");

                if (results == null || !results.isArray() || results.isEmpty()) {
                    intentosVacios++;
                    if (intentosVacios >= MAX_INTENTOS_VACIOS) break;
                    offset += limit;
                    continue;
                }

                intentosVacios = 0;
                int count = 0;
                for (JsonNode node : results) {
                    FacturaDux comprobante = objectMapper.treeToValue(node, FacturaDux.class);
                    allComprobantes.add(comprobante);
                    count++;
                }

                if (count < limit) break;
                offset += limit;

            } catch (Exception e) {
                log.error("DUX - Error parseando comprobantes: {}", e.getMessage());
                break;
            }
        }

        log.info("DUX - {} comprobantes obtenidos ({} a {}, conCobro={})", allComprobantes.size(), desde, hasta, conCobro);
        return allComprobantes;
    }

    /**
     * Obtiene el mapa id → nombre de todos los personales de una empresa en DUX.
     */
    public Map<Long, String> obtenerPersonalesMap(int idEmpresa) {
        verificarTokens();
        Map<Long, String> map = new HashMap<>();
        try {
            String response = retryHandler.get("/personales", tokens.token);
            if (response == null) return map;

            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.isArray() ? root : root.get("results");
            if (results != null && results.isArray()) {
                for (JsonNode node : results) {
                    Long id = node.has("id") ? node.get("id").asLong() : null;
                    String nombre = node.has("nombre") ? node.get("nombre").asString(null) : null;
                    String apellido = node.has("apellido_razon_social") ? node.get("apellido_razon_social").asString(null) : null;
                    if (id != null) {
                        String full = Stream.of(apellido, nombre)
                                .filter(s -> s != null && !s.isBlank())
                                .collect(Collectors.joining(", "));
                        map.put(id, full.isBlank() ? "ID " + id : full);
                    }
                }
            }
            log.info("DUX - Personales cargados: {}", map.size());
        } catch (Exception e) {
            log.warn("DUX - Error obteniendo personales: {}", e.getMessage());
        }
        return map;
    }

    // =====================================================
    // DEUDAS CLIENTES - ASYNC
    // =====================================================

    public boolean iniciarConsultaDeudas(String fechaDesde, String fechaHasta, int idEmpresa,
                                         List<Integer> idsSucursal, Boolean conCobro,
                                         String cliente, Boolean anuladas) {
        if (!trackerDeudas.adquirir()) {
            log.warn("DUX - No se pudo iniciar consulta de deudas: ya en ejecución u otro proceso DUX activo");
            return false;
        }
        resultadoDeudas = null;
        paramsDeudasActual = new DeudasConsultaParams(fechaDesde, fechaHasta, idEmpresa, idsSucursal, conCobro, cliente, anuladas);
        self.consultaDeudasAsync();
        return true;
    }

    @Async
    public void consultaDeudasAsync() {
        DeudasConsultaParams params = paramsDeudasActual;
        try {
            List<FacturaDux> todosComprobantes = new ArrayList<>();
            int sucursalIndex = 0;

            for (int idSucursal : params.idsSucursal()) {
                if (trackerDeudas.estaCancelado()) break;

                sucursalIndex++;
                trackerDeudas.actualizar(params.idsSucursal().size(), sucursalIndex - 1,
                        sucursalIndex - 1, 0,
                        String.format("Consultando sucursal %d de %d (%d comprobantes obtenidos)...",
                                sucursalIndex, params.idsSucursal().size(), todosComprobantes.size()));

                List<FacturaDux> comprobantes = obtenerComprobantes(
                        params.fechaDesde(), params.fechaHasta(),
                        params.idEmpresa(), idSucursal, params.conCobro(),
                        params.cliente(), params.anuladas());
                todosComprobantes.addAll(comprobantes);
            }

            if (trackerDeudas.estaCancelado()) {
                trackerDeudas.completar(0, 0, 0, "Consulta de deudas cancelada.");
                return;
            }

            // Obtener mapa de personales para resolver nombres de vendedores
            Map<Long, String> personalesMap = obtenerPersonalesMap(params.idEmpresa());

            // Mapear comprobantes y descartar los completamente saldados (cobrado >= total)
            List<DeudaClienteDuxDTO> todos = todosComprobantes.stream()
                    .map(f -> DeudaClienteDuxDTO.fromFacturaDux(f, personalesMap))
                    .filter(d -> d.saldo().compareTo(BigDecimal.ZERO) > 0)
                    .toList();

            long facturas = todos.stream().filter(d -> d.tipoComp() != null && (
                    d.tipoComp().equals("FACTURA") || d.tipoComp().equals("FACTURA_FCE_MIPYMES") ||
                            d.tipoComp().equals("COMPROBANTE_VENTA"))).count();

            long notasCredito = todos.stream().filter(d -> d.tipoComp() != null && (
                    d.tipoComp().equals("NOTA_CREDITO") || d.tipoComp().equals("NOTA_CREDITO_FCE_MI_PYMES"))).count();

            long notasDebito = todos.stream().filter(d -> d.tipoComp() != null && (
                    d.tipoComp().equals("NOTA_DEBITO") || d.tipoComp().equals("NOTA_DEBITO_FCE_MI_PYMES"))).count();

            resultadoDeudas = Map.of(
                    "total", todos.size(),
                    "comprobantes", todos,
                    "resumen", Map.of(
                            "facturas", facturas,
                            "notasCredito", notasCredito,
                            "notasDebito", notasDebito
                    )
            );

            trackerDeudas.completar(todos.size(), todos.size(), 0,
                    String.format("Consulta completada. %d comprobantes obtenidos.", todos.size()));

        } catch (Exception e) {
            log.error("DUX - Error consultando deudas: {}", e.getMessage(), e);
            resultadoDeudas = null;
            trackerDeudas.completarConError(e.getMessage());
        } finally {
            trackerDeudas.liberar();
        }
    }

    public boolean cancelarConsultaDeudas() {
        if (trackerDeudas.estaEjecutando()) {
            trackerDeudas.cancelar();
            log.info("DUX - Solicitud de cancelación de consulta de deudas recibida");
            return true;
        }
        return false;
    }

    public ProcesoMasivoEstadoDTO obtenerEstadoDeudas() {
        return trackerDeudas.obtener();
    }

    public Map<String, Object> obtenerResultadoDeudas() {
        return resultadoDeudas;
    }

    /**
     * Obtiene pedidos pendientes de DUX (estadoRemito=PENDIENTE) con paginación automática.
     *
     * @param desde      Fecha inicio (yyyy-MM-dd)
     * @param hasta      Fecha fin (yyyy-MM-dd)
     * @param idEmpresa  ID de empresa DUX
     * @param idSucursal ID de sucursal DUX
     * @param cancelFlag Flag para cancelar la operación
     * @return Lista de pedidos pendientes con sus detalles
     */
    public List<PedidoDux> obtenerPedidosPendientes(
            String desde, String hasta, int idEmpresa, int idSucursal, AtomicBoolean cancelFlag,
            BiConsumer<Integer, Integer> onProgress) {
        verificarTokens();

        List<PedidoDux> allPedidos = new ArrayList<>();
        int offset = 0;
        int limit = properties.itemsPerPage();
        int intentosVacios = 0;

        while (true) {
            if (cancelFlag != null && cancelFlag.get()) {
                log.info("DUX - Obtención de pedidos cancelada. Obtenidos: {}", allPedidos.size());
                break;
            }

            String uri = String.format("/pedidos?fechaDesde=%s&fechaHasta=%s&idEmpresa=%d&idSucursal=%d&estadoRemito=PENDIENTE&limit=%d&offset=%d",
                    desde, hasta, idEmpresa, idSucursal, limit, offset);

            String response = retryHandler.get(uri, tokens.token);
            if (response == null) {
                log.error("DUX - Error obteniendo pedidos en offset {}", offset);
                break;
            }

            try {
                JsonNode root = objectMapper.readTree(response);
                JsonNode results = root.isArray() ? root : root.get("results");

                if (results == null || !results.isArray() || results.isEmpty()) {
                    intentosVacios++;
                    if (intentosVacios >= MAX_INTENTOS_VACIOS) break;
                    offset += limit;
                    continue;
                }

                intentosVacios = 0;
                int count = 0;
                for (JsonNode node : results) {
                    PedidoDux pedido =
                            objectMapper.treeToValue(node, PedidoDux.class);
                    allPedidos.add(pedido);
                    count++;
                }

                if (onProgress != null) {
                    onProgress.accept(offset / limit + 1, 0);
                }

                if (count < limit) break;
                offset += limit;

            } catch (Exception e) {
                log.error("DUX - Error parseando pedidos: {}", e.getMessage());
                break;
            }
        }

        log.info("DUX - {} pedidos pendientes obtenidos ({} a {})", allPedidos.size(), desde, hasta);
        return allPedidos;
    }

    /**
     * Obtiene el stock total por SKU sumando stock_disponible de todos los depósitos.
     * Reutiliza obtenerProductos() que ya trae stock por depósito.
     *
     * @param cancelFlag Flag para cancelar la operación
     * @return Mapa de SKU → stock total disponible
     */
    public Map<String, Integer> obtenerStockMap(AtomicBoolean cancelFlag) {
        return obtenerStockMap(cancelFlag, null, null);
    }

    public Map<String, Integer> obtenerStockMap(AtomicBoolean cancelFlag, LocalDateTime desde) {
        return obtenerStockMap(cancelFlag, desde, null);
    }

    /**
     * Obtiene stock por SKU, opcionalmente filtrado por fecha de movimiento.
     *
     * @param cancelFlag Flag para cancelar
     * @param desde      Solo traer items con movimientos desde esta fecha (null = todos)
     * @param onProgress Callback de progreso por página (páginaActual, totalPáginas)
     */
    public Map<String, Integer> obtenerStockMap(AtomicBoolean cancelFlag, LocalDateTime desde,
                                                BiConsumer<Integer, Integer> onProgress) {
        List<Item> items = obtenerProductos(cancelFlag, desde, onProgress);
        Map<String, Integer> stockMap = new HashMap<>();

        for (Item item : items) {
            if (item.getCodItem() == null || item.getCodItem().isBlank()) continue;
            stockMap.put(item.getCodItem().trim(), sumarStockTotal(item.getStock()));
        }

        log.info("DUX - Stock obtenido para {} SKUs", stockMap.size());
        return stockMap;
    }

    /**
     * Suma stockDisponible de todas las entradas de stock preservando decimales
     * durante la suma y redondeando solo al final (HALF_UP). Evita la pérdida
     * silenciosa que ocurre al truncar cada valor individual antes de sumar
     * (ej: 3 depósitos con 0,4 daban 0 en vez de 1).
     */
    private int sumarStockTotal(List<Stock> stocks) {
        if (stocks == null) return 0;
        BigDecimal total = BigDecimal.ZERO;
        for (Stock stock : stocks) {
            String valor = stock.getStockDisponible();
            if (valor == null || valor.isBlank()) continue;
            try {
                total = total.add(new BigDecimal(valor.replace(",", ".")));
            } catch (NumberFormatException e) {
                // Ignorar valores no numéricos
            }
        }
        return total.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /**
     * Obtiene stock y costo por SKU desde DUX.
     * Reutiliza obtenerProductos() y extrae ambos campos de cada item.
     *
     * @param cancelFlag Flag para cancelar
     * @param desde      Solo traer items modificados desde esta fecha (null = todos)
     * @param onProgress Callback de progreso por página
     */
    public Map<String, DuxItemData> obtenerItemDataMap(AtomicBoolean cancelFlag, LocalDateTime desde,
                                                       BiConsumer<Integer, Integer> onProgress) {
        List<Item> items = obtenerProductos(cancelFlag, desde, onProgress);
        Map<String, DuxItemData> dataMap = new HashMap<>();

        for (Item item : items) {
            if (item.getCodItem() == null || item.getCodItem().isBlank()) continue;

            int stockTotal = sumarStockTotal(item.getStock());

            BigDecimal costo = null;
            if (item.getCosto() != null && !item.getCosto().isBlank()) {
                try {
                    costo = new BigDecimal(item.getCosto().replace(",", "."))
                            .setScale(2, RoundingMode.HALF_UP);
                    if (costo.compareTo(BigDecimal.ZERO) < 0 || costo.compareTo(COSTO_MAXIMO) > 0) {
                        costo = null;
                    }
                } catch (NumberFormatException e) {
                    // Ignorar costos no numéricos
                }
            }

            dataMap.put(item.getCodItem().trim(), new DuxItemData(stockTotal, costo));
        }

        log.info("DUX - Datos obtenidos para {} SKUs", dataMap.size());
        return dataMap;
    }

    public record DuxItemData(int stock, BigDecimal costo) {
    }

    // =====================================================
    // EXPORT: Local → DUX
    // =====================================================

    /**
     * Exporta productos locales a DUX usando el endpoint nuevoItem.
     * Encola el proceso y devuelve el ID de proceso DUX sin esperar confirmación.
     *
     * @param skus lista de SKUs a exportar, o null para exportar todos
     * @return resultado con productosEnviados, idProceso y errores
     */
    public ExportDuxResultDTO exportarProductosADux(List<String> skus) {
        log.info("Iniciando exportación de productos a DUX...");
        List<String> errores = new ArrayList<>();

        List<Map<String, Object>> productosJson = self.cargarYArmarItemsDux(skus, errores);

        if (productosJson.isEmpty()) {
            log.warn("DUX Export - No hay productos válidos para enviar");
            return new ExportDuxResultDTO(0, 0, List.copyOf(errores));
        }

        verificarTokens();

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(Map.of("productos", productosJson));
        } catch (Exception e) {
            log.error("DUX Export - Error serializando productos", e);
            errores.add("Error preparando datos para DUX: " + e.getMessage());
            return new ExportDuxResultDTO(0, 0, List.copyOf(errores));
        }

        String response = retryHandler.postJson("/item/nuevoItem", tokens.token, jsonBody);

        int idProceso = extraerIdProceso(response);
        if (idProceso == 0) {
            errores.add("Dux no devolvió ID de proceso");
            return new ExportDuxResultDTO(0, 0, List.copyOf(errores));
        }

        int productosEnviados = productosJson.size();
        return new ExportDuxResultDTO(productosEnviados, idProceso, List.copyOf(errores));
    }

    /**
     * Exporta productos locales a DUX y confirma el resultado con polling.
     * Usado por el formulario de 1 producto (tope 30s); no apto para exportaciones masivas.
     *
     * @param skus lista de SKUs a exportar, o null para exportar todos
     * @return resultado con cantidad creada, errores y advertencias
     */
    public ExportCanalResultDTO exportarProductosADuxConfirmado(List<String> skus) {
        ExportDuxResultDTO encolado = exportarProductosADux(skus);

        if (encolado.idProceso() == 0) {
            return new ExportCanalResultDTO(0, List.of(), List.of(), List.copyOf(encolado.errores()), List.of());
        }

        int idProceso = encolado.idProceso();
        final long TIMEOUT_MS = 30_000;
        final long INTERVALO_MS = 5_000;
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                EstadoProceso estado = parsearEstadoProceso(obtenerEstadoProceso(idProceso), objectMapper);
                if (estado.finalizado()) {
                    log.info("DUX Export - proceso {} FINALIZADO ({} errores)", idProceso, estado.errores().size());
                    return mapearResultadoProceso(estado, encolado.productosEnviados());
                }
            } catch (Exception ex) {
                log.warn("DUX Export - error consultando estado proceso {}: {}", idProceso, ex.getMessage());
                return resultadoSinConfirmar(idProceso);
            }
            try {
                Thread.sleep(INTERVALO_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.warn("DUX Export - proceso {} no finalizó en {}ms", idProceso, TIMEOUT_MS);
        return resultadoSinConfirmar(idProceso);
    }

    /**
     * Extrae el ID de proceso del response de DUX (busca "ID de proceso: &lt;número&gt;").
     *
     * @param response respuesta cruda de DUX, puede ser null
     * @return idProceso, o 0 si no se encontró
     */
    int extraerIdProceso(String response) {
        if (response == null) return 0;
        Matcher matcher = Pattern.compile("ID de proceso:\\s*(\\d+)").matcher(response);
        if (matcher.find()) {
            int idProceso = Integer.parseInt(matcher.group(1));
            log.info("DUX Export - Proceso iniciado con ID: {}", idProceso);
            return idProceso;
        }
        log.warn("DUX Export - No se encontró ID de proceso en respuesta: {}", response);
        return 0;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> cargarYArmarItemsDux(List<String> skus, List<String> errores) {
        List<Producto> productos = cargarProductosParaExportacion(skus, errores);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Producto producto : productos) {
            try {
                items.add(duxItemBuilder.construir(producto));
            } catch (Exception e) {
                errores.add("SKU " + producto.getSku() + ": Error mapeando - " + e.getMessage());
                log.warn("Error mapeando producto {} para DUX: {}", producto.getSku(), e.getMessage());
            }
        }
        return items;
    }

    private List<Producto> cargarProductosParaExportacion(List<String> skus, List<String> errores) {
        if (skus != null && !skus.isEmpty()) {
            List<String> skusNormalizados = skus.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(sku -> !sku.isBlank())
                    .distinct()
                    .toList();

            Map<String, Producto> productosPorSku = productoRepository.findBySkuIn(skusNormalizados).stream()
                    .filter(producto -> producto.getSku() != null)
                    .collect(Collectors.toMap(Producto::getSku, producto -> producto, (a, b) -> a));

            List<Producto> productos = new ArrayList<>();
            for (String sku : skusNormalizados) {
                Producto producto = productosPorSku.get(sku);
                if (producto != null) {
                    productos.add(producto);
                } else {
                    errores.add("SKU no encontrado: " + sku);
                }
            }
            return productos;
        }

        int pageNumber = 0;
        int pageSize = 500;
        List<Producto> productos = new ArrayList<>();
        Page<Producto> page;

        do {
            page = productoRepository.findAll(PageRequest.of(pageNumber, pageSize, Sort.by("id").ascending()));
            productos.addAll(page.getContent());
            pageNumber++;
        } while (page.hasNext());

        return productos;
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private Proveedor buscarOCrearProveedor(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            return null;
        }
        String nombreNormalizado = nombre.trim();
        String key = nombreNormalizado.toUpperCase();
        return cacheProveedores.computeIfAbsent(key, k ->
                proveedorRepository.findByNombreIgnoreCase(nombreNormalizado)
                        .orElseGet(() -> {
                            Proveedor nuevo = new Proveedor();
                            nuevo.setNombre(nombreNormalizado);
                            String apodo = nombreNormalizado.length() > 50
                                    ? nombreNormalizado.substring(0, 50)
                                    : nombreNormalizado;
                            nuevo.setApodo(apodo);
                            return proveedorRepository.save(nuevo);
                        })
        );
    }

    private boolean bigDecimalEquals(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.compareTo(b) == 0;
    }

    // =====================================================
    // EMPRESAS Y SUCURSALES (utilidad para config)
    // =====================================================

    /**
     * Obtiene las empresas disponibles en DUX.
     * Útil para conocer el idEmpresa a configurar en reposicion_config.
     */
    public JsonNode obtenerEmpresas() {
        verificarTokens();
        String response = retryHandler.get("/empresas", tokens.token);
        return objectMapper.readTree(response);
    }

    /**
     * Obtiene las sucursales de una empresa en DUX.
     * Útil para conocer el idSucursal a configurar en reposicion_config.
     */
    public JsonNode obtenerSucursales(int idEmpresa) {
        verificarTokens();
        String response = retryHandler.get("/sucursales?idEmpresa=" + idEmpresa, tokens.token);
        return objectMapper.readTree(response);
    }

    // =====================================================
    // RUBROS Y SUBRUBROS (para sincronizar id_dux de clasificaciones)
    // =====================================================

    private static final int SYNC_MAX_PAGINAS = 200;
    private static final int SYNC_MAX_ITEMS = 10000;

    /**
     * Obtiene todos los rubros de DUX (GET /rubros) con paginación automática.
     */
    public java.util.List<ar.com.leo.super_master_backend.apis.dux.dto.DuxRubro> obtenerRubros() {
        verificarTokens();

        List<ar.com.leo.super_master_backend.apis.dux.dto.DuxRubro> acumulado = new ArrayList<>();
        int offset = 0;
        int limit = 50;
        int paginas = 0;

        while (paginas < SYNC_MAX_PAGINAS && acumulado.size() < SYNC_MAX_ITEMS) {
            String response = retryHandler.get("/rubros?offset=" + offset + "&limit=" + limit, tokens.token);
            if (response == null) {
                log.error("DUX - Error obteniendo rubros en offset {}", offset);
                break;
            }

            ar.com.leo.super_master_backend.apis.dux.dto.DuxRubro[] pagina;
            try {
                pagina = objectMapper.readValue(response, ar.com.leo.super_master_backend.apis.dux.dto.DuxRubro[].class);
            } catch (Exception e) {
                log.error("DUX - Error parseando rubros en offset {}: {}", offset, e.getMessage());
                break;
            }

            if (pagina == null || pagina.length == 0) break;
            acumulado.addAll(Arrays.asList(pagina));
            paginas++;

            if (pagina.length < limit) break; // última página
            offset += limit;
        }

        log.info("DUX - {} rubros obtenidos", acumulado.size());
        return acumulado;
    }

    /**
     * Obtiene todos los subrubros de DUX (GET /subrubros) con paginación automática.
     */
    public java.util.List<ar.com.leo.super_master_backend.apis.dux.dto.DuxSubrubro> obtenerSubrubros() {
        verificarTokens();

        List<ar.com.leo.super_master_backend.apis.dux.dto.DuxSubrubro> acumulado = new ArrayList<>();
        int offset = 0;
        int limit = 50;
        int paginas = 0;

        while (paginas < SYNC_MAX_PAGINAS && acumulado.size() < SYNC_MAX_ITEMS) {
            String response = retryHandler.get("/subrubros?offset=" + offset + "&limit=" + limit, tokens.token);
            if (response == null) {
                log.error("DUX - Error obteniendo subrubros en offset {}", offset);
                break;
            }

            ar.com.leo.super_master_backend.apis.dux.dto.DuxSubrubro[] pagina;
            try {
                pagina = objectMapper.readValue(response, ar.com.leo.super_master_backend.apis.dux.dto.DuxSubrubro[].class);
            } catch (Exception e) {
                log.error("DUX - Error parseando subrubros en offset {}: {}", offset, e.getMessage());
                break;
            }

            if (pagina == null || pagina.length == 0) break;
            acumulado.addAll(Arrays.asList(pagina));
            paginas++;

            if (pagina.length < limit) break; // última página
            offset += limit;
        }

        log.info("DUX - {} subrubros obtenidos", acumulado.size());
        return acumulado;
    }

    // =====================================================
    // TOKENS
    // =====================================================

    private void verificarTokens() {
        if (tokens == null) {
            cargarTokens();
            if (tokens == null) {
                throw new ServiceNotConfiguredException("DUX",
                        "No hay tokens disponibles. Verifique el archivo dux_tokens.json");
            }
        }
    }

    private void cargarTokens() {
        try {
            File file = Paths.get(secretsDir).resolve("dux_tokens.json").toFile();
            if (file.exists()) {
                tokens = objectMapper.readValue(file, TokensDux.class);
                log.info("DUX - Tokens cargados desde {}", file.getAbsolutePath());
            } else {
                log.warn("DUX - Archivo de tokens no encontrado: {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("DUX - Error cargando tokens: {}", e.getMessage());
        }
    }

    /**
     * Verifica si el servicio está configurado.
     */
    public boolean isConfigured() {
        return tokens != null;
    }

    // =====================================================
    // DTOs INTERNOS
    // =====================================================

    /**
     * Datos de precio para actualizar en DUX.
     *
     * @param tipo   Tipo de producto: "SIMPLE" o "COMBO". <b>OBLIGATORIO</b> - si no se envía
     *               correctamente, DUX desconfigura el producto y puede perder componentes
     * @param precio Precio del producto
     */
    public record ProductoPrecioData(String tipo, double precio) {
    }
}

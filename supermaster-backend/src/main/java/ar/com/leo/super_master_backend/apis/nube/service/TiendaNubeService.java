package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.apis.nube.NubeRetryHandler;
import ar.com.leo.super_master_backend.apis.nube.config.NubeProperties;
import ar.com.leo.super_master_backend.apis.nube.dto.StockNubeDTO;
import ar.com.leo.super_master_backend.apis.nube.dto.VentaNubeDTO;
import ar.com.leo.super_master_backend.apis.nube.model.NubeCredentials;
import ar.com.leo.super_master_backend.apis.nube.model.NubeCredentials.StoreCredentials;
import ar.com.leo.super_master_backend.dominio.common.exception.ServiceNotConfiguredException;
import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;

import java.io.File;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TiendaNubeService {

    public static final String STORE_HOGAR = "KT HOGAR";
    public static final String STORE_GASTRO = "KT GASTRO";
    private static final long BASE_WAIT_MS = 2000L;
    private static final Set<String> EXT_NUBE = Set.of("gif", "jpg", "jpeg", "png", "webp");

    private final RestClient restClient;
    private final NubeProperties properties;
    private final ObjectMapper objectMapper;
    private final ImagenService imagenService;

    @org.springframework.beans.factory.annotation.Value("${app.secrets-dir}")
    private String secretsDir;

    private NubeRetryHandler retryHandler;
    private NubeCredentials credentials;

    public TiendaNubeService(RestClient nubeRestClient, NubeProperties properties, ObjectMapper objectMapper,
                             ImagenService imagenService) {
        this.restClient = nubeRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.imagenService = imagenService;
    }

    @PostConstruct
    public void init() {
        this.retryHandler = new NubeRetryHandler(
                restClient,
                BASE_WAIT_MS,
                properties.rateLimitPerSecond()
        );
        cargarCredenciales();
    }

    // =====================================================
    // VENTAS
    // =====================================================

    public List<VentaNubeDTO> obtenerVentasHogar() {
        StoreCredentials store = getStore(STORE_HOGAR);
        if (store == null) {
            log.warn("NUBE - Credenciales de {} no disponibles.", STORE_HOGAR);
            return List.of();
        }
        return obtenerVentas(store, STORE_HOGAR);
    }

    public List<VentaNubeDTO> obtenerVentasGastro() {
        StoreCredentials store = getStore(STORE_GASTRO);
        if (store == null) {
            log.warn("NUBE - Credenciales de {} no disponibles.", STORE_GASTRO);
            return List.of();
        }
        return obtenerVentas(store, STORE_GASTRO);
    }

    public List<VentaNubeDTO> obtenerTodasLasVentas() {
        List<VentaNubeDTO> ventas = new ArrayList<>();
        ventas.addAll(obtenerVentasHogar());
        ventas.addAll(obtenerVentasGastro());
        return ventas;
    }

    /**
     * Obtiene todas las ventas pagadas, abiertas y sin empaquetar de una tienda Nube.
     * Filtra client-side por fulfillment_orders con status UNPACKED.
     * Usa el header Link para paginación (recomendado por la API de Tiendanube).
     */
    private List<VentaNubeDTO> obtenerVentas(StoreCredentials store, String label) {
        verificarCredenciales();

        List<VentaNubeDTO> ventas = new ArrayList<>();
        String uri = String.format(
                "/%s/orders?payment_status=paid&shipping_status=unpacked&status=open&aggregates=fulfillment_orders&per_page=200",
                store.getStoreId());

        while (uri != null) {
            NubeRetryHandler.HttpResponse httpResponse;
            try {
                httpResponse = retryHandler.getWithHeaders(uri, store.getAccessToken());
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 404 && e.getResponseBodyAsString().contains("Last page is 0")) {
                    break;
                }
                log.warn("NUBE ({}) - Error al obtener órdenes: {}", label, e.getMessage());
                break;
            }

            if (httpResponse.body() == null) {
                log.warn("NUBE ({}) - Respuesta nula al obtener órdenes", label);
                break;
            }

            JsonNode ordersArray = objectMapper.readTree(httpResponse.body());
            if (!ordersArray.isArray() || ordersArray.isEmpty()) {
                break;
            }

            for (JsonNode order : ordersArray) {
                long orderId = order.path("id").asLong(0);

                if (!tieneFulfillmentUnpacked(order)) continue;

                if (esPickup(order) && tieneNota(order)) {
                    log.info("NUBE ({}) - Omitida orden pickup con nota: {}", label, orderId);
                    continue;
                }

                JsonNode products = order.path("products");
                if (!products.isArray()) continue;

                for (JsonNode product : products) {
                    String sku = product.path("sku").asString("");
                    double quantity = product.path("quantity").asDouble(0);
                    String productName = product.path("name").asString("");

                    if (quantity <= 0) {
                        log.warn("NUBE ({}) - Producto con cantidad inválida en orden {}: {}", label, orderId, sku);
                        String errorSku = sku.isBlank() ? productName : sku;
                        ventas.add(new VentaNubeDTO("CANT INVALIDA: " + errorSku, quantity, label));
                        continue;
                    }

                    if (sku.isBlank()) {
                        log.warn("NUBE ({}) - Producto sin SKU en orden {}: {}", label, orderId, productName);
                        ventas.add(new VentaNubeDTO("SIN SKU: " + productName, quantity, label));
                        continue;
                    }

                    ventas.add(new VentaNubeDTO(sku, quantity, label));
                }
            }

            // Siguiente página usando el header Link (recomendado por Tiendanube)
            uri = parseLinkNext(httpResponse.headers());
        }

        log.info("NUBE ({}) - Ventas obtenidas: {}", label, ventas.size());
        return ventas;
    }

    // =====================================================
    // STOCK
    // =====================================================

    /**
     * Obtiene el stock de un producto por SKU buscando en todas las tiendas.
     *
     * @return StockNubeDTO con el stock encontrado, o null si no se encuentra
     */
    public StockNubeDTO obtenerStockPorSku(String sku) {
        verificarCredenciales();

        for (var entry : credentials.getStores().entrySet()) {
            String storeName = entry.getKey();
            StoreCredentials store = entry.getValue();

            int stock = obtenerStockEnTienda(store, sku);
            if (stock >= 0) {
                return new StockNubeDTO(sku, stock, storeName);
            }
        }
        return null;
    }

    /**
     * Obtiene el stock de un producto por SKU en una tienda específica.
     */
    private int obtenerStockEnTienda(StoreCredentials store, String sku) {
        String uri = String.format("/%s/products/sku/%s",
                store.getStoreId(),
                URLEncoder.encode(sku, StandardCharsets.UTF_8));

        String response;
        try {
            response = retryHandler.get(uri, store.getAccessToken());
        } catch (HttpClientErrorException e) {
            return -1;
        }

        if (response == null) {
            return -1;
        }

        try {
            JsonNode product = objectMapper.readTree(response);
            JsonNode variants = product.path("variants");
            if (variants.isArray()) {
                for (JsonNode variant : variants) {
                    String variantSku = variant.path("sku").asString("");
                    if (sku.equals(variantSku)) {
                        return variant.path("stock").asInt(0);
                    }
                }
            }
            return -1;
        } catch (Exception e) {
            log.warn("NUBE - Error al obtener stock de SKU {}: {}", sku, e.getMessage());
            return -1;
        }
    }

    // =====================================================
    // ÓRDENES
    // =====================================================

    /**
     * Busca una orden por número en todas las tiendas.
     */
    public JsonNode buscarOrdenPorNumero(String numeroOrden) {
        verificarCredenciales();

        for (var entry : credentials.getStores().entrySet()) {
            String storeName = entry.getKey();
            StoreCredentials store = entry.getValue();

            String uri = String.format("/%s/orders?q=%s", store.getStoreId(), numeroOrden);

            try {
                String response = retryHandler.get(uri, store.getAccessToken());
                if (response != null) {
                    JsonNode result = objectMapper.readTree(response);
                    if (result.isArray() && !result.isEmpty()) {
                        log.info("NUBE - Orden {} encontrada en {}", numeroOrden, storeName);
                        return result;
                    }
                }
            } catch (Exception e) {
                log.warn("NUBE - Error buscando orden {} en {}: {}", numeroOrden, storeName, e.getMessage());
            }
        }

        return null;
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private boolean tieneFulfillmentUnpacked(JsonNode order) {
        JsonNode fulfillments = order.path("fulfillments");
        if (!fulfillments.isArray() || fulfillments.isEmpty()) return false;
        for (JsonNode fo : fulfillments) {
            if ("unpacked".equalsIgnoreCase(fo.path("status").asString(""))) {
                return true;
            }
        }
        return false;
    }

    private boolean esPickup(JsonNode order) {
        JsonNode fulfillments = order.path("fulfillments");
        if (!fulfillments.isArray()) return false;
        for (JsonNode fo : fulfillments) {
            if ("pickup".equalsIgnoreCase(fo.path("shipping").path("type").asString(""))) {
                return true;
            }
        }
        return false;
    }

    private boolean tieneNota(JsonNode order) {
        String nota = order.path("owner_note").asString("").trim();
        return !nota.isEmpty();
    }

    /**
     * Parsea el header Link y extrae la URL con rel="next".
     * Convierte URLs absolutas a relativas (sin el baseUrl) para el RestClient.
     * Formato: {@code <url>; rel="next", <url>; rel="last"}
     */
    String parseLinkNext(org.springframework.http.HttpHeaders headers) {
        if (headers == null) return null;
        List<String> linkHeaders = headers.get("Link");
        if (linkHeaders == null) return null;

        for (String link : linkHeaders) {
            for (String part : link.split(",")) {
                if (part.contains("rel=\"next\"")) {
                    int start = part.indexOf('<') + 1;
                    int end = part.indexOf('>');
                    if (start > 0 && end > start) {
                        String url = part.substring(start, end).trim();
                        // Usamos la URL del header Link TAL CUAL (la doc de Tienda Nube recomienda
                        // "use the Link URLs instead of building your own"); solo la pasamos a relativa.
                        String baseUrl = properties.baseUrl();
                        if (url.startsWith(baseUrl)) {
                            return url.substring(baseUrl.length());
                        }
                        return url;
                    }
                }
            }
        }
        return null;
    }

    // =====================================================
    // CREDENCIALES
    // =====================================================

    private StoreCredentials getStore(String storeName) {
        if (credentials == null || credentials.getStores() == null) return null;
        return credentials.getStores().get(storeName);
    }

    private void verificarCredenciales() {
        if (credentials == null || credentials.getStores() == null || credentials.getStores().isEmpty()) {
            cargarCredenciales();
            if (credentials == null || credentials.getStores() == null || credentials.getStores().isEmpty()) {
                throw new ServiceNotConfiguredException("NUBE",
                        "No hay credenciales disponibles. Verifique el archivo nube_tokens.json");
            }
        }
    }

    private void cargarCredenciales() {
        try {
            File file = Paths.get(secretsDir).resolve("nube_tokens.json").toFile();
            if (file.exists()) {
                credentials = objectMapper.readValue(file, NubeCredentials.class);
                log.info("NUBE - Credenciales cargadas desde {}. Stores: {}",
                        file.getAbsolutePath(),
                        credentials.getStores() != null ? credentials.getStores().keySet() : "ninguna");
            } else {
                log.warn("NUBE - Archivo de credenciales no encontrado: {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("NUBE - Error cargando credenciales: {}", e.getMessage());
        }
    }

    public boolean isConfigured() {
        return credentials != null && credentials.getStores() != null && !credentials.getStores().isEmpty();
    }

    public List<String> getStoresDisponibles() {
        if (credentials == null || credentials.getStores() == null) return List.of();
        return List.copyOf(credentials.getStores().keySet());
    }

    // =====================================================
    // ACTUALIZACIÓN DE PRECIOS (para Automatización de Precios)
    // =====================================================

    /**
     * Busca un producto por SKU en una tienda y retorna el JSON completo.
     */
    public JsonNode buscarProductoPorSku(String sku, String storeName) {
        verificarCredenciales();
        StoreCredentials store = getStore(storeName);
        if (store == null) {
            log.warn("NUBE - Store '{}' no encontrada en credenciales", storeName);
            return null;
        }

        String uri = String.format("/%s/products/sku/%s",
                store.getStoreId(),
                URLEncoder.encode(sku, StandardCharsets.UTF_8));

        try {
            String response = retryHandler.get(uri, store.getAccessToken());
            if (response == null) return null;
            return objectMapper.readTree(response);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                return null;
            }
            log.warn("NUBE - Error buscando producto SKU {} en {}: {}", sku, storeName, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("NUBE - Error buscando producto SKU {} en {}: {}", sku, storeName, e.getMessage());
            return null;
        }
    }

    /**
     * Actualiza price y promotional_price de una variante específica.
     *
     * @param price            precio tachado (más alto) — null para no enviar
     * @param promotionalPrice precio real que paga el cliente — null para no enviar
     */
    public boolean actualizarPrecioVariante(String storeName, long productId, long variantId,
                                            String price, String promotionalPrice) {
        verificarCredenciales();
        StoreCredentials store = getStore(storeName);
        if (store == null) {
            log.warn("NUBE - Store '{}' no encontrada en credenciales", storeName);
            return false;
        }
        return actualizarPrecioVariante(store, storeName, productId, variantId, price, promotionalPrice);
    }

    /** Variante que reutiliza el StoreCredentials ya resuelto (evita re-verificar credenciales y re-buscar la store). */
    private boolean actualizarPrecioVariante(StoreCredentials store, String storeName, long productId, long variantId,
                                             String price, String promotionalPrice) {
        String uri = String.format("/%s/products/%d/variants/%d",
                store.getStoreId(), productId, variantId);

        // Construir body con los campos que correspondan
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        if (price != null) {
            sb.append("\"price\":\"").append(price).append("\"");
            first = false;
        }
        if (promotionalPrice != null) {
            if (!first) sb.append(",");
            sb.append("\"promotional_price\":\"").append(promotionalPrice).append("\"");
        }
        sb.append("}");

        try {
            retryHandler.putJson(uri, store.getAccessToken(), sb.toString());
            return true;
        } catch (Exception e) {
            log.warn("NUBE - Error actualizando precio variante {}/{} en {}: {}",
                    productId, variantId, storeName, e.getMessage());
            return false;
        }
    }

    /**
     * Actualiza price y promotional_price de todas las variantes de un producto por SKU.
     *
     * @param price            precio tachado (pvpInflado) — null si no aplica
     * @param promotionalPrice precio real (pvp) — null si no aplica
     * @return int[] {actualizados, noEncontrados, errores}
     */
    public int[] actualizarPreciosPorSku(String sku, String storeName, String price, String promotionalPrice) {
        int actualizados = 0, noEncontrados = 0, errores = 0;

        JsonNode product = buscarProductoPorSku(sku, storeName);
        if (product == null) {
            return new int[]{0, 1, 0};
        }

        long productId = product.path("id").asLong(0);
        JsonNode variants = product.path("variants");
        if (!variants.isArray() || variants.isEmpty()) {
            return new int[]{0, 1, 0};
        }

        for (JsonNode variant : variants) {
            long variantId = variant.path("id").asLong(0);
            if (variantId == 0) continue;

            boolean ok = actualizarPrecioVariante(storeName, productId, variantId, price, promotionalPrice);
            if (ok) actualizados++;
            else errores++;
        }

        return new int[]{actualizados, noEncontrados, errores};
    }

    // =====================================================
    // OPTIMIZADO: LISTADO Y ACTUALIZACIÓN MASIVA
    // =====================================================

    /** Info de una variante obtenida del catálogo de Nube. */
    public record VarianteInfo(long productId, long variantId, String price, String promotionalPrice) {}

    /** Update de precios a aplicar a una variante (price/promotionalPrice pueden ser null para no modificar). */
    public record VariantePriceUpdate(long variantId, String price, String promotionalPrice) {}

    /**
     * Lista TODAS las variantes del catálogo de una tienda, paginando.
     * Retorna un mapa sku → VarianteInfo (productId, variantId, price, promotional_price).
     * Los SKUs duplicados quedan con el primero encontrado (equivale al comportamiento de GET /products/sku/{sku}).
     */
    public Map<String, VarianteInfo> listarVariantesPorSku(String storeName) {
        verificarCredenciales();
        StoreCredentials store = getStore(storeName);
        if (store == null) {
            log.warn("NUBE - Store '{}' no encontrada en credenciales", storeName);
            return Map.of();
        }

        Map<String, VarianteInfo> mapa = new LinkedHashMap<>();
        String uri = String.format("/%s/products?per_page=200", store.getStoreId());
        int paginas = 0;

        while (uri != null) {
            NubeRetryHandler.HttpResponse httpResponse;
            try {
                httpResponse = retryHandler.getWithHeaders(uri, store.getAccessToken());
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 404 && e.getResponseBodyAsString().contains("Last page is 0")) {
                    break;
                }
                // Falla real: abortar y devolver vacío para evitar falsos "no encontrados" con datos parciales
                log.warn("NUBE ({}) - Error listando productos en página {}: {}. Abortando paginación.",
                        storeName, paginas + 1, e.getMessage());
                return Map.of();
            }

            if (httpResponse.body() == null) break;

            try {
                JsonNode products = objectMapper.readTree(httpResponse.body());
                if (!products.isArray() || products.isEmpty()) break;

                for (JsonNode product : products) {
                    long productId = product.path("id").asLong(0);
                    if (productId == 0) continue;

                    JsonNode variants = product.path("variants");
                    if (!variants.isArray()) continue;

                    for (JsonNode variant : variants) {
                        String sku = variant.path("sku").asString("");
                        if (sku.isBlank()) continue;
                        long variantId = variant.path("id").asLong(0);
                        if (variantId == 0) continue;

                        String price = variant.path("price").isNull() ? null
                                : variant.path("price").asString(null);
                        String promotionalPrice = variant.path("promotional_price").isNull() ? null
                                : variant.path("promotional_price").asString(null);

                        mapa.putIfAbsent(sku, new VarianteInfo(productId, variantId, price, promotionalPrice));
                    }
                }
                paginas++;
            } catch (Exception e) {
                log.warn("NUBE ({}) - Error parseando productos en página {}: {}. Abortando paginación.",
                        storeName, paginas + 1, e.getMessage());
                return Map.of();
            }

            uri = parseLinkNext(httpResponse.headers());
        }

        log.info("NUBE ({}) - Variantes indexadas: {} SKUs ({} páginas)", storeName, mapa.size(), paginas);
        return mapa;
    }

    /**
     * Lista las categorías de la tienda. Devuelve id de categoría TN → nombre (idioma es).
     * Las categorías cuyo name no tenga texto en es se omiten.
     */
    public Map<Long, String> listarCategorias(String storeName) {
        verificarCredenciales();
        StoreCredentials store = getStore(storeName);
        if (store == null) {
            log.warn("NUBE - Store '{}' no encontrada en credenciales", storeName);
            return Map.of();
        }

        Map<Long, String> mapa = new LinkedHashMap<>();
        String uri = String.format("/%s/categories?per_page=200&fields=id,name", store.getStoreId());
        int paginas = 0;

        while (uri != null) {
            NubeRetryHandler.HttpResponse httpResponse;
            try {
                httpResponse = retryHandler.getWithHeaders(uri, store.getAccessToken());
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 404 && e.getResponseBodyAsString().contains("Last page is 0")) {
                    break;
                }
                log.warn("NUBE ({}) - Error listando categorías en página {}: {}. Abortando paginación.",
                        storeName, paginas + 1, e.getMessage());
                return Map.of();
            }

            if (httpResponse.body() == null) break;

            try {
                JsonNode categorias = objectMapper.readTree(httpResponse.body());
                if (!categorias.isArray() || categorias.isEmpty()) break;

                for (JsonNode cat : categorias) {
                    long id = cat.path("id").asLong(0);
                    if (id == 0) continue;
                    String nombre = extraerNombreProducto(cat.path("name"));
                    if (nombre == null) continue;
                    mapa.putIfAbsent(id, nombre);
                }
                paginas++;
            } catch (Exception e) {
                log.warn("NUBE ({}) - Error parseando categorías en página {}: {}. Abortando paginación.",
                        storeName, paginas + 1, e.getMessage());
                return Map.of();
            }

            uri = parseLinkNext(httpResponse.headers());
        }

        log.info("NUBE ({}) - Categorías indexadas: {} ({} páginas)", storeName, mapa.size(), paginas);
        return mapa;
    }

    /**
     * Carga el árbol de categorías de la tienda (id, name, parent) en una estructura cacheada
     * para find-or-create. Devuelve un árbol vacío si la tienda no está configurada o falla la lectura.
     */
    public NubeCategoriaArbol cargarArbolCategorias(String storeName) {
        NubeCategoriaArbol arbol = new NubeCategoriaArbol();
        StoreCredentials store;
        try {
            verificarCredenciales();
            store = getStore(storeName);
        } catch (Exception e) {
            log.warn("NUBE - No se pudo cargar árbol de categorías de '{}': {}", storeName, e.getMessage());
            return arbol;
        }
        if (store == null) return arbol;

        // No usamos el parámetro `fields`: no está documentado en la API de Tienda Nube y, aunque
        // hoy lo procesa, en la paginación rechazaba sus valores con 422 ("Invalid fields"). Traemos
        // la categoría completa (igual solo leemos id/name/parent del JSON).
        String uri = String.format("/%s/categories?per_page=200", store.getStoreId());
        int paginas = 0;

        while (uri != null) {
            NubeRetryHandler.HttpResponse httpResponse;
            try {
                httpResponse = retryHandler.getWithHeaders(uri, store.getAccessToken());
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 404 && e.getResponseBodyAsString().contains("Last page is 0")) {
                    break;
                }
                log.warn("NUBE ({}) - Error cargando árbol de categorías en página {}: {}. Abortando.",
                        storeName, paginas + 1, e.getMessage());
                return arbol;
            }

            if (httpResponse.body() == null) break;

            try {
                JsonNode categorias = objectMapper.readTree(httpResponse.body());
                if (!categorias.isArray() || categorias.isEmpty()) break;

                for (JsonNode cat : categorias) {
                    long id = cat.path("id").asLong(0);
                    if (id == 0) continue;
                    String nombre = extraerNombreProducto(cat.path("name"));
                    if (nombre == null) continue;
                    long parent = cat.path("parent").asLong(0);
                    arbol.registrar(id, parent == 0 ? null : parent, nombre);
                }
                paginas++;
            } catch (Exception e) {
                log.warn("NUBE ({}) - Error parseando árbol de categorías en página {}: {}. Abortando.",
                        storeName, paginas + 1, e.getMessage());
                return arbol;
            }

            uri = parseLinkNext(httpResponse.headers());
        }

        log.info("NUBE ({}) - Árbol de categorías cargado ({} páginas)", storeName, paginas);
        return arbol;
    }

    /**
     * Recorre los productos de la tienda y arma id de categoría TN → SKUs que la tienen.
     * El array {@code categories} está a nivel producto; el SKU está a nivel variante:
     * todas las variantes (SKUs) de un producto pertenecen a las categorías de ese producto.
     */
    public Map<Long, List<String>> mapearCategoriasASkus(String storeName) {
        verificarCredenciales();
        StoreCredentials store = getStore(storeName);
        if (store == null) {
            log.warn("NUBE - Store '{}' no encontrada en credenciales", storeName);
            return Map.of();
        }

        Map<Long, LinkedHashSet<String>> acumulador = new LinkedHashMap<>();
        String uri = String.format("/%s/products?per_page=200&fields=id,categories,variants", store.getStoreId());
        int paginas = 0;

        while (uri != null) {
            NubeRetryHandler.HttpResponse httpResponse;
            try {
                httpResponse = retryHandler.getWithHeaders(uri, store.getAccessToken());
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 404 && e.getResponseBodyAsString().contains("Last page is 0")) {
                    break;
                }
                log.warn("NUBE ({}) - Error listando productos (categorías) en página {}: {}. Abortando paginación.",
                        storeName, paginas + 1, e.getMessage());
                return Map.of();
            }

            if (httpResponse.body() == null) break;

            try {
                JsonNode products = objectMapper.readTree(httpResponse.body());
                if (!products.isArray() || products.isEmpty()) break;

                for (JsonNode product : products) {
                    JsonNode categories = product.path("categories");
                    if (!categories.isArray() || categories.isEmpty()) continue;

                    JsonNode variants = product.path("variants");
                    if (!variants.isArray()) continue;

                    List<String> skus = new ArrayList<>();
                    for (JsonNode variant : variants) {
                        String sku = variant.path("sku").asString("");
                        if (!sku.isBlank()) skus.add(sku);
                    }
                    if (skus.isEmpty()) continue;

                    for (JsonNode cat : categories) {
                        long catId = cat.path("id").asLong(0);
                        if (catId == 0) continue;
                        acumulador.computeIfAbsent(catId, k -> new LinkedHashSet<>()).addAll(skus);
                    }
                }
                paginas++;
            } catch (Exception e) {
                log.warn("NUBE ({}) - Error parseando productos (categorías) en página {}: {}. Abortando paginación.",
                        storeName, paginas + 1, e.getMessage());
                return Map.of();
            }

            uri = parseLinkNext(httpResponse.headers());
        }

        Map<Long, List<String>> mapa = new LinkedHashMap<>();
        for (Map.Entry<Long, LinkedHashSet<String>> entry : acumulador.entrySet()) {
            mapa.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        log.info("NUBE ({}) - Categorías con productos: {} ({} páginas)", storeName, mapa.size(), paginas);
        return mapa;
    }

    /**
     * Lista todos los productos de una tienda y devuelve un mapa {@code sku → titulo} (uppercase).
     * El campo {@code name} de Tienda Nube puede ser objeto i18n (ej: {@code {"es": "..."}}) o string;
     * se prefiere "es" y, si no, se toma el primer valor no vacío.
     * <p>Si la paginación falla, devuelve un mapa vacío para no escribir títulos parciales.</p>
     */
    public Map<String, String> obtenerTitulosPorSku(String storeName) {
        verificarCredenciales();
        StoreCredentials store = getStore(storeName);
        if (store == null) {
            log.warn("NUBE - Store '{}' no encontrada en credenciales", storeName);
            return Map.of();
        }

        Map<String, String> mapa = new LinkedHashMap<>();
        String uri = String.format("/%s/products?per_page=200&fields=id,name,variants", store.getStoreId());
        int paginas = 0;

        while (uri != null) {
            NubeRetryHandler.HttpResponse httpResponse;
            try {
                httpResponse = retryHandler.getWithHeaders(uri, store.getAccessToken());
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 404 && e.getResponseBodyAsString().contains("Last page is 0")) {
                    break;
                }
                log.warn("NUBE ({}) - Error listando productos en página {}: {}. Abortando paginación.",
                        storeName, paginas + 1, e.getMessage());
                return Map.of();
            }

            if (httpResponse.body() == null) break;

            try {
                JsonNode products = objectMapper.readTree(httpResponse.body());
                if (!products.isArray() || products.isEmpty()) break;

                for (JsonNode product : products) {
                    String nombre = extraerNombreProducto(product.path("name"));
                    if (nombre == null || nombre.isBlank()) continue;
                    nombre = nombre.toUpperCase();

                    JsonNode variants = product.path("variants");
                    if (!variants.isArray()) continue;

                    for (JsonNode variant : variants) {
                        String sku = variant.path("sku").asString("");
                        if (sku.isBlank()) continue;
                        mapa.putIfAbsent(sku, nombre);
                    }
                }
                paginas++;
            } catch (Exception e) {
                log.warn("NUBE ({}) - Error parseando productos en página {}: {}. Abortando paginación.",
                        storeName, paginas + 1, e.getMessage());
                return Map.of();
            }

            uri = parseLinkNext(httpResponse.headers());
        }

        log.info("NUBE ({}) - Títulos indexados: {} SKUs ({} páginas)", storeName, mapa.size(), paginas);
        return mapa;
    }

    /**
     * Resuelve el campo {@code name} de Tienda Nube. Puede venir como string directo
     * o como objeto i18n con claves de idioma. Se toma "es"; si no hay, devuelve null.
     */
    private String extraerNombreProducto(JsonNode nameNode) {
        if (nameNode == null || nameNode.isMissingNode() || nameNode.isNull()) return null;
        if (nameNode.isTextual()) {
            String v = nameNode.asString("").trim();
            return v.isEmpty() ? null : v;
        }
        if (nameNode.isObject()) {
            JsonNode es = nameNode.get("es");
            if (es != null && es.isTextual()) {
                String esValue = es.asString("").trim();
                if (!esValue.isEmpty()) return esValue;
            }
        }
        return null;
    }

    /**
     * Actualiza en bloque los precios de variantes que pertenecen al MISMO producto.
     * Usa PATCH /products/{id}/variants (array). Si falla, hace fallback a PUT individual por variante.
     *
     * @return cantidad de variantes efectivamente actualizadas (0..updates.size()).
     */
    public int actualizarVariantesDeProducto(String storeName, long productId,
                                             List<VariantePriceUpdate> updates) {
        if (updates == null || updates.isEmpty()) return 0;
        verificarCredenciales();
        StoreCredentials store = getStore(storeName);
        if (store == null) {
            log.warn("NUBE - Store '{}' no encontrada en credenciales", storeName);
            return 0;
        }

        String uri = String.format("/%s/products/%d/variants", store.getStoreId(), productId);
        String body = construirBodyPatchVariantes(updates);

        try {
            retryHandler.patchJson(uri, store.getAccessToken(), body);
            return updates.size();
        } catch (Exception e) {
            log.warn("NUBE - PATCH bulk falló para producto {} ({}), fallback a PUT individual: {}",
                    productId, storeName, e.getMessage());
        }

        int ok = 0;
        for (VariantePriceUpdate u : updates) {
            if (actualizarPrecioVariante(storeName, productId, u.variantId(), u.price(), u.promotionalPrice())) {
                ok++;
            }
        }
        return ok;
    }

    /** Crea una categoría en TN ({name:{es},parent}) y devuelve su id. parentId null = raíz. */
    private Long crearCategoria(StoreCredentials store, Long parentId, String nombre) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", Map.of("es", nombre));
        if (parentId != null) body.put("parent", parentId);
        String resp = retryHandler.postJson(
                "/" + store.getStoreId() + "/categories", store.getAccessToken(),
                objectMapper.writeValueAsString(body));
        return objectMapper.readTree(resp).path("id").asLong();
    }

    // =====================================================
    // ACTUALIZACIÓN DE PRODUCTO (PATCH /products/{id})
    // =====================================================

    /** Actualiza price/promotional_price de una variante. */
    @FunctionalInterface
    public interface ActualizadorPrecioVariante {
        boolean actualizar(long productId, long variantId, String price, String promotionalPrice);
    }

    /** Núcleo testeable de la actualización (sin red). buscador(sku)->JSON existente; patcher(uri,body)->PATCH; precioFn aplica el precio. */
    public static ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube actualizarProductoEnNubeCore(
            ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto,
            BigDecimal pvp, BigDecimal pvpInflado, ObjectMapper om, String storeId,
            List<Long> categoriaIds,
            Function<String, JsonNode> buscador,
            BiConsumer<String, String> patcher,
            ActualizadorPrecioVariante precioFn) {
        try {
            JsonNode existente = buscador.apply(producto.getSku());
            if (existente == null) return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.error("no encontrado en Nube al actualizar");
            long productId = existente.path("id").asLong(0);
            if (productId <= 0) return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.error("id de producto Nube inválido");

            // variantId: la variante cuyo sku coincide; si no, la primera.
            long variantId = 0;
            JsonNode variants = existente.path("variants");
            if (variants.isArray()) {
                for (JsonNode v : variants) {
                    if (producto.getSku().equals(v.path("sku").asString(null))) { variantId = v.path("id").asLong(0); break; }
                }
                if (variantId == 0 && variants.size() > 0) variantId = variants.get(0).path("id").asLong(0);
            }
            if (variantId <= 0) return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.error("variante Nube no encontrada");

            // PUT name + description + marca + categorias (si las hay)
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", Map.of("es", producto.getTituloNube() != null ? producto.getTituloNube() : ""));
            body.put("description", Map.of("es", NubeDescripcionBuilder.construir(producto)));
            body.put("published", Boolean.TRUE.equals(producto.getActivo()));
            if (producto.getMarca() != null && producto.getMarca().getNombre() != null && !producto.getMarca().getNombre().isBlank())
                body.put("brand", producto.getMarca().getNombre());
            if (categoriaIds != null && !categoriaIds.isEmpty()) {
                body.put("categories", new ArrayList<>(categoriaIds));
            }
            patcher.accept("/" + storeId + "/products/" + productId, om.writeValueAsString(body));

            // Precio (misma lógica que el alta: inflado => price tachado + promotional)
            String price = null, promo = null;
            if (pvpInflado != null && pvp != null && pvpInflado.compareTo(pvp) > 0) {
                price = pvpInflado.toPlainString(); promo = pvp.toPlainString();
            } else if (pvp != null) {
                price = pvp.toPlainString();
            }
            if (price != null) precioFn.actualizar(productId, variantId, price, promo);

            return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.actualizado(productId);
        } catch (Exception e) {
            return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.error(e.getMessage());
        }
    }

    /** Actualiza un producto existente en Nube (name/description/categorias/precio); reutiliza el JSON ya buscado (evita un segundo GET). */
    public ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube actualizarProductoEnNube(
            String storeName, ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto,
            BigDecimal pvp, BigDecimal pvpInflado, JsonNode existente, List<Long> categoriaIds) {
        StoreCredentials store;
        try {
            verificarCredenciales();
            store = getStore(storeName);
        } catch (Exception e) {
            return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.error("Tienda Nube no configurada: " + e.getMessage());
        }
        if (store == null) return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.error("Tienda '" + storeName + "' no configurada");

        ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube r = actualizarProductoEnNubeCore(
                producto, pvp, pvpInflado, objectMapper, store.getStoreId(),
                categoriaIds,
                sku -> existente,
                // Tienda Nube actualiza productos con PUT /products/{id} (NO acepta PATCH ahí → 404).
                (uri, body) -> retryHandler.putJson(uri, store.getAccessToken(), body),
                (productId, variantId, price, promo) ->
                        actualizarPrecioVariante(store, storeName, productId, variantId, price, promo));

        if (r.estado() == ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.Estado.ACTUALIZADO && r.productoNubeId() != null && r.productoNubeId() > 0) {
            String advertencia = sincronizarImagenesNube(store, r.productoNubeId(), producto.getSku());
            if (advertencia != null) r = r.conAdvertencia(advertencia);
        }
        return r;
    }

    /** Nombres de la ruta de clasificación (clasif + tipo) para categorizar en Nube. */
    private record ClasifRuta(List<String> clasifNombres, List<String> tipoNombres) {}

    private ClasifRuta resolverRutaNombres(String storeName, ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto) {
        boolean esGastro = STORE_GASTRO.equalsIgnoreCase(storeName);
        ClasifGral clasifGral = producto.getClasifGral();
        ClasifGastro clasifGastro = producto.getClasifGastro();
        Tipo tipo = producto.getTipo();
        List<String> clasifNombres = esGastro
                ? (clasifGastro == null ? null : NubeCategoriaRuta.aplanar(clasifGastro, ClasifGastro::getPadre, ClasifGastro::getNombre))
                : (clasifGral == null ? null : NubeCategoriaRuta.aplanar(clasifGral, ClasifGral::getPadre, ClasifGral::getNombre));
        List<String> tipoNombres = tipo == null ? null
                : NubeCategoriaRuta.aplanar(tipo, Tipo::getPadre, Tipo::getNombre);
        return new ClasifRuta(clasifNombres, tipoNombres);
    }

    /** Resuelve los ids de categoría de Nube (clasif + tipo) para un producto, creando las faltantes. */
    public List<Long> resolverCategoriaIds(String storeName, ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto, NubeCategoriaArbol arbol) {
        StoreCredentials store;
        try { verificarCredenciales(); store = getStore(storeName); }
        catch (Exception e) { return List.of(); }
        if (store == null || arbol == null) return List.of();

        ClasifRuta ruta = resolverRutaNombres(storeName, producto);
        List<String> clasifNombres = ruta.clasifNombres();
        List<String> tipoNombres = ruta.tipoNombres();
        if (clasifNombres == null || clasifNombres.isEmpty() || tipoNombres == null || tipoNombres.isEmpty()) {
            return List.of();
        }
        List<String> rutaNombres = new java.util.ArrayList<>(clasifNombres);
        rutaNombres.addAll(tipoNombres);
        return NubeCategoriaResolver.resolver(arbol, rutaNombres, (parentId, nombre) -> crearCategoria(store, parentId, nombre));
    }

    // =====================================================
    // ALTA DE PRODUCTO (POST /products)
    // =====================================================

    /**
     * Da de alta un producto en una tienda Nube (oculto). Resuelve credenciales y delega al core testeable.
     */
    public ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube crearProductoEnNube(
            String storeName, ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto,
            java.math.BigDecimal pvp, java.math.BigDecimal pvpInflado, NubeCategoriaArbol arbol) {
        StoreCredentials store;
        try {
            verificarCredenciales();
            store = getStore(storeName);
        } catch (Exception e) {
            return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.error("Tienda Nube no configurada: " + e.getMessage());
        }
        if (store == null)
            return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.error("Tienda '" + storeName + "' no configurada");

        // KT GASTRO → clasif gastro; resto (KT HOGAR) → clasif gral. El tipo cuelga debajo de la clasif.
        ClasifRuta ruta = resolverRutaNombres(storeName, producto);
        List<String> clasifNombres = ruta.clasifNombres();
        List<String> tipoNombres = ruta.tipoNombres();

        NubeCategoriaArbol arbolUsar = arbol != null ? arbol : new NubeCategoriaArbol();
        ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube r = crearProductoEnNubeCore(
                store, producto, pvp, pvpInflado, objectMapper,
                clasifNombres, tipoNombres, arbolUsar,
                (parentId, nombre) -> crearCategoria(store, parentId, nombre),
                (sku, token) -> buscarProductoPorSku(sku, storeName),
                (uri, body) -> retryHandler.postJson(uri, store.getAccessToken(), body));

        if (r.estado() == ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.Estado.CREADO
                && r.productoNubeId() != null && r.productoNubeId() > 0) {
            String advertencia = subirImagenesProducto(store, r.productoNubeId(), producto.getSku());
            if (advertencia != null) {
                r = r.conAdvertencia(advertencia);
            }
        }
        return r;
    }

    /** Lógica testeable sin red. {@code buscador} devuelve el JSON del producto si existe (o null); {@code poster} hace POST(uri, body)->respuesta. */
    static ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube crearProductoEnNubeCore(
            StoreCredentials store, ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto,
            java.math.BigDecimal pvp, java.math.BigDecimal pvpInflado,
            ObjectMapper om,
            List<String> clasifNombres, List<String> tipoNombres,
            NubeCategoriaArbol arbol,
            BiFunction<Long, String, Long> creadorCategoria,
            java.util.function.BiFunction<String, String, JsonNode> buscador,
            java.util.function.BiFunction<String, String, String> poster) {
        try {
            if (producto.getTituloNube() == null || producto.getTituloNube().isBlank())
                return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.error("falta título Nube");
            if (buscador.apply(producto.getSku(), store.getAccessToken()) != null)
                return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.yaExistia();
            if (clasifNombres == null || clasifNombres.isEmpty() || tipoNombres == null || tipoNombres.isEmpty())
                return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.error("falta clasif/tipo para categorizar");

            List<String> rutaNombres = new java.util.ArrayList<>(clasifNombres);
            rutaNombres.addAll(tipoNombres);
            List<Long> categoriaIds = NubeCategoriaResolver.resolver(arbol, rutaNombres, creadorCategoria);

            Map<String, Object> payload = NubeProductoPayloadBuilder.construir(producto, pvp, pvpInflado, categoriaIds);
            String body = om.writeValueAsString(payload);
            String uri = "/" + store.getStoreId() + "/products";
            String respuesta = poster.apply(uri, body);
            Long productoNubeId = respuesta == null ? null : om.readTree(respuesta).path("id").asLong(0);
            return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.creado(productoNubeId);
        } catch (Exception e) {
            return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.error(e.getMessage());
        }
    }

    /** Une partes de advertencia no vacías con "; "; null si no hay ninguna. */
    static String combinarAdvertencias(String... partes) {
        String r = Arrays.stream(partes)
                .filter(p -> p != null && !p.isBlank())
                .collect(Collectors.joining("; "));
        return r.isBlank() ? null : r;
    }

    /**
     * Sube a TN todas las imágenes del SKU (principal + adicionales) al producto recién creado.
     * Filtra por formato válido (EXT_NUBE) y reporta las omitidas.
     * Devuelve una advertencia para el resumen, o null si subió todas. Un fallo de una imagen
     * (lectura o POST) no frena las demás ni revierte el alta.
     */
    private String subirImagenesProducto(StoreCredentials store, Long productoNubeId, String sku) {
        ImagenService.FiltroImagenes filtro = imagenService.filtrarParaCanal(sku, EXT_NUBE);
        if (filtro.validas().isEmpty() && filtro.rechazadas().isEmpty()) {
            return "creado sin imagen";
        }
        String advSubida = filtro.validas().isEmpty() ? null : subirImagenes(store, productoNubeId, filtro.validas());
        return combinarAdvertencias(advSubida, ImagenService.describirRechazadas(filtro.rechazadas()));
    }

    /** Sube la lista de imágenes ya resuelta. Devuelve null si subió todas, o una advertencia con el conteo. */
    private String subirImagenes(StoreCredentials store, Long productoNubeId, List<String> archivos) {
        int ok = 0;
        for (int i = 0; i < archivos.size(); i++) {
            String filename = archivos.get(i);
            try {
                String base64 = imagenService.leerBase64(filename);
                Map<String, Object> body = NubeImagenPayloadBuilder.construir(filename, base64, i + 1);
                retryHandler.postJson(
                        "/" + store.getStoreId() + "/products/" + productoNubeId + "/images",
                        store.getAccessToken(), objectMapper.writeValueAsString(body));
                ok++;
            } catch (Exception e) {
                log.warn("NUBE - Falló subir imagen {} del producto {}: {}", filename, productoNubeId, e.getMessage());
            }
        }
        return ok == archivos.size() ? null : (ok + " de " + archivos.size() + " imágenes subidas");
    }

    /** Reemplaza las imágenes del producto en Nube por las locales actuales (GET lista -> DELETE -> POST). Best-effort. */
    private String sincronizarImagenesNube(StoreCredentials store, Long productoNubeId, String sku) {
        // 1) Listar y borrar las imágenes actuales.
        try {
            String body = retryHandler.get(
                    "/" + store.getStoreId() + "/products/" + productoNubeId + "/images", store.getAccessToken());
            if (body != null) {
                JsonNode imgs = objectMapper.readTree(body);
                if (imgs.isArray()) {
                    for (JsonNode img : imgs) {
                        long imgId = img.path("id").asLong(0);
                        if (imgId <= 0) continue;
                        try {
                            retryHandler.delete("/" + store.getStoreId() + "/products/" + productoNubeId + "/images/" + imgId,
                                    store.getAccessToken());
                        } catch (Exception e) {
                            log.warn("NUBE - Falló borrar imagen {} del producto {}: {}", imgId, productoNubeId, e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("NUBE - No se pudieron listar/borrar imágenes del producto {}: {}", productoNubeId, e.getMessage());
        }
        // 2) Subir las locales válidas (filtradas por formato/tamaño); reportar omitidas.
        ImagenService.FiltroImagenes filtro = imagenService.filtrarParaCanal(sku, EXT_NUBE);
        if (filtro.validas().isEmpty() && filtro.rechazadas().isEmpty()) {
            return null; // sin imágenes locales: nada que sincronizar
        }
        String advSubida = filtro.validas().isEmpty() ? null : subirImagenes(store, productoNubeId, filtro.validas());
        return combinarAdvertencias(advSubida, ImagenService.describirRechazadas(filtro.rechazadas()));
    }

    private String construirBodyPatchVariantes(List<VariantePriceUpdate> updates) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < updates.size(); i++) {
            if (i > 0) sb.append(",");
            VariantePriceUpdate u = updates.get(i);
            sb.append("{\"id\":").append(u.variantId());
            if (u.price() != null) {
                sb.append(",\"price\":\"").append(u.price()).append("\"");
            }
            if (u.promotionalPrice() != null) {
                sb.append(",\"promotional_price\":\"").append(u.promotionalPrice()).append("\"");
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }
}

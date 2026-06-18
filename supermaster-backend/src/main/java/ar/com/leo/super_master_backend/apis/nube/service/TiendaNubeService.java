package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.apis.nube.NubeRetryHandler;
import ar.com.leo.super_master_backend.apis.nube.config.NubeProperties;
import ar.com.leo.super_master_backend.apis.nube.dto.StockNubeDTO;
import ar.com.leo.super_master_backend.apis.nube.dto.VentaNubeDTO;
import ar.com.leo.super_master_backend.apis.nube.model.NubeCredentials;
import ar.com.leo.super_master_backend.apis.nube.model.NubeCredentials.StoreCredentials;
import ar.com.leo.super_master_backend.dominio.common.exception.ServiceNotConfiguredException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TiendaNubeService {

    public static final String STORE_HOGAR = "KT HOGAR";
    public static final String STORE_GASTRO = "KT GASTRO";
    private static final long BASE_WAIT_MS = 2000L;

    private final RestClient restClient;
    private final NubeProperties properties;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${app.secrets-dir}")
    private String secretsDir;

    private NubeRetryHandler retryHandler;
    private NubeCredentials credentials;

    public TiendaNubeService(RestClient nubeRestClient, NubeProperties properties, ObjectMapper objectMapper) {
        this.restClient = nubeRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
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
    private String parseLinkNext(org.springframework.http.HttpHeaders headers) {
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
                        // Convertir URL absoluta a relativa para el RestClient
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

    // =====================================================
    // ALTA DE PRODUCTO (POST /products)
    // =====================================================

    /**
     * Da de alta un producto en una tienda Nube (oculto). Resuelve credenciales y delega al core testeable.
     */
    public ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube crearProductoEnNube(
            String storeName, ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto,
            java.math.BigDecimal pvp, java.math.BigDecimal pvpInflado) {
        verificarCredenciales();
        StoreCredentials store = getStore(storeName);
        if (store == null)
            return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.error("Tienda '" + storeName + "' no configurada");
        return crearProductoEnNubeCore(store, producto, pvp, pvpInflado, objectMapper,
                (sku, token) -> buscarProductoPorSku(sku, storeName),
                (uri, body) -> retryHandler.postJson(uri, store.getAccessToken(), body));
    }

    /** Lógica testeable sin red. {@code buscador} devuelve el JSON del producto si existe (o null); {@code poster} hace POST(uri, body)->respuesta. */
    static ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube crearProductoEnNubeCore(
            StoreCredentials store, ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto,
            java.math.BigDecimal pvp, java.math.BigDecimal pvpInflado,
            ObjectMapper om,
            java.util.function.BiFunction<String, String, JsonNode> buscador,
            java.util.function.BiFunction<String, String, String> poster) {
        try {
            if (producto.getTituloNube() == null || producto.getTituloNube().isBlank())
                return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.error("Falta Título Nube");
            if (buscador.apply(producto.getSku(), store.getAccessToken()) != null)
                return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.yaExistia();

            Map<String, Object> payload = NubeProductoPayloadBuilder.construir(producto, pvp, pvpInflado);
            String body = om.writeValueAsString(payload);
            String uri = "/" + store.getStoreId() + "/products";
            poster.apply(uri, body);
            return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.creado();
        } catch (Exception e) {
            return ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube.error(e.getMessage());
        }
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

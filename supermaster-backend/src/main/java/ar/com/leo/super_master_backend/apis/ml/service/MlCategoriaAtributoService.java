package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.apis.ml.MlRetryHandler;
import ar.com.leo.super_master_backend.apis.ml.config.MercadoLibreProperties;
import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDefDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoValorDTO;
import ar.com.leo.super_master_backend.apis.ml.model.TokensML;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio que obtiene, cachea, filtra y agrupa los atributos de una categoría ML
 * desde {@code GET /categories/{id}/attributes}.
 *
 * <p>Cachea el JSON crudo por TTL (6 h) y deriva dos vistas desde él:
 * <ul>
 *   <li>{@link #obtenerAtributos(String)} — lista filtrada y agrupada para el formulario</li>
 *   <li>{@link #idsValidos(String)} — todos los ids declarados por la categoría (para gating GTIN/EAN)</li>
 * </ul>
 */
@Slf4j
@Service
public class MlCategoriaAtributoService {

    // =====================================================================
    // Atributos auto-gestionados por la plataforma: se excluyen del formulario
    // =====================================================================
    static final Set<String> AUTOGESTIONADOS = Set.of(
            "ITEM_CONDITION", "BRAND", "SELLER_SKU",
            "SELLER_PACKAGE_HEIGHT", "SELLER_PACKAGE_WIDTH",
            "SELLER_PACKAGE_LENGTH", "SELLER_PACKAGE_WEIGHT",
            "VALUE_ADDED_TAX", "IMPORT_DUTY",
            "GTIN", "EAN"
    );

    private static final long TTL_MS = 6 * 60 * 60 * 1000L; // 6 horas

    private record CacheEntry(String rawJson, long fetchedAt) {
        boolean isExpired() {
            return System.currentTimeMillis() - fetchedAt > TTL_MS;
        }
    }

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final MercadoLibreProperties properties;

    @Value("${app.secrets-dir}")
    private String secretsDir;

    private MlRetryHandler retryHandler;
    private volatile TokensML tokens;

    public MlCategoriaAtributoService(ObjectMapper objectMapper,
                                      RestClient mercadoLibreRestClient,
                                      MercadoLibreProperties properties) {
        this.objectMapper = objectMapper;
        this.restClient = mercadoLibreRestClient;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        this.retryHandler = new MlRetryHandler(
                restClient,
                properties.retryBaseWaitMs(),
                properties.rateLimitPerSecond(),
                this::verificarTokens
        );
        cargarTokens();
    }

    // =====================================================================
    // API pública
    // =====================================================================

    /**
     * Devuelve los atributos de la categoría, filtrados (sin auto-gestionados,
     * sin read_only, sin fixed) y agrupados (PRINCIPALES / SECUNDARIAS).
     */
    public List<MlAtributoDefDTO> obtenerAtributos(String categoryId) {
        String raw = obtenerJsonCrudo(categoryId);
        try {
            JsonNode arr = objectMapper.readTree(raw);
            return parsear(arr);
        } catch (Exception e) {
            log.warn("ML - Error parseando atributos de categoria {}: {}", categoryId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Devuelve todos los ids de atributos declarados por la categoría, sin filtrar,
     * para gating de GTIN/EAN.
     */
    public Set<String> idsValidos(String categoryId) {
        String raw = obtenerJsonCrudo(categoryId);
        Set<String> ids = new HashSet<>();
        try {
            JsonNode arr = objectMapper.readTree(raw);
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    String id = node.path("id").asString(null);
                    if (id != null && !id.isBlank()) {
                        ids.add(id);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("ML - Error obteniendo ids de categoria {}: {}", categoryId, e.getMessage());
        }
        return ids;
    }

    // =====================================================================
    // Parseo puro (package-private para test)
    // =====================================================================

    /**
     * Parsea el array JSON de atributos de ML aplicando las reglas de filtrado y agrupado.
     * Método estático y puro: no hace red, no usa Spring, testeable en aislamiento.
     */
    static List<MlAtributoDefDTO> parsear(JsonNode arr) {
        List<MlAtributoDefDTO> result = new ArrayList<>();
        if (arr == null || !arr.isArray()) {
            return result;
        }
        for (JsonNode node : arr) {
            String id = node.path("id").asString(null);
            if (id == null || id.isBlank()) {
                continue;
            }

            // Leer tags
            JsonNode tags = node.path("tags");
            boolean readOnly   = !tags.isMissingNode() && tags.has("read_only");
            boolean fixed      = !tags.isMissingNode() && tags.has("fixed");

            // Filtrar: auto-gestionados, read_only, fixed
            if (AUTOGESTIONADOS.contains(id) || readOnly || fixed) {
                continue;
            }

            String name      = node.path("name").asString(null);
            String valueType = node.path("value_type").asString(null);

            boolean required    = !tags.isMissingNode() && (tags.has("required") || tags.has("new_required"));
            boolean conditional = !tags.isMissingNode() && tags.has("conditional_required");
            boolean multivalued = !tags.isMissingNode() && tags.has("multivalued");

            // values[]
            List<MlAtributoValorDTO> values = new ArrayList<>();
            JsonNode valuesNode = node.path("values");
            if (valuesNode.isArray()) {
                for (JsonNode v : valuesNode) {
                    String vid   = v.path("id").asString(null);
                    String vname = v.path("name").asString(null);
                    values.add(new MlAtributoValorDTO(vid, vname));
                }
            }

            // allowed_units[] → List<String> de sus ids
            List<String> allowedUnits = new ArrayList<>();
            JsonNode unitsNode = node.path("allowed_units");
            if (unitsNode.isArray()) {
                for (JsonNode u : unitsNode) {
                    String uid = u.path("id").asString(null);
                    if (uid != null) {
                        allowedUnits.add(uid);
                    }
                }
            }

            String defaultUnit = node.path("default_unit").asString(null);

            // Grupo
            String groupId = node.path("attribute_group_id").asString("");
            String grupo = "MAIN".equals(groupId) ? "PRINCIPALES" : "SECUNDARIAS";

            result.add(new MlAtributoDefDTO(
                    id, name, valueType, values, allowedUnits, defaultUnit,
                    required, conditional, multivalued, grupo
            ));
        }
        return result;
    }

    // =====================================================================
    // Cache + red
    // =====================================================================

    private String obtenerJsonCrudo(String categoryId) {
        CacheEntry entry = cache.get(categoryId);
        if (entry != null && !entry.isExpired()) {
            return entry.rawJson();
        }
        String raw = fetchDesdeApi(categoryId);
        if (raw != null) {
            cache.put(categoryId, new CacheEntry(raw, System.currentTimeMillis()));
        }
        return raw != null ? raw : "[]";
    }

    private String fetchDesdeApi(String categoryId) {
        verificarTokens();
        String uri = "/categories/" + categoryId + "/attributes";
        String body = retryHandler.get(uri, () -> tokens.accessToken);
        if (body == null) {
            log.warn("ML - No se pudo obtener atributos de categoria {}", categoryId);
        }
        return body;
    }

    // =====================================================================
    // Token management (igual patrón que MercadoLibreService)
    // =====================================================================

    private void verificarTokens() {
        if (tokens == null) {
            cargarTokens();
        }
    }

    private void cargarTokens() {
        try {
            File file = Paths.get(secretsDir).resolve("ml_tokens.json").toFile();
            if (file.exists()) {
                tokens = objectMapper.readValue(file, TokensML.class);
                log.info("ML - CategoriaAtributoService: tokens cargados desde {}", file.getAbsolutePath());
            } else {
                log.warn("ML - CategoriaAtributoService: tokens no encontrados en {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("ML - CategoriaAtributoService: error cargando tokens: {}", e.getMessage());
        }
    }
}

package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.apis.ml.MlRetryHandler;
import ar.com.leo.super_master_backend.apis.ml.config.MercadoLibreProperties;
import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDefDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoValorDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.MlComponenteDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.MlFichaDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.MlSeccionDTO;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Construye la "ficha técnica" de una categoría de ML tal como la muestra el flujo de publicación,
 * a partir de {@code GET /categories/{id}/technical_specs/input}.
 *
 * <p>Cachea el JSON crudo por TTL (6 h) y lo reorganiza en secciones
 * (Variante / Principales / Secundarias) → componentes → atributos.
 */
@Slf4j
@Service
public class MlFichaService {

    /** Tags que ocultan el atributo del flujo de venta (no se muestran en el formulario). */
    static final Set<String> TAGS_OCULTOS = Set.of(
            "hidden", "read_only", "fixed", "vip_hidden", "used_hidden", "new_hidden");

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

    public MlFichaService(ObjectMapper objectMapper,
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
                this::verificarTokens);
        cargarTokens();
    }

    // =====================================================================
    // API pública
    // =====================================================================

    /** Devuelve la ficha técnica de la categoría (secciones → componentes → atributos). */
    public MlFichaDTO obtenerFicha(String categoryId) {
        String raw = obtenerJsonCrudo(categoryId);
        try {
            JsonNode input = objectMapper.readTree(raw);
            return parsearFicha(input);
        } catch (Exception e) {
            log.warn("ML - Error parseando ficha de categoria {}: {}", categoryId, e.getMessage());
            return new MlFichaDTO(List.of());
        }
    }

    // =====================================================================
    // Parseo puro (package-private para test)
    // =====================================================================

    /**
     * Reorganiza {@code technical_specs/input} en secciones. Método estático y puro:
     * no hace red, no usa Spring, testeable en aislamiento.
     */
    static MlFichaDTO parsearFicha(JsonNode input) {
        List<MlComponenteDTO> variante = new ArrayList<>();
        List<MlComponenteDTO> principales = new ArrayList<>();
        List<MlComponenteDTO> secundarias = new ArrayList<>();

        JsonNode groups = input == null ? null : input.path("groups");
        if (groups != null && groups.isArray()) {
            for (JsonNode group : groups) {
                String groupId = group.path("id").asString("");
                if ("PRICING".equals(groupId)) {
                    continue; // impuestos: auto-gestionados, fuera del formulario
                }
                JsonNode components = group.path("components");
                if (!components.isArray()) {
                    continue;
                }
                for (JsonNode comp : components) {
                    CompParse cp = parsearComponente(comp);
                    if (cp == null) {
                        continue; // sin atributos visibles
                    }
                    if (cp.variante()) {
                        variante.add(cp.dto());
                    } else if ("MAIN".equals(groupId)) {
                        principales.add(cp.dto());
                    } else {
                        secundarias.add(cp.dto());
                    }
                }
            }
        }

        List<MlSeccionDTO> secciones = new ArrayList<>();
        if (!variante.isEmpty()) {
            secciones.add(new MlSeccionDTO("VARIANTE", "Características de la variante", variante));
        }
        if (!principales.isEmpty()) {
            secciones.add(new MlSeccionDTO("PRINCIPALES", "Características principales", principales));
        }
        if (!secundarias.isEmpty()) {
            secciones.add(new MlSeccionDTO("SECUNDARIAS", "Características secundarias", secundarias));
        }
        return new MlFichaDTO(secciones);
    }

    private record CompParse(MlComponenteDTO dto, boolean variante) {}

    private static CompParse parsearComponente(JsonNode comp) {
        String tipo = comp.path("component").asString("");
        boolean esColor = "COLOR_INPUT".equals(tipo);

        List<MlAtributoDefDTO> atributos = new ArrayList<>();
        boolean variante = false;

        JsonNode attrs = comp.path("attributes");
        if (attrs.isArray()) {
            for (JsonNode a : attrs) {
                String id = a.path("id").asString(null);
                if (id == null || id.isBlank()) {
                    continue;
                }
                // Auto-gestionados (excepto BRAND, que sí se muestra).
                if (MlCategoriaAtributoService.AUTOGESTIONADOS.contains(id) && !"BRAND".equals(id)) {
                    continue;
                }
                // En COLOR_INPUT se conservan los atributos aunque estén ocultos:
                // proveen la paleta de colores (rgb) que necesita el swatch.
                if (!esColor && tieneAlgunTag(a, TAGS_OCULTOS)) {
                    continue;
                }
                atributos.add(parsearAtributo(a));
                if (hasTag(a, "allow_variations")) {
                    variante = true;
                }
            }
        }
        if (atributos.isEmpty()) {
            return null;
        }

        JsonNode uc = comp.path("ui_config");
        MlComponenteDTO dto = new MlComponenteDTO(
                tipo,
                comp.path("label").asString(null),
                blankToNull(uc.path("hint").asString(null)),
                blankToNull(uc.path("tooltip").asString(null)),
                blankToNull(uc.path("example").asString(null)),
                uc.path("allow_custom_value").asBoolean(false),
                uc.path("allow_filtering").asBoolean(false),
                atributos);
        return new CompParse(dto, variante);
    }

    private static MlAtributoDefDTO parsearAtributo(JsonNode a) {
        String id = a.path("id").asString(null);
        String name = a.path("name").asString(null);
        String valueType = a.path("value_type").asString(null);

        boolean required = hasTag(a, "required") || hasTag(a, "new_required");
        boolean conditional = hasTag(a, "conditional_required");
        boolean multivalued = hasTag(a, "multivalued");

        List<MlAtributoValorDTO> values = new ArrayList<>();
        JsonNode vs = a.path("values");
        if (vs.isArray()) {
            for (JsonNode v : vs) {
                String rgb = blankToNull(v.path("metadata").path("rgb").asString(null));
                values.add(new MlAtributoValorDTO(v.path("id").asString(null), v.path("name").asString(null), rgb));
            }
        }

        List<String> units = new ArrayList<>();
        JsonNode us = a.path("allowed_units");
        if (us.isArray()) {
            for (JsonNode u : us) {
                String uid = u.path("id").asString(null);
                if (uid != null) {
                    units.add(uid);
                }
            }
        }

        String defaultUnit = a.path("default_unit").asString(null);
        int relevance = a.path("relevance").asInt(0);
        Integer maxLen = a.hasNonNull("value_max_length") ? a.path("value_max_length").asInt() : null;

        // grupo/example/hint quedan en null: la sección la define la jerarquía de la ficha
        // y los textos de ayuda viven en el componente (ui_config).
        return new MlAtributoDefDTO(
                id, name, valueType, values, units, defaultUnit,
                required, conditional, multivalued, null,
                relevance, maxLen, null, null);
    }

    private static boolean hasTag(JsonNode attr, String tag) {
        JsonNode tags = attr.path("tags");
        if (tags.isArray()) {
            for (JsonNode t : tags) {
                if (tag.equals(t.asString(null))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean tieneAlgunTag(JsonNode attr, Set<String> set) {
        JsonNode tags = attr.path("tags");
        if (tags.isArray()) {
            for (JsonNode t : tags) {
                if (set.contains(t.asString(null))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
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
        return raw != null ? raw : "{}";
    }

    private String fetchDesdeApi(String categoryId) {
        verificarTokens();
        String uri = "/categories/" + categoryId + "/technical_specs/input";
        String body = retryHandler.get(uri, () -> tokens.accessToken);
        if (body == null) {
            log.warn("ML - No se pudo obtener la ficha de categoria {}", categoryId);
        }
        return body;
    }

    // =====================================================================
    // Token management (igual patrón que MercadoLibreService)
    // =====================================================================

    private void verificarTokens() {
        if (tokens == null || tokens.isExpired()) {
            cargarTokens();
        }
    }

    private void cargarTokens() {
        try {
            File file = Paths.get(secretsDir).resolve("ml_tokens.json").toFile();
            if (file.exists()) {
                tokens = objectMapper.readValue(file, TokensML.class);
                log.info("ML - FichaService: tokens cargados desde {}", file.getAbsolutePath());
            } else {
                log.warn("ML - FichaService: tokens no encontrados en {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("ML - FichaService: error cargando tokens: {}", e.getMessage());
        }
    }
}

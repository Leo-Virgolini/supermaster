package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.SeoCanal;
import ar.com.leo.super_master_backend.apis.openai.config.OpenAiProperties;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoContexto;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoGeneradoDTO;
import ar.com.leo.super_master_backend.apis.openai.model.OpenAiCredentials;
import ar.com.leo.super_master_backend.dominio.common.exception.ServiceNotConfiguredException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.File;
import java.nio.file.Paths;

/** Genera el SEO de Tienda Nube llamando a la API de chat completions de OpenAI. */
@Slf4j
@Service
public class OpenAiSeoService {

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final SeoConfigService seoConfigService;
    private final SeoUsoService seoUsoService;

    @Value("${app.secrets-dir}")
    private String secretsDir;

    private OpenAiCredentials credentials;

    public OpenAiSeoService(RestClient openaiRestClient, OpenAiProperties properties, ObjectMapper objectMapper,
                            SeoConfigService seoConfigService, SeoUsoService seoUsoService) {
        this.restClient = openaiRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.seoConfigService = seoConfigService;
        this.seoUsoService = seoUsoService;
    }

    @PostConstruct
    public void init() {
        cargarCredenciales();
    }

    /** Genera SEO (title/description/tags) para el canal indicado a partir del contexto del producto. */
    public SeoGeneradoDTO generar(SeoCanal canal, SeoContexto contexto) {
        String apiKey = credentials == null ? null : credentials.getSeoApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new ServiceNotConfiguredException("OPENAI", "OpenAI no configurado");
        }

        String jsonBody = construirBody(canal, contexto);

        String respuesta;
        try {
            respuesta = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("OpenAI - Error llamando a /chat/completions: {}", e.getMessage());
            throw new IllegalStateException("Error al generar SEO con OpenAI: " + e.getMessage(), e);
        }

        try {
            JsonNode root = objectMapper.readTree(respuesta);
            String contenido = root.path("choices").path(0).path("message").path("content").asString();
            SeoGeneradoDTO dto = OpenAiSeoParser.parseContenido(contenido, objectMapper);
            registrarUso(root);
            return dto;
        } catch (Exception e) {
            log.error("OpenAI - Error parseando la respuesta: {}", e.getMessage());
            throw new IllegalStateException("Respuesta de OpenAI no procesable: " + e.getMessage(), e);
        }
    }

    private String construirBody(SeoCanal canal, SeoContexto contexto) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.model());

        ArrayNode messages = body.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", seoConfigService.promptDe(canal));
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", OpenAiSeoPrompts.userMessage(contexto));

        // response_format json_schema (structured outputs): fuerza la forma exacta del JSON.
        ObjectNode responseFormat = body.putObject("response_format");
        responseFormat.put("type", "json_schema");
        ObjectNode jsonSchema = responseFormat.putObject("json_schema");
        jsonSchema.put("name", "seo_nube");
        jsonSchema.put("strict", true);
        ObjectNode schema = jsonSchema.putObject("schema");
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode props = schema.putObject("properties");
        props.putObject("seo_title").put("type", "string");
        props.putObject("seo_description").put("type", "string");
        props.putObject("tags").put("type", "string");
        ArrayNode required = schema.putArray("required");
        required.add("seo_title");
        required.add("seo_description");
        required.add("tags");

        return objectMapper.writeValueAsString(body);
    }

    /** Lee usage.{prompt_tokens, completion_tokens} y registra el consumo. Nunca rompe la generación. */
    private void registrarUso(JsonNode root) {
        try {
            JsonNode usage = root.path("usage");
            long in = usage.path("prompt_tokens").asLong(0);
            long out = usage.path("completion_tokens").asLong(0);
            seoUsoService.registrar(in, out);
        } catch (Exception e) {
            log.warn("OpenAI - no se pudo registrar el uso: {}", e.getMessage());
        }
    }

    private void cargarCredenciales() {
        try {
            File file = Paths.get(secretsDir).resolve(OpenAiCredentials.ARCHIVO).toFile();
            if (file.exists()) {
                credentials = objectMapper.readValue(file, OpenAiCredentials.class);
                log.info("OpenAI - Credenciales cargadas desde {}", file.getAbsolutePath());
                if (credentials != null && (credentials.getSeoApiKey() == null || credentials.getSeoApiKey().isBlank())) {
                    log.warn("OpenAI SEO - seo_api_key vacío o ausente en openai_tokens.json; el SEO fallará al usarse");
                }
            } else {
                log.warn("OpenAI - Archivo de credenciales no encontrado: {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("OpenAI - Error cargando credenciales: {}", e.getMessage());
        }
    }

}

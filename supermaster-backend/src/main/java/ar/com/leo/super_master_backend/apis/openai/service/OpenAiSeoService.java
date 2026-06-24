package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.SeoCanal;
import ar.com.leo.super_master_backend.apis.openai.config.OpenAiProperties;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoContexto;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoGeneradoDTO;
import ar.com.leo.super_master_backend.apis.openai.model.OpenAiCredentials;
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

    @Value("${app.secrets-dir}")
    private String secretsDir;

    private OpenAiCredentials credentials;

    public OpenAiSeoService(RestClient openaiRestClient, OpenAiProperties properties, ObjectMapper objectMapper) {
        this.restClient = openaiRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        cargarCredenciales();
    }

    /** Genera SEO (title/description/tags) para el canal indicado a partir del contexto del producto. */
    public SeoGeneradoDTO generar(SeoCanal canal, SeoContexto contexto) {
        String apiKey = credentials == null ? null : credentials.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI no configurado");
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
            return OpenAiSeoParser.parseContenido(contenido, objectMapper);
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
        system.put("content", OpenAiSeoPrompts.systemPrompt(canal));
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", OpenAiSeoPrompts.userMessage(contexto));

        ObjectNode responseFormat = body.putObject("response_format");
        responseFormat.put("type", "json_object");

        return objectMapper.writeValueAsString(body);
    }

    private void cargarCredenciales() {
        try {
            File file = Paths.get(secretsDir).resolve("openai_tokens.json").toFile();
            if (file.exists()) {
                credentials = objectMapper.readValue(file, OpenAiCredentials.class);
                log.info("OpenAI - Credenciales cargadas desde {}", file.getAbsolutePath());
            } else {
                log.warn("OpenAI - Archivo de credenciales no encontrado: {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("OpenAI - Error cargando credenciales: {}", e.getMessage());
        }
    }

    public boolean isConfigured() {
        return credentials != null && credentials.getApiKey() != null && !credentials.getApiKey().isBlank();
    }
}

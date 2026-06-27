package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.config.OpenAiImageProperties;
import ar.com.leo.super_master_backend.apis.openai.model.OpenAiCredentials;
import ar.com.leo.super_master_backend.dominio.common.exception.ServiceNotConfiguredException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Paths;
import java.util.Base64;

/** Genera la carátula llamando a gpt-image-2 (/images/edits) con credencial propia. Devuelve el JPG tal cual. */
@Slf4j
@Service
public class OpenAiImagenService {

    private final RestClient restClient;
    private final OpenAiImageProperties properties;
    private final ImagenIaConfigService configService;
    private final ImagenUsoService usoService;
    private final ObjectMapper objectMapper;
    private final String secretsDir;
    private OpenAiCredentials credentials;

    public OpenAiImagenService(@Qualifier("openaiImageRestClient") RestClient restClient,
                               OpenAiImageProperties properties,
                               ImagenIaConfigService configService,
                               ImagenUsoService usoService,
                               ObjectMapper objectMapper,
                               @Value("${app.secrets-dir}") String secretsDir) {
        this.restClient = restClient;
        this.properties = properties;
        this.configService = configService;
        this.usoService = usoService;
        this.objectMapper = objectMapper;
        this.secretsDir = secretsDir;
    }

    @PostConstruct
    void init() {
        try {
            File file = Paths.get(secretsDir).resolve("openai_tokens.json").toFile();
            if (file.exists()) {
                credentials = objectMapper.readValue(file, OpenAiCredentials.class);
                log.info("OpenAI imágenes - credenciales cargadas desde {}", file.getAbsolutePath());
                if (credentials != null && (credentials.getImageApiKey() == null || credentials.getImageApiKey().isBlank())) {
                    log.warn("OpenAI imágenes - image_api_key vacío o ausente en openai_tokens.json; la carátula fallará al usarse");
                }
            } else {
                log.warn("OpenAI imágenes - credenciales no encontradas: {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("OpenAI imágenes - error cargando credenciales: {}", e.getMessage());
        }
    }

    /** Key de imágenes: image_api_key (sin fallback al SEO). */
    private String apiKey() {
        if (credentials == null) return null;
        return credentials.getImageApiKey();
    }

    /** Edita la imagen cruda con fondo blanco vía OpenAI y devuelve la carátula JPG 1024×1024 (tal cual de gpt). */
    public byte[] generarCaratula(byte[] cruda, String filename) {
        String apiKey = apiKey();
        if (apiKey == null || apiKey.isBlank())
            throw new ServiceNotConfiguredException("OPENAI_IMAGE",
                    "Falta image_api_key en openai_tokens.json");

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("model", properties.model());
        parts.add("prompt", configService.prompt());
        parts.add("size", "1024x1024");
        parts.add("output_format", "jpeg");
        parts.add("quality", "high");
        parts.add("image", new ByteArrayResource(cruda) {
            @Override
            public String getFilename() { return filename; }
        });

        String resp;
        try {
            resp = restClient.post()
                    .uri("/images/edits")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("OpenAI imágenes - error llamando a /images/edits: {}", e.getMessage());
            throw new IllegalStateException("Error al generar carátula con OpenAI: " + e.getMessage(), e);
        }

        try {
            JsonNode root = objectMapper.readTree(resp);
            String b64 = OpenAiImagenParser.b64(root);
            if (b64 == null) throw new IllegalStateException("OpenAI no devolvió la imagen");
            usoService.registrar(OpenAiImagenParser.tokensEntrada(root), OpenAiImagenParser.tokensSalida(root));
            return Base64.getDecoder().decode(b64); // ya es JPG (output_format=jpeg)
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI imágenes - error parseando la respuesta: {}", e.getMessage());
            throw new IllegalStateException("Respuesta de OpenAI no procesable: " + e.getMessage(), e);
        }
    }
}

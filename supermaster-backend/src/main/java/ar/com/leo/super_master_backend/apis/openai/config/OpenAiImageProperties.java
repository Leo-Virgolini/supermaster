package ar.com.leo.super_master_backend.apis.openai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "openai.image")
public record OpenAiImageProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
    public OpenAiImageProperties {
        if (baseUrl == null) baseUrl = "https://api.openai.com/v1";
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(10);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(120);
    }
}

package ar.com.leo.super_master_backend.apis.ml.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "mercadolibre")
public record MercadoLibreProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        double rateLimitPerSecond,
        long retryBaseWaitMs
) {
    public MercadoLibreProperties {
        // Valores por defecto
        if (baseUrl == null) {
            baseUrl = "https://api.mercadolibre.com";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(10);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(30);
        }
        if (rateLimitPerSecond <= 0) {
            rateLimitPerSecond = 5.0; // 18000 RPH = 300 RPM = 5 req/s
        }
        if (retryBaseWaitMs <= 0) {
            retryBaseWaitMs = 2000L;
        }
    }
}

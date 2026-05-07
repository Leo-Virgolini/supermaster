package ar.com.leo.super_master_backend.apis.dux.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "dux")
public record DuxProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        double rateLimitPerSecond,
        int itemsPerPage
) {
    public DuxProperties {
        if (baseUrl == null) {
            baseUrl = "https://erp.duxsoftware.com.ar/WSERP/rest/services";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(10);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(30);
        }
        if (rateLimitPerSecond <= 0) {
            rateLimitPerSecond = 1.0 / 7.0; // 1 request cada 7 segundos
        }
        if (itemsPerPage <= 0) {
            itemsPerPage = 50; // Máximo permitido por DUX
        }
    }
}

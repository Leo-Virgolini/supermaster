package ar.com.leo.super_master_backend.apis.nube.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "nube")
public record NubeProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        double rateLimitPerSecond,
        String userAgent
) {
    public NubeProperties {
        if (baseUrl == null) {
            baseUrl = "https://api.tiendanube.com/v1";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(10);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(30);
        }
        if (rateLimitPerSecond <= 0) {
            rateLimitPerSecond = 2.0; // Tiendanube permite más que DUX
        }
        if (userAgent == null) {
            userAgent = "SuperMaster";
        }
    }
}

package ar.com.leo.super_master_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        long accessTokenExpirationMs
) {
}

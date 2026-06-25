package ar.com.leo.super_master_backend.apis.openai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
        String baseUrl,
        String model,
        Duration connectTimeout,
        Duration readTimeout,
        BigDecimal precioInput1m,
        BigDecimal precioOutput1m
) {
    public OpenAiProperties {
        if (baseUrl == null) {
            baseUrl = "https://api.openai.com/v1";
        }
        if (model == null) {
            model = "gpt-5-mini";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(10);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(60);
        }
        if (precioInput1m == null) {
            precioInput1m = new BigDecimal("0.25");
        }
        if (precioOutput1m == null) {
            precioOutput1m = new BigDecimal("2.00");
        }
    }
}

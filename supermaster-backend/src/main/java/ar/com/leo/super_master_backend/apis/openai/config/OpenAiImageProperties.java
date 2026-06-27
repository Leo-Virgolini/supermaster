package ar.com.leo.super_master_backend.apis.openai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

@ConfigurationProperties(prefix = "openai.image")
public record OpenAiImageProperties(
        String baseUrl, String model,
        Duration connectTimeout, Duration readTimeout,
        BigDecimal precioInput1m, BigDecimal precioOutput1m,
        String size, String outputFormat, String quality
) {
    public OpenAiImageProperties {
        if (baseUrl == null) baseUrl = "https://api.openai.com/v1";
        if (model == null) model = "gpt-image-2";
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(10);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(120);
        if (precioInput1m == null) precioInput1m = new BigDecimal("5.00");
        if (precioOutput1m == null) precioOutput1m = new BigDecimal("40.00");
        if (size == null) size = "1024x1024";
        if (outputFormat == null) outputFormat = "jpeg";
        if (quality == null) quality = "high";
    }
}

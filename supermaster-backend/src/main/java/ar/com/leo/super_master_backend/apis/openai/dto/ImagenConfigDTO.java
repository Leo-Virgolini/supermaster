package ar.com.leo.super_master_backend.apis.openai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ImagenConfigDTO(
        String contenido,
        String model,
        String size,
        String outputFormat,
        String quality,
        BigDecimal precioInput1m,
        BigDecimal precioOutput1m,
        LocalDateTime fechaModificacion) {}

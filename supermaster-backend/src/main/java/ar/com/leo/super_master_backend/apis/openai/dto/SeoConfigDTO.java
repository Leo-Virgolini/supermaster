package ar.com.leo.super_master_backend.apis.openai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SeoConfigDTO(
        String promptHogar,
        String promptGastro,
        String model,
        BigDecimal precioInput1m,
        BigDecimal precioOutput1m,
        LocalDateTime fechaModificacion) {}

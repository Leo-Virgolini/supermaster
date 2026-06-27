package ar.com.leo.super_master_backend.apis.openai.dto;

import java.math.BigDecimal;

public record ImagenUsoDTO(long consultas, long tokensEntrada, long tokensSalida, BigDecimal costoUsd,
                           String modelo, BigDecimal precioInput1m, BigDecimal precioOutput1m) {}

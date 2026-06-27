package ar.com.leo.super_master_backend.apis.openai.dto;

import jakarta.validation.constraints.NotBlank;

public record CaratulaGuardarDTO(@NotBlank String imagenBase64) {}

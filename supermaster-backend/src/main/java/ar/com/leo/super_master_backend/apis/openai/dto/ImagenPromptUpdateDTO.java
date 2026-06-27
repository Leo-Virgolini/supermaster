package ar.com.leo.super_master_backend.apis.openai.dto;

import jakarta.validation.constraints.NotBlank;

public record ImagenPromptUpdateDTO(@NotBlank(message = "El contenido del prompt es obligatorio") String contenido) {}

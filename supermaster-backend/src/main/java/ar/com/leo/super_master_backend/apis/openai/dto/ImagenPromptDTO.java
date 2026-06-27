package ar.com.leo.super_master_backend.apis.openai.dto;

import java.time.LocalDateTime;

public record ImagenPromptDTO(String contenido, LocalDateTime fechaModificacion) {}

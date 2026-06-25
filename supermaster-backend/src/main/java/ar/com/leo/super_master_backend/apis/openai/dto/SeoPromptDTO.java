package ar.com.leo.super_master_backend.apis.openai.dto;

import ar.com.leo.super_master_backend.apis.openai.SeoCanal;
import java.time.LocalDateTime;

public record SeoPromptDTO(SeoCanal canal, String contenido, LocalDateTime fechaModificacion) {}

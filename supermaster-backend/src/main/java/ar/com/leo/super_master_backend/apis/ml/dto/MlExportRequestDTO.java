package ar.com.leo.super_master_backend.apis.ml.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record MlExportRequestDTO(
        List<String> skus,
        @NotNull(message = "La cuota es obligatoria") Integer cuotas) {}

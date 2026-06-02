package ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CatalogoPdfConfigCreateDTO(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede exceder 120 caracteres")
        String nombre,

        @NotNull(message = "El canal es obligatorio")
        Integer canalId,

        @NotNull(message = "El catálogo es obligatorio")
        Integer catalogoId,

        @NotNull(message = "Las cuotas son obligatorias")
        Integer cuotas,

        List<String> ordenarPor,

        Integer clasifGralId,

        Integer tipoId,

        Integer marcaId,

        @Size(max = 50, message = "El tag no puede exceder 50 caracteres")
        String tag,

        @NotNull(message = "Carátula es obligatoria")
        Boolean caratula,

        @Size(max = 150, message = "El título no puede exceder 150 caracteres")
        String titulo,

        @Size(max = 50, message = "La estética no puede exceder 50 caracteres")
        String estetica,

        @Size(max = 50, message = "El tipo de documento no puede exceder 50 caracteres")
        String tipoDocumento,

        @NotNull(message = "Productos por página es obligatorio")
        Integer productosPorPagina,

        @NotBlank(message = "La ubicación de salida es obligatoria")
        @Size(max = 255, message = "La ubicación no puede exceder 255 caracteres")
        String ubicacionSalida,

        @NotNull(message = "Activo es obligatorio")
        Boolean activo
) {}

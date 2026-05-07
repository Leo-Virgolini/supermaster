package ar.com.leo.super_master_backend.excel.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ExportFiltrosDTO(
        @NotNull(message = "El canal es requerido")
        @Positive(message = "El ID de canal debe ser positivo")
        Integer canalId,
        
        @NotNull(message = "El catálogo es requerido")
        @Positive(message = "El ID de catálogo debe ser positivo")
        Integer catalogoId,
        
        @Positive(message = "El ID de clasificación general debe ser positivo")
        Integer clasifGralId
) {
}


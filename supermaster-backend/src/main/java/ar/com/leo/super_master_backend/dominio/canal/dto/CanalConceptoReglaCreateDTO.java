package ar.com.leo.super_master_backend.dominio.canal.dto;

import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CanalConceptoReglaCreateDTO(
        @NotNull(message = "El ID del canal es obligatorio")
        @Positive(message = "El ID del canal debe ser positivo")
        Integer canalId,

        @NotNull(message = "El ID del concepto es obligatorio")
        @Positive(message = "El ID del concepto debe ser positivo")
        Integer conceptoId,

        @NotBlank(message = "El tipo de regla es obligatorio")
        @Pattern(regexp = "INCLUIR|EXCLUIR",
                 message = "tipoRegla debe ser uno de: INCLUIR, EXCLUIR")
        String tipoRegla,

        @Positive(message = "El ID del tipo debe ser positivo")
        Integer tipoId,

        @Positive(message = "El ID de clasificación gastro debe ser positivo")
        Integer clasifGastroId,

        @Positive(message = "El ID de clasificación general debe ser positivo")
        Integer clasifGralId,

        @Positive(message = "El ID de marca debe ser positivo")
        Integer marcaId,

        Tag tag,

        Boolean tieneEnvio
) {
}


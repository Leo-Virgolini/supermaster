package ar.com.leo.super_master_backend.dominio.canal.dto;

import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CanalReglaUpdateDTO(
        @Positive(message = "El ID del canal debe ser positivo")
        Integer canalId,

        @Pattern(regexp = "INCLUIR|EXCLUIR",
                 message = "tipoRegla debe ser uno de: INCLUIR, EXCLUIR")
        String tipoRegla,

        Tag tag,

        @Positive(message = "El ID del tipo debe ser positivo")
        Integer tipoId,

        @Positive(message = "El ID de marca debe ser positivo")
        Integer marcaId,

        @Positive(message = "El ID de clasificación general debe ser positivo")
        Integer clasifGralId,

        @Positive(message = "El ID de clasificación gastro debe ser positivo")
        Integer clasifGastroId,

        @Positive(message = "El ID del producto debe ser positivo")
        Integer productoId,

        Boolean tieneEnvio
) {
}

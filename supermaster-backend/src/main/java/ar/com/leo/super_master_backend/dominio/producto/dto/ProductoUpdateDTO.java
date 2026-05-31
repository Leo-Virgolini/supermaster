package ar.com.leo.super_master_backend.dominio.producto.dto;

import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;
import ar.com.leo.super_master_backend.dominio.reposicion.entity.TagReposicion;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductoUpdateDTO(
        @Size(max = 45, message = "El SKU no puede exceder 45 caracteres")
        String sku,
        @Size(max = 45, message = "El código externo no puede exceder 45 caracteres")
        String codExt,
        @Size(max = 100, message = "La descripción no puede exceder 100 caracteres")
        String descripcion,
        @Size(max = 100, message = "El título web no puede exceder 100 caracteres")
        String tituloWeb,
        Boolean esCombo,
        @Positive(message = "UXB debe ser mayor a 0")
        Integer uxb,
        @Positive(message = "MOQ debe ser mayor a 0")
        Integer moq,
        @Size(max = 500, message = "La URL de imagen no puede exceder 500 caracteres")
        String imagenUrl,
        @PositiveOrZero(message = "El stock debe ser mayor o igual a 0")
        Integer stock,
        Boolean activo,

        @Positive(message = "El ID de marca debe ser positivo")
        Integer marcaId,
        @Positive(message = "El ID de origen debe ser positivo")
        Integer origenId,
        @Positive(message = "El ID de clasificación general debe ser positivo")
        Integer clasifGralId,
        @Positive(message = "El ID de clasificación gastro debe ser positivo")
        Integer clasifGastroId,
        @Positive(message = "El ID de tipo debe ser positivo")
        Integer tipoId,
        @Positive(message = "El ID de proveedor debe ser positivo")
        Integer proveedorId,
        @Positive(message = "El ID de material debe ser positivo")
        Integer materialId,
        @Positive(message = "El ID de MLA debe ser positivo")
        Integer mlaId,

        @Size(max = 45, message = "La capacidad no puede exceder 45 caracteres")
        String capacidad,
        @Size(max = 45, message = "El largo no puede exceder 45 caracteres")
        String largo,
        @Size(max = 45, message = "El ancho no puede exceder 45 caracteres")
        String ancho,
        @Size(max = 45, message = "El alto no puede exceder 45 caracteres")
        String alto,
        @Size(max = 45, message = "El diámetro de boca no puede exceder 45 caracteres")
        String diamboca,
        @Size(max = 45, message = "El diámetro de base no puede exceder 45 caracteres")
        String diambase,
        @Size(max = 45, message = "El espesor no puede exceder 45 caracteres")
        String espesor,

        @PositiveOrZero(message = "El costo debe ser mayor o igual a 0")
        BigDecimal costo,
        @DecimalMin(value = "0.0", inclusive = true, message = "El IVA debe ser mayor o igual a 0")
        @DecimalMax(value = "100.0", inclusive = true, message = "El IVA debe ser menor o igual a 100")
        BigDecimal iva,

        TagReposicion tagReposicion,
        Tag tag
) {
}

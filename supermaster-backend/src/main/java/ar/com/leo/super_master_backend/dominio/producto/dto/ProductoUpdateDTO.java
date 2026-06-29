package ar.com.leo.super_master_backend.dominio.producto.dto;

import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;
import ar.com.leo.super_master_backend.dominio.reposicion.entity.TagReposicion;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductoUpdateDTO(
        @Size(max = 45, message = "El SKU no puede exceder 45 caracteres")
        String sku,
        @Size(max = 45, message = "El código externo no puede exceder 45 caracteres")
        String codExt,
        @Size(max = 100, message = "El título Dux no puede exceder 100 caracteres")
        String tituloDux,
        @Size(max = 100, message = "El título ML no puede exceder 100 caracteres")
        String tituloMl,
        @Size(max = 100, message = "El título Nube no puede exceder 100 caracteres")
        String tituloNube,
        Boolean esCombo,
        @Positive(message = "UXB debe ser mayor a 0")
        Integer uxb,
        @Positive(message = "MOQ debe ser mayor a 0")
        Integer moq,
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
        @Positive(message = "El ID de sector de depósito debe ser positivo")
        Integer sectorDepositoId,

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
        Tag tag,

        @PositiveOrZero(message = "El alto del paquete ML debe ser mayor o igual a 0")
        BigDecimal mlPaqAlto,
        @PositiveOrZero(message = "El ancho del paquete ML debe ser mayor o igual a 0")
        BigDecimal mlPaqAncho,
        @PositiveOrZero(message = "El largo del paquete ML debe ser mayor o igual a 0")
        BigDecimal mlPaqLargo,
        @PositiveOrZero(message = "El peso del paquete ML debe ser mayor o igual a 0")
        BigDecimal mlPaqPeso,

        @Size(max = 20, message = "El EAN no puede exceder 20 caracteres")
        String ean
) {
}

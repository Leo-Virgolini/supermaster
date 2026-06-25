package ar.com.leo.super_master_backend.dominio.producto.dto;

import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;
import ar.com.leo.super_master_backend.dominio.reposicion.entity.TagReposicion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ProductoDTO(
        Integer id,
        String sku,
        String codExt,
        String tituloDux,
        String tituloMl,
        String tituloNube,
        Boolean esCombo,
        Integer uxb,
        Integer moq,
        Integer stock,
        Boolean activo,
        TagReposicion tagReposicion,
        Tag tag,

        // Relaciones: solo IDs
        Integer marcaId,
        Integer origenId,
        Integer clasifGralId,
        Integer clasifGastroId,
        Integer tipoId,
        Integer proveedorId,
        Integer materialId,
        Integer mlaId,
        String mlaNombre,
        Integer sectorDepositoId,

        // Paths jerárquicos completos "ABUELO > PADRE > HIJO" para mostrar la
        // herencia en la tabla sin un fetch por celda en el frontend.
        String marcaNombreCompleto,
        String tipoNombreCompleto,
        String clasifGralNombreCompleto,
        String clasifGastroNombreCompleto,

        // Atributos extra
        String capacidad,
        String largo,
        String ancho,
        String alto,
        String diamboca,
        String diambase,
        String espesor,
        BigDecimal costo,
        LocalDateTime fechaUltimoCosto,
        BigDecimal iva,

        // Fechas
        LocalDateTime fechaCreacion,
        LocalDateTime fechaModificacion,

        // Many-to-many (nombres)
        List<String> aptos,
        List<String> catalogos,
        List<String> clientes,

        // Márgenes (de producto_margen, expuestos para edición inline en la tabla)
        BigDecimal margenMinorista,
        BigDecimal margenMayorista,

        // Categoría de Mercado Libre (predictor)
        String mlCategoryId,
        String mlCategoryNombre,

        // Dimensiones del paquete de envío para Mercado Libre
        BigDecimal mlPaqAlto,
        BigDecimal mlPaqAncho,
        BigDecimal mlPaqLargo,
        BigDecimal mlPaqPeso,

        // Código universal de producto (EAN/GTIN)
        String ean
) {
}

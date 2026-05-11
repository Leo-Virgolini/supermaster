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
        String descripcion,
        String tituloWeb,
        Boolean esCombo,
        Boolean esMaquina,
        Integer uxb,
        Integer moq,
        String imagenUrl,
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

        // Atributos extra
        String capacidad,
        BigDecimal largo,
        BigDecimal ancho,
        BigDecimal alto,
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
        BigDecimal margenFijoMinorista,
        BigDecimal margenFijoMayorista
) {
}
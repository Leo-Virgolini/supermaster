package ar.com.leo.super_master_backend.dominio.producto.estado.dto;

/** Una variante (producto hermano) de una familia de variantes ML. */
public record FamiliaVarianteDTO(
        Integer productoId,
        String sku,
        String titulo,
        boolean esActual
) {}

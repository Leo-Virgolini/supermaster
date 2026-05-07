package ar.com.leo.super_master_backend.dominio.producto.dto;

public record ProductoResumenDTO(
        Integer id,
        String sku,
        String descripcion,
        String tituloWeb
) {
}

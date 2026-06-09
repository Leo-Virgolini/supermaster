package ar.com.leo.super_master_backend.dominio.producto.dto;

/**
 * Respuesta del endpoint que sugiere el próximo SKU libre.
 * {@code sku} es null cuando el rango está completo (sin SKU disponible).
 */
public record ProductoSiguienteSkuDTO(String sku) {
}

package ar.com.leo.super_master_backend.dominio.producto.mla.dto;

/**
 * Resultado de traer un MLA desde MercadoLibre por su código: el MLA ya
 * persistido (con envío/comisión) y si la publicación es de catálogo (para avisar).
 */
public record MlaDesdeMlDTO(MlaDTO mla, boolean esCatalogo) {
}

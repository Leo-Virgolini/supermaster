package ar.com.leo.super_master_backend.catalogo_pdf.pdf;

import java.math.BigDecimal;

/**
 * Snapshot mínimo de un producto para renderizar en el PDF, desacoplado
 * de la entidad JPA. Lo arma el service a partir de Producto + ProductoCanalPrecio.
 */
public record CatalogoPdfItem(
        String sku,
        String nombre,
        BigDecimal precio,
        Integer uxb,
        String imagenUrl,
        String marca,
        String tipo
) {
}

package ar.com.leo.super_master_backend.dominio.producto.estado.dto;

/** Una variante (producto hermano) de una familia de variantes ML. Los campos leídos de ML
 *  (ejeValor/stock/status/variationId) pueden ser null si no se pudo leer o no aplican. */
public record FamiliaVarianteDTO(
        Integer productoId,
        String sku,
        String titulo,
        boolean esActual,
        String ejeValor,   // valor del atributo que varía (ej. "Plateado"), leído de ML
        Integer stock,     // available_quantity en ML
        String status,     // active/paused/closed…
        Long variationId   // solo modelo legacy (variations[])
) {}

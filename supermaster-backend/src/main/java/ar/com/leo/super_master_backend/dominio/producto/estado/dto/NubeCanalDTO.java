package ar.com.leo.super_master_backend.dominio.producto.estado.dto;

/** Estado + datos editables de una tienda Nube leídos del canal (no persistidos). */
public record NubeCanalDTO(
        EstadoCanalDTO estado,
        String descripcion,
        SeoCanalDTO seo,
        String titulo,
        String peso,
        String profundidad,
        String ancho,
        String alto,
        Long productId
) {}

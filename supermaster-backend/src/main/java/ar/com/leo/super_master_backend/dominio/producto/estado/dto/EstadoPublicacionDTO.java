package ar.com.leo.super_master_backend.dominio.producto.estado.dto;

/** Respuesta del GET: estado + snapshot de cada canal. */
public record EstadoPublicacionDTO(
        EstadoCanalDTO ml,
        EstadoCanalDTO hogar,
        EstadoCanalDTO gastro
) {}

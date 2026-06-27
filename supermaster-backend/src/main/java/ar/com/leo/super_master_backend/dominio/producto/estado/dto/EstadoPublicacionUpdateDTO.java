package ar.com.leo.super_master_backend.dominio.producto.estado.dto;

/** Body del PUT: estado deseado por canal; null = no tocar ese canal. */
public record EstadoPublicacionUpdateDTO(
        String ml,        // "active" | "paused" | null
        Boolean hogar,    // true=visible / false=oculta / null
        Boolean gastro
) {}

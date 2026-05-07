package ar.com.leo.super_master_backend.dominio.config_automatizacion.dto;

public record ConfigAutomatizacionDTO(
        Integer id,
        String clave,
        String valor,
        String descripcion
) {}

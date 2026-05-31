package ar.com.leo.super_master_backend.excel.dto;

import java.util.List;

/**
 * Body opcional para POST /api/excel/limpiar-datos.
 * Si {@code tablas} es null o vacío, se limpian todas las tablas elegibles
 * (comportamiento por defecto preservando compat con la API previa).
 * Si trae nombres, solo se vacían esos (cada uno validado contra el whitelist).
 */
public record LimpiezaDatosRequestDTO(
        List<String> tablas
) {
}

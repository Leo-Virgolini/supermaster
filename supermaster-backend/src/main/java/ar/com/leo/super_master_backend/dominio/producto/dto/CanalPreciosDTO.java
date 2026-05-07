package ar.com.leo.super_master_backend.dominio.producto.dto;

import java.util.List;

/**
 * DTO que agrupa los precios de un producto por canal.
 * Evita repetir canalId y canalNombre para cada cuota.
 */
public record CanalPreciosDTO(
        Integer canalId,
        String canalNombre,
        List<PrecioDTO> precios
) {
}

package ar.com.leo.super_master_backend.dominio.campania.dto;

import java.util.List;

public record SincronizacionResultadoDTO(
        int categoriasImportadas,
        int productosVinculados,
        List<String> skusSinMatch
) {
}

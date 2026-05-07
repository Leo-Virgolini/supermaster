package ar.com.leo.super_master_backend.dominio.canal.dto;

import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;

public record CanalReglaDTO(
        Long id,
        Integer canalId,
        String tipoRegla,
        Tag tag,
        Integer tipoId,
        Integer marcaId,
        Integer clasifGralId,
        Integer clasifGastroId,
        Integer productoId,
        Boolean tieneEnvio
) {
}

package ar.com.leo.super_master_backend.dominio.reposicion.dto;

import java.util.List;

public record ReposicionResultDTO(
        List<SugerenciaReposicionDTO> sugerencias,
        int totalProductos,
        int productosConSugerencia,
        List<String> advertencias
) {
}

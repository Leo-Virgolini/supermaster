package ar.com.leo.super_master_backend.catalogo_pdf.dto;

import java.util.List;

public record GenerarTodosResultDTO(
        int total,
        int exitosos,
        int fallidos,
        List<GenerarItemResultDTO> resultados
) {
    public record GenerarItemResultDTO(
            Integer id,
            String nombre,
            boolean exito,
            int productosExportados,
            String rutaGuardada,
            String error,
            List<String> productosSinImagen
    ) {}
}

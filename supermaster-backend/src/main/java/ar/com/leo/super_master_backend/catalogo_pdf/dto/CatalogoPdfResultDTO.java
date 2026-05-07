package ar.com.leo.super_master_backend.catalogo_pdf.dto;

import java.util.List;

public record CatalogoPdfResultDTO(
        byte[] archivo,
        String nombreArchivo,
        int productosExportados,
        String rutaGuardada,
        List<String> productosSinImagen
) {
    public CatalogoPdfResultDTO(byte[] archivo, String nombreArchivo, int productosExportados, String rutaGuardada) {
        this(archivo, nombreArchivo, productosExportados, rutaGuardada, List.of());
    }

    public CatalogoPdfResultDTO(byte[] archivo, String nombreArchivo, int productosExportados) {
        this(archivo, nombreArchivo, productosExportados, null, List.of());
    }
}

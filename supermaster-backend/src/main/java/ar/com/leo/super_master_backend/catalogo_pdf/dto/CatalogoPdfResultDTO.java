package ar.com.leo.super_master_backend.catalogo_pdf.dto;

import java.util.List;

/**
 * @param productosSinImagen lista de SKUs cuyo archivo de imagen NO se encontró (no incluye corruptos/erróneos)
 * @param imagenesEnBlanco   cantidad de imágenes 100% blancas detectadas
 * @param imagenesNoLeidas   cantidad de archivos imagen que ImageIO no pudo decodificar (corruptos/inválidos)
 * @param erroresImagen      cantidad de excepciones procesando imágenes
 */
public record CatalogoPdfResultDTO(
        byte[] archivo,
        String nombreArchivo,
        int productosExportados,
        String rutaGuardada,
        List<String> productosSinImagen,
        int imagenesEnBlanco,
        int imagenesNoLeidas,
        int erroresImagen
) {
    public CatalogoPdfResultDTO(byte[] archivo, String nombreArchivo, int productosExportados, String rutaGuardada, List<String> productosSinImagen) {
        this(archivo, nombreArchivo, productosExportados, rutaGuardada, productosSinImagen, 0, 0, 0);
    }

    public CatalogoPdfResultDTO(byte[] archivo, String nombreArchivo, int productosExportados, String rutaGuardada) {
        this(archivo, nombreArchivo, productosExportados, rutaGuardada, List.of(), 0, 0, 0);
    }

    public CatalogoPdfResultDTO(byte[] archivo, String nombreArchivo, int productosExportados) {
        this(archivo, nombreArchivo, productosExportados, null, List.of(), 0, 0, 0);
    }
}

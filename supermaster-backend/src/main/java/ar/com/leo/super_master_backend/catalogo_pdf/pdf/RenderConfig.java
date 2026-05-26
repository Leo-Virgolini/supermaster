package ar.com.leo.super_master_backend.catalogo_pdf.pdf;

/**
 * Configuración inmutable de qué columnas se renderizan y con qué fuente/color.
 * Se arma una vez por request en el service y se pasa al {@link CellBuilder}.
 */
public record RenderConfig(
        boolean mostrarCodigo, float fontSizeCodigo, String colorCodigo,
        boolean mostrarNombre, float fontSizeNombre, String colorNombre,
        boolean mostrarPrecio, float fontSizePrecio, String colorPrecio,
        boolean mostrarUxb, float fontSizeUxb, String colorUxb
) {
}

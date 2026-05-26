package ar.com.leo.super_master_backend.catalogo_pdf.pdf.stats;

import java.util.ArrayList;
import java.util.List;

/**
 * Estadísticas mutables que se acumulan durante la generación del PDF.
 * Pasa por todos los componentes para que cada uno reporte su categoría
 * de error/éxito (imagen en blanco, no leída, error, etc.).
 */
public class PdfGenerationStats {

    public int productosGenerados;
    public int imagenesEnBlanco;
    public int imagenesNoLeidas;
    public int erroresImagen;

    public final List<String> productosSinImagen = new ArrayList<>();
}

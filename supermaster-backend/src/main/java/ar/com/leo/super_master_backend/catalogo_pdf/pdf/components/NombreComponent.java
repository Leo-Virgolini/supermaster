package ar.com.leo.super_master_backend.catalogo_pdf.pdf.components;

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;

public final class NombreComponent {

    private NombreComponent() {}

    public static Paragraph build(String nombreValue, float fontSize, Color color) {
        String nombre = (nombreValue == null || nombreValue.isBlank()) ? "[SIN NOMBRE]" : nombreValue.trim();
        return new Paragraph(nombre)
                .simulateBold()
                .setFontSize(fontSize)
                .setFontColor(color)
                .setMultipliedLeading(1f)
                .setTextAlignment(TextAlignment.CENTER)
                .setMargin(0)
                .setPadding(2);
    }
}

package ar.com.leo.super_master_backend.catalogo_pdf.pdf.components;

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;

public final class UxbComponent {

    private UxbComponent() {}

    public static Paragraph build(Integer uxbValue, float fontSize, Color color) {
        String value = uxbValue == null ? "--" : String.valueOf(uxbValue);
        Text valorUxb = new Text(value).simulateBold();
        return new Paragraph("UxB: ")
                .add(valorUxb)
                .setFontSize(fontSize)
                .setFontColor(color)
                .setMultipliedLeading(1f)
                .setTextAlignment(TextAlignment.CENTER)
                .setMargin(0)
                .setPadding(1);
    }
}

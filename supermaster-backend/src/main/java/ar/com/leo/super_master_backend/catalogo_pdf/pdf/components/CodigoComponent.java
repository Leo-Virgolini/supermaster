package ar.com.leo.super_master_backend.catalogo_pdf.pdf.components;

import ar.com.leo.super_master_backend.catalogo_pdf.pdf.theme.ThemeSpec;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.BorderRadius;
import com.itextpdf.layout.properties.HorizontalAlignment;

public final class CodigoComponent {

    private CodigoComponent() {}

    public static Paragraph build(String codigoValue,
                                  ThemeSpec theme,
                                  float availableWidthSpace,
                                  float fontSize,
                                  Color color) {
        String codigo = (codigoValue == null || codigoValue.isBlank()) ? "[SIN CÓDIGO]" : codigoValue.trim();
        if (codigo.matches("^\\d+\\.0$")) {
            codigo = codigo.substring(0, codigo.length() - 2);
        }
        return new Paragraph(codigo)
                .setMargin(0)
                .setMarginBottom(2)
                .setFontSize(fontSize)
                .simulateBold()
                .setFontColor(color)
                .setBorderRadius(new BorderRadius(10))
                .setBackgroundColor(theme.codeBackgroundColor())
                .setWidth(availableWidthSpace)
                .setHorizontalAlignment(HorizontalAlignment.CENTER);
    }
}

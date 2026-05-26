package ar.com.leo.super_master_backend.catalogo_pdf.pdf.components;

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class PrecioComponent {

    private PrecioComponent() {}

    public static Paragraph build(BigDecimal precioValue, float fontSize, Color color) {
        Text precioBold = new Text(formatMoney(precioValue)).simulateBold();
        return new Paragraph("PRECIO: \n")
                .add(precioBold)
                .setFontSize(fontSize)
                .setFontColor(color)
                .setMultipliedLeading(1f)
                .setPadding(1)
                .setTextAlignment(TextAlignment.CENTER)
                .setMargin(0);
    }

    private static String formatMoney(BigDecimal value) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(new Locale("es", "AR"));
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('.');
        DecimalFormat formatter = new DecimalFormat("$#,##0.00", symbols);
        return formatter.format(value != null ? value : BigDecimal.ZERO);
    }
}

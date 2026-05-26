package ar.com.leo.super_master_backend.catalogo_pdf.pdf.components;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Paragraph;

public final class SeparatorComponent {

    private SeparatorComponent() {}

    public static Paragraph build() {
        return new Paragraph()
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                .setMargin(0)
                .setPadding(0);
    }
}

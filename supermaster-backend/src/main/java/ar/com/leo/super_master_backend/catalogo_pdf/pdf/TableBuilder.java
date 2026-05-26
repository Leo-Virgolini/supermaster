package ar.com.leo.super_master_backend.catalogo_pdf.pdf;

import ar.com.leo.super_master_backend.catalogo_pdf.pdf.theme.ThemeSpec;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.BorderRadius;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;

public final class TableBuilder {

    public static final DeviceRgb LIGHT_GRAY = new DeviceRgb(235, 235, 235);

    private TableBuilder() {}

    public static Table createConfiguredTable(int productsPerPage) {
        if (productsPerPage <= 4) {
            return new Table(UnitValue.createPercentArray(1)).useAllAvailableWidth();
        }
        if (productsPerPage <= 12) {
            return new Table(UnitValue.createPercentArray(3)).useAllAvailableWidth();
        }
        return new Table(UnitValue.createPercentArray(5)).useAllAvailableWidth();
    }

    public static Div buildCardContainer(int productsPerPage, ThemeSpec theme, float availableWidthSpace) {
        if (productsPerPage == 2) {
            return new Div()
                    .setPadding(10)
                    .setMargin(0)
                    .setHeight(100)
                    .setWidth(availableWidthSpace - 60)
                    .setBackgroundColor(LIGHT_GRAY)
                    .setBorderRadius(new BorderRadius(6))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE);
        }
        if (productsPerPage == 4) {
            return new Div()
                    .setPadding(5)
                    .setMargin(0)
                    .setHeight(100)
                    .setWidth(availableWidthSpace / 2)
                    .setBackgroundColor(LIGHT_GRAY)
                    .setBorderRadius(new BorderRadius(6))
                    .setTextAlignment(TextAlignment.CENTER);
        }
        if (productsPerPage == 8) {
            return new Div()
                    .setPadding(5)
                    .setPaddingTop(0)
                    .setMargin(0)
                    .setHeight(360)
                    .setWidth(availableWidthSpace / 4)
                    .setBorder(new SolidBorder(theme.cardBorderColor(), 2f))
                    .setBorderRadius(new BorderRadius(6))
                    .setTextAlignment(TextAlignment.CENTER);
        }
        if (productsPerPage == 12) {
            return new Div()
                    .setPadding(5)
                    .setPaddingTop(0)
                    .setMargin(0)
                    .setHeight(180)
                    .setWidth(availableWidthSpace / 3)
                    .setBorder(new SolidBorder(theme.cardBorderColor(), 2f))
                    .setBorderRadius(new BorderRadius(6))
                    .setTextAlignment(TextAlignment.CENTER);
        }
        return new Div()
                .setPadding(2)
                .setPaddingTop(0)
                .setMargin(0)
                .setWidth(100)
                .setHeight(180)
                .setBorder(new SolidBorder(theme.cardBorderColor(), 2f))
                .setBorderRadius(new BorderRadius(6))
                .setTextAlignment(TextAlignment.CENTER);
    }
}

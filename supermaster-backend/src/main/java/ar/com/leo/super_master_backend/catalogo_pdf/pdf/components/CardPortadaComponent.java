package ar.com.leo.super_master_backend.catalogo_pdf.pdf.components;

import ar.com.leo.super_master_backend.catalogo_pdf.pdf.theme.ThemeSpec;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.BorderRadius;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Renderiza la portada (primera página) del catálogo: logo + título + subtítulo
 * dentro de una card centrada verticalmente.
 */
public final class CardPortadaComponent {

    private static final DateTimeFormatter COVER_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy");

    private CardPortadaComponent() {}

    public static void addFirstPage(Document doc,
                                    float pageHeight,
                                    float pageWidth,
                                    String titleTextInput,
                                    String subtitleTextInput,
                                    ThemeSpec theme,
                                    boolean presupuestoActivo) throws IOException {
        String title = safeTrim(titleTextInput);
        if (title == null) {
            title = presupuestoActivo ? "PRESUPUESTO" : "CATALOGO";
        }

        PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        Paragraph titulo = autoFitToSingleLine(title, theme, font, 350f - 4f, 70f, 10f);

        String subtitle = safeTrim(subtitleTextInput);
        if (subtitle == null) {
            subtitle = COVER_DATE_FORMAT.format(LocalDate.now());
        }

        Paragraph subtitulo = new Paragraph(subtitle)
                .setFontSize(25)
                .simulateBold()
                .setFontColor(theme.subtitleTextColor())
                .setPadding(10)
                .setTextAlignment(TextAlignment.CENTER);

        Image logo = new Image(theme.logoImage())
                .setWidth(300)
                .setHorizontalAlignment(HorizontalAlignment.CENTER)
                .setMarginBottom(20);

        float usableHeight = pageHeight - doc.getTopMargin() - doc.getBottomMargin();

        Div portada = new Div()
                .setHeight(usableHeight)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setTextAlignment(TextAlignment.CENTER);

        portada.add(logo);
        portada.add(buildCoverCard(titulo, subtitulo, presupuestoActivo));
        doc.add(portada);
        doc.add(new AreaBreak());
    }

    private static Div buildCoverCard(Paragraph titulo, Paragraph subtitulo, boolean presupuestoActivo) {
        final float cardWidth = 350f;
        final float borderWidth = 2f;
        String textoSuperior = presupuestoActivo ? "PRESUPUESTO" : "CATALOGO";

        Div separator1 = new Div().setWidth(cardWidth).setHeight(1).setBackgroundColor(ColorConstants.LIGHT_GRAY);
        Div separator2 = new Div().setWidth(cardWidth).setHeight(1).setBackgroundColor(ColorConstants.LIGHT_GRAY);

        return new Div()
                .setWidth(cardWidth)
                .setBackgroundColor(ColorConstants.WHITE)
                .setBorderRadius(new BorderRadius(15f))
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, borderWidth))
                .setHorizontalAlignment(HorizontalAlignment.CENTER)
                .add(new Paragraph(textoSuperior)
                        .simulateBold()
                        .setPadding(10)
                        .setFontSize(23)
                        .setCharacterSpacing(1)
                        .setFontColor(ColorConstants.LIGHT_GRAY))
                .add(separator1)
                .add(titulo)
                .add(separator2)
                .add(subtitulo);
    }

    private static Paragraph autoFitToSingleLine(String titleTextInput,
                                                 ThemeSpec theme,
                                                 PdfFont font,
                                                 float maxWidth,
                                                 float initialFontSize,
                                                 float minFontSize) {
        float currentFontSize = initialFontSize;
        while (font.getWidth(titleTextInput, currentFontSize) > maxWidth && currentFontSize > minFontSize) {
            currentFontSize -= 0.5f;
        }
        return new Paragraph(titleTextInput)
                .setFont(font)
                .setFontSize(currentFontSize)
                .setFontColor(theme.titleTextColor())
                .setTextAlignment(TextAlignment.CENTER)
                .setPaddingTop(30)
                .setPaddingBottom(30)
                .setMultipliedLeading(0.8f);
    }

    private static String safeTrim(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}

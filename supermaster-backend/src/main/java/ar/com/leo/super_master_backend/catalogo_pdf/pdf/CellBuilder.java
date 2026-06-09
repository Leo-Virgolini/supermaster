package ar.com.leo.super_master_backend.catalogo_pdf.pdf;

import ar.com.leo.super_master_backend.catalogo_pdf.pdf.components.*;
import ar.com.leo.super_master_backend.catalogo_pdf.pdf.stats.PdfGenerationStats;
import ar.com.leo.super_master_backend.catalogo_pdf.pdf.theme.ThemeSpec;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;

import java.io.IOException;

/**
 * Orquestador: arma la {@link Cell} de un producto combinando los componentes
 * (Codigo, Nombre, Precio, Uxb, Imagen) según la cantidad de productos por página.
 */
public final class CellBuilder {

    private CellBuilder() {}

    public static Cell createCell(CatalogoPdfItem item,
                                  boolean incluirImagenes,
                                  float imageSize,
                                  float pageWidth,
                                  float pageHeight,
                                  int itemsThisPage,
                                  int productsPerPage,
                                  boolean esPar,
                                  ThemeSpec theme,
                                  String imagenesDirActual,
                                  RenderConfig renderConfig,
                                  PdfGenerationStats stats) throws IOException {
        float availableWidthSpace = incluirImagenes ? pageWidth - imageSize : pageWidth;

        Div card = TableBuilder.buildCardContainer(productsPerPage, theme, availableWidthSpace);

        Paragraph codigo = renderConfig.mostrarCodigo()
                ? CodigoComponent.build(item.sku(), theme, availableWidthSpace, renderConfig.fontSizeCodigo(), parseHexColor(renderConfig.colorCodigo(), theme.codeTextColor()))
                : null;
        Paragraph nombre = renderConfig.mostrarNombre()
                ? NombreComponent.build(item.nombre(), renderConfig.fontSizeNombre(), parseHexColor(renderConfig.colorNombre(), theme.cardTextColor()))
                : null;
        Paragraph precio = renderConfig.mostrarPrecio()
                ? PrecioComponent.build(item.precio(), renderConfig.fontSizePrecio(), parseHexColor(renderConfig.colorPrecio(), theme.cardTextColor()))
                : null;
        Paragraph uxb = renderConfig.mostrarUxb()
                ? UxbComponent.build(item.uxb(), renderConfig.fontSizeUxb(), parseHexColor(renderConfig.colorUxb(), theme.cardTextColor()))
                : null;
        Image image = incluirImagenes
                ? ImagenComponent.build(item.imagenUrl(), imageSize, imagenesDirActual, item.sku(), stats)
                : null;

        if (productsPerPage <= 4) {
            addCardSection(card, codigo, false);
            addCardSection(card, nombre, true);
            addCardSection(card, precio, true);
            addCardSection(card, uxb, true);

            Table horizontalLayout = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                    .useAllAvailableWidth()
                    .setBorder(Border.NO_BORDER)
                    .setHeight((pageHeight - 60) / (productsPerPage == 2 ? 2 : 4));

            card.setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setHorizontalAlignment(HorizontalAlignment.CENTER);

            if (image != null) {
                if (esPar) {
                    horizontalLayout.addCell(new Cell().add(image).setVerticalAlignment(VerticalAlignment.MIDDLE).setBorder(Border.NO_BORDER));
                    horizontalLayout.addCell(new Cell().add(card).setVerticalAlignment(VerticalAlignment.MIDDLE).setBorder(Border.NO_BORDER));
                } else {
                    horizontalLayout.addCell(new Cell().add(card).setVerticalAlignment(VerticalAlignment.MIDDLE).setBorder(Border.NO_BORDER));
                    horizontalLayout.addCell(new Cell().add(image).setVerticalAlignment(VerticalAlignment.MIDDLE).setBorder(Border.NO_BORDER));
                }
            } else {
                horizontalLayout.addCell(new Cell(1, 2).add(card).setVerticalAlignment(VerticalAlignment.MIDDLE).setBorder(Border.NO_BORDER));
            }

            Cell cell = new Cell().add(horizontalLayout).setBorder(Border.NO_BORDER).setPaddingTop(2);
            if (itemsThisPage + 1 <= 3 && productsPerPage == 4) {
                cell.add(SeparatorComponent.build());
            }
            if (itemsThisPage + 1 <= 1 && productsPerPage == 2) {
                cell.add(SeparatorComponent.build());
            }
            return cell;
        }

        addCardSection(card, codigo, false);
        if (image != null) {
            card.add(image);
        }
        addCardSection(card, nombre, codigo != null || image != null);
        addCardSection(card, precio, nombre != null || codigo != null || image != null);
        addCardSection(card, uxb, precio != null || nombre != null || codigo != null || image != null);

        return new Cell().add(card).setBorder(Border.NO_BORDER).setPadding(5);
    }

    private static void addCardSection(Div card, Paragraph paragraph, boolean addSeparator) {
        if (paragraph == null) {
            return;
        }
        if (addSeparator) {
            card.add(SeparatorComponent.build());
        }
        card.add(paragraph);
    }

    private static Color parseHexColor(String hex, Color fallback) {
        if (hex == null) return fallback;
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            int r = Integer.parseInt(h.substring(0, 2), 16);
            int g = Integer.parseInt(h.substring(2, 4), 16);
            int b = Integer.parseInt(h.substring(4, 6), 16);
            return new DeviceRgb(r, g, b);
        } catch (Exception e) {
            return fallback;
        }
    }
}

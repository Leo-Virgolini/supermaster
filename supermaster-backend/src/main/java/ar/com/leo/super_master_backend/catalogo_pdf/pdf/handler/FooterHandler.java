package ar.com.leo.super_master_backend.catalogo_pdf.pdf.handler;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEvent;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEventHandler;
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent;

public final class FooterHandler extends AbstractPdfDocumentEventHandler {

    private final PdfFont font;
    private final ImageData logoData;
    private final boolean caratula;

    public FooterHandler(PdfFont font, ImageData logoData, boolean caratula) {
        this.font = font;
        this.logoData = logoData;
        this.caratula = caratula;
    }

    @Override
    protected void onAcceptedEvent(AbstractPdfDocumentEvent event) {
        PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
        PdfDocument pdfDoc = docEvent.getDocument();
        PdfPage page = docEvent.getPage();
        int pageNumber = pdfDoc.getPageNumber(page);
        if (caratula && pageNumber == 1) {
            return;
        }

        int displayPageNumber = caratula ? pageNumber - 1 : pageNumber;
        PdfCanvas canvas = new PdfCanvas(page);
        float pageWidth = page.getPageSize().getWidth();
        float fontSize = 10f;
        float y = 20f;
        float textX = pageWidth / 2;
        float logoX = pageWidth / 2 - 40;

        canvas.beginText()
                .setFontAndSize(font, fontSize)
                .setColor(ColorConstants.DARK_GRAY, true)
                .moveText(textX, y)
                .showText("Página " + displayPageNumber)
                .endText();

        Rectangle rect = new Rectangle(logoX, y - (25f / 2), 30, 25);
        canvas.addImageFittedIntoRectangle(logoData, rect, false);
    }
}

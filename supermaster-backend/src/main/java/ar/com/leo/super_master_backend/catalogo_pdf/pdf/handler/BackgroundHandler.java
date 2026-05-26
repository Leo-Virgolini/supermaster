package ar.com.leo.super_master_backend.catalogo_pdf.pdf.handler;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEvent;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEventHandler;
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class BackgroundHandler extends AbstractPdfDocumentEventHandler {

    private final PdfDocument pdfDoc;
    private final ImageData backgroundFirstPageImg;
    private final ImageData backgroundImg;
    private final boolean caratula;

    public BackgroundHandler(PdfDocument pdfDoc, ImageData backgroundFirstPageImg, ImageData backgroundImg, boolean caratula) {
        this.pdfDoc = pdfDoc;
        this.backgroundFirstPageImg = backgroundFirstPageImg;
        this.backgroundImg = backgroundImg;
        this.caratula = caratula;
    }

    @Override
    protected void onAcceptedEvent(AbstractPdfDocumentEvent event) {
        try {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfPage page = docEvent.getPage();
            int pageNumber = pdfDoc.getPageNumber(page);
            Rectangle pageSize = page.getPageSize();
            PdfCanvas canvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdfDoc);
            ImageData fondo = (caratula && pageNumber == 1) ? backgroundFirstPageImg : backgroundImg;
            canvas.addImageFittedIntoRectangle(fondo, pageSize, false);
        } catch (Exception e) {
            log.warn("Error al agregar fondo a la página del catálogo PDF: {}", e.getMessage());
        }
    }
}

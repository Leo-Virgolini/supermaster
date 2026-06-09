package ar.com.leo.super_master_backend.catalogo_pdf.pdf.components;

import ar.com.leo.super_master_backend.catalogo_pdf.pdf.stats.PdfGenerationStats;
import ar.com.leo.super_master_backend.catalogo_pdf.pdf.theme.ThemeFactory;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.properties.HorizontalAlignment;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Construye la {@link Image} de un producto para el catálogo.
 *
 * Aplica recorte de bordes blancos, escalado a DPI objetivo y compresión JPEG
 * para reducir el peso del PDF (~50%+ menos en imágenes grandes).
 */
@Slf4j
public final class ImagenComponent {

    private static final int WHITE_THRESHOLD = 240;
    private static final int VERTICAL_MARGIN = 50;
    private static final float TARGET_DPI = 400f;
    private static final float JPEG_QUALITY = 0.95f;

    private ImagenComponent() {}

    /**
     * Construye la imagen del producto. Actualiza {@code stats} según corresponda:
     * - {@code productosSinImagen} si no se encuentra el archivo (única lista expuesta al usuario)
     * - {@code imagenesEnBlanco} si la imagen es 100% blanca
     * - {@code imagenesNoLeidas} si ImageIO.read devuelve null (archivo corrupto/inválido)
     * - {@code erroresImagen} si hay otra excepción procesándola
     *
     * En todos los casos de error retorna el placeholder SINIMAGEN.jpg.
     */
    public static Image build(String imageUrl,
                              float imageSize,
                              String imagenesDirActual,
                              String sku,
                              PdfGenerationStats stats) throws IOException {
        ImageData imageData = procesarImagen(imageUrl, imagenesDirActual, sku, imageSize, stats);
        return new Image(imageData)
                .scaleToFit(imageSize, imageSize)
                .setAutoScale(false)
                .setHorizontalAlignment(HorizontalAlignment.CENTER);
    }

    private static ImageData procesarImagen(String imageUrl,
                                            String imagenesDirActual,
                                            String sku,
                                            float imageSize,
                                            PdfGenerationStats stats) throws IOException {
        Path imagePath = resolveImagePath(imageUrl, imagenesDirActual);
        if (imagePath == null || !Files.exists(imagePath)) {
            // El SKU se reporta solo cuando NO hay archivo (semántica natural de "sin imagen").
            if (sku != null) stats.productosSinImagen.add(sku);
            return placeholder();
        }

        BufferedImage original;
        try {
            original = ImageIO.read(imagePath.toFile());
        } catch (Exception e) {
            stats.erroresImagen++;
            log.debug("No se pudo procesar imagen '{}': {}", imageUrl, e.getMessage());
            return placeholder();
        }

        if (original == null) {
            stats.imagenesNoLeidas++;
            return placeholder();
        }

        try {
            BufferedImage cropped = cropWhiteBorders(original, stats);
            int targetPx = Math.round(imageSize * TARGET_DPI / 72f);
            BufferedImage prepared = prepareForJpeg(cropped, targetPx);
            byte[] jpegBytes = encodeJpeg(prepared, JPEG_QUALITY);
            return ImageDataFactory.create(jpegBytes);
        } catch (Exception e) {
            stats.erroresImagen++;
            log.debug("Error procesando imagen '{}': {}", imageUrl, e.getMessage());
            return placeholder();
        }
    }

    private static BufferedImage cropWhiteBorders(BufferedImage original, PdfGenerationStats stats) {
        int width = original.getWidth();
        int height = original.getHeight();
        int[] pixels = original.getRGB(0, 0, width, height, null, 0, width);

        int left = width;
        int right = -1;
        int top = height;
        int bottom = -1;

        for (int y = 0, idx = 0; y < height; y++) {
            for (int x = 0; x < width; x++, idx++) {
                int rgb = pixels[idx];
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                if (r < WHITE_THRESHOLD || g < WHITE_THRESHOLD || b < WHITE_THRESHOLD) {
                    if (x < left) left = x;
                    if (x > right) right = x;
                    if (y < top) top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }

        if (right >= left && bottom >= top) {
            int t = Math.max(0, top - VERTICAL_MARGIN);
            int bt = Math.min(height - 1, bottom + VERTICAL_MARGIN);
            return original.getSubimage(left, t, right - left + 1, bt - t + 1);
        }
        stats.imagenesEnBlanco++;
        return original;
    }

    private static BufferedImage prepareForJpeg(BufferedImage src, int maxDim) {
        int w = src.getWidth();
        int h = src.getHeight();
        int largest = Math.max(w, h);
        int targetW;
        int targetH;
        if (largest > maxDim) {
            double scale = (double) maxDim / largest;
            targetW = Math.max(1, (int) Math.round(w * scale));
            targetH = Math.max(1, (int) Math.round(h * scale));
        } else {
            targetW = w;
            targetH = h;
        }
        BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, targetW, targetH);
        g.drawImage(src, 0, 0, targetW, targetH, null);
        g.dispose();
        return out;
    }

    private static byte[] encodeJpeg(BufferedImage img, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            if (param instanceof JPEGImageWriteParam) {
                ((JPEGImageWriteParam) param).setOptimizeHuffmanTables(true);
            }
            writer.write(null, new IIOImage(img, null, null), param);
            return baos.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private static ImageData placeholder() throws IOException {
        return ThemeFactory.loadAsset("SINIMAGEN.jpg");
    }

    private static Path resolveImagePath(String imageUrl, String imagenesDirActual) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String normalized = imageUrl.trim().replace('\\', '/');

        int apiIdx = normalized.indexOf("/api/imagenes/");
        if (apiIdx >= 0) {
            normalized = normalized.substring(apiIdx + "/api/imagenes/".length());
        }

        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return null;
        }

        Path candidate = Paths.get(normalized);
        if (candidate.isAbsolute()) {
            return candidate;
        }

        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        return Paths.get(imagenesDirActual, normalized);
    }
}

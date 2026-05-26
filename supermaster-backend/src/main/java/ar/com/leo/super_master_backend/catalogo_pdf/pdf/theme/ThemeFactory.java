package ar.com.leo.super_master_backend.catalogo_pdf.pdf.theme;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public final class ThemeFactory {

    private static final DeviceRgb TITLE_KT = new DeviceRgb(255, 134, 28);
    private static final DeviceRgb SUBTITLE_KT = new DeviceRgb(59, 30, 9);
    private static final DeviceRgb CODE_BG_KT = new DeviceRgb(255, 135, 12);
    private static final DeviceRgb CODE_TEXT_KT = new DeviceRgb(72, 65, 151);
    private static final DeviceRgb CARD_BORDER_KT = new DeviceRgb(162, 171, 145);
    private static final DeviceRgb CARD_TEXT_KT = new DeviceRgb(255, 255, 255);
    private static final DeviceRgb TITLE_LINEAGE = new DeviceRgb(66, 67, 154);
    private static final DeviceRgb CODE_TEXT_LINEAGE = new DeviceRgb(255, 255, 255);
    private static final DeviceRgb CARD_BORDER_LINEAGE = new DeviceRgb(125, 143, 195);
    private static final DeviceRgb CARD_TEXT_LINEAGE = new DeviceRgb(0, 0, 0);

    private ThemeFactory() {}

    public static ThemeSpec resolve(String estetica) throws IOException {
        String normalized = estetica != null ? estetica.trim() : null;
        if (normalized != null && normalized.equalsIgnoreCase("KT")) {
            return kitchenTools();
        }
        return lineaGE();
    }

    public static ThemeSpec kitchenTools() throws IOException {
        return new ThemeSpec(
                TITLE_KT, SUBTITLE_KT, CODE_BG_KT, CODE_TEXT_KT,
                CARD_BORDER_KT, CARD_TEXT_KT,
                loadAsset("backgroundKT.png"),
                loadAsset("backgroundwhiteKT.png"),
                loadAsset("logoKT.png")
        );
    }

    public static ThemeSpec lineaGE() throws IOException {
        return new ThemeSpec(
                TITLE_LINEAGE, TITLE_LINEAGE, TITLE_LINEAGE, CODE_TEXT_LINEAGE,
                CARD_BORDER_LINEAGE, CARD_TEXT_LINEAGE,
                loadAsset("background.png"),
                loadAsset("backgroundwhite.png"),
                loadAsset("lineaGE.png")
        );
    }

    public static ImageData loadAsset(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("catalogo_pdf_assets/" + filename);
        try (InputStream inputStream = resource.getInputStream()) {
            return ImageDataFactory.create(inputStream.readAllBytes());
        }
    }

    /**
     * Devuelve la forma normalizada del nombre de estética (para logs/comparaciones).
     */
    public static String normalize(String estetica) {
        if (estetica == null) return null;
        return estetica.trim().toUpperCase(Locale.ROOT);
    }
}

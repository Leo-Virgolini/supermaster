package ar.com.leo.super_master_backend.catalogo_pdf.pdf.theme;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.kernel.colors.DeviceRgb;

/**
 * Conjunto de colores y assets que define la estética visual del catálogo.
 * Sin lógica; las instancias se crean en {@link ThemeFactory}.
 */
public record ThemeSpec(
        DeviceRgb titleTextColor,
        DeviceRgb subtitleTextColor,
        DeviceRgb codeBackgroundColor,
        DeviceRgb codeTextColor,
        DeviceRgb cardBorderColor,
        DeviceRgb cardTextColor,
        ImageData backgroundFirstPageImage,
        ImageData backgroundImage,
        ImageData logoImage
) {
}

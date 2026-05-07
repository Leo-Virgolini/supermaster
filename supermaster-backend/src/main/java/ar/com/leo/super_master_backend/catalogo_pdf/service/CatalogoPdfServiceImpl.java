package ar.com.leo.super_master_backend.catalogo_pdf.service;

import ar.com.leo.super_master_backend.catalogo_pdf.dto.CatalogoPdfRequestDTO;
import ar.com.leo.super_master_backend.catalogo_pdf.dto.CatalogoPdfResultDTO;
import ar.com.leo.super_master_backend.catalogo_pdf.dto.GenerarTodosResultDTO;
import ar.com.leo.super_master_backend.catalogo_pdf.dto.GenerarTodosResultDTO.GenerarItemResultDTO;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConceptoCuota;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalConceptoCuotaRepository;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.catalogo.entity.Catalogo;
import ar.com.leo.super_master_backend.dominio.catalogo.repository.CatalogoRepository;
import ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.entity.CatalogoPdfConfig;
import ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.repository.CatalogoPdfConfigRepository;
import ar.com.leo.super_master_backend.dominio.clasif_gral.repository.ClasifGralRepository;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCanalPrecio;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCatalogo;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoCanalPrecioRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoCatalogoRepository;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEvent;
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEventHandler;
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.BorderRadius;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogoPdfServiceImpl implements CatalogoPdfService {

    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(235, 235, 235);
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
    private static final DateTimeFormatter COVER_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy");

    @Value("${app.imagenes-dir:C:/ProgramData/SuperMaster/imagenes/}")
    private String imagenesDir;

    private final CatalogoRepository catalogoRepository;
    private final CanalRepository canalRepository;
    private final CanalConceptoCuotaRepository canalConceptoCuotaRepository;
    private final ProductoCatalogoRepository productoCatalogoRepository;
    private final ProductoCanalPrecioRepository productoCanalPrecioRepository;
    private final CatalogoPdfConfigRepository catalogoPdfConfigRepository;
    private final ClasifGralRepository clasifGralRepository;

    @Override
    @Transactional(readOnly = true)
    public CatalogoPdfResultDTO exportarCatalogoPdf(CatalogoPdfRequestDTO request) throws IOException {
        log.info("Generando catálogo PDF. catalogoId={}, canalId={}, cuotas={}", request.catalogoId(), request.canalId(), request.cuotas());

        if (request.catalogoId() == null || request.canalId() == null || request.cuotas() == null) {
            throw new IllegalArgumentException("catalogoId, canalId y cuotas son requeridos");
        }

        int cuotasValue = request.cuotas();

        Catalogo catalogo = catalogoRepository.findById(request.catalogoId())
                .orElseThrow(() -> new IllegalArgumentException("Catálogo no encontrado: " + request.catalogoId()));

        Canal canal = canalRepository.findById(request.canalId())
                .orElseThrow(() -> new IllegalArgumentException("Canal no encontrado: " + request.canalId()));

        List<ProductoCatalogo> productosCatalogo = new ArrayList<>(productoCatalogoRepository.findByCatalogoId(request.catalogoId()));
        productosCatalogo = productosCatalogo.stream()
                .filter(pc -> cumpleFiltros(pc.getProducto(), request))
                .collect(Collectors.toCollection(ArrayList::new));

        ordenarProductos(productosCatalogo, request.ordenarPor());

        List<ItemCatalogoPdf> items = new ArrayList<>();
        for (ProductoCatalogo pc : productosCatalogo) {
            Producto producto = pc.getProducto();
            Optional<ProductoCanalPrecio> precioOpt = productoCanalPrecioRepository
                    .findByProductoIdAndCanalIdAndCuotas(producto.getId(), request.canalId(), cuotasValue);
            if (precioOpt.isEmpty()) {
                continue;
            }

            BigDecimal precioFinal = calcularPrecioCatalogo(precioOpt.get(), producto, catalogo);
            items.add(new ItemCatalogoPdf(
                    producto.getSku(),
                    resolverNombreProducto(producto),
                    precioFinal,
                    producto.getUxb(),
                    producto.getImagenUrl(),
                    producto.getMarca() != null ? producto.getMarca().getNombre() : null,
                    producto.getTipo() != null ? producto.getTipo().getNombre() : null
            ));
        }

        if (items.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("No existen productos con precio para el catálogo '%s' en el canal '%s' con %s",
                            catalogo.getNombre(), canal.getNombre(), describirCuotas(canal.getId(), cuotasValue))
            );
        }

        String nombreBase = (canal.getNombre() + "-" + catalogo.getNombre() + "-" + describirCuotas(canal.getId(), cuotasValue))
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", "_");

        boolean incluirImagenes = !Boolean.FALSE.equals(request.incluirImagenes());
        boolean caratula = !Boolean.FALSE.equals(request.caratula());
        boolean presupuestoActivo = "PRESUPUESTO".equalsIgnoreCase(safeTrim(request.tipoDocumento()));
        int productsPerPage = normalizeProductsPerPage(request.productosPorPagina());
        float imageSize = resolveImageSize(productsPerPage);
        ThemeSpec theme = resolveTheme(request.estetica());
        String imagenesDirActual = obtenerImagenesDirGlobal();
        PageSize pageSize = resolvePageSize(request.tipoHoja());
        RenderConfig renderConfig = buildRenderConfig(request);
        List<String> productosSinImagen = new ArrayList<>();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(outputStream);
            PdfDocument pdfDocument = new PdfDocument(writer);
            Document document = new Document(pdfDocument, pageSize);
            PdfFont footerFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            pdfDocument.addEventHandler(PdfDocumentEvent.START_PAGE,
                    new BackgroundHandler(pdfDocument, theme.backgroundFirstPageImage(), theme.backgroundImage(), caratula));
            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE,
                    new FooterHandler(footerFont, theme.logoImage(), caratula));

            if (productsPerPage <= 12) {
                document.setMargins(10, 10, 10, 10);
            } else {
                document.setMargins(10, 0, 0, 0);
            }

            float pageWidth = pageSize.getWidth();
            float pageHeight = pageSize.getHeight();

            if (caratula) {
                addFirstPage(document, pageHeight, pageWidth, request.titulo(), request.subtitulo(), theme, presupuestoActivo);
            }

            renderItems(document, items, incluirImagenes, imageSize, pageWidth, pageHeight, productsPerPage, theme, imagenesDirActual, renderConfig, productosSinImagen);
            document.close();

            return new CatalogoPdfResultDTO(outputStream.toByteArray(), nombreBase, items.size(), null, productosSinImagen);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogoPdfResultDTO exportarCatalogoPdfDesdeConfig(Integer configId) throws IOException {
        CatalogoPdfConfig config = catalogoPdfConfigRepository.findById(configId)
                .filter(c -> Boolean.TRUE.equals(c.getActivo()))
                .orElseThrow(() -> new IllegalArgumentException("Configuración de catálogo PDF no encontrada o inactiva: " + configId));

        Integer catalogoId = config.getCatalogoId();
        if (catalogoRepository.findById(catalogoId).isEmpty()) {
            throw new IllegalArgumentException("No existe un catálogo con id: " + catalogoId);
        }

        Integer canalId = config.getCanalId();
        if (canalRepository.findById(canalId).isEmpty()) {
            throw new IllegalArgumentException("No existe un canal con id: " + canalId);
        }

        Integer clasifGralId = config.getClasifGralId();
        if (clasifGralId != null && clasifGralRepository.findById(clasifGralId).isEmpty()) {
            throw new IllegalArgumentException("No existe una clasificación general con id: " + clasifGralId);
        }

        CatalogoPdfRequestDTO request = new CatalogoPdfRequestDTO(
                catalogoId,
                canalId,
                config.getCuotas() != null ? config.getCuotas() : 0,
                clasifGralId,
                null,
                null,
                null,
                null,
                config.getOrdenarPor(),
                config.getTitulo(),
                null,
                true,
                config.getCaratula(),
                config.getEstetica() != null ? config.getEstetica().name().replace('_', ' ') : null,
                config.getTipoDocumento() != null ? config.getTipoDocumento().name() : null,
                config.getProductosPorPagina(),
                null,
                null, null, null,
                null, null, null,
                null, null, null,
                null, null, null
        );

        CatalogoPdfResultDTO result = exportarCatalogoPdf(request);

        String ubicacion = safeTrim(config.getUbicacionSalida());
        if (ubicacion != null) {
            String rutaGuardada = guardarEnDisco(result.archivo(), result.nombreArchivo(), ubicacion);
            return new CatalogoPdfResultDTO(result.archivo(), result.nombreArchivo(), result.productosExportados(), rutaGuardada, result.productosSinImagen());
        }

        return result;
    }

    @Override
    public GenerarTodosResultDTO generarTodosLosAutomaticos() {
        List<CatalogoPdfConfig> configs = catalogoPdfConfigRepository.findAllByActivoTrue();
        List<GenerarItemResultDTO> resultados = new java.util.ArrayList<>();
        int exitosos = 0;
        int fallidos = 0;

        for (CatalogoPdfConfig config : configs) {
            try {
                CatalogoPdfResultDTO result = exportarCatalogoPdfDesdeConfig(config.getId());
                resultados.add(new GenerarItemResultDTO(
                        config.getId(), config.getNombre(), true,
                        result.productosExportados(), result.rutaGuardada(), null, result.productosSinImagen()));
                exitosos++;
            } catch (Exception e) {
                log.error("Error al generar catálogo automático id={} '{}': {}", config.getId(), config.getNombre(), e.getMessage(), e);
                resultados.add(new GenerarItemResultDTO(
                        config.getId(), config.getNombre(), false,
                        0, null, e.getMessage(), List.of()));
                fallidos++;
            }
        }

        return new GenerarTodosResultDTO(configs.size(), exitosos, fallidos, resultados);
    }

    private String guardarEnDisco(byte[] archivo, String nombreBase, String ubicacion) throws IOException {
        Path dir = Paths.get(ubicacion);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        String filename = String.format("CATALOGO_%s_%s.pdf", nombreBase,
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
        Path destino = dir.resolve(filename);
        Files.write(destino, archivo);
        log.info("Catálogo PDF guardado en: {}", destino.toAbsolutePath());
        return destino.toAbsolutePath().toString();
    }

    private void renderItems(Document document,
                             List<ItemCatalogoPdf> items,
                             boolean incluirImagenes,
                             float imageSize,
                             float pageWidth,
                             float pageHeight,
                             int productsPerPage,
                             ThemeSpec theme,
                             String imagenesDirActual,
                             RenderConfig renderConfig,
                             List<String> productosSinImagen) throws IOException {
        int actualProductIndex = 0;

        while (actualProductIndex < items.size()) {
            Table table = createConfiguredTable(productsPerPage);
            int itemsThisPage = 0;

            while (itemsThisPage < productsPerPage && actualProductIndex < items.size()) {
                ItemCatalogoPdf item = items.get(actualProductIndex);
                boolean esPar = itemsThisPage % 2 == 0;

                Cell container = createItemCell(
                        item,
                        incluirImagenes,
                        imageSize,
                        pageWidth,
                        pageHeight,
                        itemsThisPage,
                        productsPerPage,
                        esPar,
                        theme,
                        imagenesDirActual,
                        renderConfig,
                        productosSinImagen
                );

                table.addCell(container);
                actualProductIndex++;
                itemsThisPage++;
            }

            if (itemsThisPage > 0) {
                int columnas = productsPerPage <= 4 ? 1 : productsPerPage <= 12 ? 3 : 5;
                int celdasFaltantes = (columnas - (itemsThisPage % columnas)) % columnas;
                for (int i = 0; i < celdasFaltantes; i++) {
                    table.addCell(new Cell().setBorder(Border.NO_BORDER));
                }

                document.add(table);
                if (itemsThisPage == productsPerPage && actualProductIndex < items.size()) {
                    document.add(new AreaBreak());
                }
            }
        }
    }

    private Cell createItemCell(ItemCatalogoPdf item,
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
                                List<String> productosSinImagen) throws IOException {
        float availableWidthSpace = incluirImagenes ? pageWidth - imageSize : pageWidth;

        Div card = buildCardContainer(productsPerPage, theme, availableWidthSpace);
        Paragraph codigo = renderConfig.mostrarCodigo() ? buildCodigo(item.sku(), theme, availableWidthSpace, renderConfig.fontSizeCodigo(), parseHexColor(renderConfig.colorCodigo(), theme.codeTextColor())) : null;
        Paragraph nombre = renderConfig.mostrarNombre() ? buildNombre(item.nombre(), theme, renderConfig.fontSizeNombre(), parseHexColor(renderConfig.colorNombre(), theme.cardTextColor())) : null;
        Paragraph precio = renderConfig.mostrarPrecio() ? buildPrecio(item.precio(), theme, renderConfig.fontSizePrecio(), parseHexColor(renderConfig.colorPrecio(), theme.cardTextColor())) : null;
        Paragraph uxb = renderConfig.mostrarUxb() ? buildUxb(item.uxb(), theme, renderConfig.fontSizeUxb(), parseHexColor(renderConfig.colorUxb(), theme.cardTextColor())) : null;
        Image image = incluirImagenes ? buildImage(item.imagenUrl(), imageSize, imagenesDirActual, item.sku(), productosSinImagen) : null;

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
                cell.add(buildSeparator());
            }
            if (itemsThisPage + 1 <= 1 && productsPerPage == 2) {
                cell.add(buildSeparator());
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

    private void addCardSection(Div card, Paragraph paragraph, boolean addSeparator) {
        if (paragraph == null) {
            return;
        }
        if (addSeparator) {
            card.add(buildSeparator());
        }
        card.add(paragraph);
    }

    private Table createConfiguredTable(int productsPerPage) {
        if (productsPerPage <= 4) {
            return new Table(UnitValue.createPercentArray(1)).useAllAvailableWidth();
        }
        if (productsPerPage <= 12) {
            return new Table(UnitValue.createPercentArray(3)).useAllAvailableWidth();
        }
        return new Table(UnitValue.createPercentArray(5)).useAllAvailableWidth();
    }

    private Div buildCardContainer(int productsPerPage, ThemeSpec theme, float availableWidthSpace) {
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

    private Paragraph buildCodigo(String codigoValue, ThemeSpec theme, float availableWidthSpace, float fontSize, Color color) {
        String codigo = safeText(codigoValue, "[SIN CÓDIGO]");
        if (codigo.matches("^\\d+\\.0$")) {
            codigo = codigo.substring(0, codigo.length() - 2);
        }
        return new Paragraph(codigo)
                .setMargin(0)
                .setMarginBottom(2)
                .setFontSize(fontSize)
                .simulateBold()
                .setFontColor(color)
                .setBorderRadius(new BorderRadius(10))
                .setBackgroundColor(theme.codeBackgroundColor())
                .setWidth(availableWidthSpace)
                .setHorizontalAlignment(HorizontalAlignment.CENTER);
    }

    private Paragraph buildNombre(String nombreValue, ThemeSpec theme, float fontSize, Color color) {
        return new Paragraph(safeText(nombreValue, "[SIN NOMBRE]"))
                .simulateBold()
                .setFontSize(fontSize)
                .setFontColor(color)
                .setMultipliedLeading(1f)
                .setTextAlignment(TextAlignment.CENTER)
                .setMargin(0)
                .setPadding(2);
    }

    private Paragraph buildPrecio(BigDecimal precioValue, ThemeSpec theme, float fontSize, Color color) {
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

    private Paragraph buildUxb(Integer uxbValue, ThemeSpec theme, float fontSize, Color color) {
        String value = uxbValue == null ? "--" : String.valueOf(uxbValue);
        Text valorUxb = new Text(value).simulateBold();
        return new Paragraph("UxB: ")
                .add(valorUxb)
                .setFontSize(fontSize)
                .setFontColor(color)
                .setMultipliedLeading(1f)
                .setTextAlignment(TextAlignment.CENTER)
                .setMargin(0)
                .setPadding(1);
    }

    private Paragraph buildSeparator() {
        return new Paragraph()
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                .setMargin(0)
                .setPadding(0);
    }
    private Image buildImage(String imageUrl, float imageSize, String imagenesDirActual, String sku, List<String> productosSinImagen) throws IOException {
        ImageData[] out = new ImageData[1];
        boolean encontrada = loadImageData(imageUrl, imagenesDirActual, out);
        if (!encontrada && sku != null) {
            productosSinImagen.add(sku);
        }
        return new Image(out[0])
                .scaleToFit(imageSize, imageSize)
                .setAutoScale(false)
                .setHorizontalAlignment(HorizontalAlignment.CENTER);
    }

    /**
     * @return true si la imagen se cargó correctamente, false si se usó el placeholder
     */
    private boolean loadImageData(String imageUrl, String imagenesDirActual, ImageData[] out) throws IOException {
        Path imagePath = resolveImagePath(imageUrl, imagenesDirActual);
        if (imagePath != null && Files.exists(imagePath)) {
            try {
                BufferedImage original = ImageIO.read(imagePath.toFile());
                if (original != null) {
                    BufferedImage cropped = cropWhiteBorders(original);
                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        ImageIO.write(cropped, "png", baos);
                        out[0] = ImageDataFactory.create(baos.toByteArray());
                        return true;
                    }
                }
            } catch (Exception e) {
                log.debug("No se pudo procesar imagen '{}' para catálogo PDF: {}", imageUrl, e.getMessage());
            }
        }
        out[0] = loadAsset("SINIMAGEN.jpg");
        return false;
    }

    private BufferedImage cropWhiteBorders(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();
        int threshold = 240;
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
                if (r < threshold || g < threshold || b < threshold) {
                    if (x < left) left = x;
                    if (x > right) right = x;
                    if (y < top) top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }

        if (right >= left && bottom >= top) {
            int marginVertical = 50;
            top = Math.max(0, top - marginVertical);
            bottom = Math.min(height - 1, bottom + marginVertical);
            return original.getSubimage(left, top, right - left + 1, bottom - top + 1);
        }
        return original;
    }

    private void addFirstPage(Document doc,
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

    private Div buildCoverCard(Paragraph titulo, Paragraph subtitulo, boolean presupuestoActivo) {
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

    private Paragraph autoFitToSingleLine(String titleTextInput,
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

    private ThemeSpec resolveTheme(String estetica) throws IOException {
        String normalized = safeTrim(estetica);
        if (normalized != null && normalized.equalsIgnoreCase("KT")) {
            return new ThemeSpec(
                    TITLE_KT,
                    SUBTITLE_KT,
                    CODE_BG_KT,
                    CODE_TEXT_KT,
                    CARD_BORDER_KT,
                    CARD_TEXT_KT,
                    loadAsset("backgroundKT.png"),
                    loadAsset("backgroundwhiteKT.png"),
                    loadAsset("logoKT.png")
            );
        }

        return new ThemeSpec(
                TITLE_LINEAGE,
                TITLE_LINEAGE,
                TITLE_LINEAGE,
                CODE_TEXT_LINEAGE,
                CARD_BORDER_LINEAGE,
                CARD_TEXT_LINEAGE,
                loadAsset("background.png"),
                loadAsset("backgroundwhite.png"),
                loadAsset("lineaGE.png")
        );
    }

    private ImageData loadAsset(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("catalogo_pdf_assets/" + filename);
        try (InputStream inputStream = resource.getInputStream()) {
            return ImageDataFactory.create(inputStream.readAllBytes());
        }
    }

    private record RenderConfig(
            boolean mostrarCodigo, float fontSizeCodigo, String colorCodigo,
            boolean mostrarNombre, float fontSizeNombre, String colorNombre,
            boolean mostrarPrecio, float fontSizePrecio, String colorPrecio,
            boolean mostrarUxb, float fontSizeUxb, String colorUxb) {}

    private RenderConfig buildRenderConfig(CatalogoPdfRequestDTO request) {
        return new RenderConfig(
                !Boolean.FALSE.equals(request.mostrarCodigo()),
                request.fontSizeCodigo() != null && request.fontSizeCodigo() > 0 ? request.fontSizeCodigo() : 6f,
                safeTrim(request.colorCodigo()),
                !Boolean.FALSE.equals(request.mostrarNombre()),
                request.fontSizeNombre() != null && request.fontSizeNombre() > 0 ? request.fontSizeNombre() : 6f,
                safeTrim(request.colorNombre()),
                !Boolean.FALSE.equals(request.mostrarPrecio()),
                request.fontSizePrecio() != null && request.fontSizePrecio() > 0 ? request.fontSizePrecio() : 7f,
                safeTrim(request.colorPrecio()),
                !Boolean.FALSE.equals(request.mostrarUxb()),
                request.fontSizeUxb() != null && request.fontSizeUxb() > 0 ? request.fontSizeUxb() : 6f,
                safeTrim(request.colorUxb())
        );
    }

    private Color parseHexColor(String hex, Color fallback) {
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

    private PageSize resolvePageSize(String tipoHoja) {
        if (tipoHoja == null) return PageSize.A4;
        return switch (tipoHoja.trim().toUpperCase(Locale.ROOT)) {
            case "LETTER", "CARTA" -> PageSize.LETTER;
            case "A3" -> PageSize.A3;
            case "A5" -> PageSize.A5;
            default -> PageSize.A4;
        };
    }

    private int normalizeProductsPerPage(Integer productsPerPage) {
        if (productsPerPage == null) {
            return 12;
        }
        return switch (productsPerPage) {
            case 2, 4, 8, 12, 20 -> productsPerPage;
            default -> 12;
        };
    }

    private float resolveImageSize(int productsPerPage) {
        return switch (productsPerPage) {
            case 2 -> 380f;
            case 4 -> 190f;
            case 8 -> 120f;
            case 20 -> 60f;
            default -> 90f;
        };
    }
    private Path resolveImagePath(String imageUrl, String imagenesDirActual) {
        String normalized = safeTrim(imageUrl);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.replace('\\', '/');

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

    private String obtenerImagenesDirGlobal() {
        return safeTrim(imagenesDir) != null ? safeTrim(imagenesDir) : "C:/ProgramData/SuperMaster/imagenes/";
    }

    private BigDecimal calcularPrecioCatalogo(ProductoCanalPrecio precio, Producto producto, Catalogo catalogo) {
        BigDecimal pvpFinal = precio.getPvp();
        if (pvpFinal == null) {
            return BigDecimal.ZERO;
        }

        if (Boolean.FALSE.equals(catalogo.getExportarConIva())) {
            BigDecimal iva = producto.getIva();
            if (iva != null && iva.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal divisor = BigDecimal.ONE.add(iva.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
                pvpFinal = pvpFinal.divide(divisor, 2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal recargo = catalogo.getRecargoPorcentaje();
        if (recargo != null && recargo.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal factor = BigDecimal.ONE.add(recargo.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
            pvpFinal = pvpFinal.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        }

        return pvpFinal.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean cumpleFiltros(Producto producto, CatalogoPdfRequestDTO request) {
        if (request.clasifGralId() != null && (producto.getClasifGral() == null || !request.clasifGralId().equals(producto.getClasifGral().getId()))) {
            return false;
        }
        if (request.clasifGastroId() != null && (producto.getClasifGastro() == null || !request.clasifGastroId().equals(producto.getClasifGastro().getId()))) {
            return false;
        }
        if (request.tipoId() != null && (producto.getTipo() == null || !request.tipoId().equals(producto.getTipo().getId()))) {
            return false;
        }
        if (request.marcaId() != null && (producto.getMarca() == null || !request.marcaId().equals(producto.getMarca().getId()))) {
            return false;
        }
        if (request.tag() != null) {
            if (producto.getTag() == null || !request.tag().equals(producto.getTag())) {
                return false;
            }
        }
        return true;
    }

    private void ordenarProductos(List<ProductoCatalogo> productosCatalogo, String ordenarPor) {
        List<String> camposOrden = new ArrayList<>();
        if (ordenarPor != null && !ordenarPor.isBlank()) {
            for (String campo : ordenarPor.split(",")) {
                String normalized = campo.trim().toLowerCase(Locale.ROOT);
                if (!normalized.isBlank()) {
                    camposOrden.add(normalized);
                }
            }
        }

        productosCatalogo.sort((pc1, pc2) -> {
            Producto p1 = pc1.getProducto();
            Producto p2 = pc2.getProducto();

            for (String campo : camposOrden) {
                int cmp = compararPorCampo(p1, p2, campo);
                if (cmp != 0) {
                    return cmp;
                }
            }

            return compareNullsLast(resolverNombreProducto(p1), resolverNombreProducto(p2));
        });
    }

    private int compararPorCampo(Producto p1, Producto p2, String campo) {
        return switch (campo) {
            case "clasifgral" -> compareNullsLast(
                    p1.getClasifGral() != null ? p1.getClasifGral().getNombre() : null,
                    p2.getClasifGral() != null ? p2.getClasifGral().getNombre() : null
            );
            case "clasifgastro" -> compareNullsLast(
                    p1.getClasifGastro() != null ? p1.getClasifGastro().getNombre() : null,
                    p2.getClasifGastro() != null ? p2.getClasifGastro().getNombre() : null
            );
            case "tipo" -> compareNullsLast(
                    p1.getTipo() != null ? p1.getTipo().getNombre() : null,
                    p2.getTipo() != null ? p2.getTipo().getNombre() : null
            );
            case "marca" -> compareNullsLast(
                    p1.getMarca() != null ? p1.getMarca().getNombre() : null,
                    p2.getMarca() != null ? p2.getMarca().getNombre() : null
            );
            case "tag" -> compareNullsLast(
                    p1.getTag() != null ? p1.getTag().name() : null,
                    p2.getTag() != null ? p2.getTag().name() : null
            );
            default -> 0;
        };
    }

    private int compareNullsLast(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return a.compareToIgnoreCase(b);
    }

    private String resolverNombreProducto(Producto producto) {
        if (producto.getTituloWeb() != null && !producto.getTituloWeb().isBlank()) {
            return producto.getTituloWeb();
        }
        return Objects.requireNonNullElse(producto.getDescripcion(), producto.getSku());
    }

    private String describirCuotas(Integer canalId, int cuotas) {
        if (cuotas == -1) {
            return "Transferencia";
        }
        if (cuotas == 0) {
            return "Contado";
        }
        return canalConceptoCuotaRepository.findByCanalIdAndCuotas(canalId, cuotas).stream()
                .map(CanalConceptoCuota::getDescripcion)
                .findFirst()
                .orElse(cuotas + " cuotas");
    }

    private String formatMoney(BigDecimal value) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(new Locale("es", "AR"));
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('.');
        DecimalFormat formatter = new DecimalFormat("$#,##0.00", symbols);
        return formatter.format(value != null ? value : BigDecimal.ZERO);
    }

    private String safeText(String text, String fallback) {
        String value = safeTrim(text);
        return value != null ? value : fallback;
    }

    private String safeTrim(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
    private record ItemCatalogoPdf(
            String sku,
            String nombre,
            BigDecimal precio,
            Integer uxb,
            String imagenUrl,
            String marca,
            String tipo
    ) {
    }

    private record ThemeSpec(
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

    private static final class BackgroundHandler extends AbstractPdfDocumentEventHandler {
        private final PdfDocument pdfDoc;
        private final ImageData backgroundFirstPageImg;
        private final ImageData backgroundImg;
        private final boolean caratula;

        private BackgroundHandler(PdfDocument pdfDoc, ImageData backgroundFirstPageImg, ImageData backgroundImg, boolean caratula) {
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

    private static final class FooterHandler extends AbstractPdfDocumentEventHandler {
        private final PdfFont font;
        private final ImageData logoData;
        private final boolean caratula;

        private FooterHandler(PdfFont font, ImageData logoData, boolean caratula) {
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
}







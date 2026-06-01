package ar.com.leo.super_master_backend.catalogo_pdf.service;

import ar.com.leo.super_master_backend.catalogo_pdf.dto.CatalogoPdfRequestDTO;
import ar.com.leo.super_master_backend.catalogo_pdf.dto.CatalogoPdfResultDTO;
import ar.com.leo.super_master_backend.catalogo_pdf.dto.GenerarTodosResultDTO;
import ar.com.leo.super_master_backend.catalogo_pdf.dto.GenerarTodosResultDTO.GenerarItemResultDTO;
import ar.com.leo.super_master_backend.catalogo_pdf.pdf.CatalogoPdfItem;
import ar.com.leo.super_master_backend.catalogo_pdf.pdf.CellBuilder;
import ar.com.leo.super_master_backend.catalogo_pdf.pdf.RenderConfig;
import ar.com.leo.super_master_backend.catalogo_pdf.pdf.TableBuilder;
import ar.com.leo.super_master_backend.catalogo_pdf.pdf.components.CardPortadaComponent;
import ar.com.leo.super_master_backend.catalogo_pdf.pdf.handler.BackgroundHandler;
import ar.com.leo.super_master_backend.catalogo_pdf.pdf.handler.FooterHandler;
import ar.com.leo.super_master_backend.catalogo_pdf.pdf.stats.PdfGenerationStats;
import ar.com.leo.super_master_backend.catalogo_pdf.pdf.theme.ThemeFactory;
import ar.com.leo.super_master_backend.catalogo_pdf.pdf.theme.ThemeSpec;
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
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Table;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
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

        List<CatalogoPdfItem> items = new ArrayList<>();
        for (ProductoCatalogo pc : productosCatalogo) {
            Producto producto = pc.getProducto();
            Optional<ProductoCanalPrecio> precioOpt = productoCanalPrecioRepository
                    .findByProductoIdAndCanalIdAndCuotas(producto.getId(), request.canalId(), cuotasValue);
            if (precioOpt.isEmpty()) {
                continue;
            }

            BigDecimal precioFinal = calcularPrecioCatalogo(precioOpt.get(), producto, catalogo);
            items.add(new CatalogoPdfItem(
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
        ThemeSpec theme = ThemeFactory.resolve(request.estetica());
        String imagenesDirActual = obtenerImagenesDirGlobal();
        PageSize pageSize = resolvePageSize(request.tipoHoja());
        RenderConfig renderConfig = buildRenderConfig(request);
        PdfGenerationStats stats = new PdfGenerationStats();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // Full compression + nivel 9 reduce el tamaño del PDF ~20-30% — clave en LAN.
            PdfWriter writer = new PdfWriter(outputStream,
                    new WriterProperties().setFullCompressionMode(true).setCompressionLevel(9));
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
                CardPortadaComponent.addFirstPage(document, pageHeight, pageWidth, request.titulo(), request.subtitulo(), theme, presupuestoActivo);
            }

            renderItems(document, items, incluirImagenes, imageSize, pageWidth, pageHeight, productsPerPage, theme, imagenesDirActual, renderConfig, stats);
            document.close();

            return new CatalogoPdfResultDTO(
                    outputStream.toByteArray(),
                    nombreBase,
                    items.size(),
                    null,
                    stats.productosSinImagen,
                    stats.imagenesEnBlanco,
                    stats.imagenesNoLeidas,
                    stats.erroresImagen
            );
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
            return new CatalogoPdfResultDTO(
                    result.archivo(), result.nombreArchivo(), result.productosExportados(),
                    rutaGuardada, result.productosSinImagen(),
                    result.imagenesEnBlanco(), result.imagenesNoLeidas(),
                    result.erroresImagen()
            );
        }

        return result;
    }

    @Override
    public GenerarTodosResultDTO generarTodosLosAutomaticos() {
        List<CatalogoPdfConfig> configs = catalogoPdfConfigRepository.findAllByActivoTrue();
        List<GenerarItemResultDTO> resultados = new ArrayList<>();
        int exitosos = 0;
        int fallidos = 0;

        for (CatalogoPdfConfig config : configs) {
            try {
                CatalogoPdfResultDTO result = exportarCatalogoPdfDesdeConfig(config.getId());
                resultados.add(new GenerarItemResultDTO(
                        config.getId(), config.getNombre(), true,
                        result.productosExportados(), result.rutaGuardada(), null,
                        result.productosSinImagen(),
                        result.imagenesEnBlanco(), result.imagenesNoLeidas(),
                        result.erroresImagen()));
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
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
        Path destino = dir.resolve(filename);
        Files.write(destino, archivo);
        log.info("Catálogo PDF guardado en: {}", destino.toAbsolutePath());
        return destino.toAbsolutePath().toString();
    }

    private void renderItems(Document document,
                             List<CatalogoPdfItem> items,
                             boolean incluirImagenes,
                             float imageSize,
                             float pageWidth,
                             float pageHeight,
                             int productsPerPage,
                             ThemeSpec theme,
                             String imagenesDirActual,
                             RenderConfig renderConfig,
                             PdfGenerationStats stats) throws IOException {
        int actualProductIndex = 0;

        while (actualProductIndex < items.size()) {
            Table table = TableBuilder.createConfiguredTable(productsPerPage);
            int itemsThisPage = 0;

            while (itemsThisPage < productsPerPage && actualProductIndex < items.size()) {
                CatalogoPdfItem item = items.get(actualProductIndex);
                boolean esPar = itemsThisPage % 2 == 0;

                Cell container = CellBuilder.createCell(
                        item, incluirImagenes, imageSize, pageWidth, pageHeight,
                        itemsThisPage, productsPerPage, esPar, theme, imagenesDirActual,
                        renderConfig, stats
                );

                table.addCell(container);
                actualProductIndex++;
                itemsThisPage++;
                stats.productosGenerados++;
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
            return producto.getTag() != null && request.tag().equals(producto.getTag());
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

    private String safeTrim(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}

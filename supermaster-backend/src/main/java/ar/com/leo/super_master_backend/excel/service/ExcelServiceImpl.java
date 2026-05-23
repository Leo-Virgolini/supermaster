package ar.com.leo.super_master_backend.excel.service;

import ar.com.leo.super_master_backend.dominio.apto.entity.Apto;
import ar.com.leo.super_master_backend.dominio.apto.repository.AptoRepository;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConceptoCuota;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalConceptoCuotaRepository;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalConceptoRepository;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.AplicaSobre;
import ar.com.leo.super_master_backend.dominio.catalogo.entity.Catalogo;
import ar.com.leo.super_master_backend.dominio.catalogo.repository.CatalogoRepository;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.repository.ClasifGastroRepository;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.clasif_gral.repository.ClasifGralRepository;
import ar.com.leo.super_master_backend.excel.dto.*;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.marca.repository.MarcaRepository;
import ar.com.leo.super_master_backend.dominio.material.entity.Material;
import ar.com.leo.super_master_backend.dominio.material.repository.MaterialRepository;
import ar.com.leo.super_master_backend.dominio.origen.entity.Origen;
import ar.com.leo.super_master_backend.dominio.origen.repository.OrigenRepository;
import ar.com.leo.super_master_backend.dominio.producto.calculo.service.RecalculoPrecioFacade;
import ar.com.leo.super_master_backend.dominio.producto.dto.CanalPreciosDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.DescuentoAplicableDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.PrecioDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoConPreciosDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoFilter;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCanalPrecio;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCanalPrecioInflado;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCatalogo;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoMargen;
import ar.com.leo.super_master_backend.dominio.producto.mla.entity.Mla;
import ar.com.leo.super_master_backend.dominio.producto.mla.repository.MlaRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoCanalPrecioRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoCanalPrecioInfladoRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoCatalogoRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoMargenRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import ar.com.leo.super_master_backend.dominio.producto.service.ProductoService;
import ar.com.leo.super_master_backend.dominio.regla_descuento.entity.ReglaDescuento;
import ar.com.leo.super_master_backend.dominio.regla_descuento.repository.ReglaDescuentoRepository;
import ar.com.leo.super_master_backend.dominio.proveedor.entity.Proveedor;
import ar.com.leo.super_master_backend.dominio.proveedor.repository.ProveedorRepository;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import ar.com.leo.super_master_backend.dominio.tipo.repository.TipoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.*;
import org.hibernate.AssertionFailure;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// Record para almacenar información de hojas a procesar
record SheetInfo(Sheet sheet, String nombre, int prioridad) {
}

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelServiceImpl implements ExcelService {

    private static final ZoneId ZONA_ARG = ZoneId.of("America/Argentina/Buenos_Aires");

    @PersistenceContext
    private EntityManager entityManager;

    private final ProductoRepository productoRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoCanalPrecioRepository productoCanalPrecioRepository;
    private final MarcaRepository marcaRepository;
    private final OrigenRepository origenRepository;
    private final TipoRepository tipoRepository;
    private final ClasifGralRepository clasifGralRepository;
    private final ClasifGastroRepository clasifGastroRepository;
    private final MlaRepository mlaRepository;
    private final CatalogoRepository catalogoRepository;
    private final CanalRepository canalRepository;
    private final CanalConceptoCuotaRepository canalConceptoCuotaRepository;
    private final MaterialRepository materialRepository;
    private final AptoRepository aptoRepository;
    private final ProductoCatalogoRepository productoCatalogoRepository;
    private final ProductoMargenRepository productoMargenRepository;
    private final ReglaDescuentoRepository reglaDescuentoRepository;
    private final CanalConceptoRepository canalConceptoRepository;
    private final ProductoCanalPrecioInfladoRepository productoCanalPrecioInfladoRepository;
    private final ProductoService productoService;
    private final RecalculoPrecioFacade recalculoPrecioFacade;

    // Acumula valores referenciados en MASTER que no existen en las tablas auxiliares,
    // agrupados por tipo de campo ("marca", "tipo", "origen", "material", "proveedor").
    // Se reportan al final de la importación. Se resetea al inicio de cada corrida.
    private final Map<String, Set<String>> valoresNoEncontradosMaster = new ConcurrentHashMap<>();

    private void registrarValorNoEncontrado(String campo, String valor) {
        valoresNoEncontradosMaster.computeIfAbsent(campo, k -> ConcurrentHashMap.newKeySet()).add(valor);
    }

    // Caches en memoria para evitar consultas repetidas durante la importación
    private final Map<String, Marca> cacheMarcas = new ConcurrentHashMap<>();
    private final Map<String, Origen> cacheOrigenes = new ConcurrentHashMap<>();
    private final Map<String, ClasifGral> cacheClasifGral = new ConcurrentHashMap<>();
    private final Map<String, ClasifGastro> cacheClasifGastro = new ConcurrentHashMap<>();
    private final Map<String, Proveedor> cacheProveedores = new ConcurrentHashMap<>();
    private final Map<String, Tipo> cacheTipos = new ConcurrentHashMap<>();
    private final Map<String, Catalogo> cacheCatalogos = new ConcurrentHashMap<>();
    private final Map<String, Canal> cacheCanales = new ConcurrentHashMap<>();
    private final Map<String, Material> cacheMateriales = new ConcurrentHashMap<>();

    private boolean esFilaVacia(Row row) {
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null) {
                String valor = obtenerValorCelda(row, i);
                if (valor != null && !valor.isBlank()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Compara dos BigDecimal por valor numérico (no por escala).
     * BigDecimal.equals() considera 100.00 != 100.0, pero compareTo() los considera iguales.
     */
    private boolean bigDecimalEquals(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.compareTo(b) == 0;
    }

    private boolean isExcelFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null)
            return false;
        return filename.endsWith(".xls") || filename.endsWith(".xlsx") ||
                filename.endsWith(".xlsm") || filename.endsWith(".XLS") ||
                filename.endsWith(".XLSX") || filename.endsWith(".XLSM");
    }

    /**
     * Abre un Workbook de manera eficiente.
     * Para archivos .xlsx grandes, usa OPCPackage para mejor rendimiento y menor uso de memoria.
     * Para archivos .xls o como fallback, usa WorkbookFactory.
     * <p>
     * Nota: El OPCPackage se cierra automáticamente cuando se cierra el Workbook.
     *
     * @param file El archivo Excel a abrir
     * @return Workbook abierto
     * @throws IOException Si hay un error al leer el archivo
     */
    private Workbook abrirWorkbookEficiente(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        boolean esXlsx = filename != null &&
                (filename.endsWith(".xlsx") || filename.endsWith(".XLSX") ||
                        filename.endsWith(".xlsm") || filename.endsWith(".XLSM"));

        if (esXlsx) {
            // Para archivos .xlsx, usar OPCPackage para mejor rendimiento
            // OPCPackage lee el archivo de manera más eficiente para archivos grandes
            // y reduce el uso de memoria al no cargar todo el archivo de una vez
            try {
                // OPCPackage.open(InputStream) no requiere PackageAccess (solo lectura implícita)
                OPCPackage pkg = OPCPackage.open(file.getInputStream());
                XSSFWorkbook workbook = new XSSFWorkbook(pkg);
                // El OPCPackage se cierra automáticamente cuando se cierra el Workbook
                return workbook;
            } catch (Exception e) {
                // Si falla con OPCPackage, leer el contenido completo y usar WorkbookFactory
                // Esto es necesario porque el InputStream ya se consumió
                log.warn("No se pudo abrir con OPCPackage, usando WorkbookFactory con contenido en memoria: {}", e.getMessage());
                byte[] contenido = file.getBytes();
                return WorkbookFactory.create(new ByteArrayInputStream(contenido));
            }
        } else {
            // Para archivos .xls, usar WorkbookFactory
            return WorkbookFactory.create(file.getInputStream());
        }
    }

    /**
     * Mapea los nombres de las columnas a sus índices
     */
    private Map<String, Integer> mapearColumnasPorNombre(Row headerRow) {
        Map<String, Integer> columnasMap = new HashMap<>();

        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String nombreColumna = obtenerValorCelda(headerRow, i);
                if (nombreColumna != null && !nombreColumna.isBlank()) {
                    // Normalizar nombre de columna (trim, uppercase para comparación)
                    String nombreNormalizado = nombreColumna.trim().toUpperCase();
                    columnasMap.put(nombreNormalizado, i);
                }
            }
        }

        return columnasMap;
    }

    /**
     * Obtiene el índice de una columna por su nombre (case-insensitive)
     */
    private Integer obtenerIndiceColumna(Map<String, Integer> columnasMap, String nombreColumna) {
        String nombreNormalizado = nombreColumna.trim().toUpperCase();
        Integer indice = columnasMap.get(nombreNormalizado);
        if (indice == null) {
            throw new IllegalArgumentException(
                    String.format("Columna '%s' no encontrada en el archivo. Columnas disponibles: %s",
                            nombreColumna, columnasMap.keySet()));
        }
        return indice;
    }

    private void procesarFila(Row row, String tipo, int rowIndex, Map<String, Integer> columnasMap) {
        switch (tipo.toLowerCase()) {
            case "master":
                procesarFilaMaster(row, rowIndex, columnasMap);
                break;
            case "titulos_web":
                procesarFilaTitulosWeb(row, rowIndex, columnasMap);
                break;
            case "validaciones":
                procesarFilaValidaciones(row, rowIndex, columnasMap);
                break;
            case "mla_envios":
                procesarFilaMlaEnvios(row, rowIndex, columnasMap);
                break;
            default:
                throw new IllegalArgumentException("Tipo de importación no soportado: " + tipo);
        }
    }


    private String obtenerValorCelda(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null)
            return null;

        return switch (cell.getCellType()) {
            case STRING -> {
                String valor = cell.getStringCellValue();
                yield valor != null ? valor.trim() : null;
            }
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    // Para fechas, retornar en formato DUX
                    Date fecha = cell.getDateCellValue();
                    LocalDateTime ldt = fecha.toInstant()
                            .atZone(ZoneId.of("America/Argentina/Buenos_Aires"))
                            .toLocalDateTime();
                    yield ldt.format(DateTimeFormatter.ofPattern("d/M/yyyy  HH:mm:ss"));
                } else {
                    // Para números, mantener decimales si los hay
                    double num = cell.getNumericCellValue();
                    if (num == (long) num) {
                        yield String.valueOf((long) num);
                    } else {
                        yield String.valueOf(num);
                    }
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                // Evaluar fórmula correctamente
                try {
                    org.apache.poi.ss.usermodel.FormulaEvaluator evaluator = row.getSheet().getWorkbook()
                            .getCreationHelper().createFormulaEvaluator();
                    org.apache.poi.ss.usermodel.CellValue cellValue = evaluator.evaluate(cell);

                    if (cellValue == null) {
                        yield null;
                    }

                    String resultado = switch (cellValue.getCellType()) {
                        case STRING -> {
                            String valor = cellValue.getStringValue();
                            yield (valor != null && !valor.isBlank()) ? valor.trim() : null;
                        }
                        case NUMERIC -> {
                            double num = cellValue.getNumberValue();
                            if (num == (long) num) {
                                yield String.valueOf((long) num);
                            } else {
                                yield String.valueOf(num);
                            }
                        }
                        case BOOLEAN -> String.valueOf(cellValue.getBooleanValue());
                        case BLANK, ERROR -> null;
                        default -> null;
                    };
                    yield resultado;
                } catch (Exception e) {
                    // Si hay error al evaluar la fórmula, retornar null en lugar de la fórmula
                    log.debug("Error evaluando fórmula en celda {}{}: {}",
                            row.getRowNum() + 1, columnIndex, e.getMessage());
                    yield null;
                }
            }
            case BLANK -> null;
            default -> "";
        };
    }

    /**
     * Trunca un string a un máximo de caracteres
     */
    private String truncarListaSkus(String prefijo, List<String> skus) {
        int MAX_SKUS = 20;
        if (skus.size() <= MAX_SKUS) {
            return prefijo + " (" + skus.size() + "): " + String.join(", ", skus);
        }
        return prefijo + " (" + skus.size() + "): " + String.join(", ", skus.subList(0, MAX_SKUS)) + " ... y " + (skus.size() - MAX_SKUS) + " más";
    }

    private String truncar(String valor, int maxLength) {
        if (valor == null) {
            return null;
        }
        if (valor.length() <= maxLength) {
            return valor;
        }
        return valor.substring(0, maxLength);
    }

    /**
     * Obtiene una fecha directamente de una celda de Excel
     * Si la celda es de tipo NUMERIC y está formateada como fecha, la convierte directamente
     * Si es STRING, intenta parsearla
     */
    private LocalDateTime obtenerFechaDeCelda(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return null;
        }

        // Si la celda es NUMERIC y está formateada como fecha
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            try {
                Date fecha = cell.getDateCellValue();
                return fecha.toInstant().atZone(ZONA_ARG).toLocalDateTime();
            } catch (Exception e) {
                log.warn("Error al obtener fecha de celda numérica: {}", e.getMessage());
            }
        }

        // Si es una fórmula, evaluarla primero
        if (cell.getCellType() == CellType.FORMULA) {
            try {
                FormulaEvaluator evaluator = row.getSheet().getWorkbook()
                        .getCreationHelper().createFormulaEvaluator();
                CellValue cellValue = evaluator.evaluate(cell);
                if (cellValue != null && cellValue.getCellType() == CellType.NUMERIC) {
                    // Intentar como fecha
                    try {
                        Date fecha = DateUtil.getJavaDate(cellValue.getNumberValue());
                        return fecha.toInstant().atZone(ZONA_ARG).toLocalDateTime();
                    } catch (Exception e) {
                        // No es una fecha, continuar con parseo de string
                    }
                }
            } catch (Exception e) {
                log.warn("Error al evaluar fórmula de fecha: {}", e.getMessage());
            }
        }

        // Si es STRING o no se pudo obtener como fecha numérica, parsear el string
        String fechaStr = obtenerValorCelda(row, columnIndex);
        if (fechaStr != null && !fechaStr.isBlank()) {
            // Intentar diferentes formatos
            DateTimeFormatter[] formatos = {
                    DateTimeFormatter.ofPattern("d/M/yyyy  HH:mm:ss"),
                    DateTimeFormatter.ofPattern("M/d/yyyy  HH:mm:ss"),
                    DateTimeFormatter.ofPattern("d/M/yyyy HH:mm:ss"),
                    DateTimeFormatter.ofPattern("M/d/yyyy HH:mm:ss"),
                    DateTimeFormatter.ofPattern("d/M/yyyy"),
                    DateTimeFormatter.ofPattern("M/d/yyyy"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd")
            };

            for (DateTimeFormatter formato : formatos) {
                try {
                    return LocalDateTime.parse(fechaStr, formato);
                } catch (DateTimeParseException e) {
                    // Continuar con el siguiente formato
                }
            }

            // Si es una fecha de Excel (número serial)
            try {
                double numeroSerial = Double.parseDouble(fechaStr);
                return org.apache.poi.ss.usermodel.DateUtil.getLocalDateTime(numeroSerial);
            } catch (NumberFormatException e) {
                // No es un número serial
            }
        }

        return null;
    }

    /**
     * Determina el tipo de importación según el nombre de la hoja
     * Mapea nombres de hojas comunes a tipos de importación
     */
    private String determinarTipoPorNombreHoja(String nombreHoja) {
        String nombreNormalizado = nombreHoja.trim().toUpperCase();

        // Mapeo de nombres de hojas a tipos (solo las que se procesan en importarMigracionCompleta)
        return switch (nombreNormalizado) {
            case "MASTER" -> "master"; // Hoja principal con productos completos
            case "TITULOSWEB", "TITULOS WEB" -> "titulos_web"; // Actualizar títulos web
            case "VALIDACIONES" -> "validaciones"; // Entidades maestras (marcas, tipos, orígenes, clasificaciones)
            case "MLA-ENVIOS", "MLA ENVIOS" -> "mla_envios"; // MLAs y envíos
            default -> null; // Hoja no reconocida
        };
    }

    /**
     * Procesa todas las tablas estructuradas (ListObjects) de la hoja VALIDACIONES
     * Cada tabla se identifica por su nombre y se procesa según su tipo
     */
    private void procesarTablasEstructuradas(Sheet sheet) {
        // Solo funciona con XSSFSheet (archivos .xlsx)
        if (!(sheet instanceof XSSFSheet)) {
            log.warn("⚠️  Las tablas estructuradas solo están disponibles en archivos .xlsx. Saltando procesamiento de tablas estructuradas");
            return;
        }

        XSSFSheet xssfSheet = (XSSFSheet) sheet;
        List<XSSFTable> tablas = xssfSheet.getTables();

        if (tablas.isEmpty()) {
            log.info("No se encontraron tablas estructuradas en la hoja VALIDACIONES");
            return;
        }

        log.info("🔍 Encontradas {} tablas estructuradas en la hoja VALIDACIONES", tablas.size());
        for (XSSFTable tabla : tablas) {
            String nombreTabla = tabla.getName();
            if (nombreTabla != null) {
                log.info("  - Tabla: '{}'", nombreTabla);
                procesarTablaEstructurada(sheet, tabla, nombreTabla);
            }
        }
    }

    /**
     * Procesa una tabla estructurada (ListObject) específica de la hoja VALIDACIONES
     * Busca la tabla por nombre y procesa sus filas con su propio mapeo de columnas
     */
    private void procesarTablaEstructurada(Sheet sheet, XSSFTable tabla, String nombreTabla) {
        // Solo funciona con XSSFSheet (archivos .xlsx)
        if (!(sheet instanceof XSSFSheet)) {
            log.warn("⚠️  Las tablas estructuradas solo están disponibles en archivos .xlsx. Saltando procesamiento de tabla '{}'", nombreTabla);
            return;
        }

        // Obtener el rango de la tabla
        AreaReference areaRef = tabla.getArea();
        if (areaRef == null) {
            log.warn("⚠️  La tabla '{}' no tiene un rango válido", nombreTabla);
            return;
        }

        CellReference primeraCelda = areaRef.getFirstCell();
        CellReference ultimaCelda = areaRef.getLastCell();

        int filaInicio = primeraCelda.getRow();
        int filaFin = ultimaCelda.getRow();
        int colInicio = primeraCelda.getCol();
        int colFin = ultimaCelda.getCol();

        log.info("Tabla '{}' rango: filas {} a {}, columnas {} a {}", nombreTabla,
                filaInicio + 1, filaFin + 1, colInicio + 1, colFin + 1);

        // Obtener el encabezado (primera fila del rango de datos, que es la segunda fila del área total)
        Row headerRow = sheet.getRow(filaInicio);
        if (headerRow == null) {
            log.warn("⚠️  No se encontró el encabezado de la tabla '{}' en fila {}", nombreTabla, filaInicio + 1);
            return;
        }

        // Mapear las columnas de esta tabla específica
        Map<String, Integer> columnasTabla = new HashMap<>();
        for (int colIdx = colInicio; colIdx <= colFin; colIdx++) {
            String nombreColumna = obtenerValorCelda(headerRow, colIdx);
            if (nombreColumna != null && !nombreColumna.isBlank()) {
                String nombreNormalizado = nombreColumna.trim().toUpperCase();
                columnasTabla.put(nombreNormalizado, colIdx);
            }
        }

        log.info("Columnas mapeadas para tabla '{}': {}", nombreTabla, columnasTabla.keySet());

        // Procesar las filas de datos (desde filaInicio + 1 hasta filaFin)
        int filasProcesadas = 0;
        for (int i = filaInicio + 1; i <= filaFin; i++) {
            Row row = sheet.getRow(i);
            if (row == null || esFilaVacia(row)) {
                continue;
            }

            try {
                procesarFilaTablaEstructurada(row, nombreTabla, columnasTabla, i);
                filasProcesadas++;
            } catch (Exception e) {
                log.warn("❌ Error procesando fila {} de tabla '{}': {}", i + 1, nombreTabla, e.getMessage());
            }
        }

        log.info("✓ Tabla estructurada '{}' procesada: {} filas procesadas", nombreTabla, filasProcesadas);
    }

    /**
     * Procesa una fila de una tabla estructurada según el tipo de tabla
     */
    private void procesarFilaTablaEstructurada(Row row, String nombreTabla, Map<String, Integer> columnasTabla, int filaIndex) {
        String nombreTablaUpper = nombreTabla.toUpperCase();

        try {
            // VMARCA/VLINEA - Marcas con jerarquía
            if (nombreTablaUpper.equals("VMARCA") || nombreTablaUpper.equals("VMARCAS")) {
                String marcaPadreNombreTemp = null;
                String marcaHijoNombreTemp = null;

                for (Map.Entry<String, Integer> entry : columnasTabla.entrySet()) {
                    String nombreCol = entry.getKey();
                    String valor = obtenerValorCelda(row, entry.getValue());
                    if (valor != null && !valor.isBlank()) {
                        if (nombreCol.equals("VMARCA")) {
                            marcaPadreNombreTemp = valor.trim();
                        } else if (nombreCol.equals("VLINEA")) {
                            marcaHijoNombreTemp = valor.trim();
                        }
                    }
                }

                final String marcaPadreNombre = marcaPadreNombreTemp;
                final String marcaHijoNombre = marcaHijoNombreTemp;

                if (marcaPadreNombre != null && !marcaPadreNombre.isBlank()) {
                    Marca marcaPadre = buscarOCrearMarca(marcaPadreNombre);
                    if (marcaPadre.getId() == null) {
                        marcaRepository.flush();
                        marcaPadre = marcaRepository.findByNombreIgnoreCase(marcaPadreNombre)
                                .orElseThrow(() -> new RuntimeException("No se pudo crear o encontrar marca: " + marcaPadreNombre));
                    }

                    if (marcaHijoNombre != null && !marcaHijoNombre.isBlank()) {
                        Marca marcaHijo = buscarOCrearMarca(marcaHijoNombre);
                        if (marcaHijo.getId() == null) {
                            marcaRepository.flush();
                            marcaHijo = marcaRepository.findByNombreIgnoreCase(marcaHijoNombre)
                                    .orElseThrow(() -> new RuntimeException("No se pudo crear o encontrar marca: " + marcaHijoNombre));
                        }
                        Marca padreActual = marcaHijo.getPadre();
                        if (padreActual == null || !padreActual.getId().equals(marcaPadre.getId())) {
                            marcaHijo.setPadre(marcaPadre);
                            marcaRepository.save(marcaHijo);
                        }
                    }
                }
            }
            // MATERIAL - Materiales
            else if (nombreTablaUpper.equals("MATERIAL") || nombreTablaUpper.equals("MATERIALES")) {
                String materialNombre = null;
                for (Map.Entry<String, Integer> entry : columnasTabla.entrySet()) {
                    String nombreCol = entry.getKey();
                    if (nombreCol.equals("MATERIAL")) {
                        String valor = obtenerValorCelda(row, entry.getValue());
                        if (valor != null && !valor.isBlank()) {
                            materialNombre = valor.trim();
                            break;
                        }
                    }
                }
                if (materialNombre != null && !materialNombre.isBlank()) {
                    buscarOCrearMaterial(materialNombre);
                }
            }
            // PROVEEDOR - Proveedores
            else if (nombreTablaUpper.equals("PROVEEDOR") || nombreTablaUpper.equals("PROVEEDORES")) {
                String proveedorNombre = null;
                String alias = null;
                for (Map.Entry<String, Integer> entry : columnasTabla.entrySet()) {
                    String nombreCol = entry.getKey();
                    String valor = obtenerValorCelda(row, entry.getValue());
                    if (valor != null && !valor.isBlank()) {
                        if (nombreCol.equals("PROVEEDOR")) {
                            proveedorNombre = valor.trim();
                        } else if (nombreCol.equals("ALIAS")) {
                            alias = valor.trim();
                        }
                    }
                }
                if (proveedorNombre != null && !proveedorNombre.isBlank()) {
                    buscarOCrearProveedor(proveedorNombre, alias);
                }
            }
            // VORIGEN - Orígenes
            else if (nombreTablaUpper.equals("VORIGEN") || nombreTablaUpper.equals("ORIGEN")) {
                String origenNombre = null;
                for (Map.Entry<String, Integer> entry : columnasTabla.entrySet()) {
                    String nombreCol = entry.getKey();
                    if (nombreCol.equals("VORIGEN") || nombreCol.equals("ORIGEN")) {
                        String valor = obtenerValorCelda(row, entry.getValue());
                        if (valor != null && !valor.isBlank()) {
                            origenNombre = valor.trim();
                            break;
                        }
                    }
                }
                if (origenNombre != null && !origenNombre.isBlank()) {
                    buscarOCrearOrigen(origenNombre);
                }
            }
            // TIPO1 - Tabla estructurada TIPO1: Tipos nivel 1 (sin padre)
            // Estructura: TIPO1 (nombre) e ID TIPO2 (que contiene el ID del TIPO1)
            else if (nombreTablaUpper.equals("TIPO1")) {
                String tipoNombre = null;
                if (columnasTabla.containsKey("TIPO1")) {
                    tipoNombre = obtenerValorCelda(row, columnasTabla.get("TIPO1"));
                    if (tipoNombre != null && !tipoNombre.isBlank()) {
                        tipoNombre = tipoNombre.trim();
                    } else {
                        tipoNombre = null;
                    }
                }
                if (tipoNombre != null && !tipoNombre.isBlank()) {
                    buscarOCrearTipo(tipoNombre, null);
                }
            }
            // TIPO2 - Tabla estructurada TIPO2: Tipos nivel 2 (hijo de TIPO1)
            // Estructura: TIPO2 (nombre), ID TIPO1 (padre), ID TIPO2 (ID del TIPO2)
            else if (nombreTablaUpper.equals("TIPO2")) {
                String tipoNombre = null;
                Integer tipo1Id = null;

                if (columnasTabla.containsKey("TIPO2")) {
                    String valor = obtenerValorCelda(row, columnasTabla.get("TIPO2"));
                    if (valor != null && !valor.isBlank()) {
                        tipoNombre = valor.trim();
                    }
                }

                if (columnasTabla.containsKey("ID TIPO1")) {
                    String valor = obtenerValorCelda(row, columnasTabla.get("ID TIPO1"));
                    if (valor != null && !valor.isBlank()) {
                        try {
                            tipo1Id = Integer.parseInt(valor.trim());
                        } catch (NumberFormatException e) {
                            log.warn("⚠️  Fila {} tabla TIPO2 - ID TIPO1='{}' no es un número válido",
                                    filaIndex + 1, valor);
                        }
                    }
                }

                if (tipoNombre != null && !tipoNombre.isBlank()) {
                    Tipo padre = null;
                    if (tipo1Id != null) {
                        padre = tipoRepository.findById(tipo1Id).orElse(null);
                        if (padre == null) {
                            log.warn("⚠️  Fila {} tabla TIPO2 - ID TIPO1={} no encontrado para tipo='{}'",
                                    filaIndex + 1, tipo1Id, tipoNombre);
                        }
                    }
                    buscarOCrearTipo(tipoNombre, padre);
                }
            }
            // TIPO3 - Tabla estructurada TIPO3: Tipos nivel 3 (hijo de TIPO2)
            // Estructura: TIPO3 (nombre), ID TIPO2 (padre), ID TIPO3 (ID del TIPO3)
            else if (nombreTablaUpper.equals("TIPO3")) {
                String tipoNombre = null;
                Integer tipo2Id = null;

                if (columnasTabla.containsKey("TIPO3")) {
                    String valor = obtenerValorCelda(row, columnasTabla.get("TIPO3"));
                    if (valor != null && !valor.isBlank()) {
                        tipoNombre = valor.trim();
                    }
                }

                if (columnasTabla.containsKey("ID TIPO2")) {
                    String valor = obtenerValorCelda(row, columnasTabla.get("ID TIPO2"));
                    if (valor != null && !valor.isBlank()) {
                        try {
                            tipo2Id = Integer.parseInt(valor.trim());
                        } catch (NumberFormatException e) {
                            log.warn("⚠️  Fila {} tabla TIPO3 - ID TIPO2='{}' no es un número válido",
                                    filaIndex + 1, valor);
                        }
                    }
                }

                if (tipoNombre != null && !tipoNombre.isBlank()) {
                    Tipo padre = null;
                    if (tipo2Id != null) {
                        padre = tipoRepository.findById(tipo2Id).orElse(null);
                        if (padre == null) {
                            log.warn("⚠️  Fila {} tabla TIPO3 - ID TIPO2={} no encontrado para tipo='{}'",
                                    filaIndex + 1, tipo2Id, tipoNombre);
                        }
                    }
                    buscarOCrearTipo(tipoNombre, padre);
                }
            }
            // VCLASIF - Clasificaciones (VCLASIF1, VCLASIF2, VCLASIF3, VCLASIF4)
            else if (nombreTablaUpper.equals("VCLASIF") || nombreTablaUpper.contains("CLASIF")) {
                String clasif1Nombre = null;
                String clasif2Nombre = null;
                String clasif3Nombre = null;
                String clasif4Nombre = null;

                for (Map.Entry<String, Integer> entry : columnasTabla.entrySet()) {
                    String nombreCol = entry.getKey();
                    String valor = obtenerValorCelda(row, entry.getValue());
                    if (valor != null && !valor.isBlank()) {
                        if (nombreCol.equals("VCLASIF1")) {
                            clasif1Nombre = valor.trim();
                        } else if (nombreCol.equals("VCLASIF2")) {
                            clasif2Nombre = valor.trim();
                        } else if (nombreCol.equals("VCLASIF3")) {
                            clasif3Nombre = valor.trim();
                        } else if (nombreCol.equals("VCLASIF4")) {
                            clasif4Nombre = valor.trim();
                        }
                    }
                }

                // Procesar ClasifGral (VCLASIF1 y VCLASIF2)
                ClasifGral clasifGralPadre = null;
                if (clasif1Nombre != null && !clasif1Nombre.isBlank()) {
                    clasifGralPadre = buscarOCrearClasifGral(clasif1Nombre, null);
                }
                if (clasif2Nombre != null && !clasif2Nombre.isBlank()) {
                    buscarOCrearClasifGral(clasif2Nombre, clasifGralPadre);
                }

                // Procesar ClasifGastro (VCLASIF3 y VCLASIF4)
                ClasifGastro clasifGastroPadre = null;
                if (clasif3Nombre != null && !clasif3Nombre.isBlank()) {
                    clasifGastroPadre = buscarOCrearClasifGastro(clasif3Nombre, null);
                }
                if (clasif4Nombre != null && !clasif4Nombre.isBlank()) {
                    buscarOCrearClasifGastro(clasif4Nombre, clasifGastroPadre);
                }
            } else {
                log.debug("Tabla '{}' no tiene procesador específico, se omite", nombreTabla);
            }
        } catch (Exception e) {
            log.warn("❌ Error procesando fila {} de tabla '{}': {}", filaIndex + 1, nombreTabla, e.getMessage());
            throw e;
        }
    }

    /**
     * Procesa una hoja en su propia transacción para aislar errores
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ImportResultDTO procesarHojaEnTransaccionSeparada(Sheet sheet, String tipo, String nombreHoja) {
        log.debug("Iniciando transacción separada para hoja '{}' (tipo: '{}')", nombreHoja, tipo);
        try {
            log.debug("Ejecutando procesarHoja para hoja '{}' dentro de la transacción", nombreHoja);
            return procesarHoja(sheet, tipo, nombreHoja);
        } catch (Exception e) {
            log.error("Error dentro de la transacción para hoja '{}': {} - {}", nombreHoja,
                    e.getMessage(), e.getClass().getSimpleName(), e);
            throw e; // @Transactional hará rollback automáticamente por rollbackFor = Exception.class
        }
    }

    /**
     * Procesa una hoja específica del Excel
     */
    private ImportResultDTO procesarHoja(Sheet sheet, String tipo, String nombreHoja) {
        log.info("Procesando hoja '{}' como tipo '{}'", nombreHoja, tipo);

        // Para la hoja MASTER, los encabezados reales están en la fila 1 (índice 1), no
        // en la 0
        int headerRowIndex = "master".equalsIgnoreCase(tipo) ? 1 : 0;
        Row headerRow = sheet.getRow(headerRowIndex);
        if (headerRow == null) {
            throw new IllegalArgumentException("La hoja no tiene encabezados en la fila " + (headerRowIndex + 1));
        }

        Map<String, Integer> columnasMap = mapearColumnasPorNombre(headerRow);
        log.info("Columnas mapeadas para hoja '{}': {}", nombreHoja, columnasMap.keySet());

        // Contadores iniciales para rastrear entidades creadas (solo para VALIDACIONES)
        int marcasInicial = 0;
        int origenesInicial = 0;
        int materialesInicial = 0;
        int proveedoresInicial = 0;
        int tiposInicial = 0;
        int clasifGralInicial = 0;
        int clasifGastroInicial = 0;

        if ("validaciones".equalsIgnoreCase(tipo)) {
            marcasInicial = cacheMarcas.size();
            origenesInicial = cacheOrigenes.size();
            materialesInicial = cacheMateriales.size();
            proveedoresInicial = cacheProveedores.size();
            tiposInicial = cacheTipos.size();
            clasifGralInicial = cacheClasifGral.size();
            clasifGastroInicial = cacheClasifGastro.size();
            log.info("📊 Estado inicial de entidades relacionadas: {} marcas, {} orígenes, {} materiales, {} proveedores, {} tipos, {} clasifGral, {} clasifGastro",
                    marcasInicial, origenesInicial, materialesInicial, proveedoresInicial,
                    tiposInicial, clasifGralInicial, clasifGastroInicial);

            // Procesar todas las tablas estructuradas (ListObjects) de la hoja VALIDACIONES
            log.info("🔍 Buscando y procesando todas las tablas estructuradas (ListObjects)...");
            procesarTablasEstructuradas(sheet);
        }

        // ====================================================================
        // FASE 1: PREPROCESAMIENTO - Crear todas las entidades relacionadas
        // ====================================================================
        // IMPORTANTE: Todas las entidades relacionadas deben estar creadas
        // ANTES de procesar las filas de productos para evitar errores de
        // foreign key y mejorar el rendimiento.
        //
        // Entidades que vienen de VALIDACIONES (ya procesadas antes):
        // - marcas, tipos, orígenes, materiales, proveedores, clasificaciones
        //
        // Entidades que se crean aquí (valores fijos):
        // - catálogos (LG GASTRO, LG HOGAR, LG HUDSON, KT GASTRO, DEPTOS)
        // - canales (ML, KT HOGAR, KT GASTRO, LINEA GE, LIZZY)
        // ====================================================================
        if ("master".equalsIgnoreCase(tipo)) {
            log.info("🔧 FASE 1: Preprocesando y creando entidades relacionadas para hoja MASTER...");
            preprocesarEntidadesRelacionadas(sheet, columnasMap);
            log.info("✅ FASE 1 completada: Todas las entidades relacionadas están listas");
        }

        // ====================================================================
        // FASE 2: PROCESAMIENTO - Procesar todas las filas de productos
        // ====================================================================
        List<String> errors = new ArrayList<>();
        int totalRows = 0;
        int successRows = 0;

        // OPTIMIZACIÓN: Tamaño del batch aumentado para coincidir con Hibernate batch_size
        // Antes: 50, Después: 100 (mejor rendimiento en inserciones masivas)
        int batchSize = 100;
        int productosEnBatch = 0;

        // Procesar datos: para MASTER desde la fila 3 (índice 2), para otras desde la
        // fila 2 (índice 1)
        int startRow = "master".equalsIgnoreCase(tipo) ? 2 : 1;
        int lastRowNum = sheet.getLastRowNum();

        // Límite de seguridad: máximo 100,000 filas para evitar loops infinitos
        int maxRows = Math.min(lastRowNum, startRow + 100000);
        int filasVaciasConsecutivas = 0;
        int maxFilasVaciasConsecutivas = 100; // Detener si hay 100 filas vacías consecutivas

        log.info("Procesando filas desde {} hasta {} (total: {} filas posibles, límite de seguridad: {}, batch size: {})",
                startRow + 1, lastRowNum + 1, lastRowNum - startRow + 1, maxRows - startRow + 1, batchSize);

        for (int i = startRow; i <= maxRows; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                filasVaciasConsecutivas++;
                if (filasVaciasConsecutivas >= maxFilasVaciasConsecutivas) {
                    log.info("Deteniendo procesamiento: {} filas vacías consecutivas encontradas (última fila procesada: {})",
                            filasVaciasConsecutivas, i);
                    break;
                }
                continue;
            }

            if (esFilaVacia(row)) {
                filasVaciasConsecutivas++;
                if (filasVaciasConsecutivas >= maxFilasVaciasConsecutivas) {
                    log.info("Deteniendo procesamiento: {} filas vacías consecutivas encontradas (última fila procesada: {})",
                            filasVaciasConsecutivas, i);
                    break;
                }
                continue;
            }

            // Resetear contador de filas vacías si encontramos una fila con datos
            filasVaciasConsecutivas = 0;

            totalRows++;
            String skuFila = null;
            try {
                // Intentar obtener el SKU de la fila para mejor logging
                try {
                    Integer skuColIndex = columnasMap.get("SKU");
                    if (skuColIndex != null && row.getCell(skuColIndex) != null) {
                        String skuStr = obtenerValorCelda(row, skuColIndex);
                        if (skuStr != null && !skuStr.trim().isEmpty()) {
                            skuFila = skuStr.trim();
                        }
                    }
                } catch (Exception ex) {
                    // Ignorar errores al obtener SKU para logging
                }

                // Log cada 100 filas para ver el progreso
                if (totalRows % 100 == 0) {
                    log.info("📊 Procesando fila {} (total procesadas: {}, exitosas: {})", i + 1, totalRows, successRows);
                }

                // Log cada fila para debugging (solo las primeras 10 y luego cada 1000)
                if (totalRows <= 10 || totalRows % 1000 == 0) {
                    if (skuFila != null && !skuFila.isEmpty()) {
                        log.debug("📄 Procesando fila {} (SKU: {})", i + 1, skuFila);
                    } else {
                        log.debug("📄 Procesando fila {}", i + 1);
                    }
                }

                procesarFila(row, tipo, i, columnasMap);
                successRows++;
                productosEnBatch++;

                // Hacer flush cada batchSize productos para optimizar rendimiento
                // Esto permite que Hibernate agrupe múltiples inserts en un solo batch
                if (productosEnBatch >= batchSize) {
                    productoRepository.flush();
                    // OPTIMIZACIÓN: Limpiar el contexto de persistencia para liberar memoria
                    // Esto evita que la sesión de Hibernate acumule entidades y cause OutOfMemory
                    entityManager.clear();
                    productosEnBatch = 0;
                    if (log.isTraceEnabled()) {
                        log.trace("Flush y clear realizados después de procesar {} productos", successRows);
                    }
                }
            } catch (UnsupportedOperationException e) {
                // Si el tipo no está implementado, agregar error pero continuar
                String errorMsg = String.format("Fila %d%s: %s", i + 1,
                        skuFila != null ? " (SKU: " + skuFila + ")" : "", e.getMessage());
                errors.add(errorMsg);
                log.warn("❌ ERROR en fila {} de hoja '{}'{}: {}", i + 1, nombreHoja,
                        skuFila != null ? " (SKU: " + skuFila + ")" : "", e.getMessage());
            } catch (DataIntegrityViolationException e) {
                // Manejar errores de integridad (duplicados, etc.) sin invalidar la transacción
                String errorMsg = String.format("Fila %d%s: Violación de integridad - %s", i + 1,
                        skuFila != null ? " (SKU: " + skuFila + ")" : "",
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                errors.add(errorMsg);
                log.error("❌ ERROR en fila {} de hoja '{}'{}: Violación de integridad - {}", i + 1, nombreHoja,
                        skuFila != null ? " (SKU: " + skuFila + ")" : "",
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                // Continuar con la siguiente fila
            } catch (AssertionFailure e) {
                // Si la sesión está invalidada, registrar el error y detener el procesamiento
                // de esta hoja
                String errorMsg = String.format("Fila %d%s: Sesión de Hibernate invalidada (posible error previo) - %s",
                        i + 1, skuFila != null ? " (SKU: " + skuFila + ")" : "",
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                errors.add(errorMsg);
                log.error("❌ ERROR CRÍTICO en fila {} de hoja '{}'{}: Sesión de Hibernate invalidada. Deteniendo procesamiento de esta hoja. Error: {}",
                        i + 1, nombreHoja, skuFila != null ? " (SKU: " + skuFila + ")" : "", e.getMessage());
                // No continuar procesando más filas si la sesión está invalidada
                break;
            } catch (RuntimeException e) {
                // Verificar si es un error de sesión invalidada (relanzado desde procesarFilaMaster)
                if (e.getMessage() != null && e.getMessage().contains("Sesión de Hibernate invalidada")) {
                    String errorMsg = String.format("Fila %d%s: %s", i + 1,
                            skuFila != null ? " (SKU: " + skuFila + ")" : "", e.getMessage());
                    errors.add(errorMsg);
                    log.error("❌ ERROR CRÍTICO en fila {} de hoja '{}'{}: Sesión de Hibernate invalidada. Deteniendo procesamiento de esta hoja. Error: {}",
                            i + 1, nombreHoja, skuFila != null ? " (SKU: " + skuFila + ")" : "", e.getMessage());
                    // No continuar procesando más filas si la sesión está invalidada
                    break;
                }
                // Si no es un error de sesión invalidada, tratarlo como error genérico
                String errorMsg = String.format("Fila %d%s: %s", i + 1,
                        skuFila != null ? " (SKU: " + skuFila + ")" : "", e.getMessage());
                errors.add(errorMsg);
                log.error("❌ ERROR en fila {} de hoja '{}'{}: {} - {}", i + 1, nombreHoja,
                        skuFila != null ? " (SKU: " + skuFila + ")" : "",
                        e.getMessage(), e.getClass().getSimpleName());
                if (log.isDebugEnabled()) {
                    log.debug("Stack trace completo:", e);
                }
            } catch (Exception e) {
                // Verificar si es un error de configuración (sesión invalidada)
                String exceptionName = e.getClass().getName();
                if (exceptionName.contains("ConfigurationPropertiesBindException") ||
                        (e.getMessage() != null && e.getMessage().contains("DataSourceProperties"))) {
                    String errorMsg = String.format("Fila %d%s: Error de configuración (sesión invalidada) - %s", i + 1,
                            skuFila != null ? " (SKU: " + skuFila + ")" : "",
                            e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                    errors.add(errorMsg);
                    log.error("❌ ERROR CRÍTICO en fila {} de hoja '{}'{}: Error de configuración (sesión invalidada). Deteniendo procesamiento. Error: {}",
                            i + 1, nombreHoja, skuFila != null ? " (SKU: " + skuFila + ")" : "", e.getMessage());
                    // No continuar procesando más filas si la sesión está invalidada
                    break;
                }
                // Verificar si es un error de sesión cerrada
                if (e instanceof IllegalStateException &&
                        e.getMessage() != null && e.getMessage().contains("closed")) {
                    String errorMsg = String.format("Fila %d%s: Sesión de Hibernate cerrada", i + 1,
                            skuFila != null ? " (SKU: " + skuFila + ")" : "");
                    errors.add(errorMsg);
                    log.error("❌ ERROR CRÍTICO en fila {} de hoja '{}'{}: Sesión de Hibernate cerrada. Deteniendo procesamiento. Error: {}",
                            i + 1, nombreHoja, skuFila != null ? " (SKU: " + skuFila + ")" : "", e.getMessage());
                    // No continuar procesando más filas si la sesión está cerrada
                    break;
                }

                // Error genérico
                String errorMsg = String.format("Fila %d%s: %s", i + 1,
                        skuFila != null ? " (SKU: " + skuFila + ")" : "", e.getMessage());
                errors.add(errorMsg);
                log.error("❌ ERROR en fila {} de hoja '{}'{}: {} - {}", i + 1, nombreHoja,
                        skuFila != null ? " (SKU: " + skuFila + ")" : "",
                        e.getMessage(), e.getClass().getSimpleName());
                if (log.isDebugEnabled()) {
                    log.debug("Stack trace completo:", e);
                }
            }
        }

        // Flush final para asegurar que todos los productos pendientes se guarden
        if (productosEnBatch > 0) {
            productoRepository.flush();
            log.debug("Flush final realizado: {} productos pendientes guardados", productosEnBatch);
        }

        // Mostrar resumen de entidades relacionadas creadas (solo para VALIDACIONES)
        if ("validaciones".equalsIgnoreCase(tipo)) {
            int marcasFinal = cacheMarcas.size();
            int origenesFinal = cacheOrigenes.size();
            int materialesFinal = cacheMateriales.size();
            int proveedoresFinal = cacheProveedores.size();
            int tiposFinal = cacheTipos.size();
            int clasifGralFinal = cacheClasifGral.size();
            int clasifGastroFinal = cacheClasifGastro.size();

            int marcasCreadas = marcasFinal - marcasInicial;
            int origenesCreados = origenesFinal - origenesInicial;
            int materialesCreados = materialesFinal - materialesInicial;
            int proveedoresCreados = proveedoresFinal - proveedoresInicial;
            int tiposCreados = tiposFinal - tiposInicial;
            int clasifGralCreadas = clasifGralFinal - clasifGralInicial;
            int clasifGastroCreadas = clasifGastroFinal - clasifGastroInicial;

            log.info("📊 RESUMEN DE ENTIDADES RELACIONADAS CREADAS en hoja '{}':", nombreHoja);
            log.info("   ✓ Marcas: {} creadas (total en cache: {})", marcasCreadas, marcasFinal);
            log.info("   ✓ Orígenes: {} creados (total en cache: {})", origenesCreados, origenesFinal);
            log.info("   ✓ Materiales: {} creados (total en cache: {})", materialesCreados, materialesFinal);
            log.info("   ✓ Proveedores: {} creados (total en cache: {})", proveedoresCreados, proveedoresFinal);
            log.info("   ✓ Tipos: {} creados (total en cache: {})", tiposCreados, tiposFinal);
            log.info("   ✓ ClasifGral: {} creadas (total en cache: {})", clasifGralCreadas, clasifGralFinal);
            log.info("   ✓ ClasifGastro: {} creadas (total en cache: {})", clasifGastroCreadas, clasifGastroFinal);
        }

        if (errors.isEmpty()) {
            log.info("✅ Hoja '{}' procesada exitosamente: {} filas procesadas de {} totales", nombreHoja, successRows, totalRows);
            return ImportResultDTO.success(totalRows, successRows);
        } else {
            log.error("⚠️  Hoja '{}' procesada con errores: {} exitosas, {} con errores de {} totales",
                    nombreHoja, successRows, errors.size(), totalRows);
            log.error("📋 RESUMEN DE ERRORES en hoja '{}':", nombreHoja);
            for (int j = 0; j < Math.min(errors.size(), 10); j++) {
                log.error("   {}. {}", j + 1, errors.get(j));
            }
            if (errors.size() > 10) {
                log.error("   ... y {} errores más (total: {})", errors.size() - 10, errors.size());
            }
            return ImportResultDTO.withErrors(totalRows, successRows, errors.size(), errors);
        }
    }

    /**
     * Preprocesa y crea todas las entidades relacionadas antes de procesar los productos.
     * Esto mejora el rendimiento al crear todas las entidades en batch primero.
     */
    private void preprocesarEntidadesRelacionadas(Sheet sheet, Map<String, Integer> columnasMap) {
        log.info("Iniciando preprocesamiento de entidades relacionadas...");

        // Nota: Las siguientes entidades ahora se obtienen de la hoja VALIDACIONES, no de MASTER:
        // - marcas (tabla VMARCAS)
        // - origenes (VORIGEN)
        // - materiales (tabla MATERIALES)
        // - proveedores (tabla PROVEEDORES)
        // - tipos (tabla TIPO1, TIPO2, TIPO3)
        // - clasificaciones (ClasifGral y ClasifGastro, tabla VCLASIF)
        // IMPORTANTE: La hoja VALIDACIONES debe procesarse ANTES que MASTER para que todas estas
        // entidades estén disponibles cuando se procesen los productos.

        // Segunda fase: crear todas las entidades en batch
        log.info("🔨 Creando entidades relacionadas...");

        // Nota: marcas, tipos, orígenes, materiales, proveedores y clasificaciones se procesan desde VALIDACIONES,
        // no desde aquí

        // Crear catálogos fijos
        String[] catalogosFijos = {"LG GASTRO", "LG HOGAR", "LG HUDSON", "KT GASTRO", "DEPTOS"};
        for (String nombre : catalogosFijos) {
            buscarOCrearCatalogo(nombre);
        }
        log.info("   ✓ {} catálogos fijos creados: {}", catalogosFijos.length, String.join(", ", catalogosFijos));

        // Crear canales fijos
        String[] canalesFijos = {"ML", "KT HOGAR", "KT GASTRO", "LINEA GE", "LIZZY"};
        for (String nombre : canalesFijos) {
            buscarOCrearCanal(nombre);
        }
        log.info("   ✓ {} canales fijos creados: {}", canalesFijos.length, String.join(", ", canalesFijos));

        // Flush final para asegurar que todas las entidades se guarden
        catalogoRepository.flush();
        canalRepository.flush();
        // marcaRepository, tipoRepository, origenRepository, materialRepository, proveedorRepository,
        // clasifGralRepository y clasifGastroRepository se flushan al procesar VALIDACIONES

        log.info("✅ Todas las entidades relacionadas creadas y guardadas");
    }

    /**
     * Importación única de migración: Importa TODO el Excel completo a la base de
     * datos
     * Este método está diseñado para ser usado UNA SOLA VEZ para migrar todos los
     * datos
     * del archivo SUPER MASTER.xlsm a la base de datos MySQL
     */
    @Override
    public ImportCompletoResultDTO importarMigracionCompleta(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }

        if (!isExcelFile(file)) {
            throw new IllegalArgumentException("El archivo debe ser un Excel (.xls, .xlsx o .xlsm)");
        }

        log.info("========================================");
        log.info("INICIANDO IMPORTACIÓN COMPLETA DE MIGRACIÓN");
        log.info("========================================");

        // Limpiar caches al inicio de la importación
        cacheMarcas.clear();
        cacheOrigenes.clear();
        cacheClasifGral.clear();
        cacheProveedores.clear();
        cacheTipos.clear();
        cacheCatalogos.clear();
        cacheCanales.clear();
        cacheMateriales.clear();
        valoresNoEncontradosMaster.clear();

        try (Workbook workbook = abrirWorkbookEficiente(file)) {
            int totalHojas = workbook.getNumberOfSheets();
            log.info("Archivo contiene {} hojas", totalHojas);

            // Listar todas las hojas
            for (int i = 0; i < totalHojas; i++) {
                log.info("  - Hoja {}: '{}'", i + 1, workbook.getSheetName(i));
            }

            Map<String, ImportResultDTO> resultadosPorHoja = new LinkedHashMap<>();
            List<String> erroresGenerales = new ArrayList<>();
            int hojasProcesadas = 0;
            int hojasConErrores = 0;
            int hojasOmitidas = 0;

            // Recolectar hojas a procesar y ordenarlas: VALIDACIONES primero, luego MASTER
            List<SheetInfo> hojasAProcesar = new ArrayList<>();
            for (int i = 0; i < totalHojas; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String nombreHoja = sheet.getSheetName();
                String nombreNormalizado = nombreHoja.trim().toUpperCase();

                if ("VALIDACIONES".equals(nombreNormalizado) || "MASTER".equals(nombreNormalizado)) {
                    // Prioridad: VALIDACIONES = 0, MASTER = 1 (para ordenar)
                    int prioridad = "VALIDACIONES".equals(nombreNormalizado) ? 0 : 1;
                    hojasAProcesar.add(new SheetInfo(sheet, nombreHoja, prioridad));
                } else {
                    hojasOmitidas++;
                }
            }

            // Ordenar por prioridad (VALIDACIONES primero)
            hojasAProcesar.sort(Comparator.comparingInt(SheetInfo::prioridad));

            log.info("Hojas a procesar (en orden): {}",
                    hojasAProcesar.stream().map(SheetInfo::nombre).collect(Collectors.toList()));

            // Procesar cada hoja en el orden correcto
            for (int idx = 0; idx < hojasAProcesar.size(); idx++) {
                SheetInfo sheetInfo = hojasAProcesar.get(idx);
                Sheet sheet = sheetInfo.sheet();
                String nombreHoja = sheetInfo.nombre();

                log.info("\n>>> Procesando hoja {} de {}: '{}'", idx + 1, hojasAProcesar.size(), nombreHoja);

                try {
                    // Determinar el tipo de importación según el nombre de la hoja
                    String tipo = determinarTipoPorNombreHoja(nombreHoja);

                    if (tipo == null) {
                        log.warn("⚠️  Hoja '{}' no reconocida (determinarTipoPorNombreHoja devolvió null), se omite", nombreHoja);
                        hojasOmitidas++;
                        continue;
                    }

                    log.info("   ✓ Tipo detectado: '{}'", tipo);

                    // Procesar la hoja en su propia transacción
                    log.info("   🔄 Iniciando procesamiento de hoja '{}' en transacción separada...", nombreHoja);
                    ImportResultDTO resultado = procesarHojaEnTransaccionSeparada(sheet, tipo, nombreHoja);
                    resultadosPorHoja.put(nombreHoja, resultado);

                    log.info("   ✓ Procesamiento de hoja '{}' completado. Resultado: {} exitosas, {} con errores, {} totales",
                            nombreHoja, resultado.successRows(), resultado.errorRows(), resultado.totalRows());

                    if (resultado.errorRows() > 0) {
                        hojasConErrores++;
                        log.warn("   ⚠️  Hoja '{}' procesada con errores: {} exitosas, {} con errores",
                                nombreHoja, resultado.successRows(), resultado.errorRows());
                    } else {
                        hojasProcesadas++;
                        log.info("   ✅ Hoja '{}' procesada exitosamente: {} filas", nombreHoja, resultado.successRows());
                    }

                } catch (UnsupportedOperationException e) {
                    log.error("   ❌ ERROR: Tipo no implementado para hoja '{}': {}", nombreHoja, e.getMessage());
                    erroresGenerales.add(String.format("Hoja '%s': %s", nombreHoja, e.getMessage()));
                    hojasConErrores++;

                    resultadosPorHoja.put(nombreHoja, ImportResultDTO.withErrors(
                            0, 0, 0, List.of(e.getMessage())));
                } catch (Exception e) {
                    log.error("   ❌ ERROR CRÍTICO procesando hoja '{}': {} - {}", nombreHoja,
                            e.getMessage(), e.getClass().getSimpleName(), e);
                    erroresGenerales.add(String.format("Hoja '%s': %s", nombreHoja, e.getMessage()));
                    hojasConErrores++;

                    resultadosPorHoja.put(nombreHoja, ImportResultDTO.withErrors(
                            0, 0, 0, List.of(e.getMessage())));
                }
            }

            // Resumen final
            log.info("\n========================================");
            log.info("RESUMEN DE IMPORTACIÓN");
            log.info("========================================");
            log.info("Total de hojas: {}", totalHojas);
            log.info("Hojas procesadas exitosamente: {}", hojasProcesadas);
            log.info("Hojas con errores: {}", hojasConErrores);
            log.info("Hojas omitidas (no reconocidas): {}", hojasOmitidas);
            log.info("========================================\n");

            // Snapshot de valores referenciados en MASTER que no existían en las
            // tablas auxiliares (marca/tipo/origen/material/proveedor).
            Map<String, List<String>> valoresNoEncontrados = new LinkedHashMap<>();
            for (Map.Entry<String, Set<String>> e : valoresNoEncontradosMaster.entrySet()) {
                List<String> ordenados = new ArrayList<>(e.getValue());
                Collections.sort(ordenados, String.CASE_INSENSITIVE_ORDER);
                valoresNoEncontrados.put(e.getKey(), ordenados);
            }

            // Generar resultado final
            if (erroresGenerales.isEmpty() && hojasConErrores == 0) {
                return ImportCompletoResultDTO.success(totalHojas, hojasProcesadas, resultadosPorHoja, valoresNoEncontrados);
            } else {
                return ImportCompletoResultDTO.withErrors(
                        totalHojas, hojasProcesadas, hojasConErrores, resultadosPorHoja, erroresGenerales, valoresNoEncontrados);
            }
        }
    }

    // ============================================================
    // MÉTODOS DE PROCESAMIENTO PARA MIGRACIÓN COMPLETA
    // ============================================================

    /**
     * Procesa una fila de la hoja MASTER (productos completos)
     * Columnas esperadas: SKU, PRODUCTO, TITULO WEB, TIPO DE PRODUCTO, CODEXT, UxB,
     * MARCA, LINEA, TIPO, ORIGEN
     */
    private void procesarFilaMaster(Row row, int rowIndex, Map<String, Integer> columnasMap) {
        try {
            String skuRaw = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "SKU"));
            if (skuRaw == null || skuRaw.isBlank()) {
                throw new IllegalArgumentException("SKU es requerido en fila " + (rowIndex + 1));
            }

            // Normalizar SKU (eliminar .0 si es un número decimal)
            String sku = skuRaw.trim();
            if (sku.endsWith(".0")) {
                sku = sku.substring(0, sku.length() - 2);
            }

            // Hacer sku final para usar en lambda
            final String skuFinal = sku;

            // Log de inicio de procesamiento (solo para debugging)
            if (log.isTraceEnabled()) {
                log.trace("Iniciando procesamiento de fila {} (SKU: {})", rowIndex + 1, skuFinal);
            }

            // Buscar o crear producto
            Optional<Producto> productoOpt;
            try {
                productoOpt = productoRepository.findBySku(skuFinal);
            } catch (org.hibernate.AssertionFailure e) {
                // Si la sesión está invalidada, relanzar para detener el procesamiento de la hoja
                log.error("Sesión de Hibernate invalidada al buscar producto SKU {} en fila {}: {}. Deteniendo procesamiento de esta hoja.",
                        skuFinal, rowIndex + 1, e.getMessage());
                throw new RuntimeException("Sesión de Hibernate invalidada - deteniendo procesamiento de hoja", e);
            }
            boolean esNuevo = productoOpt.isEmpty();
            Producto producto = productoOpt.orElseGet(() -> {
                Producto nuevo = new Producto();
                nuevo.setSku(skuFinal);
                // Establecer valores por defecto para campos requeridos
                nuevo.setIva(BigDecimal.ZERO); // IVA por defecto
                nuevo.setFechaCreacion(LocalDateTime.now(ZONA_ARG)); // Fecha de creación
                // Valores por defecto para campos @NotNull (se actualizarán si vienen en el
                // Excel)
                nuevo.setDescripcion(""); // Valor por defecto mínimo
                nuevo.setTituloWeb(""); // Valor por defecto mínimo
                return nuevo;
            });

            // PRODUCTO (descripcion) - máximo 100 caracteres
            if (columnasMap.containsKey("PRODUCTO")) {
                String descripcion = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "PRODUCTO"));
                if (descripcion != null && !descripcion.isBlank()) {
                    producto.setDescripcion(truncar(descripcion.trim(), 100));
                } else if (esNuevo && (producto.getDescripcion() == null || producto.getDescripcion().isEmpty())) {
                    // Si es nuevo y no tiene descripción, usar valor por defecto mínimo
                    producto.setDescripcion("");
                }
            } else if (esNuevo && (producto.getDescripcion() == null || producto.getDescripcion().isEmpty())) {
                // Si es nuevo y no viene la columna, usar valor por defecto mínimo
                producto.setDescripcion("");
            }

            // TITULO WEB - máximo 100 caracteres
            if (columnasMap.containsKey("TITULO WEB")) {
                String tituloWeb = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "TITULO WEB"));
                if (tituloWeb != null && !tituloWeb.isBlank()) {
                    producto.setTituloWeb(truncar(tituloWeb.trim(), 100));
                } else if (esNuevo && (producto.getTituloWeb() == null || producto.getTituloWeb().isEmpty())) {
                    // Si es nuevo y no tiene título web, usar valor por defecto mínimo
                    producto.setTituloWeb("");
                }
            } else if (esNuevo && (producto.getTituloWeb() == null || producto.getTituloWeb().isEmpty())) {
                // Si es nuevo y no viene la columna, usar valor por defecto mínimo
                producto.setTituloWeb("");
            }

            // TIPO DE PRODUCTO (SIMPLE o COMBO) - Guardar 1 si es COMBO, 0 si es SIMPLE
            if (columnasMap.containsKey("TIPO DE PRODUCTO")) {
                String tipoProducto = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "TIPO DE PRODUCTO"));
                if (tipoProducto != null && !tipoProducto.isBlank()) {
                    String tipoUpper = tipoProducto.trim().toUpperCase();
                    // Guardar true (1) si es COMBO, false (0) si es SIMPLE
                    producto.setEsCombo("COMBO".equals(tipoUpper));
                }
            }

            // CODEXT - máximo 45 caracteres
            if (columnasMap.containsKey("CODEXT")) {
                String codExt = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "CODEXT"));
                if (codExt != null && !codExt.isBlank()) {
                    producto.setCodExt(truncar(codExt.trim(), 45));
                }
            }

            // UxB
            if (columnasMap.containsKey("UXB") || columnasMap.containsKey("UxB")) {
                String columnaUxb = columnasMap.containsKey("UXB") ? "UXB" : "UxB";
                String uxbStr = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, columnaUxb));
                if (uxbStr != null && !uxbStr.isBlank()) {
                    try {
                        Integer uxb = Integer.parseInt(uxbStr.trim());
                        if (uxb > 0) {
                            producto.setUxb(uxb);
                        }
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("UXB inválido: " + uxbStr);
                    }
                }
            }

            // MARCA / TIPO / ORIGEN / MATERIAL / PROVEEDOR (opcionales):
            // Si el Excel trae un nombre y NO existe en la tabla auxiliar precargada,
            // se setea el campo en NULL y se registra el valor en valoresNoEncontradosMaster
            // para reportarlo al final (no falla la fila).

            if (columnasMap.containsKey("MARCA")) {
                String marcaNombre = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "MARCA"));
                if (marcaNombre != null && !marcaNombre.isBlank()) {
                    String mn = marcaNombre.trim();
                    Marca marca = marcaRepository.findByNombreIgnoreCase(mn).orElse(null);
                    if (marca == null) {
                        registrarValorNoEncontrado("marca", mn);
                        producto.setMarca(null);
                    } else {
                        producto.setMarca(marca);
                    }
                }
            }

            if (columnasMap.containsKey("TIPO")) {
                String tipoNombre = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "TIPO"));
                if (tipoNombre != null && !tipoNombre.isBlank()) {
                    String tn = tipoNombre.trim();
                    Tipo tipo = tipoRepository.findByNombreIgnoreCase(tn).orElse(null);
                    if (tipo == null) {
                        registrarValorNoEncontrado("tipo", tn);
                        producto.setTipo(null);
                    } else {
                        producto.setTipo(tipo);
                    }
                }
            }

            if (columnasMap.containsKey("ORIGEN")) {
                String origenNombre = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "ORIGEN"));
                if (origenNombre != null && !origenNombre.isBlank()) {
                    String on = origenNombre.trim();
                    Origen origen = origenRepository.findByNombreIgnoreCase(on).orElse(null);
                    if (origen == null) {
                        registrarValorNoEncontrado("origen", on);
                        producto.setOrigen(null);
                    } else {
                        producto.setOrigen(origen);
                    }
                }
            }

            if (columnasMap.containsKey("MATERIAL")) {
                String materialNombre = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "MATERIAL"));
                if (materialNombre != null && !materialNombre.isBlank()) {
                    String mn = materialNombre.trim();
                    Material material = materialRepository.findByNombreIgnoreCase(mn).orElse(null);
                    if (material == null) {
                        registrarValorNoEncontrado("material", mn);
                        producto.setMaterial(null);
                    } else {
                        producto.setMaterial(material);
                    }
                }
            }

            if (columnasMap.containsKey("PROVEEDOR")) {
                String proveedorNombre = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "PROVEEDOR"));
                if (proveedorNombre != null && !proveedorNombre.isBlank()) {
                    String pn = proveedorNombre.trim();
                    Proveedor proveedor = proveedorRepository.findByNombreIgnoreCase(pn).orElse(null);
                    if (proveedor == null) {
                        registrarValorNoEncontrado("proveedor", pn);
                        producto.setProveedor(null);
                    } else {
                        producto.setProveedor(proveedor);
                    }
                }
            }

            // CLASIF_GRAL y CLASIF_GASTRO: por decisión del flujo de importación,
            // siempre se dejan en NULL. Se cargarán por otro proceso después.
            producto.setClasifGral(null);
            producto.setClasifGastro(null);

            // TAG (enum): MAQUINA / REPUESTO / MENAJE
            if (columnasMap.containsKey("TAG")) {
                String tagStr = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "TAG"));
                if (tagStr != null && !tagStr.isBlank()) {
                    String tu = tagStr.trim().toUpperCase();
                    try {
                        producto.setTag(ar.com.leo.super_master_backend.dominio.producto.entity.Tag.valueOf(tu));
                    } catch (IllegalArgumentException e) {
                        log.warn("Tag '{}' inválido para SKU {} - se deja en NULL", tagStr, skuFinal);
                        producto.setTag(null);
                    }
                } else {
                    producto.setTag(null);
                }
            }

            // COSTO
            if (columnasMap.containsKey("COSTO")) {
                String costoStr = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "COSTO"));
                if (costoStr != null && !costoStr.isBlank()) {
                    try {
                        BigDecimal costo = new BigDecimal(costoStr.replace(",", "."));
                        producto.setCosto(costo);
                    } catch (NumberFormatException e) {
                        log.warn("Costo inválido para producto SKU {}: {}", skuFinal, costoStr);
                    }
                }
            }

            // ULTIMA ACT. COSTO - fecha_ult_costo
            if (columnasMap.containsKey("ULTIMA ACT. COSTO")) {
                try {
                    LocalDateTime fechaUltimoCosto = obtenerFechaDeCelda(row,
                            obtenerIndiceColumna(columnasMap, "ULTIMA ACT. COSTO"));
                    if (fechaUltimoCosto != null) {
                        producto.setFechaUltimoCosto(fechaUltimoCosto);
                    }
                } catch (Exception e) {
                    log.warn("Fecha de última actualización de costo inválida para producto SKU {}: {}",
                            skuFinal, e.getMessage());
                }
            }

            // IVA - Campo requerido, establecer si no está definido
            // NOTA: En el Excel el IVA viene como decimal (ej: 0.21), se multiplica por 100 para convertirlo a porcentaje (21)
            if (producto.getIva() == null) {
                // Intentar obtener IVA del Excel si existe la columna "IVA"
                if (columnasMap.containsKey("IVA")) {
                    String ivaStr = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "IVA"));
                    if (ivaStr != null && !ivaStr.isBlank()) {
                        try {
                            BigDecimal ivaDecimal = new BigDecimal(ivaStr.replace(",", "."));
                            // Multiplicar por 100 para convertir de decimal (0.21) a porcentaje (21)
                            BigDecimal iva = ivaDecimal.multiply(BigDecimal.valueOf(100));
                            // Validar que el IVA esté entre 0 y 100 (después de multiplicar)
                            if (iva.compareTo(BigDecimal.ZERO) < 0 || iva.compareTo(new BigDecimal("100")) > 0) {
                                log.warn("IVA fuera de rango para producto SKU {}: {} (convertido a {}%), usando 0",
                                        skuFinal, ivaStr, iva);
                                producto.setIva(BigDecimal.ZERO);
                            } else {
                                producto.setIva(iva);
                            }
                        } catch (NumberFormatException e) {
                            log.warn("IVA inválido para producto SKU {}, usando 0: {}", skuFinal, ivaStr);
                            producto.setIva(BigDecimal.ZERO);
                        }
                    } else {
                        producto.setIva(BigDecimal.ZERO);
                    }
                } else {
                    producto.setIva(BigDecimal.ZERO); // Valor por defecto
                }
            } else {
                // Si ya tiene IVA pero existe la columna, actualizarlo
                if (columnasMap.containsKey("IVA")) {
                    String ivaStr = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "IVA"));
                    if (ivaStr != null && !ivaStr.isBlank()) {
                        try {
                            BigDecimal ivaDecimal = new BigDecimal(ivaStr.replace(",", "."));
                            // Multiplicar por 100 para convertir de decimal (0.21) a porcentaje (21)
                            BigDecimal iva = ivaDecimal.multiply(BigDecimal.valueOf(100));
                            // Validar que el IVA esté entre 0 y 100 (después de multiplicar)
                            if (iva.compareTo(BigDecimal.ZERO) >= 0 && iva.compareTo(new BigDecimal("100")) <= 0) {
                                producto.setIva(iva);
                            } else {
                                log.warn("IVA fuera de rango para producto SKU {}: {} (convertido a {}%), manteniendo valor anterior",
                                        skuFinal, ivaStr, iva);
                            }
                        } catch (NumberFormatException e) {
                            log.warn("IVA inválido para producto SKU {}: {}, manteniendo valor anterior", skuFinal,
                                    ivaStr);
                        }
                    }
                }
            }

            // Fecha de creación - Solo para productos nuevos
            if (producto.getFechaCreacion() == null) {
                producto.setFechaCreacion(LocalDateTime.now(ZONA_ARG));
            }

            // Guardar producto
            Producto productoGuardado = productoRepository.save(producto);

            // Si el producto es nuevo (sin ID) y necesitamos crear relaciones que requieren el ID,
            // hacer flush inmediatamente para obtener el ID. Para productos existentes, el ID ya está disponible.
            // Esto optimiza el rendimiento: solo hacemos flush cuando es absolutamente necesario.
            boolean productoEsNuevo = productoGuardado.getId() == null;
            boolean tieneRelaciones = columnasMap.containsKey("MLA") ||
                    columnasMap.keySet().stream().anyMatch(k ->
                            k.contains("LG ") || k.contains("KT ") || k.contains("DEPTOS") || k.equals("CANAL"));

            if (productoEsNuevo && tieneRelaciones) {
                // Flush inmediato solo para productos nuevos que necesitan relaciones
                productoRepository.flush();
                // Si aún no tiene ID después del flush, buscar nuevamente
                if (productoGuardado.getId() == null) {
                    productoGuardado = productoRepository.findBySku(skuFinal)
                            .orElseThrow(() -> new RuntimeException(
                                    "No se pudo obtener el producto guardado con SKU: " + skuFinal));
                }
            }

            final Producto productoFinal = productoGuardado; // Variable final para usar en lambdas

            // MIX DE PRODUCTOS - Catalogos.
            // Mapeo columnaExcel -> nombreCatálogoEnBD (las claves DEBEN existir en
            // la BD; si no existen, se crean automáticamente vía buscarOCrearCatalogo).
            // Solo se crea la relación si la celda dice "VERDADERO" o "TRUE".
            Map<String, String> mapeoCatalogos = new LinkedHashMap<>();
            mapeoCatalogos.put("LG GASTRO", "LG GASTRO");
            mapeoCatalogos.put("LG HOGAR", "LG HOGAR");
            mapeoCatalogos.put("LG HUDSON", "LG HUDSON");
            mapeoCatalogos.put("KT HOGAR", "KT HOGAR");
            mapeoCatalogos.put("KT GASTRO", "KT GASTRO (SIN IVA)");
            mapeoCatalogos.put("DEPTOS", "DEPTOS");
            for (Map.Entry<String, String> entry : mapeoCatalogos.entrySet()) {
                String columnaExcel = entry.getKey();
                String nombreCatalogoBd = entry.getValue();
                try {
                    if (!columnasMap.containsKey(columnaExcel)) continue;
                    String valor = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, columnaExcel));
                    if (valor == null || valor.isBlank()) continue;
                    if (!valor.equalsIgnoreCase("VERDADERO") && !valor.equalsIgnoreCase("TRUE")) continue;

                    Catalogo catalogoTemp = buscarOCrearCatalogo(nombreCatalogoBd);
                    if (catalogoTemp.getId() == null) {
                        catalogoTemp = catalogoRepository.save(catalogoTemp);
                    }
                    final Catalogo catalogo = catalogoTemp;
                    boolean existe = productoCatalogoRepository.findByProductoId(productoFinal.getId())
                            .stream()
                            .anyMatch(pc -> pc.getCatalogo().getId().equals(catalogo.getId()));
                    if (!existe && productoFinal.getId() != null && catalogo.getId() != null) {
                        ProductoCatalogo productoCatalogo = new ProductoCatalogo(productoFinal, catalogo);
                        productoCatalogoRepository.save(productoCatalogo);
                    }
                } catch (Exception e) {
                    log.warn("Error procesando catálogo '{}' -> '{}' para producto SKU {}: {}",
                            columnaExcel, nombreCatalogoBd, skuFinal, e.getMessage(), e);
                }
            }

            // MLA - mlas.mla y mlas.precio_envio
            if (columnasMap.containsKey("MLA")) {
                try {
                    String mlaStr = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "MLA"));
                    if (mlaStr != null && !mlaStr.isBlank()) {
                        // Usar el MLA existente del producto o crear uno nuevo
                        Mla mla;
                        if (productoFinal.getMla() != null) {
                            mla = productoFinal.getMla();
                            mla.setMla(mlaStr.trim());
                        } else {
                            mla = new Mla();
                            mla.setMla(mlaStr.trim());
                        }

                        // ENVIO (precio_envio)
                        if (columnasMap.containsKey("ENVIO")) {
                            String envioStr = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "ENVIO"));
                            if (envioStr != null && !envioStr.isBlank()) {
                                try {
                                    BigDecimal precioEnvio = new BigDecimal(envioStr.replace(",", "."));
                                    mla.setPrecioEnvio(precioEnvio);
                                } catch (NumberFormatException e) {
                                    log.warn("Precio de envío inválido para producto SKU {}: {}", skuFinal, envioStr);
                                }
                            }
                        }

                        mlaRepository.save(mla);
                        // Asignar MLA al producto
                        productoFinal.setMla(mla);
                        productoRepository.save(productoFinal);
                    }
                } catch (Exception e) {
                    log.warn("Error procesando MLA para producto SKU {}: {}", skuFinal, e.getMessage());
                    // Continuar sin fallar toda la fila
                }
            }

            // Canales (ML, KT HOGAR, KT GASTRO, LINEA GE, LIZZY)
            // Asociar TODOS los productos con TODOS los canales
            String[] nombresCanales = {"ML", "KT HOGAR", "KT GASTRO", "LINEA GE", "LIZZY"};
            // ProductoMargen: 1 por producto.
            //   margen_minorista <- columna GAN.MIN.ML
            //   margen_mayorista <- columna %GAN
            // Si ya existe, se actualiza; si no, se crea. Valores no leíbles -> 0.
            if (productoFinal.getId() != null) {
                BigDecimal margenMin = leerPorcentajeDeColumna(row, columnasMap, "GAN.MIN.ML");
                BigDecimal margenMay = leerPorcentajeDeColumna(row, columnasMap, "%GAN");
                Optional<ProductoMargen> productoMargenOpt = productoMargenRepository
                        .findByProductoId(productoFinal.getId());
                ProductoMargen productoMargen = productoMargenOpt.orElseGet(() -> {
                    ProductoMargen nuevo = new ProductoMargen();
                    nuevo.setProducto(productoFinal);
                    return nuevo;
                });
                productoMargen.setMargenMinorista(margenMin != null ? margenMin : BigDecimal.ZERO);
                productoMargen.setMargenMayorista(margenMay != null ? margenMay : BigDecimal.ZERO);
                productoMargenRepository.save(productoMargen);
            }
            // Asegurar que los canales existen (sin crear ProductoMargen por cada uno)
            for (String nombreCanal : nombresCanales) {
                try {
                    Canal canal = buscarOCrearCanal(nombreCanal);
                    if (canal.getId() == null) {
                        canalRepository.save(canal);
                    }
                } catch (Exception e) {
                    log.warn("Error procesando canal '{}' para producto SKU {}: {}", nombreCanal, skuFinal,
                            e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error en procesarFilaMaster, fila {}: {}", rowIndex + 1, e.getMessage(), e);
            throw new RuntimeException("Error procesando fila " + (rowIndex + 1) + ": " + e.getMessage(), e);
        }
    }

    private ClasifGastro buscarOCrearClasifGastro(String nombre, ClasifGastro padre) {
        // IMPORTANTE: La restricción UNIQUE está solo en 'nombre', no en (nombre, id_padre)
        // Por lo tanto, solo puede haber UNA clasificación con ese nombre,
        // independientemente del padre

        if (nombre == null || nombre.trim().isEmpty()) {
            return null;
        }
        String nombreNormalizado = nombre.trim();
        String key = nombreNormalizado.toUpperCase();
        return cacheClasifGastro.computeIfAbsent(key, k -> {
            // Primero buscar por nombre (sin considerar el padre, ya que la restricción es solo en nombre)
            Optional<ClasifGastro> existente = clasifGastroRepository.findByNombreIgnoreCase(nombreNormalizado);

            if (existente.isPresent()) {
                // Si existe, verificar si el padre coincide. Si no coincide, usar la existente de todas formas
                // (porque la restricción UNIQUE no permite crear otra con el mismo nombre)
                ClasifGastro encontrada = existente.get();
                if (padre != null && encontrada.getPadre() != null
                        && !encontrada.getPadre().getId().equals(padre.getId())) {
                    log.warn("Clasificación Gastro '{}' existe con padre diferente. Usando la existente (ID: {})",
                            nombreNormalizado, encontrada.getId());
                } else if (padre == null && encontrada.getPadre() != null) {
                    log.warn("Clasificación Gastro '{}' existe con padre. Usando la existente (ID: {})",
                            nombreNormalizado, encontrada.getId());
                } else if (padre != null && encontrada.getPadre() == null) {
                    log.warn("Clasificación Gastro '{}' existe sin padre pero se esperaba con padre. Usando la existente (ID: {})",
                            nombreNormalizado, encontrada.getId());
                }
                return encontrada;
            }

            // Si no existe, intentar crear
            try {
                ClasifGastro nueva = new ClasifGastro();
                nueva.setNombre(nombreNormalizado);
                nueva.setPadre(padre);
                return clasifGastroRepository.save(nueva);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Si falla por duplicado (puede haber sido creado por otra fila en paralelo), buscar nuevamente
                log.debug("Intento de crear clasificación Gastro duplicada '{}', buscando existente...", nombreNormalizado);
                return clasifGastroRepository.findByNombreIgnoreCase(nombreNormalizado)
                        .orElseThrow(() -> new RuntimeException(
                                "Error al crear clasificación Gastro '" + nombreNormalizado + "': " + e.getMessage()));
            }
        });
    }

    /**
     * Procesa una fila de la hoja TitulosWeb
     * Columnas: SKU, Nombre
     */
    private void procesarFilaTitulosWeb(Row row, int rowIndex, Map<String, Integer> columnasMap) {
        String sku = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "SKU"));
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU es requerido");
        }

        Producto producto = productoRepository.findBySku(sku)
                .orElseThrow(() -> new IllegalArgumentException("Producto con SKU '" + sku + "' no encontrado"));

        String nombre = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "NOMBRE"));
        if (nombre != null && !nombre.isBlank()) {
            producto.setTituloWeb(truncar(nombre.trim(), 100));
            productoRepository.save(producto);
        }
    }

    /**
     * Procesa una fila de la hoja Validaciones (entidades maestras)
     * <p>
     * Tabla VMARCAS:
     * - VMARCA (primer nivel/padre), VLINEA (segundo nivel/hijo)
     * <p>
     * Tabla MATERIALES:
     * - MATERIAL
     * <p>
     * Tabla PROVEEDORES:
     * - PROVEEDOR, ALIAS (para apodo)
     * <p>
     * Tabla TIPOS:
     * - TIPO1 (nivel 1, sin padre)
     * - TIPO2 (nivel 2, hijo de TIPO1), columna ID TIPO1 indica el padre
     * - TIPO3 (nivel 3, hijo de TIPO2), columna ID TIPO2 indica el padre
     * <p>
     * Tabla VCLASIF:
     * - VCLASIF1, VCLASIF2 → ClasifGral (VCLASIF2 es hija de VCLASIF1)
     * - VCLASIF3, VCLASIF4 → ClasifGastro (VCLASIF4 es hija de VCLASIF3)
     * <p>
     * Otras:
     * - VORIGEN
     */
    private void procesarFilaValidaciones(Row row, int rowIndex, Map<String, Integer> columnasMap) {
        List<String> erroresFila = new ArrayList<>();

        try {
            // VMARCA y VLINEA - Procesar marcas con jerarquía
            // VMARCA es el primer nivel (padre), VLINEA es el segundo nivel (hijo)
            if (columnasMap.containsKey("VMARCA")) {
                String marcaPadreNombre = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "VMARCA"));
                if (marcaPadreNombre != null && !marcaPadreNombre.isBlank()) {
                    try {
                        Marca marcaPadre = buscarOCrearMarca(marcaPadreNombre.trim());
                        // Asegurar que la marca padre tenga ID
                        if (marcaPadre.getId() == null) {
                            marcaRepository.flush();
                            marcaPadre = marcaRepository.findByNombreIgnoreCase(marcaPadreNombre.trim())
                                    .orElseThrow(() -> new RuntimeException(
                                            "No se pudo crear o encontrar marca: " + marcaPadreNombre));
                        }

                        // Si hay VLINEA, crear la marca hijo asociada al padre
                        if (columnasMap.containsKey("VLINEA")) {
                            String marcaHijoNombre = obtenerValorCelda(row,
                                    obtenerIndiceColumna(columnasMap, "VLINEA"));
                            if (marcaHijoNombre != null && !marcaHijoNombre.isBlank()) {
                                try {
                                    Marca marcaHijo = buscarOCrearMarca(marcaHijoNombre.trim());
                                    // Asegurar que la marca hijo tenga ID
                                    if (marcaHijo.getId() == null) {
                                        marcaRepository.flush();
                                        marcaHijo = marcaRepository.findByNombreIgnoreCase(marcaHijoNombre.trim())
                                                .orElseThrow(() -> new RuntimeException(
                                                        "No se pudo crear o encontrar marca: " + marcaHijoNombre));
                                    }
                                    // Establecer la relación padre-hijo solo si no tiene padre o tiene un padre
                                    // diferente
                                    Marca padreActual = marcaHijo.getPadre();
                                    if (padreActual == null || !padreActual.getId().equals(marcaPadre.getId())) {
                                        marcaHijo.setPadre(marcaPadre);
                                        marcaRepository.save(marcaHijo);
                                    }
                                } catch (Exception e) {
                                    String errorMsg = String.format("Error en columna VLINEA='%s' (padre: '%s'): %s",
                                            marcaHijoNombre, marcaPadreNombre, e.getMessage());
                                    erroresFila.add(errorMsg);
                                    log.warn("❌ Fila {} VALIDACIONES - {}", rowIndex + 1, errorMsg);
                                    // Continuar sin fallar toda la fila
                                }
                            }
                        }
                    } catch (Exception e) {
                        String errorMsg = String.format("Error en columna VMARCA='%s': %s", marcaPadreNombre, e.getMessage());
                        erroresFila.add(errorMsg);
                        log.warn("❌ Fila {} VALIDACIONES - {}", rowIndex + 1, errorMsg);
                        // Continuar sin fallar toda la fila
                    }
                }
            }

            // VORIGEN
            if (columnasMap.containsKey("VORIGEN")) {
                String origenNombre = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "VORIGEN"));
                if (origenNombre != null && !origenNombre.isBlank()) {
                    try {
                        buscarOCrearOrigen(origenNombre.trim());
                    } catch (Exception e) {
                        String errorMsg = String.format("Error en columna VORIGEN='%s': %s", origenNombre, e.getMessage());
                        erroresFila.add(errorMsg);
                        log.warn("❌ Fila {} VALIDACIONES - {}", rowIndex + 1, errorMsg);
                        // Continuar sin fallar toda la fila
                    }
                }
            }

            // MATERIALES - Tabla MATERIALES, columna MATERIAL
            if (columnasMap.containsKey("MATERIAL")) {
                String materialNombre = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "MATERIAL"));
                if (materialNombre != null && !materialNombre.isBlank()) {
                    try {
                        buscarOCrearMaterial(materialNombre.trim());
                    } catch (Exception e) {
                        String errorMsg = String.format("Error en columna MATERIAL='%s': %s", materialNombre, e.getMessage());
                        erroresFila.add(errorMsg);
                        log.warn("❌ Fila {} VALIDACIONES - {}", rowIndex + 1, errorMsg);
                    }
                }
            }

            // PROVEEDORES - Tabla PROVEEDORES, columna PROVEEDOR y ALIAS para apodo
            if (columnasMap.containsKey("PROVEEDOR")) {
                String proveedorNombre = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "PROVEEDOR"));
                if (proveedorNombre != null && !proveedorNombre.isBlank()) {
                    try {
                        String alias = null;
                        if (columnasMap.containsKey("ALIAS")) {
                            alias = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "ALIAS"));
                        }
                        buscarOCrearProveedor(proveedorNombre.trim(), alias);
                    } catch (Exception e) {
                        String errorMsg = String.format("Error en columna PROVEEDOR='%s': %s", proveedorNombre, e.getMessage());
                        erroresFila.add(errorMsg);
                        log.warn("❌ Fila {} VALIDACIONES - {}", rowIndex + 1, errorMsg);
                    }
                }
            }

            // TIPOS - Tabla TIPO1, TIPO2, TIPO3 con jerarquía
            // TIPO1: nivel 1 (sin padre)
            // TIPO2: nivel 2 (hijo de TIPO1), columna ID TIPO1 indica el padre
            // TIPO3: nivel 3 (hijo de TIPO2), columna ID TIPO2 indica el padre
            if (columnasMap.containsKey("TIPO1")) {
                String tipo1Nombre = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "TIPO1"));
                if (tipo1Nombre != null && !tipo1Nombre.isBlank()) {
                    try {
                        buscarOCrearTipo(tipo1Nombre.trim(), null); // Sin padre para nivel 1
                    } catch (Exception e) {
                        String errorMsg = String.format("Error en columna TIPO1='%s': %s", tipo1Nombre, e.getMessage());
                        erroresFila.add(errorMsg);
                        log.warn("❌ Fila {} VALIDACIONES - {}", rowIndex + 1, errorMsg);
                    }
                }
            }

            // TIPO2: necesita ID TIPO1 para encontrar el padre
            if (columnasMap.containsKey("TIPO2")) {
                String tipo2Nombre = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "TIPO2"));
                if (tipo2Nombre != null && !tipo2Nombre.isBlank()) {
                    try {
                        Tipo padreTipo2 = null;
                        if (columnasMap.containsKey("ID TIPO1")) {
                            String tipo1IdStr = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "ID TIPO1"));
                            if (tipo1IdStr != null && !tipo1IdStr.isBlank()) {
                                try {
                                    Integer tipo1Id = Integer.parseInt(tipo1IdStr.trim());
                                    padreTipo2 = tipoRepository.findById(tipo1Id).orElse(null);
                                    if (padreTipo2 == null) {
                                        log.warn("⚠️  Fila {} VALIDACIONES - ID TIPO1={} no encontrado para TIPO2='{}'",
                                                rowIndex + 1, tipo1Id, tipo2Nombre);
                                    }
                                } catch (NumberFormatException e) {
                                    log.warn("⚠️  Fila {} VALIDACIONES - ID TIPO1='{}' no es un número válido para TIPO2='{}'",
                                            rowIndex + 1, tipo1IdStr, tipo2Nombre);
                                }
                            }
                        }
                        buscarOCrearTipo(tipo2Nombre.trim(), padreTipo2);
                    } catch (Exception e) {
                        String errorMsg = String.format("Error en columna TIPO2='%s': %s", tipo2Nombre, e.getMessage());
                        erroresFila.add(errorMsg);
                        log.warn("❌ Fila {} VALIDACIONES - {}", rowIndex + 1, errorMsg);
                    }
                }
            }

            // TIPO3: necesita ID TIPO2 para encontrar el padre
            if (columnasMap.containsKey("TIPO3")) {
                String tipo3Nombre = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "TIPO3"));
                if (tipo3Nombre != null && !tipo3Nombre.isBlank()) {
                    try {
                        Tipo padreTipo3 = null;
                        if (columnasMap.containsKey("ID TIPO2")) {
                            String tipo2IdStr = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "ID TIPO2"));
                            if (tipo2IdStr != null && !tipo2IdStr.isBlank()) {
                                try {
                                    Integer tipo2Id = Integer.parseInt(tipo2IdStr.trim());
                                    padreTipo3 = tipoRepository.findById(tipo2Id).orElse(null);
                                    if (padreTipo3 == null) {
                                        log.warn("⚠️  Fila {} VALIDACIONES - ID TIPO2={} no encontrado para TIPO3='{}'",
                                                rowIndex + 1, tipo2Id, tipo3Nombre);
                                    }
                                } catch (NumberFormatException e) {
                                    log.warn("⚠️  Fila {} VALIDACIONES - ID TIPO2='{}' no es un número válido para TIPO3='{}'",
                                            rowIndex + 1, tipo2IdStr, tipo3Nombre);
                                }
                            }
                        }
                        buscarOCrearTipo(tipo3Nombre.trim(), padreTipo3);
                    } catch (Exception e) {
                        String errorMsg = String.format("Error en columna TIPO3='%s': %s", tipo3Nombre, e.getMessage());
                        erroresFila.add(errorMsg);
                        log.warn("❌ Fila {} VALIDACIONES - {}", rowIndex + 1, errorMsg);
                    }
                }
            }

            // VCLASIF1, VCLASIF2 → ClasifGral (VCLASIF2 es hija de VCLASIF1)
            // VCLASIF3, VCLASIF4 → ClasifGastro (VCLASIF4 es hija de VCLASIF3)

            // Procesar ClasifGral (VCLASIF1 y VCLASIF2)
            ClasifGral clasifGralPadre = null;
            if (columnasMap.containsKey("VCLASIF1")) {
                String clasif1Nombre = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "VCLASIF1"));
                if (clasif1Nombre != null && !clasif1Nombre.isBlank()) {
                    try {
                        clasifGralPadre = buscarOCrearClasifGral(clasif1Nombre.trim(), null);
                    } catch (Exception e) {
                        String errorMsg = String.format("Error en columna VCLASIF1='%s': %s",
                                clasif1Nombre, e.getMessage());
                        erroresFila.add(errorMsg);
                        log.warn("❌ Fila {} VALIDACIONES - {}", rowIndex + 1, errorMsg);
                    }
                }
            }

            if (columnasMap.containsKey("VCLASIF2")) {
                String clasif2Nombre = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "VCLASIF2"));
                if (clasif2Nombre != null && !clasif2Nombre.isBlank()) {
                    try {
                        buscarOCrearClasifGral(clasif2Nombre.trim(), clasifGralPadre);
                    } catch (Exception e) {
                        String errorMsg = String.format("Error en columna VCLASIF2='%s' (hija de VCLASIF1): %s",
                                clasif2Nombre, e.getMessage());
                        erroresFila.add(errorMsg);
                        log.warn("❌ Fila {} VALIDACIONES - {}", rowIndex + 1, errorMsg);
                    }
                }
            }

            // Procesar ClasifGastro (VCLASIF3 y VCLASIF4)
            ClasifGastro clasifGastroPadre = null;
            if (columnasMap.containsKey("VCLASIF3")) {
                String clasif3Nombre = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "VCLASIF3"));
                if (clasif3Nombre != null && !clasif3Nombre.isBlank()) {
                    try {
                        clasifGastroPadre = buscarOCrearClasifGastro(clasif3Nombre.trim(), null);
                    } catch (Exception e) {
                        String errorMsg = String.format("Error en columna VCLASIF3='%s': %s",
                                clasif3Nombre, e.getMessage());
                        erroresFila.add(errorMsg);
                        log.warn("❌ Fila {} VALIDACIONES - {}", rowIndex + 1, errorMsg);
                    }
                }
            }

            if (columnasMap.containsKey("VCLASIF4")) {
                String clasif4Nombre = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "VCLASIF4"));
                if (clasif4Nombre != null && !clasif4Nombre.isBlank()) {
                    try {
                        buscarOCrearClasifGastro(clasif4Nombre.trim(), clasifGastroPadre);
                    } catch (Exception e) {
                        String errorMsg = String.format("Error en columna VCLASIF4='%s' (hija de VCLASIF3): %s",
                                clasif4Nombre, e.getMessage());
                        erroresFila.add(errorMsg);
                        log.warn("❌ Fila {} VALIDACIONES - {}", rowIndex + 1, errorMsg);
                    }
                }
            }

            // Si hay errores en la fila, lanzar excepción con todos los errores
            if (!erroresFila.isEmpty()) {
                String mensajeCompleto = String.format("Fila %d de VALIDACIONES tiene %d error(es): %s",
                        rowIndex + 1, erroresFila.size(), String.join("; ", erroresFila));
                throw new RuntimeException(mensajeCompleto);
            }
        } catch (RuntimeException e) {
            // Re-lanzar RuntimeException tal cual (ya tiene el mensaje formateado)
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Error general procesando fila %d de VALIDACIONES: %s",
                    rowIndex + 1, e.getMessage());
            log.error("❌ ERROR CRÍTICO en fila {} de VALIDACIONES: {}", rowIndex + 1, e.getMessage(), e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    /**
     * Procesa una fila de la hoja MLA-ENVIOS
     * Columnas: MLA, SKU, CATEGORIA, etc.
     */
    private void procesarFilaMlaEnvios(Row row, int rowIndex, Map<String, Integer> columnasMap) {
        String mla = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "MLA"));
        if (mla == null || mla.isBlank()) {
            throw new IllegalArgumentException("MLA es requerido");
        }

        String skuRaw = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "SKU"));
        if (skuRaw == null || skuRaw.isBlank()) {
            throw new IllegalArgumentException("SKU es requerido");
        }

        // Normalizar SKU (eliminar .0 si es un número decimal)
        String skuTemp = skuRaw.trim();
        if (skuTemp.endsWith(".0")) {
            skuTemp = skuTemp.substring(0, skuTemp.length() - 2);
        }
        final String sku = skuTemp; // Variable final para usar en lambda

        // Buscar o crear producto (similar a procesarFilaMaster)
        Producto producto = productoRepository.findBySku(sku)
                .orElseGet(() -> {
                    // Si el producto no existe, crear uno básico con valores por defecto para
                    // satisfacer @NotNull
                    Producto nuevo = new Producto();
                    nuevo.setSku(sku);
                    nuevo.setDescripcion(""); // Default para @NotNull
                    nuevo.setTituloWeb(""); // Default para @NotNull
                    nuevo.setIva(BigDecimal.ZERO);
                    nuevo.setFechaCreacion(LocalDateTime.now(ZONA_ARG));
                    // Establecer valores por defecto para satisfacer @NotNull
                    nuevo.setOrigen(buscarOCrearOrigen("SIN ORIGEN"));
                    nuevo.setClasifGral(buscarOCrearClasifGral("SIN CLASIFICACION"));
                    nuevo.setTipo(buscarOCrearTipoPorId(0, "SIN TIPO")); // Buscar o crear tipo por defecto
                    return productoRepository.save(nuevo);
                });

        // Usar el MLA existente del producto o crear uno nuevo
        Mla mlaEntity;
        if (producto.getMla() != null) {
            mlaEntity = producto.getMla();
            mlaEntity.setMla(mla);
        } else {
            mlaEntity = new Mla();
            mlaEntity.setMla(mla);
        }

        // Actualizar precio de envío si existe la columna
        if (columnasMap.containsKey("PRECIO FINAL")) {
            String precioStr = obtenerValorCelda(row, obtenerIndiceColumna(columnasMap, "PRECIO FINAL"));
            if (precioStr != null && !precioStr.isBlank()) {
                try {
                    BigDecimal precio = new BigDecimal(precioStr.replace(",", "."));
                    mlaEntity.setPrecioEnvio(precio);
                } catch (NumberFormatException e) {
                    // Ignorar si no se puede parsear
                }
            }
        }

        mlaRepository.save(mlaEntity);
        // Asignar MLA al producto
        producto.setMla(mlaEntity);
        productoRepository.save(producto);
    }

    // ============================================================
    // MÉTODOS AUXILIARES PARA BUSCAR O CREAR ENTIDADES
    // ============================================================

    private Marca buscarOCrearMarca(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            return null;
        }
        String nombreNormalizado = nombre.trim();
        String key = nombreNormalizado.toUpperCase();
        return cacheMarcas.computeIfAbsent(key, k -> {
            return marcaRepository.findByNombreIgnoreCase(nombreNormalizado)
                    .orElseGet(() -> {
                        Marca nueva = new Marca();
                        nueva.setNombre(nombreNormalizado);
                        return marcaRepository.save(nueva);
                    });
        });
    }

    private Origen buscarOCrearOrigen(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            return null;
        }
        String nombreNormalizado = nombre.trim();
        String key = nombreNormalizado.toUpperCase();
        return cacheOrigenes.computeIfAbsent(key, k -> {
            return origenRepository.findByNombreIgnoreCase(nombreNormalizado)
                    .orElseGet(() -> {
                        Origen nuevo = new Origen();
                        nuevo.setNombre(nombreNormalizado);
                        return origenRepository.save(nuevo);
                    });
        });
    }

    private Tipo buscarOCrearTipo(String nombre) {
        return buscarOCrearTipo(nombre, null);
    }

    private Tipo buscarOCrearTipo(String nombre, Tipo padre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            return buscarOCrearTipoPorId(0, "SIN TIPO");
        }
        String nombreNormalizado = nombre.trim();
        // Clave del cache: incluir el padre para diferenciar tipos con el mismo nombre pero diferentes padres
        // Si el padre es null, usar "null", si tiene padre, usar su ID
        String keyPadre = (padre == null || padre.getId() == null) ? "null" : String.valueOf(padre.getId());
        String key = nombreNormalizado.toUpperCase() + "|PADRE:" + keyPadre;

        return cacheTipos.computeIfAbsent(key, k -> {
            // Buscar por nombre Y padre (puede haber tipos con el mismo nombre pero diferentes padres)
            // Si el padre tiene ID, buscar por nombre y padre
            // Si el padre es null, buscar solo por nombre (tipos raíz)
            Optional<Tipo> tipoOpt;
            if (padre != null && padre.getId() != null) {
                tipoOpt = tipoRepository.findByNombreIgnoreCaseAndPadre(nombreNormalizado, padre);
            } else {
                // Para tipos sin padre, buscar por nombre y padre null
                tipoOpt = tipoRepository.findByNombreIgnoreCaseAndPadre(nombreNormalizado, null);
            }

            if (tipoOpt.isPresent()) {
                return tipoOpt.get();
            }

            // Si no existe, crear uno nuevo
            Tipo nuevo = new Tipo();
            nuevo.setNombre(nombreNormalizado);
            nuevo.setPadre(padre);
            Tipo guardado = tipoRepository.save(nuevo);
            // Hacer flush para asegurar que el tipo tenga ID antes de usarlo como padre
            tipoRepository.flush();
            return guardado;
        });
    }

    /**
     * Busca un tipo por ID, o lo crea si no existe
     *
     * @param id            El ID del tipo a buscar
     * @param nombreDefault El nombre a usar si se debe crear el tipo
     * @return El tipo encontrado o cREADo
     */
    private Tipo buscarOCrearTipoPorId(Integer id, String nombreDefault) {
        // Crear variable final para usar en lambdas
        final Integer idBuscado = (id == null) ? 0 : id;
        return tipoRepository.findById(idBuscado)
                .orElseGet(() -> {
                    // Si el tipo con id no existe, intentar buscar uno por nombre
                    if (nombreDefault != null && !nombreDefault.trim().isEmpty()) {
                        String nombreDefaultNormalizado = nombreDefault.trim();
                        String key = nombreDefaultNormalizado.toUpperCase();
                        return cacheTipos.computeIfAbsent(key, k -> {
                            return tipoRepository.findByNombreIgnoreCase(nombreDefaultNormalizado)
                                    .orElseGet(() -> {
                                        // Si tampoco existe por nombre, crear uno nuevo
                                        Tipo nuevo = new Tipo();
                                        nuevo.setNombre(nombreDefaultNormalizado);
                                        Tipo guardado = tipoRepository.save(nuevo);
                                        log.warn("Tipo con id {} no existía, se creó uno nuevo con id {} y nombre '{}'",
                                                idBuscado, guardado.getId(), guardado.getNombre());
                                        return guardado;
                                    });
                        });
                    }
                    // Si no hay nombre por defecto, crear uno con nombre "SIN TIPO"
                    String key = "SIN TIPO";
                    return cacheTipos.computeIfAbsent(key, k -> {
                        Tipo nuevo = new Tipo();
                        nuevo.setNombre("SIN TIPO");
                        Tipo guardado = tipoRepository.save(nuevo);
                        log.warn("No había tipos en la BD, se creó uno nuevo con id {} y nombre 'SIN TIPO'",
                                guardado.getId());
                        return guardado;
                    });
                });
    }

    private ClasifGral buscarOCrearClasifGral(String nombre) {
        return buscarOCrearClasifGral(nombre, null);
    }

    private ClasifGral buscarOCrearClasifGral(String nombre, ClasifGral padre) {
        // IMPORTANTE: La restricción UNIQUE está solo en 'nombre', no en (nombre,
        // id_padre)
        // Por lo tanto, solo puede haber UNA clasificación con ese nombre,
        // independientemente del padre

        if (nombre == null || nombre.trim().isEmpty()) {
            return null;
        }
        String nombreNormalizado = nombre.trim();
        String key = nombreNormalizado.toUpperCase();
        return cacheClasifGral.computeIfAbsent(key, k -> {
            // Primero buscar por nombre (sin considerar el padre, ya que la restricción es
            // solo en nombre)
            Optional<ClasifGral> existente = clasifGralRepository.findByNombreIgnoreCase(nombreNormalizado);

            if (existente.isPresent()) {
                // Si existe, verificar si el padre coincide. Si no coincide, usar la existente
                // de todas formas
                // (porque la restricción UNIQUE no permite crear otra con el mismo nombre)
                ClasifGral encontrada = existente.get();
                if (padre != null && encontrada.getPadre() != null
                        && !encontrada.getPadre().getId().equals(padre.getId())) {
                    log.warn("Clasificación '{}' existe con padre diferente. Usando la existente (ID: {})",
                            nombreNormalizado, encontrada.getId());
                } else if (padre == null && encontrada.getPadre() != null) {
                    log.warn("Clasificación '{}' existe con padre. Usando la existente (ID: {})",
                            nombreNormalizado, encontrada.getId());
                } else if (padre != null && encontrada.getPadre() == null) {
                    log.warn("Clasificación '{}' existe sin padre pero se esperaba con padre. Usando la existente (ID: {})",
                            nombreNormalizado, encontrada.getId());
                }
                return encontrada;
            }

            // Si no existe, intentar crear
            try {
                ClasifGral nueva = new ClasifGral();
                nueva.setNombre(nombreNormalizado);
                nueva.setPadre(padre);
                return clasifGralRepository.save(nueva);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Si falla por duplicado (puede haber sido cREADo por otra fila en paralelo),
                // buscar nuevamente
                log.debug("Intento de crear clasificación duplicada '{}', buscando existente...", nombreNormalizado);
                return clasifGralRepository.findByNombreIgnoreCase(nombreNormalizado)
                        .orElseThrow(() -> new RuntimeException(
                                "No se pudo crear ni encontrar clasificación: " + nombreNormalizado + " - " + e.getMessage()));
            }
        });
    }

    private Catalogo buscarOCrearCatalogo(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            return null;
        }
        String nombreNormalizado = nombre.trim();
        String key = nombreNormalizado.toUpperCase();
        return cacheCatalogos.computeIfAbsent(key, k -> {
            return catalogoRepository.findByNombreIgnoreCase(nombreNormalizado)
                    .orElseGet(() -> {
                        Catalogo nuevo = new Catalogo();
                        nuevo.setNombre(nombreNormalizado);
                        return catalogoRepository.save(nuevo);
                    });
        });
    }

    private Canal buscarOCrearCanal(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            return null;
        }
        String nombreNormalizado = nombre.trim();
        String key = nombreNormalizado.toUpperCase();
        return cacheCanales.computeIfAbsent(key, k -> {
            return canalRepository.findByNombreIgnoreCase(nombreNormalizado)
                    .orElseGet(() -> {
                        Canal nuevo = new Canal();
                        nuevo.setNombre(nombreNormalizado);
                        return canalRepository.save(nuevo);
                    });
        });
    }

    private Material buscarOCrearMaterial(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            return null;
        }
        String nombreNormalizado = nombre.trim();
        String key = nombreNormalizado.toUpperCase();
        return cacheMateriales.computeIfAbsent(key, k -> {
            return materialRepository.findByNombreIgnoreCase(nombreNormalizado)
                    .orElseGet(() -> {
                        Material nuevo = new Material();
                        nuevo.setNombre(nombreNormalizado);
                        return materialRepository.save(nuevo);
                    });
        });
    }

    private Proveedor buscarOCrearProveedor(String nombre) {
        return buscarOCrearProveedor(nombre, null);
    }

    private Proveedor buscarOCrearProveedor(String nombre, String apodo) {
        if (nombre == null || nombre.trim().isEmpty()) {
            return null;
        }
        String nombreNormalizado = nombre.trim();
        String key = nombreNormalizado.toUpperCase();
        return cacheProveedores.computeIfAbsent(key, k -> {
            return proveedorRepository.findByNombreIgnoreCase(nombreNormalizado)
                    .orElseGet(() -> {
                        Proveedor nuevo = new Proveedor();
                        nuevo.setNombre(nombreNormalizado);
                        // El apodo es requerido
                        if (apodo != null && !apodo.trim().isEmpty()) {
                            String apodoNormalizado = apodo.trim();
                            nuevo.setApodo(apodoNormalizado.length() > 50 ? apodoNormalizado.substring(0, 50) : apodoNormalizado);
                        } else {
                            // Usar el nombre como apodo por defecto (truncado a 50 caracteres)
                            String apodoDefault = nombreNormalizado.length() > 50 ? nombreNormalizado.substring(0, 50) : nombreNormalizado;
                            nuevo.setApodo(apodoDefault);
                        }
                        return proveedorRepository.save(nuevo);
                    });
        });
    }

    // ============================================================
    // IMPORTACIÓN DE COSTOS DESDE EXCEL
    // ============================================================

    @Override
    // noRollbackFor: el motor de cálculo (recalcularProductoEnTodosLosCanales) es @Transactional
    // y puede tirar NotFoundException si el producto no tiene márgenes/canal/conceptos.
    // El catch del loop atrapa la excepción pero sin noRollbackFor la tx outer quedaría
    // marcada rollback-only y al commit se tiraría UnexpectedRollbackException.
    @Transactional(noRollbackFor = NotFoundException.class)
    public ImportCostosResultDTO importarCostos(MultipartFile file) throws IOException {
        log.info("Iniciando importación de costos desde archivo: {}", file.getOriginalFilename());

        List<String> skusNoEncontrados = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        List<Integer> productosARecalcular = new ArrayList<>();
        int productosActualizados = 0;
        int proveedoresCreados = 0;

        // Limpiar cache de proveedores para contar los nuevos
        cacheProveedores.clear();
        int proveedoresAntes = (int) proveedorRepository.count();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            // Obtener encabezados de la primera fila
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return ImportCostosResultDTO.withErrors(List.of("El archivo no tiene encabezados"));
            }

            Map<String, Integer> columnasMap = mapearColumnasPorNombre(headerRow);

            // Validar columnas requeridas
            if (!columnasMap.containsKey("CODIGO")) {
                return ImportCostosResultDTO.withErrors(List.of("Falta la columna CODIGO"));
            }

            // Procesar cada fila de datos
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || esFilaVacia(row)) {
                    continue;
                }

                try {
                    String sku = obtenerValorCelda(row, columnasMap.get("CODIGO"));
                    if (sku == null || sku.isBlank()) {
                        continue;
                    }

                    // Buscar producto por SKU
                    Optional<Producto> productoOpt = productoRepository.findBySku(sku.trim());
                    if (productoOpt.isEmpty()) {
                        skusNoEncontrados.add(sku);
                        continue;
                    }

                    Producto producto = productoOpt.get();
                    boolean actualizado = false;
                    boolean requiereRecalculo = false;

                    // Guardar valores anteriores para detectar cambios que afectan el precio
                    BigDecimal costoAnterior = producto.getCosto();
                    BigDecimal ivaAnterior = producto.getIva();
                    Integer proveedorIdAnterior = producto.getProveedor() != null ? producto.getProveedor().getId() : null;

                    // PRODUCTO → descripcion
                    if (columnasMap.containsKey("PRODUCTO")) {
                        String descripcion = obtenerValorCelda(row, columnasMap.get("PRODUCTO"));
                        if (descripcion != null && !descripcion.isBlank()) {
                            producto.setDescripcion(descripcion.trim().length() > 100
                                    ? descripcion.trim().substring(0, 100)
                                    : descripcion.trim());
                            actualizado = true;
                        }
                    }

                    // COSTO → costo
                    if (columnasMap.containsKey("COSTO")) {
                        String costoStr = obtenerValorCelda(row, columnasMap.get("COSTO"));
                        if (costoStr != null && !costoStr.isBlank()) {
                            try {
                                String costoNormalizado = normalizarNumero(costoStr);
                                BigDecimal costo = new BigDecimal(costoNormalizado)
                                        .setScale(2, java.math.RoundingMode.HALF_UP); // Redondear a 2 decimales
                                // Validar que no exceda el límite de la columna DECIMAL(10,2)
                                if (costo.compareTo(COSTO_MAXIMO) > 0) {
                                    errores.add("Fila " + (i + 1) + ": COSTO excede límite (" + costoStr + " > 99,999,999.99)");
                                } else if (costo.compareTo(BigDecimal.ZERO) >= 0) {
                                    producto.setCosto(costo);
                                    actualizado = true;
                                }
                            } catch (NumberFormatException e) {
                                errores.add("Fila " + (i + 1) + ": COSTO inválido '" + costoStr + "'");
                            }
                        }
                    }

                    // CODIGO EXTERNO → cod_ext
                    if (columnasMap.containsKey("CODIGO EXTERNO")) {
                        String codExt = obtenerValorCelda(row, columnasMap.get("CODIGO EXTERNO"));
                        if (codExt != null && !codExt.isBlank()) {
                            producto.setCodExt(codExt.trim().length() > 45
                                    ? codExt.trim().substring(0, 45)
                                    : codExt.trim());
                            actualizado = true;
                        }
                    }

                    // PROVEEDOR → buscar o crear proveedor
                    if (columnasMap.containsKey("PROVEEDOR")) {
                        String proveedorNombre = obtenerValorCelda(row, columnasMap.get("PROVEEDOR"));
                        if (proveedorNombre != null && !proveedorNombre.isBlank()) {
                            Proveedor proveedor = buscarOCrearProveedor(proveedorNombre.trim());
                            producto.setProveedor(proveedor);
                            actualizado = true;
                        }
                    }

                    // TIPO DE PRODUCTO → es_combo
                    if (columnasMap.containsKey("TIPO DE PRODUCTO")) {
                        String tipoProducto = obtenerValorCelda(row, columnasMap.get("TIPO DE PRODUCTO"));
                        if (tipoProducto != null && !tipoProducto.isBlank()) {
                            producto.setEsCombo("COMBO".equalsIgnoreCase(tipoProducto.trim()));
                            actualizado = true;
                        }
                    }

                    // ULTIMA ACT. COSTO → fecha_ult_costo (formato: dd/MM/yyyy)
                    if (columnasMap.containsKey("ULTIMA ACT. COSTO")) {
                        String fechaStr = obtenerValorCelda(row, columnasMap.get("ULTIMA ACT. COSTO"));
                        if (fechaStr != null && !fechaStr.isBlank()) {
                            try {
                                LocalDateTime fecha = parsearFecha(fechaStr.trim());
                                if (fecha != null) {
                                    producto.setFechaUltimoCosto(fecha);
                                    actualizado = true;
                                }
                            } catch (Exception e) {
                                errores.add("Fila " + (i + 1) + ": Fecha inválida '" + fechaStr + "'");
                            }
                        }
                    }

                    // UNIDADES POR BULTO → uxb
                    if (columnasMap.containsKey("UNIDADES POR BULTO")) {
                        String uxbStr = obtenerValorCelda(row, columnasMap.get("UNIDADES POR BULTO"));
                        if (uxbStr != null && !uxbStr.isBlank()) {
                            try {
                                String uxbNormalizado = normalizarNumero(uxbStr);
                                double uxbDouble = Double.parseDouble(uxbNormalizado);
                                int uxb = (int) Math.round(uxbDouble);
                                if (uxb > 0) {
                                    producto.setUxb(uxb);
                                    actualizado = true;
                                }
                            } catch (NumberFormatException e) {
                                errores.add("Fila " + (i + 1) + ": UXB inválido '" + uxbStr + "'");
                            }
                        }
                    }

                    // PORCENTAJE IVA → iva
                    if (columnasMap.containsKey("PORCENTAJE IVA")) {
                        String ivaStr = obtenerValorCelda(row, columnasMap.get("PORCENTAJE IVA"));
                        if (ivaStr != null && !ivaStr.isBlank()) {
                            try {
                                String ivaNormalizado = normalizarNumero(ivaStr);
                                BigDecimal iva = new BigDecimal(ivaNormalizado);
                                if (iva.compareTo(BigDecimal.ZERO) >= 0 && iva.compareTo(new BigDecimal("100")) <= 0) {
                                    producto.setIva(iva);
                                    actualizado = true;
                                }
                            } catch (NumberFormatException e) {
                                errores.add("Fila " + (i + 1) + ": IVA inválido '" + ivaStr + "'");
                            }
                        }
                    }

                    if (actualizado) {
                        productoRepository.save(producto);
                        productosActualizados++;

                        // Verificar si cambió algún campo que afecta el precio
                        // Usar compareTo para BigDecimal (equals considera escala, compareTo solo valor)
                        boolean cambioCosto = !bigDecimalEquals(costoAnterior, producto.getCosto());
                        boolean cambioIva = !bigDecimalEquals(ivaAnterior, producto.getIva());
                        Integer proveedorIdNuevo = producto.getProveedor() != null ? producto.getProveedor().getId() : null;
                        boolean cambioProveedor = !Objects.equals(proveedorIdAnterior, proveedorIdNuevo);

                        if (cambioCosto || cambioIva || cambioProveedor) {
                            productosARecalcular.add(producto.getId());
                        }
                    }

                } catch (Exception e) {
                    errores.add("Fila " + (i + 1) + ": Error inesperado - " + e.getMessage());
                    log.warn("Error procesando fila {}: {}", i + 1, e.getMessage());
                }
            }
        }

        // Contar proveedores creados
        int proveedoresDespues = (int) proveedorRepository.count();
        proveedoresCreados = proveedoresDespues - proveedoresAntes;

        // Recalcular precios solo para productos con cambios en costo/IVA/proveedor
        int productosRecalculados = 0;
        if (!productosARecalcular.isEmpty()) {
            log.info("Recalculando precios para {} productos con cambios relevantes...", productosARecalcular.size());
            for (Integer productoId : productosARecalcular) {
                try {
                    recalculoPrecioFacade.recalcularProductoEnTodosLosCanales(productoId);
                    productosRecalculados++;
                } catch (Exception e) {
                    log.warn("Error recalculando precios para producto {}: {}", productoId, e.getMessage());
                }
            }
            log.info("Recálculo completado: {} productos", productosRecalculados);
        }

        log.info("Importación de costos completada: {} actualizados, {} no encontrados, {} proveedores creados, {} recalculados, {} errores",
                productosActualizados, skusNoEncontrados.size(), proveedoresCreados, productosRecalculados, errores.size());

        return new ImportCostosResultDTO(
                productosActualizados,
                skusNoEncontrados.size(),
                proveedoresCreados,
                skusNoEncontrados,
                errores
        );
    }

    /**
     * Parsea una fecha en formato dd/MM/yyyy o d/M/yyyy HH:mm:ss
     */
    private LocalDateTime parsearFecha(String fechaStr) {
        if (fechaStr == null || fechaStr.isBlank()) {
            return null;
        }

        // Normalizar: reemplazar múltiples espacios por uno solo
        String fechaNormalizada = fechaStr.trim().replaceAll("\\s+", " ");

        // Intentar varios formatos
        String[] formatos = {
                "dd/MM/yyyy",
                "d/M/yyyy",
                "dd/MM/yyyy HH:mm:ss",
                "d/M/yyyy HH:mm:ss",
                "dd/MM/yyyy H:mm:ss",
                "d/M/yyyy H:mm:ss"
        };

        for (String formato : formatos) {
            try {
                if (formato.contains("HH") || formato.contains("H:")) {
                    return LocalDateTime.parse(fechaNormalizada, DateTimeFormatter.ofPattern(formato));
                } else {
                    return LocalDate.parse(fechaNormalizada, DateTimeFormatter.ofPattern(formato))
                            .atStartOfDay();
                }
            } catch (DateTimeParseException ignored) {
                // Intentar siguiente formato
            }
        }

        log.warn("No se pudo parsear la fecha: {}", fechaStr);
        return null;
    }

    /**
     * Límite máximo para costo: DECIMAL(10,2) = 99,999,999.99
     */
    private static final BigDecimal COSTO_MAXIMO = new BigDecimal("99999999.99");

    /**
     * Normaliza un número detectando automáticamente el formato.
     * Soporta:
     * - Formato americano: 47639.87775 (punto decimal)
     * - Formato europeo: 47.639,88 (punto miles, coma decimal)
     * - Solo coma: 11051,3484 (coma decimal)
     */
    private String normalizarNumero(String numero) {
        if (numero == null || numero.isBlank()) {
            return "0";
        }

        String num = numero.trim();

        boolean tienePunto = num.contains(".");
        boolean tieneComa = num.contains(",");

        if (tienePunto && tieneComa) {
            // Formato europeo: 47.639,88 → punto es miles, coma es decimal
            // Encontrar cuál está más a la derecha para determinar el decimal
            int ultimoPunto = num.lastIndexOf('.');
            int ultimaComa = num.lastIndexOf(',');

            if (ultimaComa > ultimoPunto) {
                // Coma es el decimal: 47.639,88
                return num.replace(".", "").replace(",", ".");
            } else {
                // Punto es el decimal: 47,639.88 (formato americano con coma de miles)
                return num.replace(",", "");
            }
        } else if (tieneComa && !tienePunto) {
            // Solo coma: 11051,3484 → coma es decimal
            return num.replace(",", ".");
        } else {
            // Solo punto o sin separadores: 47639.87775 o 47639
            return num;
        }
    }

    // Campos de precio base por cada canal/cuota (PVP_INFLADO/COD_PROMO condicionales, FECHA_CALCULO siempre al final)
    private static final String[] CAMPOS_PRECIO = {
            "PVP", "COSTO_PRODUCTO", "COSTOS_VENTA", "INGRESO_NETO", "GANANCIA", "MARGEN_INGRESO", "MARGEN_PVP", "MARKUP_PCT"
    };

    // Colores para distinguir canales
    // Colores saturados para headers de canales (fila 0, super headers)
    private static final short[] COLORES_CANALES = {
            IndexedColors.ROYAL_BLUE.getIndex(),
            IndexedColors.GREEN.getIndex(),
            IndexedColors.DARK_YELLOW.getIndex(),
            IndexedColors.ORANGE.getIndex(),
            IndexedColors.VIOLET.getIndex(),
            IndexedColors.TEAL.getIndex(),
            IndexedColors.PLUM.getIndex(),
            IndexedColors.BROWN.getIndex(),
            IndexedColors.BLUE.getIndex(),
            IndexedColors.DARK_GREEN.getIndex()
    };

    // Colores pasteles para headers de cuotas (fila 1)
    private static final short[] COLORES_CUOTAS = {
            IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex(),
            IndexedColors.LIGHT_GREEN.getIndex(),
            IndexedColors.LIGHT_YELLOW.getIndex(),
            IndexedColors.LIGHT_ORANGE.getIndex(),
            IndexedColors.LAVENDER.getIndex(),
            IndexedColors.LIGHT_TURQUOISE.getIndex(),
            IndexedColors.ROSE.getIndex(),
            IndexedColors.TAN.getIndex(),
            IndexedColors.CORAL.getIndex(),
            IndexedColors.AQUA.getIndex(),
            IndexedColors.GOLD.getIndex(),
            IndexedColors.LIME.getIndex()
    };

    @Override
    @Transactional(readOnly = true)
    public byte[] exportarPrecios(ProductoFilter filter, Sort sort) throws IOException {
        log.info("Iniciando exportación de precios a Excel");

        List<ProductoConPreciosDTO> productos = productoService.listarConPreciosSinPaginar(filter, sort);
        log.info("Total de productos a exportar: {}", productos.size());

        // Recolectar todos los canales únicos y sus cuotas
        Map<String, List<Integer>> canalesCuotas = new LinkedHashMap<>();
        for (ProductoConPreciosDTO producto : productos) {
            if (producto.canales() != null) {
                for (CanalPreciosDTO canalPrecios : producto.canales()) {
                    String canalNombre = normalizarNombreCanal(canalPrecios.canalNombre());
                    canalesCuotas.computeIfAbsent(canalNombre, k -> new ArrayList<>());
                    if (canalPrecios.precios() != null) {
                        for (PrecioDTO precio : canalPrecios.precios()) {
                            Integer cuotas = precio.cuotas() != null ? precio.cuotas() : 0;
                            if (!canalesCuotas.get(canalNombre).contains(cuotas)) {
                                canalesCuotas.get(canalNombre).add(cuotas);
                            }
                        }
                    }
                }
            }
        }

        canalesCuotas.values().forEach(cuotas -> cuotas.sort(Integer::compareTo));

        // Recolectar IDs de canales y mapear nombre normalizado -> canalId
        Map<String, Integer> canalIdPorNombre = new LinkedHashMap<>();
        for (ProductoConPreciosDTO producto : productos) {
            if (producto.canales() != null) {
                for (CanalPreciosDTO canalPrecios : producto.canales()) {
                    String canalNombre = normalizarNombreCanal(canalPrecios.canalNombre());
                    if (!canalIdPorNombre.containsKey(canalNombre) && canalPrecios.canalId() != null) {
                        canalIdPorNombre.put(canalNombre, canalPrecios.canalId());
                    }
                }
            }
        }

        // Obtener reglas de descuento por canal (desde la BD usando canalId)
        // Mapa: canalNombre (normalizado) -> lista de reglas ordenadas por prioridad
        Map<String, List<ReglaDescuento>> descuentosPorCanal = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : canalIdPorNombre.entrySet()) {
            String canalNombre = entry.getKey();
            Integer canalId = entry.getValue();
            List<ReglaDescuento> reglas = reglaDescuentoRepository
                    .findByCanalIdAndActivoTrueOrderByPrioridadAsc(canalId);
            if (!reglas.isEmpty()) {
                descuentosPorCanal.put(canalNombre, reglas);
            }
        }
        log.info("Canales con descuentos: {}", descuentosPorCanal.keySet());

        // Detectar canales con precio inflado (tienen concepto FLAG_APLICAR_PRECIO_INFLADO)
        Set<String> canalesConPrecioInflado = new HashSet<>();
        for (Map.Entry<String, Integer> entry2 : canalIdPorNombre.entrySet()) {
            String canalNombre = entry2.getKey();
            Integer canalId = entry2.getValue();
            boolean tienePrecioInflado = canalConceptoRepository.findByCanalId(canalId).stream()
                    .anyMatch(cc -> cc.getConcepto() != null
                            && cc.getConcepto().getAplicaSobre() == AplicaSobre.FLAG_APLICAR_PRECIO_INFLADO);
            if (tienePrecioInflado) {
                canalesConPrecioInflado.add(canalNombre);
            }
        }

        // Cargar códigos de precio inflado activos: "productoId_canalId" -> codigo
        Map<String, String> codigosPrecioInflado = new HashMap<>();
        List<ProductoCanalPrecioInflado> preciosInfladosActivos = productoCanalPrecioInfladoRepository.findByActivoTrue();
        for (ProductoCanalPrecioInflado pcp : preciosInfladosActivos) {
            if (pcp.getProducto() != null && pcp.getCanal() != null && pcp.getPrecioInflado() != null) {
                String key = pcp.getProducto().getId() + "_" + pcp.getCanal().getId();
                codigosPrecioInflado.put(key, pcp.getPrecioInflado().getCodigo());
            }
        }

        // Obtener descripciones de cuotas desde canal_concepto_cuota
        // Mapa: "canalNombre_cuotas" -> descripcion
        Map<String, String> descripcionesCuotas = new HashMap<>();
        List<CanalConceptoCuota> todasCuotas = canalConceptoCuotaRepository.findAll();
        for (CanalConceptoCuota ccc : todasCuotas) {
            if (ccc.getCanal() != null && ccc.getDescripcion() != null) {
                String canalNombre = normalizarNombreCanal(ccc.getCanal().getNombre());
                String key = canalNombre + "_" + ccc.getCuotas();
                descripcionesCuotas.put(key, ccc.getDescripcion());
            }
        }

        // Construir nombre de hoja con filtros aplicados
        String nombreHoja = construirNombreHojaPrecios(filter, canalesCuotas);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.createSheet(nombreHoja);

            // Headers fijos del producto
            String[] headersFijos = {
                    "ID", "SKU", "MLA", "MLAU", "PRECIO_ENVIO", "COMISION_ML", "COD_EXT", "DESCRIPCION", "TITULO_WEB",
                    "ES_COMBO", "ES_MAQUINA", "IMAGEN_URL", "STOCK", "ACTIVO", "MARCA", "ORIGEN",
                    "CLASIF_GRAL", "CLASIF_GASTRO", "TIPO", "PROVEEDOR", "MATERIAL", "UXB", "CAPACIDAD",
                    "LARGO", "ANCHO", "ALTO", "DIAMBOCA", "DIAMBASE", "ESPESOR", "COSTO",
                    "FECHA_ULT_COSTO", "IVA", "MARGEN_MINORISTA", "MARGEN_MAYORISTA",
                    "FECHA_CREACION", "FECHA_MODIFICACION"
            };

            // Índice donde termina FECHA_MODIFICACION (0-based): último header fijo
            int colPvpMax = headersFijos.length - 1;

            // Calcular columnas por canal (CAMPOS_PRECIO + columnas de precio inflado + columnas de descuento)
            // Mapa: canalNombre -> número de columnas por cuota
            Map<String, Integer> columnasPorCuotaPorCanal = new LinkedHashMap<>();
            for (String canalNombre : canalesCuotas.keySet()) {
                int numDescuentos = descuentosPorCanal.containsKey(canalNombre)
                        ? descuentosPorCanal.get(canalNombre).size()
                        : 0;
                int colsPrecioInflado = canalesConPrecioInflado.contains(canalNombre) ? 2 : 0; // PVP_INFLADO + COD_PROMO
                int colsFechaCalculo = 1; // FECHA_CALCULO siempre al final
                columnasPorCuotaPorCanal.put(canalNombre, CAMPOS_PRECIO.length + colsPrecioInflado + numDescuentos + colsFechaCalculo);
            }

            // Calcular total de columnas dinámicas
            int totalColumnasDinamicas = 0;
            for (String canalNombre : canalesCuotas.keySet()) {
                int cuotasCount = canalesCuotas.get(canalNombre).size();
                int colsPorCuota = columnasPorCuotaPorCanal.get(canalNombre);
                totalColumnasDinamicas += cuotasCount * colsPorCuota;
            }
            int totalColumnas = headersFijos.length + totalColumnasDinamicas;

            // Crear estilos base
            CellStyle headerDatosStyle = crearEstiloHeaderCentrado(workbook, IndexedColors.LIGHT_GREEN.getIndex(), false);

            // Estilos para filas pares (sin fondo) e impares (con fondo gris claro)
            XSSFColor colorFilaAlternada = new XSSFColor(new byte[]{(byte) 234, (byte) 234, (byte) 234}, null);
            XSSFColor colorVerdePvp = new XSSFColor(new byte[]{(byte) 24, (byte) 106, (byte) 59}, null);
            CellStyle dataStyle = crearEstiloDataCentrado(workbook);
            CellStyle dataStyleAlt = crearEstiloDataCentradoConFondo(workbook, colorFilaAlternada);
            CellStyle precioStyle = crearEstiloPrecio(workbook);
            CellStyle precioStyleAlt = crearEstiloPrecioConFondo(workbook, colorFilaAlternada);
            CellStyle precioBordeStyle = crearEstiloPrecioBorde(workbook);
            CellStyle precioBordeStyleAlt = crearEstiloPrecioBordeConFondo(workbook, colorFilaAlternada);
            CellStyle porcentajeStyle = crearEstiloPorcentaje(workbook);
            CellStyle porcentajeStyleAlt = crearEstiloPorcentajeConFondo(workbook, colorFilaAlternada);
            CellStyle porcentajeBordeStyle = crearEstiloPorcentajeBorde(workbook);
            CellStyle porcentajeBordeStyleAlt = crearEstiloPorcentajeBordeConFondo(workbook, colorFilaAlternada);

            // Estilos PVP: negrita + fuente verde
            CellStyle pvpStyle = crearEstiloPrecioPvp(workbook, colorVerdePvp);
            CellStyle pvpStyleAlt = crearEstiloPrecioPvpConFondo(workbook, colorVerdePvp, colorFilaAlternada);
            CellStyle pvpBordeStyle = crearEstiloPrecioPvpBorde(workbook, colorVerdePvp);
            CellStyle pvpBordeStyleAlt = crearEstiloPrecioPvpBordeConFondo(workbook, colorVerdePvp, colorFilaAlternada);

            // Crear estilos por canal (con colores diferentes)
            List<String> nombresCanales = new ArrayList<>(canalesCuotas.keySet());
            Map<String, CellStyle> estilosSuperHeaderPorCanal = new HashMap<>();
            Map<String, CellStyle> estilosHeaderPorCanal = new HashMap<>();
            Map<String, CellStyle> estilosHeaderBordePorCanal = new HashMap<>();
            Map<String, CellStyle> estilosDataPorCanal = new HashMap<>();
            Map<String, CellStyle> estilosDataBordePorCanal = new HashMap<>();
            Map<String, CellStyle> estilosDataPorCanalAlt = new HashMap<>();
            Map<String, CellStyle> estilosDataBordePorCanalAlt = new HashMap<>();

            for (int i = 0; i < nombresCanales.size(); i++) {
                String canal = nombresCanales.get(i);
                short color = COLORES_CANALES[i % COLORES_CANALES.length];
                estilosSuperHeaderPorCanal.put(canal, crearEstiloSuperHeader(workbook, color));
                estilosHeaderPorCanal.put(canal, crearEstiloHeaderCentrado(workbook, color, false));
                estilosHeaderBordePorCanal.put(canal, crearEstiloHeaderCentrado(workbook, color, true));
                estilosDataPorCanal.put(canal, crearEstiloDataCentrado(workbook));
                estilosDataBordePorCanal.put(canal, crearEstiloDataConBordeGruesoCentrado(workbook));
                estilosDataPorCanalAlt.put(canal, crearEstiloDataCentradoConFondo(workbook, colorFilaAlternada));
                estilosDataBordePorCanalAlt.put(canal, crearEstiloDataConBordeGruesoCentradoConFondo(workbook, colorFilaAlternada));

            }

            // ========== FILA 0: Super headers (headers datos + un header por canal) ==========
            Row superHeaderRow = sheet.createRow(0);

            // Headers fijos en fila 0 (se combinarán verticalmente con filas 1 y 2)
            for (int i = 0; i < headersFijos.length; i++) {
                Cell cell = superHeaderRow.createCell(i);
                cell.setCellValue(headersFijos[i]);
                cell.setCellStyle(headerDatosStyle);
            }

            // Super headers por cada canal
            int superHeaderColIndex = headersFijos.length;
            for (String canalNombre : nombresCanales) {
                int colsPorCuota = columnasPorCuotaPorCanal.get(canalNombre);
                int numColumnasCanal = canalesCuotas.get(canalNombre).size() * colsPorCuota;
                int colInicio = superHeaderColIndex;
                int colFin = superHeaderColIndex + numColumnasCanal - 1;

                CellStyle estiloSuperCanal = estilosSuperHeaderPorCanal.get(canalNombre);

                Cell cellCanal = superHeaderRow.createCell(colInicio);
                cellCanal.setCellValue(canalNombre);
                cellCanal.setCellStyle(estiloSuperCanal);

                for (int i = colInicio + 1; i <= colFin; i++) {
                    Cell c = superHeaderRow.createCell(i);
                    c.setCellStyle(estiloSuperCanal);
                }

                if (colInicio < colFin) {
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, colInicio, colFin));
                }

                superHeaderColIndex += numColumnasCanal;
            }

            // ========== FILA 1: Sub-headers con descripción de cuotas ==========
            Row cuotasHeaderRow = sheet.createRow(1);

            // Celdas de headers fijos en fila 1 (parte del merge vertical)
            for (int i = 0; i < headersFijos.length; i++) {
                Cell cell = cuotasHeaderRow.createCell(i);
                cell.setCellStyle(headerDatosStyle);
            }

            // Sub-headers de cuotas por canal (merge por cada grupo de cuotas)
            // Lista para guardar los rangos de cuotas de fila 1 y aplicar bordes después
            List<CellRangeAddress> rangosCuotasFila1 = new ArrayList<>();
            int cuotasColIndex = headersFijos.length;
            for (String canalNombre : nombresCanales) {
                List<Integer> cuotasList = canalesCuotas.get(canalNombre);
                int colsPorCuotaCanal = columnasPorCuotaPorCanal.get(canalNombre);

                for (int cuotaIndex = 0; cuotaIndex < cuotasList.size(); cuotaIndex++) {
                    Integer cuotas = cuotasList.get(cuotaIndex);
                    String key = canalNombre + "_" + cuotas;
                    String descripcionCuotas = descripcionesCuotas.getOrDefault(key, "");

                    // Color diferente para cada cuota
                    short colorCuota = COLORES_CUOTAS[cuotaIndex % COLORES_CUOTAS.length];
                    CellStyle estiloCuotaHeader = crearEstiloHeaderCentrado(workbook, colorCuota, false);

                    int colInicioCuota = cuotasColIndex;
                    int colFinCuota = cuotasColIndex + colsPorCuotaCanal - 1;

                    // Crear celda con descripción de cuotas
                    Cell cellCuota = cuotasHeaderRow.createCell(colInicioCuota);
                    cellCuota.setCellValue(descripcionCuotas);
                    cellCuota.setCellStyle(estiloCuotaHeader);

                    // Crear celdas restantes para el merge
                    for (int i = colInicioCuota + 1; i <= colFinCuota; i++) {
                        Cell c = cuotasHeaderRow.createCell(i);
                        c.setCellStyle(estiloCuotaHeader);
                    }

                    // Merge de las columnas de esta cuota
                    CellRangeAddress rangoCuota = new CellRangeAddress(1, 1, colInicioCuota, colFinCuota);
                    if (colInicioCuota < colFinCuota) {
                        sheet.addMergedRegion(rangoCuota);
                    }
                    rangosCuotasFila1.add(rangoCuota);

                    cuotasColIndex += colsPorCuotaCanal;
                }
            }

            // Aplicar bordes gruesos a cada rango de cuota en fila 1
            for (CellRangeAddress rango : rangosCuotasFila1) {
                RegionUtil.setBorderTop(BorderStyle.THICK, rango, sheet);
                RegionUtil.setBorderBottom(BorderStyle.THICK, rango, sheet);
                RegionUtil.setBorderLeft(BorderStyle.THICK, rango, sheet);
                RegionUtil.setBorderRight(BorderStyle.THICK, rango, sheet);
            }

            // ========== FILA 2: Headers de columnas ==========
            Row headerRow = sheet.createRow(2);
            int colIndex = 0;

            // Headers fijos en fila 2 (parte del merge vertical) y aplicar merge vertical
            for (int i = 0; i < headersFijos.length; i++) {
                Cell cell = headerRow.createCell(colIndex++);
                cell.setCellStyle(headerDatosStyle);
                // Merge vertical de filas 0-2 para cada header fijo
                CellRangeAddress mergeRegion = new CellRangeAddress(0, 2, i, i);
                sheet.addMergedRegion(mergeRegion);
                // Aplicar bordes gruesos a cada celda mergeada
                RegionUtil.setBorderTop(BorderStyle.THICK, mergeRegion, sheet);
                RegionUtil.setBorderBottom(BorderStyle.THICK, mergeRegion, sheet);
                RegionUtil.setBorderLeft(BorderStyle.THICK, mergeRegion, sheet);
                RegionUtil.setBorderRight(BorderStyle.THICK, mergeRegion, sheet);
            }

            // Headers dinámicos por canal y cuotas
            // Lista para guardar los rangos de cuotas de fila 2 y aplicar bordes después
            List<CellRangeAddress> rangosCuotasFila2 = new ArrayList<>();
            for (String canalNombre : nombresCanales) {
                List<Integer> cuotasList = canalesCuotas.get(canalNombre);
                List<ReglaDescuento> reglasCanal = descuentosPorCanal.get(canalNombre);

                for (int cuotaIndex = 0; cuotaIndex < cuotasList.size(); cuotaIndex++) {
                    Integer cuotas = cuotasList.get(cuotaIndex);

                    // Color diferente para cada cuota
                    short colorCuota = COLORES_CUOTAS[cuotaIndex % COLORES_CUOTAS.length];
                    CellStyle estiloHeaderCuota = crearEstiloHeaderCentrado(workbook, colorCuota, false);

                    int colInicioCuota = colIndex;

                    // Columnas de precio base (con PVP_INFLADO y COD_PROMO después de PVP si corresponde)
                    boolean canalTienePrecioInflado = canalesConPrecioInflado.contains(canalNombre);
                    for (int i = 0; i < CAMPOS_PRECIO.length; i++) {
                        Cell cell = headerRow.createCell(colIndex++);
                        cell.setCellValue(CAMPOS_PRECIO[i]);
                        cell.setCellStyle(estiloHeaderCuota);

                        // Después de PVP (index 0), insertar PVP_INFLADO y COD_PROMO
                        if (i == 0 && canalTienePrecioInflado) {
                            Cell cellPvpInf = headerRow.createCell(colIndex++);
                            cellPvpInf.setCellValue("PVP_INFLADO");
                            cellPvpInf.setCellStyle(estiloHeaderCuota);
                            Cell cellCodPromo = headerRow.createCell(colIndex++);
                            cellCodPromo.setCellValue("COD_PROMO");
                            cellCodPromo.setCellStyle(estiloHeaderCuota);
                        }
                    }

                    // Columnas de descuento (solo si el canal tiene descuentos)
                    if (reglasCanal != null) {
                        for (ReglaDescuento regla : reglasCanal) {
                            // Header formato: "MARGEN (>$montoMinimo -porcentaje%)"
                            String headerDesc = String.format("MARGEN (>$%s -%s%%)",
                                    regla.getMontoMinimo().stripTrailingZeros().toPlainString(),
                                    regla.getDescuentoPorcentaje().stripTrailingZeros().toPlainString());
                            Cell cellDesc = headerRow.createCell(colIndex++);
                            cellDesc.setCellValue(headerDesc);
                            cellDesc.setCellStyle(estiloHeaderCuota);
                        }
                    }

                    // FECHA_CALCULO siempre al final de cada cuota
                    Cell cellFecha = headerRow.createCell(colIndex++);
                    cellFecha.setCellValue("FECHA_CALCULO");
                    cellFecha.setCellStyle(estiloHeaderCuota);

                    int colFinCuota = colIndex - 1;
                    rangosCuotasFila2.add(new CellRangeAddress(2, 2, colInicioCuota, colFinCuota));
                }
            }

            // Aplicar bordes gruesos a cada rango de cuota en fila 2
            for (CellRangeAddress rango : rangosCuotasFila2) {
                RegionUtil.setBorderTop(BorderStyle.THICK, rango, sheet);
                RegionUtil.setBorderBottom(BorderStyle.THICK, rango, sheet);
                RegionUtil.setBorderLeft(BorderStyle.THICK, rango, sheet);
                RegionUtil.setBorderRight(BorderStyle.THICK, rango, sheet);
            }

            // ========== FILAS DE DATOS ==========
            int rowIndex = 3; // Empezamos en fila 3 (después de super header, cuotas header y header)
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            int dataRowNum = 0; // Contador de filas de datos para alternar colores

            for (ProductoConPreciosDTO producto : productos) {
                Row row = sheet.createRow(rowIndex++);
                int cellIndex = 0;

                // Determinar si es fila par o impar para alternar colores (estilo tabla)
                boolean esFilaAlternada = (dataRowNum % 2 == 1);
                dataRowNum++;

                // Seleccionar estilos según si es fila alternada
                CellStyle currentDataStyle = esFilaAlternada ? dataStyleAlt : dataStyle;
                CellStyle currentPrecioStyle = esFilaAlternada ? precioStyleAlt : precioStyle;
                CellStyle currentPorcentajeStyleFijo = esFilaAlternada ? porcentajeStyleAlt : porcentajeStyle;

                // Columnas fijas
                setCellValue(row.createCell(cellIndex++), producto.id(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.sku(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.mla(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.mlau(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.precioEnvio(), currentPrecioStyle);
                setCellValue(row.createCell(cellIndex++), producto.comisionPorcentaje(), currentPorcentajeStyleFijo);
                setCellValue(row.createCell(cellIndex++), producto.codExt(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.descripcion(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.tituloWeb(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.esCombo(), currentDataStyle);
                // Si clasifGastro es null, esMaquina debe ser false
                Boolean esMaquinaExport = producto.clasifGastroNombre() == null ? false : producto.esMaquina();
                setCellValue(row.createCell(cellIndex++), esMaquinaExport, currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.imagenUrl(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.stock(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.activo(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.marcaNombre(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.origenNombre(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.clasifGralNombre(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.clasifGastroNombre(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.tipoNombre(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.proveedorNombre(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.materialNombre(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.uxb(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.capacidad(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.largo(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.ancho(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.alto(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.diamboca(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.diambase(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.espesor(), currentDataStyle);
                setCellValue(row.createCell(cellIndex++), producto.costo(), currentPrecioStyle);
                setCellValueDate(row.createCell(cellIndex++), producto.fechaUltimoCosto(), currentDataStyle, dtf);
                setCellValue(row.createCell(cellIndex++), producto.iva(), currentPorcentajeStyleFijo);
                setCellValue(row.createCell(cellIndex++), producto.margenMinorista(), currentPorcentajeStyleFijo);
                setCellValue(row.createCell(cellIndex++), producto.margenMayorista(), currentPorcentajeStyleFijo);
                setCellValueDate(row.createCell(cellIndex++), producto.fechaCreacion(), currentDataStyle, dtf);
                setCellValueDate(row.createCell(cellIndex++), producto.fechaModificacion(), currentDataStyle, dtf);

                // Columnas dinámicas por canal/cuotas
                Map<String, Map<Integer, PrecioDTO>> preciosPorCanal = new HashMap<>();
                if (producto.canales() != null) {
                    for (CanalPreciosDTO canalPrecios : producto.canales()) {
                        String canalNombre = normalizarNombreCanal(canalPrecios.canalNombre());
                        preciosPorCanal.computeIfAbsent(canalNombre, k -> new HashMap<>());
                        if (canalPrecios.precios() != null) {
                            for (PrecioDTO precio : canalPrecios.precios()) {
                                Integer cuotas = precio.cuotas() != null ? precio.cuotas() : 0;
                                preciosPorCanal.get(canalNombre).put(cuotas, precio);
                            }
                        }
                    }
                }

                for (String canalNombre : nombresCanales) {
                    List<Integer> cuotasList = canalesCuotas.get(canalNombre);
                    List<ReglaDescuento> reglasCanal = descuentosPorCanal.get(canalNombre);
                    int numDescuentosCanal = reglasCanal != null ? reglasCanal.size() : 0;

                    // Seleccionar estilos según si es fila alternada
                    CellStyle estiloData = esFilaAlternada ? estilosDataPorCanalAlt.get(canalNombre) : estilosDataPorCanal.get(canalNombre);
                    CellStyle estiloDataBorde = esFilaAlternada ? estilosDataBordePorCanalAlt.get(canalNombre) : estilosDataBordePorCanal.get(canalNombre);
                    CellStyle currentPrecioBordeStyle = esFilaAlternada ? precioBordeStyleAlt : precioBordeStyle;
                    CellStyle currentPorcentajeStyle = esFilaAlternada ? porcentajeStyleAlt : porcentajeStyle;
                    CellStyle currentPorcentajeBordeStyle = esFilaAlternada ? porcentajeBordeStyleAlt : porcentajeBordeStyle;

                    for (Integer cuotas : cuotasList) {
                        PrecioDTO precio = preciosPorCanal.getOrDefault(canalNombre, new HashMap<>())
                                .get(cuotas);

                        // Columnas de precio base (con PVP_INFLADO y COD_PROMO después de PVP si corresponde)
                        boolean canalTienePrecioInfladoData = canalesConPrecioInflado.contains(canalNombre);
                        CellStyle currentPrecioStyle2 = esFilaAlternada ? precioStyleAlt : precioStyle;
                        CellStyle currentDataStyle2 = esFilaAlternada ? estilosDataPorCanalAlt.get(canalNombre) : estilosDataPorCanal.get(canalNombre);

                        for (int i = 0; i < CAMPOS_PRECIO.length; i++) {
                            Cell cell = row.createCell(cellIndex++);
                            // Primera columna de CADA cuota usa borde grueso izquierdo
                            boolean primerColumnDeCuota = (i == 0);
                            CellStyle styleToUse = primerColumnDeCuota ? estiloDataBorde : estiloData;
                            CellStyle stylePrecioToUse = primerColumnDeCuota ? currentPrecioBordeStyle : currentPrecioStyle;
                            CellStyle stylePorcentajeToUse = primerColumnDeCuota ? currentPorcentajeBordeStyle : currentPorcentajeStyle;
                            CellStyle stylePvpToUse = primerColumnDeCuota
                                    ? (esFilaAlternada ? pvpBordeStyleAlt : pvpBordeStyle)
                                    : (esFilaAlternada ? pvpStyleAlt : pvpStyle);

                            if (precio != null) {
                                switch (i) {
                                    case 0 -> setCellValue(cell, precio.pvp(), stylePvpToUse);
                                    case 1 -> setCellValue(cell, precio.costoProducto(), stylePrecioToUse);
                                    case 2 -> setCellValue(cell, precio.costosVenta(), stylePrecioToUse);
                                    case 3 -> setCellValue(cell, precio.ingresoNetoVendedor(), stylePrecioToUse);
                                    case 4 -> setCellValue(cell, precio.ganancia(), stylePrecioToUse);
                                    case 5 -> setCellValue(cell, precio.margenSobreIngresoNeto(), stylePorcentajeToUse);
                                    case 6 -> setCellValue(cell, precio.margenSobrePvp(), stylePorcentajeToUse);
                                    case 7 -> setCellValue(cell, precio.markupPorcentaje(), stylePorcentajeToUse);
                                }
                            } else {
                                cell.setBlank();
                                cell.setCellStyle(styleToUse);
                            }

                            // Después de PVP (index 0), insertar PVP_INFLADO y COD_PROMO
                            if (i == 0 && canalTienePrecioInfladoData) {
                                Cell cellPvpInf = row.createCell(cellIndex++);
                                if (precio != null && precio.pvpInflado() != null) {
                                    setCellValue(cellPvpInf, precio.pvpInflado(), currentPrecioStyle2);
                                } else {
                                    cellPvpInf.setBlank();
                                    cellPvpInf.setCellStyle(currentPrecioStyle2);
                                }

                                Cell cellCodPromo = row.createCell(cellIndex++);
                                if (precio != null) {
                                    Integer canalId = canalIdPorNombre.get(canalNombre);
                                    String keyPromo = producto.id() + "_" + canalId;
                                    String codigo = codigosPrecioInflado.get(keyPromo);
                                    if (codigo != null) {
                                        setCellValue(cellCodPromo, codigo, currentDataStyle2);
                                    } else {
                                        cellCodPromo.setBlank();
                                        cellCodPromo.setCellStyle(currentDataStyle2);
                                    }
                                } else {
                                    cellCodPromo.setBlank();
                                    cellCodPromo.setCellStyle(currentDataStyle2);
                                }
                            }
                        }

                        // Columnas de descuentos (solo margenConDescuento, una por cada regla del canal)
                        if (numDescuentosCanal > 0) {
                            List<DescuentoAplicableDTO> descuentos = (precio != null && precio.descuentos() != null)
                                    ? precio.descuentos()
                                    : Collections.emptyList();

                            for (int d = 0; d < numDescuentosCanal; d++) {
                                DescuentoAplicableDTO descuento = d < descuentos.size() ? descuentos.get(d) : null;

                                Cell cellDescMargen = row.createCell(cellIndex++);
                                if (descuento != null) {
                                    setCellValue(cellDescMargen, descuento.margenSobreIngresoNetoConDescuento(), currentPorcentajeStyle);
                                } else {
                                    cellDescMargen.setBlank();
                                    cellDescMargen.setCellStyle(estiloData);
                                }
                            }
                        }

                        // FECHA_CALCULO siempre al final de cada cuota
                        Cell cellFechaCalculo = row.createCell(cellIndex++);
                        if (precio != null) {
                            setCellValueDate(cellFechaCalculo, precio.fechaUltimoCalculo(),
                                    esFilaAlternada ? estilosDataPorCanalAlt.get(canalNombre) : estilosDataPorCanal.get(canalNombre), dtf);
                        } else {
                            cellFechaCalculo.setBlank();
                            cellFechaCalculo.setCellStyle(esFilaAlternada ? estilosDataPorCanalAlt.get(canalNombre) : estilosDataPorCanal.get(canalNombre));
                        }
                    }
                }
            }

            // Auto-ajustar ancho de columnas
            for (int i = 0; i < totalColumnas; i++) {
                sheet.autoSizeColumn(i);
            }

            // Fijar las 3 primeras filas (super header + cuotas header + headers)
            sheet.createFreezePane(0, 3);

            workbook.write(outputStream);
            log.info("Exportación de precios completada exitosamente");
            return outputStream.toByteArray();
        }
    }

    private CellStyle crearEstiloSuperHeader(XSSFWorkbook workbook, short colorIndex) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(colorIndex);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THICK);
        style.setBorderTop(BorderStyle.THICK);
        style.setBorderLeft(BorderStyle.THICK);
        style.setBorderRight(BorderStyle.THICK);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle crearEstiloHeaderCentrado(XSSFWorkbook workbook, short colorIndex, boolean bordeGruesoIzq) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(colorIndex);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(bordeGruesoIzq ? BorderStyle.THICK : BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle crearEstiloHeaderConBordeDerecho(XSSFWorkbook workbook, short colorIndex) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(colorIndex);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THICK);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle crearEstiloDataCentrado(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle crearEstiloDataConBordeGruesoCentrado(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THICK);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /**
     * Crea un estilo para celdas de precio con formato de moneda.
     * Formato: $#,##0.00
     */
    private CellStyle crearEstiloPrecio(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("\"$\"#,##0.00"));
        return style;
    }

    /**
     * Crea un estilo para celdas de precio con borde grueso izquierdo.
     * Formato: $#,##0.00
     */
    private CellStyle crearEstiloPrecioBorde(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THICK);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("\"$\"#,##0.00"));
        return style;
    }

    /**
     * Crea un estilo para celdas de porcentaje.
     * Formato: 0.00%
     */
    private CellStyle crearEstiloPorcentaje(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.00\"%\""));
        return style;
    }

    /**
     * Crea un estilo para celdas de porcentaje con borde grueso izquierdo.
     * Formato: 0.00%
     */
    private CellStyle crearEstiloPorcentajeBorde(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THICK);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.00\"%\""));
        return style;
    }

    // ========== ESTILOS CON FONDO PARA FILAS ALTERNADAS (TableStyleLight1) ==========

    private XSSFCellStyle crearEstiloDataCentradoConFondo(XSSFWorkbook workbook, XSSFColor color) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private XSSFCellStyle crearEstiloDataConBordeGruesoCentradoConFondo(XSSFWorkbook workbook, XSSFColor color) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THICK);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private XSSFCellStyle crearEstiloPrecioConFondo(XSSFWorkbook workbook, XSSFColor color) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("\"$\"#,##0.00"));
        return style;
    }

    private XSSFCellStyle crearEstiloPrecioBordeConFondo(XSSFWorkbook workbook, XSSFColor color) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THICK);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("\"$\"#,##0.00"));
        return style;
    }

    private XSSFCellStyle crearEstiloPorcentajeConFondo(XSSFWorkbook workbook, XSSFColor color) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.00\"%\""));
        return style;
    }

    private XSSFCellStyle crearEstiloPorcentajeBordeConFondo(XSSFWorkbook workbook, XSSFColor color) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THICK);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.00\"%\""));
        return style;
    }

    // ========== ESTILOS PVP: NEGRITA + FUENTE VERDE ==========

    private XSSFCellStyle crearEstiloPrecioPvp(XSSFWorkbook workbook, XSSFColor colorFuente) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("\"$\"#,##0.00"));
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setColor(colorFuente);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle crearEstiloPrecioPvpConFondo(XSSFWorkbook workbook, XSSFColor colorFuente, XSSFColor colorFondo) {
        XSSFCellStyle style = crearEstiloPrecioPvp(workbook, colorFuente);
        style.setFillForegroundColor(colorFondo);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private XSSFCellStyle crearEstiloPrecioPvpBorde(XSSFWorkbook workbook, XSSFColor colorFuente) {
        XSSFCellStyle style = crearEstiloPrecioPvp(workbook, colorFuente);
        style.setBorderLeft(BorderStyle.THICK);
        return style;
    }

    private XSSFCellStyle crearEstiloPrecioPvpBordeConFondo(XSSFWorkbook workbook, XSSFColor colorFuente, XSSFColor colorFondo) {
        XSSFCellStyle style = crearEstiloPrecioPvpBorde(workbook, colorFuente);
        style.setFillForegroundColor(colorFondo);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private String normalizarNombreCanal(String nombre) {
        if (nombre == null) return "SIN_CANAL";
        return nombre.toUpperCase()
                .replace(" ", "_")
                .replace("-", "_");
    }

    private void setCellValue(Cell cell, Object value, CellStyle style) {
        cell.setCellStyle(style);
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value ? "SI" : "NO");
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private void setCellValueDate(Cell cell, LocalDateTime value, CellStyle style, DateTimeFormatter dtf) {
        cell.setCellStyle(style);
        if (value == null) {
            cell.setBlank();
        } else {
            cell.setCellValue(value.format(dtf));
        }
    }

    /**
     * Construye el nombre de la hoja de Excel basándose en los filtros aplicados.
     * El nombre de hoja en Excel tiene un límite de 31 caracteres.
     */
    private String construirNombreHojaPrecios(ProductoFilter filter, Map<String, List<Integer>> canalesCuotas) {
        StringBuilder sb = new StringBuilder("Precios");

        // Agregar información de canales
        if (filter.canalId() != null) {
            // Si se filtró por un canal específico, usar su nombre
            String canalNombre = canalesCuotas.keySet().stream().findFirst().orElse("C" + filter.canalId());
            sb.append("_").append(canalNombre);
        } else if (canalesCuotas.size() <= 2) {
            // Si hay pocos canales, listarlos
            sb.append("_").append(String.join("_", canalesCuotas.keySet()));
        }

        // Agregar información de cuotas
        if (filter.cuotas() != null) {
            sb.append("_").append(filter.cuotas()).append("C");
        }

        // Agregar búsqueda de texto si existe
        if (filter.search() != null && !filter.search().isBlank()) {
            String textoCorto = filter.search().length() > 10
                    ? filter.search().substring(0, 10)
                    : filter.search();
            sb.append("_").append(textoCorto);
        }

        // Limitar a 31 caracteres (límite de Excel para nombres de hoja)
        String nombre = sb.toString().replaceAll("[\\\\/*?\\[\\]:]", ""); // Eliminar caracteres inválidos
        return nombre.length() > 31 ? nombre.substring(0, 31) : nombre;
    }

    /**
     * Construye un sufijo para el nombre del archivo basándose en los filtros.
     * Usado por el controller para generar el nombre del archivo.
     */
    @Override
    public String construirSufijoArchivoPrecios(ProductoFilter filter) {
        StringBuilder sb = new StringBuilder();

        if (filter.canalId() != null) {
            String canalNombre = canalRepository.findById(filter.canalId())
                    .map(Canal::getNombre)
                    .orElse(String.valueOf(filter.canalId()));
            sb.append("_").append(canalNombre.toUpperCase().replaceAll("\\s+", "_"));

            if (filter.cuotas() != null) {
                String descCuotas = canalConceptoCuotaRepository.findByCanalIdAndCuotas(filter.canalId(), filter.cuotas()).stream()
                        .map(CanalConceptoCuota::getDescripcion)
                        .findFirst().orElse(String.valueOf(filter.cuotas()));
                sb.append("_").append(descCuotas.toUpperCase().replaceAll("\\s+", "_"));
            }
        } else if (filter.cuotas() != null) {
            sb.append("_").append(filter.cuotas()).append("_CUOTAS");
        }

        if (filter.search() != null && !filter.search().isBlank()) {
            String textoCorto = filter.search().replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
            textoCorto = textoCorto.length() > 15 ? textoCorto.substring(0, 15) : textoCorto;
            sb.append("_").append(textoCorto);
        }

        if (filter.marcaIds() != null && !filter.marcaIds().isEmpty()) {
            sb.append("_MARCA").append(filter.marcaIds().getFirst());
            if (filter.marcaIds().size() > 1) sb.append("+").append(filter.marcaIds().size() - 1);
        }

        if (filter.proveedorIds() != null && !filter.proveedorIds().isEmpty()) {
            sb.append("_PROV").append(filter.proveedorIds().getFirst());
            if (filter.proveedorIds().size() > 1) sb.append("+").append(filter.proveedorIds().size() - 1);
        }

        if (filter.catalogoIds() != null && !filter.catalogoIds().isEmpty()) {
            sb.append("_CAT").append(filter.catalogoIds().get(0));
        }

        return sb.toString();
    }

    @Override
    @Transactional(readOnly = true)
    public ExportCatalogoResultDTO exportarCatalogo(Integer catalogoId, Integer canalId, Integer cuotas,
                                                    Integer clasifGralId, Integer clasifGastroId, Integer tipoId, Integer marcaId,
                                                    ar.com.leo.super_master_backend.dominio.producto.entity.Tag tag, String ordenarPor) throws IOException {
        log.info("Iniciando exportación de catálogo {} con canal {}, cuotas {}, clasifGralId {}, clasifGastroId {}, tipoId {}, marcaId {}, tag {}, ordenarPor {}",
                catalogoId, canalId, cuotas, clasifGralId, clasifGastroId, tipoId, marcaId, tag, ordenarPor);

        // Validar parámetros
        if (catalogoId == null || canalId == null) {
            throw new IllegalArgumentException("catalogoId y canalId son requeridos");
        }

        // Si cuotas es null, usar 0 (contado)
        int cuotasValue = cuotas != null ? cuotas : 0;

        // Obtener el catálogo
        Catalogo catalogo = catalogoRepository.findById(catalogoId)
                .orElseThrow(() -> new IllegalArgumentException("Catálogo no encontrado: " + catalogoId));

        // Obtener el canal
        Canal canal = canalRepository.findById(canalId)
                .orElseThrow(() -> new IllegalArgumentException("Canal no encontrado: " + canalId));

        // Obtener productos del catálogo
        List<ProductoCatalogo> productosCatalogo = productoCatalogoRepository
                .findByCatalogoId(catalogoId);

        // Aplicar filtros opcionales
        productosCatalogo = productosCatalogo.stream()
                .filter(pc -> {
                    Producto p = pc.getProducto();
                    // Filtrar por clasifGralId
                    if (clasifGralId != null && (p.getClasifGral() == null || !clasifGralId.equals(p.getClasifGral().getId()))) {
                        return false;
                    }
                    // Filtrar por clasifGastroId
                    if (clasifGastroId != null && (p.getClasifGastro() == null || !clasifGastroId.equals(p.getClasifGastro().getId()))) {
                        return false;
                    }
                    // Filtrar por tipoId
                    if (tipoId != null && (p.getTipo() == null || !tipoId.equals(p.getTipo().getId()))) {
                        return false;
                    }
                    // Filtrar por marcaId
                    if (marcaId != null && (p.getMarca() == null || !marcaId.equals(p.getMarca().getId()))) {
                        return false;
                    }
                    // Filtrar por tag del producto (MAQUINA / REPUESTO / MENAJE)
                    if (tag != null) {
                        if (p.getTag() == null || !tag.equals(p.getTag())) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // Parsear campos de ordenamiento
        List<String> camposOrden = new ArrayList<>();
        if (ordenarPor != null && !ordenarPor.isBlank()) {
            for (String campo : ordenarPor.split(",")) {
                camposOrden.add(campo.trim().toLowerCase());
            }
        }

        // Ordenar dinámicamente según los campos especificados, siempre terminando con tituloWeb/descripcion
        productosCatalogo.sort((pc1, pc2) -> {
            Producto p1 = pc1.getProducto();
            Producto p2 = pc2.getProducto();

            for (String campo : camposOrden) {
                int cmp = compararPorCampo(p1, p2, campo);
                if (cmp != 0) return cmp;
            }

            // Siempre al final: comparar por tituloWeb, si es null usar descripcion
            String nombre1 = p1.getTituloWeb() != null ? p1.getTituloWeb() : p1.getDescripcion();
            String nombre2 = p2.getTituloWeb() != null ? p2.getTituloWeb() : p2.getDescripcion();
            return compareNullsLast(nombre1, nombre2);
        });

        log.info("Total de productos en catálogo: {}", productosCatalogo.size());

        // Obtener precios para el canal y cuotas especificadas
        Map<Integer, ProductoCanalPrecio> preciosPorProducto = new HashMap<>();
        for (ProductoCatalogo pc : productosCatalogo) {
            Integer productoId = pc.getProducto().getId();
            productoCanalPrecioRepository
                    .findByProductoIdAndCanalIdAndCuotas(productoId, canalId, cuotasValue)
                    .ifPresent(precio -> preciosPorProducto.put(productoId, precio));
        }

        // Validar que existan precios
        if (preciosPorProducto.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("No existen precios para el catálogo '%s' en el canal '%s' con %d cuotas",
                            catalogo.getNombre(), canal.getNombre(), cuotasValue));
        }

        // Obtener descripción de cuotas para nombre de archivo
        String descCuotasCat = canalConceptoCuotaRepository.findByCanalIdAndCuotas(canalId, cuotasValue).stream()
                .map(CanalConceptoCuota::getDescripcion)
                .findFirst().orElse(String.valueOf(cuotasValue));

        // Construir nombre para hoja y archivo: canal-catalogo
        String nombreBase = canal.getNombre() + "-" + catalogo.getNombre();

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.createSheet(nombreBase);

            // Crear estilos
            CellStyle headerStyle = crearEstiloHeaderCentrado(workbook, IndexedColors.GREY_25_PERCENT.getIndex(), false);
            CellStyle dataStyle = crearEstiloDataCentrado(workbook);
            CellStyle precioStyle = crearEstiloPrecio(workbook);

            // Crear headers
            Row headerRow = sheet.createRow(0);
            String pvpHeader = "PVP " + canal.getNombre().toUpperCase();
            if (Boolean.FALSE.equals(catalogo.getExportarConIva())) {
                pvpHeader += " (SIN IVA)";
            }
            if (catalogo.getRecargoPorcentaje() != null
                    && catalogo.getRecargoPorcentaje().compareTo(BigDecimal.ZERO) > 0) {
                String recargoStr = catalogo.getRecargoPorcentaje().stripTrailingZeros().toPlainString();
                pvpHeader += " (+" + recargoStr + "%)";
            }
            if (cuotasValue > 0) {
                pvpHeader += " (" + cuotasValue + " cuotas)";
            }
            String[] headers = {"SKU", "PRODUCTO", pvpHeader, "UxB"};

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Escribir datos
            int rowIndex = 1;
            for (ProductoCatalogo pc : productosCatalogo) {
                Producto producto = pc.getProducto();
                ProductoCanalPrecio precioObj = preciosPorProducto.get(producto.getId());
                if (precioObj == null) continue;

                // Calcular PVP final aplicando configuración del catálogo
                BigDecimal pvpFinal = precioObj.getPvp();
                if (pvpFinal != null) {
                    // 1. Quitar IVA si exportarConIva es false
                    if (Boolean.FALSE.equals(catalogo.getExportarConIva())) {
                        BigDecimal iva = producto.getIva();
                        if (iva != null && iva.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal divisor = BigDecimal.ONE.add(
                                    iva.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)
                            );
                            pvpFinal = pvpFinal.divide(divisor, 2, RoundingMode.HALF_UP);
                        }
                    }

                    // 2. Aplicar recargo si hay porcentaje
                    BigDecimal recargo = catalogo.getRecargoPorcentaje();
                    if (recargo != null && recargo.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal factor = BigDecimal.ONE.add(
                                recargo.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)
                        );
                        pvpFinal = pvpFinal.multiply(factor).setScale(2, RoundingMode.HALF_UP);
                    }
                }

                Row row = sheet.createRow(rowIndex++);
                setCellValue(row.createCell(0), producto.getSku(), dataStyle);
                setCellValue(row.createCell(1), producto.getDescripcion(), dataStyle);
                setCellValue(row.createCell(2), pvpFinal, precioStyle);
                setCellValue(row.createCell(3), producto.getUxb(), dataStyle);
            }

            // Auto-ajustar ancho de columnas
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Fijar la primera fila (header)
            sheet.createFreezePane(0, 1);

            workbook.write(outputStream);
            String nombreArchivo = (nombreBase + "_" + descCuotasCat).toUpperCase().replaceAll("\\s+", "_");
            log.info("Exportación de catálogo completada exitosamente");
            return new ExportCatalogoResultDTO(outputStream.toByteArray(), nombreArchivo);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ExportResultDTO exportarMercadoLibre(Integer cuotas) throws IOException {
        log.info("Iniciando exportación para Mercado Libre con cuotas {}", cuotas);

        // Buscar canal ML
        Canal canal = canalRepository.findByNombreIgnoreCase("ML")
                .orElseThrow(() -> new IllegalArgumentException("Canal 'ML' no encontrado en la base de datos"));

        // Obtener descripción de cuotas
        String descCuotas = canalConceptoCuotaRepository.findByCanalIdAndCuotas(canal.getId(), cuotas).stream()
                .map(CanalConceptoCuota::getDescripcion)
                .findFirst().orElse(String.valueOf(cuotas));

        // Obtener todos los precios para el canal y cuotas
        List<ProductoCanalPrecio> precios = productoCanalPrecioRepository.findByCanalIdAndCuotas(canal.getId(), cuotas);

        if (precios.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("No existen precios para el canal '%s' con cuotas '%s'",
                            canal.getNombre(), descCuotas));
        }

        // Filtrar productos válidos y recolectar advertencias
        List<String> advertencias = new ArrayList<>();
        List<String> productosSinMla = new ArrayList<>();
        List<String> productosConPrecioInvalido = new ArrayList<>();
        List<ProductoCanalPrecio> preciosValidos = new ArrayList<>();

        for (ProductoCanalPrecio precio : precios) {
            Producto producto = precio.getProducto();
            if (producto == null) continue;

            // Verificar que tenga MLA
            String mla = producto.getMla() != null ? producto.getMla().getMla() : null;
            if (mla == null || mla.isBlank()) {
                productosSinMla.add(producto.getSku());
                continue;
            }

            // Verificar precio válido: usar pvpInflado si existe, sino pvp
            BigDecimal precioExportar = precio.getPvpInflado();
            if (precioExportar == null || precioExportar.compareTo(BigDecimal.ZERO) <= 0) {
                precioExportar = precio.getPvp();
            }
            if (precioExportar == null || precioExportar.compareTo(BigDecimal.ZERO) <= 0) {
                productosConPrecioInvalido.add(producto.getSku());
                continue;
            }

            preciosValidos.add(precio);
        }

        // Recolectar advertencias
        if (!productosSinMla.isEmpty()) {
            advertencias.add(truncarListaSkus("Productos sin MLA", productosSinMla));
        }
        if (!productosConPrecioInvalido.isEmpty()) {
            advertencias.add(truncarListaSkus("Productos con precio inválido (<=0)", productosConPrecioInvalido));
        }

        if (preciosValidos.isEmpty()) {
            throw new IllegalArgumentException("No hay productos válidos para exportar (todos sin MLA o con precio inválido)");
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.createSheet("MercadoLibre (" + descCuotas + ")");

            CellStyle headerStyle = crearEstiloHeaderCentrado(workbook, IndexedColors.GREY_25_PERCENT.getIndex(), false);
            CellStyle dataStyle = crearEstiloDataCentrado(workbook);
            CellStyle precioStyle = crearEstiloPrecio(workbook);

            // Headers: SKU, PRECIO (descCuotas), MLA
            Row headerRow = sheet.createRow(0);
            String[] headers = {"SKU", "PRECIO (" + descCuotas + ")", "MLA"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Escribir datos válidos
            int rowIndex = 1;
            for (ProductoCanalPrecio precio : preciosValidos) {
                Producto producto = precio.getProducto();
                String mla = producto.getMla().getMla();

                BigDecimal precioML = precio.getPvpInflado();
                if (precioML == null || precioML.compareTo(BigDecimal.ZERO) <= 0) {
                    precioML = precio.getPvp();
                }

                Row row = sheet.createRow(rowIndex++);
                setCellValue(row.createCell(0), producto.getSku(), dataStyle);
                setCellValue(row.createCell(1), precioML, precioStyle);
                setCellValue(row.createCell(2), mla, dataStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Fijar la primera fila (header)
            sheet.createFreezePane(0, 1);

            workbook.write(outputStream);
            String nombreArchivo = ("MERCADOLIBRE_" + descCuotas).toUpperCase().replaceAll("\\s+", "_");
            log.info("Exportación para Mercado Libre completada: {} filas exportadas, {} advertencias",
                    rowIndex - 1, advertencias.size());
            return ExportResultDTO.of(outputStream.toByteArray(), advertencias, nombreArchivo);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ExportResultDTO exportarKtHogar(Integer cuotas) throws IOException {
        log.info("Iniciando exportación para KT HOGAR con cuotas {}", cuotas);

        // Buscar canal KT HOGAR
        Canal canal = canalRepository.findByNombreIgnoreCase("KT HOGAR")
                .orElseThrow(() -> new IllegalArgumentException("Canal 'KT HOGAR' no encontrado en la base de datos"));

        // Obtener descripción de cuotas
        String descCuotas = canalConceptoCuotaRepository.findByCanalIdAndCuotas(canal.getId(), cuotas).stream()
                .map(CanalConceptoCuota::getDescripcion)
                .findFirst().orElse(String.valueOf(cuotas));

        // Obtener todos los precios para el canal y cuotas
        List<ProductoCanalPrecio> precios = productoCanalPrecioRepository.findByCanalIdAndCuotas(canal.getId(), cuotas);

        if (precios.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("No existen precios para el canal '%s' con cuotas '%s'",
                            canal.getNombre(), descCuotas));
        }

        // Verificar que el canal tenga FLAG_APLICAR_PRECIO_INFLADO (requisito para este formato)
        boolean usaPrecioInflado = canalConceptoRepository.findByCanalId(canal.getId()).stream()
                .anyMatch(cc -> cc.getConcepto() != null
                        && cc.getConcepto().getAplicaSobre() == AplicaSobre.FLAG_APLICAR_PRECIO_INFLADO);

        // Filtrar productos válidos y recolectar advertencias
        List<String> advertencias = new ArrayList<>();

        if (!usaPrecioInflado) {
            advertencias.add("El canal '" + canal.getNombre() + "' no tiene el concepto FLAG_APLICAR_PRECIO_INFLADO. " +
                    "Los productos no tendrán PVP Inflado calculado.");
        }

        List<String> productosConPvpInvalido = new ArrayList<>();
        List<String> productosConPvpInfladoInvalido = new ArrayList<>();
        List<ProductoCanalPrecio> preciosValidos = new ArrayList<>();

        for (ProductoCanalPrecio precio : precios) {
            Producto producto = precio.getProducto();
            if (producto == null) continue;

            BigDecimal pvp = precio.getPvp();
            BigDecimal pvpInflado = precio.getPvpInflado();

            // Verificar PVP válido
            if (pvp == null || pvp.compareTo(BigDecimal.ZERO) <= 0) {
                productosConPvpInvalido.add(producto.getSku());
                continue;
            }

            // Verificar PVP Inflado válido
            if (pvpInflado == null || pvpInflado.compareTo(BigDecimal.ZERO) <= 0) {
                productosConPvpInfladoInvalido.add(producto.getSku());
                continue;
            }

            preciosValidos.add(precio);
        }

        // Recolectar advertencias
        if (!productosConPvpInvalido.isEmpty()) {
            advertencias.add(truncarListaSkus("Productos con PVP inválido (<=0)", productosConPvpInvalido));
        }
        if (!productosConPvpInfladoInvalido.isEmpty()) {
            advertencias.add(truncarListaSkus("Productos con PVP Inflado inválido (<=0)", productosConPvpInfladoInvalido));
        }

        if (preciosValidos.isEmpty()) {
            throw new IllegalArgumentException("No hay productos válidos para exportar (todos con precios inválidos)");
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.createSheet("KT HOGAR (" + descCuotas + ")");

            CellStyle headerStyle = crearEstiloHeaderCentrado(workbook, IndexedColors.GREY_25_PERCENT.getIndex(), false);
            CellStyle dataStyle = crearEstiloDataCentrado(workbook);
            CellStyle precioStyle = crearEstiloPrecio(workbook);

            // Headers: SKU, PVP_KT_HOGAR (descCuotas), PVP_INFLADO (descCuotas)
            Row headerRow = sheet.createRow(0);
            String[] headers = {"SKU", "PVP_KT_HOGAR (" + descCuotas + ")", "PVP_INFLADO (" + descCuotas + ")"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Escribir datos válidos
            int rowIndex = 1;
            for (ProductoCanalPrecio precio : preciosValidos) {
                Producto producto = precio.getProducto();

                Row row = sheet.createRow(rowIndex++);
                setCellValue(row.createCell(0), producto.getSku(), dataStyle);
                setCellValue(row.createCell(1), precio.getPvp(), precioStyle);
                setCellValue(row.createCell(2), precio.getPvpInflado(), precioStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Fijar la primera fila (header)
            sheet.createFreezePane(0, 1);

            workbook.write(outputStream);
            String nombreArchivo = ("KT_HOGAR_" + descCuotas).toUpperCase().replaceAll("\\s+", "_");
            log.info("Exportación para KT HOGAR completada: {} filas exportadas, {} advertencias",
                    rowIndex - 1, advertencias.size());
            return ExportResultDTO.of(outputStream.toByteArray(), advertencias, nombreArchivo);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ExportResultDTO exportarKtGastro(Integer cuotas) throws IOException {
        log.info("Iniciando exportación para KT GASTRO con cuotas {}", cuotas);

        Canal canal = canalRepository.findByNombreIgnoreCase("KT GASTRO")
                .orElseThrow(() -> new IllegalArgumentException("Canal 'KT GASTRO' no encontrado en la base de datos"));

        // Obtener descripción de cuotas
        String descCuotas = canalConceptoCuotaRepository.findByCanalIdAndCuotas(canal.getId(), cuotas).stream()
                .map(CanalConceptoCuota::getDescripcion)
                .findFirst().orElse(String.valueOf(cuotas));

        List<ProductoCanalPrecio> precios = productoCanalPrecioRepository.findByCanalIdAndCuotas(canal.getId(), cuotas);

        if (precios.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("No existen precios para el canal '%s' con cuotas '%s'",
                            canal.getNombre(), descCuotas));
        }

        List<String> advertencias = new ArrayList<>();
        List<String> productosConPvpInvalido = new ArrayList<>();
        List<String> productosSinIva = new ArrayList<>();
        List<ProductoCanalPrecio> preciosValidos = new ArrayList<>();

        for (ProductoCanalPrecio precio : precios) {
            Producto producto = precio.getProducto();
            if (producto == null) continue;

            BigDecimal pvp = precio.getPvp();
            if (pvp == null || pvp.compareTo(BigDecimal.ZERO) <= 0) {
                productosConPvpInvalido.add(producto.getSku());
                continue;
            }

            if (producto.getIva() == null) {
                productosSinIva.add(producto.getSku());
                continue;
            }

            preciosValidos.add(precio);
        }

        if (!productosConPvpInvalido.isEmpty()) {
            advertencias.add(truncarListaSkus("Productos con PVP inválido (<=0)", productosConPvpInvalido));
        }
        if (!productosSinIva.isEmpty()) {
            advertencias.add(truncarListaSkus("Productos sin IVA definido", productosSinIva));
        }

        if (preciosValidos.isEmpty()) {
            throw new IllegalArgumentException("No hay productos válidos para exportar (todos con precios inválidos o sin IVA)");
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.createSheet("KT GASTRO (" + descCuotas + ")");

            CellStyle headerStyle = crearEstiloHeaderCentrado(workbook, IndexedColors.GREY_25_PERCENT.getIndex(), false);
            CellStyle dataStyle = crearEstiloDataCentrado(workbook);
            CellStyle precioStyle = crearEstiloPrecio(workbook);

            Row headerRow = sheet.createRow(0);
            String[] headers = {"SKU", "PVP_GASTRO_S_IVA (" + descCuotas + ")"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (ProductoCanalPrecio precio : preciosValidos) {
                Producto producto = precio.getProducto();
                BigDecimal pvp = precio.getPvp();
                BigDecimal iva = producto.getIva();

                // pvpSinIva = pvp / (1 + iva/100)
                BigDecimal divisor = BigDecimal.ONE.add(iva.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
                BigDecimal pvpSinIva = pvp.divide(divisor, 2, RoundingMode.HALF_UP);

                Row row = sheet.createRow(rowIndex++);
                setCellValue(row.createCell(0), producto.getSku(), dataStyle);
                setCellValue(row.createCell(1), pvpSinIva, precioStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            sheet.createFreezePane(0, 1);

            workbook.write(outputStream);
            String nombreArchivo = ("KT_GASTRO_" + descCuotas).toUpperCase().replaceAll("\\s+", "_");
            log.info("Exportación para KT GASTRO completada: {} filas exportadas, {} advertencias",
                    rowIndex - 1, advertencias.size());
            return ExportResultDTO.of(outputStream.toByteArray(), advertencias, nombreArchivo);
        }
    }

    private int compareNullsLast(String s1, String s2) {
        if (s1 == null && s2 == null) return 0;
        if (s1 == null) return 1;
        if (s2 == null) return -1;
        return s1.compareToIgnoreCase(s2);
    }

    // ============================================================
    // IMPORTACIÓN DE TABLAS AUXILIARES
    // ============================================================
    //
    // Importa el Excel "Plantilla_Tablas_SuperMaster.xlsx" que contiene una hoja
    // por cada tabla pequeña: Marcas, Tipos, Materiales, Origenes,
    // Clasif. Grales, Clasif. Gastro, Aptos, Proveedores.
    //
    // Estructura de cada hoja (offsets de fila):
    //   0 -> título (ej: "MARCAS  —  Tabla: marcas")
    //   1 -> tipos de datos por columna
    //   2 -> nombres de columnas
    //   3+ -> datos
    //
    // Para hojas jerárquicas (Marcas, Tipos, Clasif. Grales, Clasif. Gastro) se hacen
    // dos pasadas: primero crear/actualizar la entidad sin padre y guardar un mapa
    // idExcel -> entidad; luego asignar el padre resolviéndolo por idExcel.
    // ============================================================

    private static final int FILA_INICIO_DATOS_AUX = 3;
    private static final int FILA_ENCABEZADOS_AUX = 2;

    /**
     * Tablas que limpia el endpoint /api/excel/limpiar-datos.
     * El orden es: primero las hojas/dependientes y al final las maestras puras.
     * Igualmente desactivo FOREIGN_KEY_CHECKS para no depender del orden y poder
     * ejecutar los DELETE sin tener que preocuparme por ciclos.
     */
    private static final List<String> TABLAS_A_LIMPIAR = List.of(
            "venta_diaria_cache",
            "orden_compra_lineas",
            "ordenes_compra",
            "producto_apto",
            "producto_catalogo",
            "producto_cliente",
            "producto_canal_precio_inflado",
            "producto_canal_precios",
            "producto_margen",
            "mlas",
            "productos",
            "clientes",
            "marcas",
            "tipos",
            "materiales",
            "origenes",
            "clasif_gral",
            "clasif_gastro",
            "proveedores"
    );

    @Override
    @Transactional
    public LimpiezaDatosResultDTO limpiarDatos() {
        log.warn("===> Iniciando LIMPIEZA DESTRUCTIVA de datos: {} tablas", TABLAS_A_LIMPIAR.size());

        List<String> tablasLimpiadas = new ArrayList<>();
        List<String> errores = new ArrayList<>();

        // Limpiar caches en memoria del service (referencias a entidades borradas)
        cacheMarcas.clear();
        cacheTipos.clear();
        cacheMateriales.clear();
        cacheOrigenes.clear();
        cacheClasifGral.clear();
        cacheClasifGastro.clear();
        cacheProveedores.clear();
        cacheCatalogos.clear();
        cacheCanales.clear();

        // Desactivar FK checks para evitar problemas con orden de borrado y ciclos
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
        try {
            for (String tabla : TABLAS_A_LIMPIAR) {
                try {
                    entityManager.createNativeQuery("DELETE FROM supermaster." + tabla).executeUpdate();
                    entityManager.createNativeQuery("ALTER TABLE supermaster." + tabla + " AUTO_INCREMENT = 1").executeUpdate();
                    tablasLimpiadas.add(tabla);
                    log.info("  ✓ Tabla supermaster.{} vaciada y AUTO_INCREMENT reseteado", tabla);
                } catch (Exception e) {
                    log.error("  ✗ Error limpiando supermaster.{}: {}", tabla, e.getMessage(), e);
                    errores.add("supermaster." + tabla + ": " + e.getMessage());
                }
            }
        } finally {
            entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
        }

        // Forzar a Hibernate a vaciar el contexto de persistencia tras los DELETE nativos
        entityManager.flush();
        entityManager.clear();

        log.warn("===> Limpieza terminada: {} tablas OK, {} errores", tablasLimpiadas.size(), errores.size());
        return errores.isEmpty()
                ? LimpiezaDatosResultDTO.success(tablasLimpiadas)
                : LimpiezaDatosResultDTO.withErrors(tablasLimpiadas, errores);
    }

    @Override
    @Transactional
    public ImportCompletoResultDTO importarTablasAuxiliares(MultipartFile file) throws IOException {
        log.info("Iniciando importación de tablas auxiliares desde archivo: {}", file.getOriginalFilename());

        if (file.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        if (!isExcelFile(file)) {
            throw new IllegalArgumentException("El archivo debe tener extensión .xls, .xlsx o .xlsm");
        }

        // Limpiar caches para evitar reutilizar referencias de importaciones previas
        cacheMarcas.clear();
        cacheTipos.clear();
        cacheMateriales.clear();
        cacheOrigenes.clear();
        cacheClasifGral.clear();
        cacheClasifGastro.clear();
        cacheProveedores.clear();

        try (Workbook workbook = abrirWorkbookEficiente(file)) {
            // Recolectar las hojas reconocidas (en el orden del workbook)
            Map<String, Sheet> sheetsPorTipo = new LinkedHashMap<>();
            Map<String, String> nombreHojaPorTipo = new LinkedHashMap<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sh = workbook.getSheetAt(i);
                String nm = sh.getSheetName();
                String tipo = determinarTipoTablaAuxiliar(nm);
                if (tipo == null) {
                    log.info("Hoja '{}' no reconocida como tabla auxiliar, se omite", nm);
                    continue;
                }
                sheetsPorTipo.put(tipo, sh);
                nombreHojaPorTipo.put(tipo, nm);
            }

            int totalHojas = sheetsPorTipo.size();
            List<String> erroresGenerales = new ArrayList<>();
            Map<String, ImportResultDTO> resultadosPorHoja = new LinkedHashMap<>();

            if (totalHojas == 0) {
                erroresGenerales.add("No se encontró ninguna hoja reconocida (Marcas, Tipos, Materiales, Origenes, Clasif. Grales, Clasif. Gastro, Aptos, Proveedores)");
                return ImportCompletoResultDTO.withErrors(0, 0, 0, resultadosPorHoja, erroresGenerales);
            }

            // PASO 1: Limpiar las tablas auxiliares que se van a importar.
            // Si hay registros en tablas dependientes (productos, canal_concepto_regla,
            // regla_descuento, producto_apto), el DELETE va a fallar por FK y la
            // transacción se va a hacer rollback.
            try {
                limpiarTablasAuxiliares(sheetsPorTipo.keySet());
            } catch (Exception e) {
                log.error("No se pudo limpiar las tablas auxiliares: {}", e.getMessage(), e);
                erroresGenerales.add("No se pudieron limpiar las tablas auxiliares antes de importar. "
                        + "Verifique que no haya registros en productos, producto_apto, "
                        + "canal_concepto_regla ni regla_descuento. Detalle: " + e.getMessage());
                return ImportCompletoResultDTO.withErrors(totalHojas, 0, totalHojas,
                        resultadosPorHoja, erroresGenerales);
            }

            // PASO 2: Importar cada hoja con IDs explícitos del Excel
            int hojasProcesadas = 0;
            int hojasConErrores = 0;
            for (Map.Entry<String, Sheet> entry : sheetsPorTipo.entrySet()) {
                String tipo = entry.getKey();
                Sheet sheet = entry.getValue();
                String nombreHoja = nombreHojaPorTipo.get(tipo);
                log.info("Procesando hoja '{}' como tipo auxiliar '{}'", nombreHoja, tipo);
                try {
                    ImportResultDTO resultado = procesarHojaTablaAuxiliar(sheet, tipo, nombreHoja);
                    resultadosPorHoja.put(nombreHoja, resultado);
                    if (resultado.errorRows() == 0) {
                        hojasProcesadas++;
                    } else {
                        hojasConErrores++;
                    }
                } catch (Exception e) {
                    log.error("Error procesando hoja '{}': {}", nombreHoja, e.getMessage(), e);
                    hojasConErrores++;
                    resultadosPorHoja.put(nombreHoja, ImportResultDTO.withErrors(0, 0, 1,
                            List.of("Error procesando hoja '" + nombreHoja + "': " + e.getMessage())));
                }
            }

            if (hojasConErrores == 0 && erroresGenerales.isEmpty()) {
                return ImportCompletoResultDTO.success(totalHojas, hojasProcesadas, resultadosPorHoja);
            }
            return ImportCompletoResultDTO.withErrors(totalHojas, hojasProcesadas, hojasConErrores,
                    resultadosPorHoja, erroresGenerales);
        }
    }

    /**
     * Borra todos los registros de las tablas auxiliares que se van a importar y
     * resetea su AUTO_INCREMENT. Falla con DataIntegrityViolationException si hay
     * tablas dependientes (productos, producto_apto, canal_concepto_regla,
     * regla_descuento) que referencien estos registros.
     */
    private void limpiarTablasAuxiliares(Set<String> tiposAImportar) {
        // El orden de limpieza no importa entre estas tablas (no se referencian
        // entre sí, salvo la auto-referencia padre que se borra junto con la tabla).
        // Lo que sí importa es que se haga DENTRO de la misma transacción que los inserts.
        Map<String, String> tablaPorTipo = Map.of(
                "marcas", "marcas",
                "tipos", "tipos",
                "materiales", "materiales",
                "origenes", "origenes",
                "clasif_gral", "clasif_gral",
                "clasif_gastro", "clasif_gastro",
                "aptos", "aptos",
                "proveedores", "proveedores"
        );

        for (String tipo : tiposAImportar) {
            String tabla = tablaPorTipo.get(tipo);
            if (tabla == null) continue;
            log.info("Limpiando tabla supermaster.{} ...", tabla);
            entityManager.createNativeQuery("DELETE FROM supermaster." + tabla).executeUpdate();
            entityManager.createNativeQuery("ALTER TABLE supermaster." + tabla + " AUTO_INCREMENT = 1").executeUpdate();
        }
        entityManager.flush();
    }

    /**
     * Mapea el nombre de la hoja al tipo de tabla auxiliar.
     */
    private String determinarTipoTablaAuxiliar(String nombreHoja) {
        if (nombreHoja == null) return null;
        String n = nombreHoja.trim().toUpperCase();
        // Normalizamos quitando puntos y espacios para tolerar variantes
        String compacto = n.replace(".", "").replace(" ", "");
        return switch (compacto) {
            case "MARCAS", "MARCA" -> "marcas";
            case "TIPOS", "TIPO" -> "tipos";
            case "MATERIALES", "MATERIAL" -> "materiales";
            case "ORIGENES", "ORIGEN", "ORÍGENES" -> "origenes";
            case "CLASIFGRALES", "CLASIFGRAL", "CLASIFGENERAL", "CLASIFGENERALES" -> "clasif_gral";
            case "CLASIFGASTRO", "CLASIFGASTROS" -> "clasif_gastro";
            case "APTOS", "APTO" -> "aptos";
            case "PROVEEDORES", "PROVEEDOR" -> "proveedores";
            default -> null;
        };
    }

    private ImportResultDTO procesarHojaTablaAuxiliar(Sheet sheet, String tipo, String nombreHoja) {
        Row headerRow = sheet.getRow(FILA_ENCABEZADOS_AUX);
        if (headerRow == null) {
            throw new IllegalArgumentException("La hoja '" + nombreHoja + "' no tiene encabezados en la fila " + (FILA_ENCABEZADOS_AUX + 1));
        }
        Map<String, Integer> columnas = mapearColumnasPorNombre(headerRow);
        log.info("Columnas mapeadas para hoja auxiliar '{}': {}", nombreHoja, columnas.keySet());

        return switch (tipo) {
            case "marcas" -> importarHojaMarcas(sheet, columnas, nombreHoja);
            case "tipos" -> importarHojaTipos(sheet, columnas, nombreHoja);
            case "materiales" -> importarHojaMateriales(sheet, columnas, nombreHoja);
            case "origenes" -> importarHojaOrigenes(sheet, columnas, nombreHoja);
            case "clasif_gral" -> importarHojaClasifGral(sheet, columnas, nombreHoja);
            case "clasif_gastro" -> importarHojaClasifGastro(sheet, columnas, nombreHoja);
            case "aptos" -> importarHojaAptos(sheet, columnas, nombreHoja);
            case "proveedores" -> importarHojaProveedores(sheet, columnas, nombreHoja);
            default -> throw new IllegalArgumentException("Tipo de tabla auxiliar no soportado: " + tipo);
        };
    }

    // -------------------- Helpers para hojas auxiliares ----------------------

    private Integer leerEntero(Row row, Map<String, Integer> columnas, String nombreColumna) {
        Integer idx = columnas.get(nombreColumna.trim().toUpperCase());
        if (idx == null) return null;
        String valor = obtenerValorCelda(row, idx);
        if (valor == null || valor.isBlank()) return null;
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String leerTexto(Row row, Map<String, Integer> columnas, String nombreColumna) {
        Integer idx = columnas.get(nombreColumna.trim().toUpperCase());
        if (idx == null) return null;
        String valor = obtenerValorCelda(row, idx);
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    private Boolean leerBooleano(Row row, Map<String, Integer> columnas, String nombreColumna) {
        String valor = leerTexto(row, columnas, nombreColumna);
        if (valor == null) return null;
        String v = valor.toUpperCase();
        if (v.equals("SI") || v.equals("SÍ") || v.equals("TRUE") || v.equals("1") || v.equals("X") || v.equals("VERDADERO")) return true;
        if (v.equals("NO") || v.equals("FALSE") || v.equals("0") || v.equals("FALSO")) return false;
        return null;
    }

    private BigDecimal leerDecimal(Row row, Map<String, Integer> columnas, String nombreColumna) {
        String valor = leerTexto(row, columnas, nombreColumna);
        if (valor == null) return null;
        try {
            // Permitir comas decimales (es-AR)
            return new BigDecimal(valor.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Lee un valor porcentual desde una celda y lo normaliza a porcentaje (60% -> 60.000).
     * Acepta:
     *   - Texto con sufijo "%" (ej. "60%") -> 60
     *   - Decimal entre -1 y 1 (ej. "0.6") -> 60 (Excel guarda los % como decimal)
     *   - Entero o decimal sin sufijo y |valor| >= 1 (ej. "60") -> 60 tal cual
     *   - Coma decimal (es-AR) -> reemplazada por punto
     * Devuelve null si la columna no existe, la celda está vacía o no se puede parsear.
     */
    private BigDecimal leerPorcentajeDeColumna(Row row, Map<String, Integer> columnasMap, String nombreColumna) {
        if (columnasMap == null || !columnasMap.containsKey(nombreColumna.toUpperCase())) return null;
        Integer idx = columnasMap.get(nombreColumna.toUpperCase());
        if (idx == null) return null;
        String valor = obtenerValorCelda(row, idx);
        if (valor == null || valor.isBlank()) return null;
        boolean tieneSufijoPorcentaje = valor.contains("%");
        String limpio = valor.replace("%", "").replace(",", ".").trim();
        if (limpio.isEmpty()) return null;
        try {
            BigDecimal num = new BigDecimal(limpio);
            if (tieneSufijoPorcentaje) return num;
            // Si Excel guardó el valor como decimal de porcentaje (|x| < 1), multiplicamos.
            if (num.abs().compareTo(BigDecimal.ONE) < 0) {
                return num.multiply(BigDecimal.valueOf(100));
            }
            return num;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // -------------------- MARCAS ----------------------

    private ImportResultDTO importarHojaMarcas(Sheet sheet, Map<String, Integer> columnas, String nombreHoja) {
        int filaFin = sheet.getLastRowNum();
        Map<Integer, Integer> padresPendientes = new LinkedHashMap<>();
        Map<Integer, Integer> idVisto = new HashMap<>();
        Map<String, Integer> nombreVisto = new HashMap<>();
        int totalRows = 0;
        int successRows = 0;
        List<String> errores = new ArrayList<>();

        // Pasada 1: INSERT con id_padre = NULL usando el ID del Excel
        for (int i = FILA_INICIO_DATOS_AUX; i <= filaFin; i++) {
            Row row = sheet.getRow(i);
            if (row == null || esFilaVacia(row)) continue;
            totalRows++;
            try {
                Integer idExcel = leerEntero(row, columnas, "ID");
                String nombre = leerTexto(row, columnas, "Nombre");
                Integer idPadre = leerEntero(row, columnas, "ID Padre");
                if (idExcel == null) {
                    errores.add("Fila " + (i + 1) + ": columna ID vacía");
                    continue;
                }
                if (nombre == null) {
                    errores.add("Fila " + (i + 1) + ": columna Nombre vacía");
                    continue;
                }
                Integer filaIdPrev = idVisto.get(idExcel);
                if (filaIdPrev != null) {
                    errores.add("Fila " + (i + 1) + ": ID " + idExcel + " duplicado (ya estaba en fila " + filaIdPrev + ")");
                    continue;
                }
                // Clave compuesta (nombre, id_padre): permite mismo nombre con padres distintos.
                String claveNombre = nombre.toUpperCase() + "|PADRE:" + (idPadre == null ? "null" : idPadre);
                Integer filaNombrePrev = nombreVisto.get(claveNombre);
                if (filaNombrePrev != null) {
                    errores.add("Fila " + (i + 1) + ": '" + nombre + "' (padre=" + idPadre + ") duplicado (ya estaba en fila " + filaNombrePrev + ")");
                    continue;
                }
                String nombreTrunc = nombre.length() > 45 ? nombre.substring(0, 45) : nombre;
                entityManager.createNativeQuery(
                        "INSERT INTO supermaster.marcas (id_marca, nombre, id_padre) VALUES (?, ?, NULL)")
                        .setParameter(1, idExcel)
                        .setParameter(2, nombreTrunc)
                        .executeUpdate();
                idVisto.put(idExcel, i + 1);
                nombreVisto.put(claveNombre, i + 1);
                if (idPadre != null) padresPendientes.put(idExcel, idPadre);
                successRows++;
            } catch (Exception e) {
                errores.add("Fila " + (i + 1) + ": " + e.getMessage());
            }
        }
        entityManager.flush();

        // Pasada 2: UPDATE id_padre (los ids del Excel = ids reales en la BD)
        // Validar que el padre fue insertado: si no, reportar y saltar el UPDATE
        // para no disparar una FK violation que marca la transacción rollback-only.
        for (Map.Entry<Integer, Integer> entry : padresPendientes.entrySet()) {
            Integer idHijo = entry.getKey();
            Integer idPadre = entry.getValue();
            if (!idVisto.containsKey(idPadre)) {
                errores.add("Marca id=" + idHijo + ": padre id=" + idPadre + " no existe en la hoja (no se insertó)");
                continue;
            }
            entityManager.createNativeQuery(
                    "UPDATE supermaster.marcas SET id_padre = ? WHERE id_marca = ?")
                    .setParameter(1, idPadre)
                    .setParameter(2, idHijo)
                    .executeUpdate();
        }

        log.info("Hoja '{}' (marcas): {}/{} filas procesadas, {} errores", nombreHoja, successRows, totalRows, errores.size());
        return errores.isEmpty()
                ? ImportResultDTO.success(totalRows, successRows)
                : ImportResultDTO.withErrors(totalRows, successRows, errores.size(), errores);
    }

    // -------------------- TIPOS ----------------------

    private ImportResultDTO importarHojaTipos(Sheet sheet, Map<String, Integer> columnas, String nombreHoja) {
        int filaFin = sheet.getLastRowNum();
        Map<Integer, Integer> padresPendientes = new LinkedHashMap<>();
        Map<Integer, Integer> idVisto = new HashMap<>();
        Map<String, Integer> nombreVisto = new HashMap<>();
        int totalRows = 0;
        int successRows = 0;
        List<String> errores = new ArrayList<>();

        for (int i = FILA_INICIO_DATOS_AUX; i <= filaFin; i++) {
            Row row = sheet.getRow(i);
            if (row == null || esFilaVacia(row)) continue;
            totalRows++;
            try {
                Integer idExcel = leerEntero(row, columnas, "ID");
                String nombre = leerTexto(row, columnas, "Nombre");
                Integer idPadre = leerEntero(row, columnas, "ID Padre");
                if (idExcel == null) {
                    errores.add("Fila " + (i + 1) + ": columna ID vacía");
                    continue;
                }
                if (nombre == null) {
                    errores.add("Fila " + (i + 1) + ": columna Nombre vacía");
                    continue;
                }
                Integer filaIdPrev = idVisto.get(idExcel);
                if (filaIdPrev != null) {
                    errores.add("Fila " + (i + 1) + ": ID " + idExcel + " duplicado (ya estaba en fila " + filaIdPrev + ")");
                    continue;
                }
                // Clave compuesta (nombre, id_padre): permite mismo nombre con padres distintos.
                String claveNombre = nombre.toUpperCase() + "|PADRE:" + (idPadre == null ? "null" : idPadre);
                Integer filaNombrePrev = nombreVisto.get(claveNombre);
                if (filaNombrePrev != null) {
                    errores.add("Fila " + (i + 1) + ": '" + nombre + "' (padre=" + idPadre + ") duplicado (ya estaba en fila " + filaNombrePrev + ")");
                    continue;
                }
                String nombreTrunc = nombre.length() > 45 ? nombre.substring(0, 45) : nombre;
                entityManager.createNativeQuery(
                        "INSERT INTO supermaster.tipos (id_tipo, nombre, id_padre) VALUES (?, ?, NULL)")
                        .setParameter(1, idExcel)
                        .setParameter(2, nombreTrunc)
                        .executeUpdate();
                idVisto.put(idExcel, i + 1);
                nombreVisto.put(claveNombre, i + 1);
                if (idPadre != null) padresPendientes.put(idExcel, idPadre);
                successRows++;
            } catch (Exception e) {
                errores.add("Fila " + (i + 1) + ": " + e.getMessage());
            }
        }
        entityManager.flush();

        for (Map.Entry<Integer, Integer> entry : padresPendientes.entrySet()) {
            Integer idHijo = entry.getKey();
            Integer idPadre = entry.getValue();
            if (!idVisto.containsKey(idPadre)) {
                errores.add("Tipo id=" + idHijo + ": padre id=" + idPadre + " no existe en la hoja (no se insertó)");
                continue;
            }
            entityManager.createNativeQuery(
                    "UPDATE supermaster.tipos SET id_padre = ? WHERE id_tipo = ?")
                    .setParameter(1, idPadre)
                    .setParameter(2, idHijo)
                    .executeUpdate();
        }

        log.info("Hoja '{}' (tipos): {}/{} filas procesadas, {} errores", nombreHoja, successRows, totalRows, errores.size());
        return errores.isEmpty()
                ? ImportResultDTO.success(totalRows, successRows)
                : ImportResultDTO.withErrors(totalRows, successRows, errores.size(), errores);
    }

    // -------------------- MATERIALES ----------------------

    private ImportResultDTO importarHojaMateriales(Sheet sheet, Map<String, Integer> columnas, String nombreHoja) {
        int filaFin = sheet.getLastRowNum();
        Map<Integer, Integer> idVisto = new HashMap<>();
        Map<String, Integer> nombreVisto = new HashMap<>();
        int totalRows = 0;
        int successRows = 0;
        List<String> errores = new ArrayList<>();

        for (int i = FILA_INICIO_DATOS_AUX; i <= filaFin; i++) {
            Row row = sheet.getRow(i);
            if (row == null || esFilaVacia(row)) continue;
            totalRows++;
            try {
                Integer idExcel = leerEntero(row, columnas, "ID");
                String nombre = leerTexto(row, columnas, "Material");
                if (idExcel == null) {
                    errores.add("Fila " + (i + 1) + ": columna ID vacía");
                    continue;
                }
                if (nombre == null) {
                    errores.add("Fila " + (i + 1) + ": columna Material vacía");
                    continue;
                }
                Integer filaIdPrev = idVisto.get(idExcel);
                if (filaIdPrev != null) {
                    errores.add("Fila " + (i + 1) + ": ID " + idExcel + " duplicado (ya estaba en fila " + filaIdPrev + ")");
                    continue;
                }
                String claveNombre = nombre.toUpperCase();
                Integer filaNombrePrev = nombreVisto.get(claveNombre);
                if (filaNombrePrev != null) {
                    errores.add("Fila " + (i + 1) + ": material '" + nombre + "' duplicado (ya estaba en fila " + filaNombrePrev + ")");
                    continue;
                }
                String nombreTrunc = nombre.length() > 45 ? nombre.substring(0, 45) : nombre;
                entityManager.createNativeQuery(
                        "INSERT INTO supermaster.materiales (id_material, nombre) VALUES (?, ?)")
                        .setParameter(1, idExcel)
                        .setParameter(2, nombreTrunc)
                        .executeUpdate();
                idVisto.put(idExcel, i + 1);
                nombreVisto.put(claveNombre, i + 1);
                successRows++;
            } catch (Exception e) {
                errores.add("Fila " + (i + 1) + ": " + e.getMessage());
            }
        }
        entityManager.flush();

        log.info("Hoja '{}' (materiales): {}/{} filas procesadas, {} errores", nombreHoja, successRows, totalRows, errores.size());
        return errores.isEmpty()
                ? ImportResultDTO.success(totalRows, successRows)
                : ImportResultDTO.withErrors(totalRows, successRows, errores.size(), errores);
    }

    // -------------------- ORIGENES ----------------------

    private ImportResultDTO importarHojaOrigenes(Sheet sheet, Map<String, Integer> columnas, String nombreHoja) {
        int filaFin = sheet.getLastRowNum();
        Map<Integer, Integer> idVisto = new HashMap<>();
        Map<String, Integer> nombreVisto = new HashMap<>();
        int totalRows = 0;
        int successRows = 0;
        List<String> errores = new ArrayList<>();

        for (int i = FILA_INICIO_DATOS_AUX; i <= filaFin; i++) {
            Row row = sheet.getRow(i);
            if (row == null || esFilaVacia(row)) continue;
            totalRows++;
            try {
                Integer idExcel = leerEntero(row, columnas, "ID");
                String nombre = leerTexto(row, columnas, "Origen");
                if (idExcel == null) {
                    errores.add("Fila " + (i + 1) + ": columna ID vacía");
                    continue;
                }
                if (nombre == null) {
                    errores.add("Fila " + (i + 1) + ": columna Origen vacía");
                    continue;
                }
                Integer filaIdPrev = idVisto.get(idExcel);
                if (filaIdPrev != null) {
                    errores.add("Fila " + (i + 1) + ": ID " + idExcel + " duplicado (ya estaba en fila " + filaIdPrev + ")");
                    continue;
                }
                String claveNombre = nombre.toUpperCase();
                Integer filaNombrePrev = nombreVisto.get(claveNombre);
                if (filaNombrePrev != null) {
                    errores.add("Fila " + (i + 1) + ": origen '" + nombre + "' duplicado (ya estaba en fila " + filaNombrePrev + ")");
                    continue;
                }
                String nombreTrunc = nombre.length() > 45 ? nombre.substring(0, 45) : nombre;
                entityManager.createNativeQuery(
                        "INSERT INTO supermaster.origenes (id_origen, nombre) VALUES (?, ?)")
                        .setParameter(1, idExcel)
                        .setParameter(2, nombreTrunc)
                        .executeUpdate();
                idVisto.put(idExcel, i + 1);
                nombreVisto.put(claveNombre, i + 1);
                successRows++;
            } catch (Exception e) {
                errores.add("Fila " + (i + 1) + ": " + e.getMessage());
            }
        }
        entityManager.flush();

        log.info("Hoja '{}' (origenes): {}/{} filas procesadas, {} errores", nombreHoja, successRows, totalRows, errores.size());
        return errores.isEmpty()
                ? ImportResultDTO.success(totalRows, successRows)
                : ImportResultDTO.withErrors(totalRows, successRows, errores.size(), errores);
    }

    // -------------------- CLASIF GRAL ----------------------

    private ImportResultDTO importarHojaClasifGral(Sheet sheet, Map<String, Integer> columnas, String nombreHoja) {
        int filaFin = sheet.getLastRowNum();
        Map<Integer, Integer> padresPendientes = new LinkedHashMap<>();
        Map<Integer, Integer> idVisto = new HashMap<>();
        Map<String, Integer> nombreVisto = new HashMap<>();
        int totalRows = 0;
        int successRows = 0;
        List<String> errores = new ArrayList<>();

        for (int i = FILA_INICIO_DATOS_AUX; i <= filaFin; i++) {
            Row row = sheet.getRow(i);
            if (row == null || esFilaVacia(row)) continue;
            totalRows++;
            try {
                Integer idExcel = leerEntero(row, columnas, "ID");
                String nombre = leerTexto(row, columnas, "Nombre");
                Integer idPadre = leerEntero(row, columnas, "ID Padre");
                if (idExcel == null) {
                    errores.add("Fila " + (i + 1) + ": columna ID vacía");
                    continue;
                }
                if (nombre == null) {
                    errores.add("Fila " + (i + 1) + ": columna Nombre vacía");
                    continue;
                }
                Integer filaIdPrev = idVisto.get(idExcel);
                if (filaIdPrev != null) {
                    errores.add("Fila " + (i + 1) + ": ID " + idExcel + " duplicado (ya estaba en fila " + filaIdPrev + ")");
                    continue;
                }
                // Clave compuesta (nombre, id_padre): permite mismo nombre con padres distintos.
                String claveNombre = nombre.toUpperCase() + "|PADRE:" + (idPadre == null ? "null" : idPadre);
                Integer filaNombrePrev = nombreVisto.get(claveNombre);
                if (filaNombrePrev != null) {
                    errores.add("Fila " + (i + 1) + ": '" + nombre + "' (padre=" + idPadre + ") duplicado (ya estaba en fila " + filaNombrePrev + ")");
                    continue;
                }
                String nombreTrunc = nombre.length() > 45 ? nombre.substring(0, 45) : nombre;
                entityManager.createNativeQuery(
                        "INSERT INTO supermaster.clasif_gral (id_clasif_gral, nombre, id_padre) VALUES (?, ?, NULL)")
                        .setParameter(1, idExcel)
                        .setParameter(2, nombreTrunc)
                        .executeUpdate();
                idVisto.put(idExcel, i + 1);
                nombreVisto.put(claveNombre, i + 1);
                if (idPadre != null) padresPendientes.put(idExcel, idPadre);
                successRows++;
            } catch (Exception e) {
                errores.add("Fila " + (i + 1) + ": " + e.getMessage());
            }
        }
        entityManager.flush();

        for (Map.Entry<Integer, Integer> entry : padresPendientes.entrySet()) {
            Integer idHijo = entry.getKey();
            Integer idPadre = entry.getValue();
            if (!idVisto.containsKey(idPadre)) {
                errores.add("Clasif. Gral id=" + idHijo + ": padre id=" + idPadre + " no existe en la hoja (no se insertó)");
                continue;
            }
            entityManager.createNativeQuery(
                    "UPDATE supermaster.clasif_gral SET id_padre = ? WHERE id_clasif_gral = ?")
                    .setParameter(1, idPadre)
                    .setParameter(2, idHijo)
                    .executeUpdate();
        }

        log.info("Hoja '{}' (clasif_gral): {}/{} filas procesadas, {} errores", nombreHoja, successRows, totalRows, errores.size());
        return errores.isEmpty()
                ? ImportResultDTO.success(totalRows, successRows)
                : ImportResultDTO.withErrors(totalRows, successRows, errores.size(), errores);
    }

    // -------------------- CLASIF GASTRO ----------------------

    private ImportResultDTO importarHojaClasifGastro(Sheet sheet, Map<String, Integer> columnas, String nombreHoja) {
        int filaFin = sheet.getLastRowNum();
        Map<Integer, Integer> padresPendientes = new LinkedHashMap<>();
        Map<Integer, Integer> idVisto = new HashMap<>();
        Map<String, Integer> nombreVisto = new HashMap<>();
        int totalRows = 0;
        int successRows = 0;
        List<String> errores = new ArrayList<>();

        for (int i = FILA_INICIO_DATOS_AUX; i <= filaFin; i++) {
            Row row = sheet.getRow(i);
            if (row == null || esFilaVacia(row)) continue;
            totalRows++;
            try {
                Integer idExcel = leerEntero(row, columnas, "ID");
                String nombre = leerTexto(row, columnas, "Nombre");
                Boolean esMaquina = leerBooleano(row, columnas, "Es Maquina");
                Integer idPadre = leerEntero(row, columnas, "ID Padre");
                if (idExcel == null) {
                    errores.add("Fila " + (i + 1) + ": columna ID vacía");
                    continue;
                }
                if (nombre == null) {
                    errores.add("Fila " + (i + 1) + ": columna Nombre vacía");
                    continue;
                }
                Integer filaIdPrev = idVisto.get(idExcel);
                if (filaIdPrev != null) {
                    errores.add("Fila " + (i + 1) + ": ID " + idExcel + " duplicado (ya estaba en fila " + filaIdPrev + ")");
                    continue;
                }
                // Clave compuesta (nombre, id_padre): permite mismo nombre con padres distintos.
                String claveNombre = nombre.toUpperCase() + "|PADRE:" + (idPadre == null ? "null" : idPadre);
                Integer filaNombrePrev = nombreVisto.get(claveNombre);
                if (filaNombrePrev != null) {
                    errores.add("Fila " + (i + 1) + ": '" + nombre + "' (padre=" + idPadre + ") duplicado (ya estaba en fila " + filaNombrePrev + ")");
                    continue;
                }
                String nombreTrunc = nombre.length() > 45 ? nombre.substring(0, 45) : nombre;
                boolean esMaquinaFinal = esMaquina != null ? esMaquina : Boolean.FALSE;
                entityManager.createNativeQuery(
                        "INSERT INTO supermaster.clasif_gastro (id_clasif_gastro, nombre, es_maquina, id_padre) VALUES (?, ?, ?, NULL)")
                        .setParameter(1, idExcel)
                        .setParameter(2, nombreTrunc)
                        .setParameter(3, esMaquinaFinal)
                        .executeUpdate();
                idVisto.put(idExcel, i + 1);
                nombreVisto.put(claveNombre, i + 1);
                if (idPadre != null) padresPendientes.put(idExcel, idPadre);
                successRows++;
            } catch (Exception e) {
                errores.add("Fila " + (i + 1) + ": " + e.getMessage());
            }
        }
        entityManager.flush();

        for (Map.Entry<Integer, Integer> entry : padresPendientes.entrySet()) {
            Integer idHijo = entry.getKey();
            Integer idPadre = entry.getValue();
            if (!idVisto.containsKey(idPadre)) {
                errores.add("Clasif. Gastro id=" + idHijo + ": padre id=" + idPadre + " no existe en la hoja (no se insertó)");
                continue;
            }
            entityManager.createNativeQuery(
                    "UPDATE supermaster.clasif_gastro SET id_padre = ? WHERE id_clasif_gastro = ?")
                    .setParameter(1, idPadre)
                    .setParameter(2, idHijo)
                    .executeUpdate();
        }

        log.info("Hoja '{}' (clasif_gastro): {}/{} filas procesadas, {} errores", nombreHoja, successRows, totalRows, errores.size());
        return errores.isEmpty()
                ? ImportResultDTO.success(totalRows, successRows)
                : ImportResultDTO.withErrors(totalRows, successRows, errores.size(), errores);
    }

    // -------------------- APTOS ----------------------

    private ImportResultDTO importarHojaAptos(Sheet sheet, Map<String, Integer> columnas, String nombreHoja) {
        int filaFin = sheet.getLastRowNum();
        Map<Integer, Integer> idVisto = new HashMap<>();
        Map<String, Integer> nombreVisto = new HashMap<>();
        int totalRows = 0;
        int successRows = 0;
        List<String> errores = new ArrayList<>();

        for (int i = FILA_INICIO_DATOS_AUX; i <= filaFin; i++) {
            Row row = sheet.getRow(i);
            if (row == null || esFilaVacia(row)) continue;
            totalRows++;
            try {
                Integer idExcel = leerEntero(row, columnas, "ID");
                String nombre = leerTexto(row, columnas, "Apto");
                if (idExcel == null) {
                    errores.add("Fila " + (i + 1) + ": columna ID vacía");
                    continue;
                }
                if (nombre == null) {
                    errores.add("Fila " + (i + 1) + ": columna Apto vacía");
                    continue;
                }
                Integer filaIdPrev = idVisto.get(idExcel);
                if (filaIdPrev != null) {
                    errores.add("Fila " + (i + 1) + ": ID " + idExcel + " duplicado (ya estaba en fila " + filaIdPrev + ")");
                    continue;
                }
                String claveNombre = nombre.toUpperCase();
                Integer filaNombrePrev = nombreVisto.get(claveNombre);
                if (filaNombrePrev != null) {
                    errores.add("Fila " + (i + 1) + ": apto '" + nombre + "' duplicado (ya estaba en fila " + filaNombrePrev + ")");
                    continue;
                }
                String nombreTrunc = nombre.length() > 45 ? nombre.substring(0, 45) : nombre;
                entityManager.createNativeQuery(
                        "INSERT INTO supermaster.aptos (id_apto, nombre) VALUES (?, ?)")
                        .setParameter(1, idExcel)
                        .setParameter(2, nombreTrunc)
                        .executeUpdate();
                idVisto.put(idExcel, i + 1);
                nombreVisto.put(claveNombre, i + 1);
                successRows++;
            } catch (Exception e) {
                errores.add("Fila " + (i + 1) + ": " + e.getMessage());
            }
        }
        entityManager.flush();

        log.info("Hoja '{}' (aptos): {}/{} filas procesadas, {} errores", nombreHoja, successRows, totalRows, errores.size());
        return errores.isEmpty()
                ? ImportResultDTO.success(totalRows, successRows)
                : ImportResultDTO.withErrors(totalRows, successRows, errores.size(), errores);
    }

    /**
     * Variante de buscarOCrearClasifGastro usada solo por la importación de tablas
     * auxiliares: setea es_maquina al crear (la columna es NOT NULL en la BD). Si la
     * celda viene vacía, se crea con es_maquina = false.
     */
    private ClasifGastro buscarOCrearClasifGastroImport(String nombre, Boolean esMaquina) {
        if (nombre == null || nombre.trim().isEmpty()) return null;
        String nombreNormalizado = nombre.trim();
        Optional<ClasifGastro> existente = clasifGastroRepository.findByNombreIgnoreCase(nombreNormalizado);
        if (existente.isPresent()) return existente.get();

        ClasifGastro nueva = new ClasifGastro();
        nueva.setNombre(nombreNormalizado.length() > 45 ? nombreNormalizado.substring(0, 45) : nombreNormalizado);
        nueva.setEsMaquina(esMaquina != null ? esMaquina : Boolean.FALSE);
        return clasifGastroRepository.save(nueva);
    }

    /**
     * Variante de buscarOCrearProveedor usada solo por la importación de tablas
     * auxiliares: si el apodo viene vacío en el Excel, el proveedor se crea con
     * apodo "" en lugar de copiar el nombre como fallback.
     */
    private Proveedor buscarOCrearProveedorImport(String nombre, String apodo) {
        if (nombre == null || nombre.trim().isEmpty()) return null;
        String nombreNormalizado = nombre.trim();
        Optional<Proveedor> existente = proveedorRepository.findByNombreIgnoreCase(nombreNormalizado);
        if (existente.isPresent()) return existente.get();

        Proveedor nuevo = new Proveedor();
        nuevo.setNombre(nombreNormalizado.length() > 100 ? nombreNormalizado.substring(0, 100) : nombreNormalizado);
        if (apodo != null && !apodo.isBlank()) {
            String apodoTrim = apodo.trim();
            nuevo.setApodo(apodoTrim.length() > 50 ? apodoTrim.substring(0, 50) : apodoTrim);
        } else {
            nuevo.setApodo("");
        }
        return proveedorRepository.save(nuevo);
    }

    private Apto buscarOCrearApto(String nombre) {
        String nombreNormalizado = nombre.trim();
        // No hay findByNombreIgnoreCase en AptoRepository; usamos findAll en memoria
        // (la tabla es muy chica, < 20 filas).
        Optional<Apto> existente = aptoRepository.findAll().stream()
                .filter(a -> nombreNormalizado.equalsIgnoreCase(a.getNombre()))
                .findFirst();
        if (existente.isPresent()) return existente.get();

        Apto nuevo = new Apto();
        nuevo.setNombre(nombreNormalizado.length() > 45 ? nombreNormalizado.substring(0, 45) : nombreNormalizado);
        return aptoRepository.save(nuevo);
    }

    // -------------------- PROVEEDORES ----------------------

    private ImportResultDTO importarHojaProveedores(Sheet sheet, Map<String, Integer> columnas, String nombreHoja) {
        int filaFin = sheet.getLastRowNum();
        Map<Integer, Integer> idVisto = new HashMap<>();
        Map<String, Integer> nombreVisto = new HashMap<>();
        int totalRows = 0;
        int successRows = 0;
        List<String> errores = new ArrayList<>();

        for (int i = FILA_INICIO_DATOS_AUX; i <= filaFin; i++) {
            Row row = sheet.getRow(i);
            if (row == null || esFilaVacia(row)) continue;
            totalRows++;
            try {
                Integer idExcel = leerEntero(row, columnas, "ID");
                String nombre = leerTexto(row, columnas, "Proveedor");
                if (idExcel == null) {
                    errores.add("Fila " + (i + 1) + ": columna ID vacía");
                    continue;
                }
                if (nombre == null) {
                    errores.add("Fila " + (i + 1) + ": columna Proveedor vacía");
                    continue;
                }
                Integer filaIdPrev = idVisto.get(idExcel);
                if (filaIdPrev != null) {
                    errores.add("Fila " + (i + 1) + ": ID " + idExcel + " duplicado (ya estaba en fila " + filaIdPrev + ")");
                    continue;
                }
                String claveNombre = nombre.toUpperCase();
                Integer filaNombrePrev = nombreVisto.get(claveNombre);
                if (filaNombrePrev != null) {
                    errores.add("Fila " + (i + 1) + ": proveedor '" + nombre + "' duplicado (ya estaba en fila " + filaNombrePrev + ")");
                    continue;
                }
                String apodo = leerTexto(row, columnas, "Apodo");
                String plazoPago = leerTexto(row, columnas, "Plazo Pago");
                Boolean entrega = leerBooleano(row, columnas, "Entrega");
                BigDecimal financiacion = leerDecimal(row, columnas, "Financiación %");
                if (financiacion == null) financiacion = leerDecimal(row, columnas, "Financiacion %");
                Integer leadTime = leerEntero(row, columnas, "Lead Time (días)");
                if (leadTime == null) leadTime = leerEntero(row, columnas, "Lead Time (dias)");

                String nombreTrunc = nombre.length() > 100 ? nombre.substring(0, 100) : nombre;
                String apodoFinal;
                if (apodo != null && !apodo.isBlank()) {
                    apodoFinal = apodo.length() > 50 ? apodo.substring(0, 50) : apodo;
                } else {
                    apodoFinal = "";
                }
                String plazoTrunc = plazoPago == null ? null
                        : (plazoPago.length() > 45 ? plazoPago.substring(0, 45) : plazoPago);

                entityManager.createNativeQuery(
                        "INSERT INTO supermaster.proveedores "
                                + "(id_proveedor, nombre, apodo, plazo_pago, entrega, financiacion_porcentaje, lead_time_dias) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)")
                        .setParameter(1, idExcel)
                        .setParameter(2, nombreTrunc)
                        .setParameter(3, apodoFinal)
                        .setParameter(4, plazoTrunc)
                        .setParameter(5, entrega)
                        .setParameter(6, financiacion)
                        .setParameter(7, leadTime)
                        .executeUpdate();
                idVisto.put(idExcel, i + 1);
                nombreVisto.put(claveNombre, i + 1);
                successRows++;
            } catch (Exception e) {
                errores.add("Fila " + (i + 1) + ": " + e.getMessage());
            }
        }
        entityManager.flush();

        log.info("Hoja '{}' (proveedores): {}/{} filas procesadas, {} errores", nombreHoja, successRows, totalRows, errores.size());
        return errores.isEmpty()
                ? ImportResultDTO.success(totalRows, successRows)
                : ImportResultDTO.withErrors(totalRows, successRows, errores.size(), errores);
    }

    private int compararPorCampo(Producto p1, Producto p2, String campo) {
        return switch (campo) {
            case "clasifgral" -> {
                String v1 = p1.getClasifGral() != null ? p1.getClasifGral().getNombre() : null;
                String v2 = p2.getClasifGral() != null ? p2.getClasifGral().getNombre() : null;
                yield compareNullsLast(v1, v2);
            }
            case "clasifgastro" -> {
                String v1 = p1.getClasifGastro() != null ? p1.getClasifGastro().getNombre() : null;
                String v2 = p2.getClasifGastro() != null ? p2.getClasifGastro().getNombre() : null;
                yield compareNullsLast(v1, v2);
            }
            case "tipo" -> {
                String v1 = p1.getTipo() != null ? p1.getTipo().getNombre() : null;
                String v2 = p2.getTipo() != null ? p2.getTipo().getNombre() : null;
                yield compareNullsLast(v1, v2);
            }
            case "marca" -> {
                String v1 = p1.getMarca() != null ? p1.getMarca().getNombre() : null;
                String v2 = p2.getMarca() != null ? p2.getMarca().getNombre() : null;
                yield compareNullsLast(v1, v2);
            }
            case "tag" -> {
                String t1 = p1.getTag() != null ? p1.getTag().name() : null;
                String t2 = p2.getTag() != null ? p2.getTag().name() : null;
                yield compareNullsLast(t1, t2);
            }
            default -> 0;
        };
    }
}


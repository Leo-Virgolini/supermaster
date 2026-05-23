package ar.com.leo.super_master_backend.excel.controller;

import ar.com.leo.super_master_backend.dominio.common.response.ErrorResponse;
import ar.com.leo.super_master_backend.excel.dto.ExportCatalogoResultDTO;
import ar.com.leo.super_master_backend.excel.dto.ExportResultDTO;
import ar.com.leo.super_master_backend.excel.dto.ImportCompletoResultDTO;
import ar.com.leo.super_master_backend.excel.dto.ImportCostosResultDTO;
import ar.com.leo.super_master_backend.excel.dto.LimpiezaDatosResultDTO;
import ar.com.leo.super_master_backend.excel.service.ExcelService;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoFilter;
import ar.com.leo.super_master_backend.dominio.reposicion.entity.TagReposicion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import ar.com.leo.super_master_backend.config.Permisos;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/excel")
public class ExcelController {

    private final ExcelService excelService;

    /**
     * Importación única de migración: Importa TODO el Excel completo a la base de datos
     * Este endpoint está diseñado para ser usado UNA SOLA VEZ para migrar todos los datos
     * del archivo SUPER MASTER.xlsm a la base de datos MySQL
     *
     * @param file Archivo Excel completo (SUPER MASTER.xlsm)
     * @return Resultado de la importación completa con estadísticas por hoja
     */
    @PostMapping("/importar-migracion")
    @PreAuthorize(Permisos.EXCEL_EDITAR)
    public ResponseEntity<?> importarMigracion(
            @RequestParam("archivo") MultipartFile file
    ) {
        try {
            ImportCompletoResultDTO resultado = excelService.importarMigracionCompleta(file);
            return ResponseEntity.ok(resultado);
        } catch (IllegalArgumentException e) {
            log.error("Error de validación al importar migración: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(
                    ImportCompletoResultDTO.withErrors(0, 0, 1,
                            new HashMap<>(),
                            List.of(e.getMessage()))
            );
        } catch (IOException e) {
            log.error("Error de I/O al importar migración: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("Error al procesar el archivo Excel: " + e.getMessage(), "/api/excel/importar-migracion"));
        } catch (Exception e) {
            log.error("Error inesperado al importar migración: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("Error interno del servidor: " + e.getMessage(), "/api/excel/importar-migracion"));
        }
    }

    /**
     * Limpia datos de la BD (operación destructiva). Vacía las siguientes tablas y
     * resetea sus AUTO_INCREMENT:
     * venta_diaria_cache, orden_compra_lineas, ordenes_compra, producto_apto,
     * producto_catalogo, producto_cliente, producto_canal_precio_inflado,
     * producto_canal_precios, producto_margen, mlas, productos, clientes,
     * marcas, tipos, materiales, origenes, clasif_gral, clasif_gastro, proveedores.
     * Usar SOLO durante setup inicial.
     */
    @PostMapping("/limpiar-datos")
    @PreAuthorize(Permisos.EXCEL_EDITAR)
    public ResponseEntity<?> limpiarDatos() {
        try {
            LimpiezaDatosResultDTO resultado = excelService.limpiarDatos();
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            log.error("Error al limpiar datos: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("Error interno del servidor: " + e.getMessage(), "/api/excel/limpiar-datos"));
        }
    }

    /**
     * Importa las tablas auxiliares (marcas, tipos, materiales, origenes, clasif_gral,
     * clasif_gastro, aptos, proveedores) desde el Excel "Plantilla_Tablas_SuperMaster.xlsx".
     * <p>
     * Cada hoja del Excel corresponde a una tabla y debe respetar la estructura:
     * fila 1 título, fila 2 tipos de datos, fila 3 encabezados, fila 4+ datos.
     * <p>
     * Las hojas con jerarquía (Marcas, Tipos, Clasif. Grales, Clasif. Gastro) usan la
     * columna "ID Padre" que referencia el ID del Excel dentro de la misma hoja.
     *
     * @param file Archivo Excel con las tablas auxiliares
     * @return Resultado de la importación con estadísticas por hoja
     */
    @PostMapping("/importar-tablas-auxiliares")
    @PreAuthorize(Permisos.EXCEL_EDITAR)
    public ResponseEntity<?> importarTablasAuxiliares(
            @RequestParam("archivo") MultipartFile file
    ) {
        try {
            ImportCompletoResultDTO resultado = excelService.importarTablasAuxiliares(file);
            return ResponseEntity.ok(resultado);
        } catch (IllegalArgumentException e) {
            log.error("Error de validación al importar tablas auxiliares: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(
                    ImportCompletoResultDTO.withErrors(0, 0, 1,
                            new HashMap<>(),
                            List.of(e.getMessage()))
            );
        } catch (IOException e) {
            log.error("Error de I/O al importar tablas auxiliares: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("Error al procesar el archivo Excel: " + e.getMessage(), "/api/excel/importar-tablas-auxiliares"));
        } catch (Exception e) {
            log.error("Error inesperado al importar tablas auxiliares: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("Error interno del servidor: " + e.getMessage(), "/api/excel/importar-tablas-auxiliares"));
        }
    }

    /**
     * Importa costos desde un archivo Excel (.xls/.xlsx) actualizando productos existentes.
     * <p>
     * Columnas esperadas:
     * - CODIGO: sku del producto (requerido)
     * - PRODUCTO: descripcion
     * - COSTO: costo del producto
     * - CODIGO EXTERNO: cod_ext
     * - PROVEEDOR: nombre del proveedor (se crea si no existe)
     * - TIPO DE PRODUCTO: "COMBO" o "SIMPLE"
     * - ULTIMA ACT. COSTO: fecha (formato dd/MM/yyyy)
     * - UNIDADES POR BULTO: uxb
     * - PORCENTAJE IVA: iva
     *
     * @param file Archivo Excel con los costos
     * @return Resultado de la importación con estadísticas
     */
    @PostMapping("/importar-costos")
    @PreAuthorize(Permisos.EXCEL_EDITAR)
    public ResponseEntity<?> importarCostos(
            @RequestParam("archivo") MultipartFile file
    ) {
        try {
            ImportCostosResultDTO resultado = excelService.importarCostos(file);
            return ResponseEntity.ok(resultado);
        } catch (IllegalArgumentException e) {
            log.error("Error de validación al importar costos: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(
                    ImportCostosResultDTO.withErrors(List.of(e.getMessage()))
            );
        } catch (IOException e) {
            log.error("Error de I/O al importar costos: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("Error al procesar el archivo Excel: " + e.getMessage(), "/api/excel/importar-costos"));
        } catch (Exception e) {
            log.error("Error inesperado al importar costos: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("Error interno del servidor: " + e.getMessage(), "/api/excel/importar-costos"));
        }
    }

    /**
     * Exporta productos con precios a un archivo Excel (.xlsx).
     *
     * Formatos disponibles:
     * - completo (default): Incluye columnas fijas del producto y columnas dinámicas por canal/cuotas.
     *                       Acepta los mismos filtros que GET /api/precios.
     * - mercadolibre: Columnas SKU, PRECIO (pvp_inflado), MLA. Usa canal "ML" internamente.
     * - kt-hogar: Columnas SKU, PVP_KT_HOGAR (pvp), PVP_INFLADO (pvp_inflado). Usa canal "KT HOGAR" internamente.
     * - kt-gastro: Columnas SKU, PVP_GASTRO_S_IVA (pvp sin IVA del producto). Usa canal "KT GASTRO" internamente.
     *
     * @param formato Formato de exportación: completo (default), mercadolibre, kt-hogar, kt-gastro
     * @return Archivo Excel descargable
     */
    @GetMapping("/exportar-precios")
    @PreAuthorize(Permisos.EXCEL_VER)
    public ResponseEntity<?> exportarPrecios(
            // FORMATO DE EXPORTACIÓN
            @RequestParam(required = false, defaultValue = "completo") String formato,

            // 0) ID
            @RequestParam(required = false) Integer productoId,

            // 1) TEXTO
            @RequestParam(required = false) String search,

            // 1.1) FILTROS DE TEXTO DEDICADOS
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String codExt,
            @RequestParam(required = false) String descripcion,
            @RequestParam(required = false) String tituloWeb,

            // 2) BOOLEANOS / NUMÉRICOS
            @RequestParam(required = false) Boolean esCombo,
            @RequestParam(required = false) Integer uxb,
            @RequestParam(required = false) Boolean esMaquina,
            @RequestParam(required = false) Boolean tieneMla,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) TagReposicion tagReposicion,
            @RequestParam(required = false) List<ar.com.leo.super_master_backend.dominio.producto.entity.Tag> tags,

            // 2.1) FILTROS MLA
            @RequestParam(required = false) String mla,
            @RequestParam(required = false) String mlau,
            @RequestParam(required = false) BigDecimal precioEnvioMin,
            @RequestParam(required = false) BigDecimal precioEnvioMax,
            @RequestParam(required = false) BigDecimal comisionPorcentajeMin,
            @RequestParam(required = false) BigDecimal comisionPorcentajeMax,
            @RequestParam(required = false) Boolean tieneComision,
            @RequestParam(required = false) Boolean tienePrecioEnvio,

            // 3) MANY-TO-ONE (multi-valor)
            @RequestParam(required = false) List<Integer> marcaIds,
            @RequestParam(required = false) List<Integer> origenIds,
            @RequestParam(required = false) List<Integer> tipoIds,
            @RequestParam(required = false) List<Integer> clasifGralIds,
            @RequestParam(required = false) List<Integer> clasifGastroIds,
            @RequestParam(required = false) List<Integer> proveedorIds,
            @RequestParam(required = false) List<Integer> materialIds,

            // 4) RANGOS (costo / IVA / stock)
            @RequestParam(required = false) BigDecimal costoMin,
            @RequestParam(required = false) BigDecimal costoMax,
            @RequestParam(required = false) BigDecimal ivaMin,
            @RequestParam(required = false) BigDecimal ivaMax,
            @RequestParam(required = false) Integer stockMin,
            @RequestParam(required = false) Integer stockMax,

            // 5) RANGO PVP
            @RequestParam(required = false) BigDecimal pvpMin,
            @RequestParam(required = false) BigDecimal pvpMax,
            @RequestParam(required = false) Integer pvpCanalId,

            // 6) FECHAS
            @RequestParam(required = false) LocalDate desdeFechaUltimoCosto,
            @RequestParam(required = false) LocalDate hastaFechaUltimoCosto,
            @RequestParam(required = false) LocalDate desdeFechaCreacion,
            @RequestParam(required = false) LocalDate hastaFechaCreacion,
            @RequestParam(required = false) LocalDate desdeFechaModificacion,
            @RequestParam(required = false) LocalDate hastaFechaModificacion,

            // 7) MANY-TO-MANY
            @RequestParam(required = false) List<Integer> aptoIds,
            @RequestParam(required = false) List<Integer> canalIds,
            @RequestParam(required = false) List<Integer> catalogoIds,
            @RequestParam(required = false) List<Integer> clienteIds,
            @RequestParam(required = false) List<Integer> mlaIds,

            // 8) SORT (Spring lo resuelve automáticamente desde ?sort=campo,asc)
            Sort sort,

            // 9) FILTRAR PRECIOS POR CANAL (también usado para ordenamiento)
            @RequestParam(required = false) Integer canalId,

            // 10) FILTRAR PRECIOS POR CUOTAS (también usado para ordenamiento)
            @RequestParam(required = false) Integer cuotas
    ) {
        try {
            // Validar formato
            if (!formato.equals("completo") && !formato.equals("mercadolibre") && !formato.equals("kt-hogar") && !formato.equals("kt-gastro")) {
                return ResponseEntity.badRequest()
                        .body(ErrorResponse.of("Formato inválido. Valores permitidos: completo, mercadolibre, kt-hogar, kt-gastro",
                                "/api/excel/exportar-precios"));
            }

            // Formatos específicos requieren el parámetro cuotas
            if (formato.equals("mercadolibre") || formato.equals("kt-hogar") || formato.equals("kt-gastro")) {
                if (cuotas == null) {
                    return ResponseEntity.badRequest()
                            .body(ErrorResponse.of("El parámetro 'cuotas' es requerido para el formato '" + formato + "'. Valores: -1 (transferencia), 0 (contado), o número de cuotas (3, 6, 12, etc.)",
                                    "/api/excel/exportar-precios?formato=" + formato));
                }
                if (formato.equals("mercadolibre")) {
                    return exportarFormatoMercadoLibre(cuotas);
                }
                if (formato.equals("kt-hogar")) {
                    return exportarFormatoKtHogar(cuotas);
                }
                return exportarFormatoKtGastro(cuotas);
            }

            // Formato completo: usar filtros
            ProductoFilter filter = new ProductoFilter(
                    productoId,
                    search,
                    // Filtros de texto dedicados
                    sku,
                    codExt,
                    descripcion,
                    tituloWeb,
                    // Booleanos/Numéricos
                    esCombo,
                    uxb,
                    esMaquina,
                    tieneMla,
                    activo,
                    tagReposicion,
                    tags,
                    // Filtros MLA
                    mla,
                    mlau,
                    precioEnvioMin,
                    precioEnvioMax,
                    comisionPorcentajeMin,
                    comisionPorcentajeMax,
                    tieneComision,
                    tienePrecioEnvio,
                    // Many-to-One (multi-valor)
                    marcaIds,
                    origenIds,
                    tipoIds,
                    clasifGralIds,
                    clasifGastroIds,
                    proveedorIds,
                    materialIds,
                    costoMin,
                    costoMax,
                    ivaMin,
                    ivaMax,
                    stockMin,
                    stockMax,
                    pvpMin,
                    pvpMax,
                    pvpCanalId,
                    desdeFechaUltimoCosto,
                    hastaFechaUltimoCosto,
                    desdeFechaCreacion,
                    hastaFechaCreacion,
                    desdeFechaModificacion,
                    hastaFechaModificacion,
                    aptoIds,
                    canalIds,
                    catalogoIds,
                    clienteIds,
                    mlaIds,
                    canalId,
                    cuotas
            );

            byte[] excelBytes = excelService.exportarPrecios(filter, sort);

            // Construir nombre de archivo con parámetros de filtro
            String sufijo = excelService.construirSufijoArchivoPrecios(filter);
            String filename = String.format("PRECIOS%s_%s.xlsx",
                    sufijo,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(excelBytes.length);

            return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            log.error("Error de validación al exportar precios: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of(e.getMessage(), "/api/excel/exportar-precios"));
        } catch (IOException e) {
            log.error("Error de I/O al exportar precios: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("Error al generar el archivo Excel: " + e.getMessage(), "/api/excel/exportar-precios"));
        } catch (Exception e) {
            log.error("Error inesperado al exportar precios: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("Error interno del servidor: " + e.getMessage(), "/api/excel/exportar-precios"));
        }
    }

    /**
     * Exporta productos de un catálogo a un archivo Excel (.xlsx).
     * Columnas: SKU, PRODUCTO, PVP (nombre del canal), UxB
     *
     * @param catalogoId ID del catálogo (requerido)
     * @param canalId    ID del canal para obtener el PVP (requerido)
     * @param cuotas     Cantidad de cuotas (requerido): -1=transferencia, 0=contado, >0=cuotas
     * @param clasifGralId ID de clasificación general (opcional)
     * @param clasifGastroId ID de clasificación gastro (opcional)
     * @param tipoId ID del tipo (opcional)
     * @param marcaId ID de la marca (opcional)
     * @param tag Filtro por tag del producto (opcional): MAQUINA, REPUESTO, MENAJE
     * @param ordenarPor Campos de ordenamiento separados por coma (opcional). Valores: clasifGral, clasifGastro, tipo, marca, tag
     * @return Archivo Excel descargable
     */
    @GetMapping("/exportar-catalogo")
    @PreAuthorize(Permisos.EXCEL_VER)
    public ResponseEntity<?> exportarCatalogo(
            @RequestParam("catalogoId") Integer catalogoId,
            @RequestParam("canalId") Integer canalId,
            @RequestParam("cuotas") Integer cuotas,  // -1=transferencia, 0=contado, >0=cuotas
            @RequestParam(value = "clasifGralId", required = false) Integer clasifGralId,
            @RequestParam(value = "clasifGastroId", required = false) Integer clasifGastroId,
            @RequestParam(value = "tipoId", required = false) Integer tipoId,
            @RequestParam(value = "marcaId", required = false) Integer marcaId,
            @RequestParam(value = "tag", required = false) ar.com.leo.super_master_backend.dominio.producto.entity.Tag tag,
            @RequestParam(value = "ordenarPor", required = false) String ordenarPor
    ) {
        try {
            ExportCatalogoResultDTO result = excelService.exportarCatalogo(catalogoId, canalId, cuotas, clasifGralId, clasifGastroId, tipoId, marcaId, tag, ordenarPor);

            String filename = String.format("CATALOGO_%s_%s.xlsx",
                    result.nombreArchivo(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(result.archivo().length);

            return new ResponseEntity<>(result.archivo(), headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            log.error("Error de validación al exportar catálogo: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of(e.getMessage(), "/api/excel/exportar-catalogo"));
        } catch (IOException e) {
            log.error("Error de I/O al exportar catálogo: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("Error al generar el archivo Excel: " + e.getMessage(), "/api/excel/exportar-catalogo"));
        } catch (Exception e) {
            log.error("Error inesperado al exportar catálogo: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("Error interno del servidor: " + e.getMessage(), "/api/excel/exportar-catalogo"));
        }
    }

    /**
     * Método privado para exportar formato Mercado Libre.
     * Columnas: SKU, PRECIO (pvp_inflado), MLA
     */
    private ResponseEntity<?> exportarFormatoMercadoLibre(Integer cuotas) {
        try {
            ExportResultDTO result = excelService.exportarMercadoLibre(cuotas);

            String filename = String.format("PRECIOS_%s_%s.xlsx",
                    result.nombreArchivo(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(result.archivo().length);

            agregarHeadersAdvertencias(headers, result.advertencias());

            return new ResponseEntity<>(result.archivo(), headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            log.error("Error de validación al exportar para Mercado Libre: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of(e.getMessage(), "/api/excel/exportar-precios?formato=mercadolibre"));
        } catch (IOException e) {
            log.error("Error de I/O al exportar para Mercado Libre: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("Error al generar el archivo Excel: " + e.getMessage(), "/api/excel/exportar-precios?formato=mercadolibre"));
        } catch (Exception e) {
            log.error("Error inesperado al exportar para Mercado Libre: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("Error interno del servidor: " + e.getMessage(), "/api/excel/exportar-precios?formato=mercadolibre"));
        }
    }

    /**
     * Método privado para exportar formato KT HOGAR.
     * Columnas: SKU, PVP_KT_HOGAR (pvp), PVP_INFLADO (pvp_inflado)
     */
    private ResponseEntity<?> exportarFormatoKtHogar(Integer cuotas) {
        try {
            ExportResultDTO result = excelService.exportarKtHogar(cuotas);

            String filename = String.format("PRECIOS_%s_%s.xlsx",
                    result.nombreArchivo(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(result.archivo().length);

            agregarHeadersAdvertencias(headers, result.advertencias());

            return new ResponseEntity<>(result.archivo(), headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            log.error("Error de validación al exportar para KT HOGAR: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of(e.getMessage(), "/api/excel/exportar-precios?formato=kt-hogar"));
        } catch (IOException e) {
            log.error("Error de I/O al exportar para KT HOGAR: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("Error al generar el archivo Excel: " + e.getMessage(), "/api/excel/exportar-precios?formato=kt-hogar"));
        } catch (Exception e) {
            log.error("Error inesperado al exportar para KT HOGAR: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("Error interno del servidor: " + e.getMessage(), "/api/excel/exportar-precios?formato=kt-hogar"));
        }
    }

    /**
     * Método privado para exportar formato KT GASTRO.
     * Columnas: SKU, PVP_GASTRO_S_IVA (pvp sin IVA del producto)
     */
    private ResponseEntity<?> exportarFormatoKtGastro(Integer cuotas) {
        try {
            ExportResultDTO result = excelService.exportarKtGastro(cuotas);

            String filename = String.format("PRECIOS_%s_%s.xlsx",
                    result.nombreArchivo(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(result.archivo().length);

            agregarHeadersAdvertencias(headers, result.advertencias());

            return new ResponseEntity<>(result.archivo(), headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            log.error("Error de validación al exportar para KT GASTRO: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of(e.getMessage(), "/api/excel/exportar-precios?formato=kt-gastro"));
        } catch (IOException e) {
            log.error("Error de I/O al exportar para KT GASTRO: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("Error al generar el archivo Excel: " + e.getMessage(), "/api/excel/exportar-precios?formato=kt-gastro"));
        } catch (Exception e) {
            log.error("Error inesperado al exportar para KT GASTRO: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("Error interno del servidor: " + e.getMessage(), "/api/excel/exportar-precios?formato=kt-gastro"));
        }
    }

    /**
     * Agrega headers de advertencias a la respuesta de exportación.
     * Envía cantidad + detalle (máx 50 items, URL-encoded JSON).
     */
    private void agregarHeadersAdvertencias(HttpHeaders headers, List<String> advertencias) {
        if (advertencias == null || advertencias.isEmpty()) {
            return;
        }

        int total = advertencias.size();
        headers.add("X-Advertencias-Count", String.valueOf(total));

        // Limitar a 50 para evitar HeadersTooLargeException
        int MAX_ADVERTENCIAS = 50;
        List<String> limitadas = total > MAX_ADVERTENCIAS
                ? advertencias.subList(0, MAX_ADVERTENCIAS)
                : advertencias;

        // Construir JSON array manualmente
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < limitadas.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(limitadas.get(i).replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        headers.add("X-Advertencias", URLEncoder.encode(sb.toString(), StandardCharsets.UTF_8).replace("+", "%20"));

        headers.add("Access-Control-Expose-Headers", "X-Advertencias-Count, X-Advertencias");
        log.warn("Exportación con {} advertencias", total);
    }
}


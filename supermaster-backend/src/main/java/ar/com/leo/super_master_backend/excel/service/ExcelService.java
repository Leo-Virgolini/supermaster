package ar.com.leo.super_master_backend.excel.service;

import ar.com.leo.super_master_backend.excel.dto.ExportCatalogoResultDTO;
import ar.com.leo.super_master_backend.excel.dto.ExportResultDTO;
import ar.com.leo.super_master_backend.excel.dto.ImportCompletoResultDTO;
import ar.com.leo.super_master_backend.excel.dto.ImportCostosResultDTO;
import ar.com.leo.super_master_backend.excel.dto.ImportResultDTO;
import ar.com.leo.super_master_backend.excel.dto.LimpiezaDatosResultDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoFilter;
import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ExcelService {

    /**
     * Importación única de migración: Importa TODO el Excel completo a la base de datos
     * Este método está diseñado para ser usado UNA SOLA VEZ para migrar todos los datos
     * @param file Archivo Excel completo (SUPER MASTER.xlsm)
     * @return Resultado de la importación completa con estadísticas por hoja
     * @throws IOException Si hay error leyendo el archivo
     */
    ImportCompletoResultDTO importarMigracionCompleta(MultipartFile file) throws IOException;

    /**
     * Importa las tablas auxiliares (marcas, tipos, materiales, origenes, clasif_gral,
     * clasif_gastro, aptos, proveedores) desde el Excel "Plantilla_Tablas_SuperMaster.xlsx".
     * <p>
     * Cada hoja del Excel corresponde a una tabla. Estructura esperada de cada hoja:
     * - Fila 1: título (ej: "MARCAS  —  Tabla: marcas")
     * - Fila 2: tipos de datos
     * - Fila 3: nombres de columnas
     * - Fila 4+: datos
     * <p>
     * Para hojas con jerarquía (Marcas, Tipos, Clasif. Grales, Clasif. Gastro) se procesa
     * en dos pasadas: primero se crean todas las entidades sin padre y se mantiene un mapa
     * id_excel → entidad creada; luego se resuelven los padres usando ese mapa.
     *
     * @param file Archivo Excel con las tablas auxiliares
     * @return Resultado de la importación con estadísticas por hoja
     * @throws IOException Si hay error leyendo el archivo
     */
    ImportCompletoResultDTO importarTablasAuxiliares(MultipartFile file) throws IOException;

    /**
     * Enriquece los productos existentes con datos del archivo NUEVO SUPER MASTER.xlsx.
     * <p>
     * Lee la hoja "MASTER" desde la fila 3 y, por cada SKU presente en la BD, actualiza:
     * <ul>
     *   <li>FKs: origen, marca (LINEA &gt; MARCA), clasif gral (2 &gt; 1), clasif gastro (2 &gt; 1),
     *       tipo (3 &gt; 2 &gt; 1), material, proveedor. ID = 0 se interpreta como sin dato.</li>
     *   <li>Dimensiones (strings, max 45): capacidad, largo, ancho, alto, diamboca, diambase, espesor.</li>
     * </ul>
     * SKUs no encontrados en BD se omiten (no crea productos nuevos).
     *
     * @param file Archivo Excel NUEVO SUPER MASTER.xlsx
     * @return Resultado con totales/exitosas/erradas y lista de errores por fila
     */
    ImportResultDTO enriquecerProductosDesdeNuevoMaster(MultipartFile file) throws IOException;

    /**
     * Vacía las tablas de datos (productos, mlas, márgenes, precios calculados,
     * tablas relacionales y maestros: marcas, tipos, materiales, orígenes, clasif,
     * clientes, proveedores, ventas y órdenes de compra) y resetea sus AUTO_INCREMENT.
     * Es una operación destructiva — usar SOLO durante setup inicial.
     *
     * @param tablasSeleccionadas Subconjunto a limpiar:
     *                            <ul>
     *                              <li>{@code null} → limpia TODAS las tablas elegibles.</li>
     *                              <li>Lista vacía → no se limpia ninguna (intent explícito).</li>
     *                              <li>Subconjunto → solo esas (validadas contra el whitelist).</li>
     *                            </ul>
     *                            Nombres no permitidos se reportan como error y no se ejecutan.
     * @return Detalle de las tablas limpiadas y errores si los hubo
     */
    LimpiezaDatosResultDTO limpiarDatos(List<String> tablasSeleccionadas);

    /**
     * Importa costos desde un archivo Excel (.xls/.xlsx) actualizando productos existentes.
     *
     * Columnas esperadas:
     * - CODIGO: sku del producto
     * - PRODUCTO: descripcion
     * - COSTO: costo del producto
     * - CODIGO EXTERNO: cod_ext
     * - PROVEEDOR: nombre del proveedor (se crea si no existe)
     * - TIPO DE PRODUCTO: "COMBO" → esCombo=true, otro → esCombo=false
     * - ULTIMA ACT. COSTO: fecha_ult_costo (formato dd/MM/yyyy)
     * - UNIDADES POR BULTO: uxb
     * - PORCENTAJE IVA: iva
     *
     * @param file Archivo Excel con los costos
     * @return Resultado de la importación con estadísticas
     * @throws IOException Si hay error leyendo el archivo
     */
    ImportCostosResultDTO importarCostos(MultipartFile file) throws IOException;

    /**
     * Exporta productos con precios a un archivo Excel (.xlsx).
     * Incluye columnas fijas del producto y columnas dinámicas por canal/cuotas.
     *
     * @param filter Filtros a aplicar (texto, marcaId, canalId, etc.)
     * @param sort Ordenamiento a aplicar (puede ser null)
     * @return Bytes del archivo Excel generado
     * @throws IOException Si hay error generando el archivo
     */
    byte[] exportarPrecios(ProductoFilter filter, Sort sort) throws IOException;

    /**
     * Construye un sufijo para el nombre del archivo basándose en los filtros aplicados.
     *
     * @param filter Filtros aplicados
     * @return Sufijo para agregar al nombre del archivo (ej: "_canal1_3cuotas")
     */
    String construirSufijoArchivoPrecios(ProductoFilter filter);

    /**
     * Exporta productos de un catálogo a un archivo Excel (.xlsx).
     * Columnas: SKU, PRODUCTO, PVP nombre_canal, UxB
     *
     * @param catalogoId ID del catálogo
     * @param canalId ID del canal para obtener el PVP
     * @param cuotas Cantidad de cuotas (0 = contado)
     * @param clasifGralId ID de clasificación general (opcional)
     * @param clasifGastroId ID de clasificación gastro (opcional)
     * @param tipoId ID del tipo (opcional)
     * @param marcaId ID de la marca (opcional)
     * @param tag Filtro por tag del producto (opcional): MAQUINA, REPUESTO, MENAJE
     * @param ordenarPor Campos de ordenamiento separados por coma. Valores: clasifGral, clasifGastro, tipo, marca, tag
     * @return ExportCatalogoResultDTO con archivo y nombre sugerido (canal-catalogo)
     * @throws IOException Si hay error generando el archivo
     */
    ExportCatalogoResultDTO exportarCatalogo(Integer catalogoId, Integer canalId, Integer cuotas,
                            Integer clasifGralId, Integer clasifGastroId, Integer tipoId, Integer marcaId,
                            Tag tag, String ordenarPor) throws IOException;

    /**
     * Exporta datos para subir a Mercado Libre.
     * Usa el canal "ML" internamente.
     * Columnas: SKU, PRECIO (pvp_inflado), MLA
     *
     * @param cuotas Cantidad de cuotas (null = sin cuotas)
     * @return ExportResultDTO con el archivo y advertencias
     * @throws IOException Si hay error generando el archivo
     */
    ExportResultDTO exportarMercadoLibre(Integer cuotas) throws IOException;

    /**
     * Exporta datos para KT HOGAR.
     * Usa el canal "KT HOGAR" internamente.
     * Columnas: SKU, PVP_KT_HOGAR (pvp), PVP_INFLADO (pvp_inflado)
     *
     * @param cuotas Cantidad de cuotas (null = sin cuotas)
     * @return ExportResultDTO con el archivo y advertencias
     * @throws IOException Si hay error generando el archivo
     */
    ExportResultDTO exportarKtHogar(Integer cuotas) throws IOException;

    /**
     * Exporta datos para KT GASTRO.
     * Usa el canal "KT GASTRO" internamente.
     * Columnas: SKU, PVP_GASTRO_S_IVA (pvp sin IVA del producto)
     *
     * @param cuotas Cantidad de cuotas (null = sin cuotas)
     * @return ExportResultDTO con el archivo y advertencias
     * @throws IOException Si hay error generando el archivo
     */
    ExportResultDTO exportarKtGastro(Integer cuotas) throws IOException;
}


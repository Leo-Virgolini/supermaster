"use client";

import { getErrorMessage } from "@/lib/errors";
import { useRef, useState, useEffect, useMemo } from "react";
import { toast } from "sonner";
import { notificar } from "../utils/notificar";
import Button from "../components/Button/Button";
import AsyncSelect from "../components/AsyncSelect/AsyncSelect";
import Modal from "../components/Modal/Modal";
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { searchCanales, searchClasifGral, searchClasifGastro, searchTipos, searchMarcas, searchCatalogos } from "../productos/productosService";
import {
    TableCellsIcon, ArrowUpTrayIcon, ArrowDownTrayIcon,
    ChevronDownIcon, ChevronUpIcon,
    PlusIcon, PencilSquareIcon, TrashIcon,
    ExclamationTriangleIcon,
} from "@heroicons/react/24/outline";
import { useAuth } from "../context/AuthContext";

// ---------------------------------------------------------------------------
// Descarga con headers
// ---------------------------------------------------------------------------

interface DescargaResult {
    advertencias: number;
    skus: string[];
}

async function descargarArchivo(url: string, fallbackFilename: string, init?: RequestInit): Promise<Response> {
    const response = await fetchAPI(url, init);
    const blob = await response.blob();
    const blobUrl = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = blobUrl;
    const disposition = response.headers.get("Content-Disposition");
    const match = disposition?.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
    a.download = match?.[1]?.replace(/['"]/g, "") || fallbackFilename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(blobUrl);
    return response;
}

async function descargarExcel(url: string): Promise<DescargaResult> {
    const response = await descargarArchivo(url, "export.xlsx");
    const advertencias = Number(response.headers.get("X-Advertencias-Count") ?? "0");
    let skus: string[] = [];
    const advertenciasRaw = response.headers.get("X-Advertencias") ?? "";
    if (advertenciasRaw) {
        try { skus = JSON.parse(decodeURIComponent(advertenciasRaw)); } catch { /* ignorar */ }
    }
    return { advertencias, skus };
}

async function descargarPdf(url: string, init?: RequestInit): Promise<number> {
    const response = await descargarArchivo(url, "catalogo.pdf", init);
    return Number(response.headers.get("X-Productos-Count") ?? "0");
}

interface CatalogoPdfConfigItem {
    id: number;
    nombre: string;
    canal: string;
    catalogo: string;
    cuotas: number;
    clasificacion?: string | null;
    caratula: boolean;
    titulo?: string | null;
    estetica?: string | null;
    tipoDocumento?: string | null;
    productosPorPagina: number;
    ubicacionSalida?: string | null;
    activo: boolean;
}

type CatalogosPdfAuditOrigin = "FORM" | "TABLE" | "API";

interface PageResponse<T> {
    content: T[];
    totalElements: number;
}

interface CatalogoPdfConfigFormState {
    nombre: string;
    canal: string;
    catalogo: string;
    cuotas: string;
    clasificacion: string;
    caratula: boolean;
    titulo: string;
    estetica: string;
    tipoDocumento: string;
    productosPorPagina: string;
    ubicacionSalida: string;
    activo: boolean;
}

const emptyConfigForm = (): CatalogoPdfConfigFormState => ({
    nombre: "",
    canal: "",
    catalogo: "",
    cuotas: "0",
    clasificacion: "",
    caratula: true,
    titulo: "",
    estetica: "",
    tipoDocumento: "CATALOGO",
    productosPorPagina: "12",
    ubicacionSalida: "",
    activo: true,
});

const configToForm = (config: CatalogoPdfConfigItem): CatalogoPdfConfigFormState => ({
    nombre: config.nombre ?? "",
    canal: config.canal ?? "",
    catalogo: config.catalogo ?? "",
    cuotas: String(config.cuotas ?? 0),
    clasificacion: config.clasificacion ?? "",
    caratula: Boolean(config.caratula),
    titulo: config.titulo ?? "",
    estetica: config.estetica ?? "",
    tipoDocumento: config.tipoDocumento ?? "CATALOGO",
    productosPorPagina: String(config.productosPorPagina ?? 12),
    ubicacionSalida: config.ubicacionSalida ?? "",
    activo: Boolean(config.activo),
});

function withAuditOrigin(origin: CatalogosPdfAuditOrigin, extraHeaders: HeadersInit = {}) {
    return {
        "X-Audit-Origin": origin,
        ...extraHeaders,
    };
}

// ---------------------------------------------------------------------------
// Resultado de importación
// ---------------------------------------------------------------------------

interface ImportResult {
    productosActualizados?: number;
    productosNoEncontrados?: number;
    proveedoresCreados?: number;
    skusNoEncontrados?: string[];
    errores?: string[];
    [key: string]: any;
}

function ResultadoImport({ data }: { data: ImportResult }) {
    const stats = [
        { key: "productosActualizados", label: "Actualizados", color: "text-green-600" },
        { key: "productosNoEncontrados", label: "No encontrados", color: "text-orange-600" },
        { key: "proveedoresCreados", label: "Proveedores creados", color: "text-blue-600" },
    ] as const;

    return (
        <div className="mt-3 space-y-2">
            <div className="grid grid-cols-3 gap-2">
                {stats.map(({ key, label, color }) => (
                    data[key] !== undefined && (
                        <div key={key} className="bg-gray-50 dark:bg-slate-700/50 rounded p-2 text-center border border-gray-100 dark:border-slate-600">
                            <div className={`text-lg font-bold ${color}`}>{data[key]}</div>
                            <div className="text-xs text-gray-500 dark:text-slate-400">{label}</div>
                        </div>
                    )
                ))}
            </div>
            {data.skusNoEncontrados && data.skusNoEncontrados.length > 0 && (
                <div className="text-xs bg-orange-50 border border-orange-200 rounded p-2">
                    <div className="font-semibold text-orange-700 mb-1">SKUs no encontrados:</div>
                    <div className="text-orange-600">{data.skusNoEncontrados.join(", ")}</div>
                </div>
            )}
            {data.errores && data.errores.length > 0 && (
                <div className="text-xs bg-red-50 border border-red-200 rounded p-2">
                    <div className="font-semibold text-red-700 mb-1">Errores ({data.errores.length}):</div>
                    <ul className="list-disc list-inside text-red-600 space-y-0.5">
                        {data.errores.slice(0, 10).map((e: string, i: number) => <li key={i}>{e}</li>)}
                        {data.errores.length > 10 && <li className="text-red-500">...y {data.errores.length - 10} más</li>}
                    </ul>
                </div>
            )}
        </div>
    );
}

// ---------------------------------------------------------------------------
// Resultado de enriquecimiento (NUEVO SUPER MASTER)
// ---------------------------------------------------------------------------

function ResultadoEnriquecimiento({ data }: { data: EnriquecimientoResult }) {
    // Si errorRows === 0 pero hay entries en errors[], son advertencias (IDs inválidos
    // que se manejaron poniendo NULL), no errores. Renderizamos con estilo amarillo
    // en lugar de rojo y cambiamos el label.
    const errorRows = data.errorRows ?? 0;
    const errors = data.errors ?? [];
    const hayAdvertencias = errorRows === 0 && errors.length > 0;
    const cajaClass = hayAdvertencias
        ? "text-xs bg-amber-50 border border-amber-200 rounded p-2"
        : "text-xs bg-red-50 border border-red-200 rounded p-2";
    const tituloClass = hayAdvertencias ? "font-semibold text-amber-700 mb-1" : "font-semibold text-red-700 mb-1";
    const itemClass = hayAdvertencias
        ? "list-disc list-inside text-amber-700 space-y-0.5"
        : "list-disc list-inside text-red-600 space-y-0.5";
    const masClass = hayAdvertencias ? "text-amber-600" : "text-red-500";
    const tituloTexto = hayAdvertencias ? `Advertencias (${errors.length})` : `Errores (${errors.length})`;

    return (
        <div className="mt-3 space-y-2">
            <div className="grid grid-cols-3 gap-2">
                <div className="bg-gray-50 dark:bg-slate-700/50 rounded p-2 text-center border border-gray-100 dark:border-slate-600">
                    <div className="text-lg font-bold text-gray-700 dark:text-slate-200">{data.totalRows ?? 0}</div>
                    <div className="text-xs text-gray-500 dark:text-slate-400">Filas leídas</div>
                </div>
                <div className="bg-gray-50 dark:bg-slate-700/50 rounded p-2 text-center border border-gray-100 dark:border-slate-600">
                    <div className="text-lg font-bold text-green-600">{data.successRows ?? 0}</div>
                    <div className="text-xs text-gray-500 dark:text-slate-400">Actualizados</div>
                </div>
                <div className="bg-gray-50 dark:bg-slate-700/50 rounded p-2 text-center border border-gray-100 dark:border-slate-600">
                    <div className="text-lg font-bold text-red-600">{errorRows}</div>
                    <div className="text-xs text-gray-500 dark:text-slate-400">Con errores</div>
                </div>
            </div>
            {errors.length > 0 && (
                <div className={cajaClass}>
                    <div className={tituloClass}>{tituloTexto}:</div>
                    <ul className={itemClass}>
                        {errors.slice(0, 10).map((e, i) => <li key={i}>{e}</li>)}
                        {errors.length > 10 && <li className={masClass}>...y {errors.length - 10} más</li>}
                    </ul>
                </div>
            )}
        </div>
    );
}

// ---------------------------------------------------------------------------
// Resultado de migración
// ---------------------------------------------------------------------------

interface MigracionHoja {
    totalRows: number;
    successRows: number;
    errorRows: number;
    errors?: string[];
    message?: string;
}

interface MigracionResult {
    totalHojas?: number;
    hojasProcesadas?: number;
    hojasConErrores?: number;
    resultadosPorHoja?: Record<string, MigracionHoja>;
    erroresGenerales?: string[];
    valoresNoEncontrados?: Record<string, string[]>;
    message?: string;
}

function ResultadoMigracion({ data }: { data: MigracionResult }) {
    const [hojaExpandida, setHojaExpandida] = useState<string | null>(null);
    const hojas = Object.entries(data.resultadosPorHoja ?? {});

    return (
        <div className="mt-3 space-y-2">
            <div className="grid grid-cols-3 gap-2">
                <div className="bg-gray-50 dark:bg-slate-700/50 rounded p-2 text-center border border-gray-100 dark:border-slate-600">
                    <div className="text-lg font-bold text-gray-700 dark:text-slate-200">{data.totalHojas ?? 0}</div>
                    <div className="text-xs text-gray-500 dark:text-slate-400">Total hojas</div>
                </div>
                <div className="bg-gray-50 dark:bg-slate-700/50 rounded p-2 text-center border border-gray-100 dark:border-slate-600">
                    <div className="text-lg font-bold text-green-600">{data.hojasProcesadas ?? 0}</div>
                    <div className="text-xs text-gray-500 dark:text-slate-400">Procesadas</div>
                </div>
                <div className="bg-gray-50 dark:bg-slate-700/50 rounded p-2 text-center border border-gray-100 dark:border-slate-600">
                    <div className={`text-lg font-bold ${(data.hojasConErrores ?? 0) > 0 ? "text-red-600" : "text-gray-400 dark:text-slate-500"}`}>{data.hojasConErrores ?? 0}</div>
                    <div className="text-xs text-gray-500 dark:text-slate-400">Con errores</div>
                </div>
            </div>

            {hojas.length > 0 && (
                <div className="space-y-1">
                    {hojas.map(([nombre, hoja]) => (
                        <div key={nombre} className="border border-gray-200 dark:border-slate-600 rounded overflow-hidden">
                            <button
                                className="w-full flex items-center justify-between px-3 py-2 bg-gray-50 dark:bg-slate-700/50 text-xs font-medium text-gray-700 dark:text-slate-200 hover:bg-gray-100 dark:hover:bg-slate-700 transition text-left"
                                onClick={() => setHojaExpandida(hojaExpandida === nombre ? null : nombre)}
                            >
                                <span className="flex items-center gap-2">
                                    {hoja.errorRows > 0 ? "❌" : "✅"} {nombre}
                                    <span className="text-gray-400 dark:text-slate-500 font-normal">({hoja.successRows}/{hoja.totalRows} ok)</span>
                                </span>
                                <span className="text-gray-400 dark:text-slate-500 text-[10px]">{hojaExpandida === nombre ? "▲" : "▼"}</span>
                            </button>
                            {hojaExpandida === nombre && (
                                <div className="px-3 py-2 bg-white dark:bg-slate-800 text-xs space-y-1 border-t border-gray-100 dark:border-slate-600">
                                    {hoja.message && <p className="text-gray-600">{hoja.message}</p>}
                                    {hoja.errors && hoja.errors.length > 0 && (
                                        <ul className="list-disc list-inside text-red-600 space-y-0.5 mt-1 max-h-96 overflow-y-auto">
                                            {hoja.errors.map((e, i) => <li key={i}>{e}</li>)}
                                        </ul>
                                    )}
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            )}

            {data.erroresGenerales && data.erroresGenerales.length > 0 && (
                <div className="text-xs bg-red-50 border border-red-200 rounded p-2">
                    <div className="font-semibold text-red-700 mb-1">Errores generales:</div>
                    <ul className="list-disc list-inside text-red-600 space-y-0.5">
                        {data.erroresGenerales.map((e, i) => <li key={i}>{e}</li>)}
                    </ul>
                </div>
            )}

            {data.valoresNoEncontrados && Object.keys(data.valoresNoEncontrados).length > 0 && (
                <div className="text-xs bg-amber-50 border border-amber-300 rounded p-2 dark:bg-amber-950/30 dark:border-amber-700">
                    <div className="font-semibold text-amber-800 dark:text-amber-300 mb-1">
                        Valores referenciados pero no encontrados en tablas auxiliares (se guardaron como NULL):
                    </div>
                    <div className="space-y-1.5">
                        {Object.entries(data.valoresNoEncontrados).map(([campo, valores]) => (
                            <div key={campo}>
                                <span className="font-medium text-amber-900 dark:text-amber-200 capitalize">{campo}</span>
                                <span className="text-amber-700 dark:text-amber-400"> ({valores.length}):</span>
                                <div className="mt-0.5 flex flex-wrap gap-1">
                                    {valores.map((v) => (
                                        <span key={v} className="bg-white dark:bg-slate-800 border border-amber-300 dark:border-amber-700 text-amber-800 dark:text-amber-300 font-mono px-1.5 py-0.5 rounded">
                                            {v}
                                        </span>
                                    ))}
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}

// ---------------------------------------------------------------------------
// ImportCard
// ---------------------------------------------------------------------------

interface ImportCardProps {
    titulo: string;
    descripcion: string;
    endpoint: string;
    tipo?: "costos" | "migracion" | "tablas-auxiliares" | "enriquecimiento";
    columnas?: string[];
    advertencia?: string;
    badge?: string;
    /** Número de paso a mostrar como círculo a la izquierda del título. */
    paso?: number;
}

interface EnriquecimientoResult {
    totalRows?: number;
    successRows?: number;
    errorRows?: number;
    errors?: string[];
    message?: string;
}

function ImportCard({ titulo, descripcion, endpoint, tipo = "costos", columnas, advertencia, badge, paso }: ImportCardProps) {
    const fileRef = useRef<HTMLInputElement>(null);
    const [cargando, setCargando] = useState(false);
    const [resultado, setResultado] = useState<{ ok: boolean; mensaje: string; data?: any } | null>(null);

    const handleImportar = async () => {
        const archivo = fileRef.current?.files?.[0];
        if (!archivo) { toast.error("Seleccioná un archivo antes de importar."); return; }
        setCargando(true);
        setResultado(null);
        try {
            const formData = new FormData();
            formData.append("archivo", archivo);
            const res = await fetchAPI(`${API_BASE_URL}${endpoint}`, { method: "POST", body: formData });
            const data = await res.json().catch(() => ({}));
            let mensaje: string;
            if (tipo === "migracion" || tipo === "tablas-auxiliares") {
                const d = data as MigracionResult;
                mensaje = d.message ?? `Importación completada. ${d.hojasProcesadas ?? 0}/${d.totalHojas ?? 0} hojas procesadas.`;
            } else if (tipo === "enriquecimiento") {
                const d = data as EnriquecimientoResult;
                mensaje = d.message ?? `Enriquecimiento completado. ${d.successRows ?? 0}/${d.totalRows ?? 0} productos actualizados.`;
            } else {
                const d = data as ImportResult;
                mensaje = `Importación completada. ${d.productosActualizados ?? 0} actualizados, ${d.productosNoEncontrados ?? 0} no encontrados.`;
            }
            setResultado({ ok: true, mensaje, data });
            if (fileRef.current) fileRef.current.value = "";
        } catch (err: unknown) {
            setResultado({ ok: false, mensaje: "Error: " + (getErrorMessage(err, "desconocido")) });
        } finally {
            setCargando(false);
        }
    };

    return (
        <div className="bg-white dark:bg-slate-800 rounded-lg border border-blue-200 dark:border-slate-600 p-5 flex flex-col gap-3">
            <div className="flex items-start gap-3">
                {paso !== undefined && (
                    <span className="shrink-0 w-9 h-9 rounded-full bg-blue-600 text-white font-bold text-base flex items-center justify-center shadow-sm">
                        {paso}
                    </span>
                )}
                <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                        <h3 className="font-semibold text-gray-800 dark:text-slate-100">{titulo}</h3>
                        {badge && (
                            <span className="text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5 rounded-full bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-300 border border-amber-300 dark:border-amber-700">
                                {badge}
                            </span>
                        )}
                    </div>
                    <p className="text-xs text-gray-500 dark:text-slate-400 mt-0.5">{descripcion}</p>
                </div>
            </div>

            {columnas && (
                <div className="text-xs bg-gray-50 dark:bg-slate-700/50 border border-gray-200 dark:border-slate-600 rounded p-2">
                    <div className="font-medium text-gray-600 dark:text-slate-300 mb-1">Columnas esperadas:</div>
                    <div className="flex flex-wrap gap-1">
                        {columnas.map((col) => (
                            <span key={col} className="bg-white dark:bg-slate-700 border border-gray-300 dark:border-slate-500 rounded px-1.5 py-0.5 font-mono text-gray-700 dark:text-slate-200">{col}</span>
                        ))}
                    </div>
                </div>
            )}

            {advertencia && (
                <div className="text-xs text-amber-700 dark:text-amber-400 bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-800 rounded p-2">
                    ⚠️ {advertencia}
                </div>
            )}

            <input
                ref={fileRef} type="file" accept=".xlsx,.xls,.xlsm"
                className="block w-full text-sm text-gray-600 dark:text-slate-300 file:mr-3 file:py-1.5 file:px-3 file:rounded file:border file:border-gray-300 dark:file:border-slate-500 file:text-sm file:bg-gray-50 dark:file:bg-slate-700 file:text-gray-700 dark:file:text-slate-200 hover:file:bg-gray-100 dark:hover:file:bg-slate-600 cursor-pointer"
            />

            <Button text={cargando ? "Importando..." : "Importar"} variant="dark" onClick={handleImportar} disabled={cargando}>
                <ArrowUpTrayIcon className="w-4 h-4" />
            </Button>

            {resultado && (
                <div className={`text-sm rounded p-3 border ${resultado.ok ? "text-green-700 bg-green-50 border-green-200" : "text-red-700 bg-red-50 border-red-200"}`}>
                    {resultado.mensaje}
                    {resultado.ok && resultado.data && (tipo === "migracion" || tipo === "tablas-auxiliares") && <ResultadoMigracion data={resultado.data} />}
                    {resultado.ok && resultado.data && tipo === "costos" && <ResultadoImport data={resultado.data} />}
                    {resultado.ok && resultado.data && tipo === "enriquecimiento" && <ResultadoEnriquecimiento data={resultado.data} />}
                </div>
            )}
        </div>
    );
}

// ---------------------------------------------------------------------------
// LimpiarDatosCard — operación destructiva con doble confirmación
// ---------------------------------------------------------------------------

interface LimpiezaResult {
    tablasLimpiadas?: string[];
    errores?: string[];
    message?: string;
}

// Grupos visuales de tablas en el orden en que se muestran en la UI.
// NO se exponen tablas de auth (usuarios/roles/permisos) — borrarlas dejaría
// al sistema sin acceso.
const GRUPOS_TABLAS: { titulo: string; descripcion: string; tablas: string[] }[] = [
    {
        titulo: "Transaccionales",
        descripcion: "Datos generados día a día y cachés. Borrar acá no afecta maestros ni configuración.",
        tablas: [
            "auditoria_cambios",
            "venta_diaria_cache",
            "ordenes_compra",
            "orden_compra_lineas",
            "producto_canal_precios",
            "producto_canal_precio_inflado",
            "producto_margen",
        ],
    },
    {
        titulo: "Productos y relaciones",
        descripcion: "Productos, MLAs y las tablas N-N (apto/catalogo/segmento).",
        tablas: [
            "productos",
            "mlas",
            "producto_apto",
            "producto_catalogo",
            "producto_segmento",
        ],
    },
    {
        titulo: "Maestros",
        descripcion: "Atributos de producto y entidades referenciadas. La cascada se autoselecciona para evitar FKs huérfanos.",
        tablas: [
            "marcas",
            "tipos",
            "materiales",
            "origenes",
            "clasif_gral",
            "clasif_gastro",
            "proveedores",
            "segmentos",
            "aptos",
            "catalogos",
        ],
    },
    {
        titulo: "Canales y cálculo",
        descripcion: "Canales de venta, conceptos de cálculo y reglas asociadas.",
        tablas: [
            "canales",
            "canal_concepto",
            "canal_concepto_cuota",
            "canal_concepto_regla",
            "canal_regla",
            "conceptos_calculo",
        ],
    },
    {
        titulo: "Reglas y configuración",
        descripcion: "Precios inflados, descuentos y configuraciones del sistema. Borrarlas requiere re-configurar.",
        tablas: [
            "precios_inflados",
            "reglas_descuentos",
            "catalogo_pdf_config",
            "configuracion_ml",
            "reposicion_config",
            "config_automatizacion",
            "dux_horario_sync",
        ],
    },
];

// Dependencias: clave → tablas que también deben borrarse si se borra la clave.
// Se aplica cascada transitiva en el frontend al toggle. Construido a partir
// de las @JoinColumn reales de las entidades JPA.
const DEPENDENCIAS: Record<string, string[]> = {
    // Maestros referenciados por productos y reglas
    marcas: ["productos", "canal_concepto_regla", "canal_regla"],
    tipos: ["productos", "canal_concepto_regla", "canal_regla"],
    materiales: ["productos"],
    origenes: ["productos"],
    // clasif_gral también es referenciada (FK informal Integer) por catalogo_pdf_config.
    clasif_gral: ["productos", "canal_concepto_regla", "canal_regla", "reglas_descuentos", "catalogo_pdf_config"],
    clasif_gastro: ["productos", "canal_concepto_regla", "canal_regla", "reglas_descuentos"],
    proveedores: ["productos", "ordenes_compra"],
    segmentos: ["producto_segmento"],
    aptos: ["producto_apto"],
    // catalogos referenciada por catalogo_pdf_config (FK informal Integer, sin @JoinColumn).
    catalogos: ["producto_catalogo", "reglas_descuentos", "catalogo_pdf_config"],
    mlas: ["productos"],

    // Productos
    productos: [
        "producto_apto",
        "producto_catalogo",
        "producto_segmento",
        "producto_canal_precio_inflado",
        "producto_canal_precios",
        "producto_margen",
        "orden_compra_lineas",
        "canal_regla",
        // venta_diaria_cache referencia productos por sku (sin FK formal pero
        // dependencia lógica: borrar productos sin esto deja ventas con SKUs huérfanos).
        "venta_diaria_cache",
    ],

    // Órdenes de compra
    ordenes_compra: ["orden_compra_lineas"],

    // Canales y cálculo
    canales: [
        "canal_concepto",
        "canal_concepto_cuota",
        "canal_concepto_regla",
        "canal_regla",
        "producto_canal_precios",
        "producto_canal_precio_inflado",
        "reglas_descuentos",
        // catalogo_pdf_config tiene canal_id como FK informal Integer (sin @JoinColumn).
        "catalogo_pdf_config",
    ],
    conceptos_calculo: ["canal_concepto", "canal_concepto_regla"],

    // Precios inflados
    precios_inflados: ["producto_canal_precio_inflado"],
};

const TODAS_LAS_TABLAS: string[] = GRUPOS_TABLAS.flatMap((g) => g.tablas);

/** Cierre transitivo: dado un set, devuelve set + todos sus dependientes recursivos. */
function expandirCascada(seleccionadas: Set<string>): Set<string> {
    const resultado = new Set(seleccionadas);
    const cola = [...seleccionadas];
    while (cola.length > 0) {
        const actual = cola.shift()!;
        const deps = DEPENDENCIAS[actual] ?? [];
        for (const d of deps) {
            if (!resultado.has(d)) {
                resultado.add(d);
                cola.push(d);
            }
        }
    }
    return resultado;
}

/** Tablas que requieren a `tabla` para ser deletable (los "padres" en la cascada). */
function padresDe(tabla: string): string[] {
    return Object.entries(DEPENDENCIAS)
        .filter(([, deps]) => deps.includes(tabla))
        .map(([padre]) => padre);
}

function LimpiarDatosCard() {
    const FRASE_CONFIRMACION = "BORRAR DATOS";

    const [cargando, setCargando] = useState(false);
    const [confirmacion, setConfirmacion] = useState("");
    const [resultado, setResultado] = useState<LimpiezaResult | null>(null);
    const [error, setError] = useState<string | null>(null);
    // Selección manual del usuario.
    const [seleccionManual, setSeleccionManual] = useState<Set<string>>(
        () => new Set(TODAS_LAS_TABLAS)
    );
    // Toggle de cascada. Si está activo (default), borrar una tabla padre arrastra
    // automáticamente sus dependientes (más seguro). Si está desactivo, solo se
    // borran las tablas explícitamente seleccionadas, ignorando dependencias
    // (puede dejar FKs huérfanos pero da control total al usuario).
    const [cascadaActiva, setCascadaActiva] = useState(true);

    // Si la cascada está activa, expande dependientes. Si no, usa solo la selección manual.
    const seleccionEfectiva = useMemo(
        () => (cascadaActiva ? expandirCascada(seleccionManual) : new Set(seleccionManual)),
        [seleccionManual, cascadaActiva]
    );

    // Una tabla está "forzada" si algún padre suyo está en la selección efectiva.
    // Solo aplica cuando la cascada está activa (sino el usuario puede tocar libremente).
    const tablasForzadas = useMemo(() => {
        if (!cascadaActiva) return new Set<string>();
        const forzadas = new Set<string>();
        for (const t of TODAS_LAS_TABLAS) {
            if (padresDe(t).some((p) => seleccionEfectiva.has(p))) {
                forzadas.add(t);
            }
        }
        return forzadas;
    }, [seleccionEfectiva, cascadaActiva]);

    const toggleTabla = (tabla: string) => {
        if (tablasForzadas.has(tabla)) {
            toast.warning(`No podés desmarcar "${tabla}": un padre seleccionado la requiere.`);
            return;
        }
        setSeleccionManual((prev) => {
            const next = new Set(prev);
            if (next.has(tabla)) next.delete(tabla);
            else next.add(tabla);
            return next;
        });
    };

    const toggleGrupo = (tablas: string[], marcarTodo: boolean) => {
        setSeleccionManual((prev) => {
            const next = new Set(prev);
            for (const t of tablas) {
                if (marcarTodo) next.add(t);
                else if (!tablasForzadas.has(t)) next.delete(t);
            }
            return next;
        });
    };

    const seleccionarTodo = () => setSeleccionManual(new Set(TODAS_LAS_TABLAS));
    const limpiarSeleccion = () => setSeleccionManual(new Set());

    const cantidadEfectiva = seleccionEfectiva.size;

    const handleLimpiar = async () => {
        if (confirmacion !== FRASE_CONFIRMACION) {
            toast.error(`Escribí "${FRASE_CONFIRMACION}" para confirmar.`);
            return;
        }
        if (cantidadEfectiva === 0) {
            toast.error("No seleccionaste ninguna tabla.");
            return;
        }
        setCargando(true);
        setResultado(null);
        setError(null);
        try {
            const res = await fetchAPI(`${API_BASE_URL}/api/excel/limpiar-datos`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ tablas: [...seleccionEfectiva] }),
            });
            const data = await res.json().catch(() => ({})) as LimpiezaResult;
            setResultado(data);
            setConfirmacion("");
            if ((data.errores?.length ?? 0) > 0) {
                notificar.warning(data.message ?? "Limpieza con errores.");
            } else {
                notificar.success(data.message ?? "Tablas vaciadas.");
            }
        } catch (err: unknown) {
            const msg = getErrorMessage(err, "Error desconocido");
            setError(msg);
            notificar.error(msg);
        } finally {
            setCargando(false);
        }
    };

    return (
        <div className="bg-white dark:bg-slate-800 rounded-lg border border-red-300 dark:border-red-900 p-5 flex flex-col gap-3">
            <div>
                <h3 className="font-semibold text-gray-800 dark:text-slate-100 flex items-center gap-2">
                    <ExclamationTriangleIcon className="w-5 h-5 text-red-600" />
                    Limpiar Datos de la BD
                </h3>
                <p className="text-xs text-gray-500 dark:text-slate-400 mt-0.5">
                    Borra el contenido de las tablas seleccionadas y resetea sus IDs a 1. Operación irreversible.
                </p>
            </div>

            <div className="flex items-center justify-between text-xs">
                <span className="text-gray-600 dark:text-slate-300">
                    <strong>{cantidadEfectiva}</strong> / {TODAS_LAS_TABLAS.length} tablas seleccionadas
                </span>
                <div className="flex gap-2">
                    <button type="button" onClick={seleccionarTodo}
                        className="text-blue-600 hover:text-blue-700 dark:text-blue-400 underline"
                        disabled={cargando}>
                        Seleccionar todas
                    </button>
                    <span className="text-gray-300">|</span>
                    <button type="button" onClick={limpiarSeleccion}
                        className="text-blue-600 hover:text-blue-700 dark:text-blue-400 underline"
                        disabled={cargando}>
                        Limpiar selección
                    </button>
                </div>
            </div>

            {/* Toggle cascada */}
            <div className={`text-xs rounded p-2 border ${cascadaActiva
                ? "bg-gray-50 dark:bg-slate-700/50 border-gray-200 dark:border-slate-600"
                : "bg-amber-50 dark:bg-amber-900/20 border-amber-300 dark:border-amber-700"}`}>
                <label className="flex items-start gap-2 cursor-pointer select-none">
                    <input
                        type="checkbox"
                        checked={cascadaActiva}
                        onChange={(e) => setCascadaActiva(e.target.checked)}
                        disabled={cargando}
                        className="mt-0.5 accent-red-600"
                    />
                    <div className="flex-1">
                        <div className="font-semibold text-gray-700 dark:text-slate-200">
                            Borrar también tablas dependientes (cascada)
                        </div>
                        {cascadaActiva ? (
                            <p className="text-gray-500 dark:text-slate-400 mt-0.5">
                                Si seleccionás una tabla con dependientes (ej. marcas → productos),
                                se auto-marcan también las dependientes. Evita FKs huérfanos.
                            </p>
                        ) : (
                            <p className="text-amber-700 dark:text-amber-300 mt-0.5">
                                ⚠️ Cascada desactivada: solo se borrarán las tablas que marques
                                explícitamente, sin tocar las dependientes. Puede dejar registros
                                con FKs apuntando a IDs inexistentes. Útil si vas a re-importar
                                con los mismos IDs.
                            </p>
                        )}
                    </div>
                </label>
            </div>

            <div className="space-y-2">
                {GRUPOS_TABLAS.map((grupo) => {
                    const enGrupo = grupo.tablas;
                    const seleccionadasGrupo = enGrupo.filter((t) => seleccionEfectiva.has(t)).length;
                    const todasSeleccionadas = seleccionadasGrupo === enGrupo.length;
                    return (
                        <div key={grupo.titulo}
                            className="bg-gray-50 dark:bg-slate-700/30 border border-gray-200 dark:border-slate-600 rounded p-2.5">
                            <div className="flex items-center justify-between mb-1.5">
                                <div>
                                    <label className="flex items-center gap-2 cursor-pointer select-none">
                                        <input
                                            type="checkbox"
                                            checked={todasSeleccionadas}
                                            ref={(el) => {
                                                if (el) el.indeterminate = seleccionadasGrupo > 0 && !todasSeleccionadas;
                                            }}
                                            onChange={(e) => toggleGrupo(enGrupo, e.target.checked)}
                                            disabled={cargando}
                                            className="accent-red-600"
                                        />
                                        <span className="text-sm font-semibold text-gray-800 dark:text-slate-100">
                                            {grupo.titulo}
                                        </span>
                                        <span className="text-xs text-gray-500 dark:text-slate-400">
                                            ({seleccionadasGrupo}/{enGrupo.length})
                                        </span>
                                    </label>
                                    <p className="text-[11px] text-gray-500 dark:text-slate-400 ml-6 mt-0.5">{grupo.descripcion}</p>
                                </div>
                            </div>
                            <div className="flex flex-wrap gap-1.5 ml-6">
                                {enGrupo.map((tabla) => {
                                    const checked = seleccionEfectiva.has(tabla);
                                    const forzada = tablasForzadas.has(tabla);
                                    return (
                                        <label
                                            key={tabla}
                                            title={forzada ? `Auto-seleccionada por dependencia` : undefined}
                                            className={`flex items-center gap-1 text-xs font-mono border rounded px-1.5 py-0.5 cursor-pointer select-none ${
                                                checked
                                                    ? forzada
                                                        ? "bg-amber-50 dark:bg-amber-900/30 border-amber-300 dark:border-amber-700 text-amber-800 dark:text-amber-300"
                                                        : "bg-red-50 dark:bg-red-900/30 border-red-300 dark:border-red-700 text-red-800 dark:text-red-300"
                                                    : "bg-white dark:bg-slate-700 border-gray-300 dark:border-slate-500 text-gray-700 dark:text-slate-200"
                                            } ${cargando ? "opacity-60 cursor-not-allowed" : ""}`}
                                        >
                                            <input
                                                type="checkbox"
                                                checked={checked}
                                                onChange={() => toggleTabla(tabla)}
                                                disabled={cargando || forzada}
                                                className="accent-red-600"
                                            />
                                            {tabla}
                                        </label>
                                    );
                                })}
                            </div>
                        </div>
                    );
                })}
            </div>

            <div className="text-xs text-red-700 dark:text-red-400 bg-red-50 dark:bg-red-950/30 border border-red-200 dark:border-red-900 rounded p-2">
                ⚠️ <strong>Acción destructiva.</strong> Para habilitar el botón, escribí <code className="font-mono bg-red-100 dark:bg-red-900/50 px-1 rounded">{FRASE_CONFIRMACION}</code> abajo.
            </div>

            <input
                type="text"
                value={confirmacion}
                onChange={(e) => setConfirmacion(e.target.value)}
                placeholder={`Escribí "${FRASE_CONFIRMACION}" para confirmar`}
                className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200 font-mono"
                disabled={cargando}
            />

            <Button
                text={cargando ? "Borrando..." : `Borrar (${cantidadEfectiva} tabla${cantidadEfectiva === 1 ? "" : "s"})`}
                variant="dark"
                onClick={handleLimpiar}
                disabled={cargando || confirmacion !== FRASE_CONFIRMACION || cantidadEfectiva === 0}
            >
                <TrashIcon className="w-4 h-4" />
            </Button>

            {error && (
                <div className="text-sm rounded p-3 border text-red-700 bg-red-50 border-red-200">
                    {error}
                </div>
            )}

            {resultado && (
                <div className={`text-sm rounded p-3 border ${(resultado.errores?.length ?? 0) > 0 ? "text-amber-800 bg-amber-50 border-amber-300" : "text-green-700 bg-green-50 border-green-200"}`}>
                    <div className="font-medium">{resultado.message}</div>
                    {resultado.tablasLimpiadas && resultado.tablasLimpiadas.length > 0 && (
                        <div className="mt-2 flex flex-wrap gap-1">
                            {resultado.tablasLimpiadas.map((t) => (
                                <span key={t} className="bg-white border border-gray-300 text-gray-700 text-xs font-mono px-1.5 py-0.5 rounded">
                                    {t}
                                </span>
                            ))}
                        </div>
                    )}
                    {resultado.errores && resultado.errores.length > 0 && (
                        <ul className="list-disc list-inside text-red-600 space-y-0.5 mt-2 text-xs">
                            {resultado.errores.map((e, i) => <li key={i}>{e}</li>)}
                        </ul>
                    )}
                </div>
            )}
        </div>
    );
}

// ---------------------------------------------------------------------------
// Formatos de Exportar Precios
// ---------------------------------------------------------------------------

const FORMATOS = [
    {
        formato: "completo",
        label: "Completo",
        descripcion: "Todos los productos con columnas por canal × cuotas: PVP, costos, márgenes, markup.",
        requiereCuotas: false,
        color: "blue",
    },
    {
        formato: "mercadolibre",
        label: "MercadoLibre",
        descripcion: "SKU · Precio (inflado si existe) · MLA. Solo productos con MLA asignado.",
        requiereCuotas: true,
        color: "amber",
    },
    {
        formato: "kt-hogar",
        label: "KT Hogar",
        descripcion: "SKU · PVP KT Hogar · PVP Inflado para las cuotas indicadas.",
        requiereCuotas: true,
        color: "emerald",
    },
    {
        formato: "kt-gastro",
        label: "KT Gastro",
        descripcion: "SKU · PVP Gastro sin IVA para las cuotas indicadas.",
        requiereCuotas: true,
        color: "violet",
    },
] as const;

const colorBadge: Record<string, string> = {
    blue: "bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300 border-blue-200 dark:border-blue-800",
    amber: "bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-300 border-amber-200 dark:border-amber-800",
    emerald: "bg-emerald-100 dark:bg-emerald-900/40 text-emerald-700 dark:text-emerald-300 border-emerald-200 dark:border-emerald-800",
    violet: "bg-violet-100 dark:bg-violet-900/40 text-violet-700 dark:text-violet-300 border-violet-200 dark:border-violet-800",
};

// ---------------------------------------------------------------------------
// Opciones de ordenarPor para Catálogo
// ---------------------------------------------------------------------------

const ORDENAR_OPCIONES = [
    { value: "clasifGral", label: "Clasif. General" },
    { value: "clasifGastro", label: "Clasif. Gastro" },
    { value: "tipo", label: "Tipo" },
    { value: "marca", label: "Marca" },
    { value: "tag", label: "Tag" },
];

const CUOTAS_OPCIONES = [
    { value: "-1", label: "Transferencia" },
    { value: "0", label: "Contado" },
    { value: "3", label: "3 cuotas" },
    { value: "6", label: "6 cuotas" },
    { value: "9", label: "9 cuotas" },
    { value: "12", label: "12 cuotas" },
];

// Canal que resuelve cada formato en el backend (case-insensitive)
const FORMATO_CANAL: Record<string, string> = {
    "mercadolibre": "ML",
    "kt-hogar":     "KT HOGAR",
    "kt-gastro":    "KT GASTRO",
};

async function fetchCuotasPorCanal(canalId: number): Promise<string[]> {
    let res: Response;
    try { res = await fetchAPI(`${API_BASE_URL}/api/canal-concepto-cuotas/canal/${canalId}`); }
    catch { return []; }
    const data = await res.json();
    const arr: any[] = Array.isArray(data) ? data : (data.content ?? []);
    const nums = [...new Set(arr.map((c: any) => Number(c.cuotas)))].sort((a, b) => a - b);
    return nums.map(String);
}

// ---------------------------------------------------------------------------
// FormatoCard — card con cuotas dinámicas por canal
// ---------------------------------------------------------------------------

interface FormatoCardProps {
    fmt: typeof FORMATOS[number];
    exportandoPrecios: string | null;
    canales: { id: number; label: string }[];
    onExportar: (formato: string, cuotas?: string, canalId?: number) => void;
}

function FormatoCard({ fmt, exportandoPrecios, canales, onExportar }: FormatoCardProps) {
    const [cuotasDisp, setCuotasDisp] = useState<string[] | null>(null);
    const [cuotaSel, setCuotaSel] = useState<string>("0");
    const [cargandoCuotas, setCargandoCuotas] = useState(false);
    const [canalSel, setCanalSel] = useState<number | "">("");

    // Para formatos específicos: cargar cuotas del canal fijo
    useEffect(() => {
        if (!fmt.requiereCuotas) return;
        const canalNombre = FORMATO_CANAL[fmt.formato];
        if (!canalNombre) return;

        setCargandoCuotas(true);
        searchCanales(canalNombre)
            .then(async (results) => {
                const canal = results.find((r: { id: string | number; label: string }) => r.label.toUpperCase() === canalNombre.toUpperCase());
                if (!canal) return [];
                return fetchCuotasPorCanal(Number(canal.id));
            })
            .then((cuotas) => {
                if (cuotas.length > 0) {
                    setCuotasDisp(cuotas);
                    setCuotaSel(cuotas.includes("0") ? "0" : cuotas[0]);
                }
            })
            .catch(() => { /* usa todas las opciones */ })
            .finally(() => setCargandoCuotas(false));
    }, [fmt.formato]);

    // Para formato completo: cargar cuotas del canal seleccionado
    useEffect(() => {
        if (fmt.requiereCuotas || !canalSel) {
            if (!fmt.requiereCuotas) { setCuotasDisp(null); setCuotaSel("0"); }
            return;
        }
        setCargandoCuotas(true);
        fetchCuotasPorCanal(Number(canalSel))
            .then((cuotas) => {
                if (cuotas.length > 0) {
                    setCuotasDisp(cuotas);
                    setCuotaSel(cuotas.includes("0") ? "0" : cuotas[0]);
                } else {
                    setCuotasDisp(null);
                    setCuotaSel("0");
                }
            })
            .catch(() => { setCuotasDisp(null); })
            .finally(() => setCargandoCuotas(false));
    }, [canalSel, fmt.requiereCuotas]);

    const opciones = cuotasDisp
        ? CUOTAS_OPCIONES.filter(op => cuotasDisp.includes(op.value))
        : CUOTAS_OPCIONES;

    const isCompleto = fmt.formato === "completo";

    return (
        <div className="bg-white dark:bg-slate-800 rounded-lg border border-green-200 dark:border-slate-600 p-4 flex flex-col gap-3">
            <div className="flex items-start justify-between gap-2">
                <h3 className="font-semibold text-gray-800 dark:text-slate-100">{fmt.label}</h3>
                <span className={`text-xs font-medium px-2 py-0.5 rounded-full border shrink-0 ${fmt.requiereCuotas ? colorBadge[fmt.color] : "bg-gray-100 dark:bg-slate-700 text-gray-500 dark:text-slate-400 border-gray-200 dark:border-slate-600"}`}>
                    {fmt.requiereCuotas ? "Con cuotas" : isCompleto ? "Todos o filtrado" : "Sin cuotas"}
                </span>
            </div>
            <p className="text-xs text-gray-500 dark:text-slate-400 flex-1">{fmt.descripcion}</p>

            {/* Formato completo: selector de canal opcional */}
            {isCompleto && (
                <div className="flex flex-col gap-2">
                    <select
                        value={canalSel}
                        onChange={(e) => setCanalSel(e.target.value ? Number(e.target.value) : "")}
                        className="border border-gray-300 dark:border-slate-600 rounded p-1.5 text-xs bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200"
                    >
                        <option value="">Todos los canales</option>
                        {canales.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
                    </select>
                    {canalSel && (
                        <div>
                            {cargandoCuotas ? (
                                <span className="text-xs text-gray-400 dark:text-slate-500">Cargando cuotas...</span>
                            ) : (
                                <div className="flex gap-1.5 flex-wrap">
                                    {opciones.map(op => (
                                        <button
                                            key={op.value}
                                            type="button"
                                            onClick={() => setCuotaSel(op.value)}
                                            className={`px-2.5 py-1 rounded-full text-xs font-semibold border transition-colors ${cuotaSel === op.value
                                                ? "bg-green-700 text-white border-green-700"
                                                : "bg-white dark:bg-slate-700 text-gray-600 dark:text-slate-300 border-gray-300 dark:border-slate-500 hover:border-green-400 hover:text-green-700 dark:hover:text-green-400"
                                            }`}
                                        >
                                            {op.label}
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>
                    )}
                </div>
            )}

            {/* Formatos específicos: cuotas fijas */}
            {fmt.requiereCuotas && (
                <div>
                    {cargandoCuotas ? (
                        <span className="text-xs text-gray-400 dark:text-slate-500">Cargando cuotas...</span>
                    ) : (
                        <div className="flex gap-1.5 flex-wrap">
                            {opciones.map(op => (
                                <button
                                    key={op.value}
                                    type="button"
                                    onClick={() => setCuotaSel(op.value)}
                                    className={`px-2.5 py-1 rounded-full text-xs font-semibold border transition-colors ${cuotaSel === op.value
                                        ? "bg-green-700 text-white border-green-700"
                                        : "bg-white dark:bg-slate-700 text-gray-600 dark:text-slate-300 border-gray-300 dark:border-slate-500 hover:border-green-400 hover:text-green-700 dark:hover:text-green-400"
                                    }`}
                                >
                                    {op.label}
                                </button>
                            ))}
                        </div>
                    )}
                </div>
            )}

            <Button
                text={exportandoPrecios === fmt.formato ? "Descargando..." : "Descargar"}
                variant="light"
                onClick={() => onExportar(
                    fmt.formato,
                    (fmt.requiereCuotas || canalSel) ? cuotaSel : undefined,
                    isCompleto && canalSel ? Number(canalSel) : undefined,
                )}
                disabled={exportandoPrecios !== null || (fmt.requiereCuotas && cargandoCuotas)}
            >
                <ArrowDownTrayIcon className="w-4 h-4" />
            </Button>
        </div>
    );
}

// ---------------------------------------------------------------------------
// Page
// ---------------------------------------------------------------------------

export default function HerramientasExcelPage() {
    const { usuario } = useAuth();
    const isAdmin = (usuario.rol || "").trim().toUpperCase() === "ADMIN";
    // Exportar precios
    const [exportandoPrecios, setExportandoPrecios] = useState<string | null>(null);
    const [advertenciasPrecios, setAdvertenciasPrecios] = useState<DescargaResult | null>(null);

    // Exportar catálogo
    const [catalogos, setCatalogos] = useState<{ id: number; label: string }[]>([]);
    const [canales, setCanales] = useState<{ id: number; label: string }[]>([]);
    const [catalogoId, setCatalogoId] = useState<number | null>(null);
    const [canalId, setCanalId] = useState<number | null>(null);
    const [cuotasCatalogo, setCuotasCatalogo] = useState<string>("0");
    const [cuotasCatalogoDisp, setCuotasCatalogoDisp] = useState<string[] | null>(null);
    const [exportandoCatalogo, setExportandoCatalogo] = useState(false);
    const [exportandoCatalogoPdf, setExportandoCatalogoPdf] = useState(false);
    const [configsPdf, setConfigsPdf] = useState<CatalogoPdfConfigItem[]>([]);
    const [cargandoConfigsPdf, setCargandoConfigsPdf] = useState(false);
    const [ejecutandoConfigSlug, setEjecutandoConfigSlug] = useState<number | null>(null);
    const [configModalOpen, setConfigModalOpen] = useState(false);
    const [configForm, setConfigForm] = useState<CatalogoPdfConfigFormState>(emptyConfigForm);
    const [editingConfigId, setEditingConfigId] = useState<number | null>(null);
    const [guardandoConfig, setGuardandoConfig] = useState(false);
    const [eliminandoConfigId, setEliminandoConfigId] = useState<number | null>(null);
    const [modalCuotasDisp, setModalCuotasDisp] = useState<string[] | null>(null);
    const [cargandoModalCuotas, setCargandoModalCuotas] = useState(false);

    // Filtros opcionales catálogo
    const [mostrarFiltros, setMostrarFiltros] = useState(false);
    const [clasifGralId, setClasifGralId] = useState<number | null>(null);
    const [clasifGralLabel, setClasifGralLabel] = useState<string | undefined>();
    const [clasifGastroId, setClasifGastroId] = useState<number | null>(null);
    const [clasifGastroLabel, setClasifGastroLabel] = useState<string | undefined>();
    const [tipoId, setTipoId] = useState<number | null>(null);
    const [tipoLabel, setTipoLabel] = useState<string | undefined>();
    const [marcaId, setMarcaId] = useState<number | null>(null);
    const [marcaLabel, setMarcaLabel] = useState<string | undefined>();
    const [tag, setTag] = useState<"" | "MAQUINA" | "REPUESTO" | "MENAJE">("");
    const [ordenarPor, setOrdenarPor] = useState<string[]>([]);

    const handleExportarPrecios = async (formato: string, cuotas?: string, canalIdParam?: number) => {
        setExportandoPrecios(formato);
        setAdvertenciasPrecios(null);
        const params = new URLSearchParams({ formato });
        if (cuotas !== undefined && cuotas !== "") params.append("cuotas", cuotas);
        if (canalIdParam) params.append("canalId", String(canalIdParam));
        try {
            const result = await descargarExcel(`${API_BASE_URL}/api/excel/exportar-precios?${params.toString()}`);
            if (result.advertencias > 0) {
                setAdvertenciasPrecios(result);
                notificar.warning(`Descargado con ${result.advertencias} advertencia${result.advertencias > 1 ? "s" : ""}.`);
            } else {
                notificar.success("Lista de precios descargada.");
            }
        } catch (err: unknown) {
            notificar.error(getErrorMessage(err, "Error al exportar"));
        } finally {
            setExportandoPrecios(null);
        }
    };

    const handleExportarCatalogo = async () => {
        if (!catalogoId || !canalId) { toast.error("Seleccioná un catálogo y un canal."); return; }
        setExportandoCatalogo(true);
        const params = new URLSearchParams({
            catalogoId: String(catalogoId),
            canalId: String(canalId),
            cuotas: cuotasCatalogo || "0",
        });
        if (clasifGralId) params.append("clasifGralId", String(clasifGralId));
        if (clasifGastroId) params.append("clasifGastroId", String(clasifGastroId));
        if (tipoId) params.append("tipoId", String(tipoId));
        if (marcaId) params.append("marcaId", String(marcaId));
        if (tag !== "") params.append("tag", tag);
        if (ordenarPor.length > 0) params.append("ordenarPor", ordenarPor.join(","));
        try {
            await descargarExcel(`${API_BASE_URL}/api/excel/exportar-catalogo?${params.toString()}`);
            notificar.success("Catálogo descargado.");
        } catch (err: unknown) {
            notificar.error(getErrorMessage(err, "Error al exportar catálogo"));
        } finally {
            setExportandoCatalogo(false);
        }
    };

    const handleExportarCatalogoPdf = async () => {
        if (!catalogoId || !canalId) { toast.error("Seleccioná un catálogo y un canal."); return; }
        setExportandoCatalogoPdf(true);
        const params = new URLSearchParams({
            catalogoId: String(catalogoId),
            canalId: String(canalId),
            cuotas: cuotasCatalogo || "0",
            incluirImagenes: "true",
        });
        if (clasifGralId) params.append("clasifGralId", String(clasifGralId));
        if (clasifGastroId) params.append("clasifGastroId", String(clasifGastroId));
        if (tipoId) params.append("tipoId", String(tipoId));
        if (marcaId) params.append("marcaId", String(marcaId));
        if (tag !== "") params.append("tag", tag);
        if (ordenarPor.length > 0) params.append("ordenarPor", ordenarPor.join(","));
        try {
            const productos = await descargarPdf(`${API_BASE_URL}/api/catalogos-pdf/exportar?${params.toString()}`);
            notificar.success(productos > 0 ? `Catálogo PDF descargado (${productos} productos).` : "Catálogo PDF descargado.");
        } catch (err: unknown) {
            notificar.error(getErrorMessage(err, "Error al exportar catálogo PDF"));
        } finally {
            setExportandoCatalogoPdf(false);
        }
    };

    const toggleOrdenarPor = (val: string) => {
        setOrdenarPor((prev) => prev.includes(val) ? prev.filter((v) => v !== val) : [...prev, val]);
    };

    const cargarConfigsPdf = async () => {
        setCargandoConfigsPdf(true);
        try {
            const response = await fetchAPI(`${API_BASE_URL}/api/catalogos-pdf-config?page=0&size=100&sort=nombre,asc`);
            const data = await response.json() as PageResponse<CatalogoPdfConfigItem>;
            setConfigsPdf(data.content ?? []);
        } catch (err: unknown) {
            notificar.error(getErrorMessage(err, "No se pudieron cargar las automatizaciones PDF."));
        } finally {
            setCargandoConfigsPdf(false);
        }
    };

    const handleEjecutarCatalogoPdfAutomatico = async (config: CatalogoPdfConfigItem) => {
        setEjecutandoConfigSlug(config.id);
        try {
            const productos = await descargarPdf(
                `${API_BASE_URL}/api/catalogos-pdf/generar-automatico/${config.id}`,
                { method: "POST" },
            );
            notificar.success(
                productos > 0
                    ? `PDF generado para "${config.nombre}" (${productos} productos).`
                    : `PDF generado para "${config.nombre}".`,
            );
        } catch (err: unknown) {
            notificar.error(getErrorMessage(err) || `No se pudo generar el PDF para "${config.nombre}".`);
        } finally {
            setEjecutandoConfigSlug(null);
        }
    };

    const openCreateConfigModal = () => {
        setEditingConfigId(null);
        setConfigForm(emptyConfigForm());
        setConfigModalOpen(true);
    };

    const openEditConfigModal = (config: CatalogoPdfConfigItem) => {
        setEditingConfigId(config.id);
        setConfigForm(configToForm(config));
        setConfigModalOpen(true);
    };

    const closeConfigModal = () => {
        if (guardandoConfig) return;
        setConfigModalOpen(false);
        setEditingConfigId(null);
        setConfigForm(emptyConfigForm());
        setModalCuotasDisp(null);
    };

    const updateConfigField = <K extends keyof CatalogoPdfConfigFormState>(key: K, value: CatalogoPdfConfigFormState[K]) => {
        setConfigForm((prev) => ({ ...prev, [key]: value }));
    };

    const handleGuardarConfig = async () => {
        if (!configForm.nombre.trim() || !configForm.canal.trim() || !configForm.catalogo.trim() || !configForm.ubicacionSalida.trim()) {
            toast.error("Completá al menos nombre, canal, catálogo y ubicación de salida.");
            return;
        }

        setGuardandoConfig(true);
        const payload = {
            nombre: configForm.nombre.trim(),
            canal: configForm.canal.trim(),
            catalogo: configForm.catalogo.trim(),
            cuotas: Number(configForm.cuotas || "0"),
            clasificacion: configForm.clasificacion.trim() || null,
            caratula: configForm.caratula,
            titulo: configForm.titulo.trim() || null,
            estetica: configForm.estetica.trim() || null,
            tipoDocumento: configForm.tipoDocumento.trim() || null,
            productosPorPagina: Number(configForm.productosPorPagina || "12"),
            ubicacionSalida: configForm.ubicacionSalida.trim(),
            activo: configForm.activo,
        };

        try {
            await fetchAPI(
                `${API_BASE_URL}/api/catalogos-pdf-config${editingConfigId ? `/${editingConfigId}` : ""}`,
                {
                    method: editingConfigId ? "PUT" : "POST",
                    headers: withAuditOrigin("FORM", { "Content-Type": "application/json" }),
                    body: JSON.stringify(payload),
                },
            );
            notificar.success(editingConfigId ? "Automatización PDF actualizada." : "Automatización PDF creada.");
            closeConfigModal();
            await cargarConfigsPdf();
        } catch (err: unknown) {
            notificar.error(getErrorMessage(err, "No se pudo guardar la automatización PDF."));
        } finally {
            setGuardandoConfig(false);
        }
    };

    const handleEliminarConfig = async (config: CatalogoPdfConfigItem) => {
        const confirmed = window.confirm(`¿Eliminar la automatización "${config.nombre}"?`);
        if (!confirmed) return;

        setEliminandoConfigId(config.id);
        try {
            await fetchAPI(`${API_BASE_URL}/api/catalogos-pdf-config/${config.id}`, {
                method: "DELETE",
                headers: withAuditOrigin("TABLE"),
                allowedStatuses: [204],
            });
            notificar.success("Automatización PDF eliminada.");
            await cargarConfigsPdf();
        } catch (err: unknown) {
            notificar.error(getErrorMessage(err, "No se pudo eliminar la automatización PDF."));
        } finally {
            setEliminandoConfigId(null);
        }
    };

    // Cargar catálogos y canales al montar
    useEffect(() => {
        searchCatalogos("")
            .then((res) => setCatalogos(res.map((r: any) => ({ id: Number(r.id), label: r.label }))))
            .catch((e) => console.warn("No se pudieron cargar catálogos:", e));
        searchCanales("")
            .then((res) => setCanales(res.map((r: any) => ({ id: Number(r.id), label: r.label }))))
            .catch((e) => console.warn("No se pudieron cargar canales:", e));
        cargarConfigsPdf();
    }, []);

    // Cargar cuotas disponibles cuando cambia el canal del catálogo
    useEffect(() => {
        if (!canalId) { setCuotasCatalogoDisp(null); return; }
        fetchCuotasPorCanal(canalId).then((cuotas) => {
            if (cuotas.length > 0) {
                setCuotasCatalogoDisp(cuotas);
                setCuotasCatalogo(cuotas.includes("0") ? "0" : cuotas[0]);
            } else {
                setCuotasCatalogoDisp(null);
            }
        }).catch(() => setCuotasCatalogoDisp(null));
    }, [canalId]);

    useEffect(() => {
        if (!configModalOpen) return;

        const canalSeleccionado = canales.find(
            (canal) => canal.label.trim().toUpperCase() === configForm.canal.trim().toUpperCase(),
        );

        if (!canalSeleccionado) {
            setModalCuotasDisp(null);
            return;
        }

        setCargandoModalCuotas(true);
        fetchCuotasPorCanal(canalSeleccionado.id)
            .then((cuotas) => {
                setModalCuotasDisp(cuotas.length > 0 ? cuotas : null);
                if (cuotas.length > 0 && !cuotas.includes(configForm.cuotas)) {
                    updateConfigField("cuotas", cuotas.includes("0") ? "0" : cuotas[0]);
                }
            })
            .catch(() => setModalCuotasDisp(null))
            .finally(() => setCargandoModalCuotas(false));
    }, [configModalOpen, configForm.canal, canales]);

    return !isAdmin ? (
        <main className="p-6 bg-gray-50 min-h-0 flex flex-col overflow-hidden dark:bg-slate-900">
            <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-amber-800 dark:border-amber-800 dark:bg-amber-500/10 dark:text-amber-200">
                Solo los administradores pueden acceder a Integraciones.
            </div>
        </main>
    ) : (
        <main className="p-4 md:p-5 bg-gray-50 dark:bg-slate-900 flex flex-col gap-4">
            {/* Header */}
            <div>
                <h1 className="text-3xl font-bold text-gray-800 dark:text-slate-100 flex items-center gap-2">
                    <TableCellsIcon className="w-8 h-8 text-gray-600 dark:text-slate-400" />
                    Herramientas Excel
                </h1>
                <p className="text-gray-500 dark:text-slate-400 text-sm mt-1">Importá y exportá datos desde archivos Excel.</p>
            </div>

            {/* ── Importación inicial / Setup (3 pasos secuenciales) ────── */}
            <section className="bg-blue-50 dark:bg-blue-950/30 rounded-xl border border-blue-200 dark:border-blue-900 p-6">
                <h2 className="text-lg font-bold text-blue-900 dark:text-blue-300 mb-1 flex items-center gap-2">
                    <ArrowUpTrayIcon className="w-5 h-5" />
                    Importación inicial — Setup en 3 pasos
                </h2>
                <p className="text-sm text-blue-700 dark:text-blue-400 mb-5">
                    Carga inicial de datos. Ejecutar los pasos <strong>en orden</strong>: primero las tablas auxiliares, luego los productos, finalmente el enriquecimiento.
                </p>

                <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
                    <ImportCard
                        paso={1}
                        titulo="Importar Tablas Auxiliares"
                        descripcion="Carga las tablas pequeñas usadas como atributos de producto: marcas, tipos, materiales, orígenes, clasif. grales, clasif. gastro, aptos y proveedores."
                        endpoint="/api/excel/importar-tablas-auxiliares"
                        tipo="tablas-auxiliares"
                        columnas={["Marcas", "Tipos", "Materiales", "Origenes", "Clasif. Grales", "Clasif. Gastro", "Aptos", "Proveedores"]}
                        advertencia="Usar la plantilla Plantilla_Tablas_SuperMaster.xlsx. Las entidades se buscan/crean por nombre (no se duplican)."
                    />
                    <ImportCard
                        paso={2}
                        titulo="Importar Productos (MASTER)"
                        descripcion="Importa la hoja MASTER del Excel SUPER MASTER. Crea o actualiza productos por SKU (descripción, costo, IVA, MLA, catálogos, márgenes). Las FKs (marca, tipo, etc.) y dimensiones se cargan luego desde NUEVO SUPER MASTER."
                        endpoint="/api/excel/importar-migracion"
                        tipo="migracion"
                        advertencia="Después de este paso correr 'Enriquecer Productos' con NUEVO SUPER MASTER.xlsx para completar marca, tipo, clasificaciones, material, proveedor y dimensiones."
                    />
                    <ImportCard
                        paso={3}
                        titulo="Enriquecer Productos (NUEVO MASTER)"
                        descripcion="Lee la hoja MASTER de NUEVO SUPER MASTER.xlsx (desde la fila 3) y completa por SKU los FKs (origen, marca/línea, clasif gral, clasif gastro, tipo, material, proveedor) y las dimensiones (capacidad, largo, ancho, alto, diamboca, diambase, espesor)."
                        endpoint="/api/excel/enriquecer-productos"
                        tipo="enriquecimiento"
                        columnas={["SKU", "ID ORIGEN", "ID MARCA", "ID LINEA", "ID CLASIF GRAL1/2", "ID CLASIF GASTRO1/2", "ID TIPO1/2/3", "CAPACIDAD", "LARGO", "ANCHO", "ALTO", "DIAMBOCA", "DIAMBASE", "ESPESOR", "ID MATERIAL", "ID PROVEEDOR"]}
                        advertencia="Ejecutar DESPUÉS de 'Importar Productos (MASTER)'. SKUs que no existan en la BD se ignoran. Un ID = 0 se interpreta como 'sin dato'."
                    />
                </div>
            </section>

            {/* ── Actualización operativa (suelto, post-setup) ──────────── */}
            <section className="bg-emerald-50 dark:bg-emerald-950/30 rounded-xl border border-emerald-200 dark:border-emerald-900 p-6">
                <h2 className="text-lg font-bold text-emerald-900 dark:text-emerald-300 mb-1 flex items-center gap-2">
                    <ArrowUpTrayIcon className="w-5 h-5" />
                    Actualización operativa
                </h2>
                <p className="text-sm text-emerald-700 dark:text-emerald-400 mb-5">
                    Acciones recurrentes sobre productos ya cargados. No requieren orden ni setup previo.
                </p>

                <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
                    <ImportCard
                        titulo="Importar Costos"
                        descripcion="Actualiza costos, IVA y proveedor de productos existentes. Dispara recálculo automático de precios si alguno cambia."
                        endpoint="/api/excel/importar-costos"
                        columnas={["CODIGO", "PRODUCTO", "COSTO", "CODIGO EXTERNO", "PROVEEDOR", "TIPO DE PRODUCTO", "ULTIMA ACT. COSTO", "UNIDADES POR BULTO", "PORCENTAJE IVA"]}
                        advertencia="Si el costo, IVA o proveedor cambia, los precios se recalculan automáticamente."
                    />
                </div>
            </section>

            {/* ── Limpieza de Datos (destructiva) ──────────────────────── */}
            <section className="bg-red-50 dark:bg-red-950/20 rounded-xl border border-red-200 dark:border-red-900 p-6">
                <h2 className="text-lg font-bold text-red-900 dark:text-red-300 mb-1 flex items-center gap-2">
                    <ExclamationTriangleIcon className="w-5 h-5" />
                    Limpieza de Datos
                </h2>
                <p className="text-sm text-red-700 dark:text-red-400 mb-5">
                    Vacía las tablas de datos y resetea los IDs a 1. Usar solo durante setup inicial — esta acción es irreversible.
                </p>

                <div className="grid grid-cols-1 max-w-2xl">
                    <LimpiarDatosCard />
                </div>
            </section>

            {/* ── Exportar Precios ─────────────────────────────────────── */}
            <section className="bg-green-50 dark:bg-green-950/30 rounded-xl border border-green-200 dark:border-green-900 p-6">
                <h2 className="text-lg font-bold text-green-900 dark:text-green-300 mb-1 flex items-center gap-2">
                    <ArrowDownTrayIcon className="w-5 h-5" />
                    Exportar Lista de Precios
                </h2>
                <p className="text-sm text-green-700 dark:text-green-400 mb-5">
                    Descarga precios de todos los productos. El formato <strong>Completo</strong> incluye todos los canales y cuotas.
                    Los formatos específicos muestran solo las cuotas configuradas en ese canal.
                </p>

                {/* Formato cards */}
                <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
                    {FORMATOS.map((fmt) => (
                        <FormatoCard
                            key={fmt.formato}
                            fmt={fmt}
                            exportandoPrecios={exportandoPrecios}
                            canales={canales}
                            onExportar={handleExportarPrecios}
                        />
                    ))}
                </div>

                {/* Panel de advertencias */}
                {advertenciasPrecios && advertenciasPrecios.advertencias > 0 && (
                    <div className="mt-2 bg-amber-50 border border-amber-300 rounded-lg p-4 text-sm">
                        <div className="flex items-start justify-between gap-2">
                            <div className="flex items-start gap-2">
                                <span className="text-amber-500 text-base leading-none mt-0.5">⚠️</span>
                                <div>
                                    <p className="font-semibold text-amber-800">
                                        {advertenciasPrecios.advertencias} advertencia{advertenciasPrecios.advertencias > 1 ? "s" : ""} en la exportación
                                    </p>
                                    {advertenciasPrecios.skus.length > 0 ? (
                                        <div className="mt-2 flex flex-wrap gap-1">
                                            {advertenciasPrecios.skus.map((sku) => (
                                                <span key={sku} className="bg-amber-100 border border-amber-300 text-amber-800 text-xs font-mono px-1.5 py-0.5 rounded">
                                                    {sku}
                                                </span>
                                            ))}
                                        </div>
                                    ) : (
                                        <p className="text-amber-700 text-xs mt-1">
                                            Hay productos con datos incompletos (ej: sin MLA asignado). Revisá el log del servidor para ver los SKUs afectados.
                                        </p>
                                    )}
                                </div>
                            </div>
                            <button
                                onClick={() => setAdvertenciasPrecios(null)}
                                className="text-amber-400 hover:text-amber-600 shrink-0 text-lg leading-none"
                            >
                                ×
                            </button>
                        </div>
                    </div>
                )}
            </section>

            {false && (
            <>
            {/* ── Exportar Catálogo ────────────────────────────────────── */}
            <section className="bg-violet-50 dark:bg-violet-950/30 rounded-xl border border-violet-200 dark:border-violet-900 p-6">
                <h2 className="text-lg font-bold text-violet-900 dark:text-violet-300 mb-1 flex items-center gap-2">
                    <ArrowDownTrayIcon className="w-5 h-5" />
                    Exportar Catálogo
                </h2>
                <p className="text-sm text-violet-700 dark:text-violet-400 mb-5">
                    Genera un catálogo con SKU, descripción, PVP y UxB. El header del PVP se construye según la config del catálogo (IVA, recargo, cuotas).
                </p>

                <div className="bg-white dark:bg-slate-800 rounded-lg border border-violet-200 dark:border-slate-600 p-5 flex flex-col gap-4">
                    {/* Campos requeridos */}
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                        <div className="flex flex-col gap-1">
                            <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Catálogo *</label>
                            <select
                                value={catalogoId ?? ""}
                                onChange={(e) => setCatalogoId(e.target.value ? Number(e.target.value) : null)}
                                className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200"
                            >
                                <option value="">Seleccionar catálogo...</option>
                                {catalogos.map((c) => (
                                    <option key={c.id} value={c.id}>{c.label}</option>
                                ))}
                            </select>
                        </div>
                        <div className="flex flex-col gap-1">
                            <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Canal *</label>
                            <select
                                value={canalId ?? ""}
                                onChange={(e) => setCanalId(e.target.value ? Number(e.target.value) : null)}
                                className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200"
                            >
                                <option value="">Seleccionar canal...</option>
                                {canales.map((c) => (
                                    <option key={c.id} value={c.id}>{c.label}</option>
                                ))}
                            </select>
                        </div>
                        <div className="flex flex-col gap-1">
                            <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Cuotas *</label>
                            <select
                                value={cuotasCatalogo}
                                onChange={(e) => setCuotasCatalogo(e.target.value)}
                                className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200"
                            >
                                {(cuotasCatalogoDisp
                                    ? CUOTAS_OPCIONES.filter(op => cuotasCatalogoDisp!.includes(op.value))
                                    : CUOTAS_OPCIONES
                                ).map((op) => (
                                    <option key={op.value} value={op.value}>{op.label}</option>
                                ))}
                            </select>
                        </div>
                    </div>

                    {/* Filtros opcionales */}
                    <div>
                        <button
                            type="button"
                            onClick={() => setMostrarFiltros((v) => !v)}
                            className="flex items-center gap-1.5 text-sm font-medium text-violet-700 dark:text-violet-400 hover:text-violet-900 dark:hover:text-violet-300"
                        >
                            {mostrarFiltros ? <ChevronUpIcon className="w-4 h-4" /> : <ChevronDownIcon className="w-4 h-4" />}
                            Filtros opcionales
                        </button>

                        {mostrarFiltros && (
                            <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 gap-4 border-t border-violet-100 dark:border-slate-600 pt-4">
                                <AsyncSelect
                                    label="Clasif. General"
                                    placeholder="Todas..."
                                    value={clasifGralId ?? undefined}
                                    displayValue={clasifGralLabel}
                                    loadOptions={searchClasifGral}
                                    onChange={(val, label) => { setClasifGralId(val ? Number(val) : null); setClasifGralLabel(label); }}
                                />
                                <AsyncSelect
                                    label="Clasif. Gastro"
                                    placeholder="Todas..."
                                    value={clasifGastroId ?? undefined}
                                    displayValue={clasifGastroLabel}
                                    loadOptions={searchClasifGastro}
                                    onChange={(val, label) => { setClasifGastroId(val ? Number(val) : null); setClasifGastroLabel(label); }}
                                />
                                <AsyncSelect
                                    label="Tipo"
                                    placeholder="Todos..."
                                    value={tipoId ?? undefined}
                                    displayValue={tipoLabel}
                                    loadOptions={searchTipos}
                                    onChange={(val, label) => { setTipoId(val ? Number(val) : null); setTipoLabel(label); }}
                                />
                                <AsyncSelect
                                    label="Marca"
                                    placeholder="Todas..."
                                    value={marcaId ?? undefined}
                                    displayValue={marcaLabel}
                                    loadOptions={searchMarcas}
                                    onChange={(val, label) => { setMarcaId(val ? Number(val) : null); setMarcaLabel(label); }}
                                />

                                <div className="flex flex-col gap-1">
                                    <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Tag</label>
                                    <select
                                        value={tag}
                                        onChange={(e) => setTag(e.target.value as "" | "MAQUINA" | "REPUESTO" | "MENAJE")}
                                        className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200"
                                    >
                                        <option value="">Todos</option>
                                        <option value="MAQUINA">Máquinas</option>
                                        <option value="REPUESTO">Repuestos</option>
                                        <option value="MENAJE">Menaje</option>
                                    </select>
                                </div>

                                <div className="flex flex-col gap-1">
                                    <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Ordenar por</label>
                                    <div className="flex flex-wrap gap-x-4 gap-y-1 mt-1">
                                        {ORDENAR_OPCIONES.map((op) => (
                                            <label key={op.value} className="flex items-center gap-1.5 text-sm text-gray-600 dark:text-slate-300 cursor-pointer">
                                                <input
                                                    type="checkbox"
                                                    checked={ordenarPor.includes(op.value)}
                                                    onChange={() => toggleOrdenarPor(op.value)}
                                                    className="accent-violet-600"
                                                />
                                                {op.label}
                                            </label>
                                        ))}
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>

                    <div className="flex flex-wrap gap-2">
                        <Button
                            text={exportandoCatalogo ? "Exportando..." : "Exportar Catálogo"}
                            variant="dark"
                            onClick={handleExportarCatalogo}
                            disabled={exportandoCatalogo}
                        >
                            <ArrowDownTrayIcon className="w-4 h-4" />
                        </Button>
                        <Button
                            text={exportandoCatalogoPdf ? "Generando PDF..." : "Exportar PDF"}
                            variant="light"
                            onClick={handleExportarCatalogoPdf}
                            disabled={exportandoCatalogoPdf}
                        >
                            <ArrowDownTrayIcon className="w-4 h-4" />
                        </Button>
                    </div>
                </div>
            </section>

            <section className="bg-amber-50 dark:bg-amber-950/30 rounded-xl border border-amber-200 dark:border-amber-900 p-6">
                <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between mb-5">
                    <div>
                        <h2 className="text-lg font-bold text-amber-900 dark:text-amber-300 flex items-center gap-2">
                            <ArrowDownTrayIcon className="w-5 h-5" />
                            Automatizaciones PDF
                        </h2>
                        <p className="text-sm text-amber-700 dark:text-amber-400">
                            Configuraciones persistidas en base de datos para ejecutar catálogos PDF manualmente o desde n8n.
                        </p>
                    </div>
                    <Button
                        text={cargandoConfigsPdf ? "Actualizando..." : "Recargar"}
                        variant="light"
                        onClick={cargarConfigsPdf}
                        disabled={cargandoConfigsPdf}
                    >
                        <ArrowDownTrayIcon className="w-4 h-4" />
                    </Button>
                </div>

                <div className="mb-4 flex justify-end">
                    <Button
                        text="Crear automatización PDF"
                        variant="dark"
                        onClick={openCreateConfigModal}
                    >
                        <PlusIcon className="w-4 h-4" />
                    </Button>
                </div>

                {configsPdf.length === 0 ? (
                    <div className="rounded-lg border border-dashed border-amber-300 dark:border-amber-800 p-4 text-sm text-amber-700 dark:text-amber-300 bg-white/70 dark:bg-slate-800/70">
                        {cargandoConfigsPdf
                            ? "Cargando automatizaciones..."
                            : "No hay configuraciones cargadas todavía."}
                    </div>
                ) : (
                    <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
                        {configsPdf.map((config) => (
                            <div
                                key={config.id}
                                className={`rounded-xl border p-4 bg-white dark:bg-slate-800 ${
                                    config.activo
                                        ? "border-amber-200 dark:border-slate-600"
                                        : "border-slate-200 dark:border-slate-700 opacity-70"
                                }`}
                            >
                                <div className="flex items-start justify-between gap-3">
                                    <div>
                                        <div className="flex items-center gap-2 flex-wrap">
                                            <h3 className="text-base font-semibold text-gray-800 dark:text-slate-100">
                                                {config.nombre}
                                            </h3>
                                            <span className={`text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5 rounded-full border ${
                                                config.activo
                                                    ? "bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-500/10 dark:text-emerald-300 dark:border-emerald-800"
                                                    : "bg-slate-100 text-slate-500 border-slate-200 dark:bg-slate-700 dark:text-slate-300 dark:border-slate-600"
                                            }`}>
                                                {config.activo ? "Activa" : "Inactiva"}
                                            </span>
                                        </div>
                                    </div>
                                    <Button
                                        text={ejecutandoConfigSlug === config.id ? "Generando..." : "Generar PDF"}
                                        variant="dark"
                                        onClick={() => handleEjecutarCatalogoPdfAutomatico(config)}
                                        disabled={!config.activo || ejecutandoConfigSlug !== null}
                                    >
                                        <ArrowDownTrayIcon className="w-4 h-4" />
                                    </Button>
                                </div>

                                <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
                                    <div className="rounded-lg bg-amber-50/70 dark:bg-amber-500/5 border border-amber-100 dark:border-amber-900/60 p-3">
                                        <div className="text-[11px] uppercase tracking-wide text-amber-700 dark:text-amber-300 font-semibold">Canal</div>
                                        <div className="text-gray-800 dark:text-slate-100 mt-1">{config.canal}</div>
                                    </div>
                                    <div className="rounded-lg bg-amber-50/70 dark:bg-amber-500/5 border border-amber-100 dark:border-amber-900/60 p-3">
                                        <div className="text-[11px] uppercase tracking-wide text-amber-700 dark:text-amber-300 font-semibold">Catálogo</div>
                                        <div className="text-gray-800 dark:text-slate-100 mt-1">{config.catalogo}</div>
                                    </div>
                                    <div className="rounded-lg bg-gray-50 dark:bg-slate-700/40 border border-gray-200 dark:border-slate-600 p-3">
                                        <div className="text-[11px] uppercase tracking-wide text-gray-500 dark:text-slate-400 font-semibold">Cuotas</div>
                                        <div className="text-gray-800 dark:text-slate-100 mt-1">{config.cuotas}</div>
                                    </div>
                                    <div className="rounded-lg bg-gray-50 dark:bg-slate-700/40 border border-gray-200 dark:border-slate-600 p-3">
                                        <div className="text-[11px] uppercase tracking-wide text-gray-500 dark:text-slate-400 font-semibold">Productos por página</div>
                                        <div className="text-gray-800 dark:text-slate-100 mt-1">{config.productosPorPagina}</div>
                                    </div>
                                </div>

                                <div className="mt-3 flex flex-wrap gap-2 text-xs">
                                    {config.clasificacion && (
                                        <span className="px-2 py-1 rounded-full bg-violet-50 text-violet-700 border border-violet-200 dark:bg-violet-500/10 dark:text-violet-300 dark:border-violet-800">
                                            Clasificación: {config.clasificacion}
                                        </span>
                                    )}
                                    {config.estetica && (
                                        <span className="px-2 py-1 rounded-full bg-sky-50 text-sky-700 border border-sky-200 dark:bg-sky-500/10 dark:text-sky-300 dark:border-sky-800">
                                            Estética: {config.estetica}
                                        </span>
                                    )}
                                    {config.tipoDocumento && (
                                        <span className="px-2 py-1 rounded-full bg-indigo-50 text-indigo-700 border border-indigo-200 dark:bg-indigo-500/10 dark:text-indigo-300 dark:border-indigo-800">
                                            {config.tipoDocumento}
                                        </span>
                                    )}
                                    <span className="px-2 py-1 rounded-full bg-orange-50 text-orange-700 border border-orange-200 dark:bg-orange-500/10 dark:text-orange-300 dark:border-orange-800">
                                        Carátula: {config.caratula ? "Sí" : "No"}
                                    </span>
                                </div>

                                {config.titulo && (
                                    <p className="mt-3 text-sm text-gray-600 dark:text-slate-300">
                                        <span className="font-medium">Título:</span> {config.titulo}
                                    </p>
                                )}
                                {config.ubicacionSalida && (
                                    <p className="mt-2 text-xs text-gray-500 dark:text-slate-400 break-all">
                                        <span className="font-medium">Ubicación:</span> {config.ubicacionSalida}
                                    </p>
                                )}

                                <div className="mt-4 flex flex-wrap gap-2">
                                    <Button
                                        text="Editar"
                                        variant="light"
                                        onClick={() => openEditConfigModal(config)}
                                        disabled={ejecutandoConfigSlug !== null}
                                    >
                                        <PencilSquareIcon className="w-4 h-4" />
                                    </Button>
                                    <Button
                                        text={eliminandoConfigId === config.id ? "Borrando..." : "Eliminar"}
                                        variant="danger"
                                        onClick={() => handleEliminarConfig(config)}
                                        disabled={eliminandoConfigId !== null || ejecutandoConfigSlug !== null}
                                    >
                                        <TrashIcon className="w-4 h-4" />
                                    </Button>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </section>

            <Modal
                isOpen={configModalOpen}
                onClose={closeConfigModal}
                title={editingConfigId ? "Editar automatización PDF" : "Nueva automatización PDF"}
                size="xl"
                footer={
                    <>
                        <Button text="Cancelar" variant="light" onClick={closeConfigModal} disabled={guardandoConfig} />
                        <Button
                            text={guardandoConfig ? (editingConfigId ? "Guardando cambios..." : "Creando automatización PDF...") : editingConfigId ? "Guardar cambios" : "Crear automatización PDF"}
                            variant="dark"
                            onClick={handleGuardarConfig}
                            disabled={guardandoConfig}
                        />
                    </>
                }
            >
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div className="flex flex-col gap-1 md:col-span-2">
                        <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Nombre *</label>
                        <input
                            value={configForm.nombre}
                            onChange={(e) => updateConfigField("nombre", e.target.value)}
                            className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200"
                            placeholder="Mayorista · LG Gastro"
                            autoFocus
                        />
                    </div>
                    <div className="flex flex-col gap-1">
                        <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Canal *</label>
                        <select
                            value={configForm.canal}
                            onChange={(e) => updateConfigField("canal", e.target.value)}
                            className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200"
                        >
                            <option value="">Seleccionar canal...</option>
                            {configForm.canal && !canales.some((canal) => canal.label === configForm.canal) && (
                                <option value={configForm.canal}>{configForm.canal}</option>
                            )}
                            {canales.map((canal) => (
                                <option key={canal.id} value={canal.label}>{canal.label}</option>
                            ))}
                        </select>
                    </div>
                    <div className="flex flex-col gap-1">
                        <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Catálogo *</label>
                        <select
                            value={configForm.catalogo}
                            onChange={(e) => updateConfigField("catalogo", e.target.value)}
                            className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200"
                        >
                            <option value="">Seleccionar catálogo...</option>
                            {configForm.catalogo && !catalogos.some((catalogo) => catalogo.label === configForm.catalogo) && (
                                <option value={configForm.catalogo}>{configForm.catalogo}</option>
                            )}
                            {catalogos.map((catalogo) => (
                                <option key={catalogo.id} value={catalogo.label}>{catalogo.label}</option>
                            ))}
                        </select>
                    </div>
                    <div className="flex flex-col gap-1">
                        <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Cuotas *</label>
                        <select
                            value={configForm.cuotas}
                            onChange={(e) => updateConfigField("cuotas", e.target.value)}
                            className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200"
                        >
                            {(modalCuotasDisp ? CUOTAS_OPCIONES.filter((op) => modalCuotasDisp!.includes(op.value)) : CUOTAS_OPCIONES).map((op) => (
                                <option key={op.value} value={op.value}>{op.label}</option>
                            ))}
                        </select>
                        {cargandoModalCuotas && (
                            <span className="text-xs text-gray-400 dark:text-slate-500">Cargando cuotas del canal...</span>
                        )}
                    </div>
                    <div className="flex flex-col gap-1">
                        <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Clasificación</label>
                        <input
                            value={configForm.clasificacion}
                            onChange={(e) => updateConfigField("clasificacion", e.target.value)}
                            className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200"
                            placeholder="COCCION"
                        />
                    </div>
                    <div className="flex flex-col gap-1">
                        <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Título</label>
                        <input
                            value={configForm.titulo}
                            onChange={(e) => updateConfigField("titulo", e.target.value)}
                            className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200"
                            placeholder="GASTRONOMIA"
                        />
                    </div>
                    <div className="flex flex-col gap-1">
                        <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Estética</label>
                        <input
                            value={configForm.estetica}
                            onChange={(e) => updateConfigField("estetica", e.target.value)}
                            className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200"
                            placeholder="KT / LINEA GE"
                        />
                    </div>
                    <div className="flex flex-col gap-1">
                        <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Tipo de documento</label>
                        <select
                            value={configForm.tipoDocumento}
                            onChange={(e) => updateConfigField("tipoDocumento", e.target.value)}
                            className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200"
                        >
                            <option value="CATALOGO">CATALOGO</option>
                            <option value="PRESUPUESTO">PRESUPUESTO</option>
                        </select>
                    </div>
                    <div className="flex flex-col gap-1">
                        <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Productos por página *</label>
                        <input
                            type="number"
                            min="1"
                            value={configForm.productosPorPagina}
                            onChange={(e) => updateConfigField("productosPorPagina", e.target.value)}
                            className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200"
                        />
                    </div>
                    <div className="md:col-span-2 flex flex-col gap-1">
                        <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Ubicación de salida</label>
                        <input
                            value={configForm.ubicacionSalida}
                            onChange={(e) => updateConfigField("ubicacionSalida", e.target.value)}
                            className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200"
                            placeholder="G:\\Mi unidad\\Catalogos Mayoristas"
                        />
                    </div>
                </div>

                <div className="mt-5 flex flex-wrap gap-6">
                    <label className="flex items-center gap-2 text-sm text-gray-700 dark:text-slate-300">
                        <input
                            type="checkbox"
                            checked={configForm.caratula}
                            onChange={(e) => updateConfigField("caratula", e.target.checked)}
                            className="accent-violet-600"
                        />
                        Incluir carátula
                    </label>
                    <label className="flex items-center gap-2 text-sm text-gray-700 dark:text-slate-300">
                        <input
                            type="checkbox"
                            checked={configForm.activo}
                            onChange={(e) => updateConfigField("activo", e.target.checked)}
                            className="accent-violet-600"
                        />
                        Configuración activa
                    </label>
                </div>
            </Modal>
            </>
            )}

        </main>
    );
}

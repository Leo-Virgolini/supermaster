"use client";

import { notificar } from "../utils/notificar";
import ErrorBanner from "../components/ErrorBanner/ErrorBanner";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
    ClipboardDocumentIcon,
    ArrowPathIcon,
    ArrowDownTrayIcon,
    FunnelIcon,
    UserCircleIcon,
    EyeIcon,
    ChevronUpDownIcon,
    ChevronUpIcon,
    ChevronDownIcon,
    XMarkIcon,
} from "@heroicons/react/24/outline";
import SearchInput from "../components/SearchInput/SearchInput";
import Button from "../components/Button/Button";
import PaginationControls from "../components/Table/core/PaginationControls";
import { getAuditoriaGlobalAPI } from "./auditoriaService";
import type { AuditoriaCambioDTO } from "./types";
import { formatFechaAR } from "../utils/formatDate";
import { exportToExcel } from "../utils/exportCSV";

type SortField = "fechaHora" | "entidad" | "entidadCodigo" | "usuarioNombreCompleto" | "accion" | "campo" | "origen";
type SortDirection = "asc" | "desc";

const fieldLabelMap: Record<string, string> = {
    canal: "Canal",
    canalBase: "Canal base",
    concepto: "Concepto",
    tipoRegla: "Tipo de regla",
    porcentaje: "Porcentaje",
    proveedor: "Proveedor",
    apodo: "Alias",
    username: "Usuario",
    nombreCompleto: "Nombre completo",
    rol: "Rol",
    password: "Contraseña",
    estado: "Estado",
    observaciones: "Observaciones",
    lineas: "Líneas",
    mesesCobertura: "Meses cobertura",
    pesoMes1: "Peso mes 1",
    pesoMes2: "Peso mes 2",
    pesoMes3: "Peso mes 3",
    umbralEnvioGratis: "Umbral envío gratis",
    tier1Hasta: "Tier 1 hasta",
    tier1Costo: "Tier 1 costo",
    tier2Hasta: "Tier 2 hasta",
    tier2Costo: "Tier 2 costo",
    tier3Costo: "Tier 3 costo",
    idEmpresaDux: "Empresa DUX",
    idsSucursalDux: "Sucursales DUX",
    calculo: "Cálculo",
    ajustesPedidos: "Ajustes de pedidos",
    ordenesGeneradas: "Órdenes generadas",
    proveedorFiltro: "Proveedor filtro",
    plazoPago: "Plazo de pago",
    entrega: "Entrega",
    financiacionPorcentaje: "Financiación %",
    leadTimeDias: "Lead time",
    segmento: "Segmento",
    sku: "SKU",
    codExt: "Cód. Ext.",
    descripcion: "Descripción",
    tituloDux: "Título Dux",
    tituloMl: "Título ML",
    tituloNube: "Título Nube",
    esCombo: "Es combo",
    uxb: "UxB",
    moq: "MOQ",
    imagenUrl: "Imagen",
    stock: "Stock",
    activo: "Activo",
    tagReposicion: "Tag reposición",
    costo: "Costo",
    iva: "IVA",
    marca: "Marca",
    origen: "Origen",
    clasifGral: "Clasif. general",
    clasifGastro: "Clasif. gastro",
    catalogo: "Catálogo",
    tipo: "Tipo",
    montoMinimo: "Monto mínimo",
    descuentoPorcentaje: "Descuento %",
    prioridad: "Prioridad",
    material: "Material",
    mla: "MLA",
    mlau: "MLAU",
    clave: "Clave",
    precioEnvio: "Precio envío",
    comisionPorcentaje: "Comisión %",
    topePromocion: "Tope promoción",
    valor: "Valor",
    caratula: "Carátula",
    ordenarPor: "Ordenar por",
    productosPorPagina: "Productos por página",
    ubicacionSalida: "Ubicación de salida",
    capacidad: "Capacidad",
    largo: "Largo",
    ancho: "Ancho",
    alto: "Alto",
    diamboca: "Diam. boca",
    diambase: "Diam. base",
    espesor: "Espesor",
};

const entityLabelMap: Record<string, string> = {
    PRODUCTO: "Producto",
    PRODUCTO_MARGEN: "Margen de producto",
    PRODUCTO_CANAL_PRECIO_INFLADO: "Precio inflado de producto",
    PRODUCTO_APTO: "Apto de producto",
    PRODUCTO_CATALOGO: "Catálogo de producto",
    PRODUCTO_SEGMENTO: "Segmento de producto",
    PROVEEDOR: "Proveedor",
    SEGMENTO: "Segmento",
    CANAL: "Canal",
    CONCEPTO_CALCULO: "Concepto cálculo",
    CANAL_CONCEPTO: "Concepto en canal",
    CANAL_CONCEPTO_CUOTA: "Cuota por canal",
    CANAL_CONCEPTO_REGLA: "Regla de excepción",
    CANAL_REGLA: "Regla de canal",
    REGLA_DESCUENTO: "Regla descuento",
    PRECIO_INFLADO: "Precio inflado",
    MLA: "MLA",
    CATALOGO: "Catálogo",
    MARCA: "Marca",
    TIPO: "Tipo",
    MATERIAL: "Material",
    ORIGEN: "Origen",
    CLASIF_GRAL: "Clasif. general",
    CLASIF_GASTRO: "Clasif. gastro",
    APTO: "Apto",
    USUARIO: "Usuario",
    ORDEN_COMPRA: "Orden de compra",
    REPOSICION: "Reposición",
    CONFIGURACION_ML: "Configuración Envíos ML",
    CONFIG_AUTOMATIZACION: "Config. automatización",
    CATALOGO_PDF_CONFIG: "Config. catálogo PDF",
    CATALOGO_PDF_GLOBAL_CONFIG: "Config. global catálogo PDF",
    AUTH: "Autenticación",
    RECALCULO: "Recálculo de precios",
};

const actionLabelMap: Record<string, string> = {
    CREATE: "Alta",
    UPDATE: "Edición",
    DELETE: "Baja",
};

const originLabelMap: Record<string, string> = {
    FORM: "Formulario",
    INLINE: "Edición inline",
    TABLE: "Tabla",
    MONITOR_PRECIOS: "Monitor de precios",
    PROCESS: "Proceso",
    MANUAL: "Manual",
    API: "API",
    SYSTEM: "Sistema",
};

const entidadOptions = [
    { value: "", label: "Todas las entidades" },
    { value: "PRODUCTO", label: "Productos" },
    { value: "PRODUCTO_MARGEN", label: "Márgenes de producto" },
    { value: "PRODUCTO_CANAL_PRECIO_INFLADO", label: "Precios inflados de producto" },
    { value: "PRODUCTO_APTO", label: "Aptos de producto" },
    { value: "PRODUCTO_CATALOGO", label: "Catálogos de producto" },
    { value: "PRODUCTO_SEGMENTO", label: "Segmentos de producto" },
    { value: "PROVEEDOR", label: "Proveedores" },
    { value: "SEGMENTO", label: "Segmentos" },
    { value: "CANAL", label: "Canales" },
    { value: "CONCEPTO_CALCULO", label: "Conceptos cálculo" },
    { value: "CANAL_CONCEPTO", label: "Conceptos en canal" },
    { value: "CANAL_CONCEPTO_CUOTA", label: "Cuotas por canal" },
    { value: "CANAL_CONCEPTO_REGLA", label: "Reglas de excepción" },
    { value: "CANAL_REGLA", label: "Reglas de canal" },
    { value: "REGLA_DESCUENTO", label: "Reglas descuento" },
    { value: "PRECIO_INFLADO", label: "Precios inflados" },
    { value: "MLA", label: "MLAs" },
    { value: "CATALOGO", label: "Catálogos" },
    { value: "MARCA", label: "Marcas" },
    { value: "TIPO", label: "Tipos" },
    { value: "MATERIAL", label: "Materiales" },
    { value: "ORIGEN", label: "Orígenes" },
    { value: "CLASIF_GRAL", label: "Clasif. generales" },
    { value: "CLASIF_GASTRO", label: "Clasif. gastro" },
    { value: "APTO", label: "Aptos" },
    { value: "USUARIO", label: "Usuarios" },
    { value: "ORDEN_COMPRA", label: "Órdenes de compra" },
    { value: "REPOSICION", label: "Reposición" },
    { value: "CONFIGURACION_ML", label: "Configuración Envíos ML" },
    { value: "CONFIG_AUTOMATIZACION", label: "Config. automatización" },
    { value: "CATALOGO_PDF_CONFIG", label: "Config. catálogo PDF" },
    { value: "CATALOGO_PDF_GLOBAL_CONFIG", label: "Config. global catálogo PDF" },
    { value: "AUTH", label: "Autenticación" },
    { value: "RECALCULO", label: "Recálculo de precios" },
];
const accionOptions = [
    { value: "", label: "Todas las acciones" },
    { value: "CREATE", label: "Altas" },
    { value: "UPDATE", label: "Ediciones" },
    { value: "DELETE", label: "Bajas" },
];
const origenOptions = [
    { value: "", label: "Todos los orígenes" },
    { value: "FORM", label: "Formulario" },
    { value: "INLINE", label: "Edición inline" },
    { value: "TABLE", label: "Tabla" },
    { value: "MONITOR_PRECIOS", label: "Monitor de precios" },
    { value: "PROCESS", label: "Proceso" },
    { value: "MANUAL", label: "Manual" },
    { value: "API", label: "API" },
    { value: "SYSTEM", label: "Sistema" },
];

function AuditActionBadge({ action }: { action: string }) {
    const classes =
        action === "CREATE"
            ? "bg-emerald-50 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-200"
            : action === "DELETE"
                ? "bg-red-50 text-red-700 dark:bg-red-500/15 dark:text-red-200"
                : "bg-blue-50 text-blue-700 dark:bg-blue-500/15 dark:text-blue-200";

    return (
        <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-semibold ${classes}`}>
            {actionLabelMap[action] || action}
        </span>
    );
}

function AuditOriginBadge({ origin }: { origin: string }) {
    const normalized = origin || "API";
    const classes =
        normalized === "FORM"
            ? "bg-sky-50 text-sky-700 dark:bg-sky-500/15 dark:text-sky-200"
            : normalized === "INLINE"
                ? "bg-violet-50 text-violet-700 dark:bg-violet-500/15 dark:text-violet-200"
                : normalized === "TABLE"
                    ? "bg-slate-100 text-slate-700 dark:bg-slate-700/80 dark:text-slate-200"
                    : normalized === "MONITOR_PRECIOS"
                        ? "bg-emerald-50 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-200"
                        : normalized === "PROCESS"
                            ? "bg-amber-50 text-amber-700 dark:bg-amber-500/15 dark:text-amber-200"
                            : normalized === "SYSTEM"
                                ? "bg-rose-50 text-rose-700 dark:bg-rose-500/15 dark:text-rose-200"
                                : "bg-slate-100 text-slate-700 dark:bg-slate-700/80 dark:text-slate-200";

    return (
        <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${classes}`}>
            {originLabelMap[normalized] || normalized}
        </span>
    );
}

export default function AuditoriaPage() {
    const searchParams = useSearchParams();
    const [items, setItems] = useState<AuditoriaCambioDTO[]>([]);
    const [pageIndex, setPageIndex] = useState(0);
    const [pageSize, setPageSize] = useState(100);
    // Refs para preservar el scroll horizontal/vertical al paginar.
    const scrollContainerRef = useRef<HTMLDivElement>(null);
    const savedScrollPos = useRef<{ left: number; top: number } | null>(null);
    const saveScrollPosition = () => {
        if (scrollContainerRef.current) {
            savedScrollPos.current = {
                left: scrollContainerRef.current.scrollLeft,
                top: scrollContainerRef.current.scrollTop,
            };
        }
    };
    const [totalRecords, setTotalRecords] = useState(0);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [search, setSearch] = useState(() => searchParams.get("search") ?? "");
    const [usuario, setUsuario] = useState(() => searchParams.get("usuario") ?? "");
    const [entidad, setEntidad] = useState(() => searchParams.get("entidad") ?? "");
    const [accion, setAccion] = useState(() => searchParams.get("accion") ?? "");
    const [campo, setCampo] = useState(() => searchParams.get("campo") ?? "");
    const [origen, setOrigen] = useState(() => searchParams.get("origen") ?? "");
    const [fechaDesde, setFechaDesde] = useState(() => searchParams.get("fechaDesde") ?? "");
    const [fechaHasta, setFechaHasta] = useState(() => searchParams.get("fechaHasta") ?? "");
    const [showFilters, setShowFilters] = useState(false);
    const [sortField, setSortField] = useState<SortField>("fechaHora");
    const [sortDirection, setSortDirection] = useState<SortDirection>("desc");

    const pageCount = Math.max(1, Math.ceil(totalRecords / pageSize));
    const sortLabelMap: Record<SortField, string> = {
        fechaHora: "Fecha",
        entidad: "Entidad",
        entidadCodigo: "Código",
        usuarioNombreCompleto: "Usuario",
        accion: "Acción",
        campo: "Campo",
        origen: "Origen",
    };

    const load = useCallback(async () => {
        if (fechaDesde && fechaHasta && fechaDesde > fechaHasta) {
            notificar.warning("La fecha 'desde' no puede ser posterior a 'hasta'.");
            return;
        }
        setIsLoading(true);
        setError(null);
        try {
            const response = await getAuditoriaGlobalAPI({
                page: pageIndex,
                size: pageSize,
                sort: `${sortField},${sortDirection}`,
                search,
                usuario,
                entidad,
                accion,
                campo,
                origen,
                fechaDesde,
                fechaHasta,
            });
            setItems(response.content ?? []);
            setTotalRecords(response.page?.totalElements ?? response.content?.length ?? 0);
        } catch (e) {
            setError(e instanceof Error ? e.message : "No se pudo cargar la auditoría.");
            setItems([]);
        } finally {
            setIsLoading(false);
        }
    }, [accion, campo, entidad, fechaDesde, fechaHasta, origen, pageIndex, pageSize, search, sortDirection, sortField, usuario]);

    useEffect(() => {
        void load();
    }, [load]);

    // Restaurar la posición del scroll después de que los items cambien
    // (cambio de página / pageSize). Solo restaura si saveScrollPosition() guardó algo.
    useEffect(() => {
        if (savedScrollPos.current !== null && scrollContainerRef.current) {
            scrollContainerRef.current.scrollLeft = savedScrollPos.current.left;
            scrollContainerRef.current.scrollTop = savedScrollPos.current.top;
            savedScrollPos.current = null;
        }
    }, [items]);

    useEffect(() => {
        const nextSearch = searchParams.get("search") ?? "";
        const nextUsuario = searchParams.get("usuario") ?? "";
        const nextEntidad = searchParams.get("entidad") ?? "";
        const nextAccion = searchParams.get("accion") ?? "";
        const nextCampo = searchParams.get("campo") ?? "";
        const nextOrigen = searchParams.get("origen") ?? "";
        const nextFechaDesde = searchParams.get("fechaDesde") ?? "";
        const nextFechaHasta = searchParams.get("fechaHasta") ?? "";

        setSearch((current) => current === nextSearch ? current : nextSearch);
        setUsuario((current) => current === nextUsuario ? current : nextUsuario);
        setEntidad((current) => current === nextEntidad ? current : nextEntidad);
        setAccion((current) => current === nextAccion ? current : nextAccion);
        setCampo((current) => current === nextCampo ? current : nextCampo);
        setOrigen((current) => current === nextOrigen ? current : nextOrigen);
        setFechaDesde((current) => current === nextFechaDesde ? current : nextFechaDesde);
        setFechaHasta((current) => current === nextFechaHasta ? current : nextFechaHasta);
        setPageIndex(0);
    }, [searchParams]);

    const activeFilterCount = useMemo(
        () => [search, usuario, entidad, accion, campo, origen, fechaDesde, fechaHasta].filter((value) => value && value.trim() !== "").length,
        [accion, campo, entidad, fechaDesde, fechaHasta, origen, search, usuario],
    );

    const clearFilters = () => {
        setSearch("");
        setUsuario("");
        setEntidad("");
        setAccion("");
        setCampo("");
        setOrigen("");
        setFechaDesde("");
        setFechaHasta("");
        setPageIndex(0);
    };

    const filterChips = useMemo(() => {
        const chips: Array<{ key: string; label: string; value: string }> = [];
        if (search.trim()) chips.push({ key: "search", label: "Búsqueda", value: search.trim() });
        if (entidad) chips.push({ key: "entidad", label: "Entidad", value: entidadOptions.find((option) => option.value === entidad)?.label ?? entidad });
        if (usuario.trim()) chips.push({ key: "usuario", label: "Usuario", value: usuario.trim() });
        if (accion) chips.push({ key: "accion", label: "Acción", value: accionOptions.find((option) => option.value === accion)?.label ?? accion });
        if (campo.trim()) chips.push({ key: "campo", label: "Campo", value: fieldLabelMap[campo.trim()] || campo.trim() });
        if (origen) chips.push({ key: "origen", label: "Origen", value: origenOptions.find((option) => option.value === origen)?.label ?? origen });
        if (fechaDesde) chips.push({ key: "fechaDesde", label: "Desde", value: fechaDesde });
        if (fechaHasta) chips.push({ key: "fechaHasta", label: "Hasta", value: fechaHasta });
        return chips;
    }, [accion, campo, entidad, fechaDesde, fechaHasta, origen, search, usuario]);

    const clearFilterChip = (key: string) => {
        setPageIndex(0);
        switch (key) {
            case "search":
                setSearch("");
                break;
            case "entidad":
                setEntidad("");
                break;
            case "usuario":
                setUsuario("");
                break;
            case "accion":
                setAccion("");
                break;
            case "campo":
                setCampo("");
                break;
            case "origen":
                setOrigen("");
                break;
            case "fechaDesde":
                setFechaDesde("");
                break;
            case "fechaHasta":
                setFechaHasta("");
                break;
        }
    };

    const toggleSort = (field: SortField) => {
        setPageIndex(0);
        if (sortField === field) {
            setSortDirection((current) => (current === "asc" ? "desc" : "asc"));
            return;
        }
        setSortField(field);
        setSortDirection(field === "fechaHora" ? "desc" : "asc");
    };

    const renderSortIcon = (field: SortField) => {
        if (sortField !== field) {
            return <ChevronUpDownIcon className="h-3.5 w-3.5 text-slate-400" />;
        }

        return sortDirection === "asc"
            ? <ChevronUpIcon className="h-3.5 w-3.5 text-blue-600" />
            : <ChevronDownIcon className="h-3.5 w-3.5 text-blue-600" />;
    };

    // Si el valor matchea un patrón numérico, lo formatea con locale es-AR
    // (punto miles, coma decimal). Si no, lo devuelve tal cual.
    const formatValor = (value: string): string => {
        if (/^-?\d+(\.\d+)?$/.test(value)) {
            const num = Number(value);
            if (!Number.isNaN(num)) {
                return num.toLocaleString("es-AR", {
                    minimumFractionDigits: 0,
                    maximumFractionDigits: 6,
                });
            }
        }
        return value;
    };

    const renderAntes = (value: string | null | undefined) => {
        if (!value) return <span className="text-slate-400 dark:text-slate-500">—</span>;
        return (
            <span className="inline-flex items-center gap-1 break-words rounded-md bg-red-50 px-1.5 py-0.5 text-red-700 dark:bg-red-500/10 dark:text-red-300">
                {formatValor(value)}
            </span>
        );
    };

    const renderDespues = (value: string | null | undefined) => {
        if (!value) return <span className="text-slate-400 dark:text-slate-500">—</span>;
        return (
            <span className="inline-flex items-center gap-1 break-words rounded-md bg-emerald-50 px-1.5 py-0.5 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300">
                {formatValor(value)}
            </span>
        );
    };

    const renderEntityLink = (item: AuditoriaCambioDTO) => {
        const code = item.entidadCodigo || (item.entidadId != null ? String(item.entidadId) : "—");

        if (item.entidad === "PRODUCTO" && item.entidadCodigo && !item.entidadCodigo.startsWith("RECALCULO")) {
            return (
                <Link
                    href={`/productos?search=${encodeURIComponent(code)}`}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-blue-200 bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700 transition hover:bg-blue-100 dark:border-blue-500/30 dark:bg-blue-500/15 dark:text-blue-200 dark:hover:bg-blue-500/25"
                >
                    <EyeIcon className="h-3.5 w-3.5" />
                    Ir al producto
                </Link>
            );
        }

        if (item.entidad === "PROVEEDOR") {
            return (
                <Link
                    href={`/proveedores?search=${encodeURIComponent(code)}`}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-cyan-200 bg-cyan-50 px-2.5 py-1 text-xs font-medium text-cyan-700 transition hover:bg-cyan-100 dark:border-cyan-500/30 dark:bg-cyan-500/15 dark:text-cyan-200 dark:hover:bg-cyan-500/25"
                >
                    <EyeIcon className="h-3.5 w-3.5" />
                    Ir al proveedor
                </Link>
            );
        }

        if (item.entidad === "SEGMENTO") {
            return (
                <Link
                    href={`/segmentos?search=${encodeURIComponent(code)}`}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-indigo-200 bg-indigo-50 px-2.5 py-1 text-xs font-medium text-indigo-700 transition hover:bg-indigo-100 dark:border-indigo-500/30 dark:bg-indigo-500/15 dark:text-indigo-200 dark:hover:bg-indigo-500/25"
                >
                    <EyeIcon className="h-3.5 w-3.5" />
                    Ir al segmento
                </Link>
            );
        }

        if (item.entidad === "MLA") {
            return (
                <Link
                    href={`/mlas?search=${encodeURIComponent(code)}`}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-emerald-200 bg-emerald-50 px-2.5 py-1 text-xs font-medium text-emerald-700 transition hover:bg-emerald-100 dark:border-emerald-500/30 dark:bg-emerald-500/15 dark:text-emerald-200 dark:hover:bg-emerald-500/25"
                >
                    <EyeIcon className="h-3.5 w-3.5" />
                    Ir al MLA
                </Link>
            );
        }

        return <span className="text-slate-400 dark:text-slate-500">—</span>;
    };

    const handleExport = async () => {
        try {
            const response = await getAuditoriaGlobalAPI({
                page: 0,
                size: Math.max(totalRecords, pageSize),
                sort: `${sortField},${sortDirection}`,
                search,
                usuario,
                entidad,
                accion,
                campo,
                origen,
                fechaDesde,
                fechaHasta,
            });

            const exportRows = (response.content ?? []).map((item) => ({
                fecha: formatFechaAR(item.fechaHora),
                entidad: entityLabelMap[item.entidad] || item.entidad,
                codigo: item.entidadCodigo || (item.entidadId != null ? `#${item.entidadId}` : "—"),
                usuario: item.usuarioNombreCompleto || item.usuarioUsername || "Sistema",
                accion: actionLabelMap[item.accion] || item.accion,
                campo: fieldLabelMap[item.campo] || item.campo,
                antes: item.valorAnterior || "—",
                despues: item.valorNuevo || "—",
                origen: originLabelMap[item.origen || "API"] || item.origen || "API",
                registro:
                    item.entidad === "PRODUCTO" ? "Producto" :
                    item.entidad === "PROVEEDOR" ? "Proveedor" :
                    item.entidad === "SEGMENTO" ? "Segmento" :
                    "—",
            }));

            exportToExcel(exportRows, [
                { header: "Fecha", accessor: "fecha" },
                { header: "Entidad", accessor: "entidad" },
                { header: "Código", accessor: "codigo" },
                { header: "Usuario", accessor: "usuario" },
                { header: "Acción", accessor: "accion" },
                { header: "Campo", accessor: "campo" },
                { header: "Antes", accessor: "antes" },
                { header: "Después", accessor: "despues" },
                { header: "Origen", accessor: "origen" },
                { header: "Registro", accessor: "registro" },
            ], "auditoria");
            notificar.success(`${exportRows.length} registro(s) exportados`);
        } catch {
            notificar.error("No se pudo exportar la auditoría.");
        }
    };

    return (
        <main className="flex min-h-0 flex-1 flex-col gap-4 p-4 md:p-5">
            <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                    <h1 className="flex items-center gap-2 text-3xl font-bold text-slate-800 dark:text-slate-100">
                        <ClipboardDocumentIcon className="h-8 w-8 text-slate-600 dark:text-slate-400" />
                        Auditoría
                    </h1>
                    <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                        Historial global de cambios auditados con usuario, origen y valores antes/después.
                    </p>
                </div>
                <Button variant="outline" text="Actualizar" onClick={() => void load()}>
                    <ArrowPathIcon className={`h-4 w-4 ${isLoading ? "animate-spin" : ""}`} />
                </Button>
            </div>

            <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-black/20">
                <div className="flex flex-wrap items-center gap-3">
                    <SearchInput
                        initialValue={search}
                        onSearch={(value) => {
                            setSearch(value);
                            setPageIndex(0);
                        }}
                        placeholder="Buscar auditoría por código, usuario, campo o valores antes/después..."
                        className="w-[30rem] max-w-full"
                        autoFocus={false}
                    />
                    <Button
                        variant={showFilters || activeFilterCount > 0 ? "outline" : "light"}
                        text="Filtros"
                        onClick={() => setShowFilters((current) => !current)}
                    >
                        <FunnelIcon className="h-4 w-4" />
                    </Button>
                    <Button variant="light" text="Limpiar filtros" onClick={clearFilters} disabled={activeFilterCount === 0} />
                </div>

                {(showFilters || activeFilterCount > 0) && (
                    <div className="mt-3 grid gap-3 border-t border-slate-200 pt-3 dark:border-slate-700 lg:grid-cols-[repeat(7,minmax(0,1fr))]">
                        <label className="flex min-w-0 flex-col gap-1 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                            Entidad
                            <select
                                value={entidad}
                                onChange={(e) => {
                                    setEntidad(e.target.value);
                                    setPageIndex(0);
                                }}
                                className="rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm font-normal tracking-normal text-slate-700 outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-100 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-500/20"
                            >
                                {entidadOptions.map((option) => (
                                    <option key={option.value || "all"} value={option.value}>{option.label}</option>
                                ))}
                            </select>
                        </label>

                        <label className="flex min-w-0 flex-col gap-1 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                            Usuario
                            <input
                                type="text"
                                value={usuario}
                                onChange={(e) => {
                                    setUsuario(e.target.value);
                                    setPageIndex(0);
                                }}
                                placeholder="Usuario o nombre..."
                                className="rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm font-normal tracking-normal text-slate-700 outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-100 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-500/20"
                            />
                        </label>

                        <label className="flex min-w-0 flex-col gap-1 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                            Acción
                            <select
                                value={accion}
                                onChange={(e) => {
                                    setAccion(e.target.value);
                                    setPageIndex(0);
                                }}
                                className="rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm font-normal tracking-normal text-slate-700 outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-100 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-500/20"
                            >
                                {accionOptions.map((option) => (
                                    <option key={option.value || "all"} value={option.value}>{option.label}</option>
                                ))}
                            </select>
                        </label>

                        <label className="flex min-w-0 flex-col gap-1 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                            Campo
                            <input
                                type="text"
                                value={campo}
                                onChange={(e) => {
                                    setCampo(e.target.value);
                                    setPageIndex(0);
                                }}
                                placeholder="Campo puntual..."
                                className="rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm font-normal tracking-normal text-slate-700 outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-100 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-500/20"
                            />
                        </label>

                        <label className="flex min-w-0 flex-col gap-1 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                            Origen
                            <select
                                value={origen}
                                onChange={(e) => {
                                    setOrigen(e.target.value);
                                    setPageIndex(0);
                                }}
                                className="rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm font-normal tracking-normal text-slate-700 outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-100 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-500/20"
                            >
                                {origenOptions.map((option) => (
                                    <option key={option.value || "all"} value={option.value}>{option.label}</option>
                                ))}
                            </select>
                        </label>

                        <label className="flex min-w-0 flex-col gap-1 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                            Desde
                            <input
                                type="date"
                                value={fechaDesde}
                                onChange={(e) => {
                                    setFechaDesde(e.target.value);
                                    setPageIndex(0);
                                }}
                                className="rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm font-normal tracking-normal text-slate-700 outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-100 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-500/20"
                            />
                        </label>

                        <label className="flex min-w-0 flex-col gap-1 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                            Hasta
                            <input
                                type="date"
                                value={fechaHasta}
                                onChange={(e) => {
                                    setFechaHasta(e.target.value);
                                    setPageIndex(0);
                                }}
                                className="rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm font-normal tracking-normal text-slate-700 outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-100 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-500/20"
                            />
                        </label>
                    </div>
                )}

                <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
                    {activeFilterCount > 0 ? (
                        <>
                            <span className="font-semibold uppercase tracking-wide text-slate-400 dark:text-slate-500">Filtros:</span>
                            {filterChips.map((chip) => (
                                <button
                                    key={chip.key}
                                    type="button"
                                    onClick={() => clearFilterChip(chip.key)}
                                    className="inline-flex items-center gap-1 rounded-full border border-blue-200 bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700 transition hover:bg-blue-100 dark:border-blue-500/30 dark:bg-blue-500/15 dark:text-blue-200 dark:hover:bg-blue-500/25"
                                >
                                    {chip.label}: {chip.value}
                                    <XMarkIcon className="h-3.5 w-3.5" />
                                </button>
                            ))}
                        </>
                    ) : (
                        <span>Sin filtros adicionales</span>
                    )}
                    <span className="text-slate-300 dark:text-slate-600">|</span>
                    <span>
                        Orden: <span className="font-medium text-slate-700 dark:text-slate-200">{sortLabelMap[sortField]} {sortDirection === "asc" ? "↑" : "↓"}</span>
                    </span>
                    <span className="text-slate-300 dark:text-slate-600">|</span>
                    <span>Página: <span className="font-medium text-slate-700 dark:text-slate-200">{pageIndex + 1} de {pageCount}</span></span>
                    {activeFilterCount > 0 && (
                        <>
                            <span className="text-slate-300 dark:text-slate-600">|</span>
                            <button
                                type="button"
                                onClick={clearFilters}
                                className="font-medium text-slate-500 transition hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
                            >
                                Limpiar filtros
                            </button>
                        </>
                    )}
                </div>
            </section>

            <section className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-black/20">
                <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-4 py-3 dark:border-slate-700">
                    <div>
                        <div className="text-sm font-semibold text-slate-700 dark:text-slate-200">{totalRecords.toLocaleString("es-AR")} cambio(s) encontrados</div>
                        <div className="text-xs text-slate-500 dark:text-slate-400">Cada fila representa un campo modificado dentro de un registro auditado.</div>
                    </div>
                    <div className="flex items-center gap-2">
                        <button
                            type="button"
                            onClick={() => void handleExport()}
                            disabled={isLoading || totalRecords === 0}
                            className="rounded-lg border border-slate-200 bg-white p-1.5 text-slate-500 transition hover:bg-slate-100 disabled:opacity-30 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-400 dark:hover:bg-slate-700"
                            title="Exportar auditoría"
                        >
                            <ArrowDownTrayIcon className="h-4 w-4" />
                        </button>
                        <div className="inline-flex items-center gap-2 rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600 dark:bg-slate-700/80 dark:text-slate-200">
                            <UserCircleIcon className="h-4 w-4" />
                            Historial global de cambios
                        </div>
                    </div>
                </div>

                {error ? <ErrorBanner message={error} />
                 : isLoading ? (
                    <div className="flex flex-1 items-center justify-center p-8 text-sm text-slate-500 dark:text-slate-400">Cargando auditoría...</div>
                ) : items.length === 0 ? (
                    <div className="flex flex-1 items-center justify-center p-8 text-sm text-slate-500 dark:text-slate-400">No hay cambios para mostrar con los filtros actuales.</div>
                ) : (
                    <div ref={scrollContainerRef} className="min-h-0 flex-1 overflow-auto">
                        <table className="w-full min-w-[1280px] text-sm">
                            <thead className="sticky top-0 z-10 bg-slate-50 text-slate-600 dark:bg-slate-900 dark:text-slate-300">
                                <tr>
                                    <th className="px-3 py-2 text-left font-semibold">
                                        <button type="button" onClick={() => toggleSort("fechaHora")} className="inline-flex items-center gap-1.5 transition hover:text-slate-900 dark:hover:text-slate-100">
                                            Fecha
                                            {renderSortIcon("fechaHora")}
                                        </button>
                                    </th>
                                    <th className="px-3 py-2 text-left font-semibold">
                                        <button type="button" onClick={() => toggleSort("entidad")} className="inline-flex items-center gap-1.5 transition hover:text-slate-900 dark:hover:text-slate-100">
                                            Entidad
                                            {renderSortIcon("entidad")}
                                        </button>
                                    </th>
                                    <th className="px-3 py-2 text-left font-semibold">
                                        <button type="button" onClick={() => toggleSort("entidadCodigo")} className="inline-flex items-center gap-1.5 transition hover:text-slate-900 dark:hover:text-slate-100">
                                            Código
                                            {renderSortIcon("entidadCodigo")}
                                        </button>
                                    </th>
                                    <th className="px-3 py-2 text-left font-semibold">
                                        <button type="button" onClick={() => toggleSort("usuarioNombreCompleto")} className="inline-flex items-center gap-1.5 transition hover:text-slate-900 dark:hover:text-slate-100">
                                            Usuario
                                            {renderSortIcon("usuarioNombreCompleto")}
                                        </button>
                                    </th>
                                    <th className="px-3 py-2 text-left font-semibold">
                                        <button type="button" onClick={() => toggleSort("accion")} className="inline-flex items-center gap-1.5 transition hover:text-slate-900 dark:hover:text-slate-100">
                                            Acción
                                            {renderSortIcon("accion")}
                                        </button>
                                    </th>
                                    <th className="px-3 py-2 text-left font-semibold">
                                        <button type="button" onClick={() => toggleSort("campo")} className="inline-flex items-center gap-1.5 transition hover:text-slate-900 dark:hover:text-slate-100">
                                            Campo
                                            {renderSortIcon("campo")}
                                        </button>
                                    </th>
                                    <th className="px-3 py-2 text-left font-semibold">Antes</th>
                                    <th className="px-3 py-2 text-left font-semibold">Después</th>
                                    <th className="px-3 py-2 text-left font-semibold">
                                        <button type="button" onClick={() => toggleSort("origen")} className="inline-flex items-center gap-1.5 transition hover:text-slate-900 dark:hover:text-slate-100">
                                            Origen
                                            {renderSortIcon("origen")}
                                        </button>
                                    </th>
                                    <th className="px-3 py-2 text-left font-semibold">Registro</th>
                                </tr>
                            </thead>
                            <tbody>
                                {items.map((item, index) => (
                                    <tr key={item.id} className={`${index % 2 === 0 ? "bg-white dark:bg-slate-800" : "bg-slate-50/60 dark:bg-slate-900/70"} border-b border-slate-100 last:border-b-0 dark:border-slate-700`}>
                                        <td className="whitespace-nowrap px-3 py-2 text-slate-600 dark:text-slate-300">{formatFechaAR(item.fechaHora)}</td>
                                        <td className="px-3 py-2 font-semibold text-slate-700 dark:text-slate-200">{entityLabelMap[item.entidad] || item.entidad}</td>
                                        <td className="px-3 py-2 font-semibold text-slate-800 dark:text-slate-100">{item.entidadCodigo || (item.entidadId != null ? `#${item.entidadId}` : "—")}</td>
                                        <td className="px-3 py-2">
                                            <div className="font-medium text-slate-700 dark:text-slate-200">{item.usuarioNombreCompleto || item.usuarioUsername || "Sistema"}</div>
                                            {item.usuarioNombreCompleto && item.usuarioUsername && (
                                                <div className="text-xs text-slate-400 dark:text-slate-500">{item.usuarioUsername}</div>
                                            )}
                                        </td>
                                        <td className="px-3 py-2"><AuditActionBadge action={item.accion} /></td>
                                        <td className="px-3 py-2 font-medium text-slate-700 dark:text-slate-200">{fieldLabelMap[item.campo] || item.campo}</td>
                                        <td className="max-w-[18rem] px-3 py-2 align-top">{renderAntes(item.valorAnterior)}</td>
                                        <td className="max-w-[18rem] px-3 py-2 align-top">{renderDespues(item.valorNuevo)}</td>
                                        <td className="whitespace-nowrap px-3 py-2"><AuditOriginBadge origin={item.origen} /></td>
                                        <td className="px-3 py-2">{renderEntityLink(item)}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}

                <div className="relative shrink-0 px-4 py-2 border-t border-gray-200 dark:border-slate-700 bg-gray-50/80 dark:bg-slate-800/60 flex justify-center items-center">
                    <div className="absolute left-4 text-xs text-gray-500 dark:text-slate-400">
                        {items.length} filas · {totalRecords.toLocaleString("es-AR")} totales
                    </div>
                    <PaginationControls
                        pageIndex={pageIndex}
                        pageSize={pageSize}
                        pageCount={pageCount}
                        onPageSizeChange={(s) => { saveScrollPosition(); setPageSize(s); }}
                        onPageChange={(p) => { saveScrollPosition(); setPageIndex(p); }}
                    />
                </div>
            </section>
        </main>
    );
}

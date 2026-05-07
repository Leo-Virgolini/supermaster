"use client";

import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
    ArrowDownTrayIcon,
    BookOpenIcon,
    ChevronDownIcon,
    ChevronUpIcon,
    XMarkIcon,
    Bars3BottomLeftIcon,
    ArrowUpIcon,
    ArrowDownIcon,
    PlusIcon,
    PencilSquareIcon,
    TrashIcon,
    PhotoIcon,
    DocumentTextIcon,
    CurrencyDollarIcon,
    Squares2X2Icon,
    DocumentDuplicateIcon,
    ClipboardDocumentIcon,
    ExclamationTriangleIcon,
    BuildingStorefrontIcon,
    BoltIcon,
    ClipboardDocumentListIcon,
    CalculatorIcon,
} from "@heroicons/react/24/outline";
import AsyncSelect from "../components/AsyncSelect/AsyncSelect";
import Button from "../components/Button/Button";
import Modal from "../components/Modal/Modal";
import { API_BASE_URL } from "../config/runtime";
import { useAuth } from "../context/AuthContext";
import { fetchAPI } from "../utils/fetchAPI";
import {
    searchCanales,
    searchCatalogos,
    searchClasifGastro,
    searchClasifGral,
    searchMarcas,
    searchTipos,
} from "../productos/productosService";

type OptionItem = { id: number; label: string };
type CatalogoPdfConfigItem = {
    id: number;
    nombre: string;
    canalId?: number | null;
    canalNombre?: string | null;
    catalogoId?: number | null;
    catalogoNombre?: string | null;
    cuotas: number;
    ordenarPor: string[];
    clasifGralId?: number | null;
    clasificacion?: string | null;
    caratula: boolean;
    titulo?: string | null;
    estetica?: string | null;
    tipoDocumento?: string | null;
    productosPorPagina: number;
    ubicacionSalida?: string | null;
    activo: boolean;
};

type CatalogoPdfConfigFormState = {
    nombre: string;
    canalId: string;
    catalogoId: string;
    cuotas: string;
    ordenarPor: string[];
    clasifGralId: string;
    caratula: boolean;
    titulo: string;
    estetica: string;
    tipoDocumento: string;
    productosPorPagina: string;
    ubicacionSalida: string;
    activo: boolean;
};

type CatalogoPdfGlobalConfig = {
    imagenesDir: string;
};

type CatalogosPdfAuditOrigin = "FORM" | "TABLE" | "API";

const CUOTAS_OPCIONES = [
    { value: "-1", label: "Transferencia" },
    { value: "0", label: "Contado" },
    { value: "3", label: "3 cuotas" },
    { value: "6", label: "6 cuotas" },
    { value: "9", label: "9 cuotas" },
    { value: "12", label: "12 cuotas" },
];

const ORDENAR_OPCIONES = [
    { value: "clasifGral", label: "Clasif. General" },
    { value: "clasifGastro", label: "Clasif. Gastro" },
    { value: "tipo", label: "Tipo" },
    { value: "marca", label: "Marca" },
    { value: "tag", label: "Tag" },
];

const ORDENAR_LABELS = Object.fromEntries(ORDENAR_OPCIONES.map((op) => [op.value, op.label])) as Record<string, string>;

const emptyConfigForm = (): CatalogoPdfConfigFormState => ({
    nombre: "",
    canalId: "",
    catalogoId: "",
    cuotas: "0",
    ordenarPor: [],
    clasifGralId: "",
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
    canalId: config.canalId != null ? String(config.canalId) : "",
    catalogoId: config.catalogoId != null ? String(config.catalogoId) : "",
    cuotas: String(config.cuotas ?? 0),
    ordenarPor: Array.isArray(config.ordenarPor) ? config.ordenarPor : [],
    clasifGralId: config.clasifGralId != null ? String(config.clasifGralId) : "",
    caratula: Boolean(config.caratula),
    titulo: config.titulo ?? "",
    estetica: config.estetica ?? "",
    tipoDocumento: config.tipoDocumento ?? "CATALOGO",
    productosPorPagina: String(config.productosPorPagina ?? 12),
    ubicacionSalida: config.ubicacionSalida ?? "",
    activo: Boolean(config.activo),
});

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

interface PdfDownloadResult {
    productos: number;
    productosSinImagen: string[];
}

async function descargarPdf(url: string, init?: RequestInit): Promise<PdfDownloadResult> {
    const response = await descargarArchivo(url, "catalogo.pdf", init);
    const productos = Number(response.headers.get("X-Productos-Count") ?? "0");
    const sinImagenHeader = response.headers.get("X-Productos-Sin-Imagen") ?? "";
    const productosSinImagen = sinImagenHeader ? sinImagenHeader.split(",") : [];
    return { productos, productosSinImagen };
}

async function fetchCuotasPorCanal(canalId: number): Promise<string[]> {
    try {
        const res = await fetchAPI(`${API_BASE_URL}/api/canal-concepto-cuotas/canal/${canalId}`);
        const data = await res.json();
        const arr = Array.isArray(data) ? data : (data.content ?? []);
        const nums = [...new Set<number>(arr.map((c: { cuotas: number | string }) => Number(c.cuotas)))].sort((a, b) => a - b);
        return nums.map(String);
    } catch {
        return [];
    }
}

function withAuditOrigin(origin: CatalogosPdfAuditOrigin, extraHeaders: HeadersInit = {}) {
    return {
        "X-Audit-Origin": origin,
        ...extraHeaders,
    };
}

export default function CatalogosPdfPage() {
    const { usuario } = useAuth();
    const isAdmin = (usuario.rol || "").trim().toUpperCase() === "ADMIN";

    const [catalogos, setCatalogos] = useState<OptionItem[]>([]);
    const [canales, setCanales] = useState<OptionItem[]>([]);
    const [clasifGrales, setClasifGrales] = useState<OptionItem[]>([]);
    const [catalogoId, setCatalogoId] = useState<number | null>(null);
    const [canalId, setCanalId] = useState<number | null>(null);
    const [cuotasCatalogo, setCuotasCatalogo] = useState("");
    const [cuotasCatalogoDisp, setCuotasCatalogoDisp] = useState<string[] | null>(null);
    const [mostrarFiltros, setMostrarFiltros] = useState(false);
    const [clasifGralId, setClasifGralId] = useState<number | null>(null);
    const [clasifGralLabel, setClasifGralLabel] = useState<string | undefined>();
    const [clasifGastroId, setClasifGastroId] = useState<number | null>(null);
    const [clasifGastroLabel, setClasifGastroLabel] = useState<string | undefined>();
    const [tipoId, setTipoId] = useState<number | null>(null);
    const [tipoLabel, setTipoLabel] = useState<string | undefined>();
    const [marcaId, setMarcaId] = useState<number | null>(null);
    const [marcaLabel, setMarcaLabel] = useState<string | undefined>();
    const [titulo, setTitulo] = useState("");
    const [subtitulo, setSubtitulo] = useState("");
    const [caratula, setCaratula] = useState(true);
    const [estetica, setEstetica] = useState("LINEA GE");
    const [tipoDocumento, setTipoDocumento] = useState("CATALOGO");
    const [productosPorPagina, setProductosPorPagina] = useState("12");
    const [tipoHoja, setTipoHoja] = useState("A4");
    const [incluirImagenes, setIncluirImagenes] = useState(true);
    const [mostrarCodigo, setMostrarCodigo] = useState(true);
    const [fontSizeCodigo, setFontSizeCodigo] = useState("6");
    const [colorCodigo, setColorCodigo] = useState("#000000");
    const [mostrarNombre, setMostrarNombre] = useState(true);
    const [fontSizeNombre, setFontSizeNombre] = useState("6");
    const [colorNombre, setColorNombre] = useState("#00008B");
    const [mostrarPrecio, setMostrarPrecio] = useState(true);
    const [fontSizePrecio, setFontSizePrecio] = useState("7");
    const [colorPrecio, setColorPrecio] = useState("#8B0000");
    const [mostrarUxb, setMostrarUxb] = useState(true);
    const [fontSizeUxb, setFontSizeUxb] = useState("6");
    const [colorUxb, setColorUxb] = useState("#000000");
    const [tag, setTag] = useState<"" | "MAQUINA" | "REPUESTO" | "MENAJE">("");
    const [ordenarPor, setOrdenarPor] = useState<string[]>([]);
    const [exportandoCatalogoPdf, setExportandoCatalogoPdf] = useState(false);
    const [configsPdf, setConfigsPdf] = useState<CatalogoPdfConfigItem[]>([]);
    const [cargandoConfigsPdf, setCargandoConfigsPdf] = useState(false);
    const [ejecutandoConfigSlug, setEjecutandoConfigSlug] = useState<number | null>(null);
    const [configModalOpen, setConfigModalOpen] = useState(false);
    const [configForm, setConfigForm] = useState<CatalogoPdfConfigFormState>(emptyConfigForm);
    const [editingConfigId, setEditingConfigId] = useState<number | null>(null);
    const [guardandoConfig, setGuardandoConfig] = useState(false);
    const [eliminandoConfigId, setEliminandoConfigId] = useState<number | null>(null);
    const [configsSortKey, setConfigsSortKey] = useState<"nombre" | "canalNombre" | "catalogoNombre" | "cuotas" | "productosPorPagina" | "activo">("nombre");
    const [configsSortDir, setConfigsSortDir] = useState<"asc" | "desc">("asc");
    const [modalCuotasDisp, setModalCuotasDisp] = useState<string[] | null>(null);
    const [cargandoModalCuotas, setCargandoModalCuotas] = useState(false);
    const [globalConfig, setGlobalConfig] = useState<CatalogoPdfGlobalConfig>({ imagenesDir: "" });
    const [cargandoGlobalConfig, setCargandoGlobalConfig] = useState(false);
    const [productosSinImagen, setProductosSinImagen] = useState<string[]>([]);

    const agregarOrden = (value: string) => {
        setOrdenarPor((prev) => (prev.includes(value) ? prev : [...prev, value]));
    };

    const quitarOrden = (value: string) => {
        setOrdenarPor((prev) => prev.filter((v) => v !== value));
    };

    const moverOrden = (index: number, direction: -1 | 1) => {
        setOrdenarPor((prev) => {
            const nextIndex = index + direction;
            if (nextIndex < 0 || nextIndex >= prev.length) return prev;
            const copy = [...prev];
            [copy[index], copy[nextIndex]] = [copy[nextIndex], copy[index]];
            return copy;
        });
    };

    const agregarConfigOrden = (value: string) => {
        setConfigForm((prev) => (
            prev.ordenarPor.includes(value)
                ? prev
                : { ...prev, ordenarPor: [...prev.ordenarPor, value] }
        ));
    };

    const quitarConfigOrden = (value: string) => {
        setConfigForm((prev) => ({ ...prev, ordenarPor: prev.ordenarPor.filter((v) => v !== value) }));
    };

    const moverConfigOrden = (index: number, direction: -1 | 1) => {
        setConfigForm((prev) => {
            const nextIndex = index + direction;
            if (nextIndex < 0 || nextIndex >= prev.ordenarPor.length) return prev;
            const copy = [...prev.ordenarPor];
            [copy[index], copy[nextIndex]] = [copy[nextIndex], copy[index]];
            return { ...prev, ordenarPor: copy };
        });
    };

    const cargarConfigsPdf = async () => {
        setCargandoConfigsPdf(true);
        try {
            const response = await fetchAPI(`${API_BASE_URL}/api/catalogos-pdf-config?page=0&size=100&sort=nombre,asc`);
            const data = await response.json() as { content?: CatalogoPdfConfigItem[] };
            setConfigsPdf(data.content ?? []);
        } catch (err) {
            toast.error(err instanceof Error ? err.message : "No se pudieron cargar las automatizaciones PDF.");
        } finally {
            setCargandoConfigsPdf(false);
        }
    };

    const cargarGlobalConfig = async () => {
        setCargandoGlobalConfig(true);
        try {
            const response = await fetchAPI(`${API_BASE_URL}/api/catalogos-pdf/configuracion-global`);
            const data = await response.json() as CatalogoPdfGlobalConfig;
            setGlobalConfig({ imagenesDir: data.imagenesDir ?? "" });
        } catch (err) {
            toast.error(err instanceof Error ? err.message : "No se pudo cargar la configuración global de imágenes.");
        } finally {
            setCargandoGlobalConfig(false);
        }
    };

    const handleExportarCatalogoPdf = async () => {
        if (!catalogoId || !canalId || cuotasCatalogo === "") {
            toast.error("Seleccioná un catálogo, canal y cuotas.");
            return;
        }
        setExportandoCatalogoPdf(true);
        const params = new URLSearchParams({
            catalogoId: String(catalogoId),
            canalId: String(canalId),
            cuotas: cuotasCatalogo || "0",
            incluirImagenes: String(incluirImagenes),
            caratula: String(caratula),
            estetica,
            tipoDocumento,
            productosPorPagina,
            tipoHoja,
            mostrarCodigo: String(mostrarCodigo),
            fontSizeCodigo,
            colorCodigo,
            mostrarNombre: String(mostrarNombre),
            fontSizeNombre,
            colorNombre,
            mostrarPrecio: String(mostrarPrecio),
            fontSizePrecio,
            colorPrecio,
            mostrarUxb: String(mostrarUxb),
            fontSizeUxb,
            colorUxb,
        });
        if (clasifGralId) params.append("clasifGralId", String(clasifGralId));
        if (clasifGastroId) params.append("clasifGastroId", String(clasifGastroId));
        if (tipoId) params.append("tipoId", String(tipoId));
        if (marcaId) params.append("marcaId", String(marcaId));
        if (tag !== "") params.append("tag", tag);
        if (ordenarPor.length > 0) params.append("ordenarPor", ordenarPor.join(","));
        if (titulo.trim()) params.append("titulo", titulo.trim());
        if (subtitulo.trim()) params.append("subtitulo", subtitulo.trim());
        try {
            const { productos, productosSinImagen: sinImagen } = await descargarPdf(`${API_BASE_URL}/api/catalogos-pdf/exportar?${params.toString()}`);
            toast.success(productos > 0 ? `Catálogo PDF descargado (${productos} productos).` : "Catálogo PDF descargado.");
            setProductosSinImagen(sinImagen);
        } catch (err) {
            toast.error(err instanceof Error ? err.message : "Error al exportar catálogo PDF");
            setProductosSinImagen([]);
        } finally {
            setExportandoCatalogoPdf(false);
        }
    };

    const handleEjecutarCatalogoPdfAutomatico = async (config: CatalogoPdfConfigItem) => {
        setEjecutandoConfigSlug(config.id);
        try {
            const url = `${API_BASE_URL}/api/catalogos-pdf/generar-automatico/${config.id}`;
            const response = await fetchAPI(url, { method: "POST" });
            const contentType = response.headers.get("Content-Type") ?? "";

            if (contentType.includes("application/json")) {
                // Guardado en disco del servidor
                const data = await response.json() as { mensaje: string; ruta: string; productosExportados: number; productosSinImagen: string[] };
                toast.success(`PDF generado para "${config.nombre}" (${data.productosExportados} productos). Guardado en: ${data.ruta}`);
                setProductosSinImagen(data.productosSinImagen ?? []);
            } else {
                // Descarga directa (sin ubicación de salida configurada)
                const blob = await response.blob();
                const blobUrl = URL.createObjectURL(blob);
                const a = document.createElement("a");
                a.href = blobUrl;
                const disposition = response.headers.get("Content-Disposition");
                const match = disposition?.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
                a.download = match?.[1]?.replace(/['"]/g, "") || `catalogo-${config.id}.pdf`;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                URL.revokeObjectURL(blobUrl);
                const productos = Number(response.headers.get("X-Productos-Count") ?? "0");
                toast.success(productos > 0 ? `PDF generado para "${config.nombre}" (${productos} productos).` : `PDF generado para "${config.nombre}".`);
                const sinImagenHeader = response.headers.get("X-Productos-Sin-Imagen") ?? "";
                setProductosSinImagen(sinImagenHeader ? sinImagenHeader.split(",") : []);
            }
        } catch (err) {
            toast.error(err instanceof Error ? err.message : `No se pudo generar el PDF para "${config.nombre}".`);
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
        if (!configForm.nombre.trim() || !configForm.canalId.trim() || !configForm.catalogoId.trim() || !configForm.ubicacionSalida.trim()) {
            toast.error("Completá al menos nombre, canal, catálogo y ubicación de salida.");
            return;
        }

        setGuardandoConfig(true);
        const payload = {
            nombre: configForm.nombre.trim(),
            canalId: Number(configForm.canalId),
            catalogoId: Number(configForm.catalogoId),
            cuotas: Number(configForm.cuotas || "0"),
            ordenarPor: configForm.ordenarPor,
            clasifGralId: configForm.clasifGralId ? Number(configForm.clasifGralId) : null,
            caratula: configForm.caratula,
            titulo: configForm.titulo.trim() || null,
            estetica: configForm.estetica.trim() || null,
            tipoDocumento: configForm.tipoDocumento.trim() || null,
            productosPorPagina: Number(configForm.productosPorPagina || "12"),
            ubicacionSalida: configForm.ubicacionSalida.trim(),
            activo: configForm.activo,
        };

        try {
            await fetchAPI(`${API_BASE_URL}/api/catalogos-pdf-config${editingConfigId ? `/${editingConfigId}` : ""}`, {
                method: editingConfigId ? "PUT" : "POST",
                headers: withAuditOrigin("FORM", { "Content-Type": "application/json" }),
                body: JSON.stringify(payload),
            });
            toast.success(editingConfigId ? "Automatización PDF actualizada." : "Automatización PDF creada.");
            closeConfigModal();
            await cargarConfigsPdf();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : "No se pudo guardar la automatización PDF.");
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
            toast.success("Automatización PDF eliminada.");
            await cargarConfigsPdf();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : "No se pudo eliminar la automatización PDF.");
        } finally {
            setEliminandoConfigId(null);
        }
    };

    useEffect(() => {
        searchCatalogos("").then((res) => setCatalogos(res.map((r: { id: string | number; label: string }) => ({ id: Number(r.id), label: r.label }))));
        searchCanales("").then((res) => setCanales(res.map((r: { id: string | number; label: string }) => ({ id: Number(r.id), label: r.label }))));
        searchClasifGral("").then((res) => setClasifGrales(res.map((r: { id: string | number; label: string }) => ({ id: Number(r.id), label: r.label }))));
        cargarConfigsPdf();
        cargarGlobalConfig();
    }, []);

    useEffect(() => {
        if (!canalId) {
            setCuotasCatalogoDisp(null);
            return;
        }
        fetchCuotasPorCanal(canalId)
            .then((cuotas) => {
                if (cuotas.length > 0) {
                    setCuotasCatalogoDisp(cuotas);
                    setCuotasCatalogo(cuotas.includes("0") ? "0" : cuotas[0]);
                } else {
                    setCuotasCatalogoDisp(null);
                }
            })
            .catch(() => setCuotasCatalogoDisp(null));
    }, [canalId]);

    useEffect(() => {
        if (!configModalOpen) return;

        const canalSeleccionado = canales.find((canal) => String(canal.id) === configForm.canalId);

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
    }, [configModalOpen, configForm.canalId, configForm.cuotas, canales]);

    const configsPdfOrdenados = useMemo(() => {
        const copia = [...configsPdf];
        const dir = configsSortDir === "asc" ? 1 : -1;
        copia.sort((a, b) => {
            const va = a[configsSortKey];
            const vb = b[configsSortKey];
            if (va == null && vb == null) return 0;
            if (va == null) return 1;
            if (vb == null) return -1;
            if (typeof va === "number" && typeof vb === "number") return (va - vb) * dir;
            if (typeof va === "boolean" && typeof vb === "boolean") return ((va === vb) ? 0 : va ? -1 : 1) * dir;
            return String(va).localeCompare(String(vb), "es", { sensitivity: "base" }) * dir;
        });
        return copia;
    }, [configsPdf, configsSortKey, configsSortDir]);

    const toggleConfigsSort = (key: typeof configsSortKey) => {
        if (configsSortKey === key) {
            setConfigsSortDir((prev) => (prev === "asc" ? "desc" : "asc"));
        } else {
            setConfigsSortKey(key);
            setConfigsSortDir("asc");
        }
    };

    if (!isAdmin) {
        return (
            <main className="p-6 bg-gray-50 min-h-0 flex flex-col overflow-hidden dark:bg-slate-900">
                <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-amber-800 dark:border-amber-800 dark:bg-amber-500/10 dark:text-amber-200">
                    Solo los administradores pueden acceder a Catálogos PDF.
                </div>
            </main>
        );
    }

    return (
        <main className="p-4 md:p-5 bg-gray-50 dark:bg-slate-900 flex flex-col gap-4">
            <div>
                <h1 className="text-3xl font-bold text-gray-800 dark:text-slate-100 flex items-center gap-2">
                    <BookOpenIcon className="w-8 h-8 text-gray-600 dark:text-slate-400" />
                    Catálogos PDF
                </h1>
                <p className="text-gray-500 dark:text-slate-400 text-sm mt-1">
                    Generación manual y ejecución de automatizaciones PDF desde la base de datos.
                </p>
            </div>

            <section className="bg-sky-50 dark:bg-sky-950/30 rounded-xl border border-sky-200 dark:border-sky-900 p-6">
                <div className="flex items-start justify-between gap-3 mb-4">
                    <div>
                        <h2 className="text-lg font-bold text-sky-900 dark:text-sky-300 flex items-center gap-2">
                            <BookOpenIcon className="w-5 h-5" />
                            Imágenes globales
                        </h2>
                        <p className="text-sm text-sky-700 dark:text-sky-400">
                            Ruta global que usa el backend para buscar las imágenes de productos en todos los catálogos PDF.
                        </p>
                    </div>
                    <Button text={cargandoGlobalConfig ? "Actualizando..." : "Recargar"} variant="light" onClick={cargarGlobalConfig} disabled={cargandoGlobalConfig}>
                        <ArrowDownTrayIcon className="w-4 h-4" />
                    </Button>
                </div>

                <div className="bg-white dark:bg-slate-800 rounded-lg border border-sky-200 dark:border-slate-600 p-5 flex flex-col gap-3">
                    <div className="flex flex-col gap-1">
                        <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Carpeta global de imágenes</label>
                        <span className="border border-gray-200 dark:border-slate-600 rounded p-2 text-sm bg-gray-50 dark:bg-slate-700/50 text-gray-700 dark:text-slate-200">
                            {cargandoGlobalConfig ? "Cargando..." : globalConfig.imagenesDir || "—"}
                        </span>
                    </div>

                    <p className="text-xs text-gray-500 dark:text-slate-400">
                        Configurada en el servidor (variable de entorno). Si una imagen no se encuentra ahí, el PDF usará la imagen de respaldo.
                    </p>
                </div>
            </section>

            <section className="bg-violet-50 dark:bg-violet-950/30 rounded-xl border border-violet-200 dark:border-violet-900 p-6">
                <h2 className="text-lg font-bold text-violet-900 dark:text-violet-300 mb-1 flex items-center gap-2">
                    <ArrowDownTrayIcon className="w-5 h-5" />
                    Generación manual
                </h2>
                <p className="text-sm text-violet-700 dark:text-violet-400 mb-5">
                    Elegí catálogo, canal y cuotas, y filtrá si hace falta antes de descargar el PDF.
                </p>

                <div className="bg-white dark:bg-slate-800 rounded-lg border border-violet-200 dark:border-slate-600 p-5 flex flex-col gap-4">
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                        <div className="flex flex-col gap-1">
                            <label className="text-sm font-medium text-gray-700 dark:text-slate-300 flex items-center gap-1.5">
                                <BookOpenIcon className="w-4 h-4 text-violet-500" />
                                Catálogo *
                            </label>
                            <div className="flex flex-wrap gap-2">
                                {catalogos.map((c) => (
                                    <button key={c.id} type="button" onClick={() => setCatalogoId(c.id)} className={`px-3 py-1.5 rounded-full text-xs font-semibold border transition ${catalogoId === c.id ? "bg-violet-100 text-violet-700 border-violet-300 dark:bg-violet-500/20 dark:text-violet-300 dark:border-violet-600" : "bg-white text-gray-500 border-gray-200 hover:border-gray-300 dark:bg-slate-700 dark:text-slate-400 dark:border-slate-600 dark:hover:border-slate-500"}`}>
                                        {c.label}
                                    </button>
                                ))}
                            </div>
                        </div>
                        <div className="flex flex-col gap-1">
                            <label className="text-sm font-medium text-gray-700 dark:text-slate-300 flex items-center gap-1.5">
                                <Squares2X2Icon className="w-4 h-4 text-violet-500" />
                                Canal *
                            </label>
                            <div className="flex flex-wrap gap-2">
                                {canales.map((c) => (
                                    <button key={c.id} type="button" onClick={() => setCanalId(c.id)} className={`px-3 py-1.5 rounded-full text-xs font-semibold border transition ${canalId === c.id ? "bg-violet-100 text-violet-700 border-violet-300 dark:bg-violet-500/20 dark:text-violet-300 dark:border-violet-600" : "bg-white text-gray-500 border-gray-200 hover:border-gray-300 dark:bg-slate-700 dark:text-slate-400 dark:border-slate-600 dark:hover:border-slate-500"}`}>
                                        {c.label}
                                    </button>
                                ))}
                            </div>
                        </div>
                        <div className="flex flex-col gap-1">
                            <label className="text-sm font-medium text-gray-700 dark:text-slate-300 flex items-center gap-1.5">
                                <CurrencyDollarIcon className="w-4 h-4 text-violet-500" />
                                Cuotas *
                            </label>
                            <div className="flex flex-wrap gap-2">
                                {(cuotasCatalogoDisp ? CUOTAS_OPCIONES.filter((op) => cuotasCatalogoDisp.includes(op.value)) : CUOTAS_OPCIONES)
                                    .map((op) => (
                                    <button key={op.value} type="button" onClick={() => setCuotasCatalogo(op.value)} className={`px-3 py-1.5 rounded-full text-xs font-semibold border transition ${cuotasCatalogo === op.value ? "bg-emerald-100 text-emerald-700 border-emerald-300 dark:bg-emerald-500/20 dark:text-emerald-300 dark:border-emerald-600" : "bg-white text-gray-500 border-gray-200 hover:border-gray-300 dark:bg-slate-700 dark:text-slate-400 dark:border-slate-600 dark:hover:border-slate-500"}`}>
                                        {op.label}
                                    </button>
                                ))}
                            </div>
                        </div>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
                        <div className="flex flex-col gap-1">
                            <label className={`text-sm font-medium flex items-center gap-1.5 ${caratula ? "text-gray-700 dark:text-slate-300" : "text-gray-400 dark:text-slate-500"}`}>
                                <DocumentTextIcon className="w-4 h-4 text-violet-500" />
                                Título
                            </label>
                            <input
                                value={titulo}
                                onChange={(e) => setTitulo(e.target.value)}
                                disabled={!caratula}
                                placeholder="CATALOGO / PRESUPUESTO / nombre del cliente..."
                                className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200 disabled:opacity-40 disabled:cursor-not-allowed"
                            />
                        </div>
                        <div className="flex flex-col gap-1">
                            <label className={`text-sm font-medium flex items-center gap-1.5 ${caratula ? "text-gray-700 dark:text-slate-300" : "text-gray-400 dark:text-slate-500"}`}>
                                <DocumentTextIcon className="w-4 h-4 text-violet-500" />
                                Subtítulo
                            </label>
                            <input
                                value={subtitulo}
                                onChange={(e) => setSubtitulo(e.target.value)}
                                disabled={!caratula}
                                placeholder="Si queda vacío, usa la fecha"
                                className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200 disabled:opacity-40 disabled:cursor-not-allowed"
                            />
                        </div>
                        <div className="flex items-end">
                            <label className="inline-flex items-center gap-2.5 text-sm font-medium text-gray-700 dark:text-slate-300 cursor-pointer select-none">
                                <input
                                    type="checkbox"
                                    checked={caratula}
                                    onChange={(e) => setCaratula(e.target.checked)}
                                    className="accent-violet-600 w-5 h-5 cursor-pointer"
                                />
                                Incluir carátula
                            </label>
                        </div>
                    </div>

                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                        <div className="flex flex-col gap-1">
                            <label className="text-sm font-medium text-gray-700 dark:text-slate-300 flex items-center gap-1.5">
                                <PencilSquareIcon className="w-4 h-4 text-violet-500" />
                                Estética
                            </label>
                            <div className="flex gap-2">
                                {([
                                    { value: "LINEA GE", img: "/logos/linea-ge.webp", active: "bg-blue-100 text-blue-700 border-blue-300 dark:bg-blue-500/20 dark:text-blue-300 dark:border-blue-600" },
                                    { value: "KT", img: "/logos/kt-icon.webp", active: "bg-orange-100 text-orange-700 border-orange-300 dark:bg-orange-500/20 dark:text-orange-300 dark:border-orange-600" },
                                ] as const).map(({ value, img, active }) => (
                                    <button key={value} type="button" onClick={() => setEstetica(value)} className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold border transition ${estetica === value ? active : "bg-white text-gray-500 border-gray-200 hover:border-gray-300 dark:bg-slate-700 dark:text-slate-400 dark:border-slate-600 dark:hover:border-slate-500"}`}>
                                        <img src={img} alt={value} className="w-4 h-4 rounded-sm object-contain" />
                                        {value}
                                    </button>
                                ))}
                            </div>
                        </div>
                        <div className="flex flex-col gap-1">
                            <label className="text-sm font-medium text-gray-700 dark:text-slate-300 flex items-center gap-1.5">
                                <DocumentDuplicateIcon className="w-4 h-4 text-violet-500" />
                                Tipo de documento
                            </label>
                            <div className="flex gap-2">
                                {([
                                    { value: "CATALOGO", icon: ClipboardDocumentListIcon, active: "bg-indigo-100 text-indigo-700 border-indigo-300 dark:bg-indigo-500/20 dark:text-indigo-300 dark:border-indigo-600" },
                                    { value: "PRESUPUESTO", icon: CalculatorIcon, active: "bg-emerald-100 text-emerald-700 border-emerald-300 dark:bg-emerald-500/20 dark:text-emerald-300 dark:border-emerald-600" },
                                ] as const).map(({ value, icon: Icon, active }) => (
                                    <button key={value} type="button" onClick={() => setTipoDocumento(value)} className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold border transition ${tipoDocumento === value ? active : "bg-white text-gray-500 border-gray-200 hover:border-gray-300 dark:bg-slate-700 dark:text-slate-400 dark:border-slate-600 dark:hover:border-slate-500"}`}>
                                        <Icon className="w-3.5 h-3.5" />
                                        {value}
                                    </button>
                                ))}
                            </div>
                        </div>
                        <div className="flex flex-col gap-1">
                            <label className="text-sm font-medium text-gray-700 dark:text-slate-300 flex items-center gap-1.5">
                                <Squares2X2Icon className="w-4 h-4 text-violet-500" />
                                Productos por página
                            </label>
                            <select value={productosPorPagina} onChange={(e) => setProductosPorPagina(e.target.value)} className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200">
                                <option value="2">2</option>
                                <option value="4">4</option>
                                <option value="8">8</option>
                                <option value="12">12</option>
                                <option value="20">20</option>
                            </select>
                        </div>
                        <div className="flex flex-col gap-1">
                            <label className="text-sm font-medium text-gray-700 dark:text-slate-300 flex items-center gap-1.5">
                                <DocumentTextIcon className="w-4 h-4 text-violet-500" />
                                Tipo de hoja
                            </label>
                            <select value={tipoHoja} onChange={(e) => setTipoHoja(e.target.value)} className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200">
                                <option value="A4">A4</option>
                                <option value="LETTER">Carta (Letter)</option>
                                <option value="A3">A3</option>
                                <option value="A5">A5</option>
                            </select>
                        </div>
                    </div>

                    {/* Columnas del producto */}
                    <div className="rounded-lg border border-gray-200 dark:border-slate-600 p-4 bg-gray-50 dark:bg-slate-700/30">
                        <div className="text-sm font-medium text-gray-700 dark:text-slate-300 mb-3">Columnas del producto</div>
                        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3">
                            {/* CÓDIGO */}
                            <div className="flex flex-col gap-1.5 rounded-lg border border-gray-200 dark:border-slate-600 bg-white dark:bg-slate-800 p-3">
                                <label className="flex items-center gap-2 text-sm font-medium text-gray-700 dark:text-slate-300">
                                    <input type="checkbox" checked={mostrarCodigo} onChange={(e) => setMostrarCodigo(e.target.checked)} className="accent-violet-600" />
                                    Código
                                </label>
                                <div className="flex items-center gap-2">
                                    <span className="text-xs text-gray-500 dark:text-slate-400 whitespace-nowrap">Tamaño (px)</span>
                                    <input
                                        type="number" min="4" max="20" step="0.5"
                                        value={fontSizeCodigo}
                                        disabled={!mostrarCodigo}
                                        onChange={(e) => setFontSizeCodigo(e.target.value)}
                                        className="w-14 border border-gray-300 dark:border-slate-600 rounded p-1 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200 disabled:opacity-40"
                                    />
                                    <input
                                        type="color"
                                        value={colorCodigo}
                                        disabled={!mostrarCodigo}
                                        onChange={(e) => setColorCodigo(e.target.value)}
                                        className="w-8 h-8 rounded border border-gray-300 dark:border-slate-600 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                                        title="Color del texto"
                                    />
                                </div>
                            </div>
                            {/* NOMBRE */}
                            <div className="flex flex-col gap-1.5 rounded-lg border border-gray-200 dark:border-slate-600 bg-white dark:bg-slate-800 p-3">
                                <label className="flex items-center gap-2 text-sm font-medium text-gray-700 dark:text-slate-300">
                                    <input type="checkbox" checked={mostrarNombre} onChange={(e) => setMostrarNombre(e.target.checked)} className="accent-violet-600" />
                                    Nombre
                                </label>
                                <div className="flex items-center gap-2">
                                    <span className="text-xs text-gray-500 dark:text-slate-400 whitespace-nowrap">Tamaño (px)</span>
                                    <input
                                        type="number" min="4" max="20" step="0.5"
                                        value={fontSizeNombre}
                                        disabled={!mostrarNombre}
                                        onChange={(e) => setFontSizeNombre(e.target.value)}
                                        className="w-14 border border-gray-300 dark:border-slate-600 rounded p-1 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200 disabled:opacity-40"
                                    />
                                    <input
                                        type="color"
                                        value={colorNombre}
                                        disabled={!mostrarNombre}
                                        onChange={(e) => setColorNombre(e.target.value)}
                                        className="w-8 h-8 rounded border border-gray-300 dark:border-slate-600 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                                        title="Color del texto"
                                    />
                                </div>
                            </div>
                            {/* PRECIO */}
                            <div className="flex flex-col gap-1.5 rounded-lg border border-gray-200 dark:border-slate-600 bg-white dark:bg-slate-800 p-3">
                                <label className="flex items-center gap-2 text-sm font-medium text-gray-700 dark:text-slate-300">
                                    <input type="checkbox" checked={mostrarPrecio} onChange={(e) => setMostrarPrecio(e.target.checked)} className="accent-violet-600" />
                                    Precio
                                </label>
                                <div className="flex items-center gap-2">
                                    <span className="text-xs text-gray-500 dark:text-slate-400 whitespace-nowrap">Tamaño (px)</span>
                                    <input
                                        type="number" min="4" max="20" step="0.5"
                                        value={fontSizePrecio}
                                        disabled={!mostrarPrecio}
                                        onChange={(e) => setFontSizePrecio(e.target.value)}
                                        className="w-14 border border-gray-300 dark:border-slate-600 rounded p-1 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200 disabled:opacity-40"
                                    />
                                    <input
                                        type="color"
                                        value={colorPrecio}
                                        disabled={!mostrarPrecio}
                                        onChange={(e) => setColorPrecio(e.target.value)}
                                        className="w-8 h-8 rounded border border-gray-300 dark:border-slate-600 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                                        title="Color del texto"
                                    />
                                </div>
                            </div>
                            {/* UxB */}
                            <div className="flex flex-col gap-1.5 rounded-lg border border-gray-200 dark:border-slate-600 bg-white dark:bg-slate-800 p-3">
                                <label className="flex items-center gap-2 text-sm font-medium text-gray-700 dark:text-slate-300">
                                    <input type="checkbox" checked={mostrarUxb} onChange={(e) => setMostrarUxb(e.target.checked)} className="accent-violet-600" />
                                    UxB
                                </label>
                                <div className="flex items-center gap-2">
                                    <span className="text-xs text-gray-500 dark:text-slate-400 whitespace-nowrap">Tamaño (px)</span>
                                    <input
                                        type="number" min="4" max="20" step="0.5"
                                        value={fontSizeUxb}
                                        disabled={!mostrarUxb}
                                        onChange={(e) => setFontSizeUxb(e.target.value)}
                                        className="w-14 border border-gray-300 dark:border-slate-600 rounded p-1 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200 disabled:opacity-40"
                                    />
                                    <input
                                        type="color"
                                        value={colorUxb}
                                        disabled={!mostrarUxb}
                                        onChange={(e) => setColorUxb(e.target.value)}
                                        className="w-8 h-8 rounded border border-gray-300 dark:border-slate-600 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                                        title="Color del texto"
                                    />
                                </div>
                            </div>
                        </div>
                        <div className="mt-3">
                            <label className="inline-flex items-center gap-2.5 text-sm font-medium text-gray-700 dark:text-slate-300 cursor-pointer select-none">
                                <input type="checkbox" checked={incluirImagenes} onChange={(e) => setIncluirImagenes(e.target.checked)} className="accent-violet-600 w-5 h-5 cursor-pointer" />
                                Incluir imágenes
                            </label>
                        </div>
                    </div>

                    <div>
                        <button type="button" onClick={() => setMostrarFiltros((v) => !v)} className="flex items-center gap-1.5 text-sm font-medium text-violet-700 dark:text-violet-400 hover:text-violet-900 dark:hover:text-violet-300">
                            {mostrarFiltros ? <ChevronUpIcon className="w-4 h-4" /> : <ChevronDownIcon className="w-4 h-4" />}
                            Filtros opcionales
                        </button>

                        {mostrarFiltros && (
                            <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 gap-4 border-t border-violet-100 dark:border-slate-600 pt-4">
                                <AsyncSelect label="Clasif. General" placeholder="Todas..." value={clasifGralId ?? undefined} displayValue={clasifGralLabel} loadOptions={searchClasifGral} onChange={(val, label) => { setClasifGralId(val ? Number(val) : null); setClasifGralLabel(label); }} />
                                <AsyncSelect label="Clasif. Gastro" placeholder="Todas..." value={clasifGastroId ?? undefined} displayValue={clasifGastroLabel} loadOptions={searchClasifGastro} onChange={(val, label) => { setClasifGastroId(val ? Number(val) : null); setClasifGastroLabel(label); }} />
                                <AsyncSelect label="Tipo" placeholder="Todos..." value={tipoId ?? undefined} displayValue={tipoLabel} loadOptions={searchTipos} onChange={(val, label) => { setTipoId(val ? Number(val) : null); setTipoLabel(label); }} />
                                <AsyncSelect label="Marca" placeholder="Todas..." value={marcaId ?? undefined} displayValue={marcaLabel} loadOptions={searchMarcas} onChange={(val, label) => { setMarcaId(val ? Number(val) : null); setMarcaLabel(label); }} />
                                <div className="flex flex-col gap-1">
                                    <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Tag</label>
                                    <select value={tag} onChange={(e) => setTag(e.target.value as "" | "MAQUINA" | "REPUESTO" | "MENAJE")} className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200">
                                        <option value="">Todos</option>
                                        <option value="MAQUINA">Máquinas</option>
                                        <option value="REPUESTO">Repuestos</option>
                                        <option value="MENAJE">Menaje</option>
                                    </select>
                                </div>
                                <div className="flex flex-col gap-1">
                                    <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Ordenar por</label>
                                    <div className="mt-1 flex flex-col gap-3">
                                        <div className="flex flex-wrap gap-2">
                                            {ORDENAR_OPCIONES.filter((op) => !ordenarPor.includes(op.value)).map((op) => (
                                                <button
                                                    key={op.value}
                                                    type="button"
                                                    onClick={() => agregarOrden(op.value)}
                                                    className="inline-flex items-center gap-1.5 rounded-full border border-violet-200 bg-violet-50 px-3 py-1 text-sm text-violet-700 transition hover:bg-violet-100 dark:border-violet-800 dark:bg-violet-500/10 dark:text-violet-300 dark:hover:bg-violet-500/20"
                                                >
                                                    <Bars3BottomLeftIcon className="h-3.5 w-3.5" />
                                                    {op.label}
                                                </button>
                                            ))}
                                        </div>

                                        {ordenarPor.length > 0 && (
                                            <div className="rounded-lg border border-gray-200 bg-gray-50 p-3 dark:border-slate-600 dark:bg-slate-700/40">
                                                <div className="mb-2 text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-slate-400">
                                                    Prioridad de orden
                                                </div>
                                                <div className="flex flex-col gap-2">
                                                    {ordenarPor.map((value, index) => (
                                                        <div
                                                            key={value}
                                                            className="flex items-center justify-between gap-3 rounded-lg border border-gray-200 bg-white px-3 py-2 dark:border-slate-600 dark:bg-slate-800"
                                                        >
                                                            <div className="flex items-center gap-3">
                                                                <span className="inline-flex h-6 min-w-6 items-center justify-center rounded-full bg-violet-100 px-2 text-xs font-semibold text-violet-700 dark:bg-violet-500/20 dark:text-violet-300">
                                                                    {index + 1}
                                                                </span>
                                                                <span className="text-sm text-gray-700 dark:text-slate-200">
                                                                    {ORDENAR_LABELS[value] ?? value}
                                                                </span>
                                                            </div>

                                                            <div className="flex items-center gap-1">
                                                                <button
                                                                    type="button"
                                                                    onClick={() => moverOrden(index, -1)}
                                                                    disabled={index === 0}
                                                                    className="rounded border border-gray-200 p-1 text-gray-500 transition hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-40 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700"
                                                                    aria-label="Subir prioridad"
                                                                >
                                                                    <ArrowUpIcon className="h-3.5 w-3.5" />
                                                                </button>
                                                                <button
                                                                    type="button"
                                                                    onClick={() => moverOrden(index, 1)}
                                                                    disabled={index === ordenarPor.length - 1}
                                                                    className="rounded border border-gray-200 p-1 text-gray-500 transition hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-40 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700"
                                                                    aria-label="Bajar prioridad"
                                                                >
                                                                    <ArrowDownIcon className="h-3.5 w-3.5" />
                                                                </button>
                                                                <button
                                                                    type="button"
                                                                    onClick={() => quitarOrden(value)}
                                                                    className="rounded border border-red-200 p-1 text-red-500 transition hover:bg-red-50 dark:border-red-800 dark:text-red-300 dark:hover:bg-red-500/10"
                                                                    aria-label="Quitar criterio"
                                                                >
                                                                    <XMarkIcon className="h-3.5 w-3.5" />
                                                                </button>
                                                            </div>
                                                        </div>
                                                    ))}
                                                </div>
                                            </div>
                                        )}

                                        {ordenarPor.length === 0 && (
                                            <p className="text-xs text-gray-500 dark:text-slate-400">
                                                Elegí uno o más criterios. El primero será el orden principal, luego el segundo, y así sucesivamente.
                                            </p>
                                        )}
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>

                    <div className="flex flex-wrap gap-2">
                        <Button text={exportandoCatalogoPdf ? "Generando PDF..." : "Generar PDF"} variant="dark" onClick={handleExportarCatalogoPdf} disabled={exportandoCatalogoPdf}>
                            <ArrowDownTrayIcon className="w-4 h-4" />
                        </Button>
                    </div>

                    {productosSinImagen.length > 0 && (
                        <div className="mt-4 rounded-lg border border-amber-300 bg-amber-50 dark:border-amber-700 dark:bg-amber-950/40 overflow-hidden">
                            <div className="flex items-center justify-between px-4 py-3 bg-amber-100/60 dark:bg-amber-900/30 border-b border-amber-200 dark:border-amber-800">
                                <div className="flex items-center gap-2">
                                    <ExclamationTriangleIcon className="w-5 h-5 text-amber-600 dark:text-amber-400" />
                                    <span className="text-sm font-semibold text-amber-800 dark:text-amber-300">
                                        Productos sin imagen
                                    </span>
                                    <span className="inline-flex items-center justify-center px-2 py-0.5 rounded-full text-xs font-bold bg-amber-200 text-amber-800 dark:bg-amber-800 dark:text-amber-200">
                                        {productosSinImagen.length}
                                    </span>
                                </div>
                                <div className="flex items-center gap-1">
                                    <button
                                        type="button"
                                        onClick={() => { navigator.clipboard.writeText(productosSinImagen.join(", ")); toast.success("SKUs copiados al portapapeles"); }}
                                        className="p-1.5 rounded-md text-amber-600 hover:bg-amber-200/60 dark:text-amber-400 dark:hover:bg-amber-800/40 transition"
                                        title="Copiar SKUs"
                                    >
                                        <ClipboardDocumentIcon className="h-4 w-4" />
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => setProductosSinImagen([])}
                                        className="p-1.5 rounded-md text-amber-600 hover:bg-amber-200/60 dark:text-amber-400 dark:hover:bg-amber-800/40 transition"
                                        title="Cerrar"
                                    >
                                        <XMarkIcon className="h-4 w-4" />
                                    </button>
                                </div>
                            </div>
                            <div className="px-4 py-3 max-h-48 overflow-y-auto">
                                <div className="flex flex-wrap gap-1.5">
                                    {productosSinImagen.slice(0, 200).map((sku) => (
                                        <span key={sku} className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-amber-100 text-amber-800 text-xs font-mono dark:bg-amber-900/40 dark:text-amber-300">
                                            <PhotoIcon className="w-3 h-3 opacity-50" />
                                            {sku}
                                        </span>
                                    ))}
                                    {productosSinImagen.length > 200 && (
                                        <span className="inline-flex items-center px-2 py-0.5 rounded bg-amber-200 text-amber-900 text-xs font-semibold dark:bg-amber-800 dark:text-amber-200">
                                            y {productosSinImagen.length - 200} más...
                                        </span>
                                    )}
                                </div>
                            </div>
                        </div>
                    )}
                </div>
            </section>

            <section className="bg-amber-50 dark:bg-amber-950/30 rounded-xl border border-amber-200 dark:border-amber-900 p-6">
                <div className="flex items-start justify-between gap-3 mb-5">
                    <div>
                        <h2 className="text-lg font-bold text-amber-900 dark:text-amber-300 flex items-center gap-2">
                            <ArrowDownTrayIcon className="w-5 h-5" />
                            Automatizaciones PDF
                            {configsPdf.length > 0 && (
                                <span className="inline-flex items-center justify-center h-6 min-w-6 px-2 rounded-full bg-amber-200 text-amber-900 text-xs font-bold dark:bg-amber-500/30 dark:text-amber-200">
                                    {configsPdf.length}
                                </span>
                            )}
                        </h2>
                        <p className="text-sm text-amber-700 dark:text-amber-400">
                            Ejecutá configuraciones automáticas guardadas en la base de datos.
                        </p>
                    </div>
                    <div className="flex flex-wrap gap-2">
                        <Button text="Crear automatización PDF" variant="dark" onClick={openCreateConfigModal}>
                            <PlusIcon className="w-4 h-4" />
                        </Button>
                        <Button text={cargandoConfigsPdf ? "Actualizando..." : "Recargar"} variant="light" onClick={cargarConfigsPdf} disabled={cargandoConfigsPdf}>
                            <ArrowDownTrayIcon className="w-4 h-4" />
                        </Button>
                    </div>
                </div>

                {configsPdf.length === 0 ? (
                    <div className="rounded-lg border border-dashed border-amber-300 dark:border-amber-800 p-4 text-sm text-amber-700 dark:text-amber-300 bg-white/70 dark:bg-slate-800/70">
                        {cargandoConfigsPdf ? "Cargando automatizaciones..." : "No hay configuraciones cargadas todavía."}
                    </div>
                ) : (
                    <div className="overflow-x-auto rounded-xl border border-amber-200 dark:border-amber-900 bg-white dark:bg-slate-800/90">
                        <table className="min-w-full text-sm">
                            <thead className="bg-amber-100/60 text-amber-900 dark:bg-amber-900/30 dark:text-amber-300">
                                <tr>
                                    <th className="px-3 py-2 text-left font-semibold w-8">
                                        <button
                                            type="button"
                                            onClick={() => toggleConfigsSort("activo")}
                                            className="inline-flex items-center gap-0.5 hover:text-amber-950 dark:hover:text-amber-200"
                                            title="Ordenar por estado"
                                        >
                                            {configsSortKey === "activo" && (configsSortDir === "asc"
                                                ? <ChevronUpIcon className="w-3 h-3" />
                                                : <ChevronDownIcon className="w-3 h-3" />)}
                                        </button>
                                    </th>
                                    <th className="px-3 py-2 text-left font-semibold">
                                        <button type="button" onClick={() => toggleConfigsSort("nombre")} className="inline-flex items-center gap-1 hover:text-amber-950 dark:hover:text-amber-200">
                                            Nombre
                                            {configsSortKey === "nombre" && (configsSortDir === "asc"
                                                ? <ChevronUpIcon className="w-3 h-3" />
                                                : <ChevronDownIcon className="w-3 h-3" />)}
                                        </button>
                                    </th>
                                    <th className="px-3 py-2 text-left font-semibold">
                                        <button type="button" onClick={() => toggleConfigsSort("canalNombre")} className="inline-flex items-center gap-1 hover:text-amber-950 dark:hover:text-amber-200">
                                            Canal
                                            {configsSortKey === "canalNombre" && (configsSortDir === "asc"
                                                ? <ChevronUpIcon className="w-3 h-3" />
                                                : <ChevronDownIcon className="w-3 h-3" />)}
                                        </button>
                                    </th>
                                    <th className="px-3 py-2 text-left font-semibold">
                                        <button type="button" onClick={() => toggleConfigsSort("catalogoNombre")} className="inline-flex items-center gap-1 hover:text-amber-950 dark:hover:text-amber-200">
                                            Catálogo
                                            {configsSortKey === "catalogoNombre" && (configsSortDir === "asc"
                                                ? <ChevronUpIcon className="w-3 h-3" />
                                                : <ChevronDownIcon className="w-3 h-3" />)}
                                        </button>
                                    </th>
                                    <th className="px-3 py-2 text-center font-semibold">
                                        <button type="button" onClick={() => toggleConfigsSort("cuotas")} className="inline-flex items-center gap-1 hover:text-amber-950 dark:hover:text-amber-200 mx-auto">
                                            Cuotas
                                            {configsSortKey === "cuotas" && (configsSortDir === "asc"
                                                ? <ChevronUpIcon className="w-3 h-3" />
                                                : <ChevronDownIcon className="w-3 h-3" />)}
                                        </button>
                                    </th>
                                    <th className="px-3 py-2 text-center font-semibold">
                                        <button type="button" onClick={() => toggleConfigsSort("productosPorPagina")} className="inline-flex items-center gap-1 hover:text-amber-950 dark:hover:text-amber-200 mx-auto">
                                            Prod/Pág
                                            {configsSortKey === "productosPorPagina" && (configsSortDir === "asc"
                                                ? <ChevronUpIcon className="w-3 h-3" />
                                                : <ChevronDownIcon className="w-3 h-3" />)}
                                        </button>
                                    </th>
                                    <th className="px-3 py-2 text-left font-semibold">Configuración</th>
                                    <th className="px-3 py-2 text-right font-semibold">Acciones</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100 dark:divide-slate-700">
                                {configsPdfOrdenados.map((config) => (
                                    <tr
                                        key={config.id}
                                        className={`transition ${config.activo ? "hover:bg-amber-50/60 dark:hover:bg-amber-950/20" : "opacity-60"}`}
                                    >
                                        <td className="px-3 py-2.5 align-top">
                                            <span
                                                title={config.activo ? "Activa" : "Inactiva"}
                                                className={`inline-block w-2.5 h-2.5 rounded-full ${config.activo ? "bg-emerald-500" : "bg-slate-400"}`}
                                            />
                                        </td>
                                        <td className="px-3 py-2.5 align-top min-w-[180px]">
                                            <div className="font-semibold text-gray-800 dark:text-slate-100 truncate">{config.nombre}</div>
                                            {config.titulo && (
                                                <div className="text-xs text-slate-500 dark:text-slate-400 truncate" title={config.titulo}>{config.titulo}</div>
                                            )}
                                            {config.ubicacionSalida && (
                                                <div className="text-[10px] text-slate-400 dark:text-slate-500 truncate" title={config.ubicacionSalida}>
                                                    {config.ubicacionSalida}
                                                </div>
                                            )}
                                        </td>
                                        <td className="px-3 py-2.5 align-top text-gray-700 dark:text-slate-200">{config.canalNombre || "—"}</td>
                                        <td className="px-3 py-2.5 align-top text-gray-700 dark:text-slate-200">{config.catalogoNombre || "—"}</td>
                                        <td className="px-3 py-2.5 align-top text-center text-gray-700 dark:text-slate-200 tabular-nums">{config.cuotas}</td>
                                        <td className="px-3 py-2.5 align-top text-center text-gray-700 dark:text-slate-200 tabular-nums">{config.productosPorPagina}</td>
                                        <td className="px-3 py-2.5 align-top">
                                            <div className="flex flex-wrap gap-1">
                                                {config.estetica && (
                                                    <span className="px-1.5 py-0.5 rounded bg-sky-50 text-sky-700 border border-sky-200 text-[10px] dark:bg-sky-500/10 dark:text-sky-300 dark:border-sky-800">
                                                        {config.estetica}
                                                    </span>
                                                )}
                                                {config.tipoDocumento && (
                                                    <span className="px-1.5 py-0.5 rounded bg-indigo-50 text-indigo-700 border border-indigo-200 text-[10px] dark:bg-indigo-500/10 dark:text-indigo-300 dark:border-indigo-800">
                                                        {config.tipoDocumento}
                                                    </span>
                                                )}
                                                {config.caratula && (
                                                    <span className="px-1.5 py-0.5 rounded bg-orange-50 text-orange-700 border border-orange-200 text-[10px] dark:bg-orange-500/10 dark:text-orange-300 dark:border-orange-800">
                                                        Carátula
                                                    </span>
                                                )}
                                                {config.clasificacion && (
                                                    <span className="px-1.5 py-0.5 rounded bg-violet-50 text-violet-700 border border-violet-200 text-[10px] dark:bg-violet-500/10 dark:text-violet-300 dark:border-violet-800">
                                                        {config.clasificacion}
                                                    </span>
                                                )}
                                                {config.ordenarPor && config.ordenarPor.length > 0 && config.ordenarPor.map((campo, index) => (
                                                    <span key={`${config.id}-${campo}`} className="px-1.5 py-0.5 rounded bg-amber-50 text-amber-700 border border-amber-200 text-[10px] dark:bg-amber-500/10 dark:text-amber-300 dark:border-amber-800">
                                                        {index + 1}. {ORDENAR_LABELS[campo] ?? campo}
                                                    </span>
                                                ))}
                                            </div>
                                        </td>
                                        <td className="px-3 py-2.5 align-top">
                                            <div className="flex items-center justify-end gap-1.5">
                                                <Button
                                                    text={ejecutandoConfigSlug === config.id ? "Generando..." : "Generar"}
                                                    variant="dark"
                                                    onClick={() => handleEjecutarCatalogoPdfAutomatico(config)}
                                                    disabled={!config.activo || ejecutandoConfigSlug !== null}
                                                >
                                                    <ArrowDownTrayIcon className="w-4 h-4" />
                                                </Button>
                                                <Button text="Editar" variant="light" onClick={() => openEditConfigModal(config)} disabled={ejecutandoConfigSlug !== null}>
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
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
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
                            value={configForm.canalId}
                            onChange={(e) => updateConfigField("canalId", e.target.value)}
                            className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200"
                        >
                            <option value="">Seleccionar canal...</option>
                            {canales.map((canal) => <option key={canal.id} value={canal.id}>{canal.label}</option>)}
                        </select>
                        {!configForm.canalId && editingConfigId !== null && (
                            <span className="text-xs text-amber-600 dark:text-amber-300">
                                Esta automatización tiene un canal viejo sin mapear. Elegí un canal real para guardarla.
                            </span>
                        )}
                    </div>
                    <div className="flex flex-col gap-1">
                        <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Catálogo *</label>
                        <select
                            value={configForm.catalogoId}
                            onChange={(e) => updateConfigField("catalogoId", e.target.value)}
                            className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200"
                        >
                            <option value="">Seleccionar catálogo...</option>
                            {catalogos.map((catalogo) => <option key={catalogo.id} value={catalogo.id}>{catalogo.label}</option>)}
                        </select>
                        {!configForm.catalogoId && editingConfigId !== null && (
                            <span className="text-xs text-amber-600 dark:text-amber-300">
                                Esta automatización tiene un catálogo viejo sin mapear. Elegí un catálogo real para guardarla.
                            </span>
                        )}
                    </div>
                    <div className="flex flex-col gap-1">
                        <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Cuotas *</label>
                        <select
                            value={configForm.cuotas}
                            onChange={(e) => updateConfigField("cuotas", e.target.value)}
                            className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200"
                        >
                            {(modalCuotasDisp ? CUOTAS_OPCIONES.filter((op) => modalCuotasDisp.includes(op.value)) : CUOTAS_OPCIONES).map((op) => (
                                <option key={op.value} value={op.value}>{op.label}</option>
                            ))}
                        </select>
                        {cargandoModalCuotas && <span className="text-xs text-gray-400 dark:text-slate-500">Cargando cuotas del canal...</span>}
                    </div>
                    <div className="flex flex-col gap-1">
                        <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Clasif. General</label>
                        <select
                            value={configForm.clasifGralId}
                            onChange={(e) => updateConfigField("clasifGralId", e.target.value)}
                            className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200"
                        >
                            <option value="">Todas...</option>
                            {configForm.clasifGralId && !clasifGrales.some((clasif) => String(clasif.id) === configForm.clasifGralId) && (
                                <option value={configForm.clasifGralId}>Clasificación actual</option>
                            )}
                            {clasifGrales.map((clasif) => <option key={clasif.id} value={clasif.id}>{clasif.label}</option>)}
                        </select>
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
                        <div className="flex gap-2">
                            {([
                                { value: "LINEA GE", img: "/logos/linea-ge.webp", active: "bg-blue-100 text-blue-700 border-blue-300 dark:bg-blue-500/20 dark:text-blue-300 dark:border-blue-600" },
                                { value: "KT", img: "/logos/kt-icon.webp", active: "bg-orange-100 text-orange-700 border-orange-300 dark:bg-orange-500/20 dark:text-orange-300 dark:border-orange-600" },
                            ] as const).map(({ value, img, active }) => (
                                <button key={value} type="button" onClick={() => updateConfigField("estetica", value)} className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold border transition ${configForm.estetica === value ? active : "bg-white text-gray-500 border-gray-200 hover:border-gray-300 dark:bg-slate-700 dark:text-slate-400 dark:border-slate-600 dark:hover:border-slate-500"}`}>
                                    <img src={img} alt={value} className="w-4 h-4 rounded-sm object-contain" />
                                    {value}
                                </button>
                            ))}
                        </div>
                    </div>
                    <div className="flex flex-col gap-1">
                        <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Tipo de documento</label>
                        <div className="flex gap-2">
                            {([
                                { value: "CATALOGO", icon: ClipboardDocumentListIcon, active: "bg-indigo-100 text-indigo-700 border-indigo-300 dark:bg-indigo-500/20 dark:text-indigo-300 dark:border-indigo-600" },
                                { value: "PRESUPUESTO", icon: CalculatorIcon, active: "bg-emerald-100 text-emerald-700 border-emerald-300 dark:bg-emerald-500/20 dark:text-emerald-300 dark:border-emerald-600" },
                            ] as const).map(({ value, icon: Icon, active }) => (
                                <button key={value} type="button" onClick={() => updateConfigField("tipoDocumento", value)} className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold border transition ${configForm.tipoDocumento === value ? active : "bg-white text-gray-500 border-gray-200 hover:border-gray-300 dark:bg-slate-700 dark:text-slate-400 dark:border-slate-600 dark:hover:border-slate-500"}`}>
                                    <Icon className="w-3.5 h-3.5" />
                                    {value}
                                </button>
                            ))}
                        </div>
                    </div>
                    <div className="flex flex-col gap-1">
                        <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Productos por página *</label>
                        <select
                            value={configForm.productosPorPagina}
                            onChange={(e) => updateConfigField("productosPorPagina", e.target.value)}
                            className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200"
                        >
                            <option value="2">2</option>
                            <option value="4">4</option>
                            <option value="8">8</option>
                            <option value="12">12</option>
                            <option value="20">20</option>
                        </select>
                    </div>
                    <div className="md:col-span-2 flex flex-col gap-1">
                        <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Ubicación de salida *</label>
                        <input
                            value={configForm.ubicacionSalida}
                            onChange={(e) => updateConfigField("ubicacionSalida", e.target.value)}
                            className="border border-gray-300 dark:border-slate-600 rounded p-2 text-sm bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200"
                            placeholder="/app/catalogos-salida/mi-subcarpeta"
                        />
                        <p className="text-xs text-gray-400 dark:text-slate-500">
                            Usar rutas internas del servidor. La raíz <code className="bg-gray-100 dark:bg-slate-700 px-1 rounded">/app/catalogos-salida/</code> mapea a <code className="bg-gray-100 dark:bg-slate-700 px-1 rounded">G:\Mi unidad\</code> en el host.
                        </p>
                    </div>
                    <div className="md:col-span-2 flex flex-col gap-2">
                        <label className="text-sm font-medium text-gray-700 dark:text-slate-300">Ordenar por</label>
                        <div className="flex flex-wrap gap-2">
                            {ORDENAR_OPCIONES.filter((op) => !configForm.ordenarPor.includes(op.value)).map((op) => (
                                <button
                                    key={op.value}
                                    type="button"
                                    onClick={() => agregarConfigOrden(op.value)}
                                    className="inline-flex items-center gap-1.5 rounded-full border border-violet-200 bg-violet-50 px-3 py-1 text-sm text-violet-700 transition hover:bg-violet-100 dark:border-violet-800 dark:bg-violet-500/10 dark:text-violet-300 dark:hover:bg-violet-500/20"
                                >
                                    <Bars3BottomLeftIcon className="h-3.5 w-3.5" />
                                    {op.label}
                                </button>
                            ))}
                        </div>
                        {configForm.ordenarPor.length > 0 && (
                            <div className="rounded-lg border border-gray-200 bg-gray-50 p-3 dark:border-slate-600 dark:bg-slate-700/40">
                                <div className="mb-2 text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-slate-400">
                                    Prioridad de orden
                                </div>
                                <div className="flex flex-col gap-2">
                                    {configForm.ordenarPor.map((value, index) => (
                                        <div
                                            key={value}
                                            className="flex items-center justify-between gap-3 rounded-lg border border-gray-200 bg-white px-3 py-2 dark:border-slate-600 dark:bg-slate-800"
                                        >
                                            <div className="flex items-center gap-3">
                                                <span className="inline-flex h-6 min-w-6 items-center justify-center rounded-full bg-violet-100 px-2 text-xs font-semibold text-violet-700 dark:bg-violet-500/20 dark:text-violet-300">
                                                    {index + 1}
                                                </span>
                                                <span className="text-sm text-gray-700 dark:text-slate-200">
                                                    {ORDENAR_LABELS[value] ?? value}
                                                </span>
                                            </div>

                                            <div className="flex items-center gap-1">
                                                <button
                                                    type="button"
                                                    onClick={() => moverConfigOrden(index, -1)}
                                                    disabled={index === 0}
                                                    className="rounded border border-gray-200 p-1 text-gray-500 transition hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-40 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700"
                                                >
                                                    <ArrowUpIcon className="h-4 w-4" />
                                                </button>
                                                <button
                                                    type="button"
                                                    onClick={() => moverConfigOrden(index, 1)}
                                                    disabled={index === configForm.ordenarPor.length - 1}
                                                    className="rounded border border-gray-200 p-1 text-gray-500 transition hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-40 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700"
                                                >
                                                    <ArrowDownIcon className="h-4 w-4" />
                                                </button>
                                                <button
                                                    type="button"
                                                    onClick={() => quitarConfigOrden(value)}
                                                    className="rounded border border-red-200 p-1 text-red-500 transition hover:bg-red-50 dark:border-red-800 dark:text-red-300 dark:hover:bg-red-500/10"
                                                >
                                                    <XMarkIcon className="h-4 w-4" />
                                                </button>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}
                        {configForm.ordenarPor.length === 0 && (
                            <div className="rounded-lg border border-dashed border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-500 dark:border-slate-600 dark:bg-slate-800/40 dark:text-slate-400">
                                Si no elegís un orden, el backend usará el orden por defecto.
                            </div>
                        )}
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
        </main>
    );
}

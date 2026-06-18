"use client";
import { useState, useMemo, useEffect, useCallback, useRef } from "react";
import { useSearchParams } from "next/navigation";
import { toast } from "sonner";
import { notificar } from "../utils/notificar";
import { BookmarkIcon, BuildingStorefrontIcon, CheckIcon, CloudArrowDownIcon, CubeIcon, XMarkIcon, IdentificationIcon, CurrencyDollarIcon, ArchiveBoxIcon, ReceiptPercentIcon, Squares2X2Icon, UserGroupIcon, ShoppingBagIcon, BanknotesIcon } from "@heroicons/react/24/outline";
import { API_BASE_URL } from "../config/runtime";
import { confirmDialog } from "../utils/confirmDialog";
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import SearchInput from "../components/SearchInput/SearchInput";
import Button from "../components/Button/Button";
import CreateButton from "../components/Button/CreateButton";
import DeleteButton from "../components/Button/DeleteButton";
import Modal from "../components/Modal/Modal";
import AsyncSelect from "../components/AsyncSelect/AsyncSelect";
import { useAuth } from "../context/AuthContext";
import { useProductos } from "./useProductos";
import ProductosFilterPanel, { ProductosFilterToggle } from "./ProductosFilterBar";
import {
    getProductosForExportAPI, getSiguienteSkuAPI, existeSkuAPI, getMlaPorSkuAPI, createMlaAPI,
    searchMarcas, searchClasifGral, searchClasifGastro, searchTipos, searchProveedores, searchOrigenes, searchMateriales, searchMlas,
    searchCatalogos, searchAptos, searchClientes, searchCanales, addProductoCatalogoAPI, addProductoAptoAPI, addProductoClienteAPI,
    removeProductoCatalogoAPI, removeProductoAptoAPI, removeProductoClienteAPI, updateProductoAPI, getNombreById,
    exportarProductosADuxAPI, calcularEnvioMlaAPI, exportarProductosANubeAPI, exportarProductosAMlAPI,
} from "./productosService";
import { updateProductoMargenAPI } from "./productoMargenService";
import {
    getProductoAptosAPI, getProductoCatalogosAPI, getProductoClientesAPI,
    getAllAptosAPI, getAllCatalogosAPI, getAllClientesAPI, asignarPrecioInfladoAPI,
} from "./productoSubRecursosService";
import MultiAsyncSelect, { type MultiOption } from "../components/MultiAsyncSelect/MultiAsyncSelect";
import { PreciosInfladosSection, type PrecioInfladoDraft } from "./PreciosInfladosSection";
import { HistorialSection } from "./HistorialSection";
import { getColumns } from "./columns";
import { ProductoCreateDTO, ProductoDTO, ProductoPatchDTO } from "./types";
import { type SortingState } from "@tanstack/react-table";


const PRODUCTOS_VIEWS_STORAGE_KEY = "productos_saved_views_v1";
type ProductosView = {
    id: string;
    name: string;
    filters: Record<string, unknown>;
    sorting: SortingState;
    columnVisibility: Record<string, boolean>;
    createdAt: string;
};

function reportarExportToast(plataforma: string, r: { creados: number; yaExistian: string[]; errores: string[]; advertencias?: string[] }) {
    const partes: string[] = [];
    if (r.creados > 0) partes.push(`${r.creados} creado(s)`);
    if (r.yaExistian.length) partes.push(`${r.yaExistian.length} ya existía(n)`);
    if (r.advertencias?.length) partes.push(`avisos: ${r.advertencias.join("; ")}`);
    if (r.errores.length) partes.push(`${r.errores.length} con error: ${r.errores.join("; ")}`);
    if (r.errores.length) notificar.error(`${plataforma}: ${partes.join(" · ")}`);
    else notificar.success(`${plataforma}: ${partes.join(" · ") || "sin cambios"}`);
}

function ImagePickerModal({ onSelect, onClose }: { onSelect: (name: string) => void; onClose: () => void }) {
    const [search, setSearch] = useState("");
    const [files, setFiles] = useState<string[]>([]);
    const [total, setTotal] = useState(0);
    const [loading, setLoading] = useState(false);

    const load = useCallback(async (q: string, signal?: AbortSignal) => {
        setLoading(true);
        try {
            const params = q ? `?search=${encodeURIComponent(q)}` : "";
            const res = await fetch(`${API_BASE_URL}/api/imagenes/listar${params}`, { signal });
            if (!res.ok) {
                if (!signal?.aborted) {
                    notificar.error(`Error cargando imágenes (HTTP ${res.status})`);
                    setFiles([]);
                    setTotal(0);
                }
                return;
            }
            const data = await res.json();
            if (!signal?.aborted) {
                setFiles(Array.isArray(data?.archivos) ? data.archivos : []);
                setTotal(typeof data?.total === "number" ? data.total : 0);
            }
        } catch (e) {
            if ((e as { name?: string })?.name === "AbortError") return;
            setFiles([]);
            setTotal(0);
        } finally {
            if (!signal?.aborted) setLoading(false);
        }
    }, []);

    // Debounce + AbortController: si el usuario tipea rápido, cancelamos la
    // request anterior para que respuestas viejas no pisen el state nuevo.
    useEffect(() => {
        const controller = new AbortController();
        const t = setTimeout(() => { void load(search, controller.signal); }, 300);
        return () => {
            clearTimeout(t);
            controller.abort();
        };
    }, [search, load]);

    // Escape cierra SOLO este selector, no el modal del producto que está detrás.
    // Lo capturamos en fase de captura y frenamos la propagación antes de que
    // llegue al listener (en bubble) del Modal del producto.
    useEffect(() => {
        const onKeyDown = (e: KeyboardEvent) => {
            if (e.key === "Escape") {
                e.stopImmediatePropagation();
                e.stopPropagation();
                onClose();
            }
        };
        window.addEventListener("keydown", onKeyDown, true);
        return () => window.removeEventListener("keydown", onKeyDown, true);
    }, [onClose]);

    return (
        <div className="fixed inset-0 z-[220] flex items-center justify-center bg-black/40 p-4" onClick={onClose}>
            <div className="flex max-h-[75vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl dark:border-slate-700 dark:bg-slate-900" onClick={(e) => e.stopPropagation()}>
                <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3 dark:border-slate-700">
                    <div>
                        <div className="text-sm font-semibold text-slate-900 dark:text-slate-100">Seleccionar imagen</div>
                        <div className="text-xs text-slate-500 dark:text-slate-400">Elegí una imagen existente de la carpeta configurada en el backend.</div>
                    </div>
                    <button onClick={onClose} className="text-slate-400 transition hover:text-slate-700 dark:hover:text-slate-200" aria-label="Cerrar selector">
                        <XMarkIcon className="h-5 w-5" />
                    </button>
                </div>

                <div className="border-b border-slate-200 px-4 py-3 dark:border-slate-700">
                    <input
                        autoFocus
                        type="text"
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        placeholder="Buscar imagen..."
                        className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100 dark:focus:ring-blue-500/20"
                    />
                    {!loading && total > files.length && (
                        <div className="mt-2 text-xs text-amber-600 dark:text-amber-400">
                            Mostrando las primeras {files.length} de {total} imágenes — refiná la búsqueda para acotar.
                        </div>
                    )}
                </div>

                <div className="grid min-h-[220px] flex-1 grid-cols-2 gap-3 overflow-y-auto p-4 sm:grid-cols-3 lg:grid-cols-4">
                    {loading ? (
                        <div className="col-span-full flex items-center justify-center text-sm text-slate-500 dark:text-slate-400">Cargando...</div>
                    ) : files.length === 0 ? (
                        <div className="col-span-full flex items-center justify-center text-sm text-slate-500 dark:text-slate-400">No hay imágenes para mostrar.</div>
                    ) : (
                        files.map((file) => (
                            <button
                                key={file}
                                type="button"
                                onClick={() => { onSelect(file); onClose(); }}
                                className="group flex flex-col gap-2 rounded-2xl border border-slate-200 bg-white p-2 text-left shadow-sm transition hover:border-blue-400 hover:bg-blue-50/60 dark:border-slate-700 dark:bg-slate-800 dark:hover:bg-blue-900/20"
                                title={file}
                            >
                                <img
                                    src={`${API_BASE_URL}/api/imagenes/${file}`}
                                    alt={file}
                                    className="h-24 w-full rounded-xl object-cover"
                                    onError={(e) => { (e.target as HTMLImageElement).src = ""; }}
                                />
                                <span className="truncate text-[11px] text-slate-500 group-hover:text-blue-700 dark:text-slate-400 dark:group-hover:text-blue-300">{file}</span>
                            </button>
                        ))
                    )}
                </div>
            </div>
        </div>
    );
}

function readProductosViews(): ProductosView[] {
    if (typeof window === "undefined") return [];
    try {
        const saved = window.localStorage.getItem(PRODUCTOS_VIEWS_STORAGE_KEY);
        if (!saved) return [];
        const parsed = JSON.parse(saved);
        return Array.isArray(parsed) ? parsed : [];
    } catch {
        return [];
    }
}

function writeProductosViews(views: ProductosView[]) {
    if (typeof window === "undefined") return;
    window.localStorage.setItem(PRODUCTOS_VIEWS_STORAGE_KEY, JSON.stringify(views));
}

export default function ProductosPage() {
    const { hasPermiso } = useAuth();
    const canEditProductos = hasPermiso("PRODUCTOS_EDITAR");
    const canExportarDux = hasPermiso("INTEGRACIONES_EDITAR");
    const searchParams = useSearchParams();
    const getSearchParamValue = useCallback(() => searchParams.get("search") ?? searchParams.get("q") ?? "", [searchParams]);
    const [pageIndex, setPageIndex] = useState(0);
    const [pageSize, setPageSize] = useState(() => getInitialPageSize("productos"));
    const [filters, setFilters] = useState<any>(() => {
        const initial: any = { search: getSearchParamValue() };
        const paramKeys = ["catalogoIds", "aptoIds", "clienteIds", "marcaIds", "tipoIds", "materialIds", "origenIds", "clasifGralIds", "clasifGastroIds"];
        for (const key of paramKeys) {
            const val = searchParams.get(key);
            if (val) initial[key] = val.split(",").map(Number);
        }
        return initial;
    });
    const [sorting, setSorting] = useState<SortingState>([{ id: "id", desc: true }]);
    const [rowSelection, setRowSelection] = useState({});
    const [savedViews, setSavedViews] = useState<ProductosView[]>(() => readProductosViews());
    const [selectedViewId, setSelectedViewId] = useState("");
    const [viewName, setViewName] = useState("");
    const [isViewModalOpen, setIsViewModalOpen] = useState(false);
    const [columnVisibilityVersion, setColumnVisibilityVersion] = useState(0);
    const [activeOverrides, setActiveOverrides] = useState<Record<number, boolean>>({});
    const [filtrosExpanded, setFiltrosExpanded] = useState(() => {
        if (typeof window === "undefined") return false;
        return window.localStorage.getItem("productos_filtros_expandido") === "1";
    });
    const toggleFiltros = () => {
        setFiltrosExpanded((prev) => {
            const next = !prev;
            if (typeof window !== "undefined") window.localStorage.setItem("productos_filtros_expandido", next ? "1" : "0");
            return next;
        });
    };

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [isImagePickerOpen, setIsImagePickerOpen] = useState(false);

    // --- Campos del Formulario ---
    const [sku, setSku] = useState("");
    // Último SKU autocompletado: nos deja saber si el usuario lo editó a mano
    // (en cuyo caso no lo pisamos al cambiar "Es Combo").
    const [lastSuggestedSku, setLastSuggestedSku] = useState("");
    // Aviso en vivo: true si el SKU tipeado ya pertenece a otro producto.
    const [skuYaExiste, setSkuYaExiste] = useState(false);
    const [codExt, setCodExt] = useState("");
    const [tituloDux, setTituloDux] = useState("");
    const [tituloMl, setTituloMl] = useState("");
    const [tituloNube, setTituloNube] = useState("");
    const [esCombo, setEsCombo] = useState(false);
    const [subirADux, setSubirADux] = useState(true);
    const [subirKtHogar, setSubirKtHogar] = useState(false);
    const [subirKtGastro, setSubirKtGastro] = useState(false);
    const [subirMl, setSubirMl] = useState(false);
    const [cuotaHogar, setCuotaHogar] = useState<number>(-1);
    const [cuotaGastro, setCuotaGastro] = useState<number>(6);
    const [uxb, setUxb] = useState(1);
    const [activo, setActivo] = useState(true);
    const [imagenUrl, setImagenUrl] = useState("");
    const [capacidad, setCapacidad] = useState("");
    const [largo, setLargo] = useState("");
    const [ancho, setAncho] = useState("");
    const [alto, setAlto] = useState("");
    const [diamboca, setDiamboca] = useState("");
    const [diambase, setDiambase] = useState("");
    const [espesor, setEspesor] = useState("");
    const [costo, setCosto] = useState<number | "">("");
    const [iva, setIva] = useState(21.0);
    const [marcaId, setMarcaId] = useState<number | null>(null);
    const [origenId, setOrigenId] = useState<number | null>(null);
    const [clasifGralId, setClasifGralId] = useState<number | null>(null);
    const [clasifGastroId, setClasifGastroId] = useState<number | null>(null);
    const [tipoId, setTipoId] = useState<number | null>(null);
    const [proveedorId, setProveedorId] = useState<number | null>(null);
    const [materialId, setMaterialId] = useState<number | null>(null);
    // Nombres a mostrar en los AsyncSelect de relación simple (necesario para
    // precargar el valor en modo edición; el AsyncSelect no resuelve nombre por id).
    const [marcaDisplay, setMarcaDisplay] = useState("");
    const [origenDisplay, setOrigenDisplay] = useState("");
    const [clasifGralDisplay, setClasifGralDisplay] = useState("");
    const [clasifGastroDisplay, setClasifGastroDisplay] = useState("");
    const [tipoDisplay, setTipoDisplay] = useState("");
    const [proveedorDisplay, setProveedorDisplay] = useState("");
    const [materialDisplay, setMaterialDisplay] = useState("");
    const [mlaId, setMlaId] = useState<number | null>(null);
    const [mlaDisplay, setMlaDisplay] = useState("");
    // Panel "Nuevo MLA" dentro del alta de producto.
    const [showNuevoMla, setShowNuevoMla] = useState(false);
    const [mlaCodigo, setMlaCodigo] = useState("");
    const [mlaMlau, setMlaMlau] = useState("");
    const [mlaPrecioEnvio, setMlaPrecioEnvio] = useState<number | "">("");
    const [mlaTope, setMlaTope] = useState<number | "">("");
    const [mlaComision, setMlaComision] = useState<number | "">("");
    const [obteniendoMla, setObteniendoMla] = useState(false);
    const [creandoMla, setCreandoMla] = useState(false);
    // Márgenes (se asocian tras crear el producto)
    const [margenMinorista, setMargenMinorista] = useState<number | "">("");
    const [margenMayorista, setMargenMayorista] = useState<number | "">("");
    // Relaciones N-a-N (se asocian tras crear el producto)
    const [catalogosSel, setCatalogosSel] = useState<MultiOption[]>([]);
    const [aptosSel, setAptosSel] = useState<MultiOption[]>([]);
    const [clientesSel, setClientesSel] = useState<MultiOption[]>([]);
    // Precios inflados a asignar tras crear el producto (solo modo alta).
    const [preciosInfladosSel, setPreciosInfladosSel] = useState<PrecioInfladoDraft[]>([]);
    // null = modo crear; con id = modo editar (mismo modal/form).
    const [editandoProductoId, setEditandoProductoId] = useState<number | null>(null);
    // Tab activo del panel en modo edición: form de datos o historial de cambios.
    const [panelTab, setPanelTab] = useState<"datos" | "historial">("datos");
    // Ref estable hacia abrirEdicion (definida más abajo) para usarla en el
    // useMemo de columnas sin invalidar la memoización en cada render.
    const abrirEdicionRef = useRef<(p: ProductoDTO) => void>(() => {});
    // True si el usuario tocó la imagen a mano (la quitó o la eligió del picker):
    // mientras esté en true, el autocompletado por SKU no la vuelve a pisar.
    const imagenTocadaManualmenteRef = useRef(false);
    // Snapshot de N-a-N al abrir en edición, para calcular el diff al guardar.
    const [catalogosOriginal, setCatalogosOriginal] = useState<MultiOption[]>([]);
    const [aptosOriginal, setAptosOriginal] = useState<MultiOption[]>([]);
    const [clientesOriginal, setClientesOriginal] = useState<MultiOption[]>([]);
    const [moq, setMoq] = useState<number | "">("");
    const [stock, setStock] = useState<number | "">(0);
    const [tagReposicion, setTagReposicion] = useState<"" | "PRIO" | "LIQ">("");
    const [tag, setTag] = useState<"" | "MAQUINA" | "REPUESTO" | "MENAJE" | "INSUMO">("");
    const [formErrors, setFormErrors] = useState<Record<string, string>>({});

    // Sync header search param (también limpia cuando se remueve ?q del URL)
    useEffect(() => {
        const q = getSearchParamValue();
        const newFilters: any = { search: q };
        const paramKeys = ["catalogoIds", "aptoIds", "clienteIds", "marcaIds", "tipoIds", "materialIds", "origenIds", "clasifGralIds", "clasifGastroIds"];
        for (const key of paramKeys) {
            const val = searchParams.get(key);
            if (val) newFilters[key] = val.split(",").map(Number);
        }
        setFilters(newFilters);
        setPageIndex(0);
    }, [getSearchParamValue, searchParams]);

    const { productos, totalRecords, isLoading, createProducto, deleteProducto, updateProducto, updateProductoMargen, refresh } = useProductos(pageIndex, pageSize, filters, sorting);
    const pageCount = totalRecords > 0 ? Math.ceil(totalRecords / pageSize) : 1;

    const sortFieldMapping: Record<string, string> = {
        rubro: "clasifGral", subrubro: "clasifGastro", marca: "marca",
        tipo: "tipo", proveedor: "proveedor", origen: "origen",
        material: "material", mlaId: "mla.mla",
    };

    const handleExportAll = async () => {
        const sortParam = sorting.length > 0
            ? sorting.map(s => `${sortFieldMapping[s.id] || s.id},${s.desc ? "desc" : "asc"}`)
            : ["id,asc"];
        const res = await getProductosForExportAPI(0, 99999, filters, sortParam);
        return res.content;
    };
    const selectedIds = Object.keys(rowSelection).map(Number);
    const hasSelection = selectedIds.length > 0;

    const columns = useMemo(
        () => getColumns((producto) => abrirEdicionRef.current(producto), canEditProductos),
        [canEditProductos]
    );

    const apiMapping: Record<string, string> = {
        "marca": "marcaIds",
        "rubro": "clasifGralIds",
        "subrubro": "clasifGastroIds",
        "tipo": "tipoIds",
        "proveedor": "proveedorIds",
        "origen": "origenIds",
        "material": "materialIds",
        "mlaId": "mla",
        "catalogo": "catalogoIds",
        "apto": "aptoIds",
        "cliente": "clienteIds",
        "tag": "tags",
    };

    // Campos que el backend acepta como valor único (no array)
    const singleValueFields = ["activo", "esCombo", "tagReposicion", "sku", "codExt", "tituloDux", "mla"];

    // Labels para mostrar los filtros activos
    const filterLabels: Record<string, string> = {
        search: "Búsqueda", marcaIds: "Marca", clasifGralIds: "Rubro", clasifGastroIds: "Gastro",
        tipoIds: "Tipo", proveedorIds: "Proveedor", origenIds: "Origen", materialIds: "Material",
        mla: "MLA", sku: "SKU", codExt: "Cód. Ext.", tituloDux: "Título Dux",
        activo: "Activo", esCombo: "Combo", tagReposicion: "Tag Rep.",
        catalogoIds: "Catálogo", aptoIds: "Apto", clienteIds: "Cliente", tags: "Tag",
    };

    const [filterValueLabels, setFilterValueLabels] = useState<Record<string, Record<string, string>>>({});

    // Resolver nombres de IDs para filtros activos
    const filterLoaders: Record<string, (q: string) => Promise<any>> = useMemo(() => ({
        marcaIds: (q: string) => searchMarcas(q, 9999),
        clasifGralIds: (q: string) => searchClasifGral(q, 9999),
        clasifGastroIds: (q: string) => searchClasifGastro(q, 9999),
        tipoIds: (q: string) => searchTipos(q, 9999),
        proveedorIds: (q: string) => searchProveedores(q, 9999),
        origenIds: (q: string) => searchOrigenes(q, 9999),
        materialIds: (q: string) => searchMateriales(q, 9999),
        catalogoIds: (q: string) => searchCatalogos(q, 9999),
        aptoIds: (q: string) => searchAptos(q, 9999),
        clienteIds: (q: string) => searchClientes(q, 9999),
        canalIds: (q: string) => searchCanales(q, 9999),
        mlaIds: (q: string) => searchMlas(q, 9999),
        tags: async () => [
            { id: "MAQUINA", label: "Máquina" },
            { id: "REPUESTO", label: "Repuesto" },
            { id: "MENAJE", label: "Menaje" },
            { id: "INSUMO", label: "Insumo" },
        ],
    }), []);

    useEffect(() => {
        const arrayFilters = Object.entries(filters).filter(
            ([key, value]) => Array.isArray(value) && value.length > 0 && filterLoaders[key] && !filterValueLabels[key]
        );
        if (arrayFilters.length === 0) return;
        for (const [key] of arrayFilters) {
            filterLoaders[key]("").then((res: any) => {
                const list: { id: any; label: string }[] = Array.isArray(res) ? res : (res.content || []);
                const labels: Record<string, string> = {};
                for (const item of list) labels[String(item.id)] = item.label;
                setFilterValueLabels((prev) => ({ ...prev, [key]: labels }));
            });
        }
    }, [filters, filterLoaders, filterValueLabels]);

    const formatFilterValue = (key: string, value: any): string => {
        if (typeof value === "boolean") return value ? "Sí" : "No";
        if (Array.isArray(value)) {
            const labelsMap = filterValueLabels[key];
            if (labelsMap) {
                const names = value.map((id) => labelsMap[String(id)]).filter(Boolean);
                if (names.length > 0) return names.join(", ");
            }
            return `${value.length} seleccionados`;
        }
        return String(value);
    };

    const activeFilterEntries = Object.entries(filters).filter(([key, value]) => {
        if (key === "search") return false;
        if (value === undefined || value === null || value === "") return false;
        if (Array.isArray(value) && value.length === 0) return false;
        return true;
    });
    const hasActiveFilters = activeFilterEntries.length > 0;

    const clearFilter = (apiParam: string) => {
        setFilters((prev: any) => {
            const next = { ...prev };
            delete next[apiParam];
            return next;
        });
        setFilterValueLabels((prev) => {
            const next = { ...prev };
            delete next[apiParam];
            return next;
        });
        setPageIndex(0);
    };

    const clearAllFilters = () => {
        setFilters({});
        setFilterValueLabels({});
        setPageIndex(0);
    };

    const getCurrentColumnVisibility = () => {
        if (typeof window === "undefined") return {};
        try {
            const saved = window.localStorage.getItem("columnVisibility_productos");
            return saved ? JSON.parse(saved) : {};
        } catch {
            return {};
        }
    };

    const openSaveView = () => {
        setViewName("");
        setIsViewModalOpen(true);
    };

    const closeSaveView = () => {
        setIsViewModalOpen(false);
        setViewName("");
    };

    const handleSaveView = () => {
        const trimmed = viewName.trim();
        if (!trimmed) {
            toast.error("Poné un nombre para la vista");
            return;
        }

        const nextView: ProductosView = {
            id: `${Date.now()}`,
            name: trimmed,
            filters,
            sorting,
            columnVisibility: getCurrentColumnVisibility(),
            createdAt: new Date().toISOString(),
        };

        const nextViews = [nextView, ...savedViews.filter((view) => view.name.toLowerCase() !== trimmed.toLowerCase())];
        setSavedViews(nextViews);
        writeProductosViews(nextViews);
        setSelectedViewId(nextView.id);
        closeSaveView();
        notificar.success(`Vista "${trimmed}" guardada`);
    };

    const handleApplyView = (viewId: string) => {
        const view = savedViews.find((item) => item.id === viewId);
        if (!view) return;
        setFilters(view.filters);
        setSorting(view.sorting);
        setPageIndex(0);
        if (typeof window !== "undefined") {
            window.localStorage.setItem("columnVisibility_productos", JSON.stringify(view.columnVisibility || {}));
        }
        setColumnVisibilityVersion((prev) => prev + 1);
        setSelectedViewId(view.id);
        notificar.success(`Vista "${view.name}" aplicada`);
    };

    const handleDeleteView = async () => {
        if (!selectedViewId) return;
        const view = savedViews.find((item) => item.id === selectedViewId);
        if (!view) return;
        if (!(await confirmDialog({ title: "Eliminar vista", message: `¿Eliminar la vista "${view.name}"?`, confirmText: "Eliminar", variant: "danger" }))) return;
        const nextViews = savedViews.filter((item) => item.id !== selectedViewId);
        setSavedViews(nextViews);
        writeProductosViews(nextViews);
        setSelectedViewId("");
        notificar.success("Vista eliminada");
    };

    useEffect(() => {
        setActiveOverrides({});
    }, [productos]);

    const validateForm = (): boolean => {
        const errors: Record<string, string> = {};
        if (!sku.trim()) errors.sku = "El SKU es obligatorio";
        else if (sku.trim().length > 45) errors.sku = "Máximo 45 caracteres";
        else if (!editandoProductoId && skuYaExiste) errors.sku = "Ya existe un producto con este SKU";
        if (!tituloDux.trim()) errors.tituloDux = "El Título Dux es obligatorio";
        else if (tituloDux.trim().length > 100) errors.tituloDux = "Máximo 100 caracteres";
        if (tituloMl.trim().length > 100) errors.tituloMl = "Máximo 100 caracteres";
        if (tituloNube.trim().length > 100) errors.tituloNube = "Máximo 100 caracteres";
        if (costo === "" || Number(costo) <= 0) errors.costo = "El costo debe ser mayor a 0";
        if (uxb < 1) errors.uxb = "UxB debe ser al menos 1";
        if (!clasifGralId && !clasifGastroId) errors.clasificacion = "Seleccioná al menos una clasificación (general o gastronómica)";
        if (!tipoId) errors.tipoId = "El tipo es obligatorio";
        if (!esCombo) {
            if (!marcaId) errors.marcaId = "La marca es obligatoria";
            if (!origenId) errors.origenId = "El origen es obligatorio";
            if (!proveedorId) errors.proveedorId = "El proveedor es obligatorio";
            if (!materialId) errors.materialId = "El material es obligatorio";
            if (!tag) errors.tag = "El tag es obligatorio";
            const tieneMargen = (margenMinorista !== "" && Number(margenMinorista) > 0) || (margenMayorista !== "" && Number(margenMayorista) > 0);
            if (!tieneMargen) errors.margen = "Cargá al menos un margen (minorista o mayorista) mayor a 0";
        }
        if (largo.length > 45) errors.largo = "Máximo 45 caracteres";
        if (ancho.length > 45) errors.ancho = "Máximo 45 caracteres";
        if (alto.length > 45) errors.alto = "Máximo 45 caracteres";
        setFormErrors(errors);
        return Object.keys(errors).length === 0;
    };

    // Tras crear el producto: guarda los márgenes (si hay) y asocia catálogos,
    // aptos y clientes. Los errores acá no invalidan el producto ya creado.
    const asociarMargenYRelaciones = async (productoId: number) => {
        // Solo enviamos los márgenes cargados (omitimos los vacíos en vez de
        // mandarlos como null). Así se puede crear con uno solo: el backend
        // defaultea a 0 el margen ausente. Mandar null explícito daba error.
        const margenDto: { margenMinorista?: number; margenMayorista?: number } = {};
        if (margenMinorista !== "") margenDto.margenMinorista = margenMinorista;
        if (margenMayorista !== "") margenDto.margenMayorista = margenMayorista;
        if (Object.keys(margenDto).length > 0) {
            try {
                await updateProductoMargenAPI(productoId, margenDto);
            } catch {
                notificar.error("El producto se creó, pero falló al guardar los márgenes");
            }
        }
        try {
            await Promise.all([
                ...catalogosSel.map((c) => addProductoCatalogoAPI(productoId, Number(c.id))),
                ...aptosSel.map((a) => addProductoAptoAPI(productoId, Number(a.id))),
                ...clientesSel.map((c) => addProductoClienteAPI(productoId, Number(c.id))),
            ]);
        } catch {
            notificar.error("El producto se creó, pero falló al asociar algún catálogo/apto/cliente");
        }
        if (preciosInfladosSel.length > 0) {
            try {
                await Promise.all(preciosInfladosSel.map((d) => asignarPrecioInfladoAPI(
                    productoId, d.canalId, d.precioInfladoId,
                    { fechaDesde: d.fechaDesde, fechaHasta: d.fechaHasta, observaciones: d.observaciones },
                )));
            } catch {
                notificar.error("El producto se creó, pero falló al asignar algún precio inflado");
            }
        }
    };

    const handleCreate = async () => {
        if (!validateForm()) return;
        try {
            setIsSaving(true);
            const costoNum = costo === "" ? 0 : costo;
            // Fallback: si el usuario cargó un MLA nuevo a mano pero no lo asoció
            // (ni con "Crear" ni con "Obtener de ML"), lo creamos ahora.
            let mlaIdFinal = mlaId;
            if (showNuevoMla && mlaCodigo.trim() && !mlaId) {
                try {
                    const mlaCreado = await createMlaAPI({
                        mla: mlaCodigo.trim(),
                        mlau: mlaMlau.trim() || null,
                        precioEnvio: mlaPrecioEnvio === "" ? null : mlaPrecioEnvio,
                        comisionPorcentaje: mlaComision === "" ? null : mlaComision,
                        topePromocion: mlaTope === "" ? 0 : mlaTope,
                    });
                    mlaIdFinal = mlaCreado.id;
                } catch (e) {
                    notificar.error(e instanceof Error ? e.message : "Error al crear el MLA");
                    setIsSaving(false);
                    return;
                }
            }
            const payload: ProductoCreateDTO = {
                sku: sku.trim(), codExt, tituloDux: tituloDux.trim(), tituloMl: tituloMl.trim() || null, tituloNube: tituloNube.trim() || null, esCombo, uxb, activo, imagenUrl,
                capacidad, largo: largo || null, ancho: ancho || null, alto: alto || null,
                diamboca: diamboca || null, diambase: diambase || null, espesor: espesor || null,
                costo: costoNum, iva,
                stock: stock !== "" ? stock : null,
                moq: moq !== "" ? moq : null,
                tagReposicion: tagReposicion || null,
                tag: tag || null,
                marcaId, origenId, clasifGralId: clasifGralId!, clasifGastroId, tipoId: tipoId!, proveedorId, materialId, mlaId: mlaIdFinal
            };
            const creado = await createProducto(payload, asociarMargenYRelaciones);
            // Si el producto quedó asociado a un MLA, ahora SÍ se puede calcular su
            // precio de envío (el cálculo necesita el producto). Se dispara en segundo
            // plano; el resultado se refleja luego en el Monitor de Precios.
            if (mlaIdFinal && creado?.id) {
                calcularEnvioMlaAPI(creado.id).catch(() => { /* se recalcula luego desde ML */ });
            }
            // Subida a Dux (opcional, no invalida el producto ya creado).
            if (subirADux && canExportarDux) {
                try {
                    const resultadoDux = await exportarProductosADuxAPI([sku.trim()]);
                    if (resultadoDux.productosEnviados > 0) {
                        notificar.success(`Producto ${sku.trim()} enviado a Dux`);
                    } else {
                        const detalle = resultadoDux.errores?.length ? `: ${resultadoDux.errores.join("; ")}` : "";
                        notificar.error(`El producto se creó, pero no se subió a Dux${detalle}`);
                    }
                } catch (e) {
                    notificar.error(e instanceof Error ? `El producto se creó, pero falló al subirlo a Dux: ${e.message}` : "El producto se creó, pero falló al subirlo a Dux");
                }
            }
            // Subida a Tienda Nube (KT HOGAR / KT GASTRO).
            const tiendasNube: { tienda: "KT HOGAR" | "KT GASTRO"; cuotas: number }[] = [];
            if (subirKtHogar) tiendasNube.push({ tienda: "KT HOGAR", cuotas: cuotaHogar });
            if (subirKtGastro) tiendasNube.push({ tienda: "KT GASTRO", cuotas: cuotaGastro });
            if (tiendasNube.length && canExportarDux) {
                try {
                    const r = await exportarProductosANubeAPI([sku.trim()], tiendasNube);
                    reportarExportToast("Tienda Nube", r);
                } catch (e) {
                    notificar.error(`Tienda Nube: ${e instanceof Error ? e.message : "error al subir"}`);
                }
            }
            if (subirMl && canExportarDux) {
                try {
                    const r = await exportarProductosAMlAPI([sku.trim()]);
                    reportarExportToast("Mercado Libre", r);
                } catch (e) {
                    notificar.error(`Mercado Libre: ${e instanceof Error ? e.message : "error al subir"}`);
                }
            }
            resetForm();
            setIsModalOpen(false);
        } catch (e) { /* hook already toasts */ } finally { setIsSaving(false); }
    };

    // Abre el modal en modo edición: precarga todos los campos del producto.
    const abrirEdicion = async (producto: ProductoDTO) => {
        setEditandoProductoId(producto.id);
        setPanelTab("datos");
        setSku(producto.sku ?? "");
        setCodExt(producto.codExt ?? "");
        setTituloDux(producto.tituloDux ?? "");
        setTituloMl(producto.tituloMl ?? "");
        setTituloNube(producto.tituloNube ?? "");
        setImagenUrl(producto.imagenUrl ?? "");
        setEsCombo(!!producto.esCombo);
        setUxb(producto.uxb ?? 1);
        setActivo(!!producto.activo);
        setSubirADux(false);
        setSubirKtHogar(false); setSubirKtGastro(false); setSubirMl(false);
        setCapacidad(producto.capacidad ?? "");
        setLargo(producto.largo ?? ""); setAncho(producto.ancho ?? ""); setAlto(producto.alto ?? "");
        setDiamboca(producto.diamboca ?? ""); setDiambase(producto.diambase ?? ""); setEspesor(producto.espesor ?? "");
        setCosto(producto.costo ?? ""); setIva(producto.iva ?? 21);
        setStock(producto.stock ?? ""); setMoq(producto.moq ?? "");
        setTagReposicion((producto.tagReposicion as "" | "PRIO" | "LIQ") ?? "");
        setTag((producto.tag as "" | "MAQUINA" | "REPUESTO" | "MENAJE" | "INSUMO") ?? "");
        setMarcaId(producto.marcaId ?? null); setOrigenId(producto.origenId ?? null);
        setClasifGralId(producto.clasifGralId ?? null); setClasifGastroId(producto.clasifGastroId ?? null);
        setTipoId(producto.tipoId ?? null); setProveedorId(producto.proveedorId ?? null);
        setMaterialId(producto.materialId ?? null); setMlaId(producto.mlaId ?? null);
        // Displays: marca/clasif/tipo traen *NombreCompleto; mla trae mlaNombre.
        setMarcaDisplay(producto.marcaNombreCompleto ?? "");
        setClasifGralDisplay(producto.clasifGralNombreCompleto ?? "");
        setClasifGastroDisplay(producto.clasifGastroNombreCompleto ?? "");
        setTipoDisplay(producto.tipoNombreCompleto ?? "");
        setMlaDisplay(producto.mlaNombre ?? "");
        // Origen/material/proveedor no traen nombre en el DTO: se resuelven por id.
        setOrigenDisplay(""); setMaterialDisplay(""); setProveedorDisplay("");
        setMargenMinorista(producto.margenMinorista ?? ""); setMargenMayorista(producto.margenMayorista ?? "");
        setFormErrors({});
        setCatalogosSel([]); setAptosSel([]); setClientesSel([]);
        setCatalogosOriginal([]); setAptosOriginal([]); setClientesOriginal([]);
        setShowNuevoMla(false);
        setIsModalOpen(true);

        // Nombres de origen/material/proveedor (no vienen en el DTO de la tabla).
        if (producto.origenId) getNombreById("origenes", producto.origenId).then(r => setOrigenDisplay(r.nombre)).catch(() => {});
        if (producto.materialId) getNombreById("materiales", producto.materialId).then(r => setMaterialDisplay(r.nombre)).catch(() => {});
        if (producto.proveedorId) getNombreById("proveedores", producto.proveedorId).then(r => setProveedorDisplay(r.nombre)).catch(() => {});

        // Relaciones N-a-N: los GET dan ids; cruzamos con getAll* para los nombres.
        try {
            const [aptosAsig, allAptos, catAsig, allCat, cliAsig, allCli] = await Promise.all([
                getProductoAptosAPI(producto.id), getAllAptosAPI(),
                getProductoCatalogosAPI(producto.id), getAllCatalogosAPI(),
                getProductoClientesAPI(producto.id), getAllClientesAPI(),
            ]);
            const aptos: MultiOption[] = aptosAsig.map(a => ({ id: a.aptoId, label: allAptos.find(x => x.id === a.aptoId)?.nombre ?? String(a.aptoId) }));
            const catalogos: MultiOption[] = catAsig.map(c => ({ id: c.catalogoId, label: allCat.find(x => x.id === c.catalogoId)?.nombre ?? String(c.catalogoId) }));
            const clientes: MultiOption[] = cliAsig.map(c => ({ id: c.clienteId, label: allCli.find(x => x.id === c.clienteId)?.nombre ?? String(c.clienteId) }));
            setAptosSel(aptos); setAptosOriginal(aptos);
            setCatalogosSel(catalogos); setCatalogosOriginal(catalogos);
            setClientesSel(clientes); setClientesOriginal(clientes);
        } catch {
            notificar.error("No se pudieron cargar catálogos/aptos/clientes del producto");
        }
    };
    // Mantener la ref apuntando a la última versión de abrirEdicion.
    abrirEdicionRef.current = abrirEdicion;

    // Guarda la edición: PATCH del producto + márgenes + diff de relaciones N-a-N.
    const handleGuardarEdicion = async () => {
        if (!validateForm() || editandoProductoId == null) return;
        try {
            setIsSaving(true);
            const id = editandoProductoId;
            const costoNum = costo === "" ? 0 : costo;
            const patch = {
                codExt, tituloDux: tituloDux.trim(), tituloMl: tituloMl.trim() || null, tituloNube: tituloNube.trim() || null, esCombo, uxb, activo, imagenUrl,
                capacidad, largo: largo || null, ancho: ancho || null, alto: alto || null,
                diamboca: diamboca || null, diambase: diambase || null, espesor: espesor || null,
                costo: costoNum, iva, stock: stock !== "" ? stock : null, moq: moq !== "" ? moq : null,
                tagReposicion: tagReposicion || null, tag: tag || null,
                marcaId, origenId, clasifGralId, clasifGastroId, tipoId, proveedorId, materialId, mlaId,
            } as ProductoPatchDTO;
            await updateProductoAPI(id, patch, "FORM");

            // Solo enviamos los márgenes cargados (omitimos los vacíos en vez de
            // mandarlos como null). Para combos sin márgenes, omitir la llamada
            // evita el 400 que devuelve el backend al recibir null explícito.
            const margenDto: { margenMinorista?: number; margenMayorista?: number } = {};
            if (margenMinorista !== "") margenDto.margenMinorista = margenMinorista;
            if (margenMayorista !== "") margenDto.margenMayorista = margenMayorista;
            if (Object.keys(margenDto).length > 0) {
                try {
                    await updateProductoMargenAPI(id, margenDto);
                } catch {
                    notificar.error("El producto se actualizó, pero falló al guardar los márgenes");
                }
            }

            const diff = (orig: MultiOption[], curr: MultiOption[]) => {
                const oid = new Set(orig.map(o => Number(o.id)));
                const cid = new Set(curr.map(c => Number(c.id)));
                return {
                    add: curr.filter(c => !oid.has(Number(c.id))).map(c => Number(c.id)),
                    remove: orig.filter(o => !cid.has(Number(o.id))).map(o => Number(o.id)),
                };
            };
            const dCat = diff(catalogosOriginal, catalogosSel);
            const dApt = diff(aptosOriginal, aptosSel);
            const dCli = diff(clientesOriginal, clientesSel);
            await Promise.all([
                ...dCat.add.map(x => addProductoCatalogoAPI(id, x)),
                ...dCat.remove.map(x => removeProductoCatalogoAPI(id, x)),
                ...dApt.add.map(x => addProductoAptoAPI(id, x)),
                ...dApt.remove.map(x => removeProductoAptoAPI(id, x)),
                ...dCli.add.map(x => addProductoClienteAPI(id, x)),
                ...dCli.remove.map(x => removeProductoClienteAPI(id, x)),
            ]);

            notificar.success(`Producto ${sku} actualizado`);

            // Actualización en Dux (opcional, no invalida los cambios ya guardados).
            // El endpoint de Dux (nuevoItem) hace upsert por cod_item: si el SKU ya
            // existe, lo actualiza con los datos actuales del producto.
            if (subirADux && canExportarDux) {
                try {
                    const resultadoDux = await exportarProductosADuxAPI([sku.trim()]);
                    if (resultadoDux.productosEnviados > 0) {
                        notificar.success(`Producto ${sku} actualizado en Dux`);
                    } else {
                        const detalle = resultadoDux.errores?.length ? `: ${resultadoDux.errores.join("; ")}` : "";
                        notificar.error(`No se actualizó en Dux${detalle}`);
                    }
                } catch (e) {
                    notificar.error(e instanceof Error ? `Falló la actualización en Dux: ${e.message}` : "Falló la actualización en Dux");
                }
            }
            // Subida a Tienda Nube (KT HOGAR / KT GASTRO) — en edición reportará "ya existía" si corresponde.
            const tiendasNubeEdit: { tienda: "KT HOGAR" | "KT GASTRO"; cuotas: number }[] = [];
            if (subirKtHogar) tiendasNubeEdit.push({ tienda: "KT HOGAR", cuotas: cuotaHogar });
            if (subirKtGastro) tiendasNubeEdit.push({ tienda: "KT GASTRO", cuotas: cuotaGastro });
            if (tiendasNubeEdit.length && canExportarDux) {
                try {
                    const r = await exportarProductosANubeAPI([sku.trim()], tiendasNubeEdit);
                    reportarExportToast("Tienda Nube", r);
                } catch (e) {
                    notificar.error(`Tienda Nube: ${e instanceof Error ? e.message : "error al subir"}`);
                }
            }
            if (subirMl && canExportarDux) {
                try {
                    const r = await exportarProductosAMlAPI([sku.trim()]);
                    reportarExportToast("Mercado Libre", r);
                } catch (e) {
                    notificar.error(`Mercado Libre: ${e instanceof Error ? e.message : "error al subir"}`);
                }
            }

            resetForm();
            setEditandoProductoId(null);
            setIsModalOpen(false);
            await refresh();
        } catch (e) {
            notificar.error(e instanceof Error ? e.message : "Error al guardar los cambios");
        } finally { setIsSaving(false); }
    };

    // Pide al backend el menor SKU libre del rango y lo carga en el form.
    // Si el rango está lleno (sku null) deja el campo vacío y avisa.
    const cargarSkuSugerido = useCallback(async (combo: boolean) => {
        try {
            const sugerido = await getSiguienteSkuAPI(combo);
            if (sugerido) {
                setSku(sugerido);
                setLastSuggestedSku(sugerido);
                setFormErrors((p) => ({ ...p, sku: "" }));
            } else {
                setLastSuggestedSku("");
                toast.warning(`No hay SKU libre en el rango ${combo ? "5000000–5999999" : "1000000–1999999"}. Cargalo manualmente.`);
            }
        } catch {
            // Silencioso: el usuario siempre puede tipear el SKU a mano.
        }
    }, []);

    const handleAbrirCrear = () => {
        setPanelTab("datos");
        setIsModalOpen(true);
        void cargarSkuSugerido(esCombo);
    };

    // Al cambiar "Es Combo" recalculamos el SKU sólo si el campo está vacío o
    // sigue siendo el que sugerimos (no pisamos un SKU escrito a mano).
    const handleToggleCombo = (next: boolean) => {
        setEsCombo(next);
        // Al togglear esCombo cambian los campos obligatorios: limpiamos los
        // errores de los que dejan de serlo para que no queden marcados.
        setFormErrors(p => {
            const n = { ...p };
            delete n.marcaId; delete n.origenId; delete n.proveedorId;
            delete n.materialId; delete n.tag; delete n.margen;
            return n;
        });
        if (sku === "" || sku === lastSuggestedSku) {
            void cargarSkuSugerido(next);
        }
    };

    // Aviso en vivo de SKU duplicado: con un pequeño debounce consultamos al
    // backend si el SKU tipeado ya pertenece a otro producto. Solo mientras el
    // modal de alta está abierto; abortamos la consulta anterior al re-tipear.
    useEffect(() => {
        // En edición el SKU es solo lectura (es el del propio producto): no validamos duplicado.
        if (!isModalOpen || editandoProductoId) { setSkuYaExiste(false); return; }
        const valor = sku.trim();
        if (!valor) { setSkuYaExiste(false); return; }
        const controller = new AbortController();
        const t = setTimeout(async () => {
            try {
                setSkuYaExiste(await existeSkuAPI(valor, controller.signal));
            } catch {
                // Silencioso: si falla la verificación, el backend igual rechaza al crear.
            }
        }, 400);
        return () => { clearTimeout(t); controller.abort(); };
    }, [sku, isModalOpen, editandoProductoId]);

    // Autocompleta la imagen cuando el nombre del archivo coincide con el SKU.
    // Solo en alta y mientras el usuario no haya elegido una imagen a mano.
    useEffect(() => {
        if (!isModalOpen || editandoProductoId || imagenTocadaManualmenteRef.current) return;
        const skuTrim = sku.trim();
        if (!skuTrim || imagenUrl) return;
        const controller = new AbortController();
        const t = setTimeout(async () => {
            try {
                const res = await fetch(`${API_BASE_URL}/api/imagenes/listar?search=${encodeURIComponent(skuTrim)}`, { signal: controller.signal });
                if (!res.ok) return;
                const data = await res.json();
                const archivos: string[] = Array.isArray(data?.archivos) ? data.archivos : [];
                // El backend filtra case-insensitive: comparamos en minúsculas para
                // que abc123.png matchee el SKU ABC123 (y viceversa).
                const match = archivos.find(a => a.replace(/\.[^.]+$/, "").toLowerCase() === skuTrim.toLowerCase());
                if (match) setImagenUrl(match);
            } catch { /* abort o sin match: ignorar */ }
        }, 400);
        return () => { controller.abort(); clearTimeout(t); };
    }, [sku, isModalOpen, editandoProductoId, imagenUrl]);

    const aplicarMlaEnForm = (mla: { id: number; mla: string; mlau: string | null; precioEnvio: number | null; comisionPorcentaje: number | null; topePromocion: number | null }) => {
        setMlaId(mla.id);
        setMlaDisplay(mla.mla);
        setMlaCodigo(mla.mla);
        setMlaMlau(mla.mlau ?? "");
        setMlaPrecioEnvio(mla.precioEnvio ?? "");
        setMlaTope(mla.topePromocion ?? "");
        setMlaComision(mla.comisionPorcentaje ?? "");
    };

    // Busca la publicación en ML por el SKU del form: crea/asegura el MLA, calcula
    // envío + comisión y lo deja seleccionado.
    const handleObtenerMlaDeML = async () => {
        if (!sku.trim()) {
            toast.error("Cargá primero el SKU para buscar en MercadoLibre");
            return;
        }
        setObteniendoMla(true);
        try {
            const mla = await getMlaPorSkuAPI(sku.trim());
            aplicarMlaEnForm(mla);
            notificar.success(`MLA ${mla.mla} obtenido de MercadoLibre`);
        } catch (e) {
            notificar.error(e instanceof Error ? e.message : "Error al obtener el MLA");
        } finally {
            setObteniendoMla(false);
        }
    };

    // Crea un MLA manual con los datos cargados y lo deja seleccionado.
    const handleCrearMla = async () => {
        if (!mlaCodigo.trim()) {
            toast.error("Ingresá el código MLA");
            return;
        }
        setCreandoMla(true);
        try {
            const mla = await createMlaAPI({
                mla: mlaCodigo.trim(),
                mlau: mlaMlau.trim() || null,
                precioEnvio: mlaPrecioEnvio === "" ? null : mlaPrecioEnvio,
                comisionPorcentaje: mlaComision === "" ? null : mlaComision,
                topePromocion: mlaTope === "" ? 0 : mlaTope,
            });
            setMlaId(mla.id);
            setMlaDisplay(mla.mla);
            notificar.success(`MLA ${mla.mla} creado`);
            setShowNuevoMla(false);
        } catch (e) {
            notificar.error(e instanceof Error ? e.message : "Error al crear el MLA");
        } finally {
            setCreandoMla(false);
        }
    };

    const resetForm = () => {
        setSku(""); setLastSuggestedSku(""); setCodExt(""); setTituloDux(""); setTituloMl(""); setTituloNube(""); setImagenUrl("");
        setEsCombo(false); setUxb(1); setActivo(true); setSubirADux(true);
        setSubirKtHogar(false); setSubirKtGastro(false); setSubirMl(false);
        setCapacidad(""); setLargo(""); setAncho(""); setAlto(""); setDiamboca(""); setDiambase(""); setEspesor("");
        setCosto(""); setIva(21.0);
        setMarcaId(null); setOrigenId(null); setClasifGralId(null); setClasifGastroId(null);
        setTipoId(null); setProveedorId(null); setMaterialId(null); setMlaId(null);
        setMarcaDisplay(""); setOrigenDisplay(""); setClasifGralDisplay(""); setClasifGastroDisplay("");
        setTipoDisplay(""); setProveedorDisplay(""); setMaterialDisplay("");
        setCatalogosOriginal([]); setAptosOriginal([]); setClientesOriginal([]);
        setMoq(""); setStock(0); setTagReposicion(""); setTag("");
        setMlaDisplay(""); setShowNuevoMla(false);
        setMlaCodigo(""); setMlaMlau(""); setMlaPrecioEnvio(""); setMlaTope(""); setMlaComision("");
        setMargenMinorista(""); setMargenMayorista("");
        setCatalogosSel([]); setAptosSel([]); setClientesSel([]);
        setPreciosInfladosSel([]);
        imagenTocadaManualmenteRef.current = false;
        setFormErrors({});
    };

    const handleDelete = async () => {
        const skus = selectedIds.map(i => productos.find(p => p.id === i)?.sku).filter(Boolean);
        const detalle = skus.length <= 3 ? skus.join(", ") : `${skus.slice(0, 3).join(", ")} y ${skus.length - 3} más`;
        if (!(await confirmDialog({ title: "Eliminar productos", message: `¿Eliminar ${selectedIds.length === 1 ? `"${detalle}"` : `${selectedIds.length} productos (${detalle})`}?`, confirmText: "Eliminar", variant: "danger" }))) return;
        try { await deleteProducto(selectedIds); setRowSelection({}); } catch (e) { /* hook already toasts */ }
    };

    const MARGEN_FIELDS = new Set(["margenMinorista", "margenMayorista"]);

    const handleUpdate = async (rowIndex: number, columnId: string, value: unknown) => {
        const itemOriginal = productos[rowIndex];
        if (!itemOriginal) return;

        if (columnId === "activo") {
            setActiveOverrides((prev) => ({ ...prev, [itemOriginal.id]: Boolean(value) }));
        }

        try {
            if (MARGEN_FIELDS.has(columnId)) {
                const numValue = value === "" || value === null || value === undefined ? null : Number(value);
                await updateProductoMargen(itemOriginal.id, { [columnId]: numValue });
            } else {
                await updateProducto(itemOriginal.id, { [columnId]: value } as ProductoPatchDTO);
            }
        } catch {
            if (columnId === "activo") {
                setActiveOverrides((prev) => {
                    const next = { ...prev };
                    delete next[itemOriginal.id];
                    return next;
                });
            }
        }
    };

    const handleGlobalSearch = (valor: string) => {
        setFilters((prev: any) => ({ ...prev, search: valor }));
        setPageIndex(0);
    };

    const handleColumnFilterChange = (columnId: string, value: any, labels?: Record<string, string>) => {
        const apiParam = apiMapping[columnId] || columnId;

        // Booleanos/enums: extraer valor único del array
        let finalValue = value;
        if (singleValueFields.includes(apiParam) && Array.isArray(value)) {
            finalValue = value.length === 1 ? value[0] : undefined;
        }

        if (labels) {
            setFilterValueLabels((prev) => ({ ...prev, [apiParam]: labels }));
        }

        setFilters((prev: any) => {
            const newFilters = { ...prev };
            if (finalValue === undefined || finalValue === null || finalValue === "" || (Array.isArray(finalValue) && finalValue.length === 0)) {
                delete newFilters[apiParam];
            } else {
                newFilters[apiParam] = finalValue;
            }
            return newFilters;
        });
        setPageIndex(0);
    };

    // Variante usada por el panel de filtros: recibe el apiParam ya resuelto
    // (no pasa por apiMapping) y el valor con su tipo final — array para
    // multi-selección, valor único o null para los segmentados.
    const handlePanelFilterChange = (apiParam: string, value: unknown, labels?: Record<string, string>) => {
        if (labels) {
            setFilterValueLabels((prev) => ({ ...prev, [apiParam]: labels }));
        }
        setFilters((prev: Record<string, unknown>) => {
            const newFilters = { ...prev };
            if (value === undefined || value === null || value === "" || (Array.isArray(value) && value.length === 0)) {
                delete newFilters[apiParam];
            } else {
                newFilters[apiParam] = value;
            }
            return newFilters;
        });
        setPageIndex(0);
    };

    const sectionClassName = "rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900";
    const sectionTitleClassName = "flex items-center gap-2 text-base font-semibold text-slate-900 dark:text-slate-100 [&_svg]:h-5 [&_svg]:w-5 [&_svg]:text-blue-500 dark:[&_svg]:text-blue-400";
    const sectionDescriptionClassName = "mt-1 text-xs text-slate-500 dark:text-slate-400";
    const fieldLabelClassName = "block text-sm font-semibold text-slate-700 dark:text-slate-200";
    const inputBaseClassName = "mt-1 w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-900 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100 dark:focus:ring-blue-500/20";
    const inputErrorClassName = "border-red-300 bg-red-50 focus:border-red-400 focus:ring-red-100 dark:border-red-700 dark:bg-red-950/20 dark:focus:ring-red-500/20";
    const checkboxCardClassName = "flex items-center gap-3 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm font-semibold text-slate-700 dark:border-slate-700 dark:bg-slate-800/80 dark:text-slate-200";
    const selectBaseClassName = `${inputBaseClassName} appearance-none`;

    return (
        <main className="min-h-0 flex flex-col bg-gray-50 p-4 overflow-hidden">
            <div className="flex justify-between items-center mb-4 shrink-0">
                <div className="flex min-w-0 flex-wrap items-center gap-3">
                    <h1 className="inline-flex items-center gap-2 leading-none text-3xl font-bold text-gray-800">
                        <CubeIcon className="w-8 h-8 text-gray-600" />
                        Productos
                    </h1>
                    <div className="flex flex-wrap items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2 shadow-sm dark:border-slate-700 dark:bg-slate-900">
                        <div className="flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200">
                            <BookmarkIcon className="h-4 w-4 text-slate-400" />
                            Vistas
                        </div>
                        <select
                            value={selectedViewId}
                            onChange={(e) => setSelectedViewId(e.target.value)}
                            className="min-w-[220px] rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100"
                        >
                            <option value="">Seleccionar vista guardada...</option>
                            {savedViews.map((view) => (
                                <option key={view.id} value={view.id}>{view.name}</option>
                            ))}
                        </select>
                        <Button variant="outline" onClick={() => { if (selectedViewId) handleApplyView(selectedViewId); }} disabled={!selectedViewId}>
                            Aplicar
                        </Button>
                        <Button variant="light" onClick={openSaveView}>
                            Guardar
                        </Button>
                        <Button variant="danger" onClick={handleDeleteView} disabled={!selectedViewId}>
                            Borrar
                        </Button>
                    </div>
                </div>
                <div className="flex gap-2 items-center">
                    {canEditProductos && hasSelection && (
                        <DeleteButton onClick={handleDelete}>
                            Borrar ({selectedIds.length})
                        </DeleteButton>
                    )}
                    <CreateButton onClick={handleAbrirCrear} disabled={!canEditProductos}>
                        Crear Producto
                    </CreateButton>
                </div>
            </div>

            <div className="flex-1 flex flex-col min-h-0 overflow-hidden">
                <Table
                    searchSlot={(
                        <div className="flex items-center gap-2">
                            <SearchInput placeholder="Buscar producto por SKU, MLA, cód. ext. o título..." onSearch={(val) => { if (val !== filters.search) handleGlobalSearch(val); }} initialValue={filters.search} className="w-[28rem] max-w-full" />
                            <ProductosFilterToggle expanded={filtrosExpanded} onToggle={toggleFiltros} activeCount={activeFilterEntries.length} />
                        </div>
                    )}
                    belowToolbarSlot={filtrosExpanded ? (
                        <ProductosFilterPanel
                            filters={filters}
                            filterValueLabels={filterValueLabels}
                            onFilterChange={handlePanelFilterChange}
                            onClearAll={clearAllFilters}
                            activeCount={activeFilterEntries.length}
                        />
                    ) : null}
                    tableId="productos"
                    data={productos}
                    isLoading={isLoading}
                    columns={columns}
                    globalFilter={filters.search}
                    setGlobalFilter={handleGlobalSearch}
                    pageCount={pageCount}
                    pageIndex={pageIndex}
                    pageSize={pageSize}
                    onPageChange={setPageIndex}
                    onPageSizeChange={setPageSize}
                    totalRecords={totalRecords}
                    sorting={sorting}
                    setSorting={setSorting}
                    rowSelection={rowSelection}
                    setRowSelection={setRowSelection}
                    updateData={handleUpdate}
                    onColumnFilterChange={handleColumnFilterChange}
                    filterSlot={hasActiveFilters ? (
                        <>
                            <span className="text-[10px] uppercase tracking-wider text-gray-400 dark:text-slate-500 font-semibold">Filtros:</span>
                            {activeFilterEntries.map(([key, value]) => (
                                <span key={key} className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-800 text-amber-700 dark:text-amber-300">
                                    {filterLabels[key] || key}: {formatFilterValue(key, value)}
                                    <button onClick={() => clearFilter(key)} className="ml-0.5 hover:text-red-500 dark:hover:text-red-400 transition-colors" title="Quitar filtro">×</button>
                                </span>
                            ))}
                            <button onClick={clearAllFilters} className="text-[11px] text-gray-400 dark:text-slate-500 hover:text-red-500 dark:hover:text-red-400 transition-colors">
                                Limpiar filtros
                            </button>
                        </>
                    ) : undefined}
                    getActiveFilter={(columnId) => {
                        const apiParam = apiMapping[columnId] || columnId;
                        const val = filters[apiParam];
                        // Booleanos/enums: convertir valor único a array para el ColumnContextMenu
                        if (singleValueFields.includes(apiParam) && val !== undefined && val !== null && !Array.isArray(val)) {
                            return [val];
                        }
                        return val;
                    }}
                    onExportAll={handleExportAll}
                    exportFilename="productos"
                    columnVisibilityStorageVersion={columnVisibilityVersion}
                    hasFiltersActive={hasActiveFilters}
                    onClearAllFilters={clearAllFilters}
                    getRowClassName={(row) => (activeOverrides[row.original.id] ?? row.original.activo) === false
                        ? "bg-rose-50/70 dark:bg-rose-950/20 text-slate-500 dark:text-slate-400"
                        : ""}
                />
            </div>

            {/* MODAL CREAR / EDITAR PRODUCTO */}
            <Modal isOpen={isModalOpen} onClose={() => { setIsModalOpen(false); resetForm(); setEditandoProductoId(null); }} title={editandoProductoId ? "Editar Producto" : "Nuevo Producto"} size="3xl" closeOnEscape={false}
                footer={<><Button variant="light" onClick={() => { setIsModalOpen(false); resetForm(); setEditandoProductoId(null); }}><XMarkIcon className="w-4 h-4" /> Cancelar</Button><Button variant="dark" onClick={editandoProductoId ? handleGuardarEdicion : handleCreate} disabled={isSaving || (!editandoProductoId && skuYaExiste)}><CheckIcon className="w-4 h-4" /> {isSaving ? (editandoProductoId ? "Guardando..." : "Creando Producto...") : (editandoProductoId ? "Guardar Cambios" : "Crear Producto")}</Button></>}>
                <div className="text-sm">
                    {/* Tabs solo en modo edición: Datos (form) e Historial */}
                    {editandoProductoId && (
                        <div className="mb-5 flex border-b border-slate-200 dark:border-slate-700">
                            {([["datos", "Datos"], ["historial", "Historial"]] as const).map(([id, label]) => (
                                <button
                                    key={id}
                                    type="button"
                                    onClick={() => setPanelTab(id)}
                                    className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${panelTab === id ? "border-blue-600 text-blue-700 dark:text-blue-300" : "border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700"}`}
                                >
                                    {label}
                                </button>
                            ))}
                        </div>
                    )}

                    {editandoProductoId && panelTab === "historial" && (
                        <HistorialSection productoId={editandoProductoId} productoSku={sku} />
                    )}

                    <div className={`flex-col gap-5 ${editandoProductoId && panelTab === "historial" ? "hidden" : "flex"}`}>
                    {Object.values(formErrors).some(Boolean) && (
                        <div className="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-950/20 dark:text-red-300">
                            Revisá los campos marcados antes de guardar.
                        </div>
                    )}

                    <fieldset className={sectionClassName}>
                        <legend className={sectionTitleClassName}><IdentificationIcon /> Identificación</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Datos principales para reconocer el producto en la tabla y en web.</p>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                            {/* Estado y atributos del producto */}
                            <div className={checkboxCardClassName}>
                                <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={activo} onChange={e => setActivo(e.target.checked)} id="activo" />
                                <label htmlFor="activo" className="cursor-pointer">{editandoProductoId ? "Activo" : "Activo al crear"}</label>
                            </div>
                            <div className={checkboxCardClassName}>
                                <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={esCombo} onChange={e => handleToggleCombo(e.target.checked)} id="esCombo" />
                                <label htmlFor="esCombo" className="cursor-pointer">Es Combo</label>
                            </div>
                            <label className="block">
                                <span className={fieldLabelClassName}>UxB</span>
                                <input type="number" min={1} className={`${inputBaseClassName} ${formErrors.uxb ? inputErrorClassName : ""}`} value={uxb} onChange={e => { setUxb(Number(e.target.value)); if (formErrors.uxb) setFormErrors(p => ({ ...p, uxb: "" })); }} />
                                {formErrors.uxb && <p className="mt-1 text-xs text-red-500">{formErrors.uxb}</p>}
                            </label>

                            {/* Identificadores */}
                            <label className="block">
                                <span className={fieldLabelClassName}>SKU <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span></span>
                                <input type="text" disabled={!!editandoProductoId} className={`${inputBaseClassName} ${editandoProductoId ? "cursor-not-allowed opacity-60" : ""} ${formErrors.sku ? inputErrorClassName : (skuYaExiste ? "border-amber-400 bg-amber-50 dark:bg-amber-900/20" : "")}`} value={sku} onChange={e => { setSku(e.target.value); if (formErrors.sku) setFormErrors(p => ({ ...p, sku: "" })); }} placeholder="Ej: CUT-001" autoFocus required />
                                {formErrors.sku
                                    ? <p className="mt-1 text-xs text-red-500">{formErrors.sku}</p>
                                    : skuYaExiste && <p className="mt-1 text-xs font-medium text-amber-600 dark:text-amber-400">⚠ Ya existe un producto con este SKU</p>}
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>Cód. Ext.</span>
                                <input type="text" className={inputBaseClassName} value={codExt} onChange={e => setCodExt(e.target.value)} placeholder="Ej: 2000" />
                            </label>
                            <label className="block xl:col-span-4">
                                <span className={fieldLabelClassName}>Título Dux <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span></span>
                                <input type="text" className={`${inputBaseClassName} ${formErrors.tituloDux ? inputErrorClassName : ""}`} value={tituloDux} onChange={e => { setTituloDux(e.target.value); if (formErrors.tituloDux) setFormErrors(p => ({ ...p, tituloDux: "" })); }} placeholder="Título principal (Dux)" required />
                                {formErrors.tituloDux && <p className="mt-1 text-xs text-red-500">{formErrors.tituloDux}</p>}
                            </label>
                            <label className="block md:col-span-2">
                                <span className={fieldLabelClassName}>Título ML</span>
                                <input type="text" className={`${inputBaseClassName} ${formErrors.tituloMl ? inputErrorClassName : ""}`} value={tituloMl} onChange={e => { setTituloMl(e.target.value); if (formErrors.tituloMl) setFormErrors(p => ({ ...p, tituloMl: "" })); }} placeholder="Título para Mercado Libre" />
                                {formErrors.tituloMl && <p className="mt-1 text-xs text-red-500">{formErrors.tituloMl}</p>}
                            </label>
                            <label className="block md:col-span-2">
                                <span className={fieldLabelClassName}>Título Nube</span>
                                <input type="text" className={`${inputBaseClassName} ${formErrors.tituloNube ? inputErrorClassName : ""}`} value={tituloNube} onChange={e => { setTituloNube(e.target.value); if (formErrors.tituloNube) setFormErrors(p => ({ ...p, tituloNube: "" })); }} placeholder="Título para Tienda Nube" />
                                {formErrors.tituloNube && <p className="mt-1 text-xs text-red-500">{formErrors.tituloNube}</p>}
                            </label>

                            {/* Imagen */}
                            <div className="block xl:col-span-4">
                                <span className={fieldLabelClassName}>Imagen</span>
                                <div className="mt-1 flex flex-col gap-3 rounded-2xl border border-slate-200 bg-slate-50/80 p-3 dark:border-slate-700 dark:bg-slate-800/70">
                                    <div className="flex items-center gap-3">
                                        {imagenUrl ? (
                                            <img
                                                src={`${API_BASE_URL}/api/imagenes/${imagenUrl}`}
                                                alt={imagenUrl}
                                                className="h-20 w-20 rounded-2xl border border-slate-200 object-cover shadow-sm dark:border-slate-700"
                                                onError={(e) => { (e.target as HTMLImageElement).style.display = "none"; }}
                                            />
                                        ) : (
                                            <div className="flex h-20 w-20 items-center justify-center rounded-2xl border border-dashed border-slate-300 bg-white text-xs text-slate-400 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-500">
                                                Sin imagen
                                            </div>
                                        )}
                                        <div className="min-w-0 flex-1">
                                            <div className="truncate text-sm font-medium text-slate-700 dark:text-slate-200">
                                                {imagenUrl || "No hay imagen seleccionada"}
                                            </div>
                                            <div className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                                                Seleccioná una imagen existente igual que en la tabla de productos.
                                            </div>
                                        </div>
                                    </div>
                                    <div className="flex flex-wrap gap-2">
                                        <Button variant="dark" onClick={() => setIsImagePickerOpen(true)}>
                                            Seleccionar imagen
                                        </Button>
                                        <Button variant="light" onClick={() => { imagenTocadaManualmenteRef.current = true; setImagenUrl(""); }} disabled={!imagenUrl}>
                                            Quitar imagen
                                        </Button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </fieldset>

                    <div className="grid grid-cols-1 gap-5 xl:grid-cols-[1.05fr_0.95fr]">
                    <fieldset className={sectionClassName}>
                        <legend className={sectionTitleClassName}><CurrencyDollarIcon /> Económicos</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Base mínima para costos y cálculo de precios.</p>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                            <label className="block">
                                <span className={fieldLabelClassName}>Costo Base <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span></span>
                                <div className="relative">
                                    <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-sm font-semibold text-slate-400">$</span>
                                    <input type="number" min={0.01} className={`${inputBaseClassName} !pl-7 ${formErrors.costo ? inputErrorClassName : ""}`} value={costo} onChange={e => { setCosto(e.target.value === "" ? "" : Number(e.target.value)); if (formErrors.costo) setFormErrors(p => ({ ...p, costo: "" })); }} required />
                                </div>
                                {formErrors.costo && <p className="mt-1 text-xs text-red-500">{formErrors.costo}</p>}
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>IVA (%) <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span></span>
                                <select
                                    className={selectBaseClassName}
                                    value={iva}
                                    onChange={e => setIva(Number(e.target.value))}
                                    required
                                >
                                    <option value={21}>21%</option>
                                    <option value={10.5}>10,5%</option>
                                </select>
                            </label>
                        </div>
                    </fieldset>

                    <fieldset className={sectionClassName}>
                        <legend className={sectionTitleClassName}><ArchiveBoxIcon /> Reposición y Stock</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Disponibilidad inicial y prioridades de compra.</p>
                        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
                            <label className="flex flex-col">
                                <span className={fieldLabelClassName}>Stock inicial</span>
                                <div className="mt-auto">
                                    <input type="number" min={0} className={inputBaseClassName} value={stock} onChange={e => setStock(e.target.value === "" ? "" : Number(e.target.value))} placeholder="0" />
                                </div>
                            </label>
                            <label className="flex flex-col">
                                <span className={fieldLabelClassName}>MOQ (mín. pedido)</span>
                                <div className="mt-auto">
                                    <input type="number" min={0} className={inputBaseClassName} value={moq} onChange={e => setMoq(e.target.value === "" ? "" : Number(e.target.value))} placeholder="0" />
                                </div>
                            </label>
                            <label className="flex flex-col">
                                <span className={fieldLabelClassName}>Prioridad de reposición</span>
                                <div className="mt-auto">
                                    <select className={selectBaseClassName} value={tagReposicion} onChange={e => setTagReposicion(e.target.value as "" | "PRIO" | "LIQ")}>
                                        <option value="">Sin tag</option>
                                        <option value="PRIO">PRIO — Prioritaria</option>
                                        <option value="LIQ">LIQ — Liquidación</option>
                                    </select>
                                </div>
                            </label>
                        </div>
                    </fieldset>
                    </div>

                    <fieldset className={sectionClassName}>
                        <legend className={sectionTitleClassName}><ReceiptPercentIcon /> Márgenes</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Márgenes minorista y mayorista (porcentaje).{!esCombo ? " Al menos uno obligatorio." : " Opcionales para combos."}</p>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                            <label className="block">
                                <span className={fieldLabelClassName}>Margen minorista (%)</span>
                                <input type="number" step={0.5} className={`${inputBaseClassName} ${formErrors.margen ? inputErrorClassName : ""}`} value={margenMinorista} onChange={e => { setMargenMinorista(e.target.value === "" ? "" : Number(e.target.value)); if (formErrors.margen) setFormErrors(p => ({ ...p, margen: "" })); }} placeholder="Sin definir" />
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>Margen mayorista (%)</span>
                                <input type="number" step={0.5} className={`${inputBaseClassName} ${formErrors.margen ? inputErrorClassName : ""}`} value={margenMayorista} onChange={e => { setMargenMayorista(e.target.value === "" ? "" : Number(e.target.value)); if (formErrors.margen) setFormErrors(p => ({ ...p, margen: "" })); }} placeholder="Sin definir" />
                            </label>
                        </div>
                        {formErrors.margen && <p className="mt-2 text-xs text-red-500">{formErrors.margen}</p>}
                    </fieldset>

                    <fieldset className={sectionClassName}>
                        <legend className={sectionTitleClassName}><Squares2X2Icon /> Clasificación y Relaciones</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Asociaciones maestras para filtros, navegación y reglas del sistema.</p>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                            <div>
                                <AsyncSelect label={<>{!esCombo && <span style={{ color: "#dc2626" }} className="font-bold mr-0.5">*</span>}Marca</>} loadOptions={searchMarcas} onChange={(v, label) => { setMarcaId(v ? Number(v) : null); setMarcaDisplay(v ? (label ?? "") : ""); if (formErrors.marcaId) setFormErrors(p => ({ ...p, marcaId: "" })); }} value={marcaId} displayValue={marcaDisplay} placeholder="Buscar marca" inputClassName={`${inputBaseClassName} ${formErrors.marcaId ? inputErrorClassName : ""}`} />
                                {formErrors.marcaId && <p className="mt-1 text-xs text-red-500">{formErrors.marcaId}</p>}
                            </div>
                            <div>
                                <AsyncSelect label={<>{!esCombo && <span style={{ color: "#dc2626" }} className="font-bold mr-0.5">*</span>}Origen</>} loadOptions={searchOrigenes} onChange={(v, label) => { setOrigenId(v ? Number(v) : null); setOrigenDisplay(v ? (label ?? "") : ""); if (formErrors.origenId) setFormErrors(p => ({ ...p, origenId: "" })); }} value={origenId} displayValue={origenDisplay} placeholder="Buscar origen" inputClassName={`${inputBaseClassName} ${formErrors.origenId ? inputErrorClassName : ""}`} />
                                {formErrors.origenId && <p className="mt-1 text-xs text-red-500">{formErrors.origenId}</p>}
                            </div>
                            <div>
                                <AsyncSelect label={<>Clasif. Gral <span className="ml-1 rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-amber-700 dark:bg-amber-900/30 dark:text-amber-300">al menos una</span></>} loadOptions={searchClasifGral} onChange={(v, label) => { setClasifGralId(v ? Number(v) : null); setClasifGralDisplay(v ? (label ?? "") : ""); if (formErrors.clasificacion) setFormErrors(p => ({ ...p, clasificacion: "" })); }} value={clasifGralId} displayValue={clasifGralDisplay} placeholder="Buscar clasificación" inputClassName={`${inputBaseClassName} ${formErrors.clasificacion ? inputErrorClassName : ""}`} />
                                {formErrors.clasificacion && <p className="mt-1 text-xs text-red-500">{formErrors.clasificacion}</p>}
                            </div>
                            <div>
                                <AsyncSelect label={<>Clasif. Gastro <span className="ml-1 rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-amber-700 dark:bg-amber-900/30 dark:text-amber-300">al menos una</span></>} loadOptions={searchClasifGastro} onChange={(v, label) => { setClasifGastroId(v ? Number(v) : null); setClasifGastroDisplay(v ? (label ?? "") : ""); if (formErrors.clasificacion) setFormErrors(p => ({ ...p, clasificacion: "" })); }} value={clasifGastroId} displayValue={clasifGastroDisplay} placeholder="Buscar clasificación" inputClassName={`${inputBaseClassName} ${formErrors.clasificacion ? inputErrorClassName : ""}`} />
                            </div>
                            <div>
                                <AsyncSelect label={<>Tipo <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span></>} loadOptions={searchTipos} onChange={(v, label) => { setTipoId(v ? Number(v) : null); setTipoDisplay(v ? (label ?? "") : ""); if (formErrors.tipoId) setFormErrors(p => ({ ...p, tipoId: "" })); }} value={tipoId} displayValue={tipoDisplay} placeholder="Buscar tipo" inputClassName={`${inputBaseClassName} ${formErrors.tipoId ? inputErrorClassName : ""}`} />
                                {formErrors.tipoId && <p className="mt-1 text-xs text-red-500">{formErrors.tipoId}</p>}
                            </div>
                            <div>
                                <AsyncSelect label={<>{!esCombo && <span style={{ color: "#dc2626" }} className="font-bold mr-0.5">*</span>}Proveedor</>} loadOptions={searchProveedores} onChange={(v, label) => { setProveedorId(v ? Number(v) : null); setProveedorDisplay(v ? (label ?? "") : ""); if (formErrors.proveedorId) setFormErrors(p => ({ ...p, proveedorId: "" })); }} value={proveedorId} displayValue={proveedorDisplay} placeholder="Buscar proveedor" inputClassName={`${inputBaseClassName} ${formErrors.proveedorId ? inputErrorClassName : ""}`} />
                                {formErrors.proveedorId && <p className="mt-1 text-xs text-red-500">{formErrors.proveedorId}</p>}
                            </div>
                            <div>
                                <AsyncSelect label={<>{!esCombo && <span style={{ color: "#dc2626" }} className="font-bold mr-0.5">*</span>}Material</>} loadOptions={searchMateriales} onChange={(v, label) => { setMaterialId(v ? Number(v) : null); setMaterialDisplay(v ? (label ?? "") : ""); if (formErrors.materialId) setFormErrors(p => ({ ...p, materialId: "" })); }} value={materialId} displayValue={materialDisplay} placeholder="Buscar material" inputClassName={`${inputBaseClassName} ${formErrors.materialId ? inputErrorClassName : ""}`} />
                                {formErrors.materialId && <p className="mt-1 text-xs text-red-500">{formErrors.materialId}</p>}
                            </div>
                            <label className="block">
                                <span className={fieldLabelClassName}>Tag {!esCombo && <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span>}</span>
                                <select className={`${selectBaseClassName} ${formErrors.tag ? inputErrorClassName : ""}`} value={tag} onChange={e => { setTag(e.target.value as "" | "MAQUINA" | "REPUESTO" | "MENAJE" | "INSUMO"); if (formErrors.tag) setFormErrors(p => ({ ...p, tag: "" })); }}>
                                    <option value="">-- Seleccionar --</option>
                                    <option value="MAQUINA">Máquina</option>
                                    <option value="REPUESTO">Repuesto</option>
                                    <option value="MENAJE">Menaje</option>
                                    <option value="INSUMO">Insumo</option>
                                </select>
                                {formErrors.tag && <p className="mt-1 text-xs text-red-500">{formErrors.tag}</p>}
                            </label>
                        </div>
                    </fieldset>

                    <fieldset className={sectionClassName}>
                        <legend className={sectionTitleClassName}><UserGroupIcon /> Catálogos y Clientes</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Asociaciones múltiples. Buscá y agregá los que correspondan.</p>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                            <MultiAsyncSelect label="Catálogos" loadOptions={(q) => searchCatalogos(q)} value={catalogosSel} onChange={setCatalogosSel} placeholder="Buscar catálogo" inputClassName={inputBaseClassName} />
                            <MultiAsyncSelect label="Clientes" loadOptions={(q) => searchClientes(q)} value={clientesSel} onChange={setClientesSel} placeholder="Buscar cliente" inputClassName={inputBaseClassName} />
                        </div>
                    </fieldset>

                    <fieldset className={sectionClassName}>
                        <legend className={sectionTitleClassName}><ShoppingBagIcon /> MercadoLibre</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Publicación de MercadoLibre (MLA) asociada al producto.</p>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                            <div>
                                <AsyncSelect label="MLA" loadOptions={searchMlas} onChange={(v, label) => { setMlaId(v ? Number(v) : null); setMlaDisplay(label ?? ""); }} value={mlaId} displayValue={mlaDisplay} placeholder="Buscar MLA" inputClassName={inputBaseClassName} />
                                <button type="button" onClick={() => setShowNuevoMla((s) => !s)} className="mt-1 text-xs font-semibold text-blue-600 hover:text-blue-700 dark:text-blue-400">
                                    {showNuevoMla ? "− Cerrar nuevo MLA" : "+ Nuevo MLA"}
                                </button>
                            </div>
                        </div>

                        {showNuevoMla && (
                            <div className="mt-4 rounded-2xl border border-blue-200 bg-blue-50/50 p-4 dark:border-blue-800 dark:bg-blue-900/15">
                                <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                                    <span className="text-sm font-semibold text-slate-700 dark:text-slate-200">Nuevo MLA</span>
                                    <button
                                        type="button"
                                        onClick={handleObtenerMlaDeML}
                                        disabled={obteniendoMla || !sku.trim()}
                                        title={!sku.trim() ? "Cargá primero el SKU" : "Trae el MLA y sus datos desde tu publicación de MercadoLibre"}
                                        className="inline-flex items-center gap-2 rounded-xl bg-gradient-to-r from-amber-400 to-yellow-400 px-4 py-2 text-sm font-semibold text-slate-900 shadow-sm transition hover:from-amber-300 hover:to-yellow-300 disabled:cursor-not-allowed disabled:opacity-50"
                                    >
                                        <CloudArrowDownIcon className={`h-4 w-4 ${obteniendoMla ? "animate-pulse" : ""}`} />
                                        {obteniendoMla ? "Trayendo de MercadoLibre..." : "Autocompletar desde MercadoLibre"}
                                    </button>
                                </div>
                                <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-6">
                                    <label className="block xl:col-span-3">
                                        <span className={fieldLabelClassName}>Código MLA</span>
                                        <input type="text" className={inputBaseClassName} value={mlaCodigo} onChange={e => setMlaCodigo(e.target.value)} placeholder="MLA123456789" />
                                    </label>
                                    <label className="block xl:col-span-3">
                                        <span className={fieldLabelClassName}>MLAU</span>
                                        <input type="text" className={inputBaseClassName} value={mlaMlau} onChange={e => setMlaMlau(e.target.value)} placeholder="Opcional" />
                                    </label>
                                    <label className="block xl:col-span-2">
                                        <span className={fieldLabelClassName}>Precio envío</span>
                                        <input type="number" min={0} className={inputBaseClassName} value={mlaPrecioEnvio} onChange={e => setMlaPrecioEnvio(e.target.value === "" ? "" : Number(e.target.value))} placeholder="0" />
                                    </label>
                                    <label className="block xl:col-span-2">
                                        <span className={fieldLabelClassName}>Comisión (%)</span>
                                        <input type="number" min={0} step={0.5} className={inputBaseClassName} value={mlaComision} onChange={e => setMlaComision(e.target.value === "" ? "" : Number(e.target.value))} placeholder="0" />
                                    </label>
                                    <label className="block xl:col-span-2">
                                        <span className={fieldLabelClassName}>Tope promoción</span>
                                        <input type="number" min={0} className={inputBaseClassName} value={mlaTope} onChange={e => setMlaTope(e.target.value === "" ? "" : Number(e.target.value))} placeholder="0" />
                                    </label>
                                </div>
                                <div className="mt-3 flex justify-end">
                                    <Button variant="dark" onClick={handleCrearMla} disabled={creandoMla || !mlaCodigo.trim()}>
                                        {creandoMla ? "Creando..." : "Crear y usar este MLA"}
                                    </Button>
                                </div>
                            </div>
                        )}
                    </fieldset>

                    <fieldset className={sectionClassName}>
                        <legend className={sectionTitleClassName}><CubeIcon /> Dimensiones Físicas</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Medidas y atributos técnicos para logística y catálogo.</p>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                            <label className="block">
                                <span className={fieldLabelClassName}>Capacidad</span>
                                <input type="text" className={inputBaseClassName} value={capacidad} onChange={e => setCapacidad(e.target.value)} placeholder="Ej: 500 ml" />
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>Largo (cm)</span>
                                <input type="text" maxLength={45} className={`${inputBaseClassName} ${formErrors.largo ? inputErrorClassName : ""}`} value={largo} onChange={e => { setLargo(e.target.value); if (formErrors.largo) setFormErrors(p => ({ ...p, largo: "" })); }} />
                                {formErrors.largo && <p className="mt-1 text-xs text-red-500">{formErrors.largo}</p>}
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>Ancho (cm)</span>
                                <input type="text" maxLength={45} className={`${inputBaseClassName} ${formErrors.ancho ? inputErrorClassName : ""}`} value={ancho} onChange={e => { setAncho(e.target.value); if (formErrors.ancho) setFormErrors(p => ({ ...p, ancho: "" })); }} />
                                {formErrors.ancho && <p className="mt-1 text-xs text-red-500">{formErrors.ancho}</p>}
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>Alto (cm)</span>
                                <input type="text" maxLength={45} className={`${inputBaseClassName} ${formErrors.alto ? inputErrorClassName : ""}`} value={alto} onChange={e => { setAlto(e.target.value); if (formErrors.alto) setFormErrors(p => ({ ...p, alto: "" })); }} />
                                {formErrors.alto && <p className="mt-1 text-xs text-red-500">{formErrors.alto}</p>}
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>Diám. Boca</span>
                                <input type="text" className={inputBaseClassName} value={diamboca} onChange={e => setDiamboca(e.target.value)} placeholder="Ej: 7 cm" />
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>Diám. Base</span>
                                <input type="text" className={inputBaseClassName} value={diambase} onChange={e => setDiambase(e.target.value)} placeholder="Ej: 5 cm" />
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>Espesor (mm)</span>
                                <input type="text" className={inputBaseClassName} value={espesor} onChange={e => setEspesor(e.target.value)} placeholder="Ej: 1.2" />
                            </label>
                            <div className="md:col-span-2 xl:col-span-4">
                                <MultiAsyncSelect label="Aptos" loadOptions={(q) => searchAptos(q)} value={aptosSel} onChange={setAptosSel} placeholder="Buscar apto" inputClassName={inputBaseClassName} />
                            </div>
                        </div>
                    </fieldset>

                    <fieldset className={sectionClassName}>
                        <legend className={sectionTitleClassName}><BuildingStorefrontIcon className="h-5 w-5" /> Canales de venta</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Dónde publicar/subir el producto. Las integraciones de cada canal se irán habilitando.</p>
                        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
                            {canExportarDux && (
                                <div className={checkboxCardClassName} title={editandoProductoId ? "Al guardar, actualiza el producto en Dux" : "Al crear, sube el producto a Dux"}>
                                    <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={subirADux} onChange={e => setSubirADux(e.target.checked)} id="subirADux" />
                                    <label htmlFor="subirADux" className="cursor-pointer">{editandoProductoId ? "Actualizar en Dux" : "Subir a Dux"}</label>
                                </div>
                            )}
                            <div className={checkboxCardClassName}>
                                <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={subirKtHogar} onChange={e => setSubirKtHogar(e.target.checked)} id="subirKtHogar" disabled={!canExportarDux} />
                                <label htmlFor="subirKtHogar" className="cursor-pointer">KT HOGAR (Nube)</label>
                                {subirKtHogar && (
                                    <select className={`${selectBaseClassName} ml-auto w-auto`} value={cuotaHogar} onChange={e => setCuotaHogar(Number(e.target.value))}>
                                        {[{cuotas:-1,descripcion:"Transferencia"},{cuotas:6,descripcion:"6 cuotas"}].map(c => (
                                            <option key={c.cuotas} value={c.cuotas}>{c.descripcion}</option>
                                        ))}
                                    </select>
                                )}
                            </div>
                            <div className={checkboxCardClassName}>
                                <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={subirKtGastro} onChange={e => setSubirKtGastro(e.target.checked)} id="subirKtGastro" disabled={!canExportarDux} />
                                <label htmlFor="subirKtGastro" className="cursor-pointer">KT GASTRO (Nube)</label>
                                {subirKtGastro && (
                                    <select className={`${selectBaseClassName} ml-auto w-auto`} value={cuotaGastro} onChange={e => setCuotaGastro(Number(e.target.value))}>
                                        {[{cuotas:-1,descripcion:"Transferencia"},{cuotas:6,descripcion:"6 cuotas"}].map(c => (
                                            <option key={c.cuotas} value={c.cuotas}>{c.descripcion}</option>
                                        ))}
                                    </select>
                                )}
                            </div>
                            <div className={checkboxCardClassName}>
                                <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={subirMl} onChange={e => setSubirMl(e.target.checked)} id="subirMl" disabled={!canExportarDux} />
                                <label htmlFor="subirMl" className="cursor-pointer">Subir a Mercado Libre</label>
                            </div>
                        </div>
                    </fieldset>

                    {/* Precios inflados por canal: en edición opera en vivo; en alta se difiere al crear */}
                    <fieldset className={sectionClassName}>
                        <legend className={sectionTitleClassName}><BanknotesIcon /> Precios Inflados por Canal</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Asigná, cambiá o quitá el precio inflado de este producto en cada canal.</p>
                        {editandoProductoId
                            ? <PreciosInfladosSection productoId={editandoProductoId} />
                            : <PreciosInfladosSection value={preciosInfladosSel} onChange={setPreciosInfladosSel} />}
                    </fieldset>
                    </div>
                </div>
            </Modal>

            {isImagePickerOpen && (
                <ImagePickerModal
                    onSelect={(name) => { imagenTocadaManualmenteRef.current = true; setImagenUrl(name); }}
                    onClose={() => setIsImagePickerOpen(false)}
                />
            )}

            <Modal
                isOpen={isViewModalOpen}
                onClose={closeSaveView}
                title="Guardar vista de Productos"
                size="md"
                footer={
                    <>
                        <Button variant="light" onClick={closeSaveView}><XMarkIcon className="w-4 h-4" /> Cancelar</Button>
                        <Button variant="dark" onClick={handleSaveView}><CheckIcon className="w-4 h-4" /> Guardar vista</Button>
                    </>
                }
            >
                <div className="grid gap-4">
                    <label className="block">
                        <span className="text-sm font-bold text-gray-700 dark:text-slate-200">Nombre de la vista</span>
                        <input
                            type="text"
                            value={viewName}
                            onChange={(e) => setViewName(e.target.value)}
                            placeholder="Ej: Catálogo activo por marca"
                            className="mt-1 block w-full rounded-md border border-gray-300 p-2 shadow-sm dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100"
                            autoFocus
                        />
                    </label>
                    <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-500 dark:border-slate-700 dark:bg-slate-800/60 dark:text-slate-400">
                        Se guardan los filtros actuales, el orden activo y las columnas visibles de la tabla.
                    </div>
                </div>
            </Modal>
        </main>
    );
}


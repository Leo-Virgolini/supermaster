"use client";
import dynamic from "next/dynamic";
import { useState, useMemo, useEffect, useCallback } from "react";
import { useSearchParams } from "next/navigation";
import { toast } from "sonner";
import { notificar } from "../utils/notificar";
import { BookmarkIcon, CheckIcon, CubeIcon, PlusIcon, TrashIcon, XMarkIcon } from "@heroicons/react/24/outline";
import { API_BASE_URL } from "../config/runtime";
import { confirmDialog } from "../utils/confirmDialog";
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import SearchInput from "../components/SearchInput/SearchInput";
import Button from "../components/Button/Button";
import Modal from "../components/Modal/Modal";
import AsyncSelect from "../components/AsyncSelect/AsyncSelect";
import { useAuth } from "../context/AuthContext";
import { useProductos } from "./useProductos";
import {
    getProductosForExportAPI,
    searchMarcas, searchClasifGral, searchClasifGastro, searchTipos, searchProveedores, searchOrigenes, searchMateriales, searchMlas,
} from "./productosService";
import { getColumns } from "./columns";
import { ProductoCreateDTO, ProductoDTO, ProductoPatchDTO } from "./types";
import { type SortingState } from "@tanstack/react-table";

const ProductoDetalleModal = dynamic(() => import("./ProductoDetalleModal"), {
    loading: () => null,
});

const PRODUCTOS_VIEWS_STORAGE_KEY = "productos_saved_views_v1";
type ProductosView = {
    id: string;
    name: string;
    filters: Record<string, unknown>;
    sorting: SortingState;
    columnVisibility: Record<string, boolean>;
    createdAt: string;
};

function ImagePickerModal({ onSelect, onClose }: { onSelect: (name: string) => void; onClose: () => void }) {
    const [search, setSearch] = useState("");
    const [files, setFiles] = useState<string[]>([]);
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
                }
                return;
            }
            const data = await res.json();
            if (!signal?.aborted) {
                setFiles(Array.isArray(data) ? data : []);
            }
        } catch (e) {
            if ((e as { name?: string })?.name === "AbortError") return;
            setFiles([]);
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

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [detalleProducto, setDetalleProducto] = useState<ProductoDTO | null>(null);
    const [isImagePickerOpen, setIsImagePickerOpen] = useState(false);

    // --- Campos del Formulario ---
    const [sku, setSku] = useState("");
    const [codExt, setCodExt] = useState("");
    const [tituloWeb, setTituloWeb] = useState("");
    const [descripcion, setDescripcion] = useState("");
    const [esCombo, setEsCombo] = useState(false);
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
    const [costo, setCosto] = useState(0);
    const [iva, setIva] = useState(21.0);
    const [marcaId, setMarcaId] = useState<number | null>(null);
    const [origenId, setOrigenId] = useState<number | null>(null);
    const [clasifGralId, setClasifGralId] = useState<number | null>(null);
    const [clasifGastroId, setClasifGastroId] = useState<number | null>(null);
    const [tipoId, setTipoId] = useState<number | null>(null);
    const [proveedorId, setProveedorId] = useState<number | null>(null);
    const [materialId, setMaterialId] = useState<number | null>(null);
    const [mlaId, setMlaId] = useState<number | null>(null);
    const [moq, setMoq] = useState<number | "">("");
    const [stock, setStock] = useState<number | "">(0);
    const [tagReposicion, setTagReposicion] = useState<"" | "PRIO" | "LIQ">("");
    const [tag, setTag] = useState<"" | "MAQUINA" | "REPUESTO" | "MENAJE">("");
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

    const { productos, totalRecords, isLoading, createProducto, deleteProducto, updateProducto, updateProductoMargen } = useProductos(pageIndex, pageSize, filters, sorting);
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
        () => getColumns((producto) => setDetalleProducto(producto), canEditProductos),
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
    };

    // Campos que el backend acepta como valor único (no array)
    const singleValueFields = ["activo", "esCombo", "tagReposicion", "sku", "codExt", "descripcion", "tituloWeb", "mla"];

    // Labels para mostrar los filtros activos
    const filterLabels: Record<string, string> = {
        search: "Búsqueda", marcaIds: "Marca", clasifGralIds: "Rubro", clasifGastroIds: "Gastro",
        tipoIds: "Tipo", proveedorIds: "Proveedor", origenIds: "Origen", materialIds: "Material",
        mla: "MLA", sku: "SKU", codExt: "Cód. Ext.", descripcion: "Descripción",
        tituloWeb: "Título Web", activo: "Activo", esCombo: "Combo", tagReposicion: "Tag Rep.",
        catalogoIds: "Catálogo", aptoIds: "Apto", clienteIds: "Cliente",
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
        if (!descripcion.trim()) errors.descripcion = "La descripción es obligatoria";
        else if (descripcion.trim().length > 100) errors.descripcion = "Máximo 100 caracteres";
        if (!tituloWeb.trim()) errors.tituloWeb = "El Título Web es obligatorio";
        else if (tituloWeb.trim().length > 100) errors.tituloWeb = "Máximo 100 caracteres";
        if (costo < 0) errors.costo = "El costo no puede ser negativo";
        if (uxb < 1) errors.uxb = "UxB debe ser al menos 1";
        if (!clasifGralId) errors.clasifGralId = "La clasificación general es obligatoria";
        if (!tipoId) errors.tipoId = "El tipo es obligatorio";
        if (largo.length > 45) errors.largo = "Máximo 45 caracteres";
        if (ancho.length > 45) errors.ancho = "Máximo 45 caracteres";
        if (alto.length > 45) errors.alto = "Máximo 45 caracteres";
        setFormErrors(errors);
        return Object.keys(errors).length === 0;
    };

    const handleCreate = async () => {
        if (!validateForm()) return;
        try {
            setIsSaving(true);
            const payload: ProductoCreateDTO = {
                sku: sku.trim(), codExt, tituloWeb: tituloWeb.trim(), descripcion: descripcion.trim(), esCombo, uxb, activo, imagenUrl,
                capacidad, largo: largo || null, ancho: ancho || null, alto: alto || null,
                diamboca: diamboca || null, diambase: diambase || null, espesor: espesor || null,
                costo, iva,
                stock: stock !== "" ? stock : null,
                moq: moq !== "" ? moq : null,
                tagReposicion: tagReposicion || null,
                tag: tag || null,
                marcaId, origenId, clasifGralId: clasifGralId!, clasifGastroId, tipoId: tipoId!, proveedorId, materialId, mlaId
            };
            await createProducto(payload);
            resetForm();
            setIsModalOpen(false);
        } catch (e) { /* hook already toasts */ } finally { setIsSaving(false); }
    };

    const resetForm = () => {
        setSku(""); setCodExt(""); setTituloWeb(""); setDescripcion(""); setImagenUrl("");
        setEsCombo(false); setUxb(1); setActivo(true);
        setCapacidad(""); setLargo(""); setAncho(""); setAlto(""); setDiamboca(""); setDiambase(""); setEspesor("");
        setCosto(0); setIva(21.0);
        setMarcaId(null); setOrigenId(null); setClasifGralId(null); setClasifGastroId(null);
        setTipoId(null); setProveedorId(null); setMaterialId(null); setMlaId(null);
        setMoq(""); setStock(0); setTagReposicion(""); setTag("");
        setFormErrors({});
    };

    const handleDelete = async () => {
        const skus = selectedIds.map(i => productos.find(p => p.id === i)?.sku).filter(Boolean);
        const detalle = skus.length <= 3 ? skus.join(", ") : `${skus.slice(0, 3).join(", ")} y ${skus.length - 3} más`;
        if (!(await confirmDialog({ title: "Eliminar productos", message: `¿Eliminar ${selectedIds.length === 1 ? `"${detalle}"` : `${selectedIds.length} productos (${detalle})`}?`, confirmText: "Eliminar", variant: "danger" }))) return;
        try { await deleteProducto(selectedIds); setRowSelection({}); } catch (e) { /* hook already toasts */ }
    };

    const MARGEN_FIELDS = new Set(["margenMinorista", "margenMayorista", "margenFijoMinorista", "margenFijoMayorista"]);

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

    const sectionClassName = "rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900";
    const sectionTitleClassName = "text-base font-semibold text-slate-900 dark:text-slate-100";
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
                        <Button variant="danger" onClick={handleDelete}>
                            <TrashIcon className="w-4 h-4" />
                            Borrar ({selectedIds.length})
                        </Button>
                    )}
                    <Button onClick={() => setIsModalOpen(true)} variant="dark" disabled={!canEditProductos}>
                        <PlusIcon className="w-4 h-4" />
                        Crear Producto
                    </Button>
                </div>
            </div>

            <div className="flex-1 flex flex-col min-h-0 overflow-hidden">
                <Table
                    searchSlot={<SearchInput placeholder="Buscar producto por SKU, MLA, cód. ext. o descripción..." onSearch={(val) => { if (val !== filters.search) handleGlobalSearch(val); }} initialValue={filters.search} className="w-[28rem] max-w-full" />}
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

            {/* MODAL CREAR PRODUCTO */}
            <Modal isOpen={isModalOpen} onClose={() => { setIsModalOpen(false); resetForm(); }} title="Nuevo Producto" size="xl"
                footer={<><Button variant="light" onClick={() => { setIsModalOpen(false); resetForm(); }}><XMarkIcon className="w-4 h-4" /> Cancelar</Button><Button variant="dark" onClick={handleCreate} disabled={isSaving}><CheckIcon className="w-4 h-4" /> {isSaving ? "Creando Producto..." : "Crear Producto"}</Button></>}>
                <div className="flex flex-col gap-5 text-sm">
                    {Object.keys(formErrors).length > 0 && (
                        <div className="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-950/20 dark:text-red-300">
                            Revisá los campos marcados antes de guardar.
                        </div>
                    )}

                    <fieldset className={sectionClassName}>
                        <legend className={sectionTitleClassName}>Identificación</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Datos principales para reconocer el producto en la tabla y en web.</p>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                            <label className="block">
                                <span className={fieldLabelClassName}>SKU <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span></span>
                                <input type="text" className={`${inputBaseClassName} ${formErrors.sku ? inputErrorClassName : ""}`} value={sku} onChange={e => { setSku(e.target.value); if (formErrors.sku) setFormErrors(p => ({ ...p, sku: "" })); }} placeholder="Ej: CUT-001" autoFocus required />
                                {formErrors.sku && <p className="mt-1 text-xs text-red-500">{formErrors.sku}</p>}
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>Cód. Ext.</span>
                                <input type="text" className={inputBaseClassName} value={codExt} onChange={e => setCodExt(e.target.value)} placeholder="Ej: 2000" />
                            </label>
                            <label className="block md:col-span-2">
                                <span className={fieldLabelClassName}>Título Web <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span></span>
                                <input type="text" className={`${inputBaseClassName} ${formErrors.tituloWeb ? inputErrorClassName : ""}`} value={tituloWeb} onChange={e => { setTituloWeb(e.target.value); if (formErrors.tituloWeb) setFormErrors(p => ({ ...p, tituloWeb: "" })); }} placeholder="Nombre corto para web" />
                                {formErrors.tituloWeb && <p className="mt-1 text-xs text-red-500">{formErrors.tituloWeb}</p>}
                            </label>
                            <label className="block xl:col-span-4">
                                <span className={fieldLabelClassName}>Descripción <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span></span>
                                <input type="text" className={`${inputBaseClassName} ${formErrors.descripcion ? inputErrorClassName : ""}`} value={descripcion} onChange={e => { setDescripcion(e.target.value); if (formErrors.descripcion) setFormErrors(p => ({ ...p, descripcion: "" })); }} placeholder="Descripción detallada" />
                                {formErrors.descripcion && <p className="mt-1 text-xs text-red-500">{formErrors.descripcion}</p>}
                            </label>
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
                                        <Button variant="light" onClick={() => setImagenUrl("")} disabled={!imagenUrl}>
                                            Quitar imagen
                                        </Button>
                                    </div>
                                </div>
                            </div>
                            <div className={checkboxCardClassName}>
                                <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={esCombo} onChange={e => setEsCombo(e.target.checked)} id="esCombo" />
                                <label htmlFor="esCombo" className="cursor-pointer">Es Combo</label>
                            </div>
                            <label className="block">
                                <span className={fieldLabelClassName}>UxB</span>
                                <input type="number" min={1} className={`${inputBaseClassName} ${formErrors.uxb ? inputErrorClassName : ""}`} value={uxb} onChange={e => { setUxb(Number(e.target.value)); if (formErrors.uxb) setFormErrors(p => ({ ...p, uxb: "" })); }} />
                                {formErrors.uxb && <p className="mt-1 text-xs text-red-500">{formErrors.uxb}</p>}
                            </label>
                            <div className={checkboxCardClassName}>
                                <input className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary" type="checkbox" checked={activo} onChange={e => setActivo(e.target.checked)} id="activo" />
                                <label htmlFor="activo" className="cursor-pointer">Activo al crear</label>
                            </div>
                        </div>
                    </fieldset>

                    <div className="grid grid-cols-1 gap-5 xl:grid-cols-[1.05fr_0.95fr]">
                    <fieldset className={sectionClassName}>
                        <legend className={sectionTitleClassName}>Económicos</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Base mínima para costos y cálculo de precios.</p>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                            <label className="block">
                                <span className={fieldLabelClassName}>Costo Base ($) <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span></span>
                                <input type="number" min={0} className={`${inputBaseClassName} ${formErrors.costo ? inputErrorClassName : ""}`} value={costo} onChange={e => { setCosto(Number(e.target.value)); if (formErrors.costo) setFormErrors(p => ({ ...p, costo: "" })); }} />
                                {formErrors.costo && <p className="mt-1 text-xs text-red-500">{formErrors.costo}</p>}
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>IVA (%) <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span></span>
                                <input
                                    type="number"
                                    min={0}
                                    max={100}
                                    step={0.5}
                                    className={inputBaseClassName}
                                    value={iva}
                                    onChange={e => setIva(Number(e.target.value))}
                                />
                            </label>
                        </div>
                    </fieldset>

                    <fieldset className={sectionClassName}>
                        <legend className={sectionTitleClassName}>Reposición y Stock</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Disponibilidad inicial y prioridades de compra.</p>
                        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
                            <label className="block">
                                <span className={fieldLabelClassName}>Stock inicial</span>
                                <input type="number" min={0} className={inputBaseClassName} value={stock} onChange={e => setStock(e.target.value === "" ? "" : Number(e.target.value))} placeholder="0" />
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>MOQ (mín. pedido)</span>
                                <input type="number" min={0} className={inputBaseClassName} value={moq} onChange={e => setMoq(e.target.value === "" ? "" : Number(e.target.value))} placeholder="0" />
                            </label>
                            <label className="block">
                                <span className={fieldLabelClassName}>Prioridad de reposición</span>
                                <select className={selectBaseClassName} value={tagReposicion} onChange={e => setTagReposicion(e.target.value as "" | "PRIO" | "LIQ")}>
                                    <option value="">Sin tag</option>
                                    <option value="PRIO">PRIO — Prioritaria</option>
                                    <option value="LIQ">LIQ — Liquidación</option>
                                </select>
                            </label>
                        </div>
                    </fieldset>
                    </div>

                    <fieldset className={sectionClassName}>
                        <legend className={sectionTitleClassName}>Clasificación y Relaciones</legend>
                        <p className={`${sectionDescriptionClassName} mb-4`}>Asociaciones maestras para filtros, navegación y reglas del sistema.</p>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                            <AsyncSelect label="Marca" loadOptions={searchMarcas} onChange={(v) => setMarcaId(v ? Number(v) : null)} value={marcaId} placeholder="Buscar marca" inputClassName={inputBaseClassName} />
                            <AsyncSelect label="Origen" loadOptions={searchOrigenes} onChange={(v) => setOrigenId(v ? Number(v) : null)} value={origenId} placeholder="Buscar origen" inputClassName={inputBaseClassName} />
                            <div>
                                <AsyncSelect label={<>Clasif. Gral <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span></>} loadOptions={searchClasifGral} onChange={(v) => { setClasifGralId(v ? Number(v) : null); if (formErrors.clasifGralId) setFormErrors(p => ({ ...p, clasifGralId: "" })); }} value={clasifGralId} placeholder="Buscar clasificación" inputClassName={`${inputBaseClassName} ${formErrors.clasifGralId ? inputErrorClassName : ""}`} />
                                {formErrors.clasifGralId && <p className="mt-1 text-xs text-red-500">{formErrors.clasifGralId}</p>}
                            </div>
                            <AsyncSelect label="Clasif. Gastro" loadOptions={searchClasifGastro} onChange={(v) => setClasifGastroId(v ? Number(v) : null)} value={clasifGastroId} placeholder="Buscar clasificación" inputClassName={inputBaseClassName} />
                            <div>
                                <AsyncSelect label={<>Tipo <span style={{ color: "#dc2626" }} className="font-bold ml-0.5">*</span></>} loadOptions={searchTipos} onChange={(v) => { setTipoId(v ? Number(v) : null); if (formErrors.tipoId) setFormErrors(p => ({ ...p, tipoId: "" })); }} value={tipoId} placeholder="Buscar tipo" inputClassName={`${inputBaseClassName} ${formErrors.tipoId ? inputErrorClassName : ""}`} />
                                {formErrors.tipoId && <p className="mt-1 text-xs text-red-500">{formErrors.tipoId}</p>}
                            </div>
                            <AsyncSelect label="Proveedor" loadOptions={searchProveedores} onChange={(v) => setProveedorId(v ? Number(v) : null)} value={proveedorId} placeholder="Buscar proveedor" inputClassName={inputBaseClassName} />
                            <AsyncSelect label="Material" loadOptions={searchMateriales} onChange={(v) => setMaterialId(v ? Number(v) : null)} value={materialId} placeholder="Buscar material" inputClassName={inputBaseClassName} />
                            <AsyncSelect label="MLA" loadOptions={searchMlas} onChange={(v) => setMlaId(v ? Number(v) : null)} value={mlaId} placeholder="Buscar MLA" inputClassName={inputBaseClassName} />
                            <label className="block">
                                <span className={fieldLabelClassName}>Tag</span>
                                <select className={selectBaseClassName} value={tag} onChange={e => setTag(e.target.value as "" | "MAQUINA" | "REPUESTO" | "MENAJE")}>
                                    <option value="">Sin tag</option>
                                    <option value="MAQUINA">Máquina</option>
                                    <option value="REPUESTO">Repuesto</option>
                                    <option value="MENAJE">Menaje</option>
                                </select>
                            </label>
                        </div>
                    </fieldset>

                    <fieldset className={sectionClassName}>
                        <legend className={sectionTitleClassName}>Dimensiones Físicas</legend>
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
                        </div>
                    </fieldset>
                </div>
            </Modal>

            {isImagePickerOpen && (
                <ImagePickerModal
                    onSelect={(name) => setImagenUrl(name)}
                    onClose={() => setIsImagePickerOpen(false)}
                />
            )}

            {/* MODAL DETALLE */}
            <ProductoDetalleModal
                isOpen={detalleProducto !== null}
                onClose={() => setDetalleProducto(null)}
                productoId={detalleProducto?.id ?? 0}
                productoSku={detalleProducto?.sku ?? ""}
            />

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


"use client";
import { useState, useMemo, useEffect, useCallback } from "react";
import { useSearchParams } from "next/navigation";
import { CubeIcon } from "@heroicons/react/24/outline";
import { confirmDialog } from "../utils/confirmDialog";
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import SearchInput from "../components/SearchInput/SearchInput";
import CreateButton from "../components/Button/CreateButton";
import DeleteButton from "../components/Button/DeleteButton";
import { useAuth } from "../context/AuthContext";
import { useProductos } from "./useProductos";
import ProductosFilterPanel, { ProductosFilterToggle } from "./ProductosFilterBar";
import {
    getProductosForExportAPI,
    searchMarcas, searchClasifGral, searchClasifGastro, searchTipos, searchProveedores, searchOrigenes, searchMateriales, searchMlas,
    searchCatalogos, searchAptos, searchSegmentos, searchCanales,
} from "./productosService";
import { getColumns } from "./columns";
import ProductoFormModal from "./ProductoFormModal";
import { ProductoDTO, ProductoPatchDTO } from "./types";
import { type SortingState } from "@tanstack/react-table";


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
        const paramKeys = ["catalogoIds", "aptoIds", "segmentoIds", "marcaIds", "tipoIds", "materialIds", "origenIds", "clasifGralIds", "clasifGastroIds"];
        for (const key of paramKeys) {
            const val = searchParams.get(key);
            if (val) initial[key] = val.split(",").map(Number);
        }
        return initial;
    });
    const [sorting, setSorting] = useState<SortingState>([{ id: "id", desc: true }]);
    const [rowSelection, setRowSelection] = useState({});
    const [columnVisibilityVersion, setColumnVisibilityVersion] = useState(0);
    const [activeOverrides, setActiveOverrides] = useState<Record<number, boolean>>({});
    const [filtrosExpanded, setFiltrosExpanded] = useState(false);
    const toggleFiltros = () => {
        setFiltrosExpanded((prev) => !prev);
    };

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [productoAEditar, setProductoAEditar] = useState<ProductoDTO | null>(null);
    const abrirCrear = useCallback(() => { setProductoAEditar(null); setIsModalOpen(true); }, []);
    const abrirEdicion = useCallback((p: ProductoDTO) => { setProductoAEditar(p); setIsModalOpen(true); }, []);
    const cerrarModal = useCallback(() => { setIsModalOpen(false); setProductoAEditar(null); }, []);

    // Sync header search param (también limpia cuando se remueve ?q del URL)
    useEffect(() => {
        const q = getSearchParamValue();
        const newFilters: any = { search: q };
        const paramKeys = ["catalogoIds", "aptoIds", "segmentoIds", "marcaIds", "tipoIds", "materialIds", "origenIds", "clasifGralIds", "clasifGastroIds"];
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
        () => getColumns(abrirEdicion, canEditProductos),
        [canEditProductos, abrirEdicion]
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
        "segmento": "segmentoIds",
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
        catalogoIds: "Catálogo", aptoIds: "Apto", segmentoIds: "Segmento", tags: "Tag",
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
        segmentoIds: (q: string) => searchSegmentos(q, 9999),
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

    useEffect(() => {
        setActiveOverrides({});
    }, [productos]);

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

    return (
        <main className="min-h-0 flex flex-col bg-gray-50 px-4 py-2 overflow-hidden">
            <div className="flex justify-between items-center mb-2 shrink-0">
                <h1 className="inline-flex items-center gap-2 leading-none text-3xl font-bold text-gray-800">
                    <CubeIcon className="w-8 h-8 text-gray-600" />
                    Productos
                </h1>
                <div className="flex gap-2 items-center">
                    {canEditProductos && hasSelection && (
                        <DeleteButton onClick={handleDelete}>
                            Borrar ({selectedIds.length})
                        </DeleteButton>
                    )}
                    <CreateButton onClick={abrirCrear} disabled={!canEditProductos}>
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
                    getActiveFilter={(columnId) => {
                        const apiParam = apiMapping[columnId] || columnId;
                        const val = filters[apiParam];
                        // Booleanos/enums: convertir valor único a array para el ColumnContextMenu
                        if (singleValueFields.includes(apiParam) && val !== undefined && val !== null && !Array.isArray(val)) {
                            return [val];
                        }
                        return val;
                    }}
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

            {isModalOpen && (
                <ProductoFormModal
                    producto={productoAEditar}
                    canExportarDux={canExportarDux}
                    createProducto={createProducto}
                    onClose={cerrarModal}
                    onSuccess={refresh}
                />
            )}

        </main>
    );
}


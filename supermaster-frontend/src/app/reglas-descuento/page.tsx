"use client";
import { getErrorMessage } from "@/lib/errors";
import { useState, useMemo } from "react";
import { notificar } from "../utils/notificar";
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import SearchInput from "../components/SearchInput/SearchInput";
import Button from "../components/Button/Button";
import Modal from "../components/Modal/Modal";
import { useReglasDescuento } from "./useReglasDescuento";
import { getReglasAPI } from "./reglasDescuentoService";
import { getColumns } from "./columns";
import { type SortingState } from "@tanstack/react-table";
import { TrashIcon, TagIcon, PlusIcon, CheckIcon, XMarkIcon, InformationCircleIcon, ChevronDownIcon } from "@heroicons/react/24/outline";
import { confirmDialog } from "../utils/confirmDialog";
import AsyncSelect from "../components/AsyncSelect/AsyncSelect";
import { useAuth } from "../context/AuthContext";
import {
    searchCanales,
    searchCatalogos,
    searchClasifGral,
    searchClasifGastro,
} from "../productos/productosService";

export default function ReglasDescuentoPage() {
    const { hasPermiso } = useAuth();
    const canEdit = hasPermiso("PRECIOS_EDITAR");
    const [pageIndex, setPageIndex] = useState(0);
    const [pageSize, setPageSize] = useState(() => getInitialPageSize("reglas-descuento"));
    const [filters, setFilters] = useState<any>({ search: "" });
    const [sorting, setSorting] = useState<SortingState>([{ id: "canalId", desc: false }]);
    const [rowSelection, setRowSelection] = useState({});

    // Modal y Form
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [ayudaAbierta, setAyudaAbierta] = useState(false);

    // Form fields
    const [canalId, setCanalId] = useState<number | null>(null);
    const [catalogoId, setCatalogoId] = useState<number | null>(null);
    const [clasifGralId, setClasifGralId] = useState<number | null>(null);
    const [clasifGastroId, setClasifGastroId] = useState<number | null>(null);
    const [montoMinimo, setMontoMinimo] = useState(0);
    const [descuentoPorcentaje, setDescuentoPorcentaje] = useState(0);
    const [prioridad, setPrioridad] = useState(1);
    const [activo, setActivo] = useState(true);
    const [descripcion, setDescripcion] = useState("");

    const { reglas, totalRecords, isLoading, createRegla, deleteRegla, updateRegla } = useReglasDescuento(pageIndex, pageSize, filters, sorting);
    const pageCount = totalRecords > 0 ? Math.ceil(totalRecords / pageSize) : 1;
    const selectedIds = Object.keys(rowSelection).map(Number);
    const hasSelection = selectedIds.length > 0;

    const SORT_MAP: Record<string, string> = { canalId: "canal.nombre" };
    const handleExportAll = async () => {
        const rawId = sorting.length > 0 ? sorting[0].id : "id";
        const sortParam = `${SORT_MAP[rawId] || rawId},${sorting.length > 0 && sorting[0].desc ? "desc" : "asc"}`;
        const res = await getReglasAPI(0, 99999, filters, sortParam);
        return res.content;
    };

    const columns = useMemo(
        () => getColumns(searchCanales, searchCatalogos, searchClasifGral, searchClasifGastro, canEdit),
        [canEdit]
    );

    const handleCreate = async () => {
        if (montoMinimo <= 0 || descuentoPorcentaje <= 0 || descuentoPorcentaje > 100) {
            notificar.warning("El monto mínimo debe ser > 0 y el descuento entre 0 y 100%.");
            return;
        }
        try {
            setIsSaving(true);
            await createRegla({
                canalId,
                catalogoId: catalogoId || null,
                clasifGralId: clasifGralId || null,
                clasifGastroId: clasifGastroId || null,
                montoMinimo,
                descuentoPorcentaje,
                prioridad,
                activo,
                descripcion: descripcion || null
            });
            // Reset fields
            setCanalId(null); setCatalogoId(null); setClasifGralId(null); setClasifGastroId(null);
            setMontoMinimo(0); setDescuentoPorcentaje(0); setPrioridad(1); setActivo(true); setDescripcion("");
            setIsModalOpen(false);
        } catch (e: unknown) { notificar.error("Error: " + getErrorMessage(e)); } finally { setIsSaving(false); }
    };

    const handleDelete = async () => {
        const desc = selectedIds.map(i => reglas.find(r => r.id === i)?.descripcion || `#${i}`);
        const detalle = desc.length <= 3 ? desc.join(", ") : `${desc.slice(0, 3).join(", ")} y ${desc.length - 3} más`;
        if (!(await confirmDialog({ title: "Eliminar reglas", message: `¿Eliminar ${selectedIds.length === 1 ? `"${detalle}"` : `${selectedIds.length} reglas (${detalle})`}?`, confirmText: "Eliminar", variant: "danger" }))) return;
        try { await deleteRegla(selectedIds); setRowSelection({}); } catch (e) { /* hook already toasts */ }
    };

    const handleUpdate = async (rowIndex: number, columnId: string, value: unknown) => {
        const itemOriginal = reglas[rowIndex];
        try { await updateRegla(itemOriginal.id, { [columnId]: value }); } catch (e) { /* hook already toasts */ }
    };

    const apiMapping: Record<string, string> = {
        // "nombreColumnaFrontend": "nombreParametroBackend"
    };

    const handleGlobalSearch = (valor: string) => {
        setFilters((prev: any) => ({
            ...prev,
            search: valor
        }));
        setPageIndex(0);
    };

    const handleColumnFilterChange = (columnId: string, value: any) => {
        const apiParam = apiMapping[columnId] || columnId;
        setFilters((prev: any) => {
            const newFilters = { ...prev };
            if (value === undefined || value === null || value === "") {
                delete newFilters[apiParam];
            } else {
                newFilters[apiParam] = value;
            }
            return newFilters;
        });
        setPageIndex(0);
    };

    // `hasActiveFilters`: hay filtros por columna activos (no contamos search,
    // porque eso ya lo detecta el Table vía globalFilter).
    const hasActiveFilters = Object.entries(filters).some(([key, value]) => {
        if (key === "search") return false;
        if (value === undefined || value === null || value === "") return false;
        if (Array.isArray(value) && value.length === 0) return false;
        return true;
    });

    const clearAllFilters = () => {
        setFilters({});
        setPageIndex(0);
    };

    return (
        <main className="p-4 bg-gray-50 min-h-0 flex flex-col overflow-hidden">
            <div className="flex justify-between items-center mb-3">
                <div>
                    <h1 className="text-3xl font-bold text-gray-800 flex items-center gap-2">
                        <TagIcon className="w-8 h-8 text-gray-600" />
                        Reglas de Descuento
                    </h1>
                </div>
                <div className="flex gap-2 items-center">
                    {canEdit && hasSelection && (
                        <Button variant="danger" onClick={handleDelete}>
                            <TrashIcon className="w-4 h-4" />
                            Borrar ({selectedIds.length})
                        </Button>
                    )}
                    <Button onClick={() => setIsModalOpen(true)} variant="dark" disabled={!canEdit}>
                        <PlusIcon className="w-4 h-4" />
                        Crear Regla de Descuento
                    </Button>
                </div>
            </div>

            <div className="mb-3 rounded-lg border border-blue-200 bg-blue-50 dark:border-blue-800/60 dark:bg-blue-900/20">
                <button
                    type="button"
                    onClick={() => setAyudaAbierta((v) => !v)}
                    className="flex w-full items-center justify-between gap-2 px-4 py-2 text-left text-sm font-semibold text-blue-900 dark:text-blue-200"
                >
                    <span className="flex items-center gap-2">
                        <InformationCircleIcon className="h-5 w-5" />
                        ¿Para qué sirven las Reglas de Descuento?
                    </span>
                    <ChevronDownIcon className={`h-4 w-4 transition-transform ${ayudaAbierta ? "rotate-180" : ""}`} />
                </button>
                {ayudaAbierta && (
                    <div className="border-t border-blue-200 px-4 py-3 text-sm text-blue-900 dark:border-blue-800/60 dark:text-blue-100">
                        <p className="mb-3">
                            Aplican <strong>descuentos automáticos al PVP</strong> según el <strong>canal</strong> de la regla. Sirven para reflejar
                            promociones tipo &quot;15% off en KT HOGAR&quot; en el cálculo de precios y en el Monitor.
                        </p>

                        <div className="mb-3 rounded-md bg-white/60 p-3 text-xs dark:bg-blue-950/40">
                            <p className="mb-2 font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
                                Ejemplo
                            </p>
                            <p>
                                Regla: <span className="font-mono">canal = KT HOGAR, descuento = 10%</span>.
                                Si un producto en KT HOGAR tiene PVP $80.000, la columna PVP c/Desc muestra el 10% de descuento →{" "}
                                <span className="font-mono">$72.000</span>. El monto mínimo, si se carga, se muestra como referencia informativa.
                            </p>
                        </div>

                        <div className="mb-3">
                            <p className="mb-1 font-semibold">Campos clave:</p>
                            <ul className="ml-4 list-disc space-y-0.5">
                                <li><strong>Canal</strong>: obligatorio. La regla solo aplica a precios de ese canal.</li>
                                <li><strong>Catálogo / Clasif. Gral / Clasif. Gastro</strong>: campos opcionales de referencia. <em>Aún no acotan el cálculo</em>: el descuento se aplica a todos los productos del canal (el filtrado por estos campos no está implementado en el motor de precios).</li>
                                <li><strong>Monto Mínimo ($)</strong>: monto de compra de referencia. Es <em>informativo</em>: se muestra junto al descuento pero no condiciona el cálculo (el descuento c/Desc se calcula siempre).</li>
                                <li><strong>Descuento (%)</strong>: porcentaje a descontar del PVP.</li>
                                <li><strong>Prioridad</strong>: ordena las reglas; la de menor número se muestra primero en las columnas del Monitor (las demás van en el tooltip).</li>
                                <li><strong>Activo</strong>: deshabilita la regla sin tener que borrarla.</li>
                            </ul>
                        </div>

                        <div className="rounded-md border border-amber-300 bg-amber-50 p-2.5 text-xs text-amber-900 dark:border-amber-700/60 dark:bg-amber-900/30 dark:text-amber-100">
                            Los descuentos se ven reflejados en el <strong>Monitor de Precios</strong> en las columnas con sufijo{" "}
                            <span className="font-mono">c/Desc</span> (PVP, Ganancia, Costos Venta, Ingreso Neto, márgenes y markup recalculados).
                            Si configurás varias reglas activas en un canal, se calculan <strong>todas</strong>: el Monitor muestra una en las columnas (la de menor prioridad) y el resto al pasar el mouse sobre la columna de descuento.
                        </div>
                    </div>
                )}
            </div>

            <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                <Table
                    searchSlot={<SearchInput placeholder="Buscar regla por canal, cliente o nombre..." onSearch={(val) => { if (val !== filters.search) handleGlobalSearch(val); }} initialValue={filters.search} className="w-[26rem] max-w-full" />}
                    tableId="reglas-descuento"
                    isLoading={isLoading}
                    globalFilter={filters.search}
                    setGlobalFilter={handleGlobalSearch}
                    data={reglas}
                    columns={columns}
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
                    hasFiltersActive={hasActiveFilters}
                    onClearAllFilters={clearAllFilters}
                    getActiveFilter={(columnId) => filters[apiMapping[columnId] || columnId]}
                    onExportAll={handleExportAll}
                    exportFilename="reglas-descuento"
                />
            </div>

            <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title="Nueva Regla de Descuento"
                footer={<><Button variant="light" onClick={() => setIsModalOpen(false)}><XMarkIcon className="w-4 h-4" /> Cancelar</Button>
                    <Button variant="dark" onClick={handleCreate} disabled={!canalId || isSaving}><CheckIcon className="w-4 h-4" /> {isSaving ? "Creando Regla de Descuento..." : "Crear Regla de Descuento"}</Button></>}>
                <div className="flex flex-col gap-4">
                    <div className="grid grid-cols-2 gap-4">
                        <AsyncSelect
                            label={<>Canal <span className="text-red-500">*</span></>}
                            placeholder="Buscar canal..."
                            loadOptions={searchCanales}
                            value={canalId}
                            onChange={(val) => setCanalId(val ? Number(val) : null)}
                        />
                        <AsyncSelect
                            label="Catálogo (Opcional)"
                            placeholder="Buscar catálogo..."
                            loadOptions={searchCatalogos}
                            value={catalogoId}
                            onChange={(val) => setCatalogoId(val ? Number(val) : null)}
                        />
                    </div>
                    <div className="grid grid-cols-2 gap-4">
                        <AsyncSelect
                            label="Clasif. General (Opcional)"
                            placeholder="Buscar clasificación..."
                            loadOptions={searchClasifGral}
                            value={clasifGralId}
                            onChange={(val) => setClasifGralId(val ? Number(val) : null)}
                        />
                        <AsyncSelect
                            label="Clasif. Gastro (Opcional)"
                            placeholder="Buscar clasif. gastro..."
                            loadOptions={searchClasifGastro}
                            value={clasifGastroId}
                            onChange={(val) => setClasifGastroId(val ? Number(val) : null)}
                        />
                    </div>

                    <div className="grid grid-cols-2 gap-4 border-t pt-4">
                        <label className="block">
                            <span className="font-bold text-gray-700 text-sm">Monto Mínimo ($) <span className="text-red-500">*</span></span>
                            <input type="number" min="0.01" className="w-full border p-2 rounded" value={montoMinimo} onChange={e => setMontoMinimo(Number(e.target.value))} />
                        </label>
                        <label className="block">
                            <span className="font-bold text-gray-700 text-sm">Descuento (%) <span className="text-red-500">*</span></span>
                            <input type="number" min="0.01" max="100" className="w-full border p-2 rounded" value={descuentoPorcentaje} onChange={e => setDescuentoPorcentaje(Number(e.target.value))} />
                        </label>
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <label className="block">
                            <span className="font-bold text-gray-700 text-sm">Prioridad (Menor = se muestra primero)</span>
                            <input type="number" className="w-full border p-2 rounded" value={prioridad} onChange={e => setPrioridad(Number(e.target.value))} />
                        </label>
                        <label className="flex items-center gap-2 pt-6">
                            <input type="checkbox" className="w-5 h-5" checked={activo} onChange={e => setActivo(e.target.checked)} />
                            <span className="font-bold text-gray-700 text-sm">¿Regla Activa?</span>
                        </label>
                    </div>
                    <label className="block">
                        <span className="font-bold text-gray-700 text-sm">Descripción</span>
                        <input type="text" className="w-full border p-2 rounded" value={descripcion} onChange={e => setDescripcion(e.target.value)} placeholder="Ej: Promo Verano Mayoristas" />
                    </label>
                </div>
            </Modal>
        </main>
    );
}

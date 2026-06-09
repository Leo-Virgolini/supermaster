"use client";

import { useMemo, useState } from "react";
import ErrorBanner from "../components/ErrorBanner/ErrorBanner";
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import SearchInput from "../components/SearchInput/SearchInput";
import Button from "../components/Button/Button";
import CreateButton from "../components/Button/CreateButton";
import DeleteButton from "../components/Button/DeleteButton";
import Modal from "../components/Modal/Modal";
import { useCatalogos } from "./useCatalogos";
import { getCatalogosAPI } from "./catalogosService";
import { getColumns } from "./columns";
import { type SortingState } from "@tanstack/react-table";
import { BookOpenIcon, CheckIcon, XMarkIcon } from "@heroicons/react/24/outline";
import { confirmDialog } from "../utils/confirmDialog";
import { useAuth } from "../context/AuthContext";

export default function CatalogosPage() {
    const { hasPermiso } = useAuth();
    const canEdit = hasPermiso("MAESTROS_EDITAR");
    const [pageIndex, setPageIndex] = useState(0);
    const [pageSize, setPageSize] = useState(() => getInitialPageSize("catalogos"));
    const [filters, setFilters] = useState<any>({ search: "" });
    const [sorting, setSorting] = useState<SortingState>([{ id: "id", desc: false }]);

    const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [formTouched, setFormTouched] = useState(false);

    const [nuevoCatalogo, setNuevoCatalogo] = useState("");
    const [exportarConIva, setExportarConIva] = useState(true);
    const [recargoPorcentaje, setRecargoPorcentaje] = useState(0);

    const {
        catalogos,
        totalRecords,
        isLoading,
        error,
        createCatalogo,
        deleteCatalogo,
        updateCatalogo
    } = useCatalogos(pageIndex, pageSize, filters, sorting);

    const pageCount = totalRecords > 0 ? Math.ceil(totalRecords / pageSize) : 1;
    const selectedIds = Object.keys(rowSelection).map(Number);
    const hasSelection = selectedIds.length > 0;
    const columns = useMemo(() => getColumns(canEdit), [canEdit]);

    const handleExportAll = async () => {
        const sortParam = sorting.length > 0 ? sorting[0].id + "," + (sorting[0].desc ? "desc" : "asc") : "id,asc";
        const res = await getCatalogosAPI(0, 99999, filters, sortParam);
        return res.content;
    };

    const handleCreate = async () => {
        setFormTouched(true);
        if (!nuevoCatalogo.trim()) return;
        try {
            setIsSaving(true);
            await createCatalogo({
                nombre: nuevoCatalogo,
                exportarConIva,
                recargoPorcentaje
            });

            setNuevoCatalogo("");
            setExportarConIva(true);
            setRecargoPorcentaje(0);
            setFormTouched(false);
            setIsModalOpen(false);
        } catch (e) { /* hook already toasts */ } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = async () => {
        if (!hasSelection) return;
        const nombres = selectedIds.map(i => catalogos.find(c => c.id === i)?.nombre).filter(Boolean);
        const detalle = nombres.length <= 3 ? nombres.join(", ") : `${nombres.slice(0, 3).join(", ")} y ${nombres.length - 3} más`;
        if (await confirmDialog({ title: "Eliminar catálogos", message: `¿Eliminar ${selectedIds.length === 1 ? `"${detalle}"` : `${selectedIds.length} catálogos (${detalle})`}?`, confirmText: "Eliminar", variant: "danger" })) {
            try {
                await deleteCatalogo(selectedIds);
                setRowSelection({});
            } catch (e) { /* hook already toasts */ }
        }
    };

    const handleUpdate = async (rowIndex: number, columnId: string, value: any) => {
        const itemOriginal = catalogos[rowIndex];

        try {
            await updateCatalogo(itemOriginal.id, { [columnId]: value });
        } catch (e) { /* hook already toasts */ }
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
                        <BookOpenIcon className="w-8 h-8 text-gray-600" />
                        Catálogos
                    </h1>
                </div>

                <div className="flex gap-2 items-center">
                    {canEdit && hasSelection && (
                        <DeleteButton onClick={handleDelete}>
                            Borrar ({selectedIds.length})
                        </DeleteButton>
                    )}
                    <CreateButton onClick={() => setIsModalOpen(true)} disabled={!canEdit}>
                        Crear Catálogo
                    </CreateButton>
                </div>
            </div>

            {error ? <ErrorBanner message={error} /> : (
                <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                    <Table
                    searchSlot={<SearchInput placeholder="Buscar catálogo por nombre..." onSearch={(val) => { if (val !== filters.search) handleGlobalSearch(val); }} initialValue={filters.search} className="w-[26rem] max-w-full" />}
                        tableId="catalogos"
                        data={catalogos}
                        isLoading={isLoading}
                        columns={columns}
                        globalFilter={filters.search}
                        setGlobalFilter={handleGlobalSearch}
                        sorting={sorting}
                        setSorting={setSorting}
                        pageIndex={pageIndex}
                        pageSize={pageSize}
                        pageCount={pageCount}
                        onPageChange={setPageIndex}
                        onPageSizeChange={setPageSize}
                        totalRecords={totalRecords}
                        rowSelection={rowSelection}
                        setRowSelection={setRowSelection}
                        updateData={handleUpdate}
                        onColumnFilterChange={handleColumnFilterChange}
                        hasFiltersActive={hasActiveFilters}
                        onClearAllFilters={clearAllFilters}
                        getActiveFilter={(columnId) => filters[apiMapping[columnId] || columnId]}
                        onExportAll={handleExportAll}
                        exportFilename="catalogos"
                    />
                </div>
            )}

            <Modal
                isOpen={isModalOpen}
                onClose={() => { setIsModalOpen(false); setFormTouched(false); }}
                title="Nuevo Catálogo"
                footer={
                    <>
                        <Button text="Cancelar" variant="light" onClick={() => { setIsModalOpen(false); setFormTouched(false); }}>
                            <XMarkIcon className="w-4 h-4" />
                        </Button>
                        <Button
                            variant="dark"
                            onClick={handleCreate}
                            disabled={isSaving}
                        >
                            <CheckIcon className="w-4 h-4" />
                            {isSaving ? "Creando Catálogo..." : "Crear Catálogo"}
                        </Button>
                    </>
                }
            >
                <div className="flex flex-col gap-4">
                    {/* CAMPO 1: NOMBRE */}
                    <label className="block">
                        <span className="text-gray-700 text-sm font-bold">Nombre del Catálogo <span className="text-red-500">*</span></span>
                        <input
                            type="text"
                            className={`mt-1 block w-full rounded-md shadow-sm focus:border-blue-500 focus:ring focus:ring-blue-200 p-2 border ${formTouched && !nuevoCatalogo.trim() ? "border-red-400 bg-red-50" : "border-gray-300"}`}
                            placeholder="Ej: Verano 2026"
                            value={nuevoCatalogo}
                            onChange={(e) => setNuevoCatalogo(e.target.value)}
                            autoFocus
                        />
                        {formTouched && !nuevoCatalogo.trim() && <span className="text-xs text-red-500 mt-1">Campo obligatorio</span>}
                    </label>

                    <div className="grid grid-cols-2 gap-4">
                        {/* CAMPO 2: RECARGO % */}
                        <label className="block">
                            <span className="text-gray-700 text-sm font-bold">Recargo (%)</span>
                            <input
                                type="number"
                                min="0"
                                step="1"
                                className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring focus:ring-blue-200 p-2 border"
                                placeholder="0"
                                value={recargoPorcentaje}
                                onChange={(e) => setRecargoPorcentaje(Number(e.target.value))}
                            />
                        </label>

                        {/* CAMPO 3: EXPORTAR CON IVA (CHECKBOX) */}
                        <div className="flex items-center h-full pt-6">
                            <label className="flex items-center gap-2 cursor-pointer select-none">
                                <input
                                    type="checkbox"
                                    className="w-5 h-5 text-blue-600 rounded border-gray-300 focus:ring-blue-500"
                                    checked={exportarConIva}
                                    onChange={(e) => setExportarConIva(e.target.checked)}
                                />
                                <span className="text-gray-700 text-sm font-medium">
                                    ¿Incluir IVA en precios?
                                </span>
                            </label>
                        </div>
                    </div>
                </div>
            </Modal>
        </main>
    );
}

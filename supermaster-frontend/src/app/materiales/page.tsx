"use client";

import { useMemo, useState } from "react";
import ErrorBanner from "../components/ErrorBanner/ErrorBanner";
import { PuzzlePieceIcon, PlusIcon, TrashIcon, CheckIcon, XMarkIcon } from "@heroicons/react/24/outline";
import { confirmDialog } from "../utils/confirmDialog";
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import SearchInput from "../components/SearchInput/SearchInput";
import Button from "../components/Button/Button";
import Modal from "../components/Modal/Modal";
import { useMateriales } from "./useMateriales";
import { getMaterialesAPI } from "./materialesService";
import { getColumns } from "./columns";
import { type SortingState } from "@tanstack/react-table";
import { useAuth } from "../context/AuthContext";

export default function MaterialesPage() {
    const { hasPermiso } = useAuth();
    const canEdit = hasPermiso("MAESTROS_EDITAR");
    const [pageIndex, setPageIndex] = useState(0);
    const [pageSize, setPageSize] = useState(() => getInitialPageSize("materiales"));
    const [filters, setFilters] = useState<any>({ search: "" });
    const [sorting, setSorting] = useState<SortingState>([{ id: "id", desc: false }]);

    const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [nuevoMaterial, setNuevoMaterial] = useState("");
    const [isSaving, setIsSaving] = useState(false);

    const {
        materiales,
        totalRecords,
        isLoading,
        error,
        createMaterial,
        deleteMaterial,
        updateMaterial
    } = useMateriales(pageIndex, pageSize, filters, sorting);

    const pageCount = totalRecords > 0 ? Math.ceil(totalRecords / pageSize) : 1;
    const selectedIds = Object.keys(rowSelection).map(Number);
    const hasSelection = selectedIds.length > 0;
    const columns = useMemo(() => getColumns(canEdit), [canEdit]);

    const handleExportAll = async () => {
        const sort = sorting.length > 0 ? sorting[0].id + "," + (sorting[0].desc ? "desc" : "asc") : "id,asc";
        const res = await getMaterialesAPI(0, 99999, filters, sort);
        return res.content;
    };

    const handleCreate = async () => {
        if (!nuevoMaterial.trim()) return;
        setIsSaving(true);
        try {
            await createMaterial(nuevoMaterial);
            setIsModalOpen(false);
            setNuevoMaterial("");
        } catch (e) { /* hook already toasts */
        } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = async () => {
        if (!hasSelection) return;
        const nombres = selectedIds.map(i => materiales.find(p => p.id === i)?.nombre).filter(Boolean);
        const detalle = nombres.length <= 3 ? nombres.join(", ") : `${nombres.slice(0, 3).join(", ")} y ${nombres.length - 3} más`;
        if (await confirmDialog({ title: "Eliminar materiales", message: `¿Eliminar ${selectedIds.length === 1 ? `"${detalle}"` : `${selectedIds.length} materiales (${detalle})`}?`, confirmText: "Eliminar", variant: "danger" })) {
            try {
                await deleteMaterial(selectedIds);
                setRowSelection({});
            } catch (e) { /* hook already toasts */ }
        }
    };

    const handleUpdate = (rowIndex: number, columnId: string, value: unknown) => {
        const materialEditar = materiales[rowIndex];
        updateMaterial(materialEditar.id, { [columnId]: value });
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
                        <PuzzlePieceIcon className="w-8 h-8 text-gray-600" />
                        Materiales
                    </h1>
                </div>

                <div className="flex gap-2 items-center">
                    {canEdit && hasSelection && (
                        <Button variant="danger" onClick={handleDelete}>
                            <TrashIcon className="w-4 h-4" />
                            Borrar ({selectedIds.length})
                        </Button>
                    )}
                    <Button
                        variant="dark"
                        onClick={() => setIsModalOpen(true)}
                        disabled={!canEdit}
                    >
                        <PlusIcon className="w-4 h-4" />
                        Crear Material
                    </Button>
                </div>
            </div>

            {error ? <ErrorBanner message={error} /> : (
                <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                    <Table
                    searchSlot={<SearchInput placeholder="Buscar material por nombre..." onSearch={(val) => { if (val !== filters.search) handleGlobalSearch(val); }} initialValue={filters.search} className="w-[26rem] max-w-full" />}
                        tableId="materiales"
                        data={materiales}
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
                        exportFilename="materiales"
                    />
                </div>
            )}

            <Modal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                title="Nuevo Material"
                footer={
                    <>
                        <Button variant="light" onClick={() => setIsModalOpen(false)}>
                            <XMarkIcon className="w-4 h-4" /> Cancelar
                        </Button>
                        <Button variant="dark" onClick={handleCreate}>
                            <CheckIcon className="w-4 h-4" /> {isSaving ? "Creando Material..." : "Crear Material"}
                        </Button>
                    </>
                }
            >
                <div className="flex flex-col gap-4">
                    <label className="block">
                        <span className="text-gray-700 text-sm font-bold">Nombre del Material <span className="text-red-500">*</span></span>
                        <input
                            type="text"
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring focus:ring-blue-200 p-2 border"
                            placeholder="Ej: Acero Inoxidable"
                            value={nuevoMaterial}
                            onChange={(e) => setNuevoMaterial(e.target.value)}
                            autoFocus
                            required
                        />
                    </label>
                </div>
            </Modal>
        </main>
    );
}

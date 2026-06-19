"use client";

import { useState, useMemo } from "react";
import ErrorBanner from "../components/ErrorBanner/ErrorBanner";
import { AdjustmentsHorizontalIcon, CheckIcon, XMarkIcon } from "@heroicons/react/24/outline";
import { confirmDialog } from "../utils/confirmDialog";
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import SearchInput from "../components/SearchInput/SearchInput";
import Button from "../components/Button/Button";
import CreateButton from "../components/Button/CreateButton";
import DeleteButton from "../components/Button/DeleteButton";
import Modal from "../components/Modal/Modal";
import AsyncSelect from "../components/AsyncSelect/AsyncSelect";
import { useTipos } from "./useTipos";
import { getTiposAPI } from "./tiposService";
import { getColumns } from "./columns";
import { type SortingState } from "@tanstack/react-table";
import { useAuth } from "../context/AuthContext";

export default function TiposPage() {
    const { hasPermiso } = useAuth();
    const canEdit = hasPermiso("MAESTROS_EDITAR");
    const [pageIndex, setPageIndex] = useState(0);
    const [pageSize, setPageSize] = useState(() => getInitialPageSize("tipos"));
    const [filters, setFilters] = useState<any>({ search: "" });
    const [sorting, setSorting] = useState<SortingState>([{ id: "id", desc: false }]);

    const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [nuevoNombre, setNuevoNombre] = useState("");
    const [nuevoPadreId, setNuevoPadreId] = useState<number | null>(null);
    const [isSaving, setIsSaving] = useState(false);

    const {
        tipos,
        totalRecords,
        isLoading,
        error,
        createTipo,
        deleteTipo,
        updateTipo,
        searchTipos,
    } = useTipos(pageIndex, pageSize, filters, sorting);

    const pageCount = totalRecords > 0 ? Math.ceil(totalRecords / pageSize) : 1;
    const selectedIds = Object.keys(rowSelection).map(Number);
    const hasSelection = selectedIds.length > 0;

    const handleExportAll = async () => {
        const sort = sorting.length > 0 ? sorting[0].id + "," + (sorting[0].desc ? "desc" : "asc") : "id,asc";
        const res = await getTiposAPI(0, 99999, filters, sort);
        return res.content;
    };

    const handleSearchTipos = async (q: string) => {
        const data = await searchTipos(q);
        // Usamos nombreCompleto ("ABUELO > PADRE > HIJO") como label si hay
        // jerarquía: AsyncSelect destaca el hijo final en negrita.
        return (data.content || []).map((t: any) => ({ id: t.id, label: t.nombreCompleto ?? t.nombre }));
    };

    const columns = useMemo(() => getColumns(handleSearchTipos, canEdit), [canEdit, tipos]);

    const handleCreate = async () => {
        if (!nuevoNombre.trim()) return;
        setIsSaving(true);
        try {
            await createTipo(nuevoNombre, nuevoPadreId);
            setIsModalOpen(false);
            setNuevoNombre("");
            setNuevoPadreId(null);
        } catch (e) { /* hook already toasts */
        } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = async () => {
        if (!hasSelection) return;
        const nombres = selectedIds.map(i => tipos.find(p => p.id === i)?.nombre).filter(Boolean);
        const detalle = nombres.length <= 3 ? nombres.join(", ") : `${nombres.slice(0, 3).join(", ")} y ${nombres.length - 3} más`;
        if (await confirmDialog({ title: "Eliminar tipos", message: `¿Eliminar ${selectedIds.length === 1 ? `"${detalle}"` : `${selectedIds.length} tipos (${detalle})`}?`, confirmText: "Eliminar", variant: "danger" })) {
            try {
                await deleteTipo(selectedIds);
                setRowSelection({});
            } catch (e) { /* hook already toasts */ }
        }
    };

    const handleUpdate = (rowIndex: number, columnId: string, value: unknown) => {
        const tipoEditar = tipos[rowIndex];
        if (columnId === "padreId") {
            updateTipo(tipoEditar.id, { padreId: value as number | null });
        } else {
            updateTipo(tipoEditar.id, { [columnId]: value } as Partial<{ nombre: string; padreId: number | null }>);
        }
    };

    const apiMapping: Record<string, string> = {};

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
        <main className="px-4 py-2 bg-gray-50 min-h-0 flex flex-col overflow-hidden">
            <div className="flex justify-between items-center mb-2">
                <div>
                    <h1 className="text-3xl font-bold text-gray-800 flex items-center gap-2">
                        <AdjustmentsHorizontalIcon className="w-8 h-8 text-gray-600" />
                        Tipos
                    </h1>
                </div>

                <div className="flex gap-2 items-center">
                    {canEdit && hasSelection && (
                        <DeleteButton onClick={handleDelete}>
                            Borrar ({selectedIds.length})
                        </DeleteButton>
                    )}
                    <CreateButton onClick={() => setIsModalOpen(true)} disabled={!canEdit}>
                        Crear Tipo
                    </CreateButton>
                </div>
            </div>

            {error ? <ErrorBanner message={error} /> : (
                <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                    <Table
                    searchSlot={<SearchInput placeholder="Buscar tipo por nombre..." onSearch={(val) => { if (val !== filters.search) handleGlobalSearch(val); }} initialValue={filters.search} className="w-[26rem] max-w-full" />}
                        tableId="tipos"
                        data={tipos}
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
                        exportFilename="tipos"
                    />
                </div>
            )}

            <Modal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                title="Nuevo Tipo"
                footer={
                    <>
                        <Button variant="light" onClick={() => setIsModalOpen(false)}>
                            <XMarkIcon className="w-4 h-4" /> Cancelar
                        </Button>
                        <Button variant="dark" onClick={handleCreate} disabled={isSaving}>
                            <CheckIcon className="w-4 h-4" /> {isSaving ? "Creando Tipo..." : "Crear Tipo"}
                        </Button>
                    </>
                }
            >
                <div className="flex flex-col gap-4">
                    <label className="block">
                        <span className="text-gray-700 text-sm font-bold">Nombre del Tipo <span className="text-red-500">*</span></span>
                        <input
                            type="text"
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring focus:ring-blue-200 p-2 border"
                            placeholder="Ej: Electrodomésticos"
                            value={nuevoNombre}
                            onChange={(e) => setNuevoNombre(e.target.value)}
                            autoFocus
                            required
                        />
                    </label>
                    <AsyncSelect
                        label="Tipo Padre (Opcional)"
                        placeholder="Buscar tipo padre..."
                        loadOptions={handleSearchTipos}
                        onChange={(val) => setNuevoPadreId(val ? Number(val) : null)}
                    />
                </div>
            </Modal>
        </main>
    );
}

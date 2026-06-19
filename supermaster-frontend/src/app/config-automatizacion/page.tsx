"use client";

import { useMemo, useState } from "react";
import ErrorBanner from "../components/ErrorBanner/ErrorBanner";
import { WrenchScrewdriverIcon, CheckIcon, XMarkIcon } from "@heroicons/react/24/outline";
import { confirmDialog } from "../utils/confirmDialog";
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import SearchInput from "../components/SearchInput/SearchInput";
import Button from "../components/Button/Button";
import CreateButton from "../components/Button/CreateButton";
import DeleteButton from "../components/Button/DeleteButton";
import Modal from "../components/Modal/Modal";
import { useConfigAutomatizacion } from "./useConfigAutomatizacion";
import { getConfigAutomatizacionAPI } from "./configAutomatizacionService";
import { getColumns } from "./columns";
import { type SortingState } from "@tanstack/react-table";
import { useAuth } from "../context/AuthContext";

export default function ConfigAutomatizacionPage() {
    const { usuario } = useAuth();
    const isAdmin = (usuario.rol || "").trim().toUpperCase() === "ADMIN";
    const canEdit = isAdmin;
    const [pageIndex, setPageIndex] = useState(0);
    const [pageSize, setPageSize] = useState(() => getInitialPageSize("config_automatizacion"));
    const [filters, setFilters] = useState<any>({ search: "" });
    const [sorting, setSorting] = useState<SortingState>([{ id: "id", desc: false }]);
    const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [clave, setClave] = useState("");
    const [valor, setValor] = useState("");
    const [descripcion, setDescripcion] = useState("");

    const {
        data,
        totalRecords,
        isLoading,
        error,
        createConfig,
        updateConfig,
        deleteConfig,
    } = useConfigAutomatizacion(pageIndex, pageSize, filters, sorting);

    const pageCount = totalRecords > 0 ? Math.ceil(totalRecords / pageSize) : 1;
    const selectedIds = Object.keys(rowSelection).map(Number);
    const hasSelection = selectedIds.length > 0;
    const columns = useMemo(() => getColumns(canEdit), [canEdit]);

    const handleExportAll = async () => {
        const sortParam = sorting.length > 0 ? sorting[0].id + "," + (sorting[0].desc ? "desc" : "asc") : "id,asc";
        const res = await getConfigAutomatizacionAPI(0, 99999, filters, sortParam);
        return res.content;
    };

    const handleCreate = async () => {
        if (!clave.trim() || !valor.trim()) return;
        setIsSaving(true);
        try {
            await createConfig({
                clave,
                valor,
                descripcion: descripcion.trim() || null,
            });
            setIsModalOpen(false);
            setClave("");
            setValor("");
            setDescripcion("");
        } catch (e) { /* hook already toasts */
        } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = async () => {
        if (!hasSelection) return;
        const nombres = selectedIds.map(i => data.find(p => p.id === i)?.clave).filter(Boolean);
        const detalle = nombres.length <= 3 ? nombres.join(", ") : `${nombres.slice(0, 3).join(", ")} y ${nombres.length - 3} más`;
        if (!(await confirmDialog({ title: "Eliminar configuraciones", message: `¿Eliminar ${selectedIds.length === 1 ? `"${detalle}"` : `${selectedIds.length} configuraciones (${detalle})`}?`, confirmText: "Eliminar", variant: "danger" }))) return;
        try {
            await deleteConfig(selectedIds);
            setRowSelection({});
        } catch {
            // toast ya manejado en useConfigAutomatizacion
        }
    };

    const handleUpdate = async (rowIndex: number, columnId: string, value: any) => {
        const itemOriginal = data[rowIndex];
        try {
            await updateConfig(itemOriginal.id, { [columnId]: value });
        } catch (e) { /* hook already toasts */ }
    };

    const apiMapping: Record<string, string> = {};

    const handleGlobalSearch = (valor: string) => {
        setFilters((prev: any) => ({ ...prev, search: valor }));
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

    if (!isAdmin) {
        return (
            <main className="px-4 py-2 bg-gray-50 min-h-0 flex flex-col overflow-hidden dark:bg-slate-900">
                <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-amber-800 dark:border-amber-800 dark:bg-amber-500/10 dark:text-amber-200">
                    Solo los administradores pueden acceder a Configuración.
                </div>
            </main>
        );
    }

    return (
        <main className="px-4 py-2 bg-gray-50 min-h-0 flex flex-col overflow-hidden">
            <div className="flex justify-between items-center mb-2">
                <div>
                    <h1 className="text-3xl font-bold text-gray-800 flex items-center gap-2">
                        <WrenchScrewdriverIcon className="w-8 h-8 text-gray-600" />
                        Config. Automatización Precios KT
                    </h1>
                </div>

                <div className="flex gap-2 items-center">
                    {canEdit && hasSelection && (
                        <DeleteButton onClick={handleDelete}>
                            Borrar ({selectedIds.length})
                        </DeleteButton>
                    )}
                    <CreateButton onClick={() => setIsModalOpen(true)} disabled={!canEdit}>
                        Crear Configuración
                    </CreateButton>
                </div>
            </div>

            {error ? <ErrorBanner message={error} /> : (
                <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                    <Table
                    searchSlot={<SearchInput placeholder="Buscar configuración por clave o valor..." onSearch={(val) => { if (val !== filters.search) handleGlobalSearch(val); }} initialValue={filters.search} className="w-[26rem] max-w-full" />}
                        tableId="config_automatizacion"
                        data={data}
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
                        exportFilename="config-automatizacion"
                    />
                </div>
            )}

            <Modal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                title="Nueva Configuración"
                footer={
                    <>
                        <Button variant="light" onClick={() => setIsModalOpen(false)}><XMarkIcon className="w-4 h-4" /> Cancelar</Button>
                        <Button variant="dark" onClick={handleCreate} disabled={!clave.trim() || !valor.trim()}><CheckIcon className="w-4 h-4" /> {isSaving ? "Creando Configuración..." : "Crear Configuración"}</Button>
                    </>
                }
            >
                <div className="flex flex-col gap-4">
                    <label className="block">
                        <span className="text-gray-700 text-sm font-bold">
                            Clave <span className="text-red-500">*</span>
                        </span>
                        <input
                            type="text"
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring focus:ring-blue-200 p-2 border"
                            placeholder="Ej: MAX_REINTENTOS"
                            value={clave}
                            onChange={(e) => setClave(e.target.value)}
                            autoFocus
                            required
                        />
                    </label>

                    <label className="block">
                        <span className="text-gray-700 text-sm font-bold">
                            Valor <span className="text-red-500">*</span>
                        </span>
                        <input
                            type="text"
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring focus:ring-blue-200 p-2 border"
                            placeholder="Ej: 5"
                            value={valor}
                            onChange={(e) => setValor(e.target.value)}
                            required
                        />
                    </label>

                    <label className="block">
                        <span className="text-gray-700 text-sm font-bold">Descripción</span>
                        <input
                            type="text"
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring focus:ring-blue-200 p-2 border"
                            placeholder="Opcional..."
                            value={descripcion}
                            onChange={(e) => setDescripcion(e.target.value)}
                        />
                    </label>
                </div>
            </Modal>
        </main>
    );
}

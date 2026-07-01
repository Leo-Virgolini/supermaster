"use client";
import { getErrorMessage } from "@/lib/errors";
import { useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { notificar } from "../utils/notificar";
import { UserIcon, CheckIcon, XMarkIcon } from "@heroicons/react/24/outline";
import { confirmDialog } from "../utils/confirmDialog";
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import SearchInput from "../components/SearchInput/SearchInput";
import Button from "../components/Button/Button";
import CreateButton from "../components/Button/CreateButton";
import DeleteButton from "../components/Button/DeleteButton";
import Modal from "../components/Modal/Modal";
import { useAuth } from "../context/AuthContext";
import { useSegmentos } from "./useSegmentos";
import { getSegmentosAPI } from "./segmentosService";
import { getColumns } from "./columns";
import { type SortingState } from "@tanstack/react-table";

export default function SegmentosPage() {
    const searchParams = useSearchParams();
    const { hasPermiso } = useAuth();
    const canEdit = hasPermiso("MAESTROS_EDITAR");
    const [pageIndex, setPageIndex] = useState(0);
    const [pageSize, setPageSize] = useState(() => getInitialPageSize("segmentos"));
    const [filters, setFilters] = useState<any>({ search: searchParams.get("search") ?? "" });
    const [sorting, setSorting] = useState<SortingState>([{ id: "id", desc: true }]);
    const [rowSelection, setRowSelection] = useState({});

    // Modal
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [formTouched, setFormTouched] = useState(false);

    // Campos
    const [segmento, setSegmento] = useState("");

    const { segmentos, totalRecords, isLoading, createSegmento, deleteSegmento, updateSegmento } = useSegmentos(pageIndex, pageSize, filters, sorting);
    const pageCount = totalRecords > 0 ? Math.ceil(totalRecords / pageSize) : 1;
    const selectedIds = Object.keys(rowSelection).map(Number);
    const hasSelection = selectedIds.length > 0;
    const columns = useMemo(() => getColumns(canEdit), [canEdit]);

    const handleExportAll = async () => {
        const sortParam = sorting.length > 0 ? sorting[0].id + "," + (sorting[0].desc ? "desc" : "asc") : "id,asc";
        const res = await getSegmentosAPI(0, 99999, filters, sortParam);
        return res.content;
    };

    const handleCreate = async () => {
        setFormTouched(true);
        if (!segmento.trim()) return;
        try {
            setIsSaving(true);
            await createSegmento({ nombre: segmento });
            setSegmento(""); setFormTouched(false);
            setIsModalOpen(false);
        } catch (e: unknown) { notificar.error("Error: " + getErrorMessage(e)); } finally { setIsSaving(false); }
    };

    const handleDelete = async () => {
        const nombres = selectedIds.map(i => segmentos.find(s => s.id === i)?.nombre).filter(Boolean);
        const detalle = nombres.length <= 3 ? nombres.join(", ") : `${nombres.slice(0, 3).join(", ")} y ${nombres.length - 3} más`;
        if (!(await confirmDialog({ title: "Eliminar segmentos", message: `¿Eliminar ${selectedIds.length === 1 ? `"${detalle}"` : `${selectedIds.length} segmentos (${detalle})`}?`, confirmText: "Eliminar", variant: "danger" }))) return;
        try { await deleteSegmento(selectedIds); setRowSelection({}); } catch (e) { /* hook already toasts */ }
    };

    const handleUpdate = async (rowIndex: number, columnId: string, value: unknown) => {
        const itemOriginal = segmentos[rowIndex];
        try { await updateSegmento(itemOriginal.id, { [columnId]: value }); } catch (e) { /* hook already toasts */ }
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
        <main className="px-4 py-2 bg-gray-50 min-h-0 flex flex-col overflow-hidden">
            <div className="flex justify-between items-center mb-2">
                <div>
                    <h1 className="inline-flex items-center gap-2 leading-none text-3xl font-bold text-gray-800">
                        <UserIcon className="w-8 h-8 text-gray-600" />
                        Segmentos
                    </h1>
                </div>
                <div className="flex gap-2 items-center">
                    {canEdit && hasSelection && (
                        <DeleteButton onClick={handleDelete}>
                            Borrar ({selectedIds.length})
                        </DeleteButton>
                    )}
                    <CreateButton onClick={() => setIsModalOpen(true)} disabled={!canEdit}>
                        Crear Segmento
                    </CreateButton>
                </div>
            </div>

            <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                <Table
                    searchSlot={<SearchInput placeholder="Buscar segmento por nombre..." onSearch={(val) => { if (val !== filters.search) handleGlobalSearch(val); }} initialValue={filters.search} className="w-[26rem] max-w-full" />}
                    tableId="segmentos"
                    isLoading={isLoading}
                    globalFilter={filters.search}
                    setGlobalFilter={handleGlobalSearch}
                    data={segmentos}
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
                    exportFilename="segmentos"
                />
            </div>

            <Modal isOpen={isModalOpen} onClose={() => { setIsModalOpen(false); setFormTouched(false); }} title="Nuevo Segmento"
                footer={<><Button variant="light" onClick={() => { setIsModalOpen(false); setFormTouched(false); }}><XMarkIcon className="w-4 h-4" /> Cancelar</Button><Button variant="dark" onClick={handleCreate} disabled={isSaving}><CheckIcon className="w-4 h-4" /> {isSaving ? "Creando Segmento..." : "Crear Segmento"}</Button></>}>
                <div className="flex flex-col gap-4">
                    <label className="block">
                        <span className="font-bold text-gray-700">Nombre del Segmento <span className="text-red-500">*</span></span>
                        <input type="text" className={`w-full border p-2 rounded ${formTouched && !segmento.trim() ? "border-red-400 bg-red-50" : ""}`} value={segmento} onChange={e => setSegmento(e.target.value)} placeholder="Ej: Mayorista" autoFocus />
                        {formTouched && !segmento.trim() && <span className="text-xs text-red-500 mt-1">Campo obligatorio</span>}
                    </label>
                </div>
            </Modal>
        </main>
    );
}

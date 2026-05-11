"use client";
import { useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { notificar } from "../utils/notificar";
import { TruckIcon, PlusIcon, TrashIcon, CheckIcon, XMarkIcon } from "@heroicons/react/24/outline";
import { confirmDialog } from "../utils/confirmDialog";
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import SearchInput from "../components/SearchInput/SearchInput";
import Button from "../components/Button/Button";
import Modal from "../components/Modal/Modal";
import { useAuth } from "../context/AuthContext";
import { useProveedores } from "./useProveedores";
import { getProveedoresAPI } from "./proveedoresService";
import { getColumns } from "./columns";
import { type SortingState } from "@tanstack/react-table";

export default function ProveedoresPage() {
    const searchParams = useSearchParams();
    const { hasPermiso } = useAuth();
    const canEdit = hasPermiso("MAESTROS_EDITAR");
    const [pageIndex, setPageIndex] = useState(0);
    const [pageSize, setPageSize] = useState(() => getInitialPageSize("proveedores"));
    const [filters, setFilters] = useState<any>({ search: searchParams.get("search") ?? "" });
    const [sorting, setSorting] = useState<SortingState>([{ id: "id", desc: true }]);
    const [rowSelection, setRowSelection] = useState({});

    // Estados del Modal
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [formTouched, setFormTouched] = useState(false);

    // Campos del Formulario (Coinciden con la API)
    const [proveedorNombre, setProveedorNombre] = useState("");
    const [apodo, setApodo] = useState("");
    const [plazoPago, setPlazoPago] = useState("");
    const [financiacionPorcentaje, setFinanciacionPorcentaje] = useState<number | null>(null);
    const [leadTimeDias, setLeadTimeDias] = useState<number | null>(null);
    const [entrega, setEntrega] = useState(false);

    const { proveedores, totalRecords, isLoading, createProveedor, deleteProveedor, updateProveedor } = useProveedores(pageIndex, pageSize, filters, sorting);
    const pageCount = totalRecords > 0 ? Math.ceil(totalRecords / pageSize) : 1;
    const selectedIds = Object.keys(rowSelection).map(Number);
    const hasSelection = selectedIds.length > 0;
    const columns = useMemo(() => getColumns(canEdit), [canEdit]);

    const handleExportAll = async () => {
        const sortParam = sorting.length > 0 ? sorting[0].id + "," + (sorting[0].desc ? "desc" : "asc") : "id,asc";
        const res = await getProveedoresAPI(0, 99999, filters, sortParam);
        return res.content;
    };

    const handleCreate = async () => {
        setFormTouched(true);
        if (!proveedorNombre.trim() || !apodo.trim()) return;
        try {
            setIsSaving(true);
            await createProveedor({
                nombre: proveedorNombre,
                apodo,
                plazoPago,
                financiacionPorcentaje,
                leadTimeDias,
                entrega
            });
            setProveedorNombre(""); setApodo(""); setPlazoPago(""); setFinanciacionPorcentaje(null); setLeadTimeDias(null); setEntrega(false); setFormTouched(false);
            setIsModalOpen(false);
        } catch (e: any) {
            notificar.error("Error: " + e.message);
        } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = async () => {
        const nombres = selectedIds.map(i => proveedores.find(p => p.id === i)?.nombre).filter(Boolean);
        const detalle = nombres.length <= 3 ? nombres.join(", ") : `${nombres.slice(0, 3).join(", ")} y ${nombres.length - 3} más`;
        if (!(await confirmDialog({ title: "Eliminar proveedores", message: `¿Eliminar ${selectedIds.length === 1 ? `"${detalle}"` : `${selectedIds.length} proveedores (${detalle})`}?`, confirmText: "Eliminar", variant: "danger" }))) return;
        try { await deleteProveedor(selectedIds); setRowSelection({}); } catch (e) { /* hook already toasts */ }
    };

    const handleUpdate = async (rowIndex: number, columnId: string, value: unknown) => {
        const itemOriginal = proveedores[rowIndex];
        try { await updateProveedor(itemOriginal.id, { [columnId]: value }); } catch (e) { /* hook already toasts */ }
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

    return (
        <main className="p-4 bg-gray-50 min-h-0 flex flex-col overflow-hidden">
            <div className="flex justify-between items-center mb-3">
                <div>
                    <h1 className="inline-flex items-center gap-2 leading-none text-3xl font-bold text-gray-800">
                        <TruckIcon className="w-8 h-8 text-gray-600" />
                        Proveedores
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
                        Crear Proveedor
                    </Button>
                </div>
            </div>

            <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                <Table
                    searchSlot={<SearchInput placeholder="Buscar proveedor por nombre o alias..." onSearch={(val) => { if (val !== filters.search) handleGlobalSearch(val); }} initialValue={filters.search} className="w-[26rem] max-w-full" />}
                    tableId="proveedores"
                    data={proveedores}
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
                    getActiveFilter={(columnId) => filters[apiMapping[columnId] || columnId]}
                    onExportAll={handleExportAll}
                    exportFilename="proveedores"
                />
            </div>

            <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title="Nuevo Proveedor"
                footer={<><Button variant="light" onClick={() => { setIsModalOpen(false); setFormTouched(false); }}><XMarkIcon className="w-4 h-4" /> Cancelar</Button><Button variant="dark" onClick={handleCreate} disabled={isSaving}><CheckIcon className="w-4 h-4" /> {isSaving ? "Creando Proveedor..." : "Crear Proveedor"}</Button></>}>

                <div className="flex flex-col gap-4">
                    {/* NOMBRE - OBLIGATORIO */}
                    <label className="block">
                        <span className="font-bold text-gray-700">Nombre / Razón Social <span className="text-red-500">*</span></span>
                        <input type="text" className={`w-full border p-2 rounded ${formTouched && !proveedorNombre.trim() ? "border-red-400 bg-red-50" : ""}`} value={proveedorNombre} onChange={e => setProveedorNombre(e.target.value)} placeholder="Ej: Importadora Sudamericana" autoFocus />
                        {formTouched && !proveedorNombre.trim() && <span className="text-xs text-red-500 mt-1">Campo obligatorio</span>}
                    </label>

                    {/* GRID DE 2 COLUMNAS */}
                    <div className="grid grid-cols-2 gap-4">
                        <label className="block">
                            <span className="font-bold text-gray-700">Apodo / Alias <span className="text-red-500">*</span></span>
                            <input type="text" className={`w-full border p-2 rounded ${formTouched && !apodo.trim() ? "border-red-400 bg-red-50" : ""}`} value={apodo} onChange={e => setApodo(e.target.value)} placeholder="Ej: El Sudaca" />
                            {formTouched && !apodo.trim() && <span className="text-xs text-red-500 mt-1">Campo obligatorio</span>}
                        </label>

                        <label className="block">
                            <span className="font-bold text-gray-700">Plazo de Pago</span>
                            <input type="text" className="w-full border p-2 rounded" value={plazoPago} onChange={e => setPlazoPago(e.target.value)} placeholder="Ej: 30/60 días" />
                        </label>

                        <label className="block">
                            <span className="font-bold text-gray-700">% Financiación</span>
                            <input type="number" step="1" className="w-full border p-2 rounded" value={financiacionPorcentaje ?? ""} onChange={e => setFinanciacionPorcentaje(e.target.value === "" ? null : Number(e.target.value))} placeholder="0" />
                        </label>

                        <label className="block">
                            <span className="font-bold text-gray-700">Lead Time (días)</span>
                            <input type="number" min="0" className="w-full border p-2 rounded" value={leadTimeDias ?? ""} onChange={e => setLeadTimeDias(e.target.value === "" ? null : Number(e.target.value))} placeholder="0" />
                        </label>

                        <div className="flex items-center pt-6">
                            <label className="flex items-center gap-2 cursor-pointer">
                                <input type="checkbox" checked={entrega} onChange={e => setEntrega(e.target.checked)} className="w-5 h-5" />
                                <span className="font-medium text-gray-700">¿Realiza entregas?</span>
                            </label>
                        </div>
                    </div>
                </div>
            </Modal>
        </main>
    );
}

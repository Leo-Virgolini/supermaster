"use client";
import { useState, useMemo } from "react";
import ErrorBanner from "../components/ErrorBanner/ErrorBanner";
import { TagIcon, PlusIcon, TrashIcon, CheckIcon, XMarkIcon } from "@heroicons/react/24/outline";
import { confirmDialog } from "../utils/confirmDialog";

import Table, { getInitialPageSize } from "../components/Table/core/Table";
import Button from "../components/Button/Button";
import Modal from "../components/Modal/Modal";
import { useMarcas } from "./useMarcas";
import { getMarcasAPI } from "./marcasService";
import { getColumns } from "./columns";
import { type SortingState } from "@tanstack/react-table";
import AsyncSelect from "../components/AsyncSelect/AsyncSelect";
import SearchInput from "../components/SearchInput/SearchInput";
import { useAuth } from "../context/AuthContext";

export default function MarcasPage() {
    const { hasPermiso } = useAuth();
    const canEdit = hasPermiso("MAESTROS_EDITAR");
    const [pageIndex, setPageIndex] = useState(0);
    const [pageSize, setPageSize] = useState(() => getInitialPageSize("marcas"));
    const [sorting, setSorting] = useState<SortingState>([{ id: "id", desc: false }]);
    const [nuevoPadreId, setNuevoPadreId] = useState<string>("");
    const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [nuevoNombre, setNuevoNombre] = useState("");
    const [isSaving, setIsSaving] = useState(false);
    const [formTouched, setFormTouched] = useState(false);

    const selectedIds = Object.keys(rowSelection).map(Number);
    const hasSelection = selectedIds.length > 0;
    const [filters, setFilters] = useState<any>({ search: "" });
    const { marcas, totalRecords, isLoading, error, pageCount, createMarca, deleteMarca, updateMarca, searchMarcas } = useMarcas(pageIndex, pageSize, filters, sorting);

    const handleExportAll = async () => {
        const sort = sorting.length > 0 ? sorting[0].id + "," + (sorting[0].desc ? "desc" : "asc") : "id,asc";
        const res = await getMarcasAPI(0, 99999, filters, sort);
        return res.content;
    };

    const apiMapping: Record<string, string> = {
        "nombre": "nombre",
        "padreId": "padreId",
    };

    const handleCreate = async () => {
        setFormTouched(true);
        if (!nuevoNombre.trim()) return;
        setIsSaving(true);
        try {
            const idPadreFinal = nuevoPadreId ? Number(nuevoPadreId) : null;
            await createMarca(nuevoNombre, idPadreFinal);
            setIsModalOpen(false);
            setNuevoNombre("");
            setNuevoPadreId("");
            setFormTouched(false);
        } catch (e) { /* hook already toasts */
        } finally {
            setIsSaving(false);
        }
    };
    const handleDelete = async () => {
        if (!hasSelection) return;
        const nombres = selectedIds.map(i => marcas.find(m => m.id === i)?.nombre).filter(Boolean);
        const detalle = nombres.length <= 3 ? nombres.join(", ") : `${nombres.slice(0, 3).join(", ")} y ${nombres.length - 3} más`;
        if (await confirmDialog({ title: "Eliminar marcas", message: `¿Eliminar ${selectedIds.length === 1 ? `"${detalle}"` : `${selectedIds.length} marcas (${detalle})`}?`, confirmText: "Eliminar", variant: "danger" })) {
            try {
                await deleteMarca(selectedIds);
                setRowSelection({});
            } catch (e) { /* hook already toasts */ }
        }
    };
    const handleUpdate = (rowIndex: number, columnId: string, value: unknown) => {
        const marcaEditar = marcas[rowIndex];
        if (columnId === "padreId") {
            updateMarca(marcaEditar.id, { padreId: value as number | null });
        } else {
            updateMarca(marcaEditar.id, { [columnId]: value } as Partial<{ nombre: string; padreId: number | null }>);
        }
    };
    const handleSearchMarcas = async (inputValue: string) => {
        const data = await searchMarcas(inputValue);
        return data.content.map((m: any) => ({ id: m.id, label: m.nombre }));
    };
    const columns = useMemo(() => getColumns(handleSearchMarcas, canEdit), [canEdit, marcas]);
    const handleGlobalSearch = (valor: string) => {

        setFilters((prev: any) => ({
            ...prev,
            search: valor // Actualizamos la clave 'search' dentro del objeto general
        }));
        setPageIndex(0); // Reseteamos a pág 1 siempre que se busca
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
                    <h1 className="text-3xl font-bold text-gray-800 flex items-center gap-2">
                        <TagIcon className="w-8 h-8 text-gray-600" />
                        Marcas
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
                        Crear Marca
                    </Button>
                </div>
            </div>

            {error ? <ErrorBanner message={error} /> : (
                <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                    {/* Barra de búsqueda */}

                    {/* La Tabla */}
                    <Table
                    searchSlot={<SearchInput placeholder="Buscar marca por nombre..." onSearch={(val) => { if (val !== filters.search) handleGlobalSearch(val); }} initialValue={filters.search} className="w-64" />}
                        tableId="marcas"
                        data={marcas}
                        isLoading={isLoading}
                        columns={columns}
                        // Filtros y Orden
                        globalFilter={filters.search}
                        setGlobalFilter={handleGlobalSearch}
                        sorting={sorting}
                        setSorting={setSorting}
                        // Paginación
                        pageIndex={pageIndex}
                        pageSize={pageSize}
                        pageCount={pageCount}
                        onPageChange={setPageIndex}
                        onPageSizeChange={setPageSize}
                        totalRecords={totalRecords}
                        // Selección y Edición
                        rowSelection={rowSelection}
                        setRowSelection={setRowSelection}
                        updateData={handleUpdate}

                        onColumnFilterChange={handleColumnFilterChange}
                        getActiveFilter={(columnId) => filters[apiMapping[columnId] || columnId]}
                        onExportAll={handleExportAll}
                        exportFilename="marcas"
                    />
                </div>
            )}

            <Modal
                isOpen={isModalOpen}
                onClose={() => { setIsModalOpen(false); setFormTouched(false); }}
                title="Nueva Marca"
                footer={
                    <>
                        <Button variant="light" onClick={() => { setIsModalOpen(false); setFormTouched(false); }}>
                            <XMarkIcon className="w-4 h-4" /> Cancelar
                        </Button>
                        <Button
                            variant="dark"
                            onClick={handleCreate}
                            disabled={isSaving}
                        >
                            <CheckIcon className="w-4 h-4" /> {isSaving ? "Creando Marca..." : "Crear Marca"}
                        </Button>
                    </>
                }
            >
                <div className="flex flex-col gap-4">
                    <label className="block">
                        <span className="text-gray-700 text-sm font-bold">Nombre de la Marca <span className="text-red-500">*</span></span>
                        <input
                            type="text"
                            className={`mt-1 block w-full rounded-md shadow-sm focus:border-blue-500 focus:ring focus:ring-blue-200 p-2 border ${formTouched && !nuevoNombre.trim() ? "border-red-400 bg-red-50" : "border-gray-300"}`}
                            placeholder="Ej: Tramontina"
                            value={nuevoNombre}
                            onChange={(e) => setNuevoNombre(e.target.value)}
                            autoFocus
                        />
                        {formTouched && !nuevoNombre.trim() && <span className="text-xs text-red-500 mt-1">Campo obligatorio</span>}
                    </label>

                    {/* 2. EL COMPONENTE NUEVO (AGREGALO ACÁ ABAJO) 👇 */}
                    <AsyncSelect
                        label="Marca Padre (Opcional)"
                        placeholder="Buscar marca padre..."
                        loadOptions={handleSearchMarcas}
                        onChange={(val) => setNuevoPadreId(val ? String(val) : "")}
                    // value={nuevoPadreId} // Si querés que se limpie visualmente, pasale el estado
                    />

                    <p className="text-xs text-gray-500">
                        Si seleccionás una, la nueva marca será una submarca de esa.
                    </p>
                </div>
            </Modal>
        </main>
    );
}

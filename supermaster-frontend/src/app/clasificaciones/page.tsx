"use client";

import { useState, useMemo } from "react";
import { toast } from "sonner";
import ErrorBanner from "../components/ErrorBanner/ErrorBanner";
import { Squares2X2Icon, PlusIcon, TrashIcon, CheckIcon, XMarkIcon } from "@heroicons/react/24/outline";

// Componentes visuales
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import Button from "../components/Button/Button";
import Modal from "../components/Modal/Modal";

// Lógica de negocio (Hooks y Columnas)
import { useClasificaciones } from "./useClasificaciones";
import { getClasificacionesAPI } from "./clasificacionesService";
import { getColumns } from "./columns";
import { type SortingState } from "@tanstack/react-table";
import AsyncSelect from "../components/AsyncSelect/AsyncSelect";
import SearchInput from "../components/SearchInput/SearchInput";
import { confirmDialog } from "../utils/confirmDialog";
import { useAuth } from "../context/AuthContext";

export default function ClasificacionesPage() {
    const { hasPermiso } = useAuth();
    const canEdit = hasPermiso("MAESTROS_EDITAR");
    // --- 1. ESTADOS VISUALES ---
    const [pageIndex, setPageIndex] = useState(0);
    const [pageSize, setPageSize] = useState(() => getInitialPageSize("clasificaciones"));
    const [filters, setFilters] = useState<any>({ search: "" });
    const [sorting, setSorting] = useState<SortingState>([{ id: "id", desc: false }]);
    const [nuevoPadreId, setNuevoPadreId] = useState<string>("");

    // Estado para la Selección (Checkboxes)
    const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});

    // Estados para el Modal de Crear
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [nuevoNombre, setNuevoNombre] = useState("");
    const [isSaving, setIsSaving] = useState(false);

    // --- 2. CONEXIÓN CON EL CEREBRO (HOOK) ---
    const {
        clasificaciones,
        totalRecords,
        isLoading,
        error,
        createClasificacion,
        deleteClasificacion,
        updateClasificacion,
        searchClasificaciones
    } = useClasificaciones(pageIndex, pageSize, filters, sorting);

    const pageCount = totalRecords > 0 ? Math.ceil(totalRecords / pageSize) : 1;

    const handleExportAll = async () => {
        const sortParam = sorting.length > 0 ? sorting[0].id + "," + (sorting[0].desc ? "desc" : "asc") : "id,asc";
        const res = await getClasificacionesAPI(0, 99999, filters, sortParam);
        return res.content;
    };

    // Calculamos qué filas están seleccionadas para el borrado masivo
    const selectedIds = Object.keys(rowSelection).map(Number);
    const hasSelection = selectedIds.length > 0;

    // --- 3. MANEJADORES DE EVENTOS (HANDLERS) ---

    // A. CREAR (Se ejecuta al dar Guardar en el Modal)
    const handleCreate = async () => {
        if (!nuevoNombre.trim()) return;
        setIsSaving(true);
        try {
            const idPadreFinal = nuevoPadreId ? Number(nuevoPadreId) : null;
            await createClasificacion(nuevoNombre, idPadreFinal); // Llama al hook
            setIsModalOpen(false); // Cierra modal
            setNuevoNombre(""); // Limpia input
        } catch (e) { /* hook already toasts */
        } finally {
            setIsSaving(false);
        }
    };

    // B. BORRAR (Se ejecuta al dar click en el botón rojo)
    const handleDelete = async () => {
        if (!hasSelection) return;
        const nombres = selectedIds.map(i => clasificaciones.find(p => p.id === i)?.nombre).filter(Boolean);
        const detalle = nombres.length <= 3 ? nombres.join(", ") : `${nombres.slice(0, 3).join(", ")} y ${nombres.length - 3} más`;
        if (await confirmDialog({ title: "Eliminar clasificaciones", message: `¿Eliminar ${selectedIds.length === 1 ? `"${detalle}"` : `${selectedIds.length} clasificaciones (${detalle})`}?`, confirmText: "Eliminar", variant: "danger" })) {
            try {
                await deleteClasificacion(selectedIds);
                setRowSelection({});
            } catch (e) { /* hook already toasts */ }
        }
    };

    // C. EDITAR (Se ejecuta automáticamente cuando editás una celda y das Enter)
    const handleUpdate = (rowIndex: number, columnId: string, value: unknown) => {
        const clasificacionEditar = clasificaciones[rowIndex];
        if (columnId === "padreId") {
            updateClasificacion(clasificacionEditar.id, { padreId: value as number | null });
        } else {
            updateClasificacion(clasificacionEditar.id, { [columnId]: value } as Partial<{ nombre: string; padreId: number | null }>);
        }
    };

    // D. Adaptador (Solo transforma datos, no llama a API)
    const handleSearchClasificaciones = async (inputValue: string) => {
        const data = await searchClasificaciones(inputValue);
        return data.content.map((m: any) => ({ id: m.id, label: m.nombre }));
    };
    const columns = useMemo(() => getColumns(handleSearchClasificaciones, canEdit), [canEdit, clasificaciones]);

    const apiMapping: Record<string, string> = {
        "padreId": "padreId"
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

    // --- 4. RENDERIZADO (HTML) ---
    return (
        <main className="p-4 bg-gray-50 min-h-0 flex flex-col overflow-hidden">
            {/* Cabecera y Botones Superiores */}
            <div className="flex justify-between items-center mb-3">
                <div>
                    <h1 className="text-3xl font-bold text-gray-800 flex items-center gap-2">
                        <Squares2X2Icon className="w-8 h-8 text-gray-600" />
                        Clasificaciones Generales
                    </h1>
                </div>

                <div className="flex gap-2 items-center">
                    {/* Botón Borrar (Solo aparece si hay selección) */}
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
                        Crear Clasificación
                    </Button>
                </div>
            </div>

            {error ? <ErrorBanner message={error} /> : (
                <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                    <Table
                    searchSlot={<SearchInput placeholder="Buscar clasificación por nombre..." onSearch={(val) => { if (val !== filters.search) handleGlobalSearch(val); }} initialValue={filters.search} className="w-[26rem] max-w-full" />}
                        tableId="clasificaciones"
                        data={clasificaciones}
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
                        updateData={handleUpdate} // <--- Pasamos la función de editar
                        onColumnFilterChange={handleColumnFilterChange}
                        getActiveFilter={(columnId) => filters[apiMapping[columnId] || columnId]}
                        onExportAll={handleExportAll}
                        exportFilename="clasificaciones"
                    />
                </div>
            )}

            {/* Modal de Creación */}
            <Modal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                title="Nueva Clasificación"
                footer={
                    <>
                        <Button variant="light" onClick={() => setIsModalOpen(false)}>
                            <XMarkIcon className="w-4 h-4" /> Cancelar
                        </Button>
                        <Button variant="dark" onClick={handleCreate}>
                            <CheckIcon className="w-4 h-4" /> {isSaving ? "Creando Clasificación..." : "Crear Clasificación"}
                        </Button>
                    </>
                }
            >
                <div className="flex flex-col gap-4">
                    {/* 1. INPUT DE NOMBRE (ESTE YA LO TENÍAS) */}
                    <label className="block">
                        <span className="text-gray-700 text-sm font-bold">Nombre de la Clasificación <span className="text-red-500">*</span></span>
                        <input
                            type="text"
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring focus:ring-blue-200 p-2 border"
                            placeholder="Ej: Tramontina"
                            value={nuevoNombre}
                            onChange={(e) => setNuevoNombre(e.target.value)}
                            autoFocus
                            required
                        />
                    </label>

                    {/* 2. EL COMPONENTE NUEVO (AGREGALO ACÁ ABAJO) 👇 */}
                    <AsyncSelect
                        label="Clasificación Padre (Opcional)"
                        placeholder="Buscar clasificación padre..."
                        loadOptions={handleSearchClasificaciones}
                        onChange={(val) => setNuevoPadreId(val ? String(val) : "")}
                    // value={nuevoPadreId} // Si querés que se limpie visualmente, pasale el estado
                    />

                    <p className="text-xs text-gray-500">
                        Si seleccionás una, la nueva clasificación será una subclasificación de esa.
                    </p>
                </div>
            </Modal>
        </main>
    );
}

"use client";

import { useMemo, useState } from "react";
import ErrorBanner from "../components/ErrorBanner/ErrorBanner";
import { ArchiveBoxIcon, CheckIcon, XMarkIcon } from "@heroicons/react/24/outline";
import { confirmDialog } from "../utils/confirmDialog";

// Componentes visuales
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import SearchInput from "../components/SearchInput/SearchInput";
import Button from "../components/Button/Button";
import CreateButton from "../components/Button/CreateButton";
import DeleteButton from "../components/Button/DeleteButton";
import Modal from "../components/Modal/Modal";

// Lógica de negocio (Hooks y Columnas)
import { useSectoresDeposito } from "./useSectoresDeposito";
import { getSectoresDepositoAPI } from "./sectoresDepositoService";
import { getColumns } from "./columns";
import { type SortingState } from "@tanstack/react-table";
import { useAuth } from "../context/AuthContext";

export default function SectoresDepositoPage() {
    const { hasPermiso } = useAuth();
    const canEdit = hasPermiso("MAESTROS_EDITAR");
    // --- 1. ESTADOS VISUALES ---
    const [pageIndex, setPageIndex] = useState(0);
    const [pageSize, setPageSize] = useState(() => getInitialPageSize("sectores-deposito"));
    const [filters, setFilters] = useState<any>({ search: "" });
    const [sorting, setSorting] = useState<SortingState>([{ id: "id", desc: false }]);

    // Estado para la Selección (Checkboxes)
    const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});

    // Estados para el Modal de Crear
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [nuevoCodigo, setNuevoCodigo] = useState("");
    const [nuevoIdDux, setNuevoIdDux] = useState("");
    const [isSaving, setIsSaving] = useState(false);

    // --- 2. CONEXIÓN CON EL CEREBRO (HOOK) ---
    const {
        sectores,
        totalRecords,
        isLoading,
        error,
        createSector,
        deleteSector,
        updateSector
    } = useSectoresDeposito(pageIndex, pageSize, filters, sorting);

    const pageCount = totalRecords > 0 ? Math.ceil(totalRecords / pageSize) : 1;

    const handleExportAll = async () => {
        const sort = sorting.length > 0 ? sorting[0].id + "," + (sorting[0].desc ? "desc" : "asc") : "id,asc";
        const res = await getSectoresDepositoAPI(0, 99999, filters, sort);
        return res.content;
    };

    // Calculamos qué filas están seleccionadas para el borrado masivo
    const selectedIds = Object.keys(rowSelection).map(Number);
    const hasSelection = selectedIds.length > 0;
    const columns = useMemo(() => getColumns(canEdit), [canEdit]);

    // --- 3. MANEJADORES DE EVENTOS (HANDLERS) ---

    // A. CREAR (Se ejecuta al dar Guardar en el Modal)
    const handleCreate = async () => {
        if (!nuevoCodigo.trim()) return;
        setIsSaving(true);
        try {
            const idDuxTrim = nuevoIdDux.trim();
            await createSector({
                codigo: nuevoCodigo.trim(),
                idDux: idDuxTrim === "" ? null : Number(idDuxTrim),
            }); // Llama al hook
            setIsModalOpen(false); // Cierra modal
            setNuevoCodigo(""); // Limpia inputs
            setNuevoIdDux("");
        } catch (e) { /* hook already toasts */
        } finally {
            setIsSaving(false);
        }
    };

    // B. BORRAR (Se ejecuta al dar click en el botón rojo)
    const handleDelete = async () => {
        if (!hasSelection) return;
        const codigos = selectedIds.map(i => sectores.find(p => p.id === i)?.codigo).filter(Boolean);
        const detalle = codigos.length <= 3 ? codigos.join(", ") : `${codigos.slice(0, 3).join(", ")} y ${codigos.length - 3} más`;
        if (await confirmDialog({ title: "Eliminar sectores de depósito", message: `¿Eliminar ${selectedIds.length === 1 ? `"${detalle}"` : `${selectedIds.length} sectores (${detalle})`}?`, confirmText: "Eliminar", variant: "danger" })) {
            try {
                await deleteSector(selectedIds);
                setRowSelection({});
            } catch (e) { /* hook already toasts */ }
        }
    };

    // C. EDITAR (Se ejecuta automáticamente cuando editás una celda y das Enter)
    const handleUpdate = (rowIndex: number, columnId: string, value: unknown) => {
        const sectorEditar = sectores[rowIndex]; // Buscamos qué sector es

        // Llamamos al hook para actualizar
        updateSector(sectorEditar.id, { [columnId]: value });
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

    // --- 4. RENDERIZADO (HTML) ---
    return (
        <main className="px-4 py-2 bg-gray-50 min-h-0 flex flex-col overflow-hidden">
            {/* Cabecera y Botones Superiores */}
            <div className="flex justify-between items-center mb-2">
                <div>
                    <h1 className="text-3xl font-bold text-gray-800 flex items-center gap-2">
                        <ArchiveBoxIcon className="w-8 h-8 text-gray-600" />
                        Sectores de depósito
                    </h1>
                </div>

                <div className="flex gap-2 items-center">
                    {/* Botón Borrar (Solo aparece si hay selección) */}
                    {canEdit && hasSelection && (
                        <DeleteButton onClick={handleDelete}>
                            Borrar ({selectedIds.length})
                        </DeleteButton>
                    )}
                    <CreateButton onClick={() => setIsModalOpen(true)} disabled={!canEdit}>
                        Crear Sector
                    </CreateButton>
                </div>
            </div>

            {error ? <ErrorBanner message={error} /> : (
                <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                    <Table
                    searchSlot={<SearchInput placeholder="Buscar sector por código..." onSearch={(val) => { if (val !== filters.search) handleGlobalSearch(val); }} initialValue={filters.search} className="w-[26rem] max-w-full" />}
                        tableId="sectores-deposito"
                        data={sectores}
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
                        hasFiltersActive={hasActiveFilters}
                        onClearAllFilters={clearAllFilters}
                        getActiveFilter={(columnId) => filters[apiMapping[columnId] || columnId]}
                        onExportAll={handleExportAll}
                        exportFilename="sectores-deposito"
                    />
                </div>
            )}

            {/* Modal de Creación */}
            <Modal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                title="Nuevo Sector de depósito"
                footer={
                    <>
                        <Button variant="light" onClick={() => setIsModalOpen(false)}>
                            <XMarkIcon className="w-4 h-4" /> Cancelar
                        </Button>
                        <Button variant="dark" onClick={handleCreate} disabled={isSaving}>
                            <CheckIcon className="w-4 h-4" /> {isSaving ? "Creando Sector..." : "Crear Sector"}
                        </Button>
                    </>
                }
            >
                <div className="flex flex-col gap-4">
                    {/* 1. INPUT DE CÓDIGO */}
                    <label className="block">
                        <span className="text-gray-700 text-sm font-bold">Código <span className="text-red-500">*</span></span>
                        <input
                            type="text"
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring focus:ring-blue-200 p-2 border"
                            placeholder="Ej: DEP-01"
                            value={nuevoCodigo}
                            onChange={(e) => setNuevoCodigo(e.target.value)}
                            autoFocus
                            required
                        />
                    </label>

                    {/* 2. INPUT DE ID DUX (opcional) */}
                    <label className="block">
                        <span className="text-gray-700 text-sm font-bold">ID Dux</span>
                        <input
                            type="number"
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring focus:ring-blue-200 p-2 border"
                            placeholder="Ej: 1234 (opcional)"
                            value={nuevoIdDux}
                            onChange={(e) => setNuevoIdDux(e.target.value)}
                        />
                    </label>
                </div>
            </Modal>
        </main>
    );
}

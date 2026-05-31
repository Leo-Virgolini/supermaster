"use client";

import { useEffect, useMemo, useState } from "react";
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import SearchInput from "../components/SearchInput/SearchInput";
import Modal from "../components/Modal/Modal";
import Button from "../components/Button/Button";
import AsyncSelect from "../components/AsyncSelect/AsyncSelect";
import { useAuth } from "../context/AuthContext";
import { useCanalConceptoCuota } from "./useCanalConceptoCuota";
import { getCuotasAPI } from "./canalConceptoCuotaService";
import { getColumns } from "./columns";
import { type SortingState } from "@tanstack/react-table";
import { CreditCardIcon, PlusIcon, TrashIcon, CheckIcon, XMarkIcon, InformationCircleIcon, ChevronDownIcon, FunnelIcon } from "@heroicons/react/24/outline";
import ErrorBanner from "../components/ErrorBanner/ErrorBanner";
import { confirmDialog } from "../utils/confirmDialog";
import { CanalConceptoCuotaPatchDTO } from "./types";
import { getCanalesAPI } from "../canales/canalesService";
import CanalSelectBadge from "../components/CanalSelectBadge/CanalSelectBadge";

export default function CanalConceptoCuotaPage() {
    const { hasPermiso } = useAuth();
    const canEdit = hasPermiso("PRECIOS_EDITAR");
    const [pageIndex, setPageIndex] = useState(0);
    const [pageSize, setPageSize] = useState(() => getInitialPageSize("cuotas"));
    const [search, setSearch] = useState("");
    const [columnFilters, setColumnFilters] = useState<Record<string, any>>({});
    const [sorting, setSorting] = useState<SortingState>([{ id: "canalNombre", desc: false }]);
    const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});

    const [ayudaAbierta, setAyudaAbierta] = useState(false);

    // Filtro por canal (server-side): muestra solo las cuotas del canal elegido.
    const [canalesList, setCanalesList] = useState<{ id: number; nombre: string }[]>([]);
    const [canalFilterId, setCanalFilterId] = useState<number | null>(null);

    useEffect(() => {
        let cancelled = false;
        getCanalesAPI(0, 500, {}, "nombre,asc")
            .then((json) => {
                if (cancelled) return;
                const items = (json.content ?? []).map((c: any) => ({ id: c.id, nombre: c.nombre }));
                setCanalesList(items);
            })
            .catch(() => { /* silencio: filtro queda deshabilitado si falla */ });
        return () => { cancelled = true; };
    }, []);

    // Modal solo para crear
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [formTouched, setFormTouched] = useState(false);

    // Campos del formulario de creación
    const [canalId, setCanalId] = useState<number | null>(null);
    const [canalNombre, setCanalNombre] = useState("");
    const [descripcion, setDescripcion] = useState("");
    const [cuotas, setCuotas] = useState<number>(0);
    const [porcentaje, setPorcentaje] = useState<number>(0);

    // El filtro por canal va server-side: lo combinamos con los filtros de columna
    // como un param más (canalId) que el backend interpreta.
    const filtrosEfectivos = useMemo(
        () => (canalFilterId != null ? { ...columnFilters, canalId: canalFilterId } : columnFilters),
        [columnFilters, canalFilterId],
    );

    const {
        data, totalRecords, pageCount, isLoading, error,
        createCuota, updateCuota, deleteCuota, searchCanales
    } = useCanalConceptoCuota(pageIndex, pageSize, search, filtrosEfectivos, sorting);
    const columns = useMemo(() => getColumns(canEdit), [canEdit]);

    const SORT_MAP: Record<string, string> = { canalNombre: "canal.nombre" };
    const handleExportAll = async () => {
        const rawId = sorting.length > 0 ? sorting[0].id : "id";
        const sortParam = `${SORT_MAP[rawId] || rawId},${sorting.length > 0 && sorting[0].desc ? "desc" : "asc"}`;
        const res = await getCuotasAPI(0, 99999, search, filtrosEfectivos, sortParam);
        return res.content;
    };

    const resetForm = () => {
        setCanalId(null);
        setCanalNombre("");
        setDescripcion("");
        setCuotas(0);
        setPorcentaje(0);
        setFormTouched(false);
    };

    const handleCreate = async () => {
        setFormTouched(true);
        if (!canalId) return;
        setIsSaving(true);
        try {
            await createCuota({ canalId, descripcion, cuotas, porcentaje });
            resetForm();
            setIsModalOpen(false);
        } catch (e) { /* hook already toasts */ } finally {
            setIsSaving(false);
        }
    };

    const handleUpdate = async (rowIndex: number, columnId: string, value: unknown) => {
        const item = data[rowIndex];
        try {
            await updateCuota(item.id, { [columnId]: value } as CanalConceptoCuotaPatchDTO);
        } catch (e) { /* hook already toasts */ }
    };

    const handleDelete = async () => {
        const ids = Object.keys(rowSelection).map(Number);
        const nombres = ids.map(i => data.find(p => p.id === i)?.descripcion).filter(Boolean);
        const detalle = nombres.length <= 3 ? nombres.join(", ") : `${nombres.slice(0, 3).join(", ")} y ${nombres.length - 3} más`;
        if (await confirmDialog({ title: "Eliminar cuotas", message: `¿Eliminar ${ids.length === 1 ? `"${detalle}"` : `${ids.length} cuotas (${detalle})`}?`, confirmText: "Eliminar", variant: "danger" })) {
            await deleteCuota(ids);
            setRowSelection({});
        }
    };

    const handleColumnFilterChange = (id: string, val: any) => {
        setColumnFilters(prev => {
            const next = { ...prev };
            if (!val) delete next[id]; else next[id] = val;
            return next;
        });
        setPageIndex(0);
    };

    // `hasActiveFilters`: hay filtros por columna activos (no contamos search,
    // porque eso ya lo detecta el Table vía globalFilter).
    const hasActiveFilters = Object.entries(columnFilters).some(([key, value]) => {
        if (key === "search") return false;
        if (value === undefined || value === null || value === "") return false;
        if (Array.isArray(value) && value.length === 0) return false;
        return true;
    });

    const clearAllFilters = () => {
        setColumnFilters({});
        setPageIndex(0);
    };

    return (
        <main className="p-4 bg-gray-50 min-h-0 flex flex-col overflow-hidden">
            <div className="flex justify-between items-center mb-3">
                <h1 className="text-3xl font-bold text-gray-800 flex items-center gap-2">
                    <CreditCardIcon className="w-8 h-8 text-gray-600" />
                    Cuotas por Canal
                </h1>
                <div className="flex gap-2">
                    {canEdit && Object.keys(rowSelection).length > 0 && (
                        <Button variant="danger" onClick={handleDelete}>
                            <TrashIcon className="w-4 h-4" />
                            Borrar ({Object.keys(rowSelection).length})
                        </Button>
                    )}
                    <Button variant="dark" onClick={() => setIsModalOpen(true)} disabled={!canEdit}>
                        <PlusIcon className="w-4 h-4" />
                        Crear Cuota
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
                        ¿Para qué sirven las Cuotas por Canal?
                    </span>
                    <ChevronDownIcon className={`h-4 w-4 transition-transform ${ayudaAbierta ? "rotate-180" : ""}`} />
                </button>
                {ayudaAbierta && (
                    <div className="border-t border-blue-200 px-4 py-3 text-sm text-blue-900 dark:border-blue-800/60 dark:text-blue-100">
                        <p className="mb-3">
                            Definen los <strong>planes de financiación disponibles para cada canal</strong> y el recargo o descuento que aplica
                            cada plan al precio final.
                        </p>

                        <div className="mb-3 rounded-md bg-white/60 p-3 text-xs dark:bg-blue-950/40">
                            <p className="mb-2 font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
                                Convención del campo &quot;Cuotas&quot;
                            </p>
                            <ul className="ml-4 list-disc space-y-0.5">
                                <li><span className="font-mono">-1</span>: Transferencia (descuento típico)</li>
                                <li><span className="font-mono">0</span>: Contado / sin cuotas</li>
                                <li><span className="font-mono">3, 6, 9, 12...</span>: Cantidad de cuotas (con interés típico)</li>
                            </ul>
                        </div>

                        <p className="mb-3">
                            El <strong>porcentaje</strong> se suma a los conceptos &quot;sobre PVP&quot; del canal y se aplica como divisor:{" "}
                            <span className="font-mono">PVP = costo / (1 − (gastos% + cuota%) / 100)</span>. Un porcentaje positivo
                            <em>aumenta</em> el PVP (interés) y uno negativo lo <em>reduce</em> (descuento por transferencia).
                        </p>

                        <div className="rounded-md border border-amber-300 bg-amber-50 p-2.5 text-xs text-amber-900 dark:border-amber-700/60 dark:bg-amber-900/30 dark:text-amber-100">
                            Las cuotas configuradas acá también determinan qué planes se exportan en{" "}
                            <strong>Lista de Precios → cuotas</strong> y qué columnas aparecen en el <strong>Monitor de Precios</strong>.
                        </div>
                    </div>
                )}
            </div>

            <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                {error ? <ErrorBanner message={error} /> : (
                    <Table
                        tableId="cuotas"
                        searchSlot={
                            <div className="flex flex-wrap items-center gap-2">
                                <SearchInput placeholder="Buscar cuota por canal o cantidad..." onSearch={setSearch} />
                                <div className="flex items-center gap-1.5">
                                    <FunnelIcon className="h-4 w-4 text-slate-400" />
                                    <CanalSelectBadge
                                        canales={canalesList}
                                        value={canalFilterId}
                                        onChange={(id) => { setCanalFilterId(id); setPageIndex(0); }}
                                        allowAll
                                        className="w-56"
                                    />
                                </div>
                            </div>
                        }
                        data={data}
                        isLoading={isLoading}
                        columns={columns}
                        pageIndex={pageIndex}
                        pageSize={pageSize}
                        pageCount={pageCount}
                        sorting={sorting}
                        globalFilter={search}
                        rowSelection={rowSelection}
                        onPageChange={setPageIndex}
                        onPageSizeChange={setPageSize}
                        totalRecords={totalRecords}
                        setSorting={setSorting}
                        setGlobalFilter={(value) => { setSearch(String(value)); setPageIndex(0); }}
                        setRowSelection={setRowSelection}
                        updateData={handleUpdate}
                        onColumnFilterChange={handleColumnFilterChange}
                        hasFiltersActive={hasActiveFilters}
                        onClearAllFilters={clearAllFilters}
                        getActiveFilter={(id) => columnFilters[id]}
                        onExportAll={handleExportAll}
                        exportFilename="canal-concepto-cuotas"
                    />
                )}
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={() => { setIsModalOpen(false); resetForm(); }}
                title="Nueva Cuota"
                footer={
                    <>
                        <Button variant="light" onClick={() => { setIsModalOpen(false); resetForm(); }}>
                            <XMarkIcon className="w-4 h-4" /> Cancelar
                        </Button>
                        <Button variant="dark" onClick={handleCreate} disabled={isSaving}>
                            <CheckIcon className="w-4 h-4" /> {isSaving ? "Creando Cuota..." : "Crear Cuota"}
                        </Button>
                    </>
                }
            >
                <div className="grid gap-4">
                    <div>
                        <AsyncSelect
                            label={<>Canal <span className="text-red-500">*</span></>}
                            placeholder="Buscar Canal..."
                            loadOptions={async (val) => {
                                const res = await searchCanales(val);
                                return res.content.map((c: any) => ({ label: c.nombre, id: c.id }));
                            }}
                            onChange={(val, label) => { setCanalId(Number(val)); setCanalNombre(label || ""); }}
                            displayValue={canalNombre || undefined}
                        />
                        {formTouched && !canalId && <span className="text-xs text-red-500 mt-1">Campo obligatorio</span>}
                    </div>

                    <label className="block">
                        <span className="text-sm font-bold">Descripción</span>
                        <input
                            className="w-full border border-gray-300 p-2 rounded"
                            value={descripcion}
                            onChange={e => setDescripcion(e.target.value)}
                            placeholder="Ej: Ahora 12"
                        />
                    </label>

                    <div className="grid grid-cols-2 gap-4">
                        <label className="block">
                            <span className="text-sm font-bold">Cant. Cuotas (-1 = Transf) <span className="text-red-500">*</span></span>
                            <input
                                type="number"
                                className="w-full border border-gray-300 p-2 rounded"
                                value={cuotas}
                                onChange={e => setCuotas(Number(e.target.value))}
                            />
                        </label>

                        <label className="block">
                            <span className="text-sm font-bold">Interés/Desc. (%) <span className="text-red-500">*</span></span>
                            <input
                                type="number"
                                step="1"
                                className="w-full border border-gray-300 p-2 rounded"
                                value={porcentaje}
                                onChange={e => setPorcentaje(Number(e.target.value))}
                            />
                        </label>
                    </div>
                </div>
            </Modal>
        </main>
    );
}


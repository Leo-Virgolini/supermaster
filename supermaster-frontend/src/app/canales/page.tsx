"use client";

import { getErrorMessage } from "@/lib/errors";
import dynamic from "next/dynamic";
import { useState, useMemo } from "react";
import { notificar } from "../utils/notificar";
import ErrorBanner from "../components/ErrorBanner/ErrorBanner";
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import SearchInput from "../components/SearchInput/SearchInput";
import Button from "../components/Button/Button";
import Modal from "../components/Modal/Modal";
import { useCanales } from "./useCanales";
import { getCanalesAPI } from "./canalesService";
import { clonarConceptosDeCanalAPI } from "./canalConceptosService";
import { getColumns } from "./columns";
import { CanalDTO } from "./types";
import { type SortingState } from "@tanstack/react-table";
import { ChartBarIcon, PlusIcon, TrashIcon, CheckIcon, XMarkIcon, InformationCircleIcon, ChevronDownIcon } from "@heroicons/react/24/outline";
import { confirmDialog } from "../utils/confirmDialog";
import { useAuth } from "../context/AuthContext";

const CanalConceptosModal = dynamic(() => import("./CanalConceptosModal"), {
    loading: () => null,
});

export default function CanalesPage() {
    const { hasPermiso } = useAuth();
    const canEdit = hasPermiso("CANALES_EDITAR");
    const [pageIndex, setPageIndex] = useState(0);
    const [pageSize, setPageSize] = useState(() => getInitialPageSize("canales"));
    const [filters, setFilters] = useState<any>({ search: "" });
    const [sorting, setSorting] = useState<SortingState>([{ id: "id", desc: false }]);

    const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [nuevoCanal, setNuevoCanal] = useState("");
    const [canalBaseId, setCanalBaseId] = useState<number | null>(null);
    const [copiarDeCanalId, setCopiarDeCanalId] = useState<number | null>(null);
    const [isSaving, setIsSaving] = useState(false);
    const [formTouched, setFormTouched] = useState(false);

    const [conceptosCanal, setConceptosCanal] = useState<CanalDTO | null>(null);

    const [ayudaCanalesAbierta, setAyudaCanalesAbierta] = useState(false);
    const [ayudaCanalBaseAbierta, setAyudaCanalBaseAbierta] = useState(false);

    const {
        canales,
        totalRecords,
        isLoading,
        error,
        createCanal,
        deleteCanal,
        updateCanal,
        searchCanales,
    } = useCanales(pageIndex, pageSize, filters, sorting);

    const pageCount = totalRecords > 0 ? Math.ceil(totalRecords / pageSize) : 1;
    const selectedIds = Object.keys(rowSelection).map(Number);
    const hasSelection = selectedIds.length > 0;

    const handleExportAll = async () => {
        const sort = sorting.length > 0 ? sorting[0].id + "," + (sorting[0].desc ? "desc" : "asc") : "id,asc";
        const res = await getCanalesAPI(0, 99999, filters, sort);
        return res.content;
    };

    const handleSearchCanales = async (q: string) => {
        const data = await searchCanales(q);
        return data.content.map((c: any) => ({ id: c.id, label: c.nombre }));
    };

    const columns = useMemo(
        () => getColumns((canal) => setConceptosCanal(canal), handleSearchCanales, canEdit),
        [canEdit, canales]
    );

    const handleCreate = async () => {
        setFormTouched(true);
        if (!nuevoCanal.trim()) return;
        setIsSaving(true);
        try {
            const creado = await createCanal({ nombre: nuevoCanal, canalBaseId: canalBaseId ?? undefined });
            // Si el usuario eligió clonar conceptos de otro canal, lo hacemos
            // post-creación. Errores aquí no rollbackean el canal — el usuario
            // ve el toast y decide si reintentar manualmente desde Conceptos.
            if (copiarDeCanalId != null && creado?.id != null) {
                try {
                    const result = await clonarConceptosDeCanalAPI(creado.id, copiarDeCanalId, "FORM");
                    notificar.success(`Se copiaron ${result.copiadas} conceptos al canal nuevo.`);
                } catch (e: unknown) {
                    notificar.error(getErrorMessage(e, "Canal creado, pero falló la copia de conceptos."));
                }
            }
            setIsModalOpen(false);
            setNuevoCanal("");
            setCanalBaseId(null);
            setCopiarDeCanalId(null);
            setFormTouched(false);
        } catch (e) { /* hook already toasts */ } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = async () => {
        if (!hasSelection) return;
        const nombres = selectedIds.map(i => canales.find(c => c.id === i)?.nombre).filter(Boolean);
        const detalle = nombres.length <= 3 ? nombres.join(", ") : `${nombres.slice(0, 3).join(", ")} y ${nombres.length - 3} más`;
        if (await confirmDialog({ title: "Eliminar canales", message: `¿Eliminar ${selectedIds.length === 1 ? `"${detalle}"` : `${selectedIds.length} canales (${detalle})`}?`, confirmText: "Eliminar", variant: "danger" })) {
            try {
                await deleteCanal(selectedIds);
                setRowSelection({});
            } catch (e) { /* hook already toasts */ }
        }
    };

    const handleUpdate = async (rowIndex: number, columnId: string, value: unknown) => {
        const itemOriginal = canales[rowIndex];
        try {
            await updateCanal(itemOriginal.id, { [columnId]: value } as Partial<CanalDTO>);
        } catch (error) {
            notificar.error((error as any)?.message || "Error al guardar los cambios.");
        }
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

    return (
        <main className="p-4 bg-gray-50 min-h-0 flex flex-col overflow-hidden">
            <div className="flex justify-between items-center mb-3">
                <div>
                    <h1 className="text-3xl font-bold text-gray-800 flex items-center gap-2">
                        <ChartBarIcon className="w-8 h-8 text-gray-600" />
                        Canales
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
                        Crear Canal
                    </Button>
                </div>
            </div>

            <div className="mb-3 rounded-lg border border-blue-200 bg-blue-50 dark:border-blue-800/60 dark:bg-blue-900/20">
                <button
                    type="button"
                    onClick={() => setAyudaCanalesAbierta((v) => !v)}
                    className="flex w-full items-center justify-between gap-2 px-4 py-2 text-left text-sm font-semibold text-blue-900 dark:text-blue-200"
                >
                    <span className="flex items-center gap-2">
                        <InformationCircleIcon className="h-5 w-5" />
                        ¿Para qué sirven los Canales?
                    </span>
                    <ChevronDownIcon className={`h-4 w-4 transition-transform ${ayudaCanalesAbierta ? "rotate-180" : ""}`} />
                </button>
                {ayudaCanalesAbierta && (
                    <div className="border-t border-blue-200 px-4 py-3 text-sm text-blue-900 dark:border-blue-800/60 dark:text-blue-100">
                        <p className="mb-3">
                            Un <strong>Canal</strong> representa un <strong>punto de venta o lista de precios</strong> (ej:{" "}
                            <span className="font-mono">ML</span>, <span className="font-mono">KT HOGAR</span>,{" "}
                            <span className="font-mono">KT GASTRO</span>, mayorista, minorista, etc.). Cada canal tiene su propia estructura
                            de cálculo definida por los conceptos, reglas y cuotas que le asignes.
                        </p>

                        <div className="mb-3 rounded-md bg-white/60 p-3 text-xs dark:bg-blue-950/40">
                            <p className="mb-2 font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
                                Qué se configura en cada canal
                            </p>
                            <ul className="ml-4 list-disc space-y-0.5">
                                <li><strong>Conceptos</strong>: gastos, comisiones, IVA, márgenes, descuentos que componen el precio. Click en <strong>Conceptos</strong> en cada fila.</li>
                                <li><strong>Reglas de Canal</strong>: deciden qué productos forman parte del canal (eligibilidad).</li>
                                <li><strong>Reglas de Excepción</strong>: ajustan cómo aplican los conceptos a subconjuntos de productos (INCLUIR / EXCLUIR por marca, tipo, tag, etc.).</li>
                                <li><strong>Cuotas</strong>: planes de financiación con su recargo o descuento.</li>
                                <li><strong>Reglas de Descuento</strong>: descuentos automáticos por monto mínimo de compra.</li>
                                <li><strong>Canal Base</strong> (opcional): hereda el PVP de otro canal como punto de partida.</li>
                            </ul>
                        </div>

                        <p className="mb-0">
                            El precio final de cada producto en cada canal se ve en el <strong>Monitor de Precios</strong>, y la estructura
                            visual del cálculo (conceptos por etapa, ramificaciones por reglas) se ve en <strong>Fórmula del Canal</strong>.
                        </p>
                    </div>
                )}
            </div>

            <div className="mb-3 rounded-lg border border-blue-200 bg-blue-50 dark:border-blue-800/60 dark:bg-blue-900/20">
                <button
                    type="button"
                    onClick={() => setAyudaCanalBaseAbierta((v) => !v)}
                    className="flex w-full items-center justify-between gap-2 px-4 py-2 text-left text-sm font-semibold text-blue-900 dark:text-blue-200"
                >
                    <span className="flex items-center gap-2">
                        <InformationCircleIcon className="h-5 w-5" />
                        ¿Qué pasa cuando un canal tiene canal base?
                    </span>
                    <ChevronDownIcon className={`h-4 w-4 transition-transform ${ayudaCanalBaseAbierta ? "rotate-180" : ""}`} />
                </button>
                {ayudaCanalBaseAbierta && (
                    <div className="border-t border-blue-200 px-4 py-3 text-sm text-blue-900 dark:border-blue-800/60 dark:text-blue-100">
                        <p className="mb-3">
                            Cuando un canal tiene <strong>canal base</strong>, su precio se calcula{" "}
                            <strong>partiendo del PVP del canal base</strong> en vez de hacerlo desde el costo del producto.
                        </p>

                        <div className="mb-3 rounded-md bg-white/60 p-3 text-xs dark:bg-blue-950/40">
                            <p className="mb-2 font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
                                Ejemplo
                            </p>
                            <p>
                                <strong>KT GASTRO</strong> tiene como canal base a <strong>KT HOGAR</strong>. Si en KT HOGAR el PVP de un producto es{" "}
                                <span className="font-mono">$10.000</span> y en KT GASTRO asignás un concepto{" "}
                                <span className="font-mono">+15%</span> de tipo &quot;Cálculo sobre canal base&quot;, el PVP en KT GASTRO será{" "}
                                <span className="font-mono">$11.500</span>.
                            </p>
                        </div>

                        <div className="mb-3 rounded-md bg-white/60 p-3 text-xs dark:bg-blue-950/40">
                            <p className="mb-2 font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
                                Variantes de &quot;Cálculo sobre canal base&quot;
                            </p>
                            <ul className="ml-4 list-disc space-y-1">
                                <li>
                                    <strong>Canal propio</strong>: el factor escala <em>tanto el PVP final como el ingreso del dueño</em>.
                                    Útil cuando el canal hijo es del propio negocio (vos cobrás el PVP completo).
                                </li>
                                <li>
                                    <strong>Reseller</strong>: el factor escala el PVP final, pero el ingreso del dueño se &quot;corta&quot; en este punto.
                                    El reseller compra al precio reseller y agrega su propio markup encima — el dueño solo cobra hasta el corte.
                                    Ejemplo: <span className="font-mono">LIZZY GASTRO</span> compra a <span className="font-mono">×0,72</span> (reseller)
                                    y vende ese precio <span className="font-mono">×1,5</span> a su cliente final (canal propio).
                                </li>
                            </ul>
                        </div>

                        <div className="mb-3">
                            <p className="mb-1 font-semibold text-emerald-700 dark:text-emerald-300">✓ Lo que SÍ aplica del canal hijo:</p>
                            <ul className="ml-4 list-disc space-y-0.5">
                                <li><strong>Reglas de Canal</strong> del hijo (filtros para decidir qué productos aplican).</li>
                                <li>Conceptos del hijo cuyo &quot;Aplica Sobre&quot; sea <strong>Cálculo sobre canal base</strong> (variantes <em>canal propio</em> y <em>reseller</em>): se aplican como multiplicadores en cascada sobre el PVP del padre.</li>
                            </ul>
                        </div>

                        <div className="mb-3">
                            <p className="mb-1 font-semibold text-amber-700 dark:text-amber-300">✕ Lo que NO se aplica del canal hijo:</p>
                            <ul className="ml-4 list-disc space-y-0.5">
                                <li>Cualquier otro concepto del hijo (gastos sobre costo, IVA, comisiones, márgenes, descuentos, etc.) — quedan ignorados aunque estén asignados.</li>
                                <li><strong>Margen</strong> del hijo (no se usa el ProductoMargen del producto en este canal).</li>
                                <li><strong>Cuotas</strong> del hijo: el porcentaje de la cuota no se aplica encima del PVP del padre.</li>
                                <li><strong>Reglas de excepción</strong> sobre conceptos que no sean &quot;sobre canal base&quot; (no tienen efecto porque esos conceptos ya están descartados).</li>
                            </ul>
                        </div>

                        <div className="rounded-md border border-amber-300 bg-amber-50 p-2.5 text-xs text-amber-900 dark:border-amber-700/60 dark:bg-amber-900/30 dark:text-amber-100">
                            <strong>Importante:</strong> los conceptos del canal padre tampoco se heredan al hijo automáticamente. Si querés que el hijo tenga los mismos
                            recargos que el padre, configurale <em>Cálculo sobre canal base</em> con el porcentaje deseado, o asigná los conceptos en otro canal sin canal base.
                        </div>
                    </div>
                )}
            </div>

            {error ? <ErrorBanner message={error} /> : (
                <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                    <Table
                    searchSlot={<SearchInput placeholder="Buscar canal por nombre..." onSearch={(val) => { if (val !== filters.search) handleGlobalSearch(val); }} initialValue={filters.search} className="w-[26rem] max-w-full" />}
                        tableId="canales"
                        data={canales}
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
                        exportFilename="canales"
                    />
                </div>
            )}

            <Modal
                isOpen={isModalOpen}
                onClose={() => { setIsModalOpen(false); setFormTouched(false); }}
                title="Nuevo Canal"
                footer={
                    <>
                        <Button text="Cancelar" variant="light" onClick={() => { setIsModalOpen(false); setFormTouched(false); }}>
                            <XMarkIcon className="w-4 h-4" />
                        </Button>
                        <Button
                            variant="dark"
                            onClick={handleCreate}
                        >
                            <CheckIcon className="w-4 h-4" />
                            {isSaving ? "Creando Canal..." : "Crear Canal"}
                        </Button>
                    </>
                }
            >
                <div className="flex flex-col gap-4">
                    <label className="block">
                        <span className="text-gray-700 text-sm font-bold">Nombre del Canal <span className="text-red-500">*</span></span>
                        <input
                            type="text"
                            className={`mt-1 block w-full rounded-md shadow-sm focus:border-blue-500 focus:ring focus:ring-blue-200 p-2 border ${formTouched && !nuevoCanal.trim() ? "border-red-400 bg-red-50" : "border-gray-300"}`}
                            placeholder="Ej: Mercado Libre"
                            value={nuevoCanal}
                            onChange={(e) => setNuevoCanal(e.target.value)}
                            autoFocus
                        />
                        {formTouched && !nuevoCanal.trim() && <span className="text-xs text-red-500 mt-1">Campo obligatorio</span>}
                    </label>

                    <label className="block">
                        <span className="text-gray-700 text-sm font-bold">Canal Base (Opcional)</span>
                        <select
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring focus:ring-blue-200 p-2 border"
                            value={canalBaseId?.toString() || ""}
                            onChange={(e) => {
                                const val = e.target.value;
                                setCanalBaseId(val ? Number(val) : null);
                            }}
                        >
                            <option value="">-- Sin Canal Base --</option>
                            {canales.map((c) => (
                                <option key={c.id} value={c.id}>
                                    {c.nombre}
                                </option>
                            ))}
                        </select>
                        <p className="text-xs text-gray-400 mt-1">
                            Si seleccionás un canal base, el precio se calculará sobre este.
                        </p>
                    </label>

                    <label className="block">
                        <span className="text-gray-700 text-sm font-bold">Copiar conceptos de (Opcional)</span>
                        <select
                            className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring focus:ring-blue-200 p-2 border"
                            value={copiarDeCanalId?.toString() || ""}
                            onChange={(e) => {
                                const val = e.target.value;
                                setCopiarDeCanalId(val ? Number(val) : null);
                            }}
                        >
                            <option value="">-- Empezar vacío --</option>
                            {canales.map((c) => (
                                <option key={c.id} value={c.id}>
                                    {c.nombre}
                                </option>
                            ))}
                        </select>
                        <p className="text-xs text-gray-400 mt-1">
                            Clona todos los conceptos y reglas del canal elegido al canal nuevo.
                            Después podés ajustar valores sin afectar al original.
                        </p>
                    </label>
                </div>
            </Modal>

            <CanalConceptosModal
                isOpen={conceptosCanal !== null}
                onClose={() => setConceptosCanal(null)}
                canalId={conceptosCanal?.id ?? 0}
                canalNombre={conceptosCanal?.nombre ?? ""}
                canalBaseId={conceptosCanal?.canalBaseId}
            />
        </main>
    );
}

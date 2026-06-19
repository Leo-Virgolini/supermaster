"use client";

import { getErrorMessage } from "@/lib/errors";
import dynamic from "next/dynamic";
import { useRouter, useSearchParams } from "next/navigation";
import { useState, useMemo } from "react";
import { notificar } from "../utils/notificar";
import { CurrencyDollarIcon, CheckIcon, XMarkIcon, ArrowPathIcon } from "@heroicons/react/24/outline";
import { confirmDialog } from "../utils/confirmDialog";
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import SearchInput from "../components/SearchInput/SearchInput";
import Button from "../components/Button/Button";
import CreateButton from "../components/Button/CreateButton";
import DeleteButton from "../components/Button/DeleteButton";
import Modal from "../components/Modal/Modal";
import { useAuth } from "../context/AuthContext";
import { useProcesoActivo } from "../context/ProcesoActivoContext";
import { useMlas } from "./useMlas";
import { getColumns } from "./columns";
import { type MlaDTO, getMlasAPI, calcularCostoEnvioMlaAPI, calcularCostoVentaMlaAPI, getProductosPorMlaAPI, type ProductoResumenDTO } from "./mlasService";
import { type SortingState } from "@tanstack/react-table";

const OperacionPanel = dynamic(
    () => import("../components/OperacionPanel/OperacionPanel").then((mod) => mod.OperacionPanel),
    { loading: () => null }
);

export default function MlasPage() {
    const router = useRouter();
    const searchParams = useSearchParams();
    const { hasPermiso } = useAuth();
    const { tieneConflicto } = useProcesoActivo();
    const canEdit = hasPermiso("MLAS_EDITAR");
    // Estados de tabla
    const [pageIndex, setPageIndex] = useState(0);
    const [pageSize, setPageSize] = useState(() => getInitialPageSize("mlas"));
    const [filters, setFilters] = useState<any>(() => ({ search: searchParams.get("search") ?? "" }));
    const [sorting, setSorting] = useState<SortingState>([{ id: "id", desc: true }]);
    const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});

    // Modal crear
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [formTouched, setFormTouched] = useState(false);
    const [mlaNombre, setMlaNombre] = useState("");
    const [mlaCodigo, setMlaCodigo] = useState("");
    const [mlaPrecio, setMlaPrecio] = useState(0);
    const [topePromocion, setTopePromocion] = useState(0);

    // Loading individual por fila: { [mlaId]: { envio: bool, comision: bool } }
    const [calcLoading, setCalcLoading] = useState<Record<number, { envio: boolean; comision: boolean }>>({});

    // Masivo
    const [envioMasivoEnProceso, setEnvioMasivoEnProceso] = useState(false);
    const [comisionMasivaEnProceso, setComisionMasivaEnProceso] = useState(false);
    const [showMasivo, setShowMasivo] = useState(false);

    // Modal SKUs
    const [skusModalOpen, setSkusModalOpen] = useState(false);
    const [skusMla, setSkusMla] = useState<MlaDTO | null>(null);
    const [skusList, setSkusList] = useState<ProductoResumenDTO[]>([]);
    const [skusLoading, setSkusLoading] = useState(false);

    const { mlas, totalRecords, isLoading, createMla, deleteMla, updateMla, getMlas, refreshMlaLocal } = useMlas(pageIndex, pageSize, filters, sorting);

    const triggerRefetch = () => getMlas(true);
    const pageCount = totalRecords > 0 ? Math.ceil(totalRecords / pageSize) : 1;

    const handleExportAll = async () => {
        const sortParam = sorting.length > 0 ? sorting[0].id + "," + (sorting[0].desc ? "desc" : "asc") : "id,asc";
        const res = await getMlasAPI(0, 99999, filters, sortParam);
        return res.content;
    };
    const selectedIds = Object.keys(rowSelection).map(Number);

    // --- Cálculos individuales ---

    const handleCalcEnvio = async (mla: MlaDTO) => {
        if (!mla.mla) return;
        const conflicto = tieneConflicto("costo-envio");
        if (conflicto) {
            notificar.warning(`No se puede calcular envío: hay otro proceso ML en curso (${conflicto.descripcion})`);
            return;
        }
        setCalcLoading((prev) => ({ ...prev, [mla.id]: { ...(prev[mla.id] ?? { envio: false, comision: false }), envio: true } }));
        try {
            const res = await calcularCostoEnvioMlaAPI(mla.mla);
            const conIva = Number(res.costoEnvioConIva ?? 0);
            if (conIva > 0) {
                notificar.success(`Envío "${mla.mla}": $${conIva.toLocaleString("es-AR")} c/IVA (s/IVA: $${Number(res.costoEnvioSinIva ?? 0).toLocaleString("es-AR")})`);
            } else {
                notificar.error(`No se pudo calcular envío para "${mla.mla}": ${res.mensaje ?? "motivo desconocido"}`);
            }
            refreshMlaLocal(mla.id);
        } catch (e: unknown) {
            notificar.error("Error al calcular envío: " + getErrorMessage(e));
        } finally {
            setCalcLoading((prev) => ({ ...prev, [mla.id]: { ...(prev[mla.id] ?? { envio: false, comision: false }), envio: false } }));
        }
    };

    const handleCalcComision = async (mla: MlaDTO) => {
        if (!mla.mla) return;
        const conflicto = tieneConflicto("costo-venta");
        if (conflicto) {
            notificar.warning(`No se puede calcular comisión: hay otro proceso ML en curso (${conflicto.descripcion})`);
            return;
        }
        setCalcLoading((prev) => ({ ...prev, [mla.id]: { ...(prev[mla.id] ?? { envio: false, comision: false }), comision: true } }));
        try {
            const res = await calcularCostoVentaMlaAPI(mla.mla);
            notificar.success(`Comisión "${mla.mla}": ${res.porcentajeTotal}% (${res.listingTypeName})`);
            refreshMlaLocal(mla.id);
        } catch (e: unknown) {
            notificar.error("Error al calcular comisión: " + getErrorMessage(e));
        } finally {
            setCalcLoading((prev) => ({ ...prev, [mla.id]: { ...(prev[mla.id] ?? { envio: false, comision: false }), comision: false } }));
        }
    };

    const handleVerSkus = async (mla: MlaDTO) => {
        setSkusMla(mla);
        setSkusList([]);
        setSkusModalOpen(true);
        setSkusLoading(true);
        try {
            const data = await getProductosPorMlaAPI(mla.id);
            setSkusList(data);
        } catch (e: unknown) {
            notificar.error("Error al cargar SKUs: " + getErrorMessage(e));
        } finally {
            setSkusLoading(false);
        }
    };

    const handleIrAProducto = (producto: ProductoResumenDTO) => {
        setSkusModalOpen(false);
        router.push(`/productos?search=${encodeURIComponent(producto.sku)}`);
    };

    const columns = useMemo(
        () => getColumns(handleCalcEnvio, handleCalcComision, calcLoading, handleVerSkus, canEdit),
        // eslint-disable-next-line react-hooks/exhaustive-deps
        [calcLoading, canEdit]
    );

    // --- CRUD ---

    const handleCreate = async () => {
        setFormTouched(true);
        if (!mlaNombre.trim()) return;
        try {
            setIsSaving(true);
            await createMla({ mla: mlaNombre, mlau: mlaCodigo, precioEnvio: mlaPrecio, topePromocion });
            setMlaNombre(""); setMlaCodigo(""); setMlaPrecio(0); setTopePromocion(0); setFormTouched(false);
            setIsModalOpen(false);
        } catch (error: any) {
            notificar.error("Error al crear: " + error.message);
        } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = async () => {
        const nombres = selectedIds.map(i => mlas.find(m => m.id === i)?.mla).filter(Boolean);
        const detalle = nombres.length <= 3 ? nombres.join(", ") : `${nombres.slice(0, 3).join(", ")} y ${nombres.length - 3} más`;
        if (!(await confirmDialog({ title: "Eliminar MLAs", message: `¿Eliminar ${selectedIds.length === 1 ? `"${detalle}"` : `${selectedIds.length} MLAs (${detalle})`}?`, confirmText: "Eliminar", variant: "danger" }))) return;
        try {
            await deleteMla(selectedIds);
            setRowSelection({});
        } catch (e) { /* hook already toasts */ }
    };

    const handleUpdate = async (rowIndex: number, columnId: string, value: unknown) => {
        const itemOriginal = mlas[rowIndex];
        try {
            await updateMla(itemOriginal.id, { [columnId]: value });
        } catch (e) {
            notificar.error((e as any)?.message || "Error al actualizar la celda");
        }
    };

    const handleGlobalSearch = (valor: string) => {
        setFilters((prev: any) => ({ ...prev, search: valor }));
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
        <main className="px-4 py-2 bg-gray-50 dark:bg-slate-900 min-h-0 flex flex-col overflow-hidden">
            <div className="flex justify-between items-center mb-2">
                <div>
                    <h1 className="text-3xl font-bold text-gray-800 dark:text-slate-100 flex items-center gap-2">
                        <CurrencyDollarIcon className="w-8 h-8 text-gray-600 dark:text-slate-400" />
                        MLAs (MercadoLibre)
                    </h1>
                </div>
                <div className="flex gap-2 items-center">
                    {canEdit && selectedIds.length > 0 && (
                        <DeleteButton onClick={handleDelete}>
                            Borrar ({selectedIds.length})
                        </DeleteButton>
                    )}
                    <button
                        type="button"
                        onClick={() => setShowMasivo(!showMasivo)}
                        disabled={!canEdit}
                        title={!canEdit
                            ? "No tenés permisos para recalcular"
                            : showMasivo
                                ? "Cerrar panel de recálculo masivo"
                                : "Recalcular envíos y comisiones de todos los MLAs"}
                        className="group inline-flex items-center gap-2 rounded-lg bg-gradient-to-b from-blue-500 to-blue-600 px-4 py-2 text-sm font-semibold text-white shadow-sm ring-1 ring-blue-700/10 transition hover:shadow-md hover:from-blue-500 hover:to-blue-700 active:scale-[0.98] disabled:cursor-not-allowed disabled:bg-none disabled:bg-slate-200 disabled:text-slate-500 disabled:ring-slate-300 disabled:shadow-none disabled:hover:shadow-none disabled:active:scale-100 dark:disabled:bg-slate-700 dark:disabled:text-slate-400 dark:disabled:ring-slate-600"
                    >
                        <ArrowPathIcon className={`w-4 h-4 transition-transform group-hover:rotate-180 group-disabled:rotate-0 ${showMasivo ? "rotate-180" : ""}`} />
                        <span>{showMasivo ? "Ocultar Masivo" : "Recálculo Masivo"}</span>
                    </button>
                    <CreateButton onClick={() => setIsModalOpen(true)} disabled={!canEdit}>
                        Crear MLA
                    </CreateButton>
                </div>
            </div>

            {showMasivo && (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-3">
                    <OperacionPanel
                        titulo="Actualizar Costos de Envío"
                        descripcion="Recalcula los costos de envío de todos los MLAs en Mercado Libre."
                        endpointIniciar="/api/ml/costo-envio"
                        endpointEstado="/api/ml/costo-envio/estado"
                        endpointCancelar="/api/ml/costo-envio/cancelar"
                        endpointResultado="/api/ml/costo-envio/resultado"
                        onRunningChange={setEnvioMasivoEnProceso}
                        onComplete={triggerRefetch}
                        disabled={comisionMasivaEnProceso}
                        disabledReason="Hay una actualización de comisiones en curso. Esperá a que termine."
                        procesoId="costo-envio"
                    />
                    <OperacionPanel
                        titulo="Actualizar Comisiones"
                        descripcion="Recalcula las comisiones de Mercado Libre por categoría."
                        endpointIniciar="/api/ml/costo-venta"
                        endpointEstado="/api/ml/costo-venta/estado"
                        endpointCancelar="/api/ml/costo-venta/cancelar"
                        endpointResultado="/api/ml/costo-venta/resultado"
                        onRunningChange={setComisionMasivaEnProceso}
                        onComplete={triggerRefetch}
                        disabled={envioMasivoEnProceso}
                        disabledReason="Hay una actualización de envíos en curso. Esperá a que termine."
                        procesoId="costo-venta"
                    />
                </div>
            )}

            <div className="flex-1 min-h-0 bg-white dark:bg-slate-800 dark:border-slate-700 rounded-lg shadow border overflow-hidden flex flex-col">
                <Table
                    searchSlot={<SearchInput placeholder="Buscar MLA por código o nombre..." onSearch={(val) => { if (val !== filters.search) handleGlobalSearch(val); }} initialValue={filters.search} className="w-[26rem] max-w-full" />}
                    tableId="mlas"
                    data={mlas}
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
                    onColumnFilterChange={() => { }}
                    hasFiltersActive={hasActiveFilters}
                    onClearAllFilters={clearAllFilters}
                    getActiveFilter={() => undefined}
                    onExportAll={handleExportAll}
                    exportFilename="mlas"
                />
            </div>

            {/* Modal SKUs */}
            <Modal
                isOpen={skusModalOpen}
                onClose={() => setSkusModalOpen(false)}
                title={skusMla ? `SKUs de "${skusMla.mla}" ${skusLoading ? "" : `(${skusList.length})`}` : "SKUs"}
                footer={
                    <Button variant="light" onClick={() => setSkusModalOpen(false)}>
                        <XMarkIcon className="w-4 h-4" /> Cerrar
                    </Button>
                }
            >
                {skusLoading ? (
                    <div className="flex justify-center py-8 text-gray-400 dark:text-slate-500 text-sm">Cargando...</div>
                ) : skusList.length === 0 ? (
                    <div className="flex justify-center py-8 text-gray-400 dark:text-slate-500 text-sm">No hay productos asociados a este MLA.</div>
                ) : (
                    <div className="overflow-auto max-h-96">
                        <table className="w-full text-sm border-collapse">
                            <thead className="bg-gray-100 dark:bg-slate-800 text-gray-700 dark:text-slate-200 text-center font-semibold sticky top-0">
                                <tr>
                                    <th className="p-2 px-3 text-left">SKU</th>
                                    <th className="p-2 px-3 text-left">Descripción</th>
                                    <th className="p-2 px-3 text-right">Costo</th>
                                    <th className="p-2 px-3 text-center">Activo</th>
                                    <th className="p-2 px-3 text-center">Acción</th>
                                </tr>
                            </thead>
                            <tbody>
                                {skusList.map((p) => (
                                    <tr key={p.id} className="border-t border-gray-100 dark:border-slate-700 even:bg-gray-50/50 dark:even:bg-slate-800/40 hover:bg-blue-50 dark:hover:bg-blue-900/15 transition-colors">
                                        <td className="p-2 px-3 font-mono font-medium text-gray-800 dark:text-slate-100">{p.sku}</td>
                                        <td className="p-2 px-3 text-gray-700 dark:text-slate-300">{p.descripcion}</td>
                                        <td className="p-2 px-3 text-right text-gray-700 dark:text-slate-300">
                                            {p.costo != null ? `$${p.costo.toLocaleString("es-AR")}` : "-"}
                                        </td>
                                        <td className="p-2 px-3 text-center">
                                            <span className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${p.activo ? "bg-green-100 text-green-700 dark:bg-emerald-500/15 dark:text-emerald-200" : "bg-gray-100 text-gray-500 dark:bg-slate-700 dark:text-slate-300"}`}>
                                                {p.activo ? "Sí" : "No"}
                                            </span>
                                        </td>
                                        <td className="p-2 px-3 text-center">
                                            <button
                                                type="button"
                                                onClick={() => handleIrAProducto(p)}
                                                className="inline-flex items-center rounded-lg border border-blue-200 bg-blue-50 px-2.5 py-1 text-xs font-semibold text-blue-700 transition hover:bg-blue-100 dark:border-blue-800 dark:bg-blue-500/10 dark:text-blue-200 dark:hover:bg-blue-500/20"
                                            >
                                                Ir al producto
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </Modal>

            <Modal
                isOpen={isModalOpen}
                onClose={() => { setIsModalOpen(false); setFormTouched(false); }}
                title="Nuevo MLA"
                footer={
                    <>
                        <Button variant="light" onClick={() => { setIsModalOpen(false); setFormTouched(false); }}>
                            <XMarkIcon className="w-4 h-4" /> Cancelar
                        </Button>
                        <Button variant="dark" onClick={handleCreate} disabled={isSaving}>
                            <CheckIcon className="w-4 h-4" /> {isSaving ? "Creando MLA..." : "Crear MLA"}
                        </Button>
                    </>
                }
            >
                <div className="flex flex-col gap-4">
                    <label className="block">
                        <span className="text-sm font-bold text-gray-700">Nombre MLA <span className="text-red-500">*</span></span>
                        <input type="text" className={`mt-1 block w-full border rounded p-2 ${formTouched && !mlaNombre.trim() ? "border-red-400 bg-red-50" : ""}`} placeholder="Ej: Clásica" value={mlaNombre} onChange={(e) => setMlaNombre(e.target.value)} />
                        {formTouched && !mlaNombre.trim() && <span className="text-xs text-red-500 mt-1">Campo obligatorio</span>}
                    </label>
                    <label className="block">
                        <span className="text-sm font-bold text-gray-700">Código MLAU</span>
                        <input type="text" className="mt-1 block w-full border rounded p-2" placeholder="Ej: MLAU1234" value={mlaCodigo} onChange={(e) => setMlaCodigo(e.target.value)} />
                    </label>
                    <label className="block">
                        <span className="text-sm font-bold text-gray-700">Precio de Envío ($)</span>
                        <input type="number" min="0" className="mt-1 block w-full border rounded p-2" placeholder="0" value={mlaPrecio} onChange={(e) => setMlaPrecio(Number(e.target.value))} />
                    </label>
                    <label className="block">
                        <span className="text-sm font-bold text-gray-700">Tope Promoción</span>
                        <input type="number" min="0" className="mt-1 block w-full border rounded p-2" placeholder="0" value={topePromocion} onChange={(e) => setTopePromocion(Number(e.target.value))} />
                    </label>
                </div>
            </Modal>
        </main>
    );
}

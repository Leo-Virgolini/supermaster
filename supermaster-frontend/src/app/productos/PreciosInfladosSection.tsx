"use client";

import { getErrorMessage } from "@/lib/errors";
import { useState, useEffect, useCallback } from "react";
import { PlusIcon, PencilSquareIcon, TrashIcon, XMarkIcon, CheckIcon } from "@heroicons/react/24/outline";
import Button from "../components/Button/Button";
import { confirmDialog } from "../utils/confirmDialog";
import { formatFechaAR } from "../utils/formatDate";
import {
    getProductoPreciosInfladosAPI, asignarPrecioInfladoAPI, actualizarPrecioInfladoAPI, quitarPrecioInfladoAPI,
    getAllPreciosInfladosAPI, getAllCanalesAPI,
    ProductoCanalPrecioInfladoDTO,
} from "./productoSubRecursosService";

const inputCls = "w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100 dark:focus:ring-blue-500/20";
const labelCls = "mb-1 block text-xs font-semibold text-slate-600 dark:text-slate-300";

type FormState = {
    canalId: number | "";
    precioInfladoId: number | "";
    fechaDesde: string;
    fechaHasta: string;
    observaciones: string;
    modo: "nuevo" | "editar";
};

// Gestión de precios inflados por canal de un producto.
// La tabla es el centro: cada fila se edita o quita; el formulario de
// alta/edición aparece bajo demanda. POST para asignar, PUT para cambiar.
export function PreciosInfladosSection({ productoId }: { productoId: number }) {
    const [canales, setCanales] = useState<{ id: number; nombre: string }[]>([]);
    const [preciosInflados, setPreciosInflados] = useState<{ id: number; nombre: string }[]>([]);
    const [asignaciones, setAsignaciones] = useState<ProductoCanalPrecioInfladoDTO[]>([]);
    const [isLoadingInit, setIsLoadingInit] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [form, setForm] = useState<FormState | null>(null);

    const recargarAsignaciones = useCallback(() => {
        return getProductoPreciosInfladosAPI(productoId)
            .then(setAsignaciones)
            .catch((e) => setError(getErrorMessage(e)));
    }, [productoId]);

    useEffect(() => {
        setIsLoadingInit(true);
        Promise.all([getAllCanalesAPI(), getAllPreciosInfladosAPI(), getProductoPreciosInfladosAPI(productoId)])
            .then(([cans, precios, asigs]) => { setCanales(cans); setPreciosInflados(precios); setAsignaciones(asigs); })
            .catch((e) => setError(getErrorMessage(e)))
            .finally(() => setIsLoadingInit(false));
    }, [productoId]);

    const nombreCanal = (canalId: number) => canales.find(c => c.id === canalId)?.nombre ?? `Canal ${canalId}`;

    // Canales sin asignación (disponibles para una nueva).
    const canalesLibres = canales.filter(c => !asignaciones.some(a => a.canalId === c.id));

    const abrirNuevo = () => {
        setError(null);
        setForm({ canalId: "", precioInfladoId: "", fechaDesde: "", fechaHasta: "", observaciones: "", modo: "nuevo" });
    };

    const abrirEditar = (a: ProductoCanalPrecioInfladoDTO) => {
        setError(null);
        setForm({
            canalId: a.canalId,
            precioInfladoId: a.precioInflado.id,
            fechaDesde: a.fechaDesde ?? "",
            fechaHasta: a.fechaHasta ?? "",
            observaciones: a.observaciones ?? "",
            modo: "editar",
        });
    };

    const guardar = async () => {
        if (!form || !form.canalId || !form.precioInfladoId) return;
        setIsSaving(true);
        setError(null);
        try {
            const extra = { fechaDesde: form.fechaDesde || null, fechaHasta: form.fechaHasta || null, observaciones: form.observaciones || null };
            if (form.modo === "nuevo") {
                await asignarPrecioInfladoAPI(productoId, Number(form.canalId), Number(form.precioInfladoId), extra);
            } else {
                await actualizarPrecioInfladoAPI(productoId, Number(form.canalId), { precioInfladoId: Number(form.precioInfladoId), ...extra });
            }
            await recargarAsignaciones();
            setForm(null);
        } catch (e: unknown) {
            setError(getErrorMessage(e));
        } finally {
            setIsSaving(false);
        }
    };

    const quitar = async (a: ProductoCanalPrecioInfladoDTO) => {
        if (!(await confirmDialog({ title: "Quitar precio inflado", message: `¿Quitar el precio inflado de "${nombreCanal(a.canalId)}"?`, confirmText: "Quitar", variant: "danger" }))) return;
        setIsSaving(true);
        setError(null);
        try {
            await quitarPrecioInfladoAPI(productoId, a.canalId);
            if (form?.canalId === a.canalId) setForm(null);
            await recargarAsignaciones();
        } catch (e: unknown) {
            setError(getErrorMessage(e));
        } finally {
            setIsSaving(false);
        }
    };

    if (isLoadingInit) return <p className="py-6 text-center text-sm text-slate-400">Cargando...</p>;

    return (
        <div className="flex flex-col gap-4">
            {error && <div className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/30 dark:text-red-300">{error}</div>}

            {/* Tabla de asignaciones actuales */}
            <div className="overflow-hidden rounded-2xl border border-slate-200 dark:border-slate-700">
                <div className="flex items-center justify-between border-b border-slate-200 bg-slate-50 px-4 py-2 dark:border-slate-700 dark:bg-slate-800">
                    <span className="text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">Asignaciones por canal</span>
                    <Button variant="dark" onClick={abrirNuevo} disabled={canalesLibres.length === 0 || isSaving}>
                        <PlusIcon className="h-4 w-4" /> Agregar
                    </Button>
                </div>
                {asignaciones.length === 0 ? (
                    <p className="px-4 py-4 text-sm text-slate-400">Este producto no tiene precios inflados asignados en ningún canal.</p>
                ) : (
                    <table className="w-full text-sm">
                        <thead className="text-left text-xs text-slate-500 dark:text-slate-400">
                            <tr className="border-b border-slate-100 dark:border-slate-800">
                                <th className="px-4 py-2 font-semibold">Canal</th>
                                <th className="px-4 py-2 font-semibold">Precio inflado</th>
                                <th className="px-4 py-2 font-semibold">Vigencia</th>
                                <th className="px-4 py-2 font-semibold">Estado</th>
                                <th className="px-4 py-2 text-right font-semibold">Acciones</th>
                            </tr>
                        </thead>
                        <tbody>
                            {asignaciones.map((a) => (
                                <tr key={a.id} className="border-t border-slate-100 dark:border-slate-800">
                                    <td className="px-4 py-2 font-medium text-slate-700 dark:text-slate-200">{nombreCanal(a.canalId)}</td>
                                    <td className="px-4 py-2 text-slate-700 dark:text-slate-200">
                                        <span className="font-semibold">{a.precioInflado.codigo}</span>
                                        <span className="ml-2 text-xs text-slate-500 dark:text-slate-400">({a.precioInflado.tipo} = {a.precioInflado.valor})</span>
                                    </td>
                                    <td className="px-4 py-2 text-xs text-slate-500 dark:text-slate-400">
                                        {a.fechaDesde || a.fechaHasta
                                            ? `${a.fechaDesde ? formatFechaAR(a.fechaDesde) : "…"} → ${a.fechaHasta ? formatFechaAR(a.fechaHasta) : "…"}`
                                            : "Sin límite"}
                                    </td>
                                    <td className="px-4 py-2">
                                        <span className={`text-xs font-medium ${a.activo ? "text-green-600 dark:text-green-400" : "text-slate-400"}`}>{a.activo ? "Activo" : "Inactivo"}</span>
                                    </td>
                                    <td className="px-4 py-2">
                                        <div className="flex justify-end gap-1">
                                            <button type="button" onClick={() => abrirEditar(a)} title="Editar" className="rounded-lg border border-slate-200 p-1.5 text-slate-600 transition hover:bg-slate-100 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800">
                                                <PencilSquareIcon className="h-4 w-4" />
                                            </button>
                                            <button type="button" onClick={() => quitar(a)} disabled={isSaving} title="Quitar" className="rounded-lg border border-red-200 p-1.5 text-red-600 transition hover:bg-red-50 disabled:opacity-50 dark:border-red-900 dark:text-red-400 dark:hover:bg-red-950/30">
                                                <TrashIcon className="h-4 w-4" />
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                )}
            </div>

            {canalesLibres.length === 0 && !form && (
                <p className="text-xs text-slate-400">Todos los canales ya tienen un precio inflado asignado. Editá o quitá los existentes.</p>
            )}

            {/* Formulario de alta / edición */}
            {form && (
                <div className="rounded-2xl border border-blue-200 bg-blue-50/40 p-4 dark:border-blue-900 dark:bg-blue-950/20">
                    <div className="mb-3 flex items-center justify-between">
                        <h4 className="text-sm font-semibold text-slate-700 dark:text-slate-200">
                            {form.modo === "nuevo" ? "Nueva asignación" : `Editar asignación · ${nombreCanal(Number(form.canalId))}`}
                        </h4>
                        <button type="button" onClick={() => setForm(null)} title="Cerrar" className="text-slate-400 transition hover:text-slate-600 dark:hover:text-slate-200">
                            <XMarkIcon className="h-5 w-5" />
                        </button>
                    </div>

                    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                        {form.modo === "nuevo" && (
                            <label className="block">
                                <span className={labelCls}>Canal</span>
                                <select className={inputCls} value={form.canalId} onChange={(e) => setForm({ ...form, canalId: e.target.value ? Number(e.target.value) : "" })}>
                                    <option value="">-- Seleccionar canal --</option>
                                    {canalesLibres.map((c) => (<option key={c.id} value={c.id}>{c.nombre}</option>))}
                                </select>
                            </label>
                        )}
                        <label className="block">
                            <span className={labelCls}>Precio inflado</span>
                            <select className={inputCls} value={form.precioInfladoId} onChange={(e) => setForm({ ...form, precioInfladoId: e.target.value ? Number(e.target.value) : "" })}>
                                <option value="">-- Seleccionar --</option>
                                {preciosInflados.map((p) => (<option key={p.id} value={p.id}>{p.nombre}</option>))}
                            </select>
                        </label>
                        <label className="block">
                            <span className={labelCls}>Fecha Desde</span>
                            <input type="date" className={inputCls} value={form.fechaDesde} onChange={(e) => setForm({ ...form, fechaDesde: e.target.value })} />
                        </label>
                        <label className="block">
                            <span className={labelCls}>Fecha Hasta</span>
                            <input type="date" className={inputCls} value={form.fechaHasta} onChange={(e) => setForm({ ...form, fechaHasta: e.target.value })} />
                        </label>
                        <label className="block md:col-span-2">
                            <span className={labelCls}>Observaciones</span>
                            <input type="text" className={inputCls} value={form.observaciones} onChange={(e) => setForm({ ...form, observaciones: e.target.value })} placeholder="Observaciones..." />
                        </label>
                    </div>

                    <div className="mt-3 flex justify-end gap-2">
                        <Button variant="light" onClick={() => setForm(null)}><XMarkIcon className="h-4 w-4" /> Cancelar</Button>
                        <Button variant="dark" onClick={guardar} disabled={!form.canalId || !form.precioInfladoId || isSaving}>
                            <CheckIcon className="h-4 w-4" /> {isSaving ? "Guardando..." : (form.modo === "nuevo" ? "Asignar" : "Guardar")}
                        </Button>
                    </div>
                </div>
            )}
        </div>
    );
}

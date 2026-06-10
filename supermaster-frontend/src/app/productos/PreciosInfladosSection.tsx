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

/** Asignación pendiente (modo alta): se acumula en memoria y se asigna tras crear el producto. */
export type PrecioInfladoDraft = {
    canalId: number;
    precioInfladoId: number;
    fechaDesde: string | null;
    fechaHasta: string | null;
    observaciones: string | null;
};

type FormState = {
    canalId: number | "";
    precioInfladoId: number | "";
    fechaDesde: string;
    fechaHasta: string;
    observaciones: string;
    modo: "nuevo" | "editar";
};

// Fila unificada que muestra la tabla, sea modo live (edición) o diferido (alta).
type Row = {
    canalId: number;
    precioInfladoId: number;
    precioLabel: string;
    fechaDesde: string | null;
    fechaHasta: string | null;
    observaciones: string | null;
    estado: "Activo" | "Inactivo" | "Pendiente";
};

type Props = {
    /** Presente => modo live (edición): asigna/quita contra el backend. */
    productoId?: number | null;
    /** Modo diferido (alta): la lista se controla desde el padre y se asigna al crear. */
    value?: PrecioInfladoDraft[];
    onChange?: (v: PrecioInfladoDraft[]) => void;
};

export function PreciosInfladosSection({ productoId, value, onChange }: Props) {
    const live = !!productoId;

    const [canales, setCanales] = useState<{ id: number; nombre: string }[]>([]);
    const [preciosInflados, setPreciosInflados] = useState<{ id: number; nombre: string }[]>([]);
    const [asignacionesLive, setAsignacionesLive] = useState<ProductoCanalPrecioInfladoDTO[]>([]);
    const [isLoadingInit, setIsLoadingInit] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [form, setForm] = useState<FormState | null>(null);

    const recargarLive = useCallback(() => {
        if (!productoId) return Promise.resolve();
        return getProductoPreciosInfladosAPI(productoId)
            .then(setAsignacionesLive)
            .catch((e) => setError(getErrorMessage(e)));
    }, [productoId]);

    useEffect(() => {
        setIsLoadingInit(true);
        const base: Promise<unknown>[] = [getAllCanalesAPI().then(setCanales), getAllPreciosInfladosAPI().then(setPreciosInflados)];
        if (productoId) base.push(getProductoPreciosInfladosAPI(productoId).then(setAsignacionesLive));
        Promise.all(base)
            .catch((e) => setError(getErrorMessage(e)))
            .finally(() => setIsLoadingInit(false));
    }, [productoId]);

    const nombreCanal = (canalId: number) => canales.find(c => c.id === canalId)?.nombre ?? `Canal ${canalId}`;
    const nombrePrecio = (id: number) => preciosInflados.find(p => p.id === id)?.nombre ?? `#${id}`;

    // Filas a mostrar, normalizadas según el modo.
    const rows: Row[] = live
        ? asignacionesLive.map(a => ({
            canalId: a.canalId,
            precioInfladoId: a.precioInflado.id,
            precioLabel: `${a.precioInflado.codigo} (${a.precioInflado.tipo} = ${a.precioInflado.valor})`,
            fechaDesde: a.fechaDesde,
            fechaHasta: a.fechaHasta,
            observaciones: a.observaciones,
            estado: a.activo ? "Activo" : "Inactivo",
        }))
        : (value ?? []).map(d => ({
            canalId: d.canalId,
            precioInfladoId: d.precioInfladoId,
            precioLabel: nombrePrecio(d.precioInfladoId),
            fechaDesde: d.fechaDesde,
            fechaHasta: d.fechaHasta,
            observaciones: d.observaciones,
            estado: "Pendiente",
        }));

    const canalesLibres = canales.filter(c => !rows.some(r => r.canalId === c.id));

    const abrirNuevo = () => {
        setError(null);
        setForm({ canalId: "", precioInfladoId: "", fechaDesde: "", fechaHasta: "", observaciones: "", modo: "nuevo" });
    };

    const abrirEditar = (r: Row) => {
        setError(null);
        setForm({
            canalId: r.canalId,
            precioInfladoId: r.precioInfladoId,
            fechaDesde: r.fechaDesde ?? "",
            fechaHasta: r.fechaHasta ?? "",
            observaciones: r.observaciones ?? "",
            modo: "editar",
        });
    };

    const guardar = async () => {
        if (!form || !form.canalId || !form.precioInfladoId) return;
        const canalId = Number(form.canalId);
        const precioInfladoId = Number(form.precioInfladoId);
        const fechaDesde = form.fechaDesde || null;
        const fechaHasta = form.fechaHasta || null;
        const observaciones = form.observaciones || null;

        if (!live) {
            // Modo diferido: solo actualizamos la lista en memoria.
            const draft: PrecioInfladoDraft = { canalId, precioInfladoId, fechaDesde, fechaHasta, observaciones };
            const actual = value ?? [];
            onChange?.(form.modo === "nuevo" ? [...actual, draft] : actual.map(d => d.canalId === canalId ? draft : d));
            setForm(null);
            return;
        }

        setIsSaving(true);
        setError(null);
        try {
            if (form.modo === "nuevo") {
                await asignarPrecioInfladoAPI(productoId!, canalId, precioInfladoId, { fechaDesde, fechaHasta, observaciones });
            } else {
                await actualizarPrecioInfladoAPI(productoId!, canalId, { precioInfladoId, fechaDesde, fechaHasta, observaciones });
            }
            await recargarLive();
            setForm(null);
        } catch (e: unknown) {
            setError(getErrorMessage(e));
        } finally {
            setIsSaving(false);
        }
    };

    const quitar = async (r: Row) => {
        if (!live) {
            onChange?.((value ?? []).filter(d => d.canalId !== r.canalId));
            if (form?.canalId === r.canalId) setForm(null);
            return;
        }
        if (!(await confirmDialog({ title: "Quitar precio inflado", message: `¿Quitar el precio inflado de "${nombreCanal(r.canalId)}"?`, confirmText: "Quitar", variant: "danger" }))) return;
        setIsSaving(true);
        setError(null);
        try {
            await quitarPrecioInfladoAPI(productoId!, r.canalId);
            if (form?.canalId === r.canalId) setForm(null);
            await recargarLive();
        } catch (e: unknown) {
            setError(getErrorMessage(e));
        } finally {
            setIsSaving(false);
        }
    };

    if (isLoadingInit) return <p className="py-6 text-center text-sm text-slate-400">Cargando...</p>;

    return (
        <div className="flex flex-col gap-4">
            {!live && (
                <p className="text-xs text-slate-500 dark:text-slate-400">Estas asignaciones se aplicarán al crear el producto.</p>
            )}
            {error && <div className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/30 dark:text-red-300">{error}</div>}

            {/* Tabla de asignaciones */}
            <div className="overflow-hidden rounded-2xl border border-slate-200 dark:border-slate-700">
                <div className="flex items-center justify-between border-b border-slate-200 bg-slate-50 px-4 py-2 dark:border-slate-700 dark:bg-slate-800">
                    <span className="text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">Asignaciones por canal</span>
                    <Button variant="dark" onClick={abrirNuevo} disabled={canalesLibres.length === 0 || isSaving}>
                        <PlusIcon className="h-4 w-4" /> Agregar
                    </Button>
                </div>
                {rows.length === 0 ? (
                    <p className="px-4 py-4 text-sm text-slate-400">Sin precios inflados asignados en ningún canal.</p>
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
                            {rows.map((r) => (
                                <tr key={r.canalId} className="border-t border-slate-100 dark:border-slate-800">
                                    <td className="px-4 py-2 font-medium text-slate-700 dark:text-slate-200">{nombreCanal(r.canalId)}</td>
                                    <td className="px-4 py-2 text-slate-700 dark:text-slate-200">{r.precioLabel}</td>
                                    <td className="px-4 py-2 text-xs text-slate-500 dark:text-slate-400">
                                        {r.fechaDesde || r.fechaHasta
                                            ? `${r.fechaDesde ? formatFechaAR(r.fechaDesde) : "…"} → ${r.fechaHasta ? formatFechaAR(r.fechaHasta) : "…"}`
                                            : "Sin límite"}
                                    </td>
                                    <td className="px-4 py-2">
                                        <span className={`text-xs font-medium ${r.estado === "Activo" ? "text-green-600 dark:text-green-400" : r.estado === "Pendiente" ? "text-amber-600 dark:text-amber-400" : "text-slate-400"}`}>{r.estado}</span>
                                    </td>
                                    <td className="px-4 py-2">
                                        <div className="flex justify-end gap-1">
                                            <button type="button" onClick={() => abrirEditar(r)} title="Editar" className="rounded-lg border border-slate-200 p-1.5 text-slate-600 transition hover:bg-slate-100 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800">
                                                <PencilSquareIcon className="h-4 w-4" />
                                            </button>
                                            <button type="button" onClick={() => quitar(r)} disabled={isSaving} title="Quitar" className="rounded-lg border border-red-200 p-1.5 text-red-600 transition hover:bg-red-50 disabled:opacity-50 dark:border-red-900 dark:text-red-400 dark:hover:bg-red-950/30">
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

            {canalesLibres.length === 0 && !form && rows.length > 0 && (
                <p className="text-xs text-slate-400">Todos los canales ya tienen un precio inflado. Editá o quitá los existentes.</p>
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
                            <CheckIcon className="h-4 w-4" /> {isSaving ? "Guardando..." : (form.modo === "nuevo" ? "Agregar" : "Guardar")}
                        </Button>
                    </div>
                </div>
            )}
        </div>
    );
}

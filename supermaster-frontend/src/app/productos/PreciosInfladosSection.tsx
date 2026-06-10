"use client";

import { getErrorMessage } from "@/lib/errors";
import { useState, useEffect, useCallback } from "react";
import Button from "../components/Button/Button";
import { confirmDialog } from "../utils/confirmDialog";
import { formatFechaAR } from "../utils/formatDate";
import {
    getProductoPrecioInfladoPorCanalAPI, getProductoPreciosInfladosAPI, asignarPrecioInfladoAPI, quitarPrecioInfladoAPI,
    getAllPreciosInfladosAPI, getAllCanalesAPI,
    ProductoCanalPrecioInfladoDTO,
} from "./productoSubRecursosService";

// Gestión de precios inflados por canal de un producto.
// La API es por canal: GET/POST/DELETE /api/productos/{id}/canales/{canalId}/precios-inflados
export function PreciosInfladosSection({ productoId }: { productoId: number }) {
    const [canales, setCanales] = useState<{ id: number; nombre: string }[]>([]);
    const [preciosInflados, setPreciosInflados] = useState<{ id: number; nombre: string }[]>([]);
    const [asignaciones, setAsignaciones] = useState<ProductoCanalPrecioInfladoDTO[]>([]);
    const [selectedCanalId, setSelectedCanalId] = useState<number | "">("");
    const [selectedPrecioId, setSelectedPrecioId] = useState<number | "">("");
    const [asignacionActual, setAsignacionActual] = useState<ProductoCanalPrecioInfladoDTO | null>(null);
    const [fechaDesde, setFechaDesde] = useState("");
    const [fechaHasta, setFechaHasta] = useState("");
    const [observaciones, setObservaciones] = useState("");
    const [isLoadingCanal, setIsLoadingCanal] = useState(false);
    const [isLoadingInit, setIsLoadingInit] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const recargarAsignaciones = useCallback(() => {
        return getProductoPreciosInfladosAPI(productoId)
            .then(setAsignaciones)
            .catch((e) => setError(getErrorMessage(e)));
    }, [productoId]);

    // Carga inicial: listas de canales, precios inflados y asignaciones actuales
    useEffect(() => {
        setIsLoadingInit(true);
        Promise.all([getAllCanalesAPI(), getAllPreciosInfladosAPI(), getProductoPreciosInfladosAPI(productoId)])
            .then(([cans, precios, asigs]) => { setCanales(cans); setPreciosInflados(precios); setAsignaciones(asigs); })
            .catch((e) => setError(getErrorMessage(e)))
            .finally(() => setIsLoadingInit(false));
    }, [productoId]);

    // Cuando cambia el canal seleccionado, busca la asignación actual
    useEffect(() => {
        if (!selectedCanalId) { setAsignacionActual(null); setSelectedPrecioId(""); setFechaDesde(""); setFechaHasta(""); setObservaciones(""); return; }
        setIsLoadingCanal(true);
        setError(null);
        setSelectedPrecioId("");
        setFechaDesde(""); setFechaHasta(""); setObservaciones("");
        getProductoPrecioInfladoPorCanalAPI(productoId, Number(selectedCanalId))
            .then((data) => {
                setAsignacionActual(data);
                if (data) {
                    setSelectedPrecioId(data.precioInflado.id);
                    setFechaDesde(data.fechaDesde ?? "");
                    setFechaHasta(data.fechaHasta ?? "");
                    setObservaciones(data.observaciones ?? "");
                }
            })
            .catch((e) => setError(getErrorMessage(e)))
            .finally(() => setIsLoadingCanal(false));
    }, [selectedCanalId, productoId]);

    const handleAsignar = async () => {
        if (!selectedCanalId || !selectedPrecioId) return;
        setIsSaving(true);
        setError(null);
        try {
            const result = await asignarPrecioInfladoAPI(
                productoId,
                Number(selectedCanalId),
                Number(selectedPrecioId),
                { fechaDesde: fechaDesde || null, fechaHasta: fechaHasta || null, observaciones: observaciones || null },
            );
            setAsignacionActual(result);
            await recargarAsignaciones();
        } catch (e: unknown) {
            setError(getErrorMessage(e));
        } finally {
            setIsSaving(false);
        }
    };

    const handleQuitar = async () => {
        if (!selectedCanalId) return;
        if (!(await confirmDialog({ title: "Eliminar", message: "¿Quitar el precio inflado de este canal?", confirmText: "Eliminar", variant: "danger" }))) return;
        setIsSaving(true);
        setError(null);
        try {
            await quitarPrecioInfladoAPI(productoId, Number(selectedCanalId));
            setAsignacionActual(null);
            setSelectedPrecioId("");
            await recargarAsignaciones();
        } catch (e: unknown) {
            setError(getErrorMessage(e));
        } finally {
            setIsSaving(false);
        }
    };

    if (isLoadingInit) return <p className="text-sm text-gray-400 text-center py-6">Cargando...</p>;

    return (
        <div className="flex flex-col gap-4">
            {error && <div className="p-3 bg-red-100 text-red-700 rounded text-sm">{error}</div>}

            {/* Resumen: precios inflados ya asignados (todos los canales) */}
            <div className="rounded-lg border border-slate-200 dark:border-slate-700">
                <div className="border-b border-slate-200 bg-slate-50 px-3 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300">
                    Asignaciones actuales
                </div>
                {asignaciones.length === 0 ? (
                    <p className="px-3 py-3 text-sm text-gray-400">Este producto no tiene precios inflados asignados en ningún canal.</p>
                ) : (
                    <table className="w-full text-sm">
                        <thead className="text-left text-xs text-slate-500">
                            <tr>
                                <th className="px-3 py-1.5 font-semibold">Canal</th>
                                <th className="px-3 py-1.5 font-semibold">Precio inflado</th>
                                <th className="px-3 py-1.5 font-semibold">Estado</th>
                            </tr>
                        </thead>
                        <tbody>
                            {asignaciones.map((a) => (
                                <tr
                                    key={a.id}
                                    onClick={() => setSelectedCanalId(a.canalId)}
                                    className="cursor-pointer border-t border-slate-100 hover:bg-blue-50/60 dark:border-slate-800 dark:hover:bg-blue-900/20"
                                    title="Click para ver / editar esta asignación"
                                >
                                    <td className="px-3 py-1.5 text-slate-700 dark:text-slate-200">{canales.find(c => c.id === a.canalId)?.nombre ?? `Canal ${a.canalId}`}</td>
                                    <td className="px-3 py-1.5 text-slate-700 dark:text-slate-200">
                                        <span className="font-semibold">{a.precioInflado.codigo}</span>
                                        <span className="ml-2 text-xs text-slate-500">({a.precioInflado.tipo} = {a.precioInflado.valor})</span>
                                    </td>
                                    <td className="px-3 py-1.5">
                                        <span className={`text-xs font-medium ${a.activo ? "text-green-600" : "text-gray-400"}`}>{a.activo ? "Activo" : "Inactivo"}</span>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                )}
            </div>

            <p className="text-xs text-gray-500">Seleccioná un canal para ver o modificar su precio inflado asignado.</p>

            {/* Selector de canal */}
            <label className="block">
                <span className="text-xs font-semibold text-gray-600">Canal</span>
                <select
                    className="w-full border border-gray-300 rounded p-2 text-sm mt-1"
                    value={selectedCanalId}
                    onChange={(e) => setSelectedCanalId(e.target.value ? Number(e.target.value) : "")}
                >
                    <option value="">-- Seleccionar canal --</option>
                    {canales.map((c) => (
                        <option key={c.id} value={c.id}>{c.nombre}</option>
                    ))}
                </select>
            </label>

            {/* Estado actual del canal seleccionado */}
            {selectedCanalId && (
                isLoadingCanal ? (
                    <p className="text-sm text-gray-400 text-center py-2">Buscando asignación...</p>
                ) : (
                    <div className="border border-gray-200 rounded p-3 bg-gray-50">
                        {asignacionActual ? (
                            <div className="flex items-start justify-between gap-3">
                                <div className="flex flex-col gap-0.5">
                                    <p className="text-xs text-gray-500 mb-0.5">Precio inflado actual</p>
                                    <p className="font-semibold text-gray-800">
                                        {asignacionActual.precioInflado.codigo}
                                        <span className="text-xs text-gray-500 ml-2">
                                            ({asignacionActual.precioInflado.tipo} = {asignacionActual.precioInflado.valor})
                                        </span>
                                    </p>
                                    <span className={`text-xs font-medium ${asignacionActual.activo ? "text-green-600" : "text-gray-400"}`}>
                                        {asignacionActual.activo ? "Activo" : "Inactivo"}
                                    </span>
                                    {(asignacionActual.fechaDesde || asignacionActual.fechaHasta) && (
                                        <span className="text-xs text-gray-500">
                                            {asignacionActual.fechaDesde && `Desde: ${formatFechaAR(asignacionActual.fechaDesde)}`}
                                            {asignacionActual.fechaDesde && asignacionActual.fechaHasta && " · "}
                                            {asignacionActual.fechaHasta && `Hasta: ${formatFechaAR(asignacionActual.fechaHasta)}`}
                                        </span>
                                    )}
                                    {asignacionActual.observaciones && (
                                        <span className="text-xs text-gray-500 italic">{asignacionActual.observaciones}</span>
                                    )}
                                </div>
                                <button
                                    onClick={handleQuitar}
                                    disabled={isSaving}
                                    className="text-red-500 hover:text-red-700 text-xs font-medium px-2 py-1 border border-red-200 rounded hover:bg-red-50 transition shrink-0"
                                >
                                    Quitar
                                </button>
                            </div>
                        ) : (
                            <p className="text-sm text-gray-400">Sin precio inflado asignado para este canal.</p>
                        )}
                    </div>
                )
            )}

            {/* Selector de precio inflado + fecha/notas + botón asignar */}
            {selectedCanalId && !isLoadingCanal && (
                <div className="flex flex-col gap-3">
                    <label className="block">
                        <span className="text-xs font-semibold text-gray-600">
                            {asignacionActual ? "Cambiar precio inflado" : "Asignar precio inflado"}
                        </span>
                        <select
                            className="w-full border border-gray-300 rounded p-2 text-sm mt-1"
                            value={selectedPrecioId}
                            onChange={(e) => setSelectedPrecioId(e.target.value ? Number(e.target.value) : "")}
                        >
                            <option value="">-- Seleccionar --</option>
                            {preciosInflados.map((p) => (
                                <option key={p.id} value={p.id}>{p.nombre}</option>
                            ))}
                        </select>
                    </label>
                    <div className="grid grid-cols-2 gap-2">
                        <label className="block">
                            <span className="text-xs font-semibold text-gray-600">Fecha Desde</span>
                            <input type="date" className="w-full border border-gray-300 rounded p-2 text-sm mt-1"
                                value={fechaDesde} onChange={e => setFechaDesde(e.target.value)} />
                        </label>
                        <label className="block">
                            <span className="text-xs font-semibold text-gray-600">Fecha Hasta</span>
                            <input type="date" className="w-full border border-gray-300 rounded p-2 text-sm mt-1"
                                value={fechaHasta} onChange={e => setFechaHasta(e.target.value)} />
                        </label>
                    </div>
                    <label className="block">
                        <span className="text-xs font-semibold text-gray-600">Observaciones</span>
                        <input type="text" className="w-full border border-gray-300 rounded p-2 text-sm mt-1"
                            value={observaciones} onChange={e => setObservaciones(e.target.value)} placeholder="Observaciones..." />
                    </label>
                    <div className="flex justify-end">
                        <Button
                            text={isSaving ? "Guardando..." : asignacionActual ? "Actualizar" : "Asignar"}
                            variant="dark"
                            onClick={handleAsignar}
                            disabled={!selectedPrecioId || isSaving}
                        />
                    </div>
                </div>
            )}
        </div>
    );
}

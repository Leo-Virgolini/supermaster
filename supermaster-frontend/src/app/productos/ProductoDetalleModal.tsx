"use client";

import Link from "next/link";
import { useState, useEffect, useCallback } from "react";
import { ArrowPathIcon } from "@heroicons/react/24/outline";
import Modal from "../components/Modal/Modal";
import Button from "../components/Button/Button";
import {
    getProductoAptosAPI, asignarAptoAPI, quitarAptoAPI, getAllAptosAPI,
    getProductoCatalogosAPI, asignarCatalogoAPI, quitarCatalogoAPI, getAllCatalogosAPI,
    getProductoClientesAPI, asignarClienteAPI, quitarClienteAPI, getAllClientesAPI,
    getProductoPrecioInfladoPorCanalAPI, asignarPrecioInfladoAPI, quitarPrecioInfladoAPI,
    getAllPreciosInfladosAPI, getAllCanalesAPI,
    ProductoCanalPrecioInfladoDTO,
} from "./productoSubRecursosService";
import {
    getProductoMargenAPI, updateProductoMargenAPI, deleteProductoMargenAPI,
    ProductoMargenDTO,
} from "./productoMargenService";
import { confirmDialog } from "../utils/confirmDialog";
import { notificar } from "../utils/notificar";
import { formatFechaAR } from "../utils/formatDate";
import {
    getProductoPreciosAPI,
    calcularPreciosAPI,
} from "../producto-canal-precios/productoCanalPreciosService";
import type { ProductoCanalPrecioDTO } from "../producto-canal-precios/types";
import type { AuditoriaCambioDTO } from "../auditoria/types";
import { getProductoAuditoriaAPI } from "./productosService";

type TabId = "aptos" | "catalogos" | "clientes" | "margen" | "preciosInflados" | "precios" | "historial";

interface Props {
    isOpen: boolean;
    onClose: () => void;
    productoId: number;
    productoSku: string;
}

// ---- Sub-componente genérico para tabs de relaciones ----
interface RelationTabProps {
    productoId: number;
    entityName: string;
    getAssigned: (id: number) => Promise<any[]>;
    assignFn: (prodId: number, entityId: number) => Promise<void>;
    removeFn: (prodId: number, entityId: number) => Promise<void>;
    getAllFn: () => Promise<{ id: number; nombre: string }[]>;
    entityIdKey: string;
}

function RelationTab({ productoId, entityName, getAssigned, assignFn, removeFn, getAllFn, entityIdKey }: RelationTabProps) {
    const [assigned, setAssigned] = useState<number[]>([]);
    const [all, setAll] = useState<{ id: number; nombre: string }[]>([]);
    const [selectedId, setSelectedId] = useState<number | "">("");
    const [isLoading, setIsLoading] = useState(false);
    const [isAdding, setIsAdding] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const load = useCallback(async () => {
        setIsLoading(true);
        setError(null);
        try {
            const [assignedData, allData] = await Promise.all([
                getAssigned(productoId),
                getAllFn(),
            ]);
            setAssigned(assignedData.map((item: any) => item[entityIdKey] as number));
            setAll(allData);
        } catch (e: any) {
            setError(e.message);
        } finally {
            setIsLoading(false);
        }
    }, [productoId]);

    useEffect(() => { load(); }, [load]);

    const handleAgregar = async () => {
        if (!selectedId) return;
        setIsAdding(true);
        setError(null);
        try {
            await assignFn(productoId, Number(selectedId));
            setSelectedId("");
            await load();
        } catch (e: any) {
            setError(e.message);
        } finally {
            setIsAdding(false);
        }
    };

    const handleQuitar = async (entityId: number) => {
        if (!(await confirmDialog({ title: "Eliminar", message: `¿Quitar este ${entityName.toLowerCase()} del producto?`, confirmText: "Eliminar", variant: "danger" }))) return;
        setError(null);
        try {
            await removeFn(productoId, entityId);
            await load();
        } catch (e: any) {
            setError(e.message);
        }
    };

    const assignedSet = new Set(assigned);
    const available = all.filter((item) => !assignedSet.has(item.id));
    const assignedItems = all.filter((item) => assignedSet.has(item.id));

    return (
        <div className="flex flex-col gap-4">
            {error && <div className="p-3 bg-red-100 text-red-700 rounded text-sm">{error}</div>}

            <div className="flex gap-2">
                <select
                    className="flex-1 border border-gray-300 rounded p-2 text-sm"
                    value={selectedId}
                    onChange={(e) => setSelectedId(e.target.value ? Number(e.target.value) : "")}
                    disabled={available.length === 0}
                >
                    <option value="">{available.length === 0 ? `Todos los ${entityName.toLowerCase()} asignados` : `-- Seleccionar ${entityName} --`}</option>
                    {available.map((item) => (
                        <option key={item.id} value={item.id}>{item.nombre}</option>
                    ))}
                </select>
                <Button
                    text={isAdding ? "Agregando..." : "Agregar"}
                    variant="dark"
                    onClick={handleAgregar}
                    disabled={!selectedId || isAdding}
                />
            </div>

            {isLoading ? (
                <p className="text-sm text-gray-400 text-center py-4">Cargando...</p>
            ) : assignedItems.length === 0 ? (
                <p className="text-sm text-gray-400 text-center py-4">Sin {entityName.toLowerCase()} asignados.</p>
            ) : (
                <div className="border border-gray-200 rounded overflow-hidden">
                    <table className="w-full text-sm">
                        <tbody>
                            {assignedItems.map((item, idx) => (
                                <tr key={item.id} className={idx % 2 === 0 ? "bg-white" : "bg-gray-50"}>
                                    <td className="px-3 py-2 text-gray-800">{item.nombre}</td>
                                    <td className="px-3 py-2 text-right">
                                        <button
                                            onClick={() => handleQuitar(item.id)}
                                            className="text-red-500 hover:text-red-700 text-xs font-medium px-2 py-1 rounded hover:bg-red-50 transition"
                                        >
                                            Quitar
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}

// ---- Tab Precios Inflados ----
// La API es por canal: GET/POST/DELETE /api/productos/{id}/canales/{canalId}/precios-inflados
function PreciosInfladosTab({ productoId }: { productoId: number }) {
    const [canales, setCanales] = useState<{ id: number; nombre: string }[]>([]);
    const [preciosInflados, setPreciosInflados] = useState<{ id: number; nombre: string }[]>([]);
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

    // Carga inicial: listas de canales y precios inflados
    useEffect(() => {
        setIsLoadingInit(true);
        Promise.all([getAllCanalesAPI(), getAllPreciosInfladosAPI()])
            .then(([cans, precios]) => { setCanales(cans); setPreciosInflados(precios); })
            .catch((e) => setError(e.message))
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
            .catch((e) => setError(e.message))
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
        } catch (e: any) {
            setError(e.message);
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
        } catch (e: any) {
            setError(e.message);
        } finally {
            setIsSaving(false);
        }
    };

    if (isLoadingInit) return <p className="text-sm text-gray-400 text-center py-6">Cargando...</p>;

    return (
        <div className="flex flex-col gap-4">
            {error && <div className="p-3 bg-red-100 text-red-700 rounded text-sm">{error}</div>}

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

// ---- Tab Margen ----
interface MargenTabProps { productoId: number; }

function MargenTab({ productoId }: MargenTabProps) {
    const [margen, setMargen] = useState<Partial<ProductoMargenDTO>>({});
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [successMsg, setSuccessMsg] = useState<string | null>(null);

    useEffect(() => {
        setIsLoading(true);
        setError(null);
        getProductoMargenAPI(productoId)
            .then((data) => setMargen(data ?? {}))
            .catch((e) => setError(e.message))
            .finally(() => setIsLoading(false));
    }, [productoId]);

    const handleSave = async () => {
        setIsSaving(true);
        setError(null);
        setSuccessMsg(null);
        try {
            const dto = {
                margenMinorista: margen.margenMinorista ?? null,
                margenMayorista: margen.margenMayorista ?? null,
                margenFijoMinorista: margen.margenFijoMinorista ?? null,
                margenFijoMayorista: margen.margenFijoMayorista ?? null,
                observaciones: margen.observaciones ?? null,
            };
            const updated = await updateProductoMargenAPI(productoId, dto);
            setMargen(updated);
            setSuccessMsg("Margen guardado correctamente.");
        } catch (e: any) {
            setError(e.message);
        } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = async () => {
        if (!(await confirmDialog({ title: "Eliminar", message: "¿Seguro que querés eliminar la configuración de margen?", confirmText: "Eliminar", variant: "danger" }))) return;
        setError(null);
        try {
            await deleteProductoMargenAPI(productoId);
            setMargen({});
            setSuccessMsg("Margen eliminado.");
        } catch (e: any) {
            setError(e.message);
        }
    };

    const numField = (label: string, key: keyof ProductoMargenDTO) => (
        <label className="block">
            <span className="text-sm font-semibold text-gray-700">{label}</span>
            <input
                type="number"
                step="1"
                className="mt-1 w-full border border-gray-300 rounded p-2 text-sm"
                placeholder="—"
                value={margen[key] !== undefined && margen[key] !== null ? String(margen[key]) : ""}
                onChange={(e) =>
                    setMargen((prev) => ({
                        ...prev,
                        [key]: e.target.value === "" ? null : Number(e.target.value),
                    }))
                }
            />
        </label>
    );

    if (isLoading) return <p className="text-sm text-gray-400 text-center py-6">Cargando...</p>;

    return (
        <div className="flex flex-col gap-4">
            {error && <div className="p-3 bg-red-100 text-red-700 rounded text-sm">{error}</div>}
            {successMsg && <div className="p-3 bg-green-100 text-green-700 rounded text-sm">{successMsg}</div>}

            <div className="grid grid-cols-2 gap-4">
                {numField("Margen Minorista (%)", "margenMinorista")}
                {numField("Margen Mayorista (%)", "margenMayorista")}
                {numField("Margen Fijo Minorista ($)", "margenFijoMinorista")}
                {numField("Margen Fijo Mayorista ($)", "margenFijoMayorista")}
            </div>

            <label className="block">
                <span className="text-sm font-semibold text-gray-700">Observaciones</span>
                <textarea
                    className="mt-1 w-full border border-gray-300 rounded p-2 text-sm resize-none"
                    rows={3}
                    placeholder="Observaciones..."
                    value={margen.observaciones ?? ""}
                    onChange={(e) => setMargen((prev) => ({ ...prev, observaciones: e.target.value || null }))}
                />
            </label>

            <div className="flex gap-2 justify-end">
                {margen.id && (
                    <button
                        onClick={handleDelete}
                        className="text-red-500 hover:text-red-700 text-sm px-3 py-1 border border-red-200 rounded hover:bg-red-50 transition"
                    >
                        Eliminar
                    </button>
                )}
                <Button
                    text={isSaving ? "Guardando..." : "Guardar Margen"}
                    variant="dark"
                    onClick={handleSave}
                    disabled={isSaving}
                />
            </div>
        </div>
    );
}

// ---- Tab Precios ----
const formatARS = (n: number) =>
    `$${n.toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

function PreciosTab({ productoId }: { productoId: number }) {
    const [data, setData] = useState<ProductoCanalPrecioDTO | null>(null);
    const [margen, setMargen] = useState<ProductoMargenDTO | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isCalculating, setIsCalculating] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const load = useCallback(async () => {
        setIsLoading(true);
        setError(null);
        try {
            const [preciosData, margenData] = await Promise.all([
                getProductoPreciosAPI(productoId),
                getProductoMargenAPI(productoId),
            ]);
            setData(preciosData);
            setMargen(margenData);
        } catch (e: any) {
            setError(e.message);
        } finally {
            setIsLoading(false);
        }
    }, [productoId]);

    useEffect(() => { load(); }, [load]);

    const handleCalcular = async () => {
        setIsCalculating(true);
        try {
            await calcularPreciosAPI(productoId);
            await load();
            notificar.success("Precios calculados.");
        } catch (e: any) {
            notificar.error("Error al calcular: " + e.message);
        } finally {
            setIsCalculating(false);
        }
    };

    return (
        <div className="flex flex-col gap-4">
            {error && <div className="p-3 bg-red-100 text-red-700 rounded text-sm">{error}</div>}

            <div className="flex justify-between items-center flex-wrap gap-2">
                {data && (
                    <p className="text-xs text-gray-400">
                        Costo: <span className="text-gray-600 dark:text-slate-300 font-medium">{formatARS(data.costo)}</span>
                        {" · "}IVA: <span className="text-gray-600 dark:text-slate-300 font-medium">{data.iva}%</span>
                        {margen && (margen.margenMinorista != null || margen.margenMayorista != null) && (
                            <>
                                {margen.margenMinorista != null && (
                                    <>{" · "}Mrg. Min: <span className="text-gray-600 dark:text-slate-300 font-medium">{margen.margenMinorista}%</span></>
                                )}
                                {margen.margenMayorista != null && (
                                    <>{" · "}Mrg. May: <span className="text-gray-600 dark:text-slate-300 font-medium">{margen.margenMayorista}%</span></>
                                )}
                                {margen.margenFijoMinorista != null && (
                                    <>{" · "}Fijo Min: <span className="text-gray-600 dark:text-slate-300 font-medium">{formatARS(margen.margenFijoMinorista)}</span></>
                                )}
                                {margen.margenFijoMayorista != null && (
                                    <>{" · "}Fijo May: <span className="text-gray-600 dark:text-slate-300 font-medium">{formatARS(margen.margenFijoMayorista)}</span></>
                                )}
                            </>
                        )}
                    </p>
                )}
                <button
                    onClick={handleCalcular}
                    disabled={isCalculating}
                    className="ml-auto text-xs px-3 py-1.5 rounded bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50 transition font-medium inline-flex items-center gap-1.5"
                >
                    <ArrowPathIcon className={`w-3.5 h-3.5 ${isCalculating ? "animate-spin" : ""}`} />
                    {isCalculating ? "Recalculando..." : "Recalcular precios"}
                </button>
            </div>

            {isLoading ? (
                <p className="text-sm text-gray-400 text-center py-8">Cargando...</p>
            ) : !data || data.canales.length === 0 ? (
                <p className="text-sm text-gray-400 text-center py-8">
                    Sin precios calculados. Hacé click en "Calcular precios".
                </p>
            ) : (
                <div className="flex flex-col gap-5">
                    {data.canales.map((canal) => (
                        <div key={canal.canalId}>
                            <h4 className="text-xs font-bold text-gray-500 uppercase tracking-wide mb-1.5 border-b border-gray-100 pb-1">
                                {canal.canalNombre}
                            </h4>
                            <div className="overflow-x-auto">
                                <table className="text-xs border-collapse whitespace-nowrap">
                                    <thead>
                                        <tr className="text-gray-500">
                                            <th className="text-left px-2 py-1 font-semibold bg-gray-50 dark:bg-slate-800">Cuotas</th>
                                            <th className="text-right px-2 py-1 font-semibold bg-emerald-50 dark:bg-emerald-900/20">PVP</th>
                                            <th className="text-right px-2 py-1 font-semibold bg-emerald-50 dark:bg-emerald-900/20">Inflado</th>
                                            <th className="text-right px-2 py-1 font-semibold bg-amber-50 dark:bg-amber-900/20">Ganancia</th>
                                            <th className="text-right px-2 py-1 font-semibold bg-blue-50 dark:bg-blue-900/20">Costos Venta</th>
                                            <th className="text-right px-2 py-1 font-semibold bg-blue-50 dark:bg-blue-900/20">Ingreso Neto</th>
                                            <th className="text-right px-2 py-1 font-semibold bg-purple-50 dark:bg-purple-900/20">Mrg s/PVP</th>
                                            <th className="text-right px-2 py-1 font-semibold bg-purple-50 dark:bg-purple-900/20">Mrg s/IN</th>
                                            <th className="text-right px-2 py-1 font-semibold bg-purple-50 dark:bg-purple-900/20">Markup</th>
                                            <th className="text-right px-2 py-1 font-semibold bg-gray-50 dark:bg-slate-800">F. Cálculo</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {canal.precios.map((precio, i) => {
                                            const ganColor = precio.ganancia >= 0 ? "text-teal-600" : "text-red-600";
                                            const dash = <span className="text-gray-300">—</span>;
                                            return (
                                                <tr key={i} className="border-t border-gray-100">
                                                    <td className="px-2 py-1.5 text-gray-700 bg-gray-50/50 dark:bg-slate-800/50">{precio.descripcion}</td>
                                                    <td className="px-2 py-1.5 text-right font-mono font-semibold text-green-700 bg-emerald-50/50 dark:bg-emerald-900/10">{formatARS(precio.pvp)}</td>
                                                    <td className="px-2 py-1.5 text-right font-mono text-blue-600 bg-emerald-50/50 dark:bg-emerald-900/10">
                                                        {precio.pvpInflado != null ? formatARS(precio.pvpInflado) : dash}
                                                    </td>
                                                    <td className={`px-2 py-1.5 text-right font-semibold font-mono ${ganColor} bg-amber-50/50 dark:bg-amber-900/10`}>
                                                        {formatARS(precio.ganancia)}
                                                    </td>
                                                    <td className="px-2 py-1.5 text-right font-mono text-gray-500 bg-blue-50/50 dark:bg-blue-900/10">
                                                        {precio.costosVenta != null ? formatARS(precio.costosVenta) : dash}
                                                    </td>
                                                    <td className="px-2 py-1.5 text-right font-mono text-gray-600 bg-blue-50/50 dark:bg-blue-900/10">
                                                        {precio.ingresoNetoVendedor != null ? formatARS(precio.ingresoNetoVendedor) : dash}
                                                    </td>
                                                    <td className={`px-2 py-1.5 text-right ${ganColor} bg-purple-50/50 dark:bg-purple-900/10`}>
                                                        {precio.margenSobrePvp.toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%
                                                    </td>
                                                    <td className={`px-2 py-1.5 text-right ${precio.margenSobreIngresoNeto != null ? ganColor : "text-gray-300"} bg-purple-50/50 dark:bg-purple-900/10`}>
                                                        {precio.margenSobreIngresoNeto != null ? `${precio.margenSobreIngresoNeto.toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%` : dash}
                                                    </td>
                                                    <td className="px-2 py-1.5 text-right text-gray-600 bg-purple-50/50 dark:bg-purple-900/10">
                                                        {precio.markupPorcentaje != null ? `${precio.markupPorcentaje.toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%` : dash}
                                                    </td>
                                                    <td className="px-2 py-1.5 text-right text-gray-400 bg-gray-50/50 dark:bg-slate-800/50">
                                                        {formatFechaAR(precio.fechaUltimoCalculo ?? null)}
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}

function HistorialTab({ productoId, productoSku }: { productoId: number; productoSku: string }) {
    const [items, setItems] = useState<AuditoriaCambioDTO[]>([]);
    const [total, setTotal] = useState(0);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const load = useCallback(async () => {
        setIsLoading(true);
        setError(null);
        try {
            const response = await getProductoAuditoriaAPI(productoId, 0, 50, "fechaHora,desc");
            setItems(response.content ?? []);
            setTotal(response.page?.totalElements ?? response.content?.length ?? 0);
        } catch (e: any) {
            setError(e.message);
        } finally {
            setIsLoading(false);
        }
    }, [productoId]);

    useEffect(() => {
        void load();
    }, [load]);

    const renderValue = (value: string | null) => {
        if (value == null || value === "") return <span className="text-gray-400">—</span>;
        return <span className="break-words text-gray-700">{value}</span>;
    };

    const fieldLabelMap: Record<string, string> = {
        sku: "SKU",
        codExt: "Cód. Ext.",
        descripcion: "Descripción",
        tituloWeb: "Título web",
        esCombo: "Es combo",
        uxb: "UxB",
        moq: "MOQ",
        imagenUrl: "Imagen",
        stock: "Stock",
        activo: "Activo",
        tagReposicion: "Tag reposición",
        tag: "Tag",
        costo: "Costo",
        iva: "IVA",
        marca: "Marca",
        origen: "Origen",
        clasifGral: "Clasif. general",
        clasifGastro: "Clasif. gastro",
        tipo: "Tipo",
        proveedor: "Proveedor",
        material: "Material",
        mla: "MLA",
        capacidad: "Capacidad",
        largo: "Largo",
        ancho: "Ancho",
        alto: "Alto",
        diamboca: "Diam. boca",
        diambase: "Diam. base",
        espesor: "Espesor",
    };

    const actionLabelMap: Record<string, string> = {
        CREATE: "Alta",
        UPDATE: "Edición",
        DELETE: "Baja",
    };

    if (isLoading) return <p className="text-sm text-gray-400 text-center py-8">Cargando historial...</p>;

    return (
        <div className="flex flex-col gap-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex flex-col gap-1">
                    <div className="text-xs text-gray-500">
                        {total > items.length ? `Mostrando los últimos ${items.length} cambios de ${total}.` : `${items.length} cambio(s) registrados.`}
                    </div>
                    <div className="text-xs text-slate-400">
                        Esta vista sirve como contexto rápido del producto. Para investigar más, conviene usar la auditoría global.
                    </div>
                </div>
                <div className="flex items-center gap-2">
                    <Link
                        href={`/auditoria?entidad=PRODUCTO&search=${encodeURIComponent(productoSku)}`}
                        className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 transition hover:bg-slate-50"
                    >
                        Ver en auditoría global
                    </Link>
                    <button
                        onClick={() => void load()}
                        className="inline-flex items-center gap-1.5 rounded-lg border border-blue-200 bg-blue-50 px-3 py-1.5 text-xs font-medium text-blue-700 transition hover:bg-blue-100"
                    >
                        <ArrowPathIcon className="h-3.5 w-3.5" />
                        Actualizar
                    </button>
                </div>
            </div>

            {error && <div className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}

            {!error && items.length === 0 ? (
                <div className="flex flex-col items-center gap-3 py-8 text-center">
                    <p className="text-sm text-gray-400">Todavía no hay cambios auditados para este producto.</p>
                    <Link
                        href={`/auditoria?entidad=PRODUCTO&search=${encodeURIComponent(productoSku)}`}
                        className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 transition hover:bg-slate-50"
                    >
                        Abrir auditoría global
                    </Link>
                </div>
            ) : (
                <div className="overflow-hidden rounded-2xl border border-slate-200">
                    <div className="max-h-[420px] overflow-auto">
                        <table className="w-full text-sm">
                            <thead className="sticky top-0 z-10 bg-slate-50 text-slate-600">
                                <tr>
                                    <th className="px-3 py-2 text-left font-semibold">Fecha</th>
                                    <th className="px-3 py-2 text-left font-semibold">Usuario</th>
                                    <th className="px-3 py-2 text-left font-semibold">Acción</th>
                                    <th className="px-3 py-2 text-left font-semibold">Campo</th>
                                    <th className="px-3 py-2 text-left font-semibold">Antes</th>
                                    <th className="px-3 py-2 text-left font-semibold">Después</th>
                                    <th className="px-3 py-2 text-left font-semibold">Origen</th>
                                </tr>
                            </thead>
                            <tbody>
                                {items.map((item, index) => (
                                    <tr key={item.id} className={index % 2 === 0 ? "bg-white" : "bg-slate-50/60"}>
                                        <td className="px-3 py-2 whitespace-nowrap text-slate-600">{formatFechaAR(item.fechaHora)}</td>
                                        <td className="px-3 py-2">
                                            <div className="font-medium text-slate-700">{item.usuarioNombreCompleto || item.usuarioUsername || "Sistema"}</div>
                                            {item.usuarioNombreCompleto && item.usuarioUsername && (
                                                <div className="text-xs text-slate-400">{item.usuarioUsername}</div>
                                            )}
                                        </td>
                                        <td className="px-3 py-2">
                                            <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-semibold ${
                                                item.accion === "CREATE"
                                                    ? "bg-emerald-50 text-emerald-700"
                                                    : item.accion === "DELETE"
                                                        ? "bg-red-50 text-red-700"
                                                        : "bg-blue-50 text-blue-700"
                                            }`}>
                                                {actionLabelMap[item.accion] || item.accion}
                                            </span>
                                        </td>
                                        <td className="px-3 py-2 font-medium text-slate-700">{fieldLabelMap[item.campo] || item.campo}</td>
                                        <td className="px-3 py-2">{renderValue(item.valorAnterior)}</td>
                                        <td className="px-3 py-2">{renderValue(item.valorNuevo)}</td>
                                        <td className="px-3 py-2 whitespace-nowrap text-slate-500">{item.origen || "API"}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}
        </div>
    );
}

// ---- Modal principal ----
const TABS: { id: TabId; label: string }[] = [
    { id: "precios", label: "Precios" },
    { id: "margen", label: "Margen" },
    { id: "preciosInflados", label: "Precios Inflados" },
    { id: "historial", label: "Historial" },
    { id: "aptos", label: "Aptos" },
    { id: "catalogos", label: "Catálogos" },
    { id: "clientes", label: "Clientes" },
];

export default function ProductoDetalleModal({ isOpen, onClose, productoId, productoSku }: Props) {
    const [activeTab, setActiveTab] = useState<TabId>("precios");

    useEffect(() => {
        if (isOpen) setActiveTab("precios");
    }, [isOpen]);

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={`Detalle: ${productoSku}`}
            footer={<Button text="Cerrar" variant="light" onClick={onClose} />}
            size="2xl"
        >
            <div className="flex flex-col gap-4">
                {/* Tabs */}
                <div className="flex border-b border-gray-200">
                    {TABS.map((tab) => (
                        <button
                            key={tab.id}
                            onClick={() => setActiveTab(tab.id)}
                            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
                                activeTab === tab.id
                                    ? "border-blue-600 text-blue-700"
                                    : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
                            }`}
                        >
                            {tab.label}
                        </button>
                    ))}
                </div>

                {/* Tab content */}
                <div className="min-h-[200px]">
                    {activeTab === "aptos" && (
                        <RelationTab
                            productoId={productoId}
                            entityName="Apto"
                            getAssigned={getProductoAptosAPI}
                            assignFn={asignarAptoAPI}
                            removeFn={quitarAptoAPI}
                            getAllFn={getAllAptosAPI}
                            entityIdKey="aptoId"
                        />
                    )}
                    {activeTab === "catalogos" && (
                        <RelationTab
                            productoId={productoId}
                            entityName="Catálogo"
                            getAssigned={getProductoCatalogosAPI}
                            assignFn={asignarCatalogoAPI}
                            removeFn={quitarCatalogoAPI}
                            getAllFn={getAllCatalogosAPI}
                            entityIdKey="catalogoId"
                        />
                    )}
                    {activeTab === "clientes" && (
                        <RelationTab
                            productoId={productoId}
                            entityName="Cliente"
                            getAssigned={getProductoClientesAPI}
                            assignFn={asignarClienteAPI}
                            removeFn={quitarClienteAPI}
                            getAllFn={getAllClientesAPI}
                            entityIdKey="clienteId"
                        />
                    )}
                    {activeTab === "margen" && <MargenTab productoId={productoId} />}
                    {activeTab === "preciosInflados" && <PreciosInfladosTab productoId={productoId} />}
                    {activeTab === "precios" && <PreciosTab productoId={productoId} />}
                    {activeTab === "historial" && <HistorialTab productoId={productoId} productoSku={productoSku} />}
                </div>
            </div>
        </Modal>
    );
}

"use client";

import { useState, useEffect, useRef } from "react";
import Button from "../Button/Button";
import { API_BASE_URL } from "../../config/runtime";
import { fetchAPI } from "../../utils/fetchAPI";
import { confirmDialog } from "../../utils/confirmDialog";
import { PlayIcon, StopIcon, ArrowPathIcon, EyeIcon } from "@heroicons/react/24/outline";
import { useProcesoActivo } from "../../context/ProcesoActivoContext";
import { toast } from "sonner";

export interface ProcesoEstado {
    enEjecucion: boolean;
    total: number;
    procesados: number;
    exitosos: number;
    errores: number;
    estado: string;
    mensaje?: string;
}

type PanelEstado = "IDLE" | "EN_PROCESO" | "COMPLETADO" | "ERROR";

function ProgressBar({ procesados, total }: { procesados: number; total: number }) {
    const porcentaje = total > 0 ? Math.min(Math.round((procesados / total) * 100), 100) : 0;
    return (
        <div className="mt-3">
            <div className="flex justify-between text-xs text-gray-500 dark:text-slate-400 mb-1">
                <span>{procesados} / {total} procesados</span>
                <span>{porcentaje}%</span>
            </div>
            <div className="w-full bg-gray-200 dark:bg-slate-700 rounded-full h-3 overflow-hidden">
                <div
                    className="bg-blue-600 h-3 rounded-full transition-all duration-500"
                    style={{ width: `${porcentaje}%` }}
                />
            </div>
        </div>
    );
}

function ResultadoPanel({ resultado }: { resultado: Record<string, any> }) {
    const [expandido, setExpandido] = useState(false);
    return (
        <div className="border border-gray-200 dark:border-slate-700 rounded-lg overflow-hidden">
            <button
                className="w-full flex justify-between items-center px-4 py-2 bg-gray-50 dark:bg-slate-700/50 text-sm font-medium text-gray-700 dark:text-slate-200 hover:bg-gray-100 dark:hover:bg-slate-700 transition"
                onClick={() => setExpandido((v) => !v)}
            >
                <span>Detalle del último resultado</span>
                <span className="text-gray-400 dark:text-slate-500">{expandido ? "▲" : "▼"}</span>
            </button>
            {expandido && (
                <div className="p-4 bg-white dark:bg-slate-800 overflow-x-auto">
                    <table className="text-xs w-full border-collapse">
                        <tbody>
                            {Object.entries(resultado).map(([key, value]) => (
                                <tr key={key} className="border-b border-gray-100 dark:border-slate-700 last:border-0">
                                    <td className="py-1 pr-4 font-medium text-gray-500 dark:text-slate-400 whitespace-nowrap align-top">{key}</td>
                                    <td className="py-1 text-gray-800 dark:text-slate-200 break-all">
                                        {typeof value === "object" && value !== null
                                            ? <pre className="whitespace-pre-wrap font-mono text-xs">{JSON.stringify(value, null, 2)}</pre>
                                            : String(value ?? "-")}
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

export interface OperacionPanelProps {
    titulo: string;
    descripcion: string;
    /** POST endpoint para iniciar */
    endpointIniciar: string;
    /** GET endpoint para consultar estado */
    endpointEstado: string;
    /** POST endpoint para cancelar */
    endpointCancelar: string;
    /** GET endpoint para obtener el resultado del último proceso (opcional) */
    endpointResultado?: string;
    /** Texto de confirmación antes de ejecutar (opcional) */
    confirmMessage?: string;
    /** Callback que se llama cuando el proceso se completa exitosamente */
    onComplete?: () => void;
    /** Callback que se llama cuando el estado de ejecución cambia */
    onRunningChange?: (running: boolean) => void;
    /** Si true, deshabilita el panel externamente */
    disabled?: boolean;
    /** Mensaje explicando por qué está deshabilitado */
    disabledReason?: string;
    /** Si true, renderiza sin borde/sombra propios (para embeber en secciones coloreadas) */
    embedded?: boolean;
    /** Función que retorna el body JSON para el POST de inicio (opcional) */
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    requestBody?: () => any;
    /** ID del proceso para el sistema de bloqueo por grupos (opcional) */
    procesoId?: string;
    /** Nota informativa que se muestra debajo de la descripcion */
    nota?: string;
}

export function OperacionPanel({
    titulo,
    descripcion,
    endpointIniciar,
    endpointEstado,
    endpointCancelar,
    endpointResultado,
    confirmMessage,
    onComplete,
    onRunningChange,
    disabled: propDisabled = false,
    disabledReason,
    embedded = false,
    requestBody,
    procesoId,
    nota,
}: OperacionPanelProps) {
    const { tieneConflicto, refresh: refreshProcesosActivos } = useProcesoActivo();
    const [panelEstado, setPanelEstado] = useState<PanelEstado>("IDLE");
    const onRunningChangeRef = useRef(onRunningChange);
    onRunningChangeRef.current = onRunningChange;
    const [proceso, setProceso] = useState<ProcesoEstado | null>(null);
    const [mensaje, setMensaje] = useState<string | null>(null);
    const [resultado, setResultado] = useState<Record<string, any> | null>(null);
    const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
    const onCompleteRef = useRef(onComplete);
    onCompleteRef.current = onComplete;

    useEffect(() => {
        onRunningChangeRef.current?.(panelEstado === "EN_PROCESO");
    }, [panelEstado]);

    const limpiarInterval = () => {
        if (intervalRef.current !== null) {
            clearInterval(intervalRef.current);
            intervalRef.current = null;
        }
    };

    // Verificar estado al montar para restaurar estado previo
    useEffect(() => {
        const checkEstadoInicial = async () => {
            try {
                const res = await fetchAPI(`${API_BASE_URL}${endpointEstado}`);
                const data: ProcesoEstado = await res.json();
                if (data.estado === "completado") {
                    setPanelEstado("COMPLETADO");
                    setProceso(data);
                    setMensaje(`Completado: ${data.exitosos} exitosos, ${data.errores} errores de ${data.total} procesados.`);
                    onCompleteRef.current?.();
                } else if (data.estado === "ejecutando" || data.enEjecucion) {
                    setPanelEstado("EN_PROCESO");
                    setProceso(data);
                    intervalRef.current = setInterval(consultarEstado, 5000);
                }
            } catch { /* ignorar */ }
        };
        checkEstadoInicial();
        return () => limpiarInterval();
    }, [endpointEstado]);

    const fetchResultado = async (origen: "auto" | "manual" = "auto") => {
        if (!endpointResultado) return;
        try {
            const res = await fetchAPI(`${API_BASE_URL}${endpointResultado}`);
            if (res.status === 204) {
                setResultado(null);
                if (origen === "manual") toast.info("Todavía no hay un resultado disponible para este proceso.");
                return;
            }
            const data = await res.json();
            setResultado(data);
        } catch {
            if (origen === "manual") toast.error("No se pudo obtener el resultado.");
        }
    };

    const consultarEstado = async () => {
        try {
            const res = await fetchAPI(`${API_BASE_URL}${endpointEstado}`);
            const data: ProcesoEstado = await res.json();
            setProceso(data);

            if (data.estado === "completado") {
                limpiarInterval();
                setPanelEstado("COMPLETADO");
                setMensaje(`Completado: ${data.exitosos} exitosos, ${data.errores} errores de ${data.total} procesados.`);
                await fetchResultado();
                onCompleteRef.current?.();
            } else if (data.estado === "cancelado") {
                limpiarInterval();
                setPanelEstado("IDLE");
                setMensaje("Proceso cancelado.");
            } else if (!data.enEjecucion && data.estado !== "ejecutando") {
                limpiarInterval();
                setPanelEstado("IDLE");
            }
        } catch (err: any) {
            limpiarInterval();
            setPanelEstado("ERROR");
            setMensaje("Error al consultar el estado: " + (err?.message ?? "desconocido"));
        }
    };

    const handleEjecutar = async () => {
        const msg = confirmMessage ?? `¿Iniciar "${titulo}"? Este proceso puede tardar varios minutos.`;
        if (!(await confirmDialog({ title: "Confirmar", message: msg, confirmText: "Iniciar" }))) return;
        limpiarInterval();
        setPanelEstado("EN_PROCESO");
        setProceso(null);
        setMensaje(null);
        setResultado(null);

        try {
            const fetchOpts: RequestInit = { method: "POST" };
            if (requestBody) {
                fetchOpts.headers = { "Content-Type": "application/json" };
                fetchOpts.body = JSON.stringify(requestBody());
            }
            await fetchAPI(`${API_BASE_URL}${endpointIniciar}`, fetchOpts);
            intervalRef.current = setInterval(consultarEstado, 5000);
        } catch (err: any) {
            setPanelEstado("ERROR");
            setMensaje("No se pudo iniciar la operación: " + (err?.message ?? "desconocido"));
        }
    };

    const handleCancelar = async () => {
        limpiarInterval();
        try {
            await fetchAPI(`${API_BASE_URL}${endpointCancelar}`, { method: "POST" });
        } catch { /* ignorar */ }
        setPanelEstado("IDLE");
        setMensaje("Proceso cancelado.");
        // Refrescar el header: el backend libera el proceso cuando el thread async
        // termina de atender la llamada HTTP actual, así que hay un delay de algunos
        // segundos. Disparamos varios refresh para acelerar la limpieza del badge.
        refreshProcesosActivos();
        setTimeout(refreshProcesosActivos, 3000);
        setTimeout(refreshProcesosActivos, 8000);
    };

    const enProceso = panelEstado === "EN_PROCESO";
    const completado = panelEstado === "COMPLETADO";
    const error = panelEstado === "ERROR";

    // Bloquear si hay un proceso activo en conflicto de grupo (pero no si este panel ya está corriendo)
    const conflicto = procesoId && !enProceso ? tieneConflicto(procesoId) : null;
    const disabled = propDisabled || !!conflicto;
    const reasonToShow = propDisabled
        ? disabledReason
        : conflicto
          ? `Otro proceso en ejecucion: ${conflicto.descripcion}`
          : disabledReason;

    return (
        <div className={`rounded-xl flex flex-col gap-4 transition-opacity ${
            embedded
                ? `bg-white dark:bg-slate-800 border border-gray-200 dark:border-slate-600 p-5 ${disabled ? "opacity-60" : ""}`
                : `border bg-white p-6 shadow dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-black/20 ${disabled ? "opacity-60 border-gray-200" : "border-gray-200"}`
        }`}>
            {(!embedded || descripcion) && (
                <div>
                    {!embedded && <h2 className="text-lg font-bold text-gray-800 dark:text-slate-100">{titulo}</h2>}
                    {descripcion && <p className="text-sm text-gray-500 dark:text-slate-400 mt-1">{descripcion}</p>}
                    {nota && (
                        <p className="inline-flex items-center gap-1.5 mt-2 text-xs font-medium text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-2.5 py-1 dark:text-amber-300 dark:bg-amber-500/10 dark:border-amber-700">
                            <svg className="w-3.5 h-3.5 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
                            {nota}
                        </p>
                    )}
                </div>
            )}

            {disabled && reasonToShow && (
                <div className="text-xs text-amber-700 dark:text-amber-400 bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-700 rounded px-3 py-2">
                    {reasonToShow}
                </div>
            )}

            <div className="flex gap-2 flex-wrap">
                <Button
                    variant="dark"
                    onClick={handleEjecutar}
                    disabled={enProceso || disabled}
                >
                    {enProceso
                        ? <><ArrowPathIcon className="w-4 h-4 animate-spin" /> Procesando...</>
                        : <><PlayIcon className="w-4 h-4" /> Ejecutar</>
                    }
                </Button>
                {enProceso && (
                    <Button variant="danger" onClick={handleCancelar}>
                        <StopIcon className="w-4 h-4" /> Cancelar
                    </Button>
                )}
                {!enProceso && endpointResultado && (
                    <Button variant="light" onClick={() => fetchResultado("manual")} disabled={disabled}>
                        <EyeIcon className="w-4 h-4" /> Ver último resultado
                    </Button>
                )}
            </div>

            {enProceso && proceso && proceso.total > 0 && (
                <ProgressBar procesados={proceso.procesados} total={proceso.total} />
            )}
            {enProceso && (!proceso || proceso.total === 0) && (
                <p className="text-sm text-gray-500 dark:text-slate-400">Iniciando proceso...</p>
            )}
            {enProceso && proceso?.mensaje && (
                <p className="text-xs text-gray-500 dark:text-slate-400">{proceso.mensaje}</p>
            )}

            {completado && mensaje && (
                <div className="text-sm text-green-700 dark:text-green-400 bg-green-50 dark:bg-green-900/30 border border-green-200 dark:border-green-700 rounded p-3">
                    {mensaje}
                </div>
            )}

            {error && mensaje && (
                <div className="text-sm text-red-700 dark:text-red-400 bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 rounded p-3">
                    {mensaje}
                </div>
            )}

            {!enProceso && !completado && !error && mensaje && (
                <div className="text-sm text-yellow-700 dark:text-yellow-400 bg-yellow-50 dark:bg-yellow-900/30 border border-yellow-200 dark:border-yellow-700 rounded p-3">
                    {mensaje}
                </div>
            )}

            {resultado && <ResultadoPanel resultado={resultado} />}
        </div>
    );
}

"use client";
import { useState, useEffect, useRef } from "react";
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { OperacionPanel } from "../components/OperacionPanel/OperacionPanel";
import { confirmDialog } from "../utils/confirmDialog";
import Button from "../components/Button/Button";
import {
    ServerStackIcon,
    PlayIcon,
    ArrowsRightLeftIcon,
    ExclamationTriangleIcon,
} from "@heroicons/react/24/outline";

// ── ExportarProductosPanel ──────────────────────────────────────────────────

const shellCardClassName = "rounded-3xl border border-slate-200 bg-white shadow-[0_10px_30px_-18px_rgba(15,23,42,0.25)] dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-black/30";

type ExportarEstado = "IDLE" | "EN_PROCESO" | "COMPLETADO" | "ERROR";

function ExportarProductosPanel() {
    const [estado, setEstado] = useState<ExportarEstado>("IDLE");
    const [mensaje, setMensaje] = useState<string | null>(null);
    const [jobMensaje, setJobMensaje] = useState<string | null>(null);
    const [procesados, setProcesados] = useState(0);
    const [total, setTotal] = useState(0);
    const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
    const idProcesoRef = useRef<number | null>(null);

    useEffect(() => {
        return () => { if (intervalRef.current) clearInterval(intervalRef.current); };
    }, []);

    const consultarEstado = async () => {
        if (idProcesoRef.current === null) return;
        try {
            const res = await fetchAPI(`${API_BASE_URL}/api/dux/procesos/${idProcesoRef.current}/estado`);
            const data = await res.json();
            if (data.procesados !== undefined) setProcesados(data.procesados);
            if (data.total !== undefined) setTotal(data.total);
            if (data.mensaje) setJobMensaje(data.mensaje);

            if (data.estado === "completado") {
                clearInterval(intervalRef.current!);
                intervalRef.current = null;
                setEstado("COMPLETADO");
                setMensaje(`Exportacion completada: ${data.exitosos ?? data.procesados} registros exportados.`);
            } else if (data.estado === "cancelado" || data.estado === "error") {
                clearInterval(intervalRef.current!);
                intervalRef.current = null;
                setEstado(data.estado === "error" ? "ERROR" : "IDLE");
                setMensaje(data.estado === "error" ? (data.mensaje ?? "Error en la exportacion.") : "Proceso cancelado.");
            }
        } catch (err: any) {
            clearInterval(intervalRef.current!);
            intervalRef.current = null;
            setEstado("ERROR");
            setMensaje("Error al consultar el estado: " + (err?.message ?? "desconocido"));
        }
    };

    const handleEjecutar = async () => {
        if (!(await confirmDialog({ title: "Confirmar", message: "Iniciar la exportacion de productos a DUX? Este proceso puede tardar varios minutos.", confirmText: "Iniciar" }))) return;
        setEstado("EN_PROCESO");
        setMensaje(null);
        setJobMensaje(null);
        setProcesados(0);
        setTotal(0);
        idProcesoRef.current = null;

        try {
            const res = await fetchAPI(`${API_BASE_URL}/api/dux/exportar-productos`, { method: "POST" });
            const data = await res.json();
            idProcesoRef.current = data.idProceso ?? data.id ?? null;
            if (idProcesoRef.current === null) throw new Error("No se recibio idProceso");
            intervalRef.current = setInterval(consultarEstado, 3000);
        } catch (err: any) {
            setEstado("ERROR");
            setMensaje("No se pudo iniciar la exportacion: " + (err?.message ?? "desconocido"));
        }
    };

    const enProceso = estado === "EN_PROCESO";
    const completado = estado === "COMPLETADO";
    const error = estado === "ERROR";
    const porcentaje = total > 0 ? Math.min(Math.round((procesados / total) * 100), 100) : 0;

    return (
        <div className={`${shellCardClassName} flex flex-col gap-4 p-6`}>
            <div className="flex items-start justify-between gap-3">
                <div>
                    <div className="mb-2 inline-flex items-center gap-2 rounded-full border border-rose-200 bg-rose-50 px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.12em] text-rose-700">
                        <ArrowsRightLeftIcon className="h-3.5 w-3.5" />
                        Escritura en DUX
                    </div>
                    <h2 className="text-lg font-bold text-slate-800 dark:text-slate-100">Exportar Productos a DUX</h2>
                    <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">Sincroniza el catalogo actual hacia DUX ERP.</p>
                </div>
            </div>

            <div className="flex items-start gap-2 rounded-2xl border border-rose-200 bg-gradient-to-r from-rose-50 to-white px-3.5 py-3 text-rose-800 dark:border-rose-500/30 dark:from-rose-500/10 dark:to-slate-800 dark:text-rose-200">
                <ExclamationTriangleIcon className="mt-0.5 h-4 w-4 shrink-0 text-rose-500 dark:text-rose-300" />
                <p className="text-xs font-medium leading-snug">
                    Esta operacion <strong>escribe datos directamente en DUX ERP</strong>. Ejecutar solo cuando se requiera sincronizar el catalogo hacia DUX.
                </p>
            </div>

            <div className="flex gap-2 flex-wrap items-center">
                <Button variant="dark" onClick={handleEjecutar} disabled={true}>
                    <PlayIcon className="w-4 h-4" />
                    Ejecutar
                </Button>
            </div>

            {enProceso && total > 0 && (
                <div className="mt-1">
                    <div className="mb-1 flex justify-between text-xs text-gray-500 dark:text-slate-400">
                        <span>{procesados} / {total} procesados</span>
                        <span>{porcentaje}%</span>
                    </div>
                    <div className="h-3 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-slate-700">
                        <div className="bg-blue-600 h-3 rounded-full transition-all duration-500" style={{ width: `${porcentaje}%` }} />
                    </div>
                </div>
            )}
            {enProceso && total === 0 && (
                <p className="text-sm text-gray-500 dark:text-slate-400">Iniciando proceso...</p>
            )}
            {enProceso && jobMensaje && (
                <p className="text-xs text-gray-500 dark:text-slate-400">{jobMensaje}</p>
            )}

            {completado && mensaje && (
                <div className="rounded-2xl border border-green-200 bg-green-50 p-3 text-sm text-green-700 dark:border-green-500/30 dark:bg-green-500/10 dark:text-green-200">{mensaje}</div>
            )}
            {error && mensaje && (
                <div className="rounded-2xl border border-red-200 bg-red-50 p-3 text-sm text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-200">{mensaje}</div>
            )}
        </div>
    );
}

// ── Page ─────────────────────────────────────────────────────────────────────

export default function DuxOperacionesPage() {
    const [obtenerCompletado, setObtenerCompletado] = useState(false);

    return (
        <main className="p-4 bg-gray-50 dark:bg-slate-900 min-h-0 flex flex-col gap-6 overflow-auto">
            <div className="flex items-center gap-3">
                <ServerStackIcon className="w-8 h-8 text-gray-600 dark:text-slate-400" />
                <div>
                    <h1 className="text-3xl font-bold text-gray-800 dark:text-slate-100">Operaciones DUX</h1>
                    <p className="text-sm text-gray-500 dark:text-slate-400">Secuencia recomendada para sincronizar productos con DUX sin perder contexto del proceso.</p>
                </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
                <OperacionPanel
                    titulo="Paso 1 — Obtener Productos de DUX"
                    descripcion="Descarga el catalogo de productos desde DUX ERP a un area de staging local. No modifica el sistema."
                    endpointIniciar="/api/dux/obtener-productos"
                    endpointEstado="/api/dux/obtener-productos/estado"
                    endpointCancelar="/api/dux/obtener-productos/cancelar"
                    endpointResultado="/api/dux/obtener-productos/resultado"
                    confirmMessage="Iniciar la descarga de productos desde DUX? Este proceso puede tardar varios minutos."
                    onComplete={() => setObtenerCompletado(true)}
                    procesoId="dux-obtencion"
                />
                <OperacionPanel
                    titulo="Paso 2 — Importar Productos al Sistema"
                    descripcion="Procesa los datos descargados en el Paso 1 y actualiza los productos existentes en el sistema (costo, IVA, proveedor, descripcion). Los SKUs no encontrados se reportan en el resultado."
                    endpointIniciar="/api/dux/importar-productos"
                    endpointEstado="/api/dux/importar-productos/estado"
                    endpointCancelar="/api/dux/importar-productos/cancelar"
                    endpointResultado="/api/dux/importar-productos/resultado"
                    nota="Solo actualiza productos existentes. No crea productos nuevos."
                    confirmMessage="Iniciar la importacion de productos de DUX? Se actualizaran los productos existentes en el sistema."
                    disabled={!obtenerCompletado}
                    disabledReason="Ejecuta primero el Paso 1 (Obtener Productos de DUX) para poder importar."
                    procesoId="dux-importacion"
                />
                <ExportarProductosPanel />
            </div>
        </main>
    );
}

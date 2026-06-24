"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
    ServerStackIcon,
    PlayIcon,
    ArrowsRightLeftIcon,
    ExclamationTriangleIcon,
    ClockIcon,
    CloudArrowDownIcon,
    PlusIcon,
    TrashIcon,
    ArrowPathIcon,
    InformationCircleIcon,
} from "@heroicons/react/24/outline";
import { toast } from "sonner";

import { getErrorMessage } from "@/lib/errors";
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { confirmDialog } from "../utils/confirmDialog";
import { OperacionPanel } from "../components/OperacionPanel/OperacionPanel";
import Button from "../components/Button/Button";

// ──────────────────────────────────────────────────────────────────────────────
// Tipos
// ──────────────────────────────────────────────────────────────────────────────

type HorarioSync = { hora: number; minuto: number };
type FilaHorario = { valor: string }; // "HH:mm" para <input type="time">
type UltimaSyncResponse = {
    ultimaSyncGlobalAt: string | null;
    ultimoIniciadoEn: string | null;
    ultimoDesde: string | null;
};
type ModoSync = "incremental" | "completo";

// ──────────────────────────────────────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────────────────────────────────────

const shellCardClassName =
    "rounded-3xl border border-slate-200 bg-white shadow-[0_10px_30px_-18px_rgba(15,23,42,0.25)] dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-black/30";

const horarioToValor = (h: HorarioSync): string =>
    `${String(h.hora).padStart(2, "0")}:${String(h.minuto).padStart(2, "0")}`;

const valorToHorario = (valor: string): HorarioSync | null => {
    const m = valor.match(/^(\d{1,2}):(\d{2})$/);
    if (!m) return null;
    const hora = Number(m[1]);
    const minuto = Number(m[2]);
    if (Number.isNaN(hora) || Number.isNaN(minuto)) return null;
    if (hora < 0 || hora > 23 || minuto < 0 || minuto > 59) return null;
    return { hora, minuto };
};

const sonIguales = (a: FilaHorario[], b: FilaHorario[]) =>
    a.length === b.length && a.every((f, i) => f.valor === b[i].valor);

const formatearFecha = (iso: string | null): string => {
    if (!iso) return "—";
    try {
        const d = new Date(iso);
        if (Number.isNaN(d.getTime())) return iso;
        return d.toLocaleString("es-AR", {
            day: "2-digit",
            month: "2-digit",
            year: "numeric",
            hour: "2-digit",
            minute: "2-digit",
        });
    } catch {
        return iso;
    }
};

// ──────────────────────────────────────────────────────────────────────────────
// Banner con el cursor incremental y la última corrida
// ──────────────────────────────────────────────────────────────────────────────

function UltimaSyncBanner({ trigger }: { trigger: number }) {
    const [data, setData] = useState<UltimaSyncResponse | null>(null);
    const [cargando, setCargando] = useState(true);

    const cargar = useCallback(async () => {
        setCargando(true);
        try {
            const res = await fetchAPI(`${API_BASE_URL}/api/dux/sincronizacion/ultima-sync`);
            setData(await res.json());
        } catch {
            setData(null);
        } finally {
            setCargando(false);
        }
    }, []);

    useEffect(() => {
        cargar();
    }, [cargar, trigger]);

    return (
        <div className="rounded-2xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800/80 px-4 py-3 flex flex-col md:flex-row md:items-center md:justify-between gap-2">
            <div className="text-sm text-slate-600 dark:text-slate-300 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-6 gap-y-1">
                <div>
                    <span className="font-semibold">Cursor incremental:</span>{" "}
                    {cargando ? "cargando…" : formatearFecha(data?.ultimaSyncGlobalAt ?? null)}
                    <span className="block text-[11px] text-slate-400 dark:text-slate-500">
                        Compartido con Automatización Precios KT.
                    </span>
                </div>
                <div>
                    <span className="font-semibold">Última corrida (inicio):</span>{" "}
                    {cargando ? "cargando…" : formatearFecha(data?.ultimoIniciadoEn ?? null)}
                    <span className="block text-[11px] text-slate-400 dark:text-slate-500">
                        En memoria; se reinicia al reiniciar el backend.
                    </span>
                </div>
                <div>
                    <span className="font-semibold">Filtro de fecha aplicado:</span>{" "}
                    {cargando
                        ? "cargando…"
                        : data?.ultimoDesde
                            ? formatearFecha(data.ultimoDesde)
                            : "sin filtro (completo)"}
                </div>
            </div>
            <button
                onClick={cargar}
                disabled={cargando}
                className="text-xs text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 inline-flex items-center gap-1 self-end md:self-center"
            >
                <ArrowPathIcon className={`w-3.5 h-3.5 ${cargando ? "animate-spin" : ""}`} />
                Refrescar
            </button>
        </div>
    );
}

// ──────────────────────────────────────────────────────────────────────────────
// Editor de horarios programados
// ──────────────────────────────────────────────────────────────────────────────

function HorariosEditor() {
    const [horarios, setHorarios] = useState<FilaHorario[]>([]);
    const [original, setOriginal] = useState<FilaHorario[]>([]);
    const [cargando, setCargando] = useState(true);
    const [guardando, setGuardando] = useState(false);

    const cargar = useCallback(async () => {
        setCargando(true);
        try {
            const res = await fetchAPI(`${API_BASE_URL}/api/dux/sincronizacion/horarios`);
            const data: HorarioSync[] = await res.json();
            const filas = data.map((h) => ({ valor: horarioToValor(h) }));
            setHorarios(filas);
            setOriginal(filas);
        } catch (err) {
            toast.error("No se pudieron cargar los horarios: " + getErrorMessage(err, "desconocido"));
        } finally {
            setCargando(false);
        }
    }, []);

    useEffect(() => {
        cargar();
    }, [cargar]);

    const hayCambios = useMemo(() => !sonIguales(horarios, original), [horarios, original]);

    const duplicados = useMemo(() => {
        const visto = new Set<string>();
        const dup = new Set<string>();
        for (const f of horarios) {
            if (!f.valor) continue;
            if (visto.has(f.valor)) dup.add(f.valor);
            visto.add(f.valor);
        }
        return dup;
    }, [horarios]);

    const incompletos = horarios.some((f) => !f.valor);

    const agregar = () => setHorarios((prev) => [...prev, { valor: "" }]);
    const eliminar = (idx: number) =>
        setHorarios((prev) => prev.filter((_, i) => i !== idx));
    const actualizar = (idx: number, valor: string) =>
        setHorarios((prev) => prev.map((f, i) => (i === idx ? { valor } : f)));

    const descartar = () => setHorarios(original);

    const guardar = async () => {
        if (incompletos) {
            toast.error("Hay horarios incompletos. Completalos o eliminalos.");
            return;
        }
        if (duplicados.size > 0) {
            toast.error("Hay horarios duplicados: " + Array.from(duplicados).join(", "));
            return;
        }
        const payload: HorarioSync[] = horarios
            .map((f) => valorToHorario(f.valor))
            .filter((h): h is HorarioSync => h !== null);

        setGuardando(true);
        try {
            const res = await fetchAPI(`${API_BASE_URL}/api/dux/sincronizacion/horarios`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            });
            const data: HorarioSync[] = await res.json();
            const filas = data.map((h) => ({ valor: horarioToValor(h) }));
            setHorarios(filas);
            setOriginal(filas);
            toast.success(
                filas.length > 0
                    ? `Horarios actualizados (${filas.length} disparo(s) diarios).`
                    : "Horarios borrados: la sincronización automática queda deshabilitada."
            );
        } catch (err) {
            toast.error("No se pudieron guardar los horarios: " + getErrorMessage(err, "desconocido"));
        } finally {
            setGuardando(false);
        }
    };

    return (
        <section className={`${shellCardClassName} p-6 flex flex-col gap-4`}>
            <header className="flex items-start justify-between gap-3">
                <div>
                    <div className="mb-2 inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.12em] text-emerald-700">
                        <ClockIcon className="h-3.5 w-3.5" />
                        Sync automática
                    </div>
                    <h2 className="text-lg font-bold text-slate-800 dark:text-slate-100">Horarios diarios</h2>
                    <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                        Cada fila es un disparo diario (zona AR) que ejecuta una importación incremental desde DUX. Si no hay filas, la sincronización automática queda deshabilitada.
                    </p>
                </div>
                <Button variant="light" onClick={cargar} disabled={cargando || guardando}>
                    <ArrowPathIcon className={`w-4 h-4 ${cargando ? "animate-spin" : ""}`} />
                    Recargar
                </Button>
            </header>

            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
                {horarios.map((f, idx) => {
                    const esDup = !!f.valor && duplicados.has(f.valor);
                    return (
                        <div
                            key={idx}
                            className={`flex items-center gap-2 rounded-xl border bg-white dark:bg-slate-700 p-2 ${
                                esDup
                                    ? "border-red-400 dark:border-red-500"
                                    : "border-slate-200 dark:border-slate-600"
                            }`}
                        >
                            <input
                                type="time"
                                step="60"
                                value={f.valor}
                                onChange={(e) => actualizar(idx, e.target.value)}
                                className="flex-1 bg-transparent text-slate-800 dark:text-slate-100 text-sm focus:outline-none"
                                disabled={guardando}
                            />
                            <button
                                onClick={() => eliminar(idx)}
                                disabled={guardando}
                                className="p-1 rounded text-slate-400 hover:text-red-600 dark:hover:text-red-400"
                                title="Eliminar"
                            >
                                <TrashIcon className="w-4 h-4" />
                            </button>
                        </div>
                    );
                })}
                <button
                    onClick={agregar}
                    disabled={guardando}
                    className="flex items-center justify-center gap-1 rounded-xl border border-dashed border-slate-300 dark:border-slate-600 text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:border-slate-400 dark:hover:border-slate-500 p-2 text-sm"
                >
                    <PlusIcon className="w-4 h-4" />
                    Agregar
                </button>
            </div>

            {(duplicados.size > 0 || incompletos) && (
                <div className="text-xs text-red-700 dark:text-red-400 bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 rounded px-3 py-2">
                    {duplicados.size > 0 && <div>Horarios duplicados: {Array.from(duplicados).join(", ")}</div>}
                    {incompletos && <div>Hay filas con horario vacío. Completalas o eliminalas.</div>}
                </div>
            )}

            {hayCambios && (
                <div className="flex gap-2 flex-wrap">
                    <Button variant="dark" onClick={guardar} disabled={guardando || cargando}>
                        {guardando ? (
                            <>
                                <ArrowPathIcon className="w-4 h-4 animate-spin" />
                                Guardando…
                            </>
                        ) : (
                            "Guardar cambios"
                        )}
                    </Button>
                    <Button variant="light" onClick={descartar} disabled={guardando}>
                        Descartar
                    </Button>
                </div>
            )}

            <div className="flex items-start gap-2 text-xs text-slate-500 dark:text-slate-400">
                <InformationCircleIcon className="w-4 h-4 shrink-0 mt-0.5" />
                <span>
                    Cada disparo automático ejecuta una importación <strong>incremental</strong>: pide a DUX solo los items modificados desde la última corrida exitosa.
                </span>
            </div>
        </section>
    );
}

// ──────────────────────────────────────────────────────────────────────────────
// Sync manual con selector de modo (incremental / completo)
// ──────────────────────────────────────────────────────────────────────────────

function SyncManualPanel({ onComplete }: { onComplete: () => void }) {
    const [modo, setModo] = useState<ModoSync>("incremental");

    const endpointIniciar = useMemo(
        () =>
            modo === "completo"
                ? "/api/dux/sincronizacion/iniciar?force=true"
                : "/api/dux/sincronizacion/iniciar",
        [modo]
    );

    const descripcion =
        modo === "incremental"
            ? "Próxima corrida: INCREMENTAL (solo items modificados desde la última sync exitosa)."
            : "Próxima corrida: COMPLETA (baja todo el catálogo de DUX, ignora el cursor).";

    const confirmMessage =
        modo === "incremental"
            ? "¿Iniciar sincronización incremental con DUX?"
            : "¿Forzar descarga COMPLETA del catálogo DUX? Puede tardar varios minutos.";

    return (
        <section className="flex flex-col gap-3">
            <div className="rounded-2xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800/80 p-4 flex flex-wrap gap-3">
                <ModoToggle
                    label="Incremental"
                    descripcion="Usa el cursor (última sync)"
                    activo={modo === "incremental"}
                    onClick={() => setModo("incremental")}
                />
                <ModoToggle
                    label="Completo"
                    descripcion="Baja todo el catálogo"
                    activo={modo === "completo"}
                    onClick={() => setModo("completo")}
                />
            </div>

            <OperacionPanel
                titulo="Sincronización manual a productos"
                descripcion={descripcion}
                endpointIniciar={endpointIniciar}
                endpointEstado="/api/dux/sincronizacion/estado"
                endpointCancelar="/api/dux/sincronizacion/cancelar"
                endpointResultado="/api/dux/sincronizacion/resultado"
                confirmMessage={confirmMessage}
                procesoId="dux-sync-programada"
                onComplete={onComplete}
                nota={
                    modo === "completo"
                        ? "Modo COMPLETO: la próxima corrida descarga TODO el catálogo (no usa el cursor)."
                        : undefined
                }
            />
        </section>
    );
}

function ModoToggle({
    label,
    descripcion,
    activo,
    onClick,
}: {
    label: string;
    descripcion: string;
    activo: boolean;
    onClick: () => void;
}) {
    return (
        <button
            type="button"
            onClick={onClick}
            className={`flex-1 min-w-[140px] rounded-xl border px-3 py-2 text-left text-sm transition ${
                activo
                    ? "border-emerald-400 bg-emerald-50 text-emerald-800 dark:border-emerald-500 dark:bg-emerald-900/30 dark:text-emerald-200"
                    : "border-slate-200 bg-white text-slate-700 hover:border-slate-300 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200"
            }`}
        >
            <div className="font-semibold">{label}</div>
            <div className="text-xs opacity-80">{descripcion}</div>
        </button>
    );
}

// ──────────────────────────────────────────────────────────────────────────────
// ExportarProductosPanel (sin cambios funcionales)
// ──────────────────────────────────────────────────────────────────────────────

type ExportarEstado = "IDLE" | "EN_PROCESO" | "COMPLETADO" | "ERROR";

function ExportarProductosPanel() {
    const [estado, setEstado] = useState<ExportarEstado>("IDLE");
    const [mensaje, setMensaje] = useState<string | null>(null);
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
            // DUX informa el estado del proceso: "PENDIENTE" (sigue) o "FINALIZADO" (con `errores` si los hubo).
            // No expone progreso granular (procesados/total), así que el avance se muestra indeterminado.
            if (data.estado === "FINALIZADO") {
                clearInterval(intervalRef.current!);
                intervalRef.current = null;
                const errores: string[] = data.errores ?? [];
                if (errores.length) {
                    setEstado("ERROR");
                    setMensaje(`Exportacion finalizada con ${errores.length} error(es): ${errores.join("; ")}`);
                } else {
                    setEstado("COMPLETADO");
                    setMensaje("Exportacion completada.");
                }
            }
            // Otro estado (PENDIENTE): el proceso sigue; el intervalo vuelve a consultar.
        } catch (err: unknown) {
            clearInterval(intervalRef.current!);
            intervalRef.current = null;
            setEstado("ERROR");
            setMensaje("Error al consultar el estado: " + (getErrorMessage(err, "desconocido")));
        }
    };

    const handleEjecutar = async () => {
        if (!(await confirmDialog({ title: "Confirmar", message: "Iniciar la exportacion de productos a DUX? Este proceso puede tardar varios minutos.", confirmText: "Iniciar" }))) return;
        setEstado("EN_PROCESO");
        setMensaje(null);
        idProcesoRef.current = null;

        try {
            const res = await fetchAPI(`${API_BASE_URL}/api/dux/exportar-productos`, { method: "POST" });
            const data = await res.json();
            idProcesoRef.current = data.idProceso ?? data.id ?? null;
            if (idProcesoRef.current === null) throw new Error("No se recibio idProceso");
            intervalRef.current = setInterval(consultarEstado, 3000);
        } catch (err: unknown) {
            setEstado("ERROR");
            setMensaje("No se pudo iniciar la exportacion: " + (getErrorMessage(err, "desconocido")));
        }
    };

    const enProceso = estado === "EN_PROCESO";
    const completado = estado === "COMPLETADO";
    const error = estado === "ERROR";

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

            {enProceso && (
                <p className="text-sm text-gray-500 dark:text-slate-400">Procesando exportación en DUX… (puede tardar varios minutos)</p>
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

// ──────────────────────────────────────────────────────────────────────────────
// Página
// ──────────────────────────────────────────────────────────────────────────────

export default function DuxOperacionesPage() {
    const [bannerTrigger, setBannerTrigger] = useState(0);
    const refrescarBanner = useCallback(() => setBannerTrigger((t) => t + 1), []);

    return (
        <main className="p-4 bg-gray-50 dark:bg-slate-900 min-h-0 flex flex-col gap-6 overflow-auto">
            <div className="flex items-center gap-3">
                <ServerStackIcon className="w-8 h-8 text-gray-600 dark:text-slate-400" />
                <div>
                    <h1 className="text-3xl font-bold text-gray-800 dark:text-slate-100">Operaciones DUX</h1>
                    <p className="text-sm text-gray-500 dark:text-slate-400">
                        Importación automática (por horarios) y manual desde DUX a la tabla <code>productos</code>, más la exportación del catálogo hacia DUX.
                    </p>
                </div>
            </div>

            <UltimaSyncBanner trigger={bannerTrigger} />

            <section className="flex flex-col gap-3">
                <header className="flex items-center gap-2">
                    <CloudArrowDownIcon className="w-5 h-5 text-emerald-600 dark:text-emerald-400" />
                    <h2 className="text-xl font-semibold text-slate-800 dark:text-slate-100">
                        Importación a base de datos local
                    </h2>
                </header>
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                    <SyncManualPanel onComplete={refrescarBanner} />
                    <HorariosEditor />
                </div>
            </section>

            <section className="flex flex-col gap-3">
                <header className="flex items-center gap-2">
                    <ArrowsRightLeftIcon className="w-5 h-5 text-rose-500 dark:text-rose-400" />
                    <h2 className="text-xl font-semibold text-slate-800 dark:text-slate-100">
                        Exportación a DUX
                    </h2>
                </header>
                <ExportarProductosPanel />
            </section>
        </main>
    );
}

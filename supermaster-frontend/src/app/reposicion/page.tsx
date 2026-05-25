"use client";
import { getErrorMessage } from "@/lib/errors";
import { useState, useEffect, useRef } from "react";
import { notificar } from "../utils/notificar";
import Button from "../components/Button/Button";
import { confirmDialog } from "../utils/confirmDialog";
import {
    CheckIcon, ArrowPathIcon, XMarkIcon,
    ArrowDownTrayIcon, DocumentPlusIcon,
    ShieldCheckIcon, CalendarDaysIcon, ScaleIcon,
    BuildingOffice2Icon, MapPinIcon,
} from "@heroicons/react/24/outline";
import {
    calcularReposicionAPI,
    cancelarJobAPI,
    generarOrdenesAPI,
    ajustarPedidosAPI,
    getConfigAPI,
    getEstadoJobAPI,
    getResultadoAPI,
    updateConfigAPI,
    type ReposicionConfigDTO,
    type SugerenciaReposicionDTO,
} from "./reposicionService";
import { formatFechaAR } from "../utils/formatDate";
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { useAuth } from "../context/AuthContext";
import { useProcesoActivo } from "../context/ProcesoActivoContext";
import { parseNumeroAR } from "../utils/parseNumero";

const DEFAULT_CONFIG: ReposicionConfigDTO = {
    mesesCobertura: 3,
    diasPorPeriodo: 30,
    pesoMes1: 0.5,
    pesoMes2: 0.3,
    pesoMes3: 0.2,
    idEmpresaDux: 1,
    idsSucursalDux: [],
};

const sectionCardClass = "rounded-3xl border border-slate-200 bg-white shadow-sm shadow-slate-200/70 dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-black/30";
const fieldLabelClass = "text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500 dark:text-slate-400";
const inputClass = "h-11 w-full rounded-2xl border border-slate-200 bg-white px-3 text-sm text-slate-700 shadow-sm outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-500/20";

export default function ReposicionPage() {
    const { usuario } = useAuth();
    const { refresh: refreshProcesosActivos } = useProcesoActivo();
    const normalizedRole = (usuario.rol || "").trim().toUpperCase();
    const canAccess = normalizedRole === "ADMIN" || normalizedRole === "OPERADOR";
    // --- Config state ---
    const [config, setConfig] = useState<ReposicionConfigDTO>(DEFAULT_CONFIG);
    const [isSavingConfig, setIsSavingConfig] = useState(false);
    const [sucursalInput, setSucursalInput] = useState("");

    // --- Job state ---
    const [jobEstado, setJobEstado] = useState<string>("idle");
    const [jobMensaje, setJobMensaje] = useState<string>("");
    const [jobProcesados, setJobProcesados] = useState(0);
    const [jobTotal, setJobTotal] = useState(0);
    const [isCalculando, setIsCalculando] = useState(false);
    const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

    // --- Results state ---
    const [resultado, setResultado] = useState<{ sugerencias: SugerenciaReposicionDTO[]; advertencias: string[]; totalProductos: number; productosConSugerencia: number } | null>(null);
    const [pedidos, setPedidos] = useState<Record<number, number>>({});
    const [isGenerandoOrdenes, setIsGenerandoOrdenes] = useState(false);
    const [isAjustando, setIsAjustando] = useState(false);

    // --- Downloads state ---
    const [ocId, setOcId] = useState<string>("");
    const [exportandoSugerencias, setExportandoSugerencias] = useState(false);
    const [exportandoOC, setExportandoOC] = useState(false);

    // --- Load config, check job state and existing result on mount ---
    useEffect(() => {
        getConfigAPI()
            .then((c) => {
                setConfig(c);
                setSucursalInput((c.idsSucursalDux || []).join(", "));
            })
            .catch(() => {/* usa defaults */});

        getEstadoJobAPI()
            .then((estadoResp) => {
                setJobEstado(estadoResp.estado);
                if (estadoResp.mensaje) setJobMensaje(estadoResp.mensaje);
                if (estadoResp.procesados !== undefined) setJobProcesados(estadoResp.procesados);
                if (estadoResp.total !== undefined) setJobTotal(estadoResp.total);
                if (estadoResp.estado === "ejecutando") {
                    setIsCalculando(true);
                    startPolling();
                }
            })
            .catch(() => {/* ignora */});

        getResultadoAPI()
            .then((data) => {
                if (data) {
                    setResultado(data);
                    const initialPedidos: Record<number, number> = {};
                    data.sugerencias.forEach((r) => { initialPedidos[r.productoId] = r.pedido; });
                    setPedidos(initialPedidos);
                }
            })
            .catch(() => {/* ignora */});
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // --- Cleanup interval ---
    useEffect(() => {
        return () => { if (intervalRef.current) clearInterval(intervalRef.current); };
    }, []);

    // --- Poll estado ---
    const startPolling = () => {
        if (intervalRef.current) clearInterval(intervalRef.current);
        intervalRef.current = setInterval(async () => {
            try {
                const estadoResp = await getEstadoJobAPI();
                setJobEstado(estadoResp.estado);
                if (estadoResp.mensaje) setJobMensaje(estadoResp.mensaje);
                if (estadoResp.procesados !== undefined) setJobProcesados(estadoResp.procesados);
                if (estadoResp.total !== undefined) setJobTotal(estadoResp.total);

                if (estadoResp.estado === "completado") {
                    clearInterval(intervalRef.current!);
                    intervalRef.current = null;
                    setIsCalculando(false);
                    const data = await getResultadoAPI();
                    if (data) {
                        setResultado(data);
                        const initialPedidos: Record<number, number> = {};
                        data.sugerencias.forEach((r) => { initialPedidos[r.productoId] = r.pedido; });
                        setPedidos(initialPedidos);
                    }
                } else if (estadoResp.estado === "cancelado" || estadoResp.estado === "error") {
                    clearInterval(intervalRef.current!);
                    intervalRef.current = null;
                    setIsCalculando(false);
                }
            } catch {
                clearInterval(intervalRef.current!);
                intervalRef.current = null;
                setIsCalculando(false);
                setJobEstado("error");
                setJobMensaje("Error al consultar el estado del proceso.");
            }
        }, 3000);
    };

    // --- Handlers ---
    const handleSaveConfig = async () => {
        try {
            setIsSavingConfig(true);
            const ids = sucursalInput.split(",").map((s) => parseInt(s.trim(), 10)).filter((n) => !isNaN(n) && n > 0);
            const updated = await updateConfigAPI({ ...config, idsSucursalDux: ids });
            setConfig(updated);
            setSucursalInput((updated.idsSucursalDux || []).join(", "));
            notificar.success("Configuración guardada.");
        } catch (e: unknown) {
            notificar.error("Error al guardar configuración: " + getErrorMessage(e));
        } finally {
            setIsSavingConfig(false);
        }
    };

    const handleCalcular = async () => {
        if (!(await confirmDialog({ title: "Confirmar", message: "¿Iniciar el cálculo de reposición? Este proceso puede tardar varios minutos. Se borrarán los resultados anteriores.", confirmText: "Iniciar" }))) return;
        try {
            setIsCalculando(true);
            setJobEstado("idle");
            setJobMensaje("");
            setJobProcesados(0);
            setJobTotal(0);
            setResultado(null);
            setPedidos({});
            await calcularReposicionAPI();
            setJobEstado("ejecutando");
            startPolling();
        } catch (e: unknown) {
            setIsCalculando(false);
            notificar.error("Error al iniciar el cálculo: " + getErrorMessage(e));
        }
    };

    const handleCancelar = async () => {
        try {
            await cancelarJobAPI();
            if (intervalRef.current) clearInterval(intervalRef.current);
            intervalRef.current = null;
            setIsCalculando(false);
            setJobEstado("cancelado");
            refreshProcesosActivos();
            setTimeout(refreshProcesosActivos, 3000);
            setTimeout(refreshProcesosActivos, 8000);
        } catch (e: unknown) {
            notificar.error("Error al cancelar: " + getErrorMessage(e));
        }
    };

    const handlePedidoChange = (productoId: number, value: string) => {
        const num = value === "" ? 0 : Number(value);
        setPedidos((prev) => ({ ...prev, [productoId]: num }));
    };

    const handleAjustar = async () => {
        if (!resultado) return;
        try {
            setIsAjustando(true);
            const ajustes = resultado.sugerencias.map((r) => ({
                productoId: r.productoId,
                pedido: pedidos[r.productoId] ?? r.pedido,
            }));
            const nuevoResultado = await ajustarPedidosAPI(ajustes);
            setResultado(nuevoResultado);
            const updatedPedidos: Record<number, number> = {};
            nuevoResultado.sugerencias.forEach((r) => { updatedPedidos[r.productoId] = r.pedido; });
            setPedidos(updatedPedidos);
            notificar.success("Pedidos ajustados correctamente.");
        } catch (e: unknown) {
            notificar.error("Error al ajustar pedidos: " + getErrorMessage(e));
        } finally {
            setIsAjustando(false);
        }
    };

    const handleGenerarOrdenes = async () => {
        if (!(await confirmDialog({ title: "Confirmar", message: "¿Generar órdenes de compra a partir de los pedidos actuales? Se crearán órdenes en el sistema para todos los productos con pedido > 0.", confirmText: "Generar" }))) return;
        try {
            setIsGenerandoOrdenes(true);
            await generarOrdenesAPI();
            notificar.success("Órdenes de compra generadas correctamente.");
        } catch (e: unknown) {
            notificar.error("Error al generar órdenes: " + getErrorMessage(e));
        } finally {
            setIsGenerandoOrdenes(false);
        }
    };

    const sugerencias = resultado?.sugerencias ?? [];

    const descargarBlob = async (url: string, fallbackName: string) => {
        const res = await fetchAPI(url);
        const blob = await res.blob();
        const blobUrl = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = blobUrl;
        const disposition = res.headers.get("Content-Disposition");
        const match = disposition?.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
        a.download = match?.[1]?.replace(/['"]/g, "") || fallbackName;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(blobUrl);
    };

    const handleDescargarSugerencias = async () => {
        setExportandoSugerencias(true);
        try {
            await descargarBlob(`${API_BASE_URL}/api/reposiciones/resultado/excel`, "sugerencias.xlsx");
            notificar.success("Archivo descargado.");
        } catch (e: unknown) { notificar.error(getErrorMessage(e, "Error al descargar")); }
        finally { setExportandoSugerencias(false); }
    };

    const handleDescargarOC = async () => {
        if (!ocId.trim()) return;
        setExportandoOC(true);
        try {
            await descargarBlob(`${API_BASE_URL}/api/reposiciones/resultado/excel/oc/${ocId.trim()}`, `oc-${ocId.trim()}.xlsx`);
            notificar.success("Archivo descargado.");
        } catch (e: unknown) { notificar.error(getErrorMessage(e, "Error al descargar")); }
        finally { setExportandoOC(false); }
    };

    return !canAccess ? (
        <main className="p-6 bg-gray-50 min-h-0 flex flex-col overflow-hidden dark:bg-slate-900">
            <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-amber-800 dark:border-amber-800 dark:bg-amber-500/10 dark:text-amber-200">
                Solo administradores y operadores pueden acceder a Reposición.
            </div>
        </main>
    ) : (
        <main className="flex flex-col gap-6 bg-slate-50 p-4 md:p-6 dark:bg-slate-900">
            {/* Header */}
            <div className="rounded-3xl border border-slate-200 bg-white px-5 py-4 shadow-sm shadow-slate-200/70 dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-black/30">
                <h1 className="inline-flex items-center gap-3 text-3xl font-bold leading-none text-slate-800 dark:text-slate-100">
                    <ArrowPathIcon className="h-8 w-8 text-slate-500 dark:text-slate-400" />
                    Reposición de Stock
                </h1>
                <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
                    Calculá cantidades sugeridas y generá órdenes de compra automáticamente.
                </p>
                <div className="mt-4 flex flex-wrap gap-2">
                    <span className="rounded-full border border-blue-200 bg-blue-50 px-3 py-1 text-xs font-medium text-blue-700 dark:border-blue-500/30 dark:bg-blue-500/15 dark:text-blue-200">
                        Cálculo ponderado por ventas recientes
                    </span>
                    <span className="rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700 dark:border-emerald-500/30 dark:bg-emerald-500/15 dark:text-emerald-200">
                        Generación automática de OC
                    </span>
                </div>
            </div>

            {/* Sección 1 - Configuración */}
            <section className={`${sectionCardClass} p-5 md:p-6`}>
                <div className="mb-5 flex flex-col gap-1">
                    <h2 className="text-xl font-semibold text-slate-800 dark:text-slate-100">Configuración</h2>
                    <p className="text-sm text-slate-500 dark:text-slate-400">
                        Ajustá la lógica base del cálculo y la integración con DUX antes de iniciar el proceso.
                    </p>
                </div>
                {/* Cálculo de demanda */}
                <div className="mb-4">
                    <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-200 mb-1">Cálculo de demanda</h3>
                    <p className="text-xs text-slate-400 dark:text-slate-500">
                        Se analizan los últimos {config.diasPorPeriodo * 3} días de ventas divididos en 3 períodos de {config.diasPorPeriodo} días. Cada peso determina cuánta importancia tiene ese período en el promedio.
                    </p>
                </div>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-5">
                    <label className="flex flex-col gap-1">
                        <span className={`${fieldLabelClass} flex items-center gap-1.5`}><ShieldCheckIcon className="w-3.5 h-3.5" /> Meses cobertura</span>
                        <span className="text-[11px] text-slate-400 dark:text-slate-500">Stock objetivo en meses</span>
                        <input type="number" min={1} className={inputClass}
                            value={config.mesesCobertura}
                            onChange={(e) => setConfig((p) => ({ ...p, mesesCobertura: Number(e.target.value) }))} />
                    </label>
                    <label className="flex flex-col gap-1">
                        <span className={`${fieldLabelClass} flex items-center gap-1.5`}><CalendarDaysIcon className="w-3.5 h-3.5" /> Días por período</span>
                        <span className="text-[11px] text-slate-400 dark:text-slate-500">Duración de cada período ({config.diasPorPeriodo * 3} días totales)</span>
                        <input type="number" min={1} className={inputClass}
                            value={config.diasPorPeriodo}
                            onChange={(e) => setConfig((p) => ({ ...p, diasPorPeriodo: Number(e.target.value) }))} />
                    </label>
                    <label className="flex flex-col gap-1">
                        <span className={`${fieldLabelClass} flex items-center gap-1.5`}><ScaleIcon className="w-3.5 h-3.5" /> Peso últimos {config.diasPorPeriodo} días</span>
                        <span className="text-[11px] text-slate-400 dark:text-slate-500">Período más reciente (0 a 1)</span>
                        <input type="text" inputMode="decimal" className={inputClass}
                            value={config.pesoMes1}
                            onChange={(e) => { const v = parseNumeroAR(e.target.value); if (v !== null) setConfig((p) => ({ ...p, pesoMes1: v })); }} />
                    </label>
                    <label className="flex flex-col gap-1">
                        <span className={`${fieldLabelClass} flex items-center gap-1.5`}><ScaleIcon className="w-3.5 h-3.5" /> Peso {config.diasPorPeriodo}-{config.diasPorPeriodo * 2} días atrás</span>
                        <span className="text-[11px] text-slate-400 dark:text-slate-500">Período intermedio (0 a 1)</span>
                        <input type="text" inputMode="decimal" className={inputClass}
                            value={config.pesoMes2}
                            onChange={(e) => { const v = parseNumeroAR(e.target.value); if (v !== null) setConfig((p) => ({ ...p, pesoMes2: v })); }} />
                    </label>
                    <label className="flex flex-col gap-1">
                        <span className={`${fieldLabelClass} flex items-center gap-1.5`}><ScaleIcon className="w-3.5 h-3.5" /> Peso {config.diasPorPeriodo * 2}-{config.diasPorPeriodo * 3} días atrás</span>
                        <span className="text-[11px] text-slate-400 dark:text-slate-500">Período más antiguo (0 a 1)</span>
                        <input type="text" inputMode="decimal" className={inputClass}
                            value={config.pesoMes3}
                            onChange={(e) => { const v = parseNumeroAR(e.target.value); if (v !== null) setConfig((p) => ({ ...p, pesoMes3: v })); }} />
                    </label>
                </div>

                {/* Integración DUX */}
                <div className="mt-6 mb-4">
                    <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-200 mb-1">Integración DUX</h3>
                    <p className="text-xs text-slate-400 dark:text-slate-500">
                        Datos de conexión con el ERP para obtener stock y ventas.
                    </p>
                </div>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                    <label className="flex flex-col gap-1">
                        <span className={`${fieldLabelClass} flex items-center gap-1.5`}><BuildingOffice2Icon className="w-3.5 h-3.5" /> ID Empresa Dux</span>
                        <input type="number" min={1} className={inputClass}
                            value={config.idEmpresaDux}
                            onChange={(e) => setConfig((p) => ({ ...p, idEmpresaDux: Number(e.target.value) }))} />
                    </label>
                    <label className="flex flex-col gap-1">
                        <span className={`${fieldLabelClass} flex items-center gap-1.5`}><MapPinIcon className="w-3.5 h-3.5" /> IDs Sucursal Dux</span>
                        <input type="text" className={inputClass}
                            value={sucursalInput}
                            onChange={(e) => setSucursalInput(e.target.value)}
                            placeholder="1, 2, 3" />
                        <span className="text-[11px] text-slate-400 dark:text-slate-500">Separadas por coma</span>
                    </label>
                </div>
                <div className="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-5 dark:border-slate-700">
                    <p className="text-sm text-slate-500 dark:text-slate-400">
                        El cálculo usará esta configuración hasta que la vuelvas a guardar.
                    </p>
                    <Button text={isSavingConfig ? "Guardando..." : "Guardar configuración"} variant="dark"
                        onClick={handleSaveConfig} disabled={isSavingConfig}>
                        <CheckIcon className="w-4 h-4" />
                    </Button>
                </div>
            </section>

            {/* Sección 2 - Control de Cálculo */}
            <section className={`${sectionCardClass} p-5 md:p-6`}>
                <div className="mb-5 flex flex-col gap-1">
                    <h2 className="text-xl font-semibold text-slate-800 dark:text-slate-100">Control de cálculo</h2>
                    <p className="text-sm text-slate-500 dark:text-slate-400">
                        Ejecutá el proceso, seguí su avance en tiempo real y cancelalo si necesitás rehacer parámetros.
                    </p>
                </div>

                {(isCalculando || jobEstado === "ejecutando") ? (
                    <div className="rounded-2xl border border-blue-100 bg-blue-50/60 p-4 dark:border-blue-500/30 dark:bg-blue-500/10">
                        <div className="flex flex-wrap items-center gap-4">
                            <svg className="animate-spin h-5 w-5 text-blue-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                            </svg>
                            <div className="min-w-[12rem]">
                                <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">Calculando reposición</p>
                                <p className="text-xs text-slate-500 dark:text-slate-400">El proceso puede tardar varios minutos según el volumen.</p>
                            </div>
                            <Button text="Cancelar" variant="danger" onClick={handleCancelar}>
                                <XMarkIcon className="w-4 h-4" />
                            </Button>
                        </div>
                        <div className="mt-4 flex flex-col gap-3">
                        {jobTotal > 0 && (
                            <div>
                                <div className="mb-1 flex justify-between text-xs text-slate-500 dark:text-slate-400">
                                    <span>{jobProcesados} / {jobTotal} procesados</span>
                                    <span>{Math.min(Math.round((jobProcesados / jobTotal) * 100), 100)}%</span>
                                </div>
                                <div className="h-3 w-full overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
                                    <div className="h-3 rounded-full bg-blue-600 transition-all duration-500" style={{ width: `${Math.min(Math.round((jobProcesados / jobTotal) * 100), 100)}%` }} />
                                </div>
                            </div>
                        )}
                        {jobMensaje && (
                            <p className="text-xs text-slate-500 dark:text-slate-400">{jobMensaje}</p>
                        )}
                        </div>
                    </div>
                ) : (
                    <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-4 dark:border-slate-700 dark:bg-slate-900/70">
                        <div>
                            <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">Proceso listo para ejecutarse</p>
                            <p className="text-xs text-slate-500 dark:text-slate-400">Al iniciar se reemplazan los resultados anteriores por el nuevo cálculo.</p>
                        </div>
                        <Button text="Calcular reposición" variant="dark" onClick={handleCalcular}>
                            <ArrowPathIcon className="w-4 h-4" />
                        </Button>
                    </div>
                )}

                {jobEstado === "error" && (
                    <div className="mt-4 rounded-2xl border border-red-200 bg-red-50 p-3 text-sm text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-200">
                        {jobMensaje || "Ocurrió un error durante el cálculo."}
                    </div>
                )}

                {jobEstado === "cancelado" && (
                    <div className="mt-4 rounded-2xl border border-yellow-200 bg-yellow-50 p-3 text-sm text-yellow-700 dark:border-yellow-500/30 dark:bg-yellow-500/10 dark:text-yellow-200">
                        Cálculo cancelado.
                    </div>
                )}

                {jobEstado === "completado" && sugerencias.length > 0 && (
                    <p className="mt-4 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700 dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-200">
                        Cálculo completado. {resultado?.productosConSugerencia} productos con sugerencia de {resultado?.totalProductos} totales.
                    </p>
                )}
            </section>

            {/* Sección 3 - Advertencias */}
            {resultado && resultado.advertencias && resultado.advertencias.length > 0 && (
                <section className="rounded-3xl border border-yellow-200 bg-yellow-50 p-4 dark:border-yellow-500/30 dark:bg-yellow-500/10">
                    <h2 className="mb-2 text-sm font-bold text-yellow-800 dark:text-yellow-200">Advertencias</h2>
                    <ul className="list-disc list-inside space-y-1 text-sm text-yellow-700 dark:text-yellow-100">
                        {resultado.advertencias.map((adv, i) => <li key={i}>{adv}</li>)}
                    </ul>
                </section>
            )}

            {/* Sección 4 - Resultados */}
            {sugerencias.length > 0 && (
                <section className={`${sectionCardClass} flex flex-col gap-4 p-5 md:p-6`}>
                    <div className="flex flex-wrap items-start justify-between gap-3">
                        <div>
                            <h2 className="text-xl font-semibold text-slate-800 dark:text-slate-100">
                                Resultados ({sugerencias.length} productos)
                            </h2>
                            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                                Revisá pedidos, guardá ajustes manuales y generá órdenes de compra con la versión actual del cálculo.
                            </p>
                        </div>
                        <div className="flex gap-2 flex-wrap">
                            <Button text={isAjustando ? "Ajustando..." : "Guardar ajustes"} variant="light"
                                onClick={handleAjustar} disabled={isAjustando}>
                                <CheckIcon className="w-4 h-4" />
                            </Button>
                            <Button text={exportandoSugerencias ? "Descargando..." : "Descargar Excel"} variant="light"
                                onClick={handleDescargarSugerencias} disabled={exportandoSugerencias}>
                                <ArrowDownTrayIcon className="w-4 h-4" />
                            </Button>
                            <Button text={isGenerandoOrdenes ? "Generando..." : "Generar Órdenes de Compra"}
                                variant="dark" onClick={handleGenerarOrdenes} disabled={isGenerandoOrdenes}>
                                <DocumentPlusIcon className="w-4 h-4" />
                            </Button>
                        </div>
                    </div>

                    <div className="overflow-hidden rounded-3xl border border-slate-200 dark:border-slate-700">
                    <div className="overflow-x-auto">
                        <table className="w-full text-xs border-collapse">
                            <thead>
                                <tr className="bg-slate-100 text-center text-slate-600 dark:bg-slate-900 dark:text-slate-300">
                                    <th className="border-b border-r border-slate-200 px-2 py-3 font-semibold dark:border-slate-700">SKU</th>
                                    <th className="border-b border-r border-slate-200 px-2 py-3 font-semibold dark:border-slate-700">Cód.Ext</th>
                                    <th className="border-b border-r border-slate-200 px-2 py-3 text-left font-semibold dark:border-slate-700">Descripción</th>
                                    <th className="border-b border-r border-slate-200 px-2 py-3 font-semibold dark:border-slate-700">Proveedor</th>
                                    <th className="border-b border-r border-slate-200 px-2 py-3 font-semibold dark:border-slate-700">UxB</th>
                                    <th className="border-b border-r border-slate-200 px-2 py-3 font-semibold dark:border-slate-700">MOQ</th>
                                    <th className="border-b border-r border-slate-200 px-2 py-3 font-semibold dark:border-slate-700">Stock</th>
                                    <th className="border-b border-r border-slate-200 px-2 py-3 font-semibold dark:border-slate-700">Pend.Cli</th>
                                    <th className="border-b border-r border-slate-200 px-2 py-3 font-semibold dark:border-slate-700">Pend.Prov</th>
                                    <th className="border-b border-r border-slate-200 px-2 py-3 font-semibold dark:border-slate-700">Saldo</th>
                                    <th className="border-b border-r border-slate-200 px-2 py-3 font-semibold dark:border-slate-700">V.M1</th>
                                    <th className="border-b border-r border-slate-200 px-2 py-3 font-semibold dark:border-slate-700">V.M2</th>
                                    <th className="border-b border-r border-slate-200 px-2 py-3 font-semibold dark:border-slate-700">V.M3</th>
                                    <th className="border-b border-r border-slate-200 px-2 py-3 font-semibold dark:border-slate-700">Prom/mes</th>
                                    <th className="border-b border-r border-slate-200 px-2 py-3 font-semibold dark:border-slate-700">Prom/día</th>
                                    <th className="border-b border-r border-slate-200 px-2 py-3 font-semibold dark:border-slate-700">P.Reorden</th>
                                    <th className="border-b border-r border-slate-200 px-2 py-3 font-semibold dark:border-slate-700">Sugerido</th>
                                    <th className="border-b border-r border-slate-200 px-2 py-3 font-semibold dark:border-slate-700">Pedido</th>
                                    <th className="border-b border-r border-slate-200 px-2 py-3 font-semibold dark:border-slate-700">Últ.Compra</th>
                                    <th className="border-b border-r border-slate-200 px-2 py-3 font-semibold dark:border-slate-700">Últ.Cant.</th>
                                    <th className="border-b border-r border-slate-200 px-2 py-3 font-semibold dark:border-slate-700">Tag</th>
                                    <th className="border-b border-slate-200 px-2 py-3 font-semibold dark:border-slate-700">Urgente</th>
                                </tr>
                            </thead>
                            <tbody>
                                {sugerencias.map((r) => (
                                    <tr key={r.productoId}
                                        className={`transition-colors odd:bg-white even:bg-slate-50/70 hover:bg-blue-50/70 dark:odd:bg-slate-800 dark:even:bg-slate-800/70 dark:hover:bg-blue-500/10 ${r.urgente ? "!bg-red-50/80 dark:!bg-red-500/10" : ""}`}>
                                        <td className="border-b border-r border-slate-200 px-2 py-2 text-center font-mono text-slate-700 dark:border-slate-700 dark:text-slate-200">{r.sku}</td>
                                        <td className="border-b border-r border-slate-200 px-2 py-2 text-center text-slate-500 dark:border-slate-700 dark:text-slate-400">{r.codExt ?? "-"}</td>
                                        <td className="max-w-xs truncate border-b border-r border-slate-200 px-2 py-2 text-slate-800 dark:border-slate-700 dark:text-slate-100">{r.descripcion}</td>
                                        <td className="border-b border-r border-slate-200 px-2 py-2 text-center text-slate-600 dark:border-slate-700 dark:text-slate-300">{r.proveedorNombre ?? "-"}</td>
                                        <td className="border-b border-r border-slate-200 px-2 py-2 text-right text-slate-500 dark:border-slate-700 dark:text-slate-400">{r.uxb ?? "-"}</td>
                                        <td className="border-b border-r border-slate-200 px-2 py-2 text-right text-slate-500 dark:border-slate-700 dark:text-slate-400">{r.moq ?? "-"}</td>
                                        <td className="border-b border-r border-slate-200 px-2 py-2 text-right font-medium text-slate-700 dark:border-slate-700 dark:text-slate-200">{r.stockActual}</td>
                                        <td className="border-b border-r border-slate-200 px-2 py-2 text-right text-slate-500 dark:border-slate-700 dark:text-slate-400">{r.pendienteClientes}</td>
                                        <td className="border-b border-r border-slate-200 px-2 py-2 text-right text-slate-500 dark:border-slate-700 dark:text-slate-400">{r.pendienteProveedores}</td>
                                        <td className="border-b border-r border-slate-200 px-2 py-2 text-right font-medium text-slate-700 dark:border-slate-700 dark:text-slate-200">{r.saldoDisponible}</td>
                                        <td className="border-b border-r border-slate-200 px-2 py-2 text-right text-slate-500 dark:border-slate-700 dark:text-slate-400">{r.ventasMes1}</td>
                                        <td className="border-b border-r border-slate-200 px-2 py-2 text-right text-slate-500 dark:border-slate-700 dark:text-slate-400">{r.ventasMes2}</td>
                                        <td className="border-b border-r border-slate-200 px-2 py-2 text-right text-slate-500 dark:border-slate-700 dark:text-slate-400">{r.ventasMes3}</td>
                                        <td className="border-b border-r border-slate-200 px-2 py-2 text-right font-medium text-slate-700 dark:border-slate-700 dark:text-slate-200">{r.promedioVentas.toLocaleString("es-AR", { minimumFractionDigits: 1, maximumFractionDigits: 1 })}</td>
                                        <td className="border-b border-r border-slate-200 px-2 py-2 text-right text-slate-500 dark:border-slate-700 dark:text-slate-400">{r.promedioDiario != null ? r.promedioDiario.toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : "-"}</td>
                                        <td className="border-b border-r border-slate-200 px-2 py-2 text-right font-medium text-slate-700 dark:border-slate-700 dark:text-slate-200">{r.puntoReorden}</td>
                                        <td className="border-b border-r border-slate-200 px-2 py-2 text-right font-semibold text-slate-800 dark:border-slate-700 dark:text-slate-100">{r.sugerencia}</td>
                                        <td className="border-b border-r border-slate-200 px-2 py-2 text-right dark:border-slate-700">
                                            <input
                                                type="number" min={0}
                                                className="h-9 w-20 rounded-xl border border-slate-200 bg-white px-2 text-right text-xs text-slate-700 outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-500/20"
                                                value={pedidos[r.productoId] ?? r.pedido}
                                                onChange={(e) => handlePedidoChange(r.productoId, e.target.value)}
                                            />
                                        </td>
                                        <td className="border-b border-r border-slate-200 px-2 py-2 text-center text-slate-500 dark:border-slate-700 dark:text-slate-400">{formatFechaAR(r.ultimaCompraFecha)}</td>
                                        <td className="border-b border-r border-slate-200 px-2 py-2 text-right text-slate-500 dark:border-slate-700 dark:text-slate-400">{r.ultimaCompraCantidad ?? "-"}</td>
                                        <td className="border-b border-r border-slate-200 px-2 py-2 text-center dark:border-slate-700">
                                            {r.tagReposicion ? (
                                                <span className={`rounded-full px-2 py-1 text-[11px] font-bold ${r.tagReposicion === "PRIO" ? "bg-amber-100 text-amber-800 dark:bg-amber-500/20 dark:text-amber-200" : "bg-red-100 text-red-800 dark:bg-red-500/20 dark:text-red-200"}`}>
                                                    {r.tagReposicion}
                                                </span>
                                            ) : "-"}
                                        </td>
                                        <td className="border-b border-slate-200 px-2 py-2 text-center dark:border-slate-700">
                                            {r.urgente ? <span className="rounded-full bg-red-100 px-2 py-1 text-[11px] font-bold text-red-700 dark:bg-red-500/20 dark:text-red-200">Sí</span> : <span className="text-xs text-slate-400 dark:text-slate-500">No</span>}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                    </div>
                </section>
            )}
            {/* Sección - Descargas */}
            <section className={`${sectionCardClass} p-5 md:p-6`}>
                <div className="mb-5 flex flex-col gap-1">
                    <h2 className="text-xl font-semibold text-slate-800 dark:text-slate-100">Descargas</h2>
                    <p className="text-sm text-slate-500 dark:text-slate-400">
                        Exportá el resultado actual o descargá una orden puntual por su identificador.
                    </p>
                </div>
                <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
                    <div className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900/70">
                        <div>
                            <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-100">Sugerencias de Reposición</h3>
                            <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">Descarga el último resultado del cálculo de reposición.</p>
                        </div>
                        <div>
                            <Button text={exportandoSugerencias ? "Descargando..." : "Descargar"} variant="light"
                                disabled={exportandoSugerencias}
                                onClick={handleDescargarSugerencias}>
                                <ArrowDownTrayIcon className="w-4 h-4" />
                            </Button>
                        </div>
                    </div>
                    <div className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900/70">
                        <div>
                            <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-100">Orden de Compra</h3>
                            <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">Descarga una orden de compra específica por ID.</p>
                        </div>
                        <div className="flex flex-wrap gap-2">
                            <input type="number" min="1" placeholder="ID de OC"
                                className={`${inputClass} w-40`}
                                value={ocId} onChange={(e) => setOcId(e.target.value)} />
                            <Button text={exportandoOC ? "..." : "Descargar"} variant="light"
                                onClick={handleDescargarOC} disabled={!ocId.trim() || exportandoOC}>
                                <ArrowDownTrayIcon className="w-4 h-4" />
                            </Button>
                        </div>
                    </div>
                </div>
            </section>
        </main>
    );
}

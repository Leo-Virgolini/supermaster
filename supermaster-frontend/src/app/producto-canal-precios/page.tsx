"use client";
import { getErrorMessage } from "@/lib/errors";
import dynamic from "next/dynamic";
import { useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState, useRef, useCallback } from "react";
import { ComputerDesktopIcon, ArrowPathIcon, XMarkIcon, InformationCircleIcon, ChevronDownIcon } from "@heroicons/react/24/outline";
import Modal from "../components/Modal/Modal";
import Button from "../components/Button/Button";
import { useProductoCanalPrecios } from "./useProductoCanalPrecios";
import { FormulaCalculo } from "./types";
import { calcularPreciosAPI, getFormulaAPI, getProductoCanalPreciosAPI } from "./productoCanalPreciosService";
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { confirmDialog } from "../utils/confirmDialog";
import { useProcesoActivo } from "../context/ProcesoActivoContext";
import { type SortingState } from "@tanstack/react-table";
import { notificar } from "../utils/notificar";
import { toast } from "sonner";
import { updateProductoAPI } from "../productos/productosService";
import { getProductoMargenAPI, updateProductoMargenAPI } from "../productos/productoMargenService";
import { asignarPrecioInfladoAPI } from "../productos/productoSubRecursosService";


const MonitorPrecios = dynamic(() => import("./MonitorPrecios"), {
    loading: () => (
        <div className="flex h-full items-center justify-center rounded-xl border border-slate-200 bg-white text-sm text-slate-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400">
            Cargando monitor...
        </div>
    ),
});

import { SORT_FIELD_MAP } from "./sortMap";
import { construirFiltroCanal, cuotasDeshabilitadas } from "./canalFiltro";

export default function ProductoCanalPreciosPage() {
    const { procesos, tieneConflicto } = useProcesoActivo();
    // Estados básicos
    const [pageIndex, setPageIndex] = useState(0);
    const [pageSize, setPageSize] = useState(() => {
        if (typeof window === "undefined") return 100;
        const saved = localStorage.getItem("pageSize_v2_monitor-precios");
        return saved ? Number(saved) : 100;
    });

    useEffect(() => {
        try {
            localStorage.setItem("pageSize_v2_monitor-precios", String(pageSize));
        } catch { /* QuotaExceededError o modo privado — ignorar, no es crítico */ }
    }, [pageSize]);
    // Filtro inicial por URL (?q=SKU o ?search=SKU), p. ej. al venir desde el
    // botón "Ver precios en Monitor" del detalle de producto.
    const searchParams = useSearchParams();
    const [search, setSearch] = useState(() => searchParams.get("q") ?? searchParams.get("search") ?? "");
    const [sorting, setSorting] = useState<SortingState>([]);

    const mappedSorting = useMemo(
        () => sorting.map((s) => ({ ...s, id: SORT_FIELD_MAP[s.id] ?? s.id })),
        [sorting]
    );
    // Canales: null = aún no inicializado, [] = todos, [n…] = canales específicos.
    const [selectedCanales, setSelectedCanales] = useState<number[] | null>(null);
    // Cuotas: null = no inicializado, "all" = todas, number = cuota específica.
    const [selectedCuotas, setSelectedCuotas] = useState<number | "all" | null>(null);

    // Estado loading por fila (Recalcular individual)
    const [calcLoading, setCalcLoading] = useState<Record<number, boolean>>({});
    // Si está corriendo el recálculo masivo, deshabilitamos los recálculos
    // por fila. Lo derivamos del contexto SSE — es la única fuente de verdad.
    const recalculoMasivoActivo = procesos.some((p) => p.proceso === "recalculo-precios");

    const [ayudaAbierta, setAyudaAbierta] = useState(false);


    // Estado modal fórmula
    const [formulaModal, setFormulaModal] = useState<{
        isOpen: boolean;
        loading: boolean;
        data: FormulaCalculo | null;
    }>({ isOpen: false, loading: false, data: null });

    const filters = useMemo(() => {
        const f: Record<string, any> = {};
        // null = no inicializado todavía → _ready=false para que el hook no fetche
        if (selectedCanales === null) { f._ready = false; return f; }
        // [] = todos (sin filtro); 1 canal → canalId; 2+ → canalIds.
        Object.assign(f, construirFiltroCanal(selectedCanales));
        // Las cuotas solo aplican con 0 o 1 canal (con 2+ se fuerzan a "Todas").
        if (!cuotasDeshabilitadas(selectedCanales) && selectedCuotas != null && selectedCuotas !== "all") {
            f.cuotas = selectedCuotas;
        }
        return f;
    }, [selectedCanales, selectedCuotas]);

    // Hook de Datos
    const {
        data,
        totalRecords,
        pageCount,
        isLoading,
        error,
        refetch,
        refreshRowLocal,
    } = useProductoCanalPrecios(pageIndex, pageSize, search, filters, mappedSorting);

    // Refetch automático cuando termina un recálculo en background.
    // El backend procesa el plan scoped (o el masivo) en async y libera el lock global
    // al terminar. Lo detectamos viendo cuándo un proceso de recálculo desaparece de la
    // lista de procesos activos y disparamos refetch para que la tabla muestre los
    // nuevos precios sin que el usuario tenga que recargar la página.
    const RECALC_PROCESOS = useMemo(() => new Set([
        "recalculo-pendiente-scoped",
        "recalculo-precios",
        "recalculo-canal",
    ]), []);
    const recalcActivoRef = useRef(false);
    useEffect(() => {
        const algunoActivo = procesos.some((p) => RECALC_PROCESOS.has(p.proceso));
        if (recalcActivoRef.current && !algunoActivo) {
            // Pasó de "había recálculo" a "ya no hay" → refrescar la tabla.
            refetch();
        }
        recalcActivoRef.current = algunoActivo;
    }, [procesos, RECALC_PROCESOS, refetch]);

    // Los callbacks van envueltos en useCallback con refs estables para data y tieneConflicto.
    // Sin esto, cualquier re-render de page.tsx (ej. al cambiar calcLoading o al llegar un
    // evento del SSE de procesos activos) generaba nuevas referencias de callback, lo que
    // hacía que MonitorPrecios reconstruyera allColumns/columns, TanStack re-montaba las
    // celdas y la edición en curso se cerraba.
    const dataRef = useRef(data);
    dataRef.current = data;
    const tieneConflictoRef = useRef(tieneConflicto);
    tieneConflictoRef.current = tieneConflicto;

    // Recalcular precio de un producto individual.
    // Usamos refetch (no refreshRowLocal) porque el recálculo cambia valores
    // ordenables (pvp, margen, ingreso neto, etc.). Si insertamos las filas frescas
    // en la posición vieja, el orden global queda inconsistente con el sort actual.
    const handleRecalcular = useCallback(async (productoId: number) => {
        const conflicto = tieneConflictoRef.current("recalculo-precios");
        if (conflicto) {
            notificar.warning(`No se puede recalcular: hay otro proceso en curso (${conflicto.descripcion})`);
            return;
        }
        const sku = dataRef.current.find((p) => p.id === productoId)?.sku ?? productoId;
        setCalcLoading((prev) => ({ ...prev, [productoId]: true }));
        try {
            await calcularPreciosAPI(productoId);
            await refetch();
            notificar.success(`Precios recalculados para ${sku}`);
        } catch (e: unknown) {
            notificar.error(getErrorMessage(e, "Error al recalcular"));
        } finally {
            setCalcLoading((prev) => ({ ...prev, [productoId]: false }));
        }
    }, [refetch]);


    // Abrir modal de fórmula
    const handleVerFormula = useCallback(async (productoId: number, canalId: number, cuotas: number) => {
        setFormulaModal({ isOpen: true, loading: true, data: null });
        try {
            const formula = await getFormulaAPI(productoId, canalId, cuotas);
            setFormulaModal({ isOpen: true, loading: false, data: formula });
        } catch (e: unknown) {
            notificar.error(getErrorMessage(e, "Error al obtener la fórmula"));
            setFormulaModal({ isOpen: false, loading: false, data: null });
        }
    }, []);

    // Edición inline de campos desde el monitor.
    // El update se espera (para que EditableCell marque success rápido).
    // El recálculo + refetch corre en background para que el highlight emerald
    // aparezca inmediatamente y el usuario lo perciba.
    const handleEditField = useCallback(async (productoId: number, canalId: number, field: string, value: number | string) => {
        const numValue = value === "" ? null : Number(value);
        const sku = dataRef.current.find((p) => p.id === productoId)?.sku ?? productoId;
        try {
            if (field === "costo" || field === "iva") {
                await updateProductoAPI(productoId, { [field]: numValue }, "MONITOR_PRECIOS");
            } else {
                const current = await getProductoMargenAPI(productoId);
                await updateProductoMargenAPI(productoId, {
                    margenMinorista: current?.margenMinorista ?? null,
                    margenMayorista: current?.margenMayorista ?? null,
                    observaciones: current?.observaciones ?? null,
                    [field]: numValue,
                });
            }
            notificar.success(`SKU ${sku} actualizado`);
        } catch (e: unknown) {
            notificar.error(getErrorMessage(e, "Error al actualizar"));
            throw e;
        }
        // El PATCH solo MARCA el cambio como pendiente — el recálculo no corre hasta que
        // el usuario aprete Aplicar en el banner global. Refrescamos la fila para que el
        // costo/iva/margen recién editado se vea, aunque los precios calculados sigan
        // siendo los viejos hasta el próximo Apply (el banner avisa con cardinal).
        refreshRowLocal(productoId).catch((e: any) => {
            notificar.error(`Error refrescando SKU ${sku}: ${getErrorMessage(e, "")}`);
        });
    }, [refreshRowLocal]);

    const handleEditReglaInflada = useCallback(async (productoId: number, canalId: number, precioInfladoId: number | null) => {
        try {
            if (precioInfladoId) {
                await asignarPrecioInfladoAPI(productoId, canalId, precioInfladoId);
            } else {
                const { quitarPrecioInfladoAPI } = await import("../productos/productoSubRecursosService");
                await quitarPrecioInfladoAPI(productoId, canalId);
            }
            await refreshRowLocal(productoId);
            const sku = dataRef.current.find((p) => p.id === productoId)?.sku ?? productoId;
            notificar.success(precioInfladoId ? `SKU ${sku}: regla inflada actualizada` : `SKU ${sku}: regla inflada quitada`);
        } catch (e: unknown) {
            notificar.error(getErrorMessage(e, "Error al actualizar"));
        }
    }, [refreshRowLocal]);

    const anyRowLoading = Object.values(calcLoading).some(Boolean);

    return (
        <main className="p-4 bg-gray-50 min-h-0 flex flex-col overflow-hidden">
            <div className="flex items-center justify-between mb-2">
                <div>
                    <h1 className="text-3xl font-bold text-gray-800 flex items-center gap-2">
                        <ComputerDesktopIcon className="w-8 h-8 text-gray-600" />
                        Monitor de Precios
                    </h1>
                    <p className="text-gray-500 text-sm mt-0.5">Comparador de costos, precios y márgenes por canal.</p>
                </div>
                <RecalculoMasivoButton disabled={anyRowLoading} />
            </div>

            <div className="mb-3 rounded-lg border border-blue-200 bg-blue-50 dark:border-blue-800/60 dark:bg-blue-900/20">
                <button
                    type="button"
                    onClick={() => setAyudaAbierta((v) => !v)}
                    className="flex w-full items-center justify-between gap-2 px-4 py-2 text-left text-sm font-semibold text-blue-900 dark:text-blue-200"
                >
                    <span className="flex items-center gap-2">
                        <InformationCircleIcon className="h-5 w-5" />
                        ¿Para qué sirve el Monitor de Precios?
                    </span>
                    <ChevronDownIcon className={`h-4 w-4 transition-transform ${ayudaAbierta ? "rotate-180" : ""}`} />
                </button>
                {ayudaAbierta && (
                    <div className="border-t border-blue-200 px-4 py-3 text-sm text-blue-900 dark:border-blue-800/60 dark:text-blue-100">
                        <p className="mb-3">
                            Es la <strong>vista única donde se ven todos los precios calculados</strong> de cada producto en cada canal y plan
                            de cuotas, junto con costo, márgenes, ganancia, ingreso neto y descuentos. Es la herramienta principal
                            para verificar resultados después de tocar costos, márgenes o reglas.
                        </p>

                        <div className="mb-3 rounded-md bg-white/60 p-3 text-xs dark:bg-blue-950/40">
                            <p className="mb-2 font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
                                Filtros de la barra superior
                            </p>
                            <ul className="ml-4 list-disc space-y-0.5">
                                <li><strong>Buscador</strong>: por SKU, MLA o nombre del producto.</li>
                                <li><strong>Canal</strong>: filtrá por uno o varios canales (checkboxes) o &quot;Todos&quot;. Marcar &quot;Todos&quot; limpia el resto.</li>
                                <li><strong>Cuotas</strong>: filtra por plan de cuotas. Las opciones dependen del canal seleccionado. Con 2 o más canales seleccionados se fija en &quot;Todas&quot; y se deshabilita.</li>
                                <li><strong>Vista</strong>: alterna entre <em>Rentabilidad</em>, <em>Edición</em>, <em>Descuentos</em> o <em>Completo</em> para mostrar diferentes grupos de columnas.</li>
                            </ul>
                        </div>

                        <div className="mb-3">
                            <p className="mb-1 font-semibold">Acciones por fila:</p>
                            <ul className="ml-4 list-disc space-y-0.5">
                                <li><strong>Editar inline</strong>: hacé clic sobre celdas como <span className="font-mono">Mrg Min</span>, <span className="font-mono">Mrg May</span> o <span className="font-mono">Regla Inflado</span> para modificarlas. Disparan recálculo automático.</li>
                                <li><strong>Fórmula</strong>: abre un modal con el desglose paso a paso del cálculo del PVP final.</li>
                                <li><strong>Recalcular</strong>: fuerza el recálculo del producto en ese canal+cuotas.</li>
                            </ul>
                        </div>

                        <div className="mb-3">
                            <p className="mb-1 font-semibold">Leyenda de colores en márgenes / markup:</p>
                            <ul className="ml-4 list-disc space-y-0.5">
                                <li><span className="text-red-600 dark:text-red-400 font-semibold">&lt; 0%</span> — pérdida (vendés debajo del costo).</li>
                                <li><span className="text-orange-500 font-semibold">0–15%</span> — bajo.</li>
                                <li><span className="text-yellow-600 dark:text-yellow-400 font-semibold">15–25%</span> — moderado.</li>
                                <li><span className="text-green-600 dark:text-green-400 font-semibold">25–40%</span> — bueno.</li>
                                <li><span className="text-emerald-600 dark:text-emerald-400 font-semibold">&gt; 40%</span> — excelente.</li>
                            </ul>
                        </div>

                        <div className="mb-3 rounded-md bg-white/60 p-3 text-xs dark:bg-blue-950/40">
                            <p className="mb-1 font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">Por qué baja el margen al subir cuotas</p>
                            <p>
                                El % de la cuota se traslada al PVP, pero el <strong>IVA y los impuestos sobre PVP escalan con el precio final</strong> y
                                no están cubiertos por el divisor de cuotas. Ese exceso sale de la ganancia. No es un bug — es el efecto matemático
                                de aplicar el IVA sobre un precio mayor. Las columnas <span className="font-mono">c/Desc</span> reflejan el mismo cálculo
                                con descuentos automáticos aplicados (ver <strong>Reglas de Descuento</strong>).
                            </p>
                        </div>

                        <div className="rounded-md border border-amber-300 bg-amber-50 p-2.5 text-xs text-amber-900 dark:border-amber-700/60 dark:bg-amber-900/30 dark:text-amber-100">
                            <strong>Recalcular Todos</strong> (botón azul arriba a la derecha) recalcula todos los productos en todos los canales.
                            Tarda varios minutos y bloquea otros procesos. Usalo solo cuando hayas hecho cambios masivos a costos, márgenes o reglas.
                        </div>
                    </div>
                )}
            </div>

            <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                <MonitorPrecios
                    data={data}
                    isLoading={isLoading}
                    onExportAll={async () => {
                        const sortParam = mappedSorting.length > 0
                            ? mappedSorting.map(s => `${s.id},${s.desc ? "desc" : "asc"}`)
                            : ["id,desc"];

                        // Paginamos por chunks razonables en vez de un fetch único con tope.
                        // 5000 productos por página es un buen balance: pocas requests y
                        // poca memoria por request (cada producto trae canales × cuotas anidados).
                        const PAGE_SIZE = 5000;
                        const toastId = toast.loading("Cargando datos para exportar…");
                        try {
                            const firstPage = await getProductoCanalPreciosAPI(0, PAGE_SIZE, search, filters, sortParam);
                            const total = firstPage.page?.totalElements ?? firstPage.content.length;
                            const totalPages = firstPage.page?.totalPages ?? Math.max(1, Math.ceil(total / PAGE_SIZE));
                            const allData = [...firstPage.content];

                            if (totalPages > 1) {
                                toast.loading(`Cargando 1/${totalPages} (${allData.length.toLocaleString("es-AR")}/${total.toLocaleString("es-AR")} productos)…`, { id: toastId });
                                for (let p = 1; p < totalPages; p++) {
                                    const page = await getProductoCanalPreciosAPI(p, PAGE_SIZE, search, filters, sortParam);
                                    allData.push(...page.content);
                                    toast.loading(`Cargando ${p + 1}/${totalPages} (${allData.length.toLocaleString("es-AR")}/${total.toLocaleString("es-AR")} productos)…`, { id: toastId });
                                }
                            }

                            toast.dismiss(toastId);
                            return allData;
                        } catch (err) {
                            toast.error("Error al cargar datos para exportar", { id: toastId });
                            throw err;
                        }
                    }}
                    pageIndex={pageIndex}
                    pageSize={pageSize}
                    pageCount={pageCount}
                    totalRecords={totalRecords}
                    onPageChange={setPageIndex}
                    onPageSizeChange={setPageSize}
                    search={search}
                    onSearch={(val) => { setSearch(val); setPageIndex(0); }}
                    onRecalcular={handleRecalcular}
                    onVerFormula={handleVerFormula}
                    calcLoading={calcLoading}
                    globalLoading={recalculoMasivoActivo}
                    onCanalChange={(canales) => { setSelectedCanales(canales); setPageIndex(0); }}
                    onCuotasChange={(c) => { setSelectedCuotas(c as any); setPageIndex(0); }}
                    onSortingChange={(s) => { setSorting(s); setPageIndex(0); }}

                    error={error}
                    onEditField={handleEditField}
                    onEditReglaInflada={handleEditReglaInflada}
                />
            </div>

            {/* MODAL FÓRMULA DE CÁLCULO */}
            <Modal
                isOpen={formulaModal.isOpen}
                onClose={() => setFormulaModal({ isOpen: false, loading: false, data: null })}
                title={formulaModal.data
                    ? `Fórmula — ${formulaModal.data.canalNombre} · ${formulaModal.data.descripcionCuotas}`
                    : "Fórmula de cálculo"
                }
            >
                {formulaModal.loading ? (
                    <div className="flex justify-center items-center py-12">
                        <ArrowPathIcon className="w-8 h-8 animate-spin text-blue-500" />
                        <span className="ml-2 text-gray-500">Cargando fórmula...</span>
                    </div>
                ) : formulaModal.data ? (
                    <div className="max-h-[65vh] overflow-y-auto pr-1">
                        <div className="mb-5 p-4 bg-slate-800 dark:bg-slate-900 rounded-xl">
                            <p className="text-[10px] text-blue-400 uppercase font-bold tracking-widest mb-1.5">Formula general</p>
                            <p className="font-mono text-sm text-slate-100 leading-relaxed">{formulaModal.data.formulaGeneral}</p>
                        </div>
                        <div className="space-y-2">
                            {formulaModal.data.pasos.map((paso) => {
                                const u = paso.unidad;
                                const colorClass = u === "porcentaje"
                                    ? "text-blue-700 dark:text-blue-300"
                                    : u === "factor"
                                        ? "text-amber-700 dark:text-amber-300"
                                        : "text-emerald-700 dark:text-emerald-300";
                                const formatted = u === "porcentaje"
                                    ? `${paso.valor.toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`
                                    : u === "factor"
                                        ? `x ${paso.valor.toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 4 })}`
                                        : `$ ${paso.valor.toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
                                return (
                                    <div key={paso.numeroPaso} className="border border-gray-100 dark:border-slate-700 rounded-xl p-3.5 bg-white dark:bg-slate-800/50 hover:border-gray-200 dark:hover:border-slate-600 transition">
                                        <div className="flex justify-between items-start gap-4">
                                            <div className="flex-1 min-w-0">
                                                <div className="flex items-center gap-2 mb-1">
                                                    <span className="inline-flex items-center justify-center w-6 h-6 rounded-full bg-gray-100 dark:bg-slate-700 text-[10px] font-bold text-gray-500 dark:text-slate-400">
                                                        {paso.numeroPaso}
                                                    </span>
                                                    <span className="font-semibold text-sm text-gray-800 dark:text-slate-100">{paso.descripcion}</span>
                                                </div>
                                                <p className="font-mono text-xs text-gray-400 dark:text-slate-500 ml-8 break-all">{paso.formula}</p>
                                                {paso.detalle && (
                                                    <p className="text-xs text-gray-400 dark:text-slate-500 mt-1 ml-8 italic">{paso.detalle}</p>
                                                )}
                                            </div>
                                            <span className={`text-base font-bold shrink-0 ${colorClass}`}>
                                                {formatted}
                                            </span>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                        <div className="mt-4 p-4 bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800 rounded-xl flex justify-between items-center">
                            <span className="font-bold text-emerald-800 dark:text-emerald-300 text-lg">PVP Final</span>
                            <span className="text-2xl font-extrabold text-emerald-700 dark:text-emerald-300">
                                $ {formulaModal.data.resultadoFinal.toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                            </span>
                        </div>
                    </div>
                ) : null}
                <div className="mt-4 flex justify-end border-t pt-4">
                    <Button variant="light" onClick={() => setFormulaModal({ isOpen: false, loading: false, data: null })}>
                        <XMarkIcon className="w-4 h-4" />
                        Cerrar
                    </Button>
                </div>
            </Modal>
        </main>
    );
}

function RecalculoMasivoButton({ disabled }: { disabled: boolean }) {
    const { procesos, tieneConflicto } = useProcesoActivo();
    // Detección directa desde el contexto SSE — sin polling local. El header
    // muestra el progreso (procesados/total/errores), por eso este botón solo
    // necesita: arrancar, cancelar, y reflejar el estado running/idle.
    const masivoActivo = procesos.some((p) => p.proceso === "recalculo-precios");
    const conflicto = tieneConflicto("recalculo-precios");

    // Feedback inmediato al apretar Cancelar (el backend tarda en marcar el
    // proceso como cancelado y el SSE refleja el cambio recién cuando termina
    // el batch en curso). Cuando masivoActivo pasa a false, reseteamos.
    const [cancelando, setCancelando] = useState(false);
    useEffect(() => {
        if (!masivoActivo && cancelando) setCancelando(false);
    }, [masivoActivo, cancelando]);

    const isDisabled = disabled || !!conflicto || masivoActivo;

    const handleClick = async () => {
        if (isDisabled) return;
        const confirmed = await confirmDialog({
            title: "Recalcular todos los precios",
            message: "Esta operación recalcula todos los precios de todos los productos en todos los canales. Puede tardar varios minutos.",
            confirmText: "Recalcular",
            variant: "warning",
        });
        if (!confirmed) return;
        try {
            await fetchAPI(`${API_BASE_URL}/api/precios/recalculo-masivo/iniciar`, { method: "POST" });
            // El badge del header se enciende al instante (ProcesoGlobalService
            // hace broadcast cuando se adquiere el lock). No hace falta poll.
        } catch (e: unknown) {
            notificar.error(e instanceof Error ? getErrorMessage(e) : "Error al iniciar recálculo");
        }
    };

    const handleCancelar = async () => {
        if (cancelando) return;
        setCancelando(true);
        try {
            await fetchAPI(`${API_BASE_URL}/api/precios/recalculo-masivo/cancelar`, { method: "POST" });
            notificar.info("Cancelación solicitada. Esperando que termine el batch en curso...");
        } catch (e: unknown) {
            setCancelando(false);
            notificar.error(e instanceof Error ? getErrorMessage(e) : "Error al cancelar el recálculo");
        }
    };

    if (masivoActivo) {
        return (
            <button
                type="button"
                onClick={handleCancelar}
                disabled={cancelando}
                title="Detener el recálculo en curso"
                className="group inline-flex items-center gap-2 rounded-lg bg-gradient-to-b from-rose-500 to-rose-600 px-4 py-2 text-sm font-semibold text-white shadow-sm ring-1 ring-rose-700/10 transition hover:shadow-md hover:from-rose-500 hover:to-rose-700 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60 disabled:active:scale-100"
            >
                <XMarkIcon className="w-4 h-4" />
                {cancelando ? "Cancelando…" : "Cancelar recálculo"}
            </button>
        );
    }

    const tooltipDisabled = conflicto
        ? `Bloqueado: ${conflicto.descripcion}`
        : disabled
            ? "No tenés permisos para recalcular"
            : "Recalcula todos los productos en todos los canales (puede tardar varios minutos)";

    return (
        <button
            type="button"
            onClick={handleClick}
            disabled={isDisabled}
            title={tooltipDisabled}
            className="group inline-flex items-center gap-2 rounded-lg bg-gradient-to-b from-blue-500 to-blue-600 px-4 py-2 text-sm font-semibold text-white shadow-sm ring-1 ring-blue-700/10 transition hover:shadow-md hover:from-blue-500 hover:to-blue-700 active:scale-[0.98] disabled:cursor-not-allowed disabled:bg-none disabled:bg-slate-200 disabled:text-slate-500 disabled:ring-slate-300 disabled:shadow-none disabled:hover:shadow-none disabled:active:scale-100 dark:disabled:bg-slate-700 dark:disabled:text-slate-400 dark:disabled:ring-slate-600"
        >
            <ArrowPathIcon className="w-4 h-4 transition-transform group-hover:rotate-180 group-disabled:rotate-0" />
            <span>Recalcular todos</span>
            {conflicto && (
                <span className="ml-1 rounded-md bg-amber-400/90 px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide text-amber-900 ring-1 ring-amber-500/30">
                    Bloqueado
                </span>
            )}
        </button>
    );
}

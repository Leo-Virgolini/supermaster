"use client";

import { createContext, useContext, useState, useEffect, useCallback, useRef } from "react";
import { toast } from "sonner";
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { notificar } from "../utils/notificar";
import { getResultadoRecalculoMasivoAPI } from "../producto-canal-precios/productoCanalPreciosService";

interface ProcesoItem {
    proceso: string;
    descripcion: string;
    // Campos de progreso opcionales (vienen del backend cuando el proceso lleva
    // contador interno: recálculo, DUX, ML, automatización, reposición, canal).
    total?: number | null;
    procesados?: number | null;
    exitosos?: number | null;
    errores?: number | null;
    mensaje?: string | null;
}

interface ProcesosActivosResponse {
    activo: boolean;
    procesos: ProcesoItem[];
}

interface StreamPayload {
    activo: boolean;
    procesos: Array<{
        proceso: string;
        descripcion: string;
        total?: number | null;
        procesados?: number | null;
        exitosos?: number | null;
        errores?: number | null;
        mensaje?: string | null;
    }>;
}

interface ProcesoActivoContextType {
    procesos: ProcesoItem[];
    hayProcesoActivo: boolean;
    /** Retorna la ruta de la página del primer proceso activo (para el badge del Header) */
    primerProceso: ProcesoItem | null;
    /** Verifica si un proceso específico tiene conflicto con alguno activo */
    tieneConflicto: (procesoId: string) => ProcesoItem | null;
    /** Fuerza una consulta inmediata a /api/procesos/activo (útil al cancelar). */
    refresh: () => Promise<void>;
}

const PROCESO_RUTAS: Record<string, string> = {
    "automatizacion-precios": "/automatizacion-precios",
    "costo-envio": "/operaciones-ml",
    "costo-venta": "/operaciones-ml",
    "reposicion": "/reposicion",
    "dux-importacion": "/dux",
    "dux-sync-programada": "/dux-operaciones",
    "dux-deudas": "/dux-deudas",
    "recalculo-precios": "/producto-canal-precios",
    "recalculo-canal": "/canal-concepto-cuotas",
};

// Procesos cuyo inicio/fin ya se notifica desde un componente específico
// (típicamente el banner que dispara el proceso). Para evitar toasts duplicados
// (uno del banner + uno del context) los silenciamos acá.
const PROCESOS_SIN_TOAST_GLOBAL = new Set<string>([]);

// Procesos cuyo INICIO ya se notifica desde otro componente (ej. el banner emite
// "Recálculo iniciado…" al apretar Aplicar). Silenciamos solo el toast "Proceso
// iniciado: …" del context para no duplicar, pero dejamos el de cierre — ese sí
// agrega valor (contadores: "X productos recalculados").
const PROCESOS_SIN_TOAST_INICIO = new Set<string>([
    "recalculo-pendiente-scoped",
]);

// Descripciones que indican side-effects automáticos (no acciones explícitas del
// usuario). El usuario ya recibió la notificación principal del cambio que los
// disparó (ej. "Comisión MLA xxx: 14.35%") y los toasts "Proceso iniciado/
// finalizado" del recálculo asíncrono son ruido. Los silenciamos por prefijo.
const DESCRIPCIONES_SIN_TOAST_PREFIXES: string[] = [
    "Recálculo por cambio en ",
];

function descripcionEsSideEffect(descripcion: string | undefined | null): boolean {
    if (!descripcion) return false;
    return DESCRIPCIONES_SIN_TOAST_PREFIXES.some((p) => descripcion.startsWith(p));
}

// Grupos de exclusión: el frontend los pide al backend en /api/procesos/grupos
// para que sean una sola fuente de verdad. Mientras la respuesta no llegue,
// usamos un fallback embebido (sincronizado con el backend al momento de
// escribir esto, por si la red falla en la primera carga).
const GRUPOS_FALLBACK: Record<string, string[]> = {
    "recalculo-precios": ["BD", "ML"],
    "recalculo-canal": ["BD"],
    "recalculo-pendiente-scoped": ["BD"],
    "costo-envio": ["ML"],
    "costo-venta": ["ML"],
    "automatizacion-precios": ["ML", "DUX", "BD"],
    "dux-importacion": ["DUX", "BD"],
    "dux-sync-programada": ["DUX", "BD"],
    "dux-deudas": ["DUX"],
    "reposicion": [],
};

const ProcesoActivoContext = createContext<ProcesoActivoContextType>({
    procesos: [],
    hayProcesoActivo: false,
    primerProceso: null,
    tieneConflicto: () => null,
    refresh: async () => {},
});

/**
 * Cuando un recálculo masivo termina con SKUs problemáticos, hace fetch del
 * resultado detallado y emite:
 *  - Un toast con acción "Copiar SKUs" (efímero, 30s).
 *  - Una notificación al panel con el detalle pegable (persistente vía localStorage).
 *
 * Útil para diagnosticar qué productos fallaron sin tener que abrir otra pantalla.
 */
async function mostrarDetalleRecalculoMasivo() {
    try {
        const resultado = await getResultadoRecalculoMasivoAPI();
        if (!resultado) return;
        const { skusConErrores = [], skusSinCosto = [], skusSinMargen = [] } = resultado;
        if (skusConErrores.length === 0 && skusSinCosto.length === 0 && skusSinMargen.length === 0) {
            return;
        }
        const partes: string[] = [];
        if (skusConErrores.length > 0) partes.push(`${skusConErrores.length} con errores`);
        if (skusSinCosto.length > 0) partes.push(`${skusSinCosto.length} sin costo`);
        if (skusSinMargen.length > 0) partes.push(`${skusSinMargen.length} sin margen`);
        const titulo = `SKUs problemáticos: ${partes.join(", ")}`;
        // Texto a copiar: secciones separadas por encabezado.
        const lineas: string[] = [];
        if (skusConErrores.length > 0) {
            lineas.push("# Con errores", ...skusConErrores, "");
        }
        if (skusSinCosto.length > 0) {
            lineas.push("# Sin costo", ...skusSinCosto, "");
        }
        if (skusSinMargen.length > 0) {
            lineas.push("# Sin margen", ...skusSinMargen, "");
        }
        const textoCopia = lineas.join("\n").trimEnd();

        // 1) Toast efímero con acción rápida (sonner). 30s para apretar el botón.
        toast.info(titulo, {
            description: "Apretá 'Copiar SKUs' o expandilo desde el panel de notificaciones.",
            duration: 30000,
            action: {
                label: "Copiar SKUs",
                onClick: () => {
                    navigator.clipboard.writeText(textoCopia)
                        .then(() => toast.success("SKUs copiados al portapapeles"))
                        .catch(() => toast.error("No se pudo copiar al portapapeles"));
                },
            },
        });

        // 2) Notificación persistente en el panel con el detalle expandible/copiable.
        //    Usamos persistir (no info) para no emitir un toast duplicado: el
        //    toast efímero de arriba ya cubre la notificación visual.
        notificar.persistir("info", titulo, textoCopia);
    } catch {
        // Silencioso: si falla el fetch, ya hay un toast con los contadores agregados.
    }
}

export function ProcesoActivoProvider({ children }: { children: React.ReactNode }) {
    const [procesos, setProcesos] = useState<ProcesoItem[]>([]);
    // Mapping de grupos de exclusión: arranca con el fallback embebido y se
    // reemplaza apenas llega la respuesta del backend en /api/procesos/grupos.
    const [grupos, setGrupos] = useState<Record<string, string[]>>(GRUPOS_FALLBACK);
    // Guardamos el ÚLTIMO ProcesoItem visto por id (no solo descripcion). Cuando un
    // proceso desaparece, usamos su último snapshot — que viene del backend con
    // procesados/exitosos/errores finales gracias al broadcast pre-liberar — para
    // emitir la notificación con el tipo correcto (success / warning / error).
    const prevProcesosRef = useRef<Map<string, ProcesoItem>>(new Map());
    const firstEventRef = useRef(true);

    // Carga única del mapping autoritativo. Si falla (ej. red caída), seguimos
    // con el fallback. Tras autenticarse, fetchAPI inyecta el token.
    useEffect(() => {
        let cancelled = false;
        fetchAPI(`${API_BASE_URL}/api/procesos/grupos`)
            .then((res) => res.ok ? res.json() : null)
            .then((data) => {
                if (!cancelled && data && typeof data === "object") {
                    setGrupos(data);
                }
            })
            .catch(() => { /* fallback queda activo */ });
        return () => { cancelled = true; };
    }, []);

    const aplicarSnapshot = useCallback((payload: StreamPayload) => {
        const nuevos: ProcesoItem[] = (payload.procesos ?? []).map((p) => ({
            proceso: p.proceso,
            descripcion: p.descripcion,
            total: p.total ?? null,
            procesados: p.procesados ?? null,
            exitosos: p.exitosos ?? null,
            errores: p.errores ?? null,
            mensaje: p.mensaje ?? null,
        }));
        const nuevosMap = new Map(nuevos.map((p) => [p.proceso, p]));

        // En el primer snapshot (conexión inicial o reconexión) no disparamos
        // notificaciones: sólo sincronizamos el estado para no spamear toasts
        // "iniciado" de procesos que ya estaban corriendo.
        if (!firstEventRef.current) {
            for (const [prevId, prev] of prevProcesosRef.current) {
                if (!nuevosMap.has(prevId)) {
                    if (PROCESOS_SIN_TOAST_GLOBAL.has(prevId)) continue;
                    if (descripcionEsSideEffect(prev.descripcion)) continue;
                    // El proceso terminó: clasificar según contadores finales del
                    // último snapshot que recibimos antes de que desapareciera.
                    const desc = prev.descripcion;
                    const mensajeBack = prev.mensaje?.trim() ?? "";
                    const tieneContadores =
                        prev.total != null || prev.procesados != null
                        || prev.exitosos != null || prev.errores != null;
                    const exitosos = prev.exitosos ?? 0;
                    const errores = prev.errores ?? 0;
                    // El tipo (success/warning/error) lo decidimos por contadores.
                    // El TEXTO preferimos tomarlo del backend porque ya trae la
                    // unidad correcta para cada proceso ("precios calculados",
                    // "comprobantes obtenidos", "productos actualizados", etc.).
                    // Si no hay mensaje, caemos a un texto genérico con contadores.
                    const texto = mensajeBack
                        ? `${desc} — ${mensajeBack}`
                        : (errores === 0 && exitosos > 0
                            ? `${desc}: ${exitosos} OK`
                            : errores > 0 && exitosos > 0
                                ? `${desc}: ${exitosos} OK, ${errores} errores`
                                : errores > 0
                                    ? `${desc} falló: ${errores} errores`
                                    : `Proceso finalizado: ${desc}`);
                    if (!tieneContadores) {
                        // Proceso sin contadores: no sabemos si fue OK. info neutro.
                        notificar.info(texto);
                    } else if (errores === 0 && exitosos > 0) {
                        notificar.success(texto);
                    } else if (errores > 0 && exitosos > 0) {
                        notificar.warning(texto);
                    } else if (errores > 0) {
                        notificar.error(texto);
                    } else {
                        // Contadores en cero (sin items que procesar): info neutro.
                        notificar.info(texto);
                    }

                    // Si fue un recálculo masivo y hubo SKUs problemáticos (errores, sin
                    // costo o sin margen), hacemos fetch del resultado detallado y
                    // mostramos un toast con acción "Copiar SKUs" para diagnóstico.
                    if (prevId === "recalculo-precios"
                        && (errores > 0 || mensajeBack.includes("sin costo") || mensajeBack.includes("sin margen"))) {
                        mostrarDetalleRecalculoMasivo();
                    }
                }
            }
            for (const [nuevoId, nuevo] of nuevosMap) {
                if (PROCESOS_SIN_TOAST_GLOBAL.has(nuevoId)) continue;
                if (PROCESOS_SIN_TOAST_INICIO.has(nuevoId)) continue;
                if (descripcionEsSideEffect(nuevo.descripcion)) continue;
                if (!prevProcesosRef.current.has(nuevoId)) notificar.info(`Proceso iniciado: ${nuevo.descripcion}`);
            }
        }
        firstEventRef.current = false;
        prevProcesosRef.current = nuevosMap;
        setProcesos(nuevos);
    }, []);

    // Refresh manual (útil al cancelar un proceso para no esperar al próximo push).
    const refresh = useCallback(async () => {
        try {
            const res = await fetchAPI(`${API_BASE_URL}/api/procesos/activo`);
            const data: ProcesosActivosResponse = await res.json();
            aplicarSnapshot(data);
        } catch {
            // Silencioso: el stream SSE eventualmente nos traerá el estado.
        }
    }, [aplicarSnapshot]);

    useEffect(() => {
        // EventSource nativo: el endpoint es público (ver SecurityConfig en el backend),
        // así que no necesitamos headers custom. El browser reconecta solo con backoff
        // cuando la conexión se cae.
        if (typeof window === "undefined") return;
        const es = new EventSource(`${API_BASE_URL}/api/procesos/activo/stream`);

        es.addEventListener("procesos", (ev) => {
            try {
                const data = JSON.parse((ev as MessageEvent).data) as StreamPayload;
                aplicarSnapshot(data);
            } catch {
                // Payload mal formado: ignorar.
            }
        });

        // Cuando la conexión se rompe, marcamos el próximo snapshot como "inicial"
        // para no disparar toasts fantasmas de procesos que ya estaban corriendo.
        es.addEventListener("error", () => { firstEventRef.current = true; });

        return () => es.close();
    }, [aplicarSnapshot]);

    const tieneConflicto = useCallback((procesoId: string): ProcesoItem | null => {
        const gruposDelProceso = grupos[procesoId] ?? [];
        if (gruposDelProceso.length === 0) return null;

        for (const activo of procesos) {
            const gruposActivo = grupos[activo.proceso] ?? [];
            for (const grupo of gruposDelProceso) {
                if (gruposActivo.includes(grupo)) {
                    return activo;
                }
            }
        }
        return null;
    }, [procesos, grupos]);

    return (
        <ProcesoActivoContext.Provider
            value={{
                procesos,
                hayProcesoActivo: procesos.length > 0,
                primerProceso: procesos.length > 0 ? procesos[0] : null,
                tieneConflicto,
                refresh,
            }}
        >
            {children}
        </ProcesoActivoContext.Provider>
    );
}

export function useProcesoActivo() {
    return useContext(ProcesoActivoContext);
}

export function getRutaProceso(procesoId: string): string | null {
    return PROCESO_RUTAS[procesoId] ?? null;
}

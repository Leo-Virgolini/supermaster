"use client";
import { useState } from "react";
import { ArrowPathIcon, ChevronDownIcon, ExclamationTriangleIcon } from "@heroicons/react/24/outline";
import { notificar } from "../../utils/notificar";
import { useRecalculoPendiente } from "./useRecalculoPendiente";

/**
 * Banner global que aparece en el Header cuando hay cambios que requieren recálculo
 * de precios (conceptos, reglas, cuotas, márgenes, etc.). El usuario lo aplica con
 * un solo click y el banner desaparece al confirmarse el inicio del job.
 */
export default function RecalculoPendienteBanner() {
    const { estado, aplicando, aplicar } = useRecalculoPendiente();
    const [detalleAbierto, setDetalleAbierto] = useState(false);

    if (!estado.pendiente) return null;

    const handleAplicar = async () => {
        const result = await aplicar();
        if (result.ok) {
            notificar.success("Recálculo iniciado — corre en segundo plano. Podés seguir trabajando.");
        } else if (result.error) {
            notificar.error(result.error);
        }
    };

    // Etiqueta principal según el scope. Refleja items únicos pendientes,
    // no el total de modificaciones (3 edits al mismo producto = "1 producto pendiente").
    const scopeLabel = (() => {
        if (estado.recalcularTodo) {
            // Hay un cambio amplio (concepto, MLA, proveedor...): se va a recalcular todo.
            return "recálculo masivo";
        }
        const partes: string[] = [];
        if (estado.productosCount > 0) {
            partes.push(`${estado.productosCount} producto${estado.productosCount === 1 ? "" : "s"}`);
        }
        if (estado.canalesCount > 0) {
            partes.push(`${estado.canalesCount} canal${estado.canalesCount === 1 ? "" : "es"}`);
        }
        return partes.join(" + ");
    })();

    return (
        <div className="relative">
            <div className="flex flex-wrap items-center gap-2 rounded-lg border border-amber-300 bg-amber-50 px-3 py-1.5 text-sm dark:border-amber-700/60 dark:bg-amber-900/30">
                <ExclamationTriangleIcon className="h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400" />
                <span className="font-semibold text-amber-900 dark:text-amber-200">
                    Recálculo pendiente
                </span>
                <span className="text-xs text-amber-700 dark:text-amber-300">
                    {scopeLabel} pendiente{estado.recalcularTodo || estado.cantidad === 1 ? "" : "s"}
                </span>
                <button
                    type="button"
                    onClick={() => setDetalleAbierto((v) => !v)}
                    className="ml-1 inline-flex items-center gap-0.5 rounded text-[11px] text-amber-700 hover:text-amber-900 dark:text-amber-300 dark:hover:text-amber-100"
                    title="Ver motivos"
                >
                    detalle
                    <ChevronDownIcon className={`h-3 w-3 transition-transform ${detalleAbierto ? "rotate-180" : ""}`} />
                </button>
                <button
                    type="button"
                    onClick={handleAplicar}
                    disabled={aplicando}
                    className="ml-auto inline-flex items-center gap-1 rounded-md bg-amber-600 px-2.5 py-1 text-xs font-bold text-white shadow-sm transition hover:bg-amber-700 disabled:opacity-60"
                >
                    <ArrowPathIcon className={`h-3.5 w-3.5 ${aplicando ? "animate-spin" : ""}`} />
                    {aplicando ? "Iniciando..." : "Aplicar recálculo"}
                </button>
            </div>

            {detalleAbierto && estado.motivos.length > 0 && (
                <div className="absolute left-0 right-0 top-full z-30 mt-1 rounded-md border border-amber-200 bg-white shadow-lg dark:border-amber-800/60 dark:bg-slate-900">
                    <div className="border-b border-amber-100 px-3 py-1.5 text-[10px] font-bold uppercase tracking-wider text-amber-700 dark:border-amber-800/60 dark:text-amber-300">
                        Motivos pendientes
                    </div>
                    <ul className="max-h-60 overflow-auto py-1 text-xs">
                        {estado.motivos.map((m) => (
                            <li key={m.motivo} className="flex items-center justify-between gap-2 px-3 py-1 text-slate-700 dark:text-slate-200">
                                <span className="truncate">{m.motivo}</span>
                                <span className="shrink-0 rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-bold text-amber-800 dark:bg-amber-500/20 dark:text-amber-200">
                                    {m.cantidad}×
                                </span>
                            </li>
                        ))}
                    </ul>
                </div>
            )}
        </div>
    );
}

"use client";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import {
    ShareIcon,
    FunnelIcon,
    ArrowDownIcon,
    InformationCircleIcon,
    ChevronDownIcon,
    BeakerIcon,
    CreditCardIcon,
    ExclamationTriangleIcon,
    CalculatorIcon,
    PencilSquareIcon,
    PlusIcon,
    XMarkIcon,
} from "@heroicons/react/24/outline";
import CanalSelectBadge from "../components/CanalSelectBadge/CanalSelectBadge";
import { toast } from "sonner";
import {
    buildCanalFormulaView,
    getAllCanalesSimpleAPI,
    loadLookupMaps,
    type CanalListItem,
} from "./canalFormulaService";
import { APLICA_SOBRE_LABEL, isFlag, ETAPAS_INFO } from "./etapas";
import { getNaturalezaInfo } from "./naturaleza";
import type { EtapaConConceptos, EtapaId } from "./types";
import type { CanalFormulaView, ConceptoEnCanal, CuotaCanal } from "./types";
import type { CanalConceptoReglaDTO } from "../canal-concepto-regla/types";
import type { CanalReglaDTO } from "../canal-regla/types";
import { getCanalColor, CANAL_BADGE_CLASS } from "../utils/canalColors";
import { buildFormulaCompuesta, type FormulaStep } from "./formula-builder";
import { asignarConceptoAPI, eliminarConceptoDelCanalAPI } from "../canales/canalConceptosService";
import { getConceptosGastoAPI, updateConceptoGastoAPI, type ConceptoGastoDTO } from "../conceptos-gastos/conceptosGastosService";
import { confirmDialog } from "../utils/confirmDialog";

type LookupMaps = Awaited<ReturnType<typeof loadLookupMaps>>;

const errorMessage = (e: unknown): string | null =>
    e instanceof Error ? e.message : null;

function formatPorcentaje(p: number): string {
    if (p === 0) return "0%";
    const sign = p > 0 ? "+" : "";
    const fixed = Number.isInteger(p) ? p.toString() : p.toFixed(2).replace(/\.?0+$/, "");
    return `${sign}${fixed}%`;
}

function PorcentajeBadge({ valor, mono = true }: { valor: number; mono?: boolean }) {
    const color =
        valor > 0
            ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-500/20 dark:text-emerald-200"
            : valor < 0
                ? "bg-red-100 text-red-800 dark:bg-red-500/20 dark:text-red-200"
                : "bg-slate-100 text-slate-700 dark:bg-slate-700 dark:text-slate-200";
    return (
        <span className={`inline-flex items-center rounded px-2 py-0.5 text-xs font-bold ${color} ${mono ? "font-mono" : ""}`}>
            {formatPorcentaje(valor)}
        </span>
    );
}

function describeCondicion(regla: CanalConceptoReglaDTO | CanalReglaDTO, lookups: LookupMaps): string[] {
    const partes: string[] = [];
    const r = regla as any;
    if (r.tipoId) partes.push(`Tipo = ${lookups.tipos[r.tipoId] ?? `#${r.tipoId}`}`);
    if (r.marcaId) partes.push(`Marca = ${lookups.marcas[r.marcaId] ?? `#${r.marcaId}`}`);
    if (r.clasifGralId) partes.push(`Rubro = ${lookups.clasifGral[r.clasifGralId] ?? `#${r.clasifGralId}`}`);
    if (r.clasifGastroId) partes.push(`Gastro = ${lookups.clasifGastro[r.clasifGastroId] ?? `#${r.clasifGastroId}`}`);
    if (r.tag) partes.push(`Tag = ${r.tag}`);
    if (r.tieneEnvio === true) partes.push("tiene envío");
    if (r.tieneEnvio === false) partes.push("no tiene envío");
    if (r.productoId) partes.push(`Producto #${r.productoId}` + (r.productoLabel ? ` (${r.productoLabel})` : ""));
    return partes;
}

function ReglaPill({ tipo }: { tipo: "INCLUIR" | "EXCLUIR" }) {
    const colors =
        tipo === "INCLUIR"
            ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-500/20 dark:text-emerald-200"
            : "bg-red-100 text-red-800 dark:bg-red-500/20 dark:text-red-200";
    return (
        <span className={`inline-flex items-center rounded px-2 py-0.5 text-[11px] font-bold tracking-wide ${colors}`}>
            {tipo}
        </span>
    );
}

function ReglaLine({ regla, lookups }: { regla: CanalConceptoReglaDTO | CanalReglaDTO; lookups: LookupMaps }) {
    const condiciones = describeCondicion(regla, lookups);
    return (
        <div className="flex items-start gap-2">
            <ReglaPill tipo={regla.tipoRegla} />
            <span className="text-xs leading-relaxed text-slate-700 dark:text-slate-300">
                {condiciones.length > 0 ? condiciones.join(" + ") : <em className="text-slate-400">sin condición</em>}
            </span>
        </div>
    );
}

function ConceptoCard({
    concepto,
    lookups,
    editMode,
    onEditPorcentaje,
    onQuitar,
}: {
    concepto: ConceptoEnCanal;
    lookups: LookupMaps;
    editMode?: boolean;
    onEditPorcentaje?: (conceptoId: number, nuevo: number) => Promise<void>;
    onQuitar?: (conceptoId: number) => Promise<void>;
}) {
    const [open, setOpen] = useState(false);
    const [editingPorcentaje, setEditingPorcentaje] = useState(false);
    const [porcInput, setPorcInput] = useState(concepto.porcentaje);
    const [savingPorc, setSavingPorc] = useState(false);
    const tieneReglas = concepto.reglas.length > 0;
    const inclusiones = concepto.reglas.filter((r) => r.tipoRegla === "INCLUIR");
    const exclusiones = concepto.reglas.filter((r) => r.tipoRegla === "EXCLUIR");
    const flag = isFlag(concepto.aplicaSobre);

    const guardarPorcentaje = async () => {
        if (!onEditPorcentaje || porcInput === concepto.porcentaje) {
            setEditingPorcentaje(false);
            return;
        }
        setSavingPorc(true);
        try {
            await onEditPorcentaje(concepto.conceptoId, porcInput);
            setEditingPorcentaje(false);
        } finally {
            setSavingPorc(false);
        }
    };

    return (
        <div
            className={`overflow-hidden rounded-lg border bg-white shadow-sm transition dark:bg-slate-900 ${
                tieneReglas
                    ? "border-amber-300 dark:border-amber-700/60"
                    : "border-slate-200 dark:border-slate-700"
            }`}
        >
            <div
                role={tieneReglas ? "button" : undefined}
                tabIndex={tieneReglas ? 0 : undefined}
                aria-expanded={tieneReglas ? open : undefined}
                onClick={() => { if (tieneReglas && !editingPorcentaje) setOpen((v) => !v); }}
                onKeyDown={(e) => {
                    if (!tieneReglas || editingPorcentaje) return;
                    if (e.key === "Enter" || e.key === " ") { e.preventDefault(); setOpen((v) => !v); }
                }}
                className={`flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition ${
                    tieneReglas ? "cursor-pointer hover:bg-amber-50/50 dark:hover:bg-amber-900/10" : "cursor-default"
                }`}
            >
                <div className="flex min-w-0 items-center gap-3">
                    <div className="flex min-w-0 flex-col">
                        <span className="font-semibold text-slate-800 dark:text-slate-100">{concepto.nombre}</span>
                        <span className="flex items-center gap-1.5 text-[11px] font-medium uppercase tracking-wide text-slate-400 dark:text-slate-500">
                            <span>{APLICA_SOBRE_LABEL[concepto.aplicaSobre] ?? concepto.aplicaSobre}</span>
                            {(() => {
                                const nat = getNaturalezaInfo(concepto.naturaleza);
                                return (
                                    <span
                                        className={`inline-flex items-center gap-0.5 rounded px-1.5 py-0.5 text-[10px] font-bold normal-case tracking-normal ${nat.badgeClass}`}
                                        title={nat.descripcion}
                                    >
                                        <span>{nat.icon}</span>
                                        <span>{nat.label}</span>
                                    </span>
                                );
                            })()}
                        </span>
                    </div>
                </div>
                <div className="flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
                    {flag ? (
                        <span className="inline-flex items-center gap-1 rounded bg-slate-100 px-2 py-0.5 text-xs font-bold text-slate-700 dark:bg-slate-700 dark:text-slate-200">
                            ⚑ flag
                        </span>
                    ) : editMode && editingPorcentaje ? (
                        <span className="inline-flex items-center gap-1">
                            <input
                                type="number"
                                step={0.01}
                                value={porcInput}
                                onChange={(e) => setPorcInput(Number(e.target.value))}
                                onKeyDown={(e) => {
                                    if (e.key === "Enter") guardarPorcentaje();
                                    if (e.key === "Escape") { setPorcInput(concepto.porcentaje); setEditingPorcentaje(false); }
                                }}
                                onClick={(e) => e.stopPropagation()}
                                disabled={savingPorc}
                                autoFocus
                                className="w-20 rounded border border-blue-300 bg-white px-1.5 py-0.5 text-xs font-mono text-right dark:border-blue-700 dark:bg-slate-800"
                            />
                            <span className="text-xs text-slate-500">%</span>
                            <button
                                type="button"
                                onClick={(e) => { e.stopPropagation(); guardarPorcentaje(); }}
                                disabled={savingPorc}
                                className="text-[10px] font-bold text-emerald-600 hover:text-emerald-800"
                            >
                                {savingPorc ? "..." : "✓"}
                            </button>
                        </span>
                    ) : editMode && onEditPorcentaje ? (
                        <button
                            type="button"
                            onClick={(e) => { e.stopPropagation(); setPorcInput(concepto.porcentaje); setEditingPorcentaje(true); }}
                            title="Click para editar"
                            className="rounded border border-dashed border-slate-300 hover:border-blue-400 dark:border-slate-600"
                        >
                            <PorcentajeBadge valor={concepto.porcentaje} />
                        </button>
                    ) : (
                        <PorcentajeBadge valor={concepto.porcentaje} />
                    )}
                    {tieneReglas && (
                        <span className="inline-flex items-center gap-1 rounded bg-amber-100 px-2 py-0.5 text-[11px] font-bold text-amber-800 dark:bg-amber-500/20 dark:text-amber-200">
                            <ExclamationTriangleIcon className="h-3 w-3" />
                            {concepto.reglas.length} {concepto.reglas.length === 1 ? "regla" : "reglas"}
                        </span>
                    )}
                    {editMode && onQuitar && (
                        <button
                            type="button"
                            onClick={(e) => { e.stopPropagation(); onQuitar(concepto.conceptoId); }}
                            title="Quitar concepto del canal"
                            className="inline-flex h-6 w-6 items-center justify-center rounded text-red-500 hover:bg-red-50 hover:text-red-700 dark:hover:bg-red-500/10"
                        >
                            <XMarkIcon className="h-4 w-4" />
                        </button>
                    )}
                    {tieneReglas && (
                        <ChevronDownIcon className={`h-4 w-4 text-slate-400 transition-transform ${open ? "rotate-180" : ""}`} />
                    )}
                </div>
            </div>

            {tieneReglas && open && (
                <div className="border-t border-slate-100 bg-slate-50/50 px-4 py-3 dark:border-slate-700 dark:bg-slate-800/30">
                    <div className="mb-2 flex items-start gap-2 text-xs text-slate-600 dark:text-slate-400">
                        <ShareIcon className="mt-0.5 h-4 w-4 shrink-0" />
                        <span>
                            <strong>Ramificación:</strong> este concepto no se aplica igual a todos los productos.
                        </span>
                    </div>

                    {/* Rama default */}
                    <div className="ml-6 border-l-2 border-dashed border-slate-300 dark:border-slate-600">
                        {/* Si hay alguna INCLUIR, el comportamiento default cambia */}
                        {inclusiones.length > 0 ? (
                            <div className="ml-3 mb-2 rounded-md border border-slate-200 bg-white p-2 text-xs dark:border-slate-700 dark:bg-slate-900">
                                <span className="font-semibold text-slate-500 dark:text-slate-400">Default:</span>{" "}
                                <span className="text-slate-700 dark:text-slate-300">
                                    NO se aplica salvo a productos que cumplan alguna condición INCLUIR.
                                </span>
                            </div>
                        ) : (
                            <div className="ml-3 mb-2 rounded-md border border-emerald-200 bg-emerald-50 p-2 text-xs dark:border-emerald-800/60 dark:bg-emerald-900/20">
                                <span className="font-semibold text-emerald-700 dark:text-emerald-300">Default:</span>{" "}
                                <span className="text-emerald-800 dark:text-emerald-200">
                                    se aplica a todos los productos del canal.
                                </span>
                            </div>
                        )}

                        {inclusiones.length > 0 && (
                            <div className="ml-3 mb-2">
                                <p className="mb-1 text-[11px] font-bold uppercase tracking-wide text-emerald-700 dark:text-emerald-400">
                                    Aplica solo si:
                                </p>
                                <div className="flex flex-col gap-1.5 rounded-md border border-emerald-200 bg-emerald-50 p-2 dark:border-emerald-800/60 dark:bg-emerald-900/20">
                                    {inclusiones.map((r) => (
                                        <ReglaLine key={r.id} regla={r} lookups={lookups} />
                                    ))}
                                </div>
                            </div>
                        )}

                        {exclusiones.length > 0 && (
                            <div className="ml-3 mb-1">
                                <p className="mb-1 text-[11px] font-bold uppercase tracking-wide text-red-700 dark:text-red-400">
                                    NO aplica si:
                                </p>
                                <div className="flex flex-col gap-1.5 rounded-md border border-red-200 bg-red-50 p-2 dark:border-red-800/60 dark:bg-red-900/20">
                                    {exclusiones.map((r) => (
                                        <ReglaLine key={r.id} regla={r} lookups={lookups} />
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>

                    {concepto.descripcion && (
                        <p className="mt-3 rounded-md bg-slate-100 px-2 py-1 text-[11px] italic text-slate-600 dark:bg-slate-800 dark:text-slate-400">
                            💬 {concepto.descripcion}
                        </p>
                    )}
                </div>
            )}
        </div>
    );
}

function CuotaCard({ cuota }: { cuota: CuotaCanal }) {
    const esTransf = cuota.cuotas === -1;
    const label = esTransf ? "Transferencia" : cuota.cuotas === 0 || cuota.cuotas === 1 ? "Contado" : `${cuota.cuotas} cuotas`;
    const recargoPositivo = cuota.porcentaje > 0;
    return (
        <div className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white p-3 shadow-sm dark:border-slate-700 dark:bg-slate-900">
            <div className="flex flex-col">
                <span className="text-sm font-semibold text-slate-800 dark:text-slate-100">{label}</span>
                {cuota.descripcion && (
                    <span className="text-[11px] text-slate-500 dark:text-slate-400">{cuota.descripcion}</span>
                )}
            </div>
            <span
                className={`rounded px-2 py-0.5 text-xs font-bold font-mono ${
                    cuota.porcentaje === 0
                        ? "bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-300"
                        : recargoPositivo
                            ? "bg-amber-100 text-amber-800 dark:bg-amber-500/20 dark:text-amber-200"
                            : "bg-emerald-100 text-emerald-800 dark:bg-emerald-500/20 dark:text-emerald-200"
                }`}
            >
                {formatPorcentaje(cuota.porcentaje)}
            </span>
        </div>
    );
}

function FormulaStepRow({ paso }: { paso: FormulaStep }) {
    const opColor =
        paso.operator === "×" ? "text-emerald-600 dark:text-emerald-400"
        : paso.operator === "÷" ? "text-purple-600 dark:text-purple-400"
        : paso.operator === "+" ? "text-blue-600 dark:text-blue-400"
        : paso.operator === "−" ? "text-red-600 dark:text-red-400"
        : "text-slate-600 dark:text-slate-400";
    return (
        <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1 font-mono text-sm">
            <span className={`text-lg font-bold ${opColor}`}>{paso.operator}</span>
            <span className="font-semibold text-slate-800 dark:text-slate-100">{paso.expression}</span>
            <span className="text-[11px] font-normal text-slate-500 dark:text-slate-400">
                — {paso.label}
                {paso.conceptos.length > 0 && (
                    <span className="ml-1 text-slate-400 dark:text-slate-500">
                        ({paso.conceptos.map((c) => c.nombre).join(", ")})
                    </span>
                )}
            </span>
        </div>
    );
}

function FormulaFinalCard({ view, cuotaSel, onCuotaChange }: {
    view: CanalFormulaView;
    cuotaSel: CuotaCanal | null;
    onCuotaChange: (c: CuotaCanal | null) => void;
}) {
    const formula = useMemo(() => buildFormulaCompuesta(view, cuotaSel), [view, cuotaSel]);

    const sinPasos = formula.pasos.length === 0;

    return (
        <div className="rounded-xl border border-indigo-200 bg-gradient-to-br from-indigo-50 to-white p-4 shadow-sm dark:border-indigo-800/60 dark:from-indigo-950/30 dark:to-slate-900">
            <div className="mb-3 flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                    <CalculatorIcon className="h-5 w-5 text-indigo-600 dark:text-indigo-400" />
                    <h2 className="text-base font-bold text-slate-800 dark:text-slate-100">
                        Fórmula final
                    </h2>
                </div>
                <div className="flex items-center gap-2">
                    {view.cuotas.length > 0 && (
                        <label className="flex items-center gap-1.5 text-xs text-slate-600 dark:text-slate-400">
                            <span>Cuotas:</span>
                            <select
                                value={cuotaSel?.id ?? ""}
                                onChange={(e) => {
                                    const id = e.target.value;
                                    if (!id) onCuotaChange(null);
                                    else onCuotaChange(view.cuotas.find((c) => String(c.id) === id) ?? null);
                                }}
                                className="rounded border border-slate-300 bg-white px-2 py-0.5 text-xs dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100"
                            >
                                <option value="">— sin cuotas —</option>
                                {view.cuotas.map((c) => {
                                    const label = c.cuotas === -1 ? "Transferencia"
                                        : c.cuotas <= 1 ? "Contado"
                                        : `${c.cuotas} cuotas`;
                                    return (
                                        <option key={c.id} value={c.id}>{label}</option>
                                    );
                                })}
                            </select>
                        </label>
                    )}
                    {formula.tienePrecioInflado && (
                        <span className="inline-flex items-center gap-1 rounded bg-amber-100 px-2 py-0.5 text-[11px] font-bold text-amber-800 dark:bg-amber-500/20 dark:text-amber-200">
                            <ExclamationTriangleIcon className="h-3 w-3" />
                            Usa precio inflado
                        </span>
                    )}
                </div>
            </div>

            {formula.partidaCanalBase && (
                <p className="mb-3 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900 dark:border-amber-800/60 dark:bg-amber-900/20 dark:text-amber-100">
                    Parte del PVP de <strong className="font-mono">{formula.partidaCanalBase.nombre}</strong>
                    {" "}({formula.partidaCanalBase.tipo}). Los conceptos del pipeline son ignorados salvo el factor sobre canal base.
                </p>
            )}

            {sinPasos ? (
                <p className="text-sm italic text-slate-500 dark:text-slate-400">
                    Sin conceptos asignados: el PVP coincide con el costo del producto.
                </p>
            ) : (
                <div className="rounded-md bg-white/70 p-3 dark:bg-slate-900/50">
                    <p className="mb-2 text-[11px] font-bold uppercase tracking-wide text-indigo-700 dark:text-indigo-300">
                        Composición
                    </p>
                    <div className="flex flex-col gap-1.5">
                        <div className="font-mono text-sm font-semibold text-slate-800 dark:text-slate-100">
                            Costo
                            {formula.partidaCanalBase && (
                                <span className="ml-1 text-[11px] font-normal text-amber-700 dark:text-amber-300">
                                    (= PVP {formula.partidaCanalBase.nombre})
                                </span>
                            )}
                        </div>
                        {formula.pasos.map((p, i) => (
                            <FormulaStepRow key={i} paso={p} />
                        ))}
                        <div className="mt-1 flex items-baseline gap-2 border-t border-indigo-200 pt-1.5 font-mono text-sm font-bold dark:border-indigo-800/60">
                            <span className="text-lg text-slate-600 dark:text-slate-400">=</span>
                            <span className="text-emerald-700 dark:text-emerald-300">PVP final</span>
                            {formula.tienePrecioInflado && (
                                <span className="text-[11px] font-normal text-amber-700 dark:text-amber-300">
                                    (o precio inflado si está configurado)
                                </span>
                            )}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

/**
 * Botón "+" que abre un dropdown con los conceptos disponibles para una etapa
 * específica. Filtra por aplicaSobre compatible y excluye los ya asignados.
 */
function AddConceptoButton({
    etapaId,
    allConceptos,
    asignados,
    onAsignar,
}: {
    etapaId: EtapaId;
    allConceptos: ConceptoGastoDTO[];
    asignados: Set<number>;
    onAsignar: (id: number) => Promise<void>;
}) {
    const [open, setOpen] = useState(false);
    const [filtro, setFiltro] = useState("");
    const candidatos = useMemo(() => {
        return allConceptos.filter((c) =>
            c.etapa === etapaId
            && !asignados.has(c.id)
            && (filtro.trim() === ""
                || c.nombre.toLowerCase().includes(filtro.toLowerCase())
                || (c.descripcion?.toLowerCase().includes(filtro.toLowerCase()) ?? false))
        );
    }, [allConceptos, etapaId, asignados, filtro]);

    return (
        <div className="relative">
            <button
                type="button"
                onClick={() => setOpen((v) => !v)}
                className="inline-flex items-center gap-1 rounded-md border border-dashed border-slate-300 px-2 py-1 text-xs font-bold text-slate-500 hover:border-blue-400 hover:bg-blue-50 hover:text-blue-700 dark:border-slate-600 dark:text-slate-400 dark:hover:border-blue-500 dark:hover:bg-blue-900/20"
                title="Agregar concepto a esta etapa"
            >
                <PlusIcon className="h-3.5 w-3.5" />
                Agregar
            </button>
            {open && (
                <div className="absolute right-0 top-full z-30 mt-1 w-80 rounded-lg border border-slate-200 bg-white shadow-lg dark:border-slate-700 dark:bg-slate-900">
                    <div className="border-b border-slate-100 p-2 dark:border-slate-800">
                        <input
                            type="text"
                            placeholder="Filtrar conceptos..."
                            value={filtro}
                            onChange={(e) => setFiltro(e.target.value)}
                            autoFocus
                            className="w-full rounded border border-slate-300 px-2 py-1 text-xs dark:border-slate-700 dark:bg-slate-800"
                        />
                    </div>
                    <ul className="max-h-64 overflow-auto py-1">
                        {candidatos.length === 0 ? (
                            <li className="px-3 py-2 text-xs italic text-slate-400">
                                No hay conceptos disponibles para esta etapa.
                                {asignados.size > 0 && filtro === "" && " Quizás todos están asignados — creá uno nuevo en Conceptos de Cálculo."}
                            </li>
                        ) : candidatos.map((c) => (
                            <li key={c.id}>
                                <button
                                    type="button"
                                    onClick={async () => {
                                        await onAsignar(c.id);
                                        setOpen(false);
                                        setFiltro("");
                                    }}
                                    className="flex w-full flex-col items-start gap-0.5 px-3 py-1.5 text-left text-xs hover:bg-slate-100 dark:hover:bg-slate-800"
                                >
                                    <span className="font-bold text-slate-800 dark:text-slate-100">{c.nombre}</span>
                                    <span className="text-[10px] text-slate-400">
                                        {APLICA_SOBRE_LABEL[c.aplicaSobre] ?? c.aplicaSobre}
                                        {c.porcentaje != null && !c.aplicaSobre.startsWith("FLAG_") && ` · ${c.porcentaje}%`}
                                    </span>
                                </button>
                            </li>
                        ))}
                    </ul>
                    <div className="border-t border-slate-100 p-1 dark:border-slate-800">
                        <button
                            type="button"
                            onClick={() => { setOpen(false); setFiltro(""); }}
                            className="w-full rounded px-2 py-1 text-[10px] text-slate-500 hover:bg-slate-50 dark:hover:bg-slate-800"
                        >
                            Cerrar
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}

export default function CanalFormulaPage() {
    const [canales, setCanales] = useState<CanalListItem[]>([]);
    const [canalIdSel, setCanalIdSel] = useState<number | null>(null);
    const [view, setView] = useState<CanalFormulaView | null>(null);
    const [lookups, setLookups] = useState<LookupMaps>({ marcas: {}, tipos: {}, clasifGral: {}, clasifGastro: {} });
    const [isLoadingCanales, setIsLoadingCanales] = useState(true);
    const [isLoadingView, setIsLoadingView] = useState(false);
    const [ayudaAbierta, setAyudaAbierta] = useState(false);
    const [cuotaSel, setCuotaSel] = useState<CuotaCanal | null>(null);
    // Modo edición: muestra controles + / × / editar % inline en cada etapa.
    const [editMode, setEditMode] = useState(false);
    const [allConceptos, setAllConceptos] = useState<ConceptoGastoDTO[]>([]);
    const searchParams = useSearchParams();
    const canalIdFromUrl = useMemo(() => {
        const raw = searchParams.get("canalId");
        const n = raw ? Number(raw) : NaN;
        return Number.isFinite(n) ? n : null;
    }, [searchParams]);

    // Carga inicial: canales + lookups en paralelo.
    useEffect(() => {
        let cancelled = false;
        (async () => {
            try {
                setIsLoadingCanales(true);
                const [canalesData, lookupData] = await Promise.all([
                    getAllCanalesSimpleAPI(),
                    loadLookupMaps(),
                ]);
                if (cancelled) return;
                setCanales(canalesData);
                setLookups(lookupData);
                if (canalesData.length > 0) {
                    const desde = canalIdFromUrl != null && canalesData.some((c) => c.id === canalIdFromUrl)
                        ? canalIdFromUrl
                        : canalesData[0].id;
                    setCanalIdSel(desde);
                }
            } catch (e: any) {
                toast.error(e?.message || "Error al cargar canales");
            } finally {
                if (!cancelled) setIsLoadingCanales(false);
            }
        })();
        return () => { cancelled = true; };
    }, []);

    // Carga la vista cuando cambia el canal seleccionado.
    useEffect(() => {
        if (canalIdSel == null) return;
        const canal = canales.find((c) => c.id === canalIdSel);
        if (!canal) return;
        const canalBaseNombre = canal.canalBaseId != null
            ? canales.find((c) => c.id === canal.canalBaseId)?.nombre ?? null
            : null;
        let cancelled = false;
        (async () => {
            try {
                setIsLoadingView(true);
                const data = await buildCanalFormulaView(canal.id, canal.nombre, canalBaseNombre);
                if (cancelled) return;
                setView(data);
                setCuotaSel(null);
            } catch (e: any) {
                toast.error(e?.message || "Error al cargar la fórmula del canal");
                setView(null);
            } finally {
                if (!cancelled) setIsLoadingView(false);
            }
        })();
        return () => { cancelled = true; };
    }, [canalIdSel, canales]);

    const reglasInclude = useMemo(() => view?.reglasCanal.filter((r) => r.tipoRegla === "INCLUIR") ?? [], [view]);
    const reglasExclude = useMemo(() => view?.reglasCanal.filter((r) => r.tipoRegla === "EXCLUIR") ?? [], [view]);

    // Recarga la vista del canal actual sin tocar el resto del state.
    const refrescarView = useCallback(async () => {
        if (canalIdSel == null) return;
        const canal = canales.find((c) => c.id === canalIdSel);
        if (!canal) return;
        const canalBaseNombre = canal.canalBaseId != null
            ? canales.find((c) => c.id === canal.canalBaseId)?.nombre ?? null
            : null;
        try {
            const data = await buildCanalFormulaView(canal.id, canal.nombre, canalBaseNombre);
            setView(data);
        } catch (e) {
            toast.error(errorMessage(e) || "Error al refrescar la fórmula");
        }
    }, [canalIdSel, canales]);

    // Carga el catálogo de conceptos al entrar en modo edición (cacheado).
    useEffect(() => {
        if (!editMode || allConceptos.length > 0) return;
        getConceptosGastoAPI(0, 500, {}, "nombre,asc")
            .then((r) => setAllConceptos(r.content || []))
            .catch(() => toast.error("No se pudieron cargar los conceptos disponibles"));
    }, [editMode, allConceptos.length]);

    const handleAsignarConcepto = useCallback(async (conceptoId: number) => {
        if (canalIdSel == null) return;
        try {
            await asignarConceptoAPI(canalIdSel, conceptoId, "INLINE");
            await refrescarView();
            toast.success("Concepto agregado al canal");
        } catch (e) {
            toast.error(errorMessage(e) || "Error al agregar concepto");
        }
    }, [canalIdSel, refrescarView]);

    const handleQuitarConcepto = useCallback(async (conceptoId: number) => {
        if (canalIdSel == null) return;
        const confirmed = await confirmDialog({
            title: "Quitar concepto",
            message: "¿Quitar este concepto del canal? Las reglas asociadas también se eliminan.",
            confirmText: "Quitar",
            variant: "danger",
        });
        if (!confirmed) return;
        try {
            await eliminarConceptoDelCanalAPI(canalIdSel, conceptoId, "INLINE");
            await refrescarView();
            toast.success("Concepto quitado del canal");
        } catch (e) {
            toast.error(errorMessage(e) || "Error al quitar concepto");
        }
    }, [canalIdSel, refrescarView]);

    const handleEditarPorcentaje = useCallback(async (conceptoId: number, nuevo: number) => {
        try {
            await updateConceptoGastoAPI(conceptoId, { porcentaje: nuevo }, "INLINE");
            await refrescarView();
            toast.success("Porcentaje actualizado");
        } catch (e) {
            toast.error(errorMessage(e) || "Error al actualizar el porcentaje");
            throw e;
        }
    }, [refrescarView]);

    return (
        <main className="flex flex-col gap-4 bg-gray-50 p-4 dark:bg-slate-950">
            <header>
                <h1 className="flex items-center gap-2 text-3xl font-bold text-gray-800 dark:text-slate-100">
                    <ShareIcon className="h-8 w-8 text-gray-600 dark:text-slate-400" />
                    Fórmula del Canal
                </h1>
                <p className="mt-1 text-sm text-gray-500 dark:text-slate-400">
                    Visualizá los conceptos que componen el cálculo de precio de cada canal y sus ramificaciones por reglas.
                </p>
            </header>

            {/* Panel de ayuda */}
            <div className="rounded-lg border border-blue-200 bg-blue-50 dark:border-blue-800/60 dark:bg-blue-900/20">
                <button
                    type="button"
                    onClick={() => setAyudaAbierta((v) => !v)}
                    className="flex w-full items-center justify-between gap-2 px-4 py-2 text-left text-sm font-semibold text-blue-900 dark:text-blue-200"
                >
                    <span className="flex items-center gap-2">
                        <InformationCircleIcon className="h-5 w-5" />
                        Cómo leer esta vista
                    </span>
                    <ChevronDownIcon className={`h-4 w-4 transition-transform ${ayudaAbierta ? "rotate-180" : ""}`} />
                </button>
                {ayudaAbierta && (
                    <div className="border-t border-blue-200 px-4 py-3 text-sm text-blue-900 dark:border-blue-800/60 dark:text-blue-100">
                        <ol className="ml-4 list-decimal space-y-1.5">
                            <li><strong>Reglas de Canal</strong>: definen qué productos forman parte del canal (eligibilidad).</li>
                            <li><strong>Pipeline de etapas</strong>: los conceptos asignados al canal se agrupan por la etapa del cálculo donde se aplican (Costo → Margen → Impuestos → Precio → Post-Precio).</li>
                            <li><strong>Tarjetas con ⚠ &quot;X reglas&quot;</strong>: hacé clic para ver la ramificación. Las reglas <em>INCLUIR</em> restringen el concepto a un subconjunto; <em>EXCLUIR</em> lo quita para un subconjunto.</li>
                            <li><strong>Cuotas</strong>: planes de financiación con su recargo o descuento aplicable al final del cálculo.</li>
                            <li><strong>Calculadora</strong>: para simular &quot;¿qué precio tendría un producto con estos atributos?&quot;, ir a <strong>Canales → Calculadora de Precios</strong>.</li>
                        </ol>
                    </div>
                )}
            </div>

            {/* Selector de canal — destacado en su propia tarjeta */}
            <div className="flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
                <span className="text-sm font-bold text-gray-700 dark:text-slate-300">Canal:</span>
                {isLoadingCanales ? (
                    <span className="text-sm text-slate-400 dark:text-slate-500">Cargando canales...</span>
                ) : canales.length === 0 ? (
                    <span className="text-sm text-slate-400 dark:text-slate-500">No hay canales</span>
                ) : (
                    <CanalSelectBadge
                        canales={canales}
                        value={canalIdSel}
                        onChange={(id) => setCanalIdSel(id)}
                    />
                )}
                {view && !isLoadingView && (
                    <span className="text-xs text-slate-500 dark:text-slate-400">
                        {view.totalConceptos} concepto{view.totalConceptos === 1 ? "" : "s"} ·{" "}
                        {view.totalReglasExcepcion} regla{view.totalReglasExcepcion === 1 ? "" : "s"} de excepción ·{" "}
                        {view.cuotas.length} plan{view.cuotas.length === 1 ? "" : "es"} de cuotas
                    </span>
                )}
            </div>

            {/* Cuerpo */}
            {isLoadingView ? (
                <div className="flex h-64 items-center justify-center text-sm text-slate-500 dark:text-slate-400">
                    Cargando fórmula...
                </div>
            ) : !view ? (
                <div className="flex h-64 items-center justify-center text-sm text-slate-500 dark:text-slate-400">
                    Seleccioná un canal para ver su fórmula.
                </div>
            ) : (
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start">
                    {/* Columna principal: pipeline */}
                    <div className="flex min-w-0 flex-col gap-4 lg:flex-1">
                        {/* Header del canal: badge + toggle modo edición */}
                        <div className="flex items-center gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
                            <span className={`${CANAL_BADGE_CLASS} ${getCanalColor(view.canalNombre)}`}>{view.canalNombre}</span>
                            <span className="text-xs text-slate-400 dark:text-slate-500">
                                Resumen del cálculo
                            </span>
                            <div className="ml-auto">
                                <button
                                    type="button"
                                    onClick={() => setEditMode((v) => !v)}
                                    className={`inline-flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-xs font-bold transition ${
                                        editMode
                                            ? "border-blue-500 bg-blue-500 text-white hover:bg-blue-600"
                                            : "border-slate-300 bg-white text-slate-700 hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200"
                                    }`}
                                    title={editMode ? "Salir del modo edición" : "Entrar en modo edición para asignar/quitar/editar conceptos"}
                                >
                                    <PencilSquareIcon className="h-4 w-4" />
                                    {editMode ? "Cerrar edición" : "Editar"}
                                </button>
                            </div>
                        </div>
                        {editMode && (
                            <div className="rounded-md border border-blue-200 bg-blue-50 px-3 py-2 text-xs text-blue-900 dark:border-blue-800/60 dark:bg-blue-900/20 dark:text-blue-200">
                                <strong>Modo edición activo.</strong> Hacé click en el porcentaje de un concepto para editarlo,
                                en la <span className="font-bold">×</span> para quitarlo del canal, o en el <span className="font-bold">+</span> de cada etapa para agregar uno nuevo.
                                <br />
                                <span className="text-[11px] opacity-80">
                                    ⚠ Editar el porcentaje afecta al concepto en <strong>todos los canales</strong> donde esté asignado y dispara recálculo de precios.
                                </span>
                            </div>
                        )}

                        {/* Alerta: canal con canal base */}
                        {view.canalBaseNombre && (
                            <div className="rounded-xl border-l-4 border-l-amber-400 border-y border-r border-amber-200 bg-amber-50 p-4 shadow-sm dark:border-amber-800/60 dark:bg-amber-900/20">
                                <div className="flex items-start gap-2">
                                    <ExclamationTriangleIcon className="mt-0.5 h-5 w-5 shrink-0 text-amber-600 dark:text-amber-400" />
                                    <div className="text-sm text-amber-900 dark:text-amber-100">
                                        <p className="mb-1 font-bold">
                                            Este canal usa <span className="font-mono">{view.canalBaseNombre}</span> como canal base.
                                        </p>
                                        <p>
                                            El precio se calcula <strong>partiendo del PVP de {view.canalBaseNombre}</strong>.
                                            En el pipeline de abajo, <strong>solo los conceptos de la etapa &quot;Precio&quot; con &quot;Aplica Sobre = Cálculo sobre canal base&quot; (variantes <em>canal propio</em> o <em>reseller</em>) se aplican efectivamente</strong>.
                                            Los demás conceptos asignados, el margen y los porcentajes de cuotas se ignoran en este canal.
                                        </p>
                                        <ul className="mt-2 ml-4 list-disc text-xs">
                                            <li><strong>Canal propio</strong>: el factor escala tanto el PVP final como el ingreso del dueño (típico de un canal del propio negocio).</li>
                                            <li><strong>Reseller</strong>: el factor escala el PVP final pero el ingreso del dueño se &quot;corta&quot; — el reseller agrega su propio markup encima. Útil cuando el canal hijo es un revendedor que compra a un descuento mayorista.</li>
                                        </ul>
                                    </div>
                                </div>
                            </div>
                        )}

                        {/* Reglas de Canal */}
                        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
                            <div className="mb-3 flex items-center gap-2">
                                <FunnelIcon className="h-5 w-5 text-cyan-600 dark:text-cyan-400" />
                                <h2 className="text-base font-bold text-slate-800 dark:text-slate-100">
                                    Reglas de Canal — eligibilidad de productos
                                </h2>
                            </div>
                            {view.reglasCanal.length === 0 ? (
                                <div className="rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800 dark:border-emerald-800/60 dark:bg-emerald-900/20 dark:text-emerald-200">
                                    Sin reglas: <strong>todos</strong> los productos del catálogo aplican a este canal.
                                </div>
                            ) : (
                                <div className="flex flex-col gap-2">
                                    {reglasInclude.length > 0 && (
                                        <div className="rounded-md border border-emerald-200 bg-emerald-50 p-3 dark:border-emerald-800/60 dark:bg-emerald-900/20">
                                            <p className="mb-2 text-xs font-bold uppercase tracking-wide text-emerald-700 dark:text-emerald-300">
                                                Solo aplican productos que cumplen alguna de:
                                            </p>
                                            <div className="flex flex-col gap-1.5">
                                                {reglasInclude.map((r) => (
                                                    <ReglaLine key={r.id} regla={r} lookups={lookups} />
                                                ))}
                                            </div>
                                        </div>
                                    )}
                                    {reglasExclude.length > 0 && (
                                        <div className="rounded-md border border-red-200 bg-red-50 p-3 dark:border-red-800/60 dark:bg-red-900/20">
                                            <p className="mb-2 text-xs font-bold uppercase tracking-wide text-red-700 dark:text-red-300">
                                                Quedan EXCLUIDOS los productos que cumplen:
                                            </p>
                                            <div className="flex flex-col gap-1.5">
                                                {reglasExclude.map((r) => (
                                                    <ReglaLine key={r.id} regla={r} lookups={lookups} />
                                                ))}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>

                        {/* Pipeline */}
                        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
                            <div className="mb-4 flex items-center gap-2">
                                <BeakerIcon className="h-5 w-5 text-purple-600 dark:text-purple-400" />
                                <h2 className="text-base font-bold text-slate-800 dark:text-slate-100">
                                    Pipeline de cálculo
                                </h2>
                            </div>

                            {view.etapas.length === 0 && !editMode ? (
                                <p className="text-sm italic text-slate-500 dark:text-slate-400">
                                    Este canal no tiene conceptos asignados. El precio se forma sólo con costo + IVA del producto.
                                    {" "}Usá <strong>Editar</strong> arriba para asignarle conceptos.
                                </p>
                            ) : (() => {
                                // En modo edición, mostrar TODAS las etapas (incluso vacías) para que
                                // el usuario pueda agregar conceptos a etapas que aún no tienen ninguno.
                                // En modo lectura, mostrar solo las etapas con conceptos.
                                const etapasConIndice: { etapa: EtapaConConceptos; idx: number }[] = editMode
                                    ? ETAPAS_INFO.map((info, i) => ({
                                        etapa: view.etapas.find((e) => e.info.id === info.id) ?? { info, conceptos: [] },
                                        idx: i,
                                    }))
                                    : view.etapas.map((etapa) => ({
                                        etapa,
                                        idx: ETAPAS_INFO.findIndex((e) => e.id === etapa.info.id),
                                    }));
                                const conceptoIdsAsignados = new Set(view.etapas.flatMap((e) => e.conceptos.map((c) => c.conceptoId)));
                                return (
                                    <div className="flex flex-col gap-3">
                                        {/* Punto de partida */}
                                        <div className="rounded-md border-2 border-dashed border-slate-300 bg-slate-50 p-3 text-center text-sm font-semibold text-slate-700 dark:border-slate-600 dark:bg-slate-800/50 dark:text-slate-300">
                                            🏁 Costo del producto (base)
                                        </div>

                                        {etapasConIndice.map(({ etapa, idx }) => (
                                            <div key={etapa.info.id}>
                                                <div className="flex justify-center py-1">
                                                    <ArrowDownIcon className="h-5 w-5 text-slate-400" />
                                                </div>
                                                <div className={`overflow-hidden rounded-xl border-l-4 ${etapa.info.accentClass} border-y border-r border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900`}>
                                                    <div className={`flex items-center justify-between gap-3 border-b border-slate-100 px-4 py-2.5 dark:border-slate-700 ${etapa.info.colorClass}`}>
                                                        <div className="flex items-center gap-2">
                                                            <span className="text-lg">{etapa.info.icon}</span>
                                                            <div>
                                                                <span className="text-xs font-bold uppercase tracking-wider opacity-70">Etapa {idx + 1}</span>
                                                                <p className="text-sm font-bold leading-tight">{etapa.info.label}</p>
                                                            </div>
                                                        </div>
                                                        <div className="flex items-center gap-2">
                                                            <span className="text-[11px] opacity-70">{etapa.conceptos.length} concepto{etapa.conceptos.length === 1 ? "" : "s"}</span>
                                                            {editMode && (
                                                                <AddConceptoButton
                                                                    etapaId={etapa.info.id}
                                                                    allConceptos={allConceptos}
                                                                    asignados={conceptoIdsAsignados}
                                                                    onAsignar={handleAsignarConcepto}
                                                                />
                                                            )}
                                                        </div>
                                                    </div>
                                                    <div className="flex flex-col gap-2 p-3">
                                                        {etapa.conceptos.length === 0 ? (
                                                            <p className="text-center text-xs italic text-slate-400 py-2">
                                                                {editMode
                                                                    ? "Esta etapa no tiene conceptos asignados — usá Agregar para añadir uno."
                                                                    : "Sin conceptos en esta etapa."}
                                                            </p>
                                                        ) : etapa.conceptos.map((c) => (
                                                            <ConceptoCard
                                                                key={c.conceptoId}
                                                                concepto={c}
                                                                lookups={lookups}
                                                                editMode={editMode}
                                                                onEditPorcentaje={editMode ? handleEditarPorcentaje : undefined}
                                                                onQuitar={editMode ? handleQuitarConcepto : undefined}
                                                            />
                                                        ))}
                                                    </div>
                                                </div>
                                            </div>
                                        ))}

                                        {/* PVP final */}
                                        <div className="flex justify-center py-1">
                                            <ArrowDownIcon className="h-5 w-5 text-slate-400" />
                                        </div>
                                        <div className="rounded-md border-2 border-emerald-400 bg-emerald-50 p-3 text-center text-sm font-bold text-emerald-800 dark:border-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-200">
                                            💲 PVP final
                                        </div>
                                    </div>
                                );
                            })()}
                        </div>

                        {/* Fórmula final compuesta */}
                        <FormulaFinalCard
                            view={view}
                            cuotaSel={cuotaSel}
                            onCuotaChange={setCuotaSel}
                        />
                    </div>

                    {/* Columna lateral: cuotas (ancho fijo en desktop) */}
                    <aside className="flex w-full min-w-0 flex-col gap-4 lg:sticky lg:top-4 lg:w-80 lg:shrink-0">
                        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
                            <div className="mb-3 flex items-center gap-2">
                                <CreditCardIcon className="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
                                <h2 className="text-base font-bold text-slate-800 dark:text-slate-100">
                                    Planes de cuotas
                                </h2>
                            </div>
                            {view.cuotas.length === 0 ? (
                                <p className="text-sm italic text-slate-500 dark:text-slate-400">
                                    Este canal no tiene planes de cuotas configurados.
                                </p>
                            ) : (
                                <div className="flex flex-col gap-2">
                                    {view.cuotas.map((c) => (
                                        <CuotaCard key={c.id} cuota={c} />
                                    ))}
                                </div>
                            )}
                        </div>

                        <div className="rounded-xl border border-slate-200 bg-white p-4 text-xs text-slate-600 shadow-sm dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400">
                            <p className="mb-2 font-bold uppercase tracking-wide text-slate-500 dark:text-slate-400">Leyenda</p>
                            <ul className="space-y-1.5">
                                <li className="flex items-center gap-2"><span className="inline-block h-3 w-3 rounded border border-slate-200 bg-white"></span> Concepto sin reglas: aplica a todos los productos.</li>
                                <li className="flex items-center gap-2"><span className="inline-block h-3 w-3 rounded border border-amber-300 bg-amber-50"></span> Concepto con reglas: ramificación por filtros.</li>
                                <li className="flex items-center gap-2"><ReglaPill tipo="INCLUIR" /> aplica solo si cumple la condición.</li>
                                <li className="flex items-center gap-2"><ReglaPill tipo="EXCLUIR" /> NO aplica si cumple la condición.</li>
                            </ul>
                        </div>
                    </aside>
                </div>
            )}
        </main>
    );
}

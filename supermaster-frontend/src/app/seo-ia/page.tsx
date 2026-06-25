"use client";

import { useEffect, useState } from "react";
import Button from "../components/Button/Button";
import { useSeoIa } from "./useSeoIa";
import type { SeoCanal } from "./types";

const CANALES: { canal: SeoCanal; titulo: string }[] = [
    { canal: "HOGAR", titulo: "KT Hogar" },
    { canal: "GASTRO", titulo: "KT Gastro" },
];

export default function SeoIaPage() {
    const { prompts, uso, isLoading, isSaving, savePrompt } = useSeoIa();
    const [borradores, setBorradores] = useState<Record<string, string>>({});

    useEffect(() => {
        setBorradores(prev => {
            const next = { ...prev };
            prompts.forEach(p => { if (next[p.canal] === undefined) next[p.canal] = p.contenido; });
            return next;
        });
    }, [prompts]);

    const fmt = (n: number) => new Intl.NumberFormat("es-AR").format(n);

    return (
        <div className="mx-auto max-w-5xl px-4 py-6">
            <h1 className="mb-6 text-2xl font-bold text-slate-800 dark:text-slate-100">SEO IA — configuración y uso</h1>

            {/* Panel de uso */}
            <div className="mb-8 rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-800/60">
                <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">Uso de IA (acumulado)</h2>
                {uso ? (
                    <div className="flex flex-wrap gap-x-8 gap-y-2 text-sm text-slate-700 dark:text-slate-200">
                        <span><b>Consultas:</b> {fmt(uso.consultas)}</span>
                        <span><b>Tokens entrada:</b> {fmt(uso.tokensEntrada)}</span>
                        <span><b>Tokens salida:</b> {fmt(uso.tokensSalida)}</span>
                        <span><b>Costo:</b> US$ {uso.costoUsd.toFixed(4)}</span>
                        <span className="text-slate-500 dark:text-slate-400">
                            Modelo: {uso.modelo} · in US${uso.precioInput1m}/1M · out US${uso.precioOutput1m}/1M
                        </span>
                    </div>
                ) : (
                    <p className="text-sm text-slate-400">{isLoading ? "Cargando…" : "Sin datos de uso"}</p>
                )}
            </div>

            {/* Editores de prompt por canal */}
            <div className="space-y-6">
                {CANALES.map(({ canal, titulo }) => {
                    const p = prompts.find(x => x.canal === canal);
                    const valor = borradores[canal] ?? "";
                    return (
                        <div key={canal} className="rounded-xl border border-slate-200 p-4 dark:border-slate-700">
                            <div className="mb-2 flex items-center justify-between">
                                <h3 className="font-semibold text-slate-700 dark:text-slate-200">Prompt — {titulo}</h3>
                                {p?.fechaModificacion && (
                                    <span className="text-xs text-slate-400">
                                        Modificado: {new Date(p.fechaModificacion).toLocaleString("es-AR")}
                                    </span>
                                )}
                            </div>
                            <textarea
                                className="h-64 w-full rounded-lg border border-slate-300 bg-white p-3 font-mono text-xs text-slate-800 focus:border-primary focus:ring-primary dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100"
                                value={valor}
                                onChange={e => setBorradores(prev => ({ ...prev, [canal]: e.target.value }))}
                                disabled={isLoading}
                            />
                            <div className="mt-2 flex justify-end">
                                <Button
                                    variant="dark"
                                    onClick={() => savePrompt(canal, valor)}
                                    disabled={isSaving !== null || isLoading || !valor.trim()}
                                >
                                    {isSaving === canal ? "Guardando…" : "Guardar"}
                                </Button>
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}

"use client";

import { useEffect, useState } from "react";
import Button from "../components/Button/Button";
import { useSeoIa } from "./useSeoIa";
import type { SeoCanal } from "./types";
import { CANAL_LABEL } from "./types";
import { SparklesIcon } from "@heroicons/react/24/outline";

const CANALES: SeoCanal[] = ["HOGAR", "GASTRO"];

export default function SeoIaPage() {
    const { prompts, uso, imagenPrompt, imagenUso, imagenBorrador, setImagenBorrador, isLoading, isSaving, isSavingImagen, savePrompt, saveImagenPrompt } = useSeoIa();
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
        <main className="p-4 md:p-5 bg-gray-50 dark:bg-slate-900 flex flex-col gap-4">
            <div>
                <h1 className="text-3xl font-bold text-gray-800 dark:text-slate-100 flex items-center gap-3">
                    <SparklesIcon className="w-8 h-8 text-purple-600 dark:text-purple-400" />
                    SEO IA
                </h1>
                <p className="text-gray-500 dark:text-slate-400 text-sm mt-1">
                    Prompts de SEO de Tienda Nube y consumo de OpenAI
                </p>
            </div>

            {/* Panel de uso SEO */}
            <div className="mb-2 rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-800/60">
                <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">Uso de IA — SEO (acumulado)</h2>
                {uso ? (
                    <div className="flex flex-wrap gap-x-8 gap-y-2 text-sm text-slate-700 dark:text-slate-200">
                        <span><b>Consultas:</b> {fmt(uso.consultas)}</span>
                        <span><b>Tokens entrada:</b> {fmt(uso.tokensEntrada)}</span>
                        <span><b>Tokens salida:</b> {fmt(uso.tokensSalida)}</span>
                        <span><b>Costo:</b> US$ {uso.costoUsd.toFixed(4)}</span>
                        <span className="text-slate-500 dark:text-slate-400">
                            Modelo: {uso.modelo} · in US${uso.precioInput1m.toFixed(2)}/1M · out US${uso.precioOutput1m.toFixed(2)}/1M
                        </span>
                    </div>
                ) : (
                    <p className="text-sm text-slate-400">{isLoading ? "Cargando…" : "Sin datos de uso"}</p>
                )}
            </div>

            {/* Panel de uso carátula */}
            <div className="mb-8 rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-800/60">
                <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">Uso de IA — Carátula (acumulado)</h2>
                {imagenUso ? (
                    <div className="flex flex-wrap gap-x-8 gap-y-2 text-sm text-slate-700 dark:text-slate-200">
                        <span><b>Consultas:</b> {fmt(imagenUso.consultas)}</span>
                        <span><b>Tokens entrada:</b> {fmt(imagenUso.tokensEntrada)}</span>
                        <span><b>Tokens salida:</b> {fmt(imagenUso.tokensSalida)}</span>
                        <span><b>Costo:</b> US$ {imagenUso.costoUsd.toFixed(4)}</span>
                        <span className="text-slate-500 dark:text-slate-400">
                            Modelo: {imagenUso.modelo} · in US${imagenUso.precioInput1m.toFixed(2)}/1M · out US${imagenUso.precioOutput1m.toFixed(2)}/1M
                        </span>
                    </div>
                ) : (
                    <p className="text-sm text-slate-400">{isLoading ? "Cargando…" : "Sin datos de uso"}</p>
                )}
            </div>

            {/* Editores de prompt por canal */}
            <div className="space-y-6">
                {CANALES.map((canal) => {
                    const titulo = CANAL_LABEL[canal];
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

                {/* Tarjeta prompt de carátula */}
                <div className="rounded-xl border border-slate-200 p-4 dark:border-slate-700">
                    <div className="mb-2 flex items-center justify-between">
                        <h3 className="font-semibold text-slate-700 dark:text-slate-200">Prompt — Carátula</h3>
                        {imagenPrompt?.fechaModificacion && (
                            <span className="text-xs text-slate-400">
                                Modificado: {new Date(imagenPrompt.fechaModificacion).toLocaleString("es-AR")}
                            </span>
                        )}
                    </div>
                    <textarea
                        className="h-64 w-full rounded-lg border border-slate-300 bg-white p-3 font-mono text-xs text-slate-800 focus:border-primary focus:ring-primary dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100"
                        value={imagenBorrador}
                        onChange={e => setImagenBorrador(e.target.value)}
                        disabled={isLoading}
                    />
                    <div className="mt-2 flex justify-end">
                        <Button
                            variant="dark"
                            onClick={() => saveImagenPrompt(imagenBorrador)}
                            disabled={isSavingImagen || isLoading || !imagenBorrador.trim()}
                        >
                            {isSavingImagen ? "Guardando…" : "Guardar"}
                        </Button>
                    </div>
                </div>
            </div>
        </main>
    );
}

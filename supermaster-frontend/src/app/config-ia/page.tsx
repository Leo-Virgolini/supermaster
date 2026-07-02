"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import Button from "../components/Button/Button";
import Modal from "../components/Modal/Modal";
import { useSeoIa } from "./useSeoIa";
import { FORMATO_OPCIONES, MODEL_IMAGEN_OPCIONES, MODEL_SEO_OPCIONES, QUALITY_OPCIONES, SIZE_OPCIONES } from "./types";
import { SparklesIcon } from "@heroicons/react/24/outline";

function ConfigIaInner() {
    const { seoConfig, imagenConfig, uso, imagenUso, isLoading, isSavingSeo, isSavingImagen, saveSeoConfig, saveImagenConfig, resetSeoUso, resetImagenUso, isResettingSeo, isResettingImagen } = useSeoIa();

    // Borradores SEO
    const [promptHogar, setPromptHogar] = useState("");
    const [promptGastro, setPromptGastro] = useState("");
    const [seoModel, setSeoModel] = useState("");
    const [seoIn, setSeoIn] = useState("");
    const [seoOut, setSeoOut] = useState("");

    // Borradores Imagen
    const [imgPrompt, setImgPrompt] = useState("");
    const [imgModel, setImgModel] = useState("");
    const [imgSize, setImgSize] = useState("1024x1024");
    const [imgQuality, setImgQuality] = useState("high");
    const [imgFormato, setImgFormato] = useState("jpeg");
    const [imgIn, setImgIn] = useState("");
    const [imgOut, setImgOut] = useState("");

    const searchParams = useSearchParams();
    const tabInicial = searchParams.get("tab") === "caratula" ? "caratula" : "seo";
    const [tab, setTab] = useState<"seo" | "caratula">(tabInicial);

    // Confirmación de reseteo (diálogo propio en vez de window.confirm).
    const [resetPendiente, setResetPendiente] = useState<{ run: () => void; titulo: string } | null>(null);

    const precioInvalido = (v: string) => v.trim() === "" || !(Number(v) > 0);

    useEffect(() => {
        if (seoConfig) {
            setPromptHogar(seoConfig.promptHogar);
            setPromptGastro(seoConfig.promptGastro);
            setSeoModel(seoConfig.model);
            setSeoIn(String(seoConfig.precioInput1m));
            setSeoOut(String(seoConfig.precioOutput1m));
        }
    }, [seoConfig]);

    useEffect(() => {
        if (imagenConfig) {
            setImgPrompt(imagenConfig.contenido);
            setImgModel(imagenConfig.model);
            setImgSize(imagenConfig.size);
            setImgQuality(imagenConfig.quality);
            setImgFormato(imagenConfig.outputFormat);
            setImgIn(String(imagenConfig.precioInput1m));
            setImgOut(String(imagenConfig.precioOutput1m));
        }
    }, [imagenConfig]);

    const fmt = (n: number) => new Intl.NumberFormat("es-AR").format(n);
    const inputCls = "w-full rounded-lg border border-slate-300 bg-white p-2 text-sm text-slate-800 focus:border-primary focus:ring-primary dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100";
    const textareaCls = "h-64 w-full rounded-lg border border-slate-300 bg-white p-3 font-mono text-xs text-slate-800 focus:border-primary focus:ring-primary dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100";

    const guardarSeo = () => saveSeoConfig({
        promptHogar, promptGastro, model: seoModel,
        precioInput1m: Number(seoIn), precioOutput1m: Number(seoOut),
    });
    const guardarImagen = () => saveImagenConfig({
        contenido: imgPrompt, model: imgModel, size: imgSize, quality: imgQuality, outputFormat: imgFormato,
        precioInput1m: Number(imgIn), precioOutput1m: Number(imgOut),
    });

    const usoBox = (titulo: string, u: typeof uso, onReset: () => void, resetting: boolean) => (
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-800/60">
            <div className="mb-3 flex items-center justify-between">
                <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">{titulo}</h2>
                <Button variant="light" onClick={() => setResetPendiente({ run: onReset, titulo })} disabled={resetting || isLoading}>
                    {resetting ? "Reseteando…" : "Resetear"}
                </Button>
            </div>
            {u ? (<>
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                    {[
                        { label: "Consultas", valor: fmt(u.consultas), cls: "bg-blue-50 text-blue-700 ring-blue-200 dark:bg-blue-900/30 dark:text-blue-300 dark:ring-blue-800/60" },
                        { label: "Tokens entrada", valor: fmt(u.tokensEntrada), cls: "bg-indigo-50 text-indigo-700 ring-indigo-200 dark:bg-indigo-900/30 dark:text-indigo-300 dark:ring-indigo-800/60" },
                        { label: "Tokens salida", valor: fmt(u.tokensSalida), cls: "bg-violet-50 text-violet-700 ring-violet-200 dark:bg-violet-900/30 dark:text-violet-300 dark:ring-violet-800/60" },
                        { label: "Costo", valor: `US$ ${u.costoUsd.toFixed(4)}`, cls: "bg-emerald-50 text-emerald-700 ring-emerald-200 dark:bg-emerald-900/30 dark:text-emerald-300 dark:ring-emerald-800/60" },
                    ].map(s => (
                        <div key={s.label} className={`rounded-xl px-3 py-2 shadow-sm ring-1 ${s.cls}`}>
                            <div className="text-[11px] font-medium uppercase tracking-wide opacity-80">{s.label}</div>
                            <div className="text-lg font-bold tabular-nums">{s.valor}</div>
                        </div>
                    ))}
                </div>
                <p className="mt-2.5 text-xs text-slate-500 dark:text-slate-400">Modelo: <span className="font-medium text-slate-600 dark:text-slate-300">{u.modelo}</span> · in US${u.precioInput1m.toFixed(2)}/1M · out US${u.precioOutput1m.toFixed(2)}/1M</p>
            </>) : (
                <p className="text-sm text-slate-400">{isLoading ? "Cargando…" : "Sin datos de uso"}</p>
            )}
        </div>
    );

    const tabCls = (active: boolean) => `rounded-lg px-4 py-2 text-sm font-medium transition ${active ? "bg-primary text-white shadow-sm" : "bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"}`;

    return (
        <main className="p-4 md:p-5 bg-gray-50 dark:bg-slate-900 flex flex-col gap-4">
            <div>
                <h1 className="text-3xl font-bold text-gray-800 dark:text-slate-100 flex items-center gap-3">
                    <SparklesIcon className="w-8 h-8 text-purple-600 dark:text-purple-400" />
                    Configuración IA
                </h1>
                <p className="text-gray-500 dark:text-slate-400 text-sm mt-1">Configuración de OpenAI (prompts + parámetros) y consumo, por función</p>
            </div>

            <div className="flex gap-2">
                <button type="button" className={tabCls(tab === "seo")} onClick={() => setTab("seo")}>SEO</button>
                <button type="button" className={tabCls(tab === "caratula")} onClick={() => setTab("caratula")}>Carátula</button>
            </div>

            {tab === "seo" && (<>
            {usoBox("Uso de IA — SEO (acumulado)", uso, resetSeoUso, isResettingSeo)}

            {/* Config SEO */}
            <div className="rounded-xl border border-slate-200 p-4 dark:border-slate-700 space-y-3">
                <h3 className="font-semibold text-slate-700 dark:text-slate-200">Configuración SEO</h3>
                <div>
                    <label className="text-xs text-slate-500">Prompt — KT Hogar</label>
                    <textarea className={textareaCls} value={promptHogar} onChange={e => setPromptHogar(e.target.value)} disabled={isLoading} />
                </div>
                <div>
                    <label className="text-xs text-slate-500">Prompt — KT Gastro</label>
                    <textarea className={textareaCls} value={promptGastro} onChange={e => setPromptGastro(e.target.value)} disabled={isLoading} />
                </div>
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                    <div><label className="text-xs text-slate-500">Modelo</label>
                        <select className={inputCls} value={seoModel} onChange={e => setSeoModel(e.target.value)} disabled={isLoading}>
                            {!MODEL_SEO_OPCIONES.includes(seoModel) && seoModel !== "" && (
                                <option value={seoModel}>{seoModel}</option>
                            )}
                            {MODEL_SEO_OPCIONES.map(m => <option key={m} value={m}>{m}</option>)}
                        </select>
                    </div>
                    <div><label className="text-xs text-slate-500">US$ input / 1M</label><input type="number" step="0.0001" className={inputCls} value={seoIn} onChange={e => setSeoIn(e.target.value)} disabled={isLoading} /></div>
                    <div><label className="text-xs text-slate-500">US$ output / 1M</label><input type="number" step="0.0001" className={inputCls} value={seoOut} onChange={e => setSeoOut(e.target.value)} disabled={isLoading} /></div>
                </div>
                <div className="flex justify-end">
                    <Button variant="dark" onClick={guardarSeo} disabled={isSavingSeo || isLoading || !promptHogar.trim() || !promptGastro.trim() || !seoModel.trim() || precioInvalido(seoIn) || precioInvalido(seoOut)}>
                        {isSavingSeo ? "Guardando…" : "Guardar"}
                    </Button>
                </div>
            </div>
            </>)}

            {tab === "caratula" && (<>
            {usoBox("Uso de IA — Carátula (acumulado)", imagenUso, resetImagenUso, isResettingImagen)}

            {/* Config Imagen */}
            <div className="rounded-xl border border-slate-200 p-4 dark:border-slate-700 space-y-3">
                <h3 className="font-semibold text-slate-700 dark:text-slate-200">Configuración Carátula</h3>
                <div>
                    <label className="text-xs text-slate-500">Prompt</label>
                    <textarea className={textareaCls} value={imgPrompt} onChange={e => setImgPrompt(e.target.value)} disabled={isLoading} />
                </div>
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                    <div><label className="text-xs text-slate-500">Modelo</label>
                        <select className={inputCls} value={imgModel} onChange={e => setImgModel(e.target.value)} disabled={isLoading}>
                            {!MODEL_IMAGEN_OPCIONES.some(o => o.value === imgModel) && imgModel !== "" && (
                                <option value={imgModel}>{imgModel}</option>
                            )}
                            {MODEL_IMAGEN_OPCIONES.map(o => <option key={o.value} value={o.value}>{o.value}</option>)}
                        </select>
                    </div>
                    <div><label className="text-xs text-slate-500">Tamaño</label>
                        <select className={inputCls} value={imgSize} onChange={e => setImgSize(e.target.value)} disabled={isLoading}>
                            {SIZE_OPCIONES.map(o => <option key={o} value={o}>{o}</option>)}
                        </select>
                    </div>
                    <div><label className="text-xs text-slate-500">Calidad</label>
                        <select className={inputCls} value={imgQuality} onChange={e => setImgQuality(e.target.value)} disabled={isLoading}>
                            {QUALITY_OPCIONES.map(o => <option key={o} value={o}>{o}</option>)}
                        </select>
                    </div>
                    <div><label className="text-xs text-slate-500">Formato</label>
                        <select className={inputCls} value={imgFormato} onChange={e => setImgFormato(e.target.value)} disabled={isLoading}>
                            {FORMATO_OPCIONES.map(o => <option key={o} value={o}>{o}</option>)}
                        </select>
                    </div>
                    <div><label className="text-xs text-slate-500">US$ input / 1M</label><input type="number" step="0.0001" className={inputCls} value={imgIn} onChange={e => setImgIn(e.target.value)} disabled={isLoading} /></div>
                    <div><label className="text-xs text-slate-500">US$ output / 1M</label><input type="number" step="0.0001" className={inputCls} value={imgOut} onChange={e => setImgOut(e.target.value)} disabled={isLoading} /></div>
                </div>
                <div className="flex justify-end">
                    <Button variant="dark" onClick={guardarImagen} disabled={isSavingImagen || isLoading || !imgPrompt.trim() || !imgModel.trim() || precioInvalido(imgIn) || precioInvalido(imgOut)}>
                        {isSavingImagen ? "Guardando…" : "Guardar"}
                    </Button>
                </div>
            </div>
            </>)}

            <Modal
                isOpen={!!resetPendiente}
                onClose={() => setResetPendiente(null)}
                title="Resetear uso acumulado"
                size="sm"
                footer={<>
                    <Button variant="light" onClick={() => setResetPendiente(null)}>Cancelar</Button>
                    <Button variant="danger" onClick={() => { resetPendiente?.run(); setResetPendiente(null); }}>Sí, resetear</Button>
                </>}
            >
                <p className="text-sm text-slate-700 dark:text-slate-200">
                    ¿Seguro que querés resetear a cero el uso acumulado de <b>{resetPendiente?.titulo}</b>?
                </p>
                <p className="mt-2 text-xs text-amber-600 dark:text-amber-400">Esta acción no se puede deshacer.</p>
            </Modal>
        </main>
    );
}

export default function SeoIaPage() {
    return (
        <Suspense fallback={null}>
            <ConfigIaInner />
        </Suspense>
    );
}

"use client";

import React, { useRef } from "react";

type Props = {
    value: string;
    onChange: (v: string) => void;
    disabled?: boolean;
    placeholder?: string;
    rows?: number;
    id?: string;
};

/**
 * Editor de HTML sin dependencias: toolbar de formato + textarea con el HTML crudo + vista previa.
 * Los botones envuelven la selección actual con el tag correspondiente. Contenido interno/confiable;
 * la preview lo renderiza con dangerouslySetInnerHTML solo dentro del modal.
 */
export default function HtmlEditor({ value, onChange, disabled, placeholder, rows = 8, id }: Props) {
    const taRef = useRef<HTMLTextAreaElement>(null);
    // Selección + valor base capturados al abrir el selector de color. El <input type="color"> dispara
    // onChange en cada movimiento del arrastre: si recomputáramos desde el value mutado, anidaría un
    // <span> por cada disparo. Recomputamos SIEMPRE desde esta base → un único span (el último gana).
    const colorCtx = useRef<{ base: string; start: number; end: number } | null>(null);

    const inputClass =
        "w-full rounded-xl border border-slate-300 bg-white px-3 py-2 font-mono text-xs text-slate-800 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100";
    const btnClass =
        "rounded border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-200 dark:hover:bg-slate-600";

    const wrap = (prefix: string, suffix: string) => {
        const ta = taRef.current;
        if (!ta) return;
        const start = ta.selectionStart ?? value.length;
        const end = ta.selectionEnd ?? value.length;
        const sel = value.slice(start, end);
        const next = value.slice(0, start) + prefix + sel + suffix + value.slice(end);
        onChange(next);
        requestAnimationFrame(() => {
            const t = taRef.current;
            if (!t) return;
            t.focus();
            t.setSelectionRange(start + prefix.length, start + prefix.length + sel.length);
        });
    };

    // Captura la selección actual del textarea como base para el color (antes de abrir el picker).
    const capturarColor = () => {
        const ta = taRef.current;
        if (!ta) return;
        colorCtx.current = { base: value, start: ta.selectionStart ?? value.length, end: ta.selectionEnd ?? value.length };
    };

    // Aplica el color recomputando desde la base capturada (no desde el value mutado): evita anidar spans.
    const aplicarColor = (color: string) => {
        const ctx = colorCtx.current;
        if (!ctx || ctx.start === ctx.end) return; // sin selección: no envolver (evita spans vacíos)
        const { base, start, end } = ctx;
        const sel = base.slice(start, end);
        const prefix = `<span style="color:${color}">`;
        const next = base.slice(0, start) + prefix + sel + "</span>" + base.slice(end);
        onChange(next);
        requestAnimationFrame(() => {
            const t = taRef.current;
            if (!t) return;
            t.focus();
            t.setSelectionRange(start + prefix.length, start + prefix.length + sel.length);
        });
    };

    const insertarLista = () => {
        const ta = taRef.current;
        if (!ta) return;
        const start = ta.selectionStart ?? value.length;
        const end = ta.selectionEnd ?? value.length;
        const sel = value.slice(start, end);
        const items = sel.includes("\n")
            ? sel.split("\n").filter(l => l.trim() !== "").map(l => `<li>${l.trim()}</li>`).join("")
            : `<li>${sel}</li>`;
        const replacement = `<ul>${items}</ul>`;
        const next = value.slice(0, start) + replacement + value.slice(end);
        onChange(next);
        requestAnimationFrame(() => { taRef.current?.focus(); });
    };

    return (
        <div className="flex flex-col gap-2">
            <div className="flex flex-wrap items-center gap-1.5">
                <button type="button" className={btnClass} disabled={disabled} onClick={() => wrap("<b>", "</b>")} title="Negrita"><b>B</b></button>
                <button type="button" className={btnClass} disabled={disabled} onClick={() => wrap("<i>", "</i>")} title="Cursiva"><i>I</i></button>
                <button type="button" className={btnClass} disabled={disabled} onClick={() => wrap("<u>", "</u>")} title="Subrayado"><u>U</u></button>
                <button type="button" className={btnClass} disabled={disabled} onClick={insertarLista} title="Lista">• Lista</button>
                <label className="flex items-center gap-1 text-xs text-slate-500 dark:text-slate-400" title="Color de texto">
                    Color
                    <input type="color" disabled={disabled} defaultValue="#1e40af"
                        className="h-6 w-7 cursor-pointer rounded border border-slate-300 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600"
                        onMouseDown={capturarColor} onFocus={capturarColor}
                        onChange={e => aplicarColor(e.target.value)} />
                </label>
            </div>
            <div className="flex flex-col gap-3 lg:flex-row lg:items-stretch">
                <textarea
                    ref={taRef}
                    id={id}
                    className={`${inputClass} lg:w-1/2`}
                    value={value}
                    onChange={e => onChange(e.target.value)}
                    disabled={disabled}
                    placeholder={placeholder}
                    rows={rows}
                />
                <div className="lg:w-1/2">
                    <span className="mb-1 block text-xs font-medium text-slate-400 dark:text-slate-500">Vista previa</span>
                    <div
                        className="prose prose-sm max-w-none overflow-auto rounded-xl border border-dashed border-slate-300 bg-slate-50 px-3 py-2 text-sm text-slate-800 dark:border-slate-600 dark:bg-slate-800/60 dark:text-slate-100"
                        style={{ minHeight: `${rows * 1.5}rem` }}
                        dangerouslySetInnerHTML={{ __html: value || "<span style=\"color:#94a3b8\">(sin contenido)</span>" }}
                    />
                </div>
            </div>
        </div>
    );
}

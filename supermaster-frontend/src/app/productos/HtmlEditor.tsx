"use client";
import React, { useRef, useEffect } from "react";

type Props = { value: string; onChange: (v: string) => void; disabled?: boolean; placeholder?: string; rows?: number; id?: string };

export default function HtmlEditor({ value, onChange, disabled, placeholder, rows = 8, id }: Props) {
    const ref = useRef<HTMLDivElement>(null);
    // Solo re-escribir el HTML cuando el valor externo difiere y el editor NO tiene foco (no pisar cursor al tipear).
    useEffect(() => {
        const el = ref.current;
        if (el && document.activeElement !== el && el.innerHTML !== value) el.innerHTML = value || "";
    }, [value]);

    const exec = (cmd: string, arg?: string) => { ref.current?.focus(); document.execCommand(cmd, false, arg); onChange(ref.current?.innerHTML ?? ""); };
    const btn = "rounded border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-50 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-200";

    return (
        <div className="flex flex-col gap-2">
            <div className="flex flex-wrap items-center gap-1.5">
                <button type="button" className={btn} disabled={disabled} onClick={() => exec("bold")} title="Negrita"><b>B</b></button>
                <button type="button" className={btn} disabled={disabled} onClick={() => exec("italic")} title="Cursiva"><i>I</i></button>
                <button type="button" className={btn} disabled={disabled} onClick={() => exec("underline")} title="Subrayado"><u>U</u></button>
                <button type="button" className={btn} disabled={disabled} onClick={() => exec("insertUnorderedList")} title="Lista">• Lista</button>
                <label className="flex items-center gap-1 text-xs text-slate-500 dark:text-slate-400" title="Color de texto">
                    Color
                    <input type="color" disabled={disabled} defaultValue="#1e40af"
                        className="h-6 w-7 cursor-pointer rounded border border-slate-300 disabled:opacity-50 dark:border-slate-600"
                        onChange={e => exec("foreColor", e.target.value)} />
                </label>
            </div>
            <div
                ref={ref}
                id={id}
                contentEditable={!disabled}
                suppressContentEditableWarning
                onInput={() => onChange(ref.current?.innerHTML ?? "")}
                data-placeholder={placeholder}
                className="prose prose-sm max-w-none overflow-auto rounded-xl border border-slate-300 bg-white px-3 py-2 text-sm text-slate-800 shadow-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 empty:before:text-slate-400 empty:before:content-[attr(data-placeholder)] dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100"
                style={{ minHeight: `${rows * 1.5}rem` }}
            />
        </div>
    );
}

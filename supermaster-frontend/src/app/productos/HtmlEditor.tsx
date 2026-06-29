"use client";

import React from "react";

type Props = {
    value: string;
    onChange: (v: string) => void;
    disabled?: boolean;
    placeholder?: string;
    rows?: number;
    id?: string;
};

/**
 * Editor de HTML sin dependencias: textarea con el HTML crudo + vista previa en vivo.
 * El contenido es interno/confiable (lo genera el sistema o lo edita el usuario); la
 * preview lo renderiza con dangerouslySetInnerHTML solo dentro del modal.
 */
export default function HtmlEditor({ value, onChange, disabled, placeholder, rows = 8, id }: Props) {
    const inputClass =
        "w-full rounded-xl border border-slate-300 bg-white px-3 py-2 font-mono text-xs text-slate-800 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100";
    return (
        <div className="flex flex-col gap-3 lg:flex-row lg:items-stretch">
            <textarea
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
    );
}

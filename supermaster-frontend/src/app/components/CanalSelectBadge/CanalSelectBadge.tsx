"use client";
import { useEffect, useRef, useState } from "react";
import { ChevronDownIcon } from "@heroicons/react/24/outline";
import { getCanalColor, CANAL_BADGE_CLASS } from "../../utils/canalColors";

export interface CanalOption {
    id: number;
    nombre: string;
}

interface Props {
    canales: CanalOption[];
    value: number | null;
    onChange: (id: number | null) => void;
    disabled?: boolean;
    placeholder?: string;
    className?: string;
    /** Si es true, agrega una opción "Todos los canales" al tope que setea null. */
    allowAll?: boolean;
    /** Label de la opción "todos" cuando allowAll está activo. */
    allLabel?: string;
}

// Dropdown custom que renderiza cada canal como badge en el trigger y en las opciones.
export default function CanalSelectBadge({
    canales,
    value,
    onChange,
    disabled = false,
    placeholder = "Seleccionar canal",
    className = "",
    allowAll = false,
    allLabel = "— Todos los canales —",
}: Props) {
    const [open, setOpen] = useState(false);
    const containerRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!open) return;
        const handleClick = (e: MouseEvent) => {
            if (!containerRef.current?.contains(e.target as Node)) setOpen(false);
        };
        const handleKey = (e: KeyboardEvent) => {
            if (e.key === "Escape") setOpen(false);
        };
        document.addEventListener("mousedown", handleClick);
        document.addEventListener("keydown", handleKey);
        return () => {
            document.removeEventListener("mousedown", handleClick);
            document.removeEventListener("keydown", handleKey);
        };
    }, [open]);

    const selected = canales.find((c) => c.id === value) ?? null;

    return (
        <div ref={containerRef} className={`relative min-w-[220px] ${className}`}>
            <button
                type="button"
                onClick={() => !disabled && setOpen((v) => !v)}
                disabled={disabled}
                className={`flex w-full items-center justify-between gap-2 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-slate-800 outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100 dark:focus:border-blue-600 dark:focus:ring-blue-500/30 ${
                    disabled ? "cursor-not-allowed opacity-60" : "cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-700/50"
                }`}
            >
                {selected ? (
                    <span className={`${CANAL_BADGE_CLASS} ${getCanalColor(selected.nombre)}`}>{selected.nombre}</span>
                ) : allowAll ? (
                    <span className="text-slate-500 dark:text-slate-300">{allLabel}</span>
                ) : (
                    <span className="text-slate-400 dark:text-slate-500">{placeholder}</span>
                )}
                <ChevronDownIcon className={`h-4 w-4 shrink-0 text-slate-400 transition-transform ${open ? "rotate-180" : ""}`} />
            </button>
            {open && (
                <div className="absolute left-0 right-0 top-full z-30 mt-1 max-h-72 overflow-auto rounded-lg border border-slate-200 bg-white py-1 shadow-lg dark:border-slate-700 dark:bg-slate-900">
                    {allowAll && (
                        <button
                            key="__all__"
                            type="button"
                            onClick={() => { onChange(null); setOpen(false); }}
                            className={`flex w-full items-center justify-between gap-2 px-3 py-1.5 text-left text-sm font-semibold transition ${
                                value === null
                                    ? "bg-blue-50 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300"
                                    : "text-slate-600 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-800"
                            }`}
                        >
                            <span>{allLabel}</span>
                            {value === null && (
                                <span className="text-[10px] font-bold uppercase tracking-wide text-blue-600 dark:text-blue-400">
                                    Activo
                                </span>
                            )}
                        </button>
                    )}
                    {canales.map((c) => {
                        const isSelected = c.id === value;
                        return (
                            <button
                                key={c.id}
                                type="button"
                                onClick={() => { onChange(c.id); setOpen(false); }}
                                className={`flex w-full items-center justify-between gap-2 px-3 py-1.5 text-left transition ${
                                    isSelected
                                        ? "bg-blue-50 dark:bg-blue-500/15"
                                        : "hover:bg-slate-50 dark:hover:bg-slate-800"
                                }`}
                            >
                                <span className={`${CANAL_BADGE_CLASS} ${getCanalColor(c.nombre)}`}>{c.nombre}</span>
                                {isSelected && (
                                    <span className="text-[10px] font-bold uppercase tracking-wide text-blue-600 dark:text-blue-400">
                                        Activo
                                    </span>
                                )}
                            </button>
                        );
                    })}
                </div>
            )}
        </div>
    );
}

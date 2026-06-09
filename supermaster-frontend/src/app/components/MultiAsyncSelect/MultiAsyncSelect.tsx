"use client";

import React, { useEffect, useId, useRef, useState } from "react";
import { PlusIcon } from "@heroicons/react/24/outline";

export type MultiOption = { id: number | string; label: string };

type Props = {
    label: React.ReactNode;
    placeholder?: string;
    loadOptions: (inputValue: string) => Promise<MultiOption[]>;
    value: MultiOption[];
    onChange: (items: MultiOption[]) => void;
    inputClassName?: string;
};

/**
 * Selector múltiple con búsqueda asíncrona: los elegidos se muestran como chips
 * removibles arriba y se van agregando desde un dropdown de resultados.
 */
export default function MultiAsyncSelect({ label, placeholder, loadOptions, value, onChange, inputClassName = "" }: Props) {
    const [inputValue, setInputValue] = useState("");
    const [options, setOptions] = useState<MultiOption[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [isOpen, setIsOpen] = useState(false);
    const [hasPrefetched, setHasPrefetched] = useState(false);

    const timeoutRef = useRef<NodeJS.Timeout | null>(null);
    const containerRef = useRef<HTMLDivElement>(null);
    const inputId = useId();

    useEffect(() => {
        const onClickOutside = (e: MouseEvent) => {
            if (containerRef.current && !containerRef.current.contains(e.target as Node)) setIsOpen(false);
        };
        document.addEventListener("mousedown", onClickOutside);
        return () => document.removeEventListener("mousedown", onClickOutside);
    }, []);

    useEffect(() => () => { if (timeoutRef.current) clearTimeout(timeoutRef.current); }, []);

    const fetchOptions = (text: string) => {
        if (timeoutRef.current) clearTimeout(timeoutRef.current);
        timeoutRef.current = setTimeout(() => {
            setIsLoading(true);
            loadOptions(text)
                .then((res) => setOptions(res))
                .catch(() => setOptions([]))
                .finally(() => setIsLoading(false));
        }, 300);
    };

    const handleInputChange = (text: string) => {
        setInputValue(text);
        setIsOpen(true);
        fetchOptions(text);
    };

    const handleFocus = () => {
        setIsOpen(true);
        if (!hasPrefetched) {
            setHasPrefetched(true);
            fetchOptions("");
        }
    };

    const agregar = (opt: MultiOption) => {
        if (!value.some((v) => v.id === opt.id)) onChange([...value, opt]);
        setInputValue("");
    };
    const quitar = (id: MultiOption["id"]) => onChange(value.filter((v) => v.id !== id));

    const disponibles = options.filter((o) => !value.some((v) => v.id === o.id));

    return (
        <div ref={containerRef} className="relative w-full">
            <label htmlFor={inputId} className="block text-sm font-semibold text-slate-700 dark:text-slate-200">{label}</label>

            {value.length > 0 && (
                <div className="mb-1 mt-1 flex flex-wrap gap-1.5">
                    {value.map((v) => (
                        <span key={v.id} className="inline-flex items-center gap-1 rounded-full border border-blue-200 bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-700 dark:border-blue-800 dark:bg-blue-900/30 dark:text-blue-300">
                            {v.label}
                            <button type="button" onClick={() => quitar(v.id)} className="leading-none transition-colors hover:text-red-500" aria-label={`Quitar ${v.label}`}>×</button>
                        </span>
                    ))}
                </div>
            )}

            <input
                id={inputId}
                type="text"
                autoComplete="off"
                spellCheck={false}
                className={inputClassName}
                placeholder={placeholder || "Buscar..."}
                value={inputValue}
                onChange={(e) => handleInputChange(e.target.value)}
                onFocus={handleFocus}
            />

            {isOpen && (
                <ul className="absolute z-50 mt-1 max-h-60 w-full overflow-auto rounded-xl border border-gray-200 bg-white p-1 shadow-lg dark:border-slate-600 dark:bg-slate-800">
                    {isLoading ? (
                        <li className="p-2 text-sm text-gray-500 dark:text-slate-400">Buscando...</li>
                    ) : disponibles.length > 0 ? (
                        disponibles.map((opt) => (
                            <li
                                key={opt.id}
                                className="group flex cursor-pointer items-center justify-between gap-2 rounded-lg px-2.5 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-blue-50 dark:text-slate-200 dark:hover:bg-blue-900/30"
                                onClick={() => agregar(opt)}
                                onMouseDown={(e) => e.preventDefault()}
                            >
                                <span className="truncate">{opt.label}</span>
                                <PlusIcon className="h-4 w-4 shrink-0 text-blue-500 opacity-0 transition-opacity group-hover:opacity-100" />
                            </li>
                        ))
                    ) : (
                        <li className="p-2 text-sm text-gray-500 dark:text-slate-400">{options.length > 0 ? "Ya agregaste todos" : "No hay resultados"}</li>
                    )}
                </ul>
            )}
        </div>
    );
}

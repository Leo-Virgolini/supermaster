"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronDownIcon, MagnifyingGlassIcon } from "@heroicons/react/24/outline";

type Option = { id: number | string; label: string };

type Props = {
    /** Etiqueta visible arriba del control. */
    label: string;
    /** Parámetro de API que representa este filtro (p. ej. "marcaIds"). */
    apiParam: string;
    /** Cargador de opciones (reusa los search* de productosService). */
    loadOptions: (query: string, size?: number) => Promise<Option[]>;
    /** IDs actualmente seleccionados (desde el estado `filters`). */
    value: (number | string)[];
    /** Mapa id → nombre para mostrar el resumen sin tener que recargar. */
    valueLabels?: Record<string, string>;
    /** Aplica el cambio. Se llama al CERRAR el popover (un solo refetch). */
    onChange: (apiParam: string, ids: (number | string)[], labels: Record<string, string>) => void;
};

/**
 * Render de un label jerárquico "ABUELO > PADRE > HIJO": los ancestros van en
 * gris con separador "›" y el último segmento (el valor real) en negrita.
 * Mismo criterio visual que AsyncSelect.
 */
function renderOptionLabel(text: string) {
    const parts = text.split(" > ");
    const last = parts[parts.length - 1];
    const ancestors = parts.slice(0, -1);
    return (
        <span className="leading-snug">
            {ancestors.map((part, i) => (
                <span key={i} className="text-slate-400 dark:text-slate-500">
                    {part}
                    <span className="mx-0.5">›</span>
                </span>
            ))}
            <span className="font-semibold text-slate-900 dark:text-slate-100">{last}</span>
        </span>
    );
}

/**
 * Filtro multi-selección con buscador interno. Acumula la selección en estado
 * local mientras el popover está abierto y la aplica al cerrar — así un cambio
 * de varias opciones dispara un único refetch en vez de uno por click.
 */
export default function MultiSelectFilter({ label, apiParam, loadOptions, value, valueLabels = {}, onChange }: Props) {
    const [isOpen, setIsOpen] = useState(false);
    const [options, setOptions] = useState<Option[]>([]);
    const [loading, setLoading] = useState(false);
    const [search, setSearch] = useState("");
    const [selected, setSelected] = useState<(number | string)[]>(value);
    const [loaded, setLoaded] = useState(false);

    const containerRef = useRef<HTMLDivElement>(null);
    const searchRef = useRef<HTMLInputElement>(null);

    // Carga perezosa: sólo pega al backend la primera vez que se abre.
    // El fetch va en una IIFE async para no llamar setState de forma síncrona
    // dentro del cuerpo del effect.
    useEffect(() => {
        if (!isOpen || loaded) return;
        let cancelled = false;
        void (async () => {
            setLoading(true);
            try {
                const opts = await loadOptions("", 9999);
                if (!cancelled) { setOptions(opts); setLoaded(true); }
            } catch {
                if (!cancelled) setOptions([]);
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();
        return () => { cancelled = true; };
    }, [isOpen, loaded, loadOptions]);

    // Al abrir, arranca el borrador desde el valor aplicado actual (cubre
    // cambios externos: menú por columna, "Limpiar todo", vistas guardadas).
    const openPopover = () => {
        setSelected(value);
        setIsOpen(true);
    };

    useEffect(() => {
        if (isOpen) {
            const frame = requestAnimationFrame(() => searchRef.current?.focus());
            return () => cancelAnimationFrame(frame);
        }
    }, [isOpen]);

    // Aplica al cerrar (click afuera o Escape) si la selección cambió.
    const closeAndApply = () => {
        setIsOpen(false);
        setSearch("");
        const changed = selected.length !== value.length || selected.some((id) => !value.includes(id));
        if (!changed) return;
        const labels: Record<string, string> = {};
        for (const id of selected) {
            const opt = options.find((o) => o.id === id);
            labels[String(id)] = opt ? opt.label : (valueLabels[String(id)] ?? String(id));
        }
        onChange(apiParam, selected, labels);
    };

    useEffect(() => {
        if (!isOpen) return;
        const handleClickOutside = (e: MouseEvent) => {
            if (containerRef.current && !containerRef.current.contains(e.target as Node)) closeAndApply();
        };
        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isOpen, selected, value, options]);

    const toggle = (id: number | string) => {
        setSelected((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
    };

    const filteredOptions = useMemo(() => {
        const q = search.trim().toLowerCase();
        if (!q) return options;
        return options.filter((o) => o.label.toLowerCase().includes(q));
    }, [options, search]);

    // Resumen en el trigger: nombre si hay 1, "N sel." si hay varios.
    const summary = useMemo(() => {
        if (value.length === 0) return null;
        if (value.length === 1) {
            const id = value[0];
            const opt = options.find((o) => o.id === id);
            const full = opt?.label ?? valueLabels[String(id)];
            // En el trigger mostramos sólo el hijo final (el path completo se ve
            // al abrir el popover); más legible en el espacio acotado del chip.
            return full ? full.split(" > ").pop() : "1 sel.";
        }
        return `${value.length} sel.`;
    }, [value, options, valueLabels]);

    const hasSelection = value.length > 0;

    return (
        <div ref={containerRef} className="relative">
            <span className="mb-1 block text-xs font-semibold text-slate-600 dark:text-slate-300">{label}</span>
            <button
                type="button"
                onClick={() => (isOpen ? closeAndApply() : openPopover())}
                className={`flex w-full items-center justify-between gap-2 rounded-lg border px-2.5 py-2 text-left text-sm shadow-sm transition ${
                    hasSelection
                        ? "border-blue-300 bg-blue-50 text-blue-800 dark:border-blue-700 dark:bg-blue-900/30 dark:text-blue-200"
                        : "border-slate-200 bg-white text-slate-600 hover:border-slate-300 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300"
                }`}
            >
                <span className="truncate">{summary ?? <span className="text-slate-400 dark:text-slate-500">Todos</span>}</span>
                <ChevronDownIcon className={`h-4 w-4 shrink-0 text-slate-400 transition-transform ${isOpen ? "rotate-180" : ""}`} />
            </button>

            {isOpen && (
                <div className="absolute left-0 z-50 mt-1 flex max-h-80 w-[24rem] max-w-[90vw] flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl dark:border-slate-700 dark:bg-slate-800">
                    <div className="border-b border-slate-200 p-2 dark:border-slate-700">
                        <div className="relative">
                            <MagnifyingGlassIcon className="pointer-events-none absolute left-2 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                            <input
                                ref={searchRef}
                                type="text"
                                value={search}
                                onChange={(e) => setSearch(e.target.value)}
                                placeholder={`Buscar ${label.toLowerCase()}...`}
                                className="w-full rounded-lg border border-slate-200 bg-white py-1.5 pl-8 pr-2 text-sm text-slate-800 outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-200 dark:focus:ring-blue-500/20"
                            />
                        </div>
                    </div>

                    <div className="min-h-[120px] flex-1 overflow-y-auto p-1">
                        {loading ? (
                            <div className="p-3 text-xs text-slate-400 dark:text-slate-500">Cargando...</div>
                        ) : filteredOptions.length === 0 ? (
                            <div className="p-3 text-xs text-slate-400 dark:text-slate-500">Sin resultados</div>
                        ) : (
                            filteredOptions.map((opt) => {
                                const isSel = selected.includes(opt.id);
                                return (
                                    <button
                                        key={opt.id}
                                        type="button"
                                        onClick={() => toggle(opt.id)}
                                        className={`flex w-full items-start gap-2 rounded-lg px-2 py-1.5 text-left text-sm transition hover:bg-blue-50 dark:hover:bg-blue-900/30 ${isSel ? "bg-blue-50/60 dark:bg-blue-900/20" : ""}`}
                                    >
                                        <span className={`mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded border ${isSel ? "border-blue-600 bg-blue-600" : "border-slate-300 dark:border-slate-500"}`}>
                                            {isSel && <span className="text-[10px] leading-none text-white">✓</span>}
                                        </span>
                                        <span className="min-w-0 flex-1 break-words text-slate-700 dark:text-slate-200">{renderOptionLabel(opt.label)}</span>
                                    </button>
                                );
                            })
                        )}
                    </div>

                    <div className="flex items-center justify-between border-t border-slate-200 px-2 py-1.5 dark:border-slate-700">
                        <button
                            type="button"
                            onClick={() => setSelected([])}
                            disabled={selected.length === 0}
                            className="text-xs text-slate-500 hover:text-red-500 disabled:opacity-40 dark:text-slate-400"
                        >
                            Limpiar
                        </button>
                        <button
                            type="button"
                            onClick={closeAndApply}
                            className="rounded-lg bg-blue-600 px-3 py-1 text-xs font-medium text-white shadow-sm hover:bg-blue-700"
                        >
                            Aplicar
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}

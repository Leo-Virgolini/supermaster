"use client";

import React, { useEffect, useId, useRef, useState } from "react";

type Option = {
    id: number | string;
    label: string;
};

type Props = {
    label: React.ReactNode;
    placeholder?: string;
    loadOptions: (inputValue: string) => Promise<Option[]>;
    onChange: (value: string | number | null, label?: string) => void;
    value?: string | number | null;
    displayValue?: string;
    autoFocus?: boolean;
    inputClassName?: string;
};

export default function AsyncSelect({ label, placeholder, loadOptions, onChange, value, displayValue, autoFocus = false, inputClassName = "" }: Props) {
    const [inputValue, setInputValue] = useState(displayValue ?? "");
    const [options, setOptions] = useState<Option[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [isOpen, setIsOpen] = useState(false);
    const [activeIndex, setActiveIndex] = useState(-1);

    const timeoutRef = useRef<NodeJS.Timeout | null>(null);
    const containerRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLInputElement>(null);
    const hasPrefetchedRef = useRef(false);
    const inputId = useId();
    const listboxId = useId();

    useEffect(() => {
        if (displayValue !== undefined) {
            setInputValue(displayValue);
        }
    }, [displayValue]);

    useEffect(() => {
        if (!value && displayValue === undefined) {
            setInputValue("");
        }
    }, [value, displayValue]);

    useEffect(() => {
        setActiveIndex(options.length > 0 ? 0 : -1);
    }, [options]);

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
                setIsOpen(false);
                setActiveIndex(-1);
            }
        };

        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
    }, []);

    useEffect(() => {
        return () => {
            if (timeoutRef.current) clearTimeout(timeoutRef.current);
        };
    }, []);

    useEffect(() => {
        if (!autoFocus) return;

        const frame = requestAnimationFrame(() => {
            inputRef.current?.focus();
            inputRef.current?.select();
        });

        return () => cancelAnimationFrame(frame);
    }, [autoFocus]);

    const handleInputChange = (text: string) => {
        setInputValue(text);
        setIsOpen(true);
        setActiveIndex(-1);
        hasPrefetchedRef.current = true;

        if (timeoutRef.current) clearTimeout(timeoutRef.current);

        timeoutRef.current = setTimeout(async () => {
            if (text.trim().length === 0) {
                setOptions([]);
                setIsLoading(false);
                return;
            }

            setIsLoading(true);
            try {
                const results = await loadOptions(text);
                setOptions(results);
            } catch {
                setOptions([]);
            } finally {
                setIsLoading(false);
            }
        }, 300);
    };

    const prefetchOptions = async () => {
        if (hasPrefetchedRef.current) return;

        hasPrefetchedRef.current = true;
        setIsLoading(true);
        try {
            const results = await loadOptions(inputValue.trim());
            setOptions(results);
        } catch {
            setOptions([]);
        } finally {
            setIsLoading(false);
        }
    };

    const handleSelect = (opt: Option) => {
        setInputValue(opt.label);
        onChange(opt.id, opt.label);
        setIsOpen(false);
        setActiveIndex(-1);
    };

    /**
     * Render del label de una opción. El último segmento (o el único si no hay
     * jerarquía) es el valor efectivamente seleccionable y se muestra en
     * negrita. Cuando hay path "ABUELO > PADRE > HIJO", los ancestros se
     * muestran en gris suave antes del hijo.
     */
    const renderOptionLabel = (text: string) => {
        const parts = text.split(" > ");
        const last = parts[parts.length - 1];
        const ancestors = parts.slice(0, -1);
        return (
            <>
                {ancestors.map((part, i) => (
                    <span key={i} className="text-slate-400 dark:text-slate-500">
                        {part}
                        <span className="mx-1">›</span>
                    </span>
                ))}
                <span className="font-semibold text-slate-900 dark:text-slate-100">{last}</span>
            </>
        );
    };

    const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (!isOpen && (e.key === "ArrowDown" || e.key === "ArrowUp")) {
            setIsOpen(true);
            return;
        }

        if (e.key === "ArrowDown") {
            e.preventDefault();
            if (options.length > 0) {
                setActiveIndex((prev) => (prev + 1) % options.length);
            }
            return;
        }

        if (e.key === "ArrowUp") {
            e.preventDefault();
            if (options.length > 0) {
                setActiveIndex((prev) => (prev <= 0 ? options.length - 1 : prev - 1));
            }
            return;
        }

        if (e.key === "Enter" && isOpen && activeIndex >= 0 && options[activeIndex]) {
            e.preventDefault();
            handleSelect(options[activeIndex]);
            return;
        }

        if (e.key === "Escape") {
            setIsOpen(false);
            setActiveIndex(-1);
        }
    };

    return (
        <div ref={containerRef} className="relative w-full">
            <label htmlFor={inputId} className="block text-gray-700 text-sm font-bold mb-1">
                {label}
            </label>
            <input
                ref={inputRef}
                id={inputId}
                type="text"
                autoComplete="off"
                spellCheck={false}
                className={`block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring focus:ring-blue-200 p-2 border ${inputClassName}`}
                placeholder={placeholder || "Escribí para buscar..."}
                value={inputValue}
                onChange={(e) => handleInputChange(e.target.value)}
                onFocus={() => {
                    setIsOpen(true);
                    void prefetchOptions();
                }}
                onKeyDown={handleKeyDown}
                role="combobox"
                aria-expanded={isOpen}
                aria-controls={listboxId}
                aria-autocomplete="list"
                aria-activedescendant={activeIndex >= 0 ? `${listboxId}-option-${activeIndex}` : undefined}
                autoFocus={autoFocus}
            />

            {isOpen && (
                <ul
                    id={listboxId}
                    role="listbox"
                    className="absolute z-50 w-full bg-white dark:bg-slate-800 border border-gray-300 dark:border-slate-600 mt-1 rounded-md shadow-lg max-h-60 overflow-auto"
                >
                    {isLoading ? (
                        <li className="p-2 text-gray-500 dark:text-slate-400 text-sm">Buscando...</li>
                    ) : options.length > 0 ? (
                        options.map((opt, index) => (
                            <li
                                id={`${listboxId}-option-${index}`}
                                key={opt.id}
                                role="option"
                                aria-selected={index === activeIndex}
                                className={`p-2 cursor-pointer text-gray-700 dark:text-slate-200 ${index === activeIndex ? "bg-blue-50 dark:bg-slate-700" : "hover:bg-blue-50 dark:hover:bg-slate-700"}`}
                                onClick={() => handleSelect(opt)}
                                onMouseDown={(e) => e.preventDefault()}
                                onMouseEnter={() => setActiveIndex(index)}
                            >
                                {renderOptionLabel(opt.label)}
                            </li>
                        ))
                    ) : (
                        <li className="p-2 text-gray-500 dark:text-slate-400 text-sm">No hay resultados</li>
                    )}
                </ul>
            )}
        </div>
    );
}

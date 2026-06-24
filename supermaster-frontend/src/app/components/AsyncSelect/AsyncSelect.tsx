"use client";

import React, { useCallback, useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { renderHierarchyLabel } from "../HierarchyLabel/HierarchyLabel";

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
    const [isFocused, setIsFocused] = useState(false);
    const [activeIndex, setActiveIndex] = useState(-1);

    const timeoutRef = useRef<NodeJS.Timeout | null>(null);
    const containerRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLInputElement>(null);
    const listRef = useRef<HTMLUListElement>(null);
    const hasPrefetchedRef = useRef(false);
    const inputId = useId();
    const listboxId = useId();

    /**
     * Posición del dropdown. Lo renderizamos en un portal con position:fixed para
     * que flote por encima de todo y NO quede atrapado/recortado dentro de
     * contenedores con overflow (modales, celdas de tabla). Sin esto, el dropdown
     * forzaba scroll dentro del modal o se cortaba en las celdas inline.
     */
    const [menuPos, setMenuPos] = useState<{ top: number; left: number; width: number } | null>(null);

    const updateMenuPos = useCallback(() => {
        if (!inputRef.current) return;
        const rect = inputRef.current.getBoundingClientRect();
        const width = Math.max(rect.width, 320); // mínimo ~20rem para que entren los paths largos
        let left = rect.left;
        if (left + width > window.innerWidth - 8) left = window.innerWidth - width - 8;
        if (left < 8) left = 8;
        setMenuPos({ top: rect.bottom + 4, left, width });
    }, []);

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
            const target = event.target as Node;
            const insideContainer = containerRef.current?.contains(target);
            const insideList = listRef.current?.contains(target);
            if (!insideContainer && !insideList) {
                setIsOpen(false);
                setActiveIndex(-1);
            }
        };

        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
    }, []);

    // Mantener el dropdown pegado al input mientras está abierto (al scrollear o
    // redimensionar la ventana recalculamos su posición fija).
    useEffect(() => {
        if (!isOpen) return;
        updateMenuPos();
        window.addEventListener("scroll", updateMenuPos, true);
        window.addEventListener("resize", updateMenuPos);
        return () => {
            window.removeEventListener("scroll", updateMenuPos, true);
            window.removeEventListener("resize", updateMenuPos);
        };
    }, [isOpen, updateMenuPos]);

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

    // Mostramos el valor seleccionado con formato (último hijo en negrita) solo
    // cuando el campo no está enfocado; al enfocar se ve el texto plano editable.
    const showOverlay = !isFocused && inputValue.trim() !== "";

    return (
        <div ref={containerRef} className="relative w-full">
            <label htmlFor={inputId} className="block text-gray-700 text-sm font-bold mb-1">
                {label}
            </label>
            <div className="relative">
                <input
                    ref={inputRef}
                    id={inputId}
                    type="text"
                    autoComplete="off"
                    spellCheck={false}
                    className={`block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring focus:ring-blue-200 p-2 border ${showOverlay ? "!text-transparent" : ""} ${inputClassName}`}
                    placeholder={placeholder || "Escribí para buscar..."}
                    value={inputValue}
                    onChange={(e) => handleInputChange(e.target.value)}
                    onFocus={() => {
                        setIsFocused(true);
                        setIsOpen(true);
                        void prefetchOptions();
                    }}
                    onBlur={() => {
                        setIsFocused(false);
                        // Al salir del campo sincronizamos el valor con lo que se ve:
                        // - vacío => limpiar la selección (vaciar el texto = quitar el valor).
                        // - texto que no es la selección actual (se tipeó sin elegir una
                        //   opción) => revertir al valor seleccionado, sin dejar texto huérfano.
                        if (inputValue.trim() === "") {
                            if (value != null && value !== "") onChange(null);
                        } else if (inputValue !== (displayValue ?? "")) {
                            setInputValue(displayValue ?? "");
                        }
                    }}
                    onKeyDown={handleKeyDown}
                    role="combobox"
                    aria-expanded={isOpen}
                    aria-controls={listboxId}
                    aria-autocomplete="list"
                    aria-activedescendant={activeIndex >= 0 ? `${listboxId}-option-${activeIndex}` : undefined}
                    autoFocus={autoFocus}
                />
                {/* Overlay con el valor seleccionado formateado (último hijo en
                    negrita). Reusa el mismo className del input (mismo padding,
                    tamaño y borde) para alinear el texto exactamente; el borde se
                    fuerza transparente para no duplicar el del input. Solo cuando
                    el campo no tiene foco; al enfocar se ve el texto plano editable. */}
                {showOverlay && (
                    <div
                        aria-hidden="true"
                        className={`pointer-events-none absolute inset-0 flex items-center !border-transparent !bg-transparent !shadow-none p-2 border ${inputClassName}`}
                    >
                        <span className="min-w-0 truncate">{renderHierarchyLabel(inputValue)}</span>
                    </div>
                )}
            </div>

            {isOpen && menuPos && createPortal(
                <ul
                    ref={listRef}
                    id={listboxId}
                    role="listbox"
                    style={{ position: "fixed", top: menuPos.top, left: menuPos.left, width: menuPos.width, zIndex: 9999 }}
                    className="bg-white dark:bg-slate-800 border border-gray-300 dark:border-slate-600 rounded-md shadow-lg max-h-80 overflow-y-auto overflow-x-hidden"
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
                                className={`p-2 cursor-pointer break-words text-gray-700 dark:text-slate-200 ${index === activeIndex ? "bg-blue-50 dark:bg-slate-700" : "hover:bg-blue-50 dark:hover:bg-slate-700"}`}
                                onClick={() => handleSelect(opt)}
                                onMouseDown={(e) => e.preventDefault()}
                                onMouseEnter={() => setActiveIndex(index)}
                            >
                                {renderHierarchyLabel(opt.label)}
                            </li>
                        ))
                    ) : (
                        <li className="p-2 text-gray-500 dark:text-slate-400 text-sm">No hay resultados</li>
                    )}
                </ul>,
                document.body
            )}
        </div>
    );
}

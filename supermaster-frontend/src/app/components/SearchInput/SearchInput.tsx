"use client";
import { useState, useEffect, useRef } from "react";

type Props = {
    onSearch: (value: string) => void;
    placeholder?: string;
    delay?: number;
    className?: string;
    initialValue?: string;
    autoFocus?: boolean;
};

export default function SearchInput({
    onSearch,
    placeholder = "Escribí para buscar...",
    delay = 500,
    className = "",
    initialValue = "",
    autoFocus = true,
}: Props) {
    const [inputValue, setInputValue] = useState(initialValue);
    const inputRef = useRef<HTMLInputElement>(null);

    // Sincronizar con cambios externos (ej: clear all filters)
    useEffect(() => {
        setInputValue(initialValue);
    }, [initialValue]);

    useEffect(() => {
        const handler = setTimeout(() => {
            onSearch(inputValue);
        }, delay);

        return () => {
            clearTimeout(handler);
        };
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [inputValue, delay]);

    useEffect(() => {
        if (!autoFocus) return;

        const frame = requestAnimationFrame(() => {
            inputRef.current?.focus();
        });

        return () => cancelAnimationFrame(frame);
    }, [autoFocus]);

    return (
        <input
            ref={inputRef}
            type="text"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            autoComplete="off"
            spellCheck={false}
            placeholder={placeholder}
            autoFocus={autoFocus}
            className={`w-[26rem] max-w-full border border-gray-300 dark:border-slate-600 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500 outline-none transition-all text-gray-700 dark:text-slate-200 bg-white dark:bg-slate-800 placeholder:text-gray-400 dark:placeholder:text-slate-500 ${className}`}
        />
    );
}

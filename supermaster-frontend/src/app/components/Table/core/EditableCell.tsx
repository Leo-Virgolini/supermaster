"use client";

import { useState, useEffect, useId, useRef } from "react";
import { ArrowPathIcon, CheckCircleIcon, CheckIcon, ExclamationCircleIcon, XMarkIcon } from "@heroicons/react/20/solid";
import { useEditingCell } from "./EditingCellContext";
import { parseNumeroAR } from "../../../utils/parseNumero";

type EditableCellProps = {
    initialValue: string | number;
    onSave: (value: string | number | null) => void | Promise<void>;
    type?: "text" | "number";
    nullable?: boolean;
    className?: string;
    prefix?: string;
    suffix?: string;
    displayFormatter?: (value: string | number) => string;
    renderDisplay?: (value: string | number, openEditing: () => void) => React.ReactNode;
    disabled?: boolean;
};

const EditableCell = ({
    initialValue,
    onSave,
    type = "text",
    nullable = false,
    className = "",
    prefix = "",
    suffix = "",
    displayFormatter,
    renderDisplay,
    disabled = false,
}: EditableCellProps) => {
    const myId = useId();
    const { editingId, setEditingId } = useEditingCell();
    const isEditing = editingId === myId;

    const [value, setValue] = useState(initialValue);
    const [rawText, setRawText] = useState(String(initialValue));
    const [saveState, setSaveState] = useState<"idle" | "saving" | "success" | "error">("idle");
    const [saveError, setSaveError] = useState<string | null>(null);
    const successTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const resolvedFormatter = displayFormatter
        ?? (type === "number" && prefix.includes("$")
            ? (currentValue: string | number) =>
                Number(currentValue).toLocaleString("es-AR", Number.isInteger(Number(currentValue))
                    ? {
                        minimumFractionDigits: 0,
                        maximumFractionDigits: 0,
                    }
                    : {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2,
                    })
            : undefined);

    // Si la celda está en edición, no pisamos lo que el usuario tipeó. Sólo actualizamos
    // `value` (que se usa para el display cuando no está editando). De lo contrario, un
    // refresh de datos en background (ej. refreshRowLocal después de guardar en otra celda)
    // pisaría el input del usuario — o, si coincide con un remount, cerraría la edición.
    const isEditingRef = useRef(isEditing);
    isEditingRef.current = isEditing;
    useEffect(() => {
        setValue(initialValue);
        if (!isEditingRef.current) {
            setRawText(String(initialValue));
        }
    }, [initialValue]);

    useEffect(() => () => {
        if (successTimeoutRef.current) clearTimeout(successTimeoutRef.current);
    }, []);

    const markSuccess = () => {
        if (successTimeoutRef.current) clearTimeout(successTimeoutRef.current);
        setSaveState("success");
        successTimeoutRef.current = setTimeout(() => {
            setSaveState("idle");
            setSaveError(null);
        }, 1600);
    };

    const openEditing = () => {
        if (disabled) return;
        setRawText(value === "" || value === 0 ? "" : String(value));
        setSaveState("idle");
        setSaveError(null);
        setEditingId(myId);
    };

    const handleConfirm = async () => {
        if (saveState === "saving") return;
        const isEmpty = rawText.trim() === "";
        const normalizedInitial = initialValue === "" || initialValue === undefined ? null : initialValue;

        if (nullable && isEmpty) {
            if (normalizedInitial === null) {
                setValue("");
                setEditingId(null);
                setSaveState("idle");
                setSaveError(null);
                return;
            }

            try {
                setSaveState("saving");
                setSaveError(null);
                await Promise.resolve(onSave(null));
                setValue("");
                setEditingId(null);
                markSuccess();
            } catch (error) {
                setSaveState("error");
                setSaveError(error instanceof Error ? error.message : "No se pudo guardar");
            }
            return;
        }

        let finalValue: string | number;
        let sinCambios = false;
        if (type === "number") {
            const parsed = parseNumeroAR(rawText);
            if (parsed === null) {
                setSaveState("error");
                setSaveError("Valor numérico inválido");
                return;
            }
            finalValue = parsed;
            // Comparar numéricamente: initialValue puede venir como string ("100.5") o número (100.5).
            const parsedInitial = parseNumeroAR(String(initialValue));
            sinCambios = parsedInitial !== null && parsed === parsedInitial;
        } else {
            finalValue = rawText;
            sinCambios = finalValue === initialValue;
        }

        if (sinCambios) {
            setValue(finalValue);
            setEditingId(null);
            setSaveState("idle");
            setSaveError(null);
            return;
        }

        try {
            setSaveState("saving");
            setSaveError(null);
            await Promise.resolve(onSave(finalValue));
            setValue(finalValue);
            setEditingId(null);
            markSuccess();
        } catch (error) {
            setSaveState("error");
            setSaveError(error instanceof Error ? error.message : "No se pudo guardar");
        }
    };

    const handleCancel = () => {
        setValue(initialValue);
        setRawText(String(initialValue));
        setEditingId(null);
        setSaveState("idle");
        setSaveError(null);
    };

    const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === "Enter") void handleConfirm();
        if (e.key === "Escape") handleCancel();
    };

    if (isEditing) {
        return (
            <div className="relative">
                {/* spacer para mantener la altura de fila */}
                <div className={`invisible px-2 py-1 text-sm ${className}`}>{String(initialValue) || "—"}</div>
                {/* editor flotante */}
                <div
                    className="absolute left-1/2 -translate-x-1/2 top-1/2 -translate-y-1/2 z-20 flex items-center gap-1 bg-white dark:bg-slate-800 border border-blue-300 dark:border-blue-700 rounded shadow-lg px-1 py-0.5"
                    style={{ width: type === "number" ? 200 : Math.min(Math.max(String(value).length * 9 + 80, 260), 500) }}
                >
                    <input
                        type="text"
                        inputMode={type === "number" ? "decimal" : "text"}
                        value={rawText}
                        onChange={(e) => setRawText(e.target.value)}
                        onKeyDown={handleKeyDown}
                        autoFocus
                        disabled={saveState === "saving"}
                        className={`flex-1 min-w-0 px-2 py-1 focus:outline-none text-sm text-center bg-transparent text-gray-800 dark:text-slate-200 ${className}`}
                    />
                    {saveState === "error" && (
                        <span
                            className="shrink-0 inline-flex items-center gap-1 rounded-full bg-red-50 px-2 py-0.5 text-[10px] font-semibold text-red-600 dark:bg-red-500/10 dark:text-red-300"
                            title={saveError || "No se pudo guardar"}
                        >
                            <ExclamationCircleIcon className="h-3.5 w-3.5" />
                            Error
                        </span>
                    )}
                    <button
                        onMouseDown={(e) => { e.preventDefault(); void handleConfirm(); }}
                        disabled={saveState === "saving"}
                        className="shrink-0 text-green-600 hover:text-green-800 hover:bg-green-50 dark:hover:bg-green-900/30 p-1 rounded transition disabled:opacity-50"
                        title="Confirmar"
                    >
                        {saveState === "saving" ? <ArrowPathIcon className="w-4 h-4 animate-spin" /> : <CheckIcon className="w-4 h-4" />}
                    </button>
                    <button
                        onMouseDown={(e) => { e.preventDefault(); handleCancel(); }}
                        disabled={saveState === "saving"}
                        className="shrink-0 text-red-500 hover:text-red-700 hover:bg-red-50 dark:hover:bg-red-900/30 p-1 rounded transition disabled:opacity-50"
                        title="Cancelar"
                    >
                        <XMarkIcon className="w-4 h-4" />
                    </button>
                </div>
            </div>
        );
    }

    if (renderDisplay && value !== "" && value !== null && value !== undefined) {
        return (
            <div className="relative">
                {renderDisplay(value, openEditing)}
                {saveState === "success" && (
                    <span className="absolute left-1 top-1 inline-flex h-2 w-2 rounded-full bg-emerald-500 shadow-[0_0_0_3px_rgba(16,185,129,0.15)]" title="Celda actualizada" />
                )}
            </div>
        );
    }

    return (
        <div
            onClick={openEditing}
            className={`relative px-2 py-1 rounded text-sm text-center transition-colors ${
                disabled ? "cursor-default" : "cursor-text hover:bg-gray-100 dark:hover:bg-slate-700"
            } ${
                saveState === "success"
                    ? "bg-emerald-50 ring-1 ring-emerald-200 dark:bg-emerald-500/10 dark:ring-emerald-500/30"
                    : ""
            } ${className}`}
        >
            {value !== "" && value !== null && value !== undefined ? (
                <>{prefix}{resolvedFormatter ? resolvedFormatter(value) : value}{suffix}</>
            ) : (
                <span className="text-gray-400 dark:text-slate-500">—</span>
            )}
            {saveState === "success" && (
                <span className="absolute left-1 top-1 inline-flex h-2 w-2 rounded-full bg-emerald-500 shadow-[0_0_0_3px_rgba(16,185,129,0.15)]" title="Celda actualizada" />
            )}
            {saveState === "saving" && (
                <ArrowPathIcon className="absolute right-1 top-1 h-3.5 w-3.5 animate-spin text-blue-500" title="Guardando..." />
            )}
            {saveState === "success" && (
                <CheckCircleIcon className="absolute right-1 top-1 h-3.5 w-3.5 text-emerald-500" title="Guardado" />
            )}
            {saveState === "error" && (
                <ExclamationCircleIcon
                    className="absolute right-1 top-1 h-3.5 w-3.5 text-red-500"
                    title={saveError || "No se pudo guardar"}
                />
            )}
        </div>
    );
};

export default EditableCell;

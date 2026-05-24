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

    // Convierte un valor a lo que debe mostrarse en el input.
    // Devuelve "" para nulos/undefined/vacíos y para el 0 numérico (mostrar "0"
    // es ruido visual y dispara el bug "Tab → error" en celdas con valor 0).
    // `String(null) === "null"`, etc., así que NO basta con `String(v)`.
    const toRawText = (v: string | number | null | undefined): string => {
        if (v === null || v === undefined || v === "") return "";
        if (type === "number" && v === 0) return "";
        return String(v);
    };

    const [value, setValue] = useState(initialValue);
    const [rawText, setRawText] = useState(() => toRawText(initialValue));
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
            setRawText(toRawText(initialValue));
        }
        // toRawText cierra sobre `type` que es estable durante la vida del componente.
        // eslint-disable-next-line react-hooks/exhaustive-deps
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
        setRawText(toRawText(value));
        setSaveState("idle");
        setSaveError(null);
        setEditingId(myId);
    };

    // Devuelve true si la edición se cerró OK (sin cambios, o guardado exitoso) — útil
    // para que el caller (Tab) decida si avanzar a la siguiente celda. False si quedó
    // en error y conviene mantener al usuario en el input.
    const handleConfirm = async (): Promise<boolean> => {
        if (saveState === "saving") return false;
        const isEmpty = rawText.trim() === "";
        const normalizedInitial = initialValue === "" || initialValue === undefined ? null : initialValue;

        // Caso "abrir y salir sin tocar nada": si el input se dejó vacío y el valor
        // original también era efectivamente vacío (0 en números, "" en texto), no
        // hay cambio real → cerrar como "sin cambios" en lugar de tirar error.
        // Antes pasaba con Tab/Enter sobre una celda numérica que mostraba 0.
        if (isEmpty && !nullable) {
            const initialEsVacio = normalizedInitial === null
                || (type === "number" && Number(initialValue) === 0);
            if (initialEsVacio) {
                setEditingId(null);
                setSaveState("idle");
                setSaveError(null);
                return true;
            }
        }

        if (nullable && isEmpty) {
            if (normalizedInitial === null) {
                setValue("");
                setEditingId(null);
                setSaveState("idle");
                setSaveError(null);
                return true;
            }

            try {
                setSaveState("saving");
                setSaveError(null);
                await Promise.resolve(onSave(null));
                setValue("");
                setEditingId(null);
                markSuccess();
                return true;
            } catch (error) {
                setSaveState("error");
                setSaveError(error instanceof Error ? error.message : "No se pudo guardar");
                return false;
            }
        }

        let finalValue: string | number;
        let sinCambios = false;
        if (type === "number") {
            const parsed = parseNumeroAR(rawText);
            if (parsed === null) {
                setSaveState("error");
                setSaveError("Valor numérico inválido");
                return false;
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
            return true;
        }

        try {
            setSaveState("saving");
            setSaveError(null);
            await Promise.resolve(onSave(finalValue));
            setValue(finalValue);
            setEditingId(null);
            markSuccess();
            return true;
        } catch (error) {
            setSaveState("error");
            setSaveError(error instanceof Error ? error.message : "No se pudo guardar");
            return false;
        }
    };

    const handleCancel = () => {
        setValue(initialValue);
        setRawText(toRawText(initialValue));
        setEditingId(null);
        setSaveState("idle");
        setSaveError(null);
    };

    /**
     * Busca la siguiente celda editable (en orden DOM: izq→der, arriba→abajo) y
     * dispara click para abrirla. Si no encuentra, no hace nada. Pequeño delay
     * para esperar a que el editor actual se desmonte y devuelva el atributo
     * `data-editable-cell="display"` al wrapper.
     */
    const moverASiguienteEditable = (direccion: 1 | -1) => {
        // Doble RAF: uno espera el re-render que vuelve la celda a modo display,
        // el segundo asegura que el DOM esté pintado antes de querySelectorAll.
        requestAnimationFrame(() => requestAnimationFrame(() => {
            const todas = Array.from(document.querySelectorAll<HTMLElement>('[data-editable-cell="display"]'));
            const idx = todas.findIndex((el) => el.getAttribute("data-editable-cell-id") === myId);
            if (idx === -1) return;
            const objetivo = todas[idx + direccion];
            if (objetivo) objetivo.click();
        }));
    };

    const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === "Enter") {
            e.preventDefault();
            void handleConfirm();
            return;
        }
        if (e.key === "Escape") {
            e.preventDefault();
            handleCancel();
            return;
        }
        if (e.key === "Tab") {
            e.preventDefault();
            const direccion: 1 | -1 = e.shiftKey ? -1 : 1;
            void handleConfirm().then((ok) => {
                if (ok) moverASiguienteEditable(direccion);
            });
        }
    };

    if (isEditing) {
        // Texto para el spacer (mantener altura) y para calcular ancho del editor flotante.
        // Usamos toRawText para evitar "null"/"undefined" cuando initialValue es nulo.
        const spacerText = toRawText(initialValue) || "—";
        const widthBaseText = toRawText(value);
        return (
            <div className="relative">
                {/* spacer para mantener la altura de fila */}
                <div className={`invisible px-2 py-1 text-sm ${className}`}>{spacerText}</div>
                {/* editor flotante */}
                <div
                    className="absolute left-1/2 -translate-x-1/2 top-1/2 -translate-y-1/2 z-20 flex items-center gap-1 bg-white dark:bg-slate-800 border border-blue-300 dark:border-blue-700 rounded shadow-lg px-1 py-0.5"
                    style={{ width: type === "number" ? 200 : Math.min(Math.max(widthBaseText.length * 9 + 80, 260), 500) }}
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
            <div
                className={`relative transition-colors ${
                    saveState === "success"
                        ? "bg-emerald-100 ring-2 ring-emerald-400 dark:bg-emerald-500/20 dark:ring-emerald-400"
                        : ""
                }`}
                data-editable-cell={disabled ? undefined : "display"}
                data-editable-cell-id={myId}
                onClick={disabled ? undefined : openEditing}
            >
                {renderDisplay(value, openEditing)}
            </div>
        );
    }

    return (
        <div
            onClick={openEditing}
            data-editable-cell={disabled ? undefined : "display"}
            data-editable-cell-id={myId}
            className={`relative px-2 py-1 rounded text-sm text-center transition-colors ${
                disabled ? "cursor-default" : "cursor-text hover:bg-gray-100 dark:hover:bg-slate-700"
            } ${
                saveState === "success"
                    ? "bg-emerald-100 ring-2 ring-emerald-400 dark:bg-emerald-500/20 dark:ring-emerald-400"
                    : ""
            } ${className}`}
        >
            {value !== "" && value !== null && value !== undefined ? (
                <>{prefix}{resolvedFormatter ? resolvedFormatter(value) : value}{suffix}</>
            ) : (
                <span className="text-gray-400 dark:text-slate-500">—</span>
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

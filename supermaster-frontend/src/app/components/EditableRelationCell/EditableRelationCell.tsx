import { useEffect, useId, useRef } from "react";
import { CheckIcon, XMarkIcon } from "@heroicons/react/20/solid";
import { NoSymbolIcon } from "@heroicons/react/24/outline";
import AsyncSelect from "../AsyncSelect/AsyncSelect";
import { getNombreById } from "../../productos/productosService";
import { useEditingCell } from "../Table/core/EditingCellContext";
import { useState } from "react";

type RelationOption = {
    id: number | string;
    label: string;
};

export const EditableRelationCell = ({
    initialName,
    initialId,
    onSave,
    loadOptions,
    placeholder,
    endpoint,
    labelKey = "nombre",
    nullable = false,
    renderDisplay,
    displayClassName = "",
    inputClassName = "",
    disabled = false,
}: {
    initialName: string;
    initialId: number | null;
    onSave: (newId: number | null) => void;
    loadOptions: (inputValue: string) => Promise<RelationOption[]>;
    placeholder: string;
    endpoint: string;
    labelKey?: string;
    nullable?: boolean;
    renderDisplay?: (name: string, onClick: () => void) => React.ReactNode;
    displayClassName?: string;
    inputClassName?: string;
    disabled?: boolean;
}) => {
    const myId = useId();
    const { editingId, setEditingId } = useEditingCell();
    const isEditing = editingId === myId;

    const [pendingVal, setPendingVal] = useState<{ id: number | null; label: string } | null>(null);
    const [currentName, setCurrentName] = useState(initialName && initialName !== "---" ? initialName : "---");
    const [saveState, setSaveState] = useState<"idle" | "success">("idle");
    const successTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    useEffect(() => {
        let isMounted = true;
        const fetchName = async () => {
            if (initialId && (!initialName || initialName === "---")) {
                setCurrentName("...");
                const realName = await getNombreById(endpoint, initialId, labelKey);
                if (isMounted) setCurrentName(realName);
            } else if (initialName) {
                if (isMounted) setCurrentName(initialName);
            } else {
                if (isMounted) setCurrentName("---");
            }
        };
        fetchName();
        return () => { isMounted = false; };
    }, [initialId, initialName, endpoint, labelKey]);

    useEffect(() => () => {
        if (successTimeoutRef.current) clearTimeout(successTimeoutRef.current);
    }, []);

    const markSuccess = () => {
        if (successTimeoutRef.current) clearTimeout(successTimeoutRef.current);
        setSaveState("success");
        successTimeoutRef.current = setTimeout(() => {
            setSaveState("idle");
        }, 1600);
    };

    const handleSelect = (val: number | string | null | undefined, label?: string) => {
        if (val !== undefined) {
            setPendingVal({
                id: val === null ? null : Number(val),
                label: label ?? (val === null ? "---" : String(val)),
            });
        }
    };

    const handleClear = () => {
        setCurrentName("---");
        onSave(null);
        setEditingId(null);
        setPendingVal(null);
        markSuccess();
    };

    const handleConfirm = () => {
        if (pendingVal) {
            setCurrentName(pendingVal.label);
            onSave(pendingVal.id);
            markSuccess();
        }
        setEditingId(null);
        setPendingVal(null);
    };

    const handleCancel = () => {
        setEditingId(null);
        setPendingVal(null);
    };

    /**
     * Busca la siguiente celda editable (en orden DOM) y dispara click para abrirla.
     * Doble requestAnimationFrame para esperar a que el editor actual se desmonte
     * y devuelva el atributo `data-editable-cell="display"` al wrapper.
     */
    const moverASiguienteEditable = (direccion: 1 | -1) => {
        requestAnimationFrame(() => requestAnimationFrame(() => {
            const todas = Array.from(document.querySelectorAll<HTMLElement>('[data-editable-cell="display"]'));
            const idx = todas.findIndex((el) => el.getAttribute("data-editable-cell-id") === myId);
            if (idx === -1) return;
            const objetivo = todas[idx + direccion];
            if (objetivo) objetivo.click();
        }));
    };

    /**
     * Capturamos Tab/Shift+Tab en fase de captura para que el AsyncSelect no
     * mueva el foco a los botones de confirmar/cancelar internos: guardamos y
     * saltamos a la siguiente celda editable de la tabla.
     */
    const handleEditorKeyDownCapture = (e: React.KeyboardEvent<HTMLDivElement>) => {
        if (e.key === "Tab") {
            e.preventDefault();
            e.stopPropagation();
            const direccion: 1 | -1 = e.shiftKey ? -1 : 1;
            handleConfirm();
            moverASiguienteEditable(direccion);
        }
    };

    if (isEditing) {
        return (
            <div className="relative" onKeyDownCapture={handleEditorKeyDownCapture}>
                <div className="invisible px-2 py-1 text-sm">{currentName}</div>
                <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-20 flex items-center gap-1 bg-white dark:bg-slate-800 border border-blue-300 dark:border-blue-700 rounded shadow-lg px-1 py-0.5 min-w-[280px]">
                    <div className="flex-1 min-w-0">
                        <AsyncSelect
                            label=""
                            placeholder={placeholder}
                            loadOptions={loadOptions}
                            value={pendingVal?.id ?? initialId}
                            displayValue={pendingVal?.label === "---" ? "" : (pendingVal?.label ?? (currentName !== "---" ? currentName : ""))}
                            onChange={(val, label) => handleSelect(val, label)}
                            autoFocus
                            inputClassName={inputClassName}
                        />
                    </div>
                    {nullable && (
                        <button
                            onMouseDown={(e) => { e.preventDefault(); handleClear(); }}
                            className="shrink-0 text-gray-400 hover:text-orange-600 hover:bg-orange-50 dark:hover:bg-orange-900/30 p-1 rounded transition"
                            title="Quitar (dejar vacío)"
                        >
                            <NoSymbolIcon className="w-4 h-4" />
                        </button>
                    )}
                    <button
                        onMouseDown={(e) => { e.preventDefault(); handleConfirm(); }}
                        className="shrink-0 text-green-600 hover:text-green-800 hover:bg-green-50 dark:hover:bg-green-900/30 p-1 rounded transition"
                        title="Confirmar"
                    >
                        <CheckIcon className="w-4 h-4" />
                    </button>
                    <button
                        onMouseDown={(e) => { e.preventDefault(); handleCancel(); }}
                        className="shrink-0 text-red-500 hover:text-red-700 hover:bg-red-50 dark:hover:bg-red-900/30 p-1 rounded transition"
                        title="Cancelar"
                    >
                        <XMarkIcon className="w-4 h-4" />
                    </button>
                </div>
            </div>
        );
    }

    if (renderDisplay) {
        // Envolvemos en un div con data-* para que Tab desde otra celda editable
        // pueda saltar acá. El renderDisplay interno mantiene su propio onClick
        // (idempotente con el del wrapper, ambos abren la edición).
        return (
            <div
                className={`relative transition-colors ${
                    saveState === "success"
                        ? "bg-emerald-100 ring-2 ring-emerald-400 dark:bg-emerald-500/20 dark:ring-emerald-400"
                        : ""
                }`}
                data-editable-cell={disabled ? undefined : "display"}
                data-editable-cell-id={myId}
                onClick={disabled ? undefined : () => setEditingId(myId)}
            >
                {renderDisplay(currentName, () => { if (!disabled) setEditingId(myId); })}
            </div>
        );
    }

    return (
        <div
            onClick={() => { if (!disabled) setEditingId(myId); }}
            data-editable-cell={disabled ? undefined : "display"}
            data-editable-cell-id={myId}
            className={`relative px-2 py-1 rounded border min-h-[24px] flex items-center justify-center text-sm truncate transition-colors ${
                saveState === "success"
                    ? "border-emerald-400 bg-emerald-100 ring-2 ring-emerald-400 dark:border-emerald-400 dark:bg-emerald-500/20 dark:ring-emerald-400"
                    : "border-transparent"
            } ${
                disabled ? "cursor-default" : "cursor-pointer hover:bg-blue-50 dark:hover:bg-blue-900/20 hover:border-blue-200 dark:hover:border-blue-800"
            } ${displayClassName}`}
            title={disabled ? `ID: ${initialId || "N/A"}` : `ID: ${initialId || "N/A"} - Click para editar`}
        >
            {currentName}
        </div>
    );
};

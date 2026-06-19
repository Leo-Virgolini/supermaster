import { useEffect, useId, useRef } from "react";
import { CheckIcon, XMarkIcon } from "@heroicons/react/20/solid";
import { NoSymbolIcon } from "@heroicons/react/24/outline";
import AsyncSelect from "../AsyncSelect/AsyncSelect";
import { renderHierarchyLabel } from "../HierarchyLabel/HierarchyLabel";
import { getNombreById } from "../../productos/productosService";
import { useEditingCell } from "../Table/core/EditingCellContext";
import { useState } from "react";

type RelationOption = {
    id: number | string;
    /** Texto mostrado en el dropdown. Para entidades jerárquicas: path completo "A > B > C". */
    label: string;
};

export const EditableRelationCell = ({
    initialName = "",
    initialId,
    fullName,
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
    initialName?: string;
    initialId: number | null;
    /**
     * Path jerárquico completo "ABUELO > PADRE > HIJO" ya resuelto por el caller
     * (ej. la tabla de productos lo trae del backend). Si se provee, se muestra
     * la herencia completa SIN un fetch por celda; el nombre corto para el input
     * se deriva del último segmento.
     */
    fullName?: string | null;
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

    const [pendingVal, setPendingVal] = useState<{ id: number | null; label: string; nombreCorto: string } | null>(null);
    const [currentName, setCurrentName] = useState(initialName && initialName !== "---" ? initialName : "---");
    /** Path completo "A > B > C" para tooltip y para el displayValue del AsyncSelect mientras se edita. */
    const [currentFullName, setCurrentFullName] = useState<string | null>(null);
    const [saveState, setSaveState] = useState<"idle" | "success">("idle");
    const successTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    useEffect(() => {
        let isMounted = true;
        const fetchName = async () => {
            // El caller ya nos pasó el path completo (ej. tabla de productos):
            // lo usamos directo, sin fetch. El nombre corto para el input se
            // deriva del último segmento.
            if (fullName && fullName !== "---") {
                const corto = fullName.includes(" > ") ? (fullName.split(" > ").pop() ?? fullName) : fullName;
                if (isMounted) {
                    setCurrentName(corto);
                    setCurrentFullName(fullName);
                }
                return;
            }
            if (initialId && (!initialName || initialName === "---")) {
                setCurrentName("...");
                setCurrentFullName(null);
                const info = await getNombreById(endpoint, initialId, labelKey);
                if (isMounted) {
                    setCurrentName(info.nombre);
                    setCurrentFullName(info.nombreCompleto ?? null);
                }
            } else if (initialName) {
                if (isMounted) {
                    setCurrentName(initialName);
                    // No hacemos un fetch extra solo para el tooltip cuando ya
                    // tenemos el nombre: sería 1 request por celda en cada render.
                    setCurrentFullName(null);
                }
            } else {
                if (isMounted) {
                    setCurrentName("---");
                    setCurrentFullName(null);
                }
            }
        };
        fetchName();
        return () => { isMounted = false; };
    }, [initialId, initialName, fullName, endpoint, labelKey]);

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
            const fullLabel = label ?? (val === null ? "---" : String(val));
            // El AsyncSelect solo nos da el label visible. Para entidades
            // jerárquicas ese label es el path completo "A > B > C"; el corto
            // (último segmento) sirve para mostrar en la celda al confirmar.
            const nombreCorto = val === null
                ? "---"
                : (fullLabel.includes(" > ") ? fullLabel.split(" > ").pop() ?? fullLabel : fullLabel);
            setPendingVal({
                id: val === null ? null : Number(val),
                label: fullLabel,
                nombreCorto,
            });
        }
    };

    const handleClear = () => {
        setCurrentName("---");
        setCurrentFullName(null);
        onSave(null);
        setEditingId(null);
        setPendingVal(null);
        markSuccess();
    };

    const handleConfirm = () => {
        if (pendingVal) {
            setCurrentName(pendingVal.nombreCorto);
            setCurrentFullName(pendingVal.label !== pendingVal.nombreCorto ? pendingVal.label : null);
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
                <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-20 flex items-center gap-1 bg-white dark:bg-slate-800 border border-blue-300 dark:border-blue-700 rounded shadow-lg px-1 py-0.5 min-w-[340px]">
                    <div className="flex-1 min-w-0">
                        <AsyncSelect
                            label=""
                            placeholder={placeholder}
                            loadOptions={loadOptions}
                            value={pendingVal?.id ?? initialId}
                            // El input nativo solo acepta texto plano, así que mostramos
                            // únicamente el nombre corto (el "hijo final" — lo realmente
                            // seleccionado). El path completo "ABUELO > PADRE > HIJO" sigue
                            // visible en el dropdown (con el último segmento destacado) y
                            // en el tooltip de la celda.
                            displayValue={pendingVal?.label === "---"
                                ? ""
                                : (pendingVal?.nombreCorto ?? (currentName !== "---" ? currentName : ""))}
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

    // Tooltip prioriza la jerarquía completa "A > B > C" si está disponible
    // (entidades jerárquicas con nombreCompleto); cae al ID en su defecto.
    const baseTooltip = currentFullName ?? `ID: ${initialId || "N/A"}`;
    const tooltipDisplay = disabled ? baseTooltip : `${baseTooltip} - Click para editar`;

    // Para entidades jerárquicas mostramos el path completo (ancestros + hijo);
    // el último nivel va en negrita. `currentFullName` solo se puebla cuando hay
    // herencia real (con " > "), así que las relaciones planas se ven igual.
    const displayFull = currentFullName ?? currentName;
    const mostrarJerarquia = currentName !== "---" && displayFull.includes(" > ");

    return (
        <div
            onClick={() => { if (!disabled) setEditingId(myId); }}
            data-editable-cell={disabled ? undefined : "display"}
            data-editable-cell-id={myId}
            className={`relative px-2 py-1 rounded border min-h-[24px] flex items-center justify-center text-sm whitespace-nowrap transition-colors ${
                saveState === "success"
                    ? "border-emerald-400 bg-emerald-100 ring-2 ring-emerald-400 dark:border-emerald-400 dark:bg-emerald-500/20 dark:ring-emerald-400"
                    : "border-transparent"
            } ${
                disabled ? "cursor-default" : "cursor-pointer hover:bg-blue-50 dark:hover:bg-blue-900/20 hover:border-blue-200 dark:hover:border-blue-800"
            } ${displayClassName}`}
            title={tooltipDisplay}
        >
            {mostrarJerarquia ? renderHierarchyLabel(displayFull) : currentName}
        </div>
    );
};

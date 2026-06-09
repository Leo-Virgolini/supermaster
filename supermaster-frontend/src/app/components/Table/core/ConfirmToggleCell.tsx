"use client";

import { useState } from "react";
import { CheckIcon, XMarkIcon } from "@heroicons/react/24/outline";

type Props = {
    value: boolean;
    onConfirm: (newValue: boolean) => void;
    disabled?: boolean;
    trueLabel?: string;
    falseLabel?: string;
    /** Clases del badge cuando el valor (o preview) es true. */
    trueClassName: string;
    /** Clases del badge cuando el valor (o preview) es false. */
    falseClassName: string;
    /** Tooltip según el valor actual. */
    titleFor?: (value: boolean) => string;
};

/**
 * Celda de toggle booleano con confirmación inline: al clickear, muestra el
 * nuevo estado en modo "pendiente" + botones ✓/✗. El cambio se aplica recién al
 * ✓; el ✗ descarta. Evita modificar el dato con un clic accidental.
 */
export default function ConfirmToggleCell({
    value,
    onConfirm,
    disabled = false,
    trueLabel = "Sí",
    falseLabel = "No",
    trueClassName,
    falseClassName,
    titleFor,
}: Props) {
    const [pending, setPending] = useState<boolean | null>(null);

    const display = pending ?? value;
    const badgeClass = display ? trueClassName : falseClassName;
    const label = display ? trueLabel : falseLabel;
    const badgeBase = "inline-block px-2 py-0.5 rounded text-xs font-semibold transition-colors";

    if (disabled) {
        return <span className={`${badgeBase} ${badgeClass}`} title={titleFor?.(value)}>{label}</span>;
    }

    if (pending === null) {
        return (
            <button
                type="button"
                onClick={() => setPending(!value)}
                className={`${badgeBase} cursor-pointer ${badgeClass}`}
                title={titleFor?.(value) ?? "Clic para cambiar"}
            >
                {label}
            </button>
        );
    }

    return (
        <span className="inline-flex items-center gap-1">
            <span className={`${badgeBase} ring-1 ring-inset ring-amber-300 dark:ring-amber-500/50 ${badgeClass}`} title="Cambio pendiente — confirmá con ✓">
                {label}
            </span>
            <button
                type="button"
                onClick={() => { onConfirm(pending); setPending(null); }}
                className="flex h-5 w-5 items-center justify-center rounded-full bg-emerald-500 text-white shadow-sm transition hover:bg-emerald-600"
                title="Aplicar cambio"
            >
                <CheckIcon className="h-3 w-3" />
            </button>
            <button
                type="button"
                onClick={() => setPending(null)}
                className="flex h-5 w-5 items-center justify-center rounded-full bg-rose-500 text-white shadow-sm transition hover:bg-rose-600"
                title="Cancelar"
            >
                <XMarkIcon className="h-3 w-3" />
            </button>
        </span>
    );
}

import React from "react";

/**
 * Renderiza un path jerárquico "ABUELO > PADRE > HIJO": los ancestros en gris
 * suave (separados por "›") y el último segmento —el valor realmente
 * seleccionado— en negrita. Único punto de verdad para mostrar herencia, usado
 * por el dropdown del AsyncSelect y por las celdas de relación de las tablas.
 */
export function renderHierarchyLabel(text: string): React.ReactNode {
    const parts = text.split(" > ");
    const last = parts[parts.length - 1];
    return (
        <>
            {parts.slice(0, -1).map((part, i) => (
                <span key={i} className="text-slate-400 dark:text-slate-500">
                    {part}
                    <span className="mx-1">›</span>
                </span>
            ))}
            <span className="font-semibold text-slate-900 dark:text-slate-100">{last}</span>
        </>
    );
}

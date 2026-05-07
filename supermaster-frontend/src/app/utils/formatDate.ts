/**
 * Formatea una fecha ISO al estándar argentino: DD/MM/YYYY HH:MM:SS
 * Retorna "—" si el valor es nulo, vacío o inválido.
 */
export function formatFechaAR(fecha: string | null | undefined): string {
    if (!fecha) return "—";
    const d = new Date(fecha);
    if (isNaN(d.getTime())) return "—";
    const date = d.toLocaleDateString("es-AR", { day: "2-digit", month: "2-digit", year: "numeric" });
    const time = d.toLocaleTimeString("es-AR", { hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false });
    return `${date} ${time}`;
}


/** Texto estandarizado para valores vacíos */
export const EMPTY = "—";

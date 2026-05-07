/**
 * Parsea un string numérico tolerando formato argentino, estadounidense y mixto.
 *
 * Reglas:
 * - Strippea `$`, espacios y `%` final.
 * - Si hay punto y coma: el que aparece último es el separador decimal y el otro
 *   son separadores de miles.
 * - Si hay sólo coma: se interpreta como separador decimal (AR).
 * - Si hay sólo puntos y son múltiples: todos son separadores de miles (AR).
 * - Si hay sólo un punto: se interpreta como separador decimal (US).
 *
 * Devuelve `null` si el resultado no es un número finito. El caller es responsable
 * de decidir qué hacer con ese caso (mostrar error, rechazar el guardado, etc).
 *
 * Ejemplos:
 *   "$ 10.153,58" → 10153.58
 *   "10.153,58"   → 10153.58
 *   "10,15"       → 10.15
 *   "10.15"       → 10.15
 *   "10.123.456"  → 10123456
 *   "-1.234,5"    → -1234.5
 *   ""            → null
 *   "abc"         → null
 */
export function parseNumeroAR(raw: string | number | null | undefined): number | null {
    if (raw === null || raw === undefined) return null;
    if (typeof raw === "number") return isFinite(raw) ? raw : null;

    const s = String(raw).trim()
        .replace(/[$\s]/g, "")
        .replace(/%$/, "");
    if (s === "") return null;

    const hasComma = s.includes(",");
    const hasDot = s.includes(".");
    let normalized: string;

    if (hasComma && hasDot) {
        const lastComma = s.lastIndexOf(",");
        const lastDot = s.lastIndexOf(".");
        if (lastComma > lastDot) {
            normalized = s.replace(/\./g, "").replace(",", ".");
        } else {
            normalized = s.replace(/,/g, "");
        }
    } else if (hasComma) {
        normalized = s.replace(",", ".");
    } else if (hasDot) {
        const dotCount = (s.match(/\./g) ?? []).length;
        normalized = dotCount > 1 ? s.replace(/\./g, "") : s;
    } else {
        normalized = s;
    }

    const n = parseFloat(normalized);
    return isFinite(n) ? n : null;
}

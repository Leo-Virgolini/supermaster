/**
 * Lógica pura del filtro multi-canal del Monitor de Precios.
 *
 * La selección de canales se modela como un `number[]`:
 *   - `[]`        → TODOS (mostrar todos los canales)
 *   - `[n]`       → un único canal
 *   - `[n, m, …]` → varios canales
 *
 * Se mantiene aparte del componente para poder razonarla y testearla aislada.
 */

/** Selección de canales. `[]` significa TODOS. */
export type SeleccionCanal = number[];

/**
 * Aplica el toggle de un canal (o de la opción "all") sobre la selección actual,
 * con la regla de "TODOS exclusivo":
 *   - "all"  → siempre limpia la selección a `[]` (TODOS).
 *   - canal  → lo agrega si no estaba, o lo quita si estaba. Si al quitarlo la
 *              selección queda vacía, equivale a volver a TODOS.
 */
export function toggleCanal(actual: SeleccionCanal, toggled: number | "all"): SeleccionCanal {
    if (toggled === "all") return [];
    return actual.includes(toggled)
        ? actual.filter((c) => c !== toggled)
        : [...actual, toggled];
}

/**
 * Arma los parámetros de filtro de canal que se envían al backend según la
 * cantidad de canales seleccionados:
 *   - `[]`    → sin filtro de canal (TODOS).
 *   - `[n]`   → `{ canalId: n }` (preserva el comportamiento de un solo canal).
 *   - `[n,…]` → `{ canalIds: [n, …] }` (filtra por `canal.id IN (...)`).
 */
export function construirFiltroCanal(
    canales: SeleccionCanal,
): { canalId?: number; canalIds?: number[] } {
    if (canales.length === 0) return {};
    if (canales.length === 1) return { canalId: canales[0] };
    return { canalIds: canales };
}

/**
 * Con 2+ canales seleccionados las cuotas difieren entre canales y no hay una
 * descripción única, por lo que el filtro de cuotas se fuerza a "Todas" y se
 * deshabilita. Con 0 (TODOS) o 1 canal, el filtro de cuotas funciona normal.
 */
export function cuotasDeshabilitadas(canales: SeleccionCanal): boolean {
    return canales.length >= 2;
}

/**
 * Clave de persistencia (columnas ocultas / preset) en función de la selección:
 *   - exactamente 1 canal → clave por canal (preferencias propias de ese canal).
 *   - TODOS o multi       → clave compartida `multi`.
 */
export function claveCanalPersistencia(canales: SeleccionCanal): string {
    return canales.length === 1 ? String(canales[0]) : "multi";
}

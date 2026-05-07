const CATALOGO_COLORS = [
    "text-indigo-700 bg-indigo-100 dark:text-indigo-200 dark:bg-indigo-500/15",
    "text-cyan-700 bg-cyan-100 dark:text-cyan-200 dark:bg-cyan-500/15",
    "text-violet-700 bg-violet-100 dark:text-violet-200 dark:bg-violet-500/15",
    "text-sky-700 bg-sky-100 dark:text-sky-200 dark:bg-sky-500/15",
    "text-teal-700 bg-teal-100 dark:text-teal-200 dark:bg-teal-500/15",
    "text-blue-700 bg-blue-100 dark:text-blue-200 dark:bg-blue-500/15",
    "text-purple-700 bg-purple-100 dark:text-purple-200 dark:bg-purple-500/15",
    "text-emerald-700 bg-emerald-100 dark:text-emerald-200 dark:bg-emerald-500/15",
    "text-fuchsia-700 bg-fuchsia-100 dark:text-fuchsia-200 dark:bg-fuchsia-500/15",
    "text-amber-700 bg-amber-100 dark:text-amber-200 dark:bg-amber-500/15",
];

const catalogoColorCache = new Map<string, string>();
let catalogoColorIndex = 0;

export function getCatalogoColor(name: string): string {
    const key = name.trim().toUpperCase();
    if (catalogoColorCache.has(key)) return catalogoColorCache.get(key)!;
    const color = CATALOGO_COLORS[catalogoColorIndex % CATALOGO_COLORS.length];
    catalogoColorIndex += 1;
    catalogoColorCache.set(key, color);
    return color;
}

export const CATALOGO_BADGE_CLASS = "text-xs font-bold px-3 py-1 rounded-full uppercase tracking-[0.08em]";

type CanalPalette = { badge: string; border: string };

// Paleta rotativa para canales sin override explícito (round-robin por orden de aparición).
const CANAL_COLORS: CanalPalette[] = [
    { badge: "text-rose-700 bg-rose-100 dark:text-rose-200 dark:bg-rose-500/15",         border: "border-rose-400 dark:border-rose-500/60" },
    { badge: "text-emerald-700 bg-emerald-100 dark:text-emerald-200 dark:bg-emerald-500/15", border: "border-emerald-400 dark:border-emerald-500/60" },
    { badge: "text-orange-700 bg-orange-100 dark:text-orange-200 dark:bg-orange-500/15",    border: "border-orange-400 dark:border-orange-500/60" },
    { badge: "text-purple-700 bg-purple-100 dark:text-purple-200 dark:bg-purple-500/15",    border: "border-purple-400 dark:border-purple-500/60" },
    { badge: "text-teal-700 bg-teal-100 dark:text-teal-200 dark:bg-teal-500/15",           border: "border-teal-400 dark:border-teal-500/60" },
    { badge: "text-amber-700 bg-amber-100 dark:text-amber-200 dark:bg-amber-500/15",       border: "border-amber-400 dark:border-amber-500/60" },
    { badge: "text-pink-700 bg-pink-100 dark:text-pink-200 dark:bg-pink-500/15",           border: "border-pink-400 dark:border-pink-500/60" },
    { badge: "text-lime-700 bg-lime-100 dark:text-lime-200 dark:bg-lime-500/15",           border: "border-lime-400 dark:border-lime-500/60" },
    { badge: "text-red-700 bg-red-100 dark:text-red-200 dark:bg-red-500/15",              border: "border-red-400 dark:border-red-500/60" },
    { badge: "text-green-700 bg-green-100 dark:text-green-200 dark:bg-green-500/15",       border: "border-green-400 dark:border-green-500/60" },
    { badge: "text-fuchsia-700 bg-fuchsia-100 dark:text-fuchsia-200 dark:bg-fuchsia-500/15", border: "border-fuchsia-400 dark:border-fuchsia-500/60" },
    { badge: "text-yellow-700 bg-yellow-100 dark:text-yellow-200 dark:bg-yellow-500/15",    border: "border-yellow-400 dark:border-yellow-500/60" },
];

// Overrides por nombre de canal (clave normalizada: trim + uppercase). Tienen prioridad
// sobre el round-robin. Útil cuando un canal específico tiene que tener siempre el mismo
// color sin importar el orden en que se carga.
const CANAL_OVERRIDES: Record<string, CanalPalette> = {
    "LINEA GE": { badge: "text-sky-700 bg-sky-100 dark:text-sky-200 dark:bg-sky-500/15", border: "border-sky-400 dark:border-sky-500/60" },
    // Amarillo MercadoLibre (#FFE600 aprox). Texto oscuro para contraste.
    "ML": { badge: "text-yellow-900 bg-yellow-300 dark:text-yellow-950 dark:bg-yellow-300", border: "border-yellow-500 dark:border-yellow-400" },
    "MERCADO LIBRE": { badge: "text-yellow-900 bg-yellow-300 dark:text-yellow-950 dark:bg-yellow-300", border: "border-yellow-500 dark:border-yellow-400" },
    "MERCADOLIBRE": { badge: "text-yellow-900 bg-yellow-300 dark:text-yellow-950 dark:bg-yellow-300", border: "border-yellow-500 dark:border-yellow-400" },
};

const canalColorCache = new Map<string, CanalPalette>();
let canalColorNext = 0;

function getCanalPalette(name: string): CanalPalette {
    const key = name.trim().toUpperCase();
    const cached = canalColorCache.get(key);
    if (cached) return cached;
    const palette = CANAL_OVERRIDES[key] ?? CANAL_COLORS[canalColorNext++ % CANAL_COLORS.length];
    canalColorCache.set(key, palette);
    return palette;
}

export function getCanalColor(name: string): string {
    return getCanalPalette(name).badge;
}

export function getCanalBorderColor(name: string): string {
    return getCanalPalette(name).border;
}

export const CANAL_BADGE_CLASS = "text-xs font-bold px-3 py-1 rounded-full uppercase tracking-[0.08em]";

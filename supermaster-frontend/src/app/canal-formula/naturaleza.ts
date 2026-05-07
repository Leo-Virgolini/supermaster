// Metadata visual para los 8 valores de NaturalezaConcepto.
// La naturaleza define cómo el concepto IMPACTA LOS INDICADORES (ganancia,
// márgenes, markup) — independiente de aplicaSobre que define la matemática del PVP.

export type NaturalezaId =
    | "COSTO_PRODUCTO"
    | "COSTO_VENTA"
    | "IMPUESTO"
    | "MARKUP"
    | "INFLACION"
    | "DESCUENTO"
    | "BASE"
    | "COSMETICO";

export interface NaturalezaInfo {
    id: NaturalezaId;
    label: string;
    descripcion: string;
    badgeClass: string;   // bg + text para badge
    icon: string;         // emoji corto
}

export const NATURALEZAS_INFO: NaturalezaInfo[] = [
    {
        id: "COSTO_PRODUCTO",
        label: "Costo Producto",
        descripcion: "Forma parte del costo del producto (ej: financiación del proveedor). Reduce markup, no aparece como costo de venta.",
        badgeClass: "bg-sky-100 text-sky-800 dark:bg-sky-500/20 dark:text-sky-200",
        icon: "📦",
    },
    {
        id: "COSTO_VENTA",
        label: "Costo de Venta",
        descripcion: "Plata real que sale del negocio al vender (comisiones, fletes, marketing). Se resta de ingreso neto → reduce ganancia.",
        badgeClass: "bg-rose-100 text-rose-800 dark:bg-rose-500/20 dark:text-rose-200",
        icon: "💸",
    },
    {
        id: "IMPUESTO",
        label: "Impuesto",
        descripcion: "Se le paga al estado (IVA, IIBB). Se extrae del PVP y se resta del ingreso neto.",
        badgeClass: "bg-amber-100 text-amber-800 dark:bg-amber-500/20 dark:text-amber-200",
        icon: "📊",
    },
    {
        id: "MARKUP",
        label: "Markup",
        descripcion: "Define o ajusta el % de ganancia objetivo (margen minorista, mayorista, ajustes). Es la base sobre la que se calcula la ganancia.",
        badgeClass: "bg-emerald-100 text-emerald-800 dark:bg-emerald-500/20 dark:text-emerald-200",
        icon: "💰",
    },
    {
        id: "INFLACION",
        label: "Inflación",
        descripcion: "Sube el PVP sin ser plata que sale. El cliente paga el sobreprecio y queda como ganancia. Caso típico: precio tachado de marketing.",
        badgeClass: "bg-purple-100 text-purple-800 dark:bg-purple-500/20 dark:text-purple-200",
        icon: "📈",
    },
    {
        id: "DESCUENTO",
        label: "Descuento",
        descripcion: "Reduce el PVP final. No es plata extra que salga, solo rebaja el precio.",
        badgeClass: "bg-orange-100 text-orange-800 dark:bg-orange-500/20 dark:text-orange-200",
        icon: "🏷",
    },
    {
        id: "BASE",
        label: "Canal Base",
        descripcion: "Cambia el punto de partida del cálculo (toma PVP de canal base en lugar del costo del producto).",
        badgeClass: "bg-fuchsia-100 text-fuchsia-800 dark:bg-fuchsia-500/20 dark:text-fuchsia-200",
        icon: "🔗",
    },
    {
        id: "COSMETICO",
        label: "Cosmético",
        descripcion: "Solo afecta el precio mostrado/tachado. No afecta el PVP que paga el cliente ni los indicadores.",
        badgeClass: "bg-slate-100 text-slate-700 dark:bg-slate-500/20 dark:text-slate-300",
        icon: "✨",
    },
];

const NATURALEZA_BY_ID: Record<NaturalezaId, NaturalezaInfo> = NATURALEZAS_INFO.reduce(
    (acc, n) => { acc[n.id] = n; return acc; },
    {} as Record<NaturalezaId, NaturalezaInfo>,
);

const NATURALEZA_IDS: ReadonlySet<string> = new Set(NATURALEZAS_INFO.map((n) => n.id));

/**
 * Castea el campo `naturaleza` del DTO a `NaturalezaId`. Si el valor es desconocido,
 * cae a "INFLACION" como fallback no destructivo (no asume que es costo).
 */
export const toNaturalezaId = (naturaleza: string | null | undefined): NaturalezaId => {
    return naturaleza && NATURALEZA_IDS.has(naturaleza) ? (naturaleza as NaturalezaId) : "INFLACION";
};

export const getNaturalezaInfo = (naturaleza: string | null | undefined): NaturalezaInfo => {
    return NATURALEZA_BY_ID[toNaturalezaId(naturaleza)];
};

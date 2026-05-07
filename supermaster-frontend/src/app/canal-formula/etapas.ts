// Definición de las 5 etapas del cálculo de precio (metadata visual).
// El mapeo aplicaSobre → etapa vive en el backend (AplicaSobre.getEtapa()) y
// viene serializado en cada DTO como `etapa`. Acá solo definimos label/color/icon.

import type { EtapaId, EtapaInfo } from "./types";

export const ETAPAS_INFO: EtapaInfo[] = [
    {
        id: "COSTO",
        label: "Costo Base",
        descripcion: "Punto de partida: costo del producto y financiación de proveedor.",
        colorClass: "bg-blue-50 text-blue-900 border-blue-200 dark:bg-blue-950/40 dark:text-blue-200 dark:border-blue-800/60",
        badgeClass: "bg-blue-100 text-blue-800 dark:bg-blue-500/20 dark:text-blue-200",
        accentClass: "border-l-blue-400",
        icon: "📦",
    },
    {
        id: "MARGEN",
        label: "Margen",
        descripcion: "Selección del margen y ajustes sobre la ganancia objetivo.",
        colorClass: "bg-emerald-50 text-emerald-900 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-200 dark:border-emerald-800/60",
        badgeClass: "bg-emerald-100 text-emerald-800 dark:bg-emerald-500/20 dark:text-emerald-200",
        accentClass: "border-l-emerald-400",
        icon: "💰",
    },
    {
        id: "IMPUESTOS",
        label: "Impuestos",
        descripcion: "Aplicación del IVA y otros impuestos.",
        colorClass: "bg-amber-50 text-amber-900 border-amber-200 dark:bg-amber-950/40 dark:text-amber-200 dark:border-amber-800/60",
        badgeClass: "bg-amber-100 text-amber-800 dark:bg-amber-500/20 dark:text-amber-200",
        accentClass: "border-l-amber-400",
        icon: "📊",
    },
    {
        id: "PRECIO",
        label: "Precio",
        descripcion: "Comisiones, envíos y conversiones que llegan al PVP final.",
        colorClass: "bg-purple-50 text-purple-900 border-purple-200 dark:bg-purple-950/40 dark:text-purple-200 dark:border-purple-800/60",
        badgeClass: "bg-purple-100 text-purple-800 dark:bg-purple-500/20 dark:text-purple-200",
        accentClass: "border-l-purple-400",
        icon: "💲",
    },
    {
        id: "POST_PRECIO",
        label: "Post-Precio",
        descripcion: "Recargos por cuotas, descuentos e inflados que ajustan el PVP final.",
        colorClass: "bg-orange-50 text-orange-900 border-orange-200 dark:bg-orange-950/40 dark:text-orange-200 dark:border-orange-800/60",
        badgeClass: "bg-orange-100 text-orange-800 dark:bg-orange-500/20 dark:text-orange-200",
        accentClass: "border-l-orange-400",
        icon: "🏷",
    },
];

const ETAPA_IDS: ReadonlySet<EtapaId> = new Set(ETAPAS_INFO.map((e) => e.id));

/**
 * Castea el campo `etapa` del DTO a `EtapaId`. Si el backend manda un valor
 * desconocido (por ej. agregaron un nuevo Etapa pero el front todavía no
 * conoce), cae a "POST_PRECIO" para no romper la UI.
 */
export const toEtapaId = (etapa: string | null | undefined): EtapaId => {
    return etapa && ETAPA_IDS.has(etapa as EtapaId) ? (etapa as EtapaId) : "POST_PRECIO";
};

// Etiqueta legible de cada `aplicaSobre`.
export const APLICA_SOBRE_LABEL: Record<string, string> = {
    GASTO_SOBRE_COSTO: "% sobre Costo",
    FLAG_FINANCIACION_PROVEEDOR: "Financiación del proveedor",
    AJUSTE_MARGEN_PUNTOS: "Ajuste de margen (puntos)",
    AJUSTE_MARGEN_PROPORCIONAL: "Ajuste de margen (%)",
    FLAG_USAR_MARGEN_MINORISTA: "Margen minorista",
    FLAG_USAR_MARGEN_MAYORISTA: "Margen mayorista",
    GASTO_POST_GANANCIA: "% post-ganancia",
    FLAG_APLICAR_IVA: "Aplicar IVA",
    IMPUESTO_ADICIONAL: "Impuesto adicional",
    GASTO_POST_IMPUESTOS: "% post-impuestos",
    FLAG_INCLUIR_ENVIO: "Incluir costo de envío",
    COMISION_SOBRE_PVP: "Comisión sobre PVP",
    FLAG_COMISION_ML: "Comisión MercadoLibre",
    FLAG_INFLACION_ML: "Inflación MercadoLibre (% del MLA)",
    INFLACION_SOBRE_PVP: "Inflación sobre PVP (% propio)",
    CALCULO_SOBRE_CANAL_BASE: "Cálculo sobre canal base (canal propio)",
    CALCULO_SOBRE_CANAL_BASE_RESELLER: "Cálculo sobre canal base (reseller)",
    RECARGO_CUPON: "Recargo por cupón",
    DESCUENTO_PORCENTUAL: "Descuento %",
    INFLACION_DIVISOR: "Inflación divisor",
    GASTO_FUERA_PVP: "Gasto fuera de PVP",
    FLAG_APLICAR_PRECIO_INFLADO: "Precio inflado",
};

export const isFlag = (aplicaSobre: string) => aplicaSobre.startsWith("FLAG_");

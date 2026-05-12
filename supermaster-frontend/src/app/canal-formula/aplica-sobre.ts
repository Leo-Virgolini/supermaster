// Metadata visual para los 20 valores de AplicaSobre.
// AplicaSobre define la MATEMÁTICA del PVP (qué etapa, qué fórmula). Esto es
// independiente de la NaturalezaConcepto, que define cómo impacta los indicadores.
// Patrón paralelo a NATURALEZAS_INFO (ver naturaleza.ts).

import type { EtapaId } from "./types";

// Badge class por etapa. Mantenido aquí en vez de derivarlo de ETAPAS_INFO
// para evitar la dependencia circular (etapas.ts re-exporta de este archivo).
// Si cambia el color de una etapa en etapas.ts, sincronizar acá también.
const BADGE_CLASS_POR_ETAPA: Record<EtapaId, string> = {
    COSTO: "bg-blue-100 text-blue-800 dark:bg-blue-500/20 dark:text-blue-200",
    MARGEN: "bg-emerald-100 text-emerald-800 dark:bg-emerald-500/20 dark:text-emerald-200",
    IMPUESTOS: "bg-amber-100 text-amber-800 dark:bg-amber-500/20 dark:text-amber-200",
    PRECIO: "bg-purple-100 text-purple-800 dark:bg-purple-500/20 dark:text-purple-200",
    POST_PRECIO: "bg-orange-100 text-orange-800 dark:bg-orange-500/20 dark:text-orange-200",
};

export type AplicaSobreId =
    // ===== ETAPA: COSTO =====
    | "GASTO_SOBRE_COSTO"
    | "FLAG_FINANCIACION_PROVEEDOR"
    // ===== ETAPA: MARGEN =====
    | "AJUSTE_MARGEN_PUNTOS"
    | "AJUSTE_MARGEN_PROPORCIONAL"
    | "FLAG_USAR_MARGEN_MINORISTA"
    | "FLAG_USAR_MARGEN_MAYORISTA"
    | "GASTO_POST_GANANCIA"
    // ===== ETAPA: IMPUESTOS =====
    | "FLAG_APLICAR_IVA"
    | "IMPUESTO_EN_FACTOR_IMP"
    | "GASTO_POST_IMPUESTOS"
    // ===== ETAPA: PRECIO =====
    | "FLAG_INCLUIR_ENVIO"
    | "COMISION_SOBRE_PVP"
    | "FLAG_COMISION_ML"
    | "CALCULO_SOBRE_CANAL_BASE"
    | "CALCULO_SOBRE_CANAL_BASE_RESELLER"
    // ===== ETAPA: POST_PRECIO =====
    | "COSTO_OCULTO_PVP"
    | "DESCUENTO_PORCENTUAL"
    | "INFLACION_DIVISOR_FINAL"
    | "GASTO_SIN_INFLAR_PVP"
    | "FLAG_APLICAR_PRECIO_INFLADO";

export interface AplicaSobreInfo {
    id: AplicaSobreId;
    /** Etiqueta legible larga (para tooltips, modales, manuales). */
    label: string;
    /** Etiqueta corta para chips/badges en tablas (≤22 chars). */
    labelCorto: string;
    /** Descripción de 1 línea para mostrar al lado del badge en la guía. */
    descripcion: string;
    /** Emoji propio del concepto (reemplaza la bandera ⚑ histórica). */
    icon: string;
    /** Etapa del cálculo a la que pertenece (determina el color de badge). */
    etapa: EtapaId;
}

export const APLICA_SOBRE_INFO: AplicaSobreInfo[] = [
    // ===== ETAPA: COSTO =====
    {
        id: "GASTO_SOBRE_COSTO",
        label: "Gasto sobre Costo",
        labelCorto: "% sobre Costo",
        descripcion: "Multiplica el costo base: COSTO × (1 + %/100).",
        icon: "📦",
        etapa: "COSTO",
    },
    {
        id: "FLAG_FINANCIACION_PROVEEDOR",
        label: "Financiación de proveedor",
        labelCorto: "Financ. Prov.",
        descripcion: "Usa el % de financiación del proveedor del producto.",
        icon: "🚩",
        etapa: "COSTO",
    },

    // ===== ETAPA: MARGEN =====
    {
        id: "AJUSTE_MARGEN_PUNTOS",
        label: "Ajuste de margen (puntos)",
        labelCorto: "Margen pts",
        descripcion: "Suma/resta puntos al margen. Ej: 60% + 25pts = 85%.",
        icon: "➕",
        etapa: "MARGEN",
    },
    {
        id: "AJUSTE_MARGEN_PROPORCIONAL",
        label: "Ajuste de margen (%)",
        labelCorto: "Margen %",
        descripcion: "Ajusta proporcionalmente: margen × (1 + %/100).",
        icon: "✖️",
        etapa: "MARGEN",
    },
    {
        id: "FLAG_USAR_MARGEN_MINORISTA",
        label: "Margen minorista",
        labelCorto: "Mg. Minorista",
        descripcion: "Usa el margen minorista del producto (por defecto si no hay flag).",
        icon: "🚩",
        etapa: "MARGEN",
    },
    {
        id: "FLAG_USAR_MARGEN_MAYORISTA",
        label: "Margen mayorista",
        labelCorto: "Mg. Mayorista",
        descripcion: "Usa el margen mayorista del producto en vez del minorista.",
        icon: "🚩",
        etapa: "MARGEN",
    },
    {
        id: "GASTO_POST_GANANCIA",
        label: "Gasto post-ganancia",
        labelCorto: "Post Ganancia",
        descripcion: "Multiplica después de la ganancia, antes de impuestos.",
        icon: "🪙",
        etapa: "MARGEN",
    },

    // ===== ETAPA: IMPUESTOS =====
    {
        id: "FLAG_APLICAR_IVA",
        label: "Aplicar IVA",
        labelCorto: "Aplicar IVA",
        descripcion: "Habilita el IVA del producto para el canal. Sin este flag, IVA = 0%.",
        icon: "🚩",
        etapa: "IMPUESTOS",
    },
    {
        id: "IMPUESTO_EN_FACTOR_IMP",
        label: "Impuesto en factor IMP",
        labelCorto: "Imp. en IMP",
        descripcion: "Se suma al factor de impuestos junto al IVA. Ej: IIBB.",
        icon: "📊",
        etapa: "IMPUESTOS",
    },
    {
        id: "GASTO_POST_IMPUESTOS",
        label: "Gasto post-impuestos",
        labelCorto: "Post Impuestos",
        descripcion: "Multiplica después de aplicar los impuestos.",
        icon: "💵",
        etapa: "IMPUESTOS",
    },

    // ===== ETAPA: PRECIO =====
    {
        id: "FLAG_INCLUIR_ENVIO",
        label: "Incluir costo de envío",
        labelCorto: "Incluir Envío",
        descripcion: "Suma el costo de envío del MLA al precio.",
        icon: "🚩",
        etapa: "PRECIO",
    },
    {
        id: "COMISION_SOBRE_PVP",
        label: "Comisión sobre PVP",
        labelCorto: "Comisión s/PVP",
        descripcion: "Divisor sobre PVP: PVP / (1 - %/100). Costo o inflación según naturaleza.",
        icon: "💸",
        etapa: "PRECIO",
    },
    {
        id: "FLAG_COMISION_ML",
        label: "Comisión MercadoLibre",
        labelCorto: "Comisión ML",
        descripcion: "Usa la comisión del MLA como divisor sobre PVP.",
        icon: "🚩",
        etapa: "PRECIO",
    },
    {
        id: "CALCULO_SOBRE_CANAL_BASE",
        label: "Cálculo sobre canal base (canal propio)",
        labelCorto: "Canal Base",
        descripcion: "PVP = PVP_base × (1 + %/100). Escala PVP e ingreso del dueño.",
        icon: "🔗",
        etapa: "PRECIO",
    },
    {
        id: "CALCULO_SOBRE_CANAL_BASE_RESELLER",
        label: "Cálculo sobre canal base (reseller)",
        labelCorto: "Canal Base (Reseller)",
        descripcion: "Variante reseller: el ingreso del dueño se 'corta' en este factor.",
        icon: "🔁",
        etapa: "PRECIO",
    },

    // ===== ETAPA: POST_PRECIO =====
    {
        id: "COSTO_OCULTO_PVP",
        label: "Costo oculto sobre PVP",
        labelCorto: "Costo Oculto s/PVP",
        descripcion: "Divisor adicional sobre PVP (retención de plataforma). Sí cuenta como costo.",
        icon: "🕳️",
        etapa: "POST_PRECIO",
    },
    {
        id: "DESCUENTO_PORCENTUAL",
        label: "Descuento porcentual",
        labelCorto: "Descuento %",
        descripcion: "Reduce el PVP: PVP × (1 - %/100). Ej: descuento máquinas.",
        icon: "🔻",
        etapa: "POST_PRECIO",
    },
    {
        id: "INFLACION_DIVISOR_FINAL",
        label: "Inflación divisor final",
        labelCorto: "Inflación Final",
        descripcion: "Bucket divisor independiente al final. Infla PVP sin contar como costo.",
        icon: "📈",
        etapa: "POST_PRECIO",
    },
    {
        id: "GASTO_SIN_INFLAR_PVP",
        label: "Gasto que no infla PVP",
        labelCorto: "Gasto sin inflar",
        descripcion: "Costo del dueño que NO se traslada al PVP pero sí cuenta como costo.",
        icon: "🫥",
        etapa: "POST_PRECIO",
    },
    {
        id: "FLAG_APLICAR_PRECIO_INFLADO",
        label: "Aplicar precio inflado",
        labelCorto: "Precio Inflado",
        descripcion: "Habilita las reglas de precio inflado para el canal.",
        icon: "🚩",
        etapa: "POST_PRECIO",
    },
];

const APLICA_SOBRE_BY_ID: Record<string, AplicaSobreInfo> = APLICA_SOBRE_INFO.reduce(
    (acc, info) => { acc[info.id] = info; return acc; },
    {} as Record<string, AplicaSobreInfo>,
);

/**
 * Devuelve la info visual del aplicaSobre. Si el valor es desconocido (caso
 * defensivo: backend agregó un valor nuevo), devuelve un fallback gris neutro
 * para no romper la UI.
 */
export function getAplicaSobreInfo(id: string | null | undefined): AplicaSobreInfo {
    if (id && APLICA_SOBRE_BY_ID[id]) return APLICA_SOBRE_BY_ID[id];
    return {
        id: (id ?? "DESCONOCIDO") as AplicaSobreId,
        label: id ?? "Desconocido",
        labelCorto: id ?? "Desconocido",
        descripcion: "Valor no reconocido — ¿el backend agregó un valor nuevo al enum AplicaSobre?",
        icon: "❓",
        etapa: "POST_PRECIO",
    };
}

/** Clase de badge derivada de la etapa del aplicaSobre. */
export function getAplicaSobreBadgeClass(id: string | null | undefined): string {
    const info = getAplicaSobreInfo(id);
    // Override: el RESELLER usa un color distintivo (fuchsia) para diferenciarlo del propio.
    if (info.id === "CALCULO_SOBRE_CANAL_BASE_RESELLER") {
        return "bg-fuchsia-100 text-fuchsia-800 dark:bg-fuchsia-500/20 dark:text-fuchsia-200";
    }
    return BADGE_CLASS_POR_ETAPA[info.etapa]
        ?? "bg-gray-100 text-gray-700 dark:bg-slate-700 dark:text-slate-300";
}

/** Backward-compat: mapa simple id → label largo (para usos legacy). */
export const APLICA_SOBRE_LABEL: Record<string, string> = APLICA_SOBRE_INFO.reduce(
    (acc, info) => { acc[info.id] = info.label; return acc; },
    {} as Record<string, string>,
);

/** Conserva la noción "es flag" (id que arranca con FLAG_) — útil para lógica. */
export const isFlag = (aplicaSobre: string): boolean => aplicaSobre.startsWith("FLAG_");

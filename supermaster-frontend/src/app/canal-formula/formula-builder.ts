// Compone la fórmula final del canal a partir de sus conceptos.
// Sigue el orden real del backend (CalculoPrecioServiceImpl):
//   1. Costo
//   2. GASTO_SOBRE_COSTO         × (1 + Σ%)
//   3. FLAG_FINANCIACION_PROVEEDOR × (1 + financiación proveedor)
//   4. Margen base + AJUSTE_MARGEN_PUNTOS + AJUSTE_MARGEN_PROPORCIONAL
//   5. GASTO_POST_GANANCIA       × (1 + Σ%)
//   6. FLAG_INCLUIR_ENVIO        + envío
//   7. IVA (FLAG_APLICAR_IVA) + IMPUESTO_ADICIONAL  × (1 + Σ%)
//   8. GASTO_POST_IMPUESTOS      × (1 + Σ%)
//   9. COMISION_SOBRE_PVP + FLAG_COMISION_ML + FLAG_INFLACION_ML + cuota → ÷ (1 − Σ%)
//  10. RECARGO_CUPON             ÷ (1 − %)
//  11. DESCUENTO_PORCENTUAL      × (1 − %)
//  12. INFLACION_DIVISOR         ÷ (1 − %)
//  13. FLAG_APLICAR_PRECIO_INFLADO  reemplazo final si aplica.

import type { CanalFormulaView, ConceptoEnCanal, CuotaCanal } from "./types";

// Operador renderizable.
export type FormulaOperator = "×" | "÷" | "+" | "−" | "=";

// Una etapa concreta de la fórmula final.
export interface FormulaStep {
    // Etiqueta corta legible (ej: "Gastos sobre costo").
    label: string;
    // Operador que conecta con el valor previo.
    operator: FormulaOperator;
    // Expresión simbólica (ej: "(1 + 8% + 3%)").
    expression: string;
    // Conceptos que contribuyen a este paso (para mostrar detalle).
    conceptos: { nombre: string; porcentaje: number; tieneReglas: boolean }[];
}

// Resultado final.
export interface FormulaCompuesta {
    // Pasos en orden, sin la línea inicial del costo.
    pasos: FormulaStep[];
    // Si el canal usa canal base, el costo de partida no es el costo del producto.
    partidaCanalBase?: { nombre: string; tipo: "propio" | "reseller" };
    // Hay al menos un FLAG_APLICAR_PRECIO_INFLADO activo.
    tienePrecioInflado: boolean;
}

const fmtPorc = (p: number): string => {
    if (p === 0) return "0%";
    const fixed = Number.isInteger(p) ? p.toString() : p.toFixed(2).replace(/\.?0+$/, "");
    return `${fixed}%`;
};

// Suma porcentajes y los formatea como "(1 + 8% + 3%)" (con signos según valor).
const expresionFactorMas = (porcentajes: number[]): string => {
    if (porcentajes.length === 0) return "";
    const partes = porcentajes.map((p, i) => {
        const sgn = p >= 0 ? (i === 0 ? "1 + " : " + ") : (i === 0 ? "1 − " : " − ");
        return `${sgn}${fmtPorc(Math.abs(p))}`;
    });
    return `(${partes.join("")})`;
};

const expresionDivisorMenos = (porcentajes: number[]): string => {
    if (porcentajes.length === 0) return "";
    const partes = porcentajes.map((p, i) => {
        const sgn = p >= 0 ? (i === 0 ? "1 − " : " − ") : (i === 0 ? "1 + " : " + ");
        return `${sgn}${fmtPorc(Math.abs(p))}`;
    });
    return `(${partes.join("")})`;
};

const expresionFactorMenos = (porcentaje: number): string => {
    const sgn = porcentaje >= 0 ? "1 − " : "1 + ";
    return `(${sgn}${fmtPorc(Math.abs(porcentaje))})`;
};

const conceptosResumen = (conceptos: ConceptoEnCanal[]) =>
    conceptos.map((c) => ({
        nombre: c.nombre,
        porcentaje: c.porcentaje,
        tieneReglas: c.reglas.length > 0,
    }));

// Filtra conceptos por uno o varios `aplicaSobre`.
const por = (view: CanalFormulaView, ...keys: string[]): ConceptoEnCanal[] => {
    const set = new Set(keys);
    const out: ConceptoEnCanal[] = [];
    for (const e of view.etapas) {
        for (const c of e.conceptos) {
            if (set.has(c.aplicaSobre)) out.push(c);
        }
    }
    return out;
};

// Construye la lista de pasos según los conceptos presentes.
export function buildFormulaCompuesta(view: CanalFormulaView, cuotaSel?: CuotaCanal | null): FormulaCompuesta {
    const pasos: FormulaStep[] = [];

    // Canal base: cambia el punto de partida. Si hay CALCULO_SOBRE_CANAL_BASE o ..._RESELLER,
    // el costo inicial se reemplaza por PVP del canal base × factor.
    const sobreBaseProp = por(view, "CALCULO_SOBRE_CANAL_BASE");
    const sobreBaseRes = por(view, "CALCULO_SOBRE_CANAL_BASE_RESELLER");
    let partidaCanalBase: FormulaCompuesta["partidaCanalBase"];
    if (view.canalBaseNombre && (sobreBaseProp.length > 0 || sobreBaseRes.length > 0)) {
        partidaCanalBase = {
            nombre: view.canalBaseNombre,
            tipo: sobreBaseRes.length > 0 ? "reseller" : "propio",
        };
    }

    // 2. Gastos sobre costo (suma).
    const gastosCosto = por(view, "GASTO_SOBRE_COSTO");
    if (gastosCosto.length > 0) {
        const ps = gastosCosto.map((c) => c.porcentaje);
        pasos.push({
            label: "Gastos sobre costo",
            operator: "×",
            expression: expresionFactorMas(ps),
            conceptos: conceptosResumen(gastosCosto),
        });
    }

    // 3. Financiación de proveedor (flag).
    const finProv = por(view, "FLAG_FINANCIACION_PROVEEDOR");
    if (finProv.length > 0) {
        pasos.push({
            label: "Financiación de proveedor",
            operator: "×",
            expression: "(1 + financiación proveedor)",
            conceptos: conceptosResumen(finProv),
        });
    }

    // 4. Margen base + ajustes.
    const flagMin = por(view, "FLAG_USAR_MARGEN_MINORISTA");
    const flagMay = por(view, "FLAG_USAR_MARGEN_MAYORISTA");
    const ajustePts = por(view, "AJUSTE_MARGEN_PUNTOS");
    const ajusteProp = por(view, "AJUSTE_MARGEN_PROPORCIONAL");

    if (flagMin.length > 0 || flagMay.length > 0) {
        // Margen base: viene del producto (no es porcentaje del canal). El ajusteProp se SUMA al margen
        // antes de aplicarlo, mientras que ajustePts SUMA puntos al porcentaje resultante.
        const tipo = flagMay.length > 0 ? "mayorista" : "minorista";
        const baseConceptos = flagMay.length > 0 ? flagMay : flagMin;
        const ajustes = [...ajustePts, ...ajusteProp];
        const conceptos = [...baseConceptos, ...ajustes];

        const partesExpr: string[] = [`margen ${tipo}`];
        for (const a of ajustes) {
            partesExpr.push(a.porcentaje >= 0 ? ` + ${fmtPorc(a.porcentaje)}` : ` − ${fmtPorc(Math.abs(a.porcentaje))}`);
        }
        pasos.push({
            label: `Margen ${tipo}` + (ajustes.length > 0 ? " (con ajustes)" : ""),
            operator: "×",
            expression: `(1 + ${partesExpr.join("")})`,
            conceptos: conceptosResumen(conceptos),
        });
    } else if (ajustePts.length > 0 || ajusteProp.length > 0) {
        // Solo ajustes sin flag de margen base: raro pero posible.
        const ajustes = [...ajustePts, ...ajusteProp];
        const ps = ajustes.map((c) => c.porcentaje);
        pasos.push({
            label: "Ajustes de margen",
            operator: "×",
            expression: expresionFactorMas(ps),
            conceptos: conceptosResumen(ajustes),
        });
    }

    // 5. Gastos post-ganancia.
    const postGanancia = por(view, "GASTO_POST_GANANCIA");
    if (postGanancia.length > 0) {
        const ps = postGanancia.map((c) => c.porcentaje);
        pasos.push({
            label: "Gastos post-ganancia",
            operator: "×",
            expression: expresionFactorMas(ps),
            conceptos: conceptosResumen(postGanancia),
        });
    }

    // 6. Envío (flag).
    const envio = por(view, "FLAG_INCLUIR_ENVIO");
    if (envio.length > 0) {
        pasos.push({
            label: "Costo de envío",
            operator: "+",
            expression: "envío",
            conceptos: conceptosResumen(envio),
        });
    }

    // 7. IVA + impuesto adicional.
    const iva = por(view, "FLAG_APLICAR_IVA");
    const impAdic = por(view, "IMPUESTO_ADICIONAL");
    if (iva.length > 0 || impAdic.length > 0) {
        const conceptos = [...iva, ...impAdic];
        const partes: string[] = [];
        if (iva.length > 0) partes.push("IVA producto");
        for (const i of impAdic) {
            partes.push(i.porcentaje >= 0 ? `+ ${fmtPorc(i.porcentaje)}` : `− ${fmtPorc(Math.abs(i.porcentaje))}`);
        }
        pasos.push({
            label: iva.length > 0 ? "Impuestos (IVA + adicionales)" : "Impuestos adicionales",
            operator: "×",
            expression: `(1 + ${partes.join(" ")})`,
            conceptos: conceptosResumen(conceptos),
        });
    }

    // 8. Gastos post-impuestos.
    const postImp = por(view, "GASTO_POST_IMPUESTOS");
    if (postImp.length > 0) {
        const ps = postImp.map((c) => c.porcentaje);
        pasos.push({
            label: "Gastos post-impuestos",
            operator: "×",
            expression: expresionFactorMas(ps),
            conceptos: conceptosResumen(postImp),
        });
    }

    // 9. Comisiones + inflaciones + cuotas (divisor).
    const comisionPVP = por(view, "COMISION_SOBRE_PVP");
    const comisionML = por(view, "FLAG_COMISION_ML");
    const inflacionML = por(view, "FLAG_INFLACION_ML");
    const inflacionPVP = por(view, "INFLACION_SOBRE_PVP");
    const tieneFlagsML = comisionML.length > 0 || inflacionML.length > 0;

    if (comisionPVP.length > 0 || tieneFlagsML || inflacionPVP.length > 0 || (cuotaSel && cuotaSel.porcentaje !== 0)) {
        const conceptos = [...comisionPVP, ...comisionML, ...inflacionML, ...inflacionPVP];
        const partes: string[] = [];
        for (const c of comisionPVP) {
            partes.push(c.porcentaje >= 0 ? `+ ${fmtPorc(c.porcentaje)}` : `− ${fmtPorc(Math.abs(c.porcentaje))}`);
        }
        // Inflación s/PVP (porcentaje propio): se suma al divisor pero NO cuenta como costo.
        for (const c of inflacionPVP) {
            partes.push(c.porcentaje >= 0
                ? `+ ${fmtPorc(c.porcentaje)} (inflación)`
                : `− ${fmtPorc(Math.abs(c.porcentaje))} (inflación)`);
        }
        if (tieneFlagsML) partes.push("+ comisión ML");

        if (cuotaSel && cuotaSel.porcentaje !== 0) {
            const cuotaLabel =
                cuotaSel.cuotas === -1 ? "transf." :
                cuotaSel.cuotas <= 1 ? "contado" :
                `${cuotaSel.cuotas} cuotas`;
            partes.push(cuotaSel.porcentaje >= 0
                ? `+ ${fmtPorc(cuotaSel.porcentaje)} (${cuotaLabel})`
                : `− ${fmtPorc(Math.abs(cuotaSel.porcentaje))} (${cuotaLabel})`);
        }

        if (partes.length > 0) {
            const expression = "1 " + partes.join(" ");
            pasos.push({
                label: "Comisiones / inflaciones / cuotas",
                operator: "÷",
                expression: `(${expression})`,
                conceptos: conceptosResumen(conceptos),
            });
        }
    }

    // 10. Recargo cupón.
    const recargoCupon = por(view, "RECARGO_CUPON");
    if (recargoCupon.length > 0) {
        // Se aplica como divisor (1 − %).
        const ps = recargoCupon.map((c) => c.porcentaje);
        pasos.push({
            label: "Recargo por cupón",
            operator: "÷",
            expression: expresionDivisorMenos(ps),
            conceptos: conceptosResumen(recargoCupon),
        });
    }

    // 11. Descuento porcentual.
    const descuento = por(view, "DESCUENTO_PORCENTUAL");
    if (descuento.length > 0) {
        // Se aplica como factor (1 − %).
        const ps = descuento.map((c) => c.porcentaje);
        const total = ps.reduce((a, b) => a + b, 0);
        pasos.push({
            label: "Descuento %",
            operator: "×",
            expression: expresionFactorMenos(total),
            conceptos: conceptosResumen(descuento),
        });
    }

    // 12. Inflación divisor.
    const inflacionDiv = por(view, "INFLACION_DIVISOR");
    if (inflacionDiv.length > 0) {
        const ps = inflacionDiv.map((c) => c.porcentaje);
        pasos.push({
            label: "Inflación (divisor)",
            operator: "÷",
            expression: expresionDivisorMenos(ps),
            conceptos: conceptosResumen(inflacionDiv),
        });
    }

    // 13. Precio inflado: reemplazo del PVP final.
    const precioInflado = por(view, "FLAG_APLICAR_PRECIO_INFLADO");

    return {
        pasos,
        partidaCanalBase,
        tienePrecioInflado: precioInflado.length > 0,
    };
}

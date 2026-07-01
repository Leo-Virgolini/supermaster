// Borrador de una variante en el modal de creación.
// La variante #1 es el producto base (SKU de arriba); estas son las hermanas.
export type VarianteBorrador = {
    id: string;                 // key estable para React (no se envía al backend)
    sku: string;
    ejeValorId: string | null;  // value_id del eje si es de lista (ej. id del color)
    ejeValorNombre: string;     // value_name (o valor libre)
    stock: number | "";
    ean: string;
    cuotaMl: number;
    cuotaHogar: number;
    cuotaGastro: number;
    expandida: boolean;
};

let _seq = 0;

/** Crea una variante vacía con las cuotas por defecto (las del producto base). */
export function nuevaVariante(cuotas: { ml: number; hogar: number; gastro: number }): VarianteBorrador {
    _seq += 1;
    return {
        id: `v${_seq}`, sku: "", ejeValorId: null, ejeValorNombre: "", stock: "", ean: "",
        cuotaMl: cuotas.ml, cuotaHogar: cuotas.hogar, cuotaGastro: cuotas.gastro, expandida: true,
    };
}

/**
 * Valida el conjunto base + variantes. Devuelve el primer mensaje de error, o null si está OK.
 * hayImagenSku(sku) => true si el SKU tiene al menos una imagen válida para ML.
 */
export function validarVariantes(
    base: { sku: string; ejeValorNombre: string },
    variantes: VarianteBorrador[],
    ejeAtributoId: string,
    subirMl: boolean,
    hayImagenSku: (sku: string) => boolean,
): string | null {
    if (!ejeAtributoId) return "Elegí el eje de variación (ej. Color).";
    if (variantes.length < 1) return "Agregá al menos una variante además del producto base.";
    if (!base.ejeValorNombre.trim()) return "Cargá el valor del eje del producto base (variante #1).";

    const skus = [base.sku.trim().toLowerCase()];
    const valores = [base.ejeValorNombre.trim().toLowerCase()];
    for (const v of variantes) {
        if (!v.sku.trim()) return "Cada variante necesita su SKU.";
        if (!v.ejeValorNombre.trim()) return "Cada variante necesita su valor de eje.";
        const sk = v.sku.trim().toLowerCase();
        const val = v.ejeValorNombre.trim().toLowerCase();
        if (skus.includes(sk)) return `SKU repetido entre variantes: ${v.sku.trim()}.`;
        if (valores.includes(val)) return `Valor de eje repetido entre variantes: ${v.ejeValorNombre.trim()}.`;
        skus.push(sk);
        valores.push(val);
    }
    if (subirMl) {
        const todos = [base.sku, ...variantes.map(v => v.sku)];
        const sinImagen = todos.filter(s => s.trim() && !hayImagenSku(s.trim()));
        if (sinImagen.length > 0) return `Mercado Libre exige imagen; faltan para: ${sinImagen.join(", ")}.`;
    }
    return null;
}

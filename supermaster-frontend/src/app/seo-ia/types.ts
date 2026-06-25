export type SeoCanal = "HOGAR" | "GASTRO";

export type SeoPrompt = {
    canal: SeoCanal;
    contenido: string;
    fechaModificacion: string | null;
};

export type SeoUso = {
    consultas: number;
    tokensEntrada: number;
    tokensSalida: number;
    costoUsd: number;
    modelo: string;
    precioInput1m: number;
    precioOutput1m: number;
};

export const CANAL_LABEL: Record<SeoCanal, string> = {
    HOGAR: "KT Hogar",
    GASTRO: "KT Gastro",
};

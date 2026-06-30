export type SeoConfig = {
    promptHogar: string;
    promptGastro: string;
    model: string;
    precioInput1m: number;
    precioOutput1m: number;
    fechaModificacion: string | null;
};

export type ImagenConfig = {
    contenido: string;
    model: string;
    size: string;
    outputFormat: string;
    quality: string;
    precioInput1m: number;
    precioOutput1m: number;
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

export type ImagenUso = SeoUso;

export const SIZE_OPCIONES = ["1024x1024", "1024x1536", "1536x1024", "auto"] as const;
export const QUALITY_OPCIONES = ["low", "medium", "high", "auto"] as const;
export const FORMATO_OPCIONES = ["png", "jpeg", "webp"] as const;

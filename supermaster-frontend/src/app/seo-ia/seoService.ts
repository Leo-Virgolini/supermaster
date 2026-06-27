import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import type { SeoCanal, SeoPrompt, SeoUso, ImagenPrompt, ImagenUso } from "./types";

export type { ImagenPrompt, ImagenUso };

const API_URL = `${API_BASE_URL}/api/seo`;
const IMAGEN_URL = `${API_BASE_URL}/api/imagen-ia`;

export const getSeoPromptsAPI = async (): Promise<SeoPrompt[]> => {
    const res = await fetchAPI(`${API_URL}/prompts`);
    if (!res.ok) throw new Error("Error al obtener los prompts de SEO");
    return await res.json();
};

export const updateSeoPromptAPI = async (canal: SeoCanal, contenido: string): Promise<SeoPrompt> => {
    const res = await fetchAPI(`${API_URL}/prompts/${canal}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ contenido }),
    });
    if (!res.ok) throw new Error("Error al guardar el prompt de SEO");
    return await res.json();
};

export const getSeoUsoAPI = async (): Promise<SeoUso> => {
    const res = await fetchAPI(`${API_URL}/uso`);
    if (!res.ok) throw new Error("Error al obtener el uso de SEO");
    return await res.json();
};

export const getImagenPromptAPI = async (): Promise<ImagenPrompt> => {
    const res = await fetchAPI(`${IMAGEN_URL}/prompt`);
    if (!res.ok) throw new Error("Error al obtener el prompt de carátula");
    return await res.json();
};

export const updateImagenPromptAPI = async (contenido: string): Promise<ImagenPrompt> => {
    const res = await fetchAPI(`${IMAGEN_URL}/prompt`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ contenido }),
    });
    if (!res.ok) throw new Error("Error al guardar el prompt de carátula");
    return await res.json();
};

export const getImagenUsoAPI = async (): Promise<ImagenUso> => {
    const res = await fetchAPI(`${IMAGEN_URL}/uso`);
    if (!res.ok) throw new Error("Error al obtener el uso de carátula");
    return await res.json();
};

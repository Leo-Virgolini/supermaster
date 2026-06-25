import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import type { SeoCanal, SeoPrompt, SeoUso } from "./types";

const API_URL = `${API_BASE_URL}/api/seo`;

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

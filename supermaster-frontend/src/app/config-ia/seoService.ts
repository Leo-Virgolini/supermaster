import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import type { SeoConfig, ImagenConfig, SeoUso, ImagenUso } from "./types";

const API_URL = `${API_BASE_URL}/api/seo`;
const IMAGEN_URL = `${API_BASE_URL}/api/imagen-ia`;

export const getSeoConfigAPI = async (): Promise<SeoConfig> => {
    const res = await fetchAPI(`${API_URL}/config`);
    if (!res.ok) throw new Error("Error al obtener la configuración de SEO");
    return await res.json();
};

export const updateSeoConfigAPI = async (config: Omit<SeoConfig, "fechaModificacion">): Promise<SeoConfig> => {
    const res = await fetchAPI(`${API_URL}/config`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(config),
    });
    if (!res.ok) throw new Error("Error al guardar la configuración de SEO");
    return await res.json();
};

export const getSeoUsoAPI = async (): Promise<SeoUso> => {
    const res = await fetchAPI(`${API_URL}/uso`);
    if (!res.ok) throw new Error("Error al obtener el uso de SEO");
    return await res.json();
};

export const getImagenConfigAPI = async (): Promise<ImagenConfig> => {
    const res = await fetchAPI(`${IMAGEN_URL}/config`);
    if (!res.ok) throw new Error("Error al obtener la configuración de carátula");
    return await res.json();
};

export const updateImagenConfigAPI = async (config: Omit<ImagenConfig, "fechaModificacion">): Promise<ImagenConfig> => {
    const res = await fetchAPI(`${IMAGEN_URL}/config`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(config),
    });
    if (!res.ok) throw new Error("Error al guardar la configuración de carátula");
    return await res.json();
};

export const getImagenUsoAPI = async (): Promise<ImagenUso> => {
    const res = await fetchAPI(`${IMAGEN_URL}/uso`);
    if (!res.ok) throw new Error("Error al obtener el uso de carátula");
    return await res.json();
};

"use client";
import { getErrorMessage } from "@/lib/errors";
import { useCallback, useEffect, useState } from "react";
import { notificar } from "../utils/notificar";
import {
    getImagenPromptAPI,
    getImagenUsoAPI,
    getSeoPromptsAPI,
    getSeoUsoAPI,
    updateImagenPromptAPI,
    updateSeoPromptAPI,
    type ImagenPrompt,
    type ImagenUso,
} from "./seoService";
import { CANAL_LABEL, type SeoCanal, type SeoPrompt, type SeoUso } from "./types";

export function useSeoIa() {
    const [prompts, setPrompts] = useState<SeoPrompt[]>([]);
    const [uso, setUso] = useState<SeoUso | null>(null);
    const [imagenPrompt, setImagenPrompt] = useState<ImagenPrompt | null>(null);
    const [imagenUso, setImagenUso] = useState<ImagenUso | null>(null);
    const [imagenBorrador, setImagenBorrador] = useState<string>("");
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState<SeoCanal | null>(null);
    const [isSavingImagen, setIsSavingImagen] = useState(false);

    const fetchData = useCallback(async () => {
        setIsLoading(true);
        try {
            const [p, u, ip, iu] = await Promise.all([
                getSeoPromptsAPI(),
                getSeoUsoAPI(),
                getImagenPromptAPI(),
                getImagenUsoAPI(),
            ]);
            setPrompts(p);
            setUso(u);
            setImagenPrompt(ip);
            setImagenUso(iu);
            setImagenBorrador(ip.contenido);
        } catch (e: unknown) {
            notificar.error(getErrorMessage(e, "No se pudo cargar la configuración de SEO"));
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => { fetchData(); }, [fetchData]);

    const savePrompt = async (canal: SeoCanal, contenido: string) => {
        setIsSaving(canal);
        try {
            const actualizado = await updateSeoPromptAPI(canal, contenido);
            setPrompts(prev => prev.map(p => p.canal === canal ? actualizado : p));
            notificar.success(`Prompt de ${CANAL_LABEL[canal]} guardado`);
        } catch (e: unknown) {
            notificar.error(getErrorMessage(e, "Error al guardar el prompt"));
        } finally {
            setIsSaving(null);
        }
    };

    const saveImagenPrompt = async (contenido: string) => {
        setIsSavingImagen(true);
        try {
            const actualizado = await updateImagenPromptAPI(contenido);
            setImagenPrompt(actualizado);
            setImagenBorrador(actualizado.contenido);
            notificar.success("Prompt de carátula guardado");
        } catch (e: unknown) {
            notificar.error(getErrorMessage(e, "Error al guardar el prompt de carátula"));
        } finally {
            setIsSavingImagen(false);
        }
    };

    return { prompts, uso, imagenPrompt, imagenUso, imagenBorrador, setImagenBorrador, isLoading, isSaving, isSavingImagen, savePrompt, saveImagenPrompt };
}

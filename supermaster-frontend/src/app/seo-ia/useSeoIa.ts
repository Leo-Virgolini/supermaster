"use client";
import { getErrorMessage } from "@/lib/errors";
import { useCallback, useEffect, useState } from "react";
import { notificar } from "../utils/notificar";
import { getSeoPromptsAPI, getSeoUsoAPI, updateSeoPromptAPI } from "./seoService";
import { CANAL_LABEL, type SeoCanal, type SeoPrompt, type SeoUso } from "./types";

export function useSeoIa() {
    const [prompts, setPrompts] = useState<SeoPrompt[]>([]);
    const [uso, setUso] = useState<SeoUso | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState<SeoCanal | null>(null);

    const fetchData = useCallback(async () => {
        setIsLoading(true);
        try {
            const [p, u] = await Promise.all([getSeoPromptsAPI(), getSeoUsoAPI()]);
            setPrompts(p);
            setUso(u);
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

    return { prompts, uso, isLoading, isSaving, savePrompt };
}

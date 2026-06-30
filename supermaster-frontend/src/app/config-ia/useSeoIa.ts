"use client";
import { getErrorMessage } from "@/lib/errors";
import { useCallback, useEffect, useState } from "react";
import { notificar } from "../utils/notificar";
import {
    getImagenConfigAPI,
    getImagenUsoAPI,
    getSeoConfigAPI,
    getSeoUsoAPI,
    updateImagenConfigAPI,
    updateSeoConfigAPI,
    resetImagenUsoAPI,
    resetSeoUsoAPI,
} from "./seoService";
import type { ImagenConfig, ImagenUso, SeoConfig, SeoUso } from "./types";

export function useSeoIa() {
    const [seoConfig, setSeoConfig] = useState<SeoConfig | null>(null);
    const [imagenConfig, setImagenConfig] = useState<ImagenConfig | null>(null);
    const [uso, setUso] = useState<SeoUso | null>(null);
    const [imagenUso, setImagenUso] = useState<ImagenUso | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isSavingSeo, setIsSavingSeo] = useState(false);
    const [isSavingImagen, setIsSavingImagen] = useState(false);
    const [isResettingSeo, setIsResettingSeo] = useState(false);
    const [isResettingImagen, setIsResettingImagen] = useState(false);

    const fetchData = useCallback(async () => {
        setIsLoading(true);
        try {
            const [sc, ic, u, iu] = await Promise.all([
                getSeoConfigAPI(),
                getImagenConfigAPI(),
                getSeoUsoAPI(),
                getImagenUsoAPI(),
            ]);
            setSeoConfig(sc);
            setImagenConfig(ic);
            setUso(u);
            setImagenUso(iu);
        } catch (e: unknown) {
            notificar.error(getErrorMessage(e, "No se pudo cargar la configuración de SEO IA"));
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => { fetchData(); }, [fetchData]);

    const saveSeoConfig = async (config: Omit<SeoConfig, "fechaModificacion">) => {
        setIsSavingSeo(true);
        try {
            const actualizado = await updateSeoConfigAPI(config);
            setSeoConfig(actualizado);
            notificar.success("Configuración de SEO guardada");
        } catch (e: unknown) {
            notificar.error(getErrorMessage(e, "Error al guardar la configuración de SEO"));
        } finally {
            setIsSavingSeo(false);
        }
    };

    const saveImagenConfig = async (config: Omit<ImagenConfig, "fechaModificacion">) => {
        setIsSavingImagen(true);
        try {
            const actualizado = await updateImagenConfigAPI(config);
            setImagenConfig(actualizado);
            notificar.success("Configuración de carátula guardada");
        } catch (e: unknown) {
            notificar.error(getErrorMessage(e, "Error al guardar la configuración de carátula"));
        } finally {
            setIsSavingImagen(false);
        }
    };

    const resetSeoUso = async () => {
        setIsResettingSeo(true);
        try {
            await resetSeoUsoAPI();
            setUso(await getSeoUsoAPI());
            notificar.success("Uso de SEO reseteado");
        } catch (e: unknown) {
            notificar.error(getErrorMessage(e, "No se pudo resetear el uso de SEO"));
        } finally {
            setIsResettingSeo(false);
        }
    };

    const resetImagenUso = async () => {
        setIsResettingImagen(true);
        try {
            await resetImagenUsoAPI();
            setImagenUso(await getImagenUsoAPI());
            notificar.success("Uso de carátula reseteado");
        } catch (e: unknown) {
            notificar.error(getErrorMessage(e, "No se pudo resetear el uso de carátula"));
        } finally {
            setIsResettingImagen(false);
        }
    };

    return { seoConfig, imagenConfig, uso, imagenUso, isLoading, isSavingSeo, isSavingImagen, saveSeoConfig, saveImagenConfig, resetSeoUso, resetImagenUso, isResettingSeo, isResettingImagen };
}

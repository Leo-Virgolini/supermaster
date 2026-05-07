"use client";

import { useState, useEffect, useCallback } from "react";
import { getConfig } from "./automatizacionPreciosService";
import type { SincronizacionConfig, SyncRequest } from "./types";

const DEFAULT_REQUEST: SyncRequest = {
    importarCostosDux: true,
    generarEnvio: true,
    excluirPromociones: true,
    duxMl: true,
    duxGastro: true,
    duxNube: true,
    preciosMl: true,
    incluirPromociones: true,
    preciosNube: true,
};

export function useAutomatizacionPrecios() {
    const [config, setConfig] = useState<SincronizacionConfig | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [request, setRequest] = useState<SyncRequest>(DEFAULT_REQUEST);

    const fetchConfig = useCallback(async () => {
        setIsLoading(true);
        setError(null);
        try {
            const data = await getConfig();
            setConfig(data);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Error cargando configuración");
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchConfig();
    }, [fetchConfig]);

    const toggleStep = (key: keyof SyncRequest) => {
        setRequest((prev) => ({ ...prev, [key]: !prev[key] }));
    };

    return { config, isLoading, error, request, toggleStep, refreshConfig: fetchConfig };
}

"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { getConfig } from "./automatizacionPreciosService";
import type { SincronizacionConfig, SyncRequest, SyncSteps } from "./types";

const STORAGE_KEY = "automatizacion-precios.request";

const DEFAULT_REQUEST: SyncRequest = {
    importarCostosDux: true,
    bajarTitulosNube: true,
    generarEnvio: true,
    excluirPromociones: true,
    duxMl: true,
    duxGastro: true,
    duxNube: true,
    preciosMl: true,
    incluirPromociones: true,
    preciosNube: true,
};

/**
 * Lee el request guardado en localStorage. Si está corrupto o falta, devuelve DEFAULT_REQUEST.
 * Se hace defensivo porque las claves pueden cambiar entre versiones.
 */
function loadRequestFromStorage(): SyncRequest {
    if (typeof window === "undefined") return DEFAULT_REQUEST;
    try {
        const raw = window.localStorage.getItem(STORAGE_KEY);
        if (!raw) return DEFAULT_REQUEST;
        const parsed = JSON.parse(raw) as Partial<SyncRequest>;
        // Merge con defaults: si en una versión nueva se agrega un paso, no se pierde el default true.
        const merged: SyncRequest = { ...DEFAULT_REQUEST };
        for (const key of Object.keys(DEFAULT_REQUEST) as (keyof SyncSteps)[]) {
            if (typeof parsed[key] === "boolean") {
                merged[key] = parsed[key] as boolean;
            }
        }
        if (Array.isArray(parsed.filtroMlas) && parsed.filtroMlas.length > 0) {
            merged.filtroMlas = parsed.filtroMlas.filter((m): m is string => typeof m === "string");
        }
        return merged;
    } catch {
        return DEFAULT_REQUEST;
    }
}

export function useAutomatizacionPrecios() {
    const [config, setConfig] = useState<SincronizacionConfig | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [request, setRequest] = useState<SyncRequest>(DEFAULT_REQUEST);
    const hydratedRef = useRef(false);

    // Hidratar desde localStorage solo en cliente, después del primer render
    // (evita mismatch de SSR).
    useEffect(() => {
        if (hydratedRef.current) return;
        hydratedRef.current = true;
        setRequest(loadRequestFromStorage());
    }, []);

    // Persistir cada cambio del request al localStorage.
    useEffect(() => {
        if (!hydratedRef.current || typeof window === "undefined") return;
        try {
            window.localStorage.setItem(STORAGE_KEY, JSON.stringify(request));
        } catch {
            // sin acceso o cuota llena: ignorar silenciosamente
        }
    }, [request]);

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

    const toggleStep = (key: keyof SyncSteps) => {
        setRequest((prev) => ({ ...prev, [key]: !prev[key] }));
    };

    const setAllSteps = (value: boolean) => {
        setRequest((prev) => {
            const next: SyncRequest = { ...prev };
            for (const k of Object.keys(DEFAULT_REQUEST) as (keyof SyncSteps)[]) {
                next[k] = value;
            }
            return next;
        });
    };

    const setFiltroMlas = (mlas: string[]) => {
        setRequest((prev) => ({ ...prev, filtroMlas: mlas.length > 0 ? mlas : undefined }));
    };

    /** Parsea texto bulk (con , ; espacio o newline) y agrega las MLAs nuevas sin duplicar. */
    const agregarFiltroMlas = (textoBulk: string) => {
        const nuevas = textoBulk
            .split(/[\s,;]+/)
            .map((s) => s.trim().toUpperCase())
            .filter((s) => s.length > 0);
        if (nuevas.length === 0) return;
        setRequest((prev) => {
            const actuales = new Set(prev.filtroMlas ?? []);
            for (const m of nuevas) actuales.add(m);
            const merged = Array.from(actuales);
            return { ...prev, filtroMlas: merged.length > 0 ? merged : undefined };
        });
    };

    const quitarFiltroMla = (mla: string) => {
        setRequest((prev) => {
            const restantes = (prev.filtroMlas ?? []).filter((m) => m !== mla);
            return { ...prev, filtroMlas: restantes.length > 0 ? restantes : undefined };
        });
    };

    const limpiarFiltroMlas = () => {
        setRequest((prev) => ({ ...prev, filtroMlas: undefined }));
    };

    return {
        config, isLoading, error, request,
        toggleStep, setAllSteps,
        setFiltroMlas, agregarFiltroMlas, quitarFiltroMla, limpiarFiltroMlas,
        refreshConfig: fetchConfig,
    };
}

"use client";
import { useCallback, useEffect, useState } from "react";
import { API_BASE_URL } from "../../config/runtime";
import { fetchAPI } from "../../utils/fetchAPI";

export interface MotivoPendiente {
    motivo: string;
    cantidad: number;
    ultimoCambio: string | null;
}

export interface RecalculoPendiente {
    pendiente: boolean;
    cantidad: number;
    recalcularTodo: boolean;
    productosCount: number;
    canalesCount: number;
    ultimaModificacion: string | null;
    motivos: MotivoPendiente[];
}

const VACIO: RecalculoPendiente = {
    pendiente: false,
    cantidad: 0,
    recalcularTodo: false,
    productosCount: 0,
    canalesCount: 0,
    ultimaModificacion: null,
    motivos: [],
};

/**
 * Hook global que escucha el estado de recálculos pendientes vía SSE (Server-Sent Events)
 * y expone una función para aplicarlos manualmente. Lo usa el banner global del header.
 *
 * Ventajas vs polling:
 * - El banner se actualiza al instante cuando otro usuario hace cambios (no espera al
 *   próximo tick).
 * - Cero tráfico en idle: solo recibe eventos cuando hay cambios + un heartbeat cada 25s.
 * - Reconexión automática del browser si la conexión se cae (EventSource nativo).
 */
export function useRecalculoPendiente() {
    const [estado, setEstado] = useState<RecalculoPendiente>(VACIO);
    const [aplicando, setAplicando] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const url = `${API_BASE_URL}/api/precios/recalculo-pendiente/stream`;
        const es = new EventSource(url);

        es.addEventListener("pendiente", (e) => {
            try {
                const data: RecalculoPendiente = JSON.parse((e as MessageEvent).data);
                setEstado(data);
            } catch {
                /* ignore malformed event */
            }
        });

        // EventSource reconecta solo, pero si falla durante mucho rato logueamos para debug.
        es.onerror = () => {
            // No-op: el browser reintenta automáticamente. Mantenemos el último estado
            // conocido hasta que vuelva la conexión.
        };

        return () => es.close();
    }, []);

    const aplicar = useCallback(async (): Promise<{ ok: boolean; error?: string }> => {
        try {
            setAplicando(true);
            setError(null);
            const res = await fetchAPI(`${API_BASE_URL}/api/precios/recalculo-pendiente/aplicar`, {
                method: "POST",
                allowedStatuses: [409],
            });
            if (res.status === 409) {
                const msg = "Ya hay un recálculo en proceso. Esperá a que termine.";
                setError(msg);
                return { ok: false, error: msg };
            }
            if (!res.ok) {
                const msg = "Error al iniciar el recálculo";
                setError(msg);
                return { ok: false, error: msg };
            }
            // El backend limpia el contador y broadcast por SSE: el banner desaparece solo.
            return { ok: true };
        } catch (e: any) {
            const msg = e?.message ?? "Error al iniciar el recálculo";
            setError(msg);
            return { ok: false, error: msg };
        } finally {
            setAplicando(false);
        }
    }, []);

    return { estado, aplicando, error, aplicar };
}

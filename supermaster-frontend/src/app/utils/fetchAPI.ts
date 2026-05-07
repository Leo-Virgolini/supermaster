/**
 * Wrapper de fetch que:
 * - Agrega el Bearer token de sesión a cada request
 * - Extrae el mensaje de error del backend y lo lanza como Error
 * - Dispara eventos globales para errores de conexión (backendOffline) y 401 (authExpired)
 *
 * El backend devuelve: { success: false, message: "...", ... }
 */

import { API_BASE_URL } from "../config/runtime";

function getAuthHeaders(): Record<string, string> {
    if (typeof window === "undefined") return {};
    const token = localStorage.getItem("accessToken");
    return token ? { Authorization: `Bearer ${token}` } : {};
}

async function probeBackendAlive(): Promise<boolean> {
    if (typeof window === "undefined") return true;
    try {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 1500);
        const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
            method: "OPTIONS",
            signal: controller.signal,
            cache: "no-store",
        }).catch(() => null);
        clearTimeout(timeout);
        return response !== null;
    } catch {
        return false;
    }
}

async function signalBackendOfflineIfConfirmed() {
    if (typeof window !== "undefined" && !(await probeBackendAlive())) {
        window.dispatchEvent(new CustomEvent("backendOffline"));
    }
}

function signalAuthExpired() {
    if (typeof window !== "undefined") {
        window.dispatchEvent(new CustomEvent("authExpired"));
    }
}

function isAbortLikeError(error: unknown, signal?: AbortSignal | null) {
    if (signal?.aborted) return true;
    if (error instanceof DOMException && error.name === "AbortError") return true;
    if (error instanceof Error) {
        const name = error.name.toLowerCase();
        const message = error.message.toLowerCase();
        return name.includes("abort") || message.includes("abort");
    }
    return false;
}

export async function fetchAPI(
    input: RequestInfo | URL,
    init?: RequestInit & { allowedStatuses?: number[] },
): Promise<Response> {
    // Merge del token con los headers existentes
    const mergedInit: RequestInit = {
        ...init,
        headers: {
            ...getAuthHeaders(),
            ...(init?.headers as Record<string, string> ?? {}),
        },
    };

    let response: Response;
    try {
        response = await fetch(input, mergedInit);
    } catch (error) {
        if (isAbortLikeError(error, mergedInit.signal)) {
            throw error;
        }
        void signalBackendOfflineIfConfirmed();
        throw new Error("No se puede conectar con el servidor. Verificá que el backend esté en línea.");
    }

    const allowedStatuses = init?.allowedStatuses;

    if (!response.ok && !allowedStatuses?.includes(response.status)) {
        // Token expirado o inválido
        if (response.status === 401) {
            signalAuthExpired();
            throw new Error("Sesión expirada. Por favor, volvé a iniciar sesión.");
        }

        // Backend caído (gateway/proxy errors)
        if (response.status === 502 || response.status === 503 || response.status === 504) {
            void signalBackendOfflineIfConfirmed();
        }

        let message = `Error ${response.status}`;
        try {
            const data = await response.clone().json();
            if (data?.message) message = data.message;
        } catch {
            const text = await response.text().catch(() => "");
            if (text) message = text;
        }
        throw new Error(message);
    }

    return response;
}

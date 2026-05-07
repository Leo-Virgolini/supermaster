import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import type { SincronizacionConfig, SyncRequest } from "./types";

const BASE = `${API_BASE_URL}/api/automatizacion-precios`;

export async function getConfig(): Promise<SincronizacionConfig> {
    const res = await fetchAPI(`${BASE}/config`);
    return res.json();
}

export async function iniciarSincronizacion(request: SyncRequest): Promise<void> {
    await fetchAPI(`${BASE}/iniciar`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(request),
    });
}

export async function getEstado() {
    const res = await fetchAPI(`${BASE}/estado`);
    return res.json();
}

export async function cancelarSincronizacion(): Promise<void> {
    await fetchAPI(`${BASE}/cancelar`, { method: "POST" });
}

export async function getResultado() {
    const res = await fetchAPI(`${BASE}/resultado`, { allowedStatuses: [204] });
    if (res.status === 204) return null;
    return res.json();
}

export async function getLog(desde: number = 0): Promise<string[]> {
    const res = await fetchAPI(`${BASE}/log?desde=${desde}`);
    return res.json();
}

export async function getLogFile(lineas: number = 500): Promise<string> {
    const res = await fetchAPI(`${BASE}/log-file?lineas=${lineas}`);
    return res.text();
}

// Actualizar configuración por clave
export async function updateConfigByClave(clave: string, valor: string): Promise<void> {
    // Buscar el registro por clave
    const res = await fetchAPI(`${API_BASE_URL}/api/config-automatizacion?search=${encodeURIComponent(clave)}&size=50&sort=id,asc`);
    const data = await res.json();
    const item = (data.content ?? []).find((c: { clave: string }) => c.clave.toLowerCase() === clave.toLowerCase());
    if (item) {
        await fetchAPI(`${API_BASE_URL}/api/config-automatizacion/${item.id}`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json", "X-Audit-Origin": "FORM" },
            body: JSON.stringify({ valor }),
        });
    } else {
        await fetchAPI(`${API_BASE_URL}/api/config-automatizacion`, {
            method: "POST",
            headers: { "Content-Type": "application/json", "X-Audit-Origin": "FORM" },
            body: JSON.stringify({ clave, valor }),
        });
    }
}

// Topes de promoción por MLA
export type TopePromocionDTO = { id: number | null; mla: string; topePromocion: number };

export async function getTopesPromocion(): Promise<TopePromocionDTO[]> {
    const res = await fetchAPI(`${API_BASE_URL}/api/mlas/topes-promocion`);
    return res.json();
}

export async function saveTopesPromocion(topes: TopePromocionDTO[]): Promise<TopePromocionDTO[]> {
    const res = await fetchAPI(`${API_BASE_URL}/api/mlas/topes-promocion`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(topes),
    });
    return res.json();
}

export async function searchMlas(query: string): Promise<{ id: number; label: string }[]> {
    const res = await fetchAPI(`${API_BASE_URL}/api/mlas?search=${encodeURIComponent(query)}&size=10&sort=mla,asc`);
    const data = await res.json();
    return (data.content ?? []).map((m: { id: number; mla: string }) => ({ id: m.id, label: m.mla }));
}

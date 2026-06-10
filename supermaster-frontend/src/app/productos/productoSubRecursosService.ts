import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";

const BASE = `${API_BASE_URL}/api/productos`;

// ---- Tipos ----
export interface ProductoAptoDTO { productoId: number; aptoId: number; }
export interface ProductoCatalogoDTO { productoId: number; catalogoId: number; }
export interface ProductoClienteDTO { productoId: number; clienteId: number; }

// ---- Aptos ----
export const getProductoAptosAPI = async (productoId: number): Promise<ProductoAptoDTO[]> => {
    const res = await fetchAPI(`${BASE}/${productoId}/aptos`);
    if (!res.ok) throw new Error("Error al obtener aptos del producto");
    return res.json();
};

export const asignarAptoAPI = async (productoId: number, aptoId: number): Promise<void> => {
    const res = await fetchAPI(`${BASE}/${productoId}/aptos/${aptoId}`, { method: "POST" });
    if (!res.ok) throw new Error("Error al asignar apto");
};

export const quitarAptoAPI = async (productoId: number, aptoId: number): Promise<void> => {
    const res = await fetchAPI(`${BASE}/${productoId}/aptos/${aptoId}`, { method: "DELETE" });
    if (!res.ok) throw new Error("Error al quitar apto");
};

// ---- Catálogos ----
export const getProductoCatalogosAPI = async (productoId: number): Promise<ProductoCatalogoDTO[]> => {
    const res = await fetchAPI(`${BASE}/${productoId}/catalogos`);
    if (!res.ok) throw new Error("Error al obtener catálogos del producto");
    return res.json();
};

export const asignarCatalogoAPI = async (productoId: number, catalogoId: number): Promise<void> => {
    const res = await fetchAPI(`${BASE}/${productoId}/catalogos/${catalogoId}`, { method: "POST" });
    if (!res.ok) throw new Error("Error al asignar catálogo");
};

export const quitarCatalogoAPI = async (productoId: number, catalogoId: number): Promise<void> => {
    const res = await fetchAPI(`${BASE}/${productoId}/catalogos/${catalogoId}`, { method: "DELETE" });
    if (!res.ok) throw new Error("Error al quitar catálogo");
};

// ---- Clientes ----
export const getProductoClientesAPI = async (productoId: number): Promise<ProductoClienteDTO[]> => {
    const res = await fetchAPI(`${BASE}/${productoId}/clientes`);
    if (!res.ok) throw new Error("Error al obtener clientes del producto");
    return res.json();
};

export const asignarClienteAPI = async (productoId: number, clienteId: number): Promise<void> => {
    const res = await fetchAPI(`${BASE}/${productoId}/clientes/${clienteId}`, { method: "POST" });
    if (!res.ok) throw new Error("Error al asignar cliente");
};

export const quitarClienteAPI = async (productoId: number, clienteId: number): Promise<void> => {
    const res = await fetchAPI(`${BASE}/${productoId}/clientes/${clienteId}`, { method: "DELETE" });
    if (!res.ok) throw new Error("Error al quitar cliente");
};

// ---- Precios Inflados por Canal ----
// Endpoint real: /api/productos/{productoId}/canales/{canalId}/precios-inflados (uno por canal)
export interface ProductoCanalPrecioInfladoDTO {
    id: number;
    productoId: number;
    canalId: number;
    precioInflado: { id: number; codigo: string; tipo: string; valor: number; };
    activo: boolean;
    fechaDesde: string | null;
    fechaHasta: string | null;
    observaciones: string | null;
}

// Lista todas las asignaciones de precios inflados del producto (todos los canales).
export const getProductoPreciosInfladosAPI = async (productoId: number): Promise<ProductoCanalPrecioInfladoDTO[]> => {
    const res = await fetchAPI(`${BASE}/${productoId}/precios-inflados`);
    if (!res.ok) throw new Error("Error al obtener precios inflados del producto");
    return res.json();
};

export const getProductoPrecioInfladoPorCanalAPI = async (
    productoId: number,
    canalId: number,
): Promise<ProductoCanalPrecioInfladoDTO | null> => {
    const res = await fetchAPI(`${BASE}/${productoId}/canales/${canalId}/precios-inflados`, { allowedStatuses: [404] });
    if (res.status === 404) return null;
    if (!res.ok) throw new Error("Error al obtener precio inflado del canal");
    return res.json();
};

export const asignarPrecioInfladoAPI = async (
    productoId: number,
    canalId: number,
    precioInfladoId: number,
    extra?: { fechaDesde?: string | null; fechaHasta?: string | null; observaciones?: string | null },
): Promise<ProductoCanalPrecioInfladoDTO> => {
    const res = await fetchAPI(`${BASE}/${productoId}/canales/${canalId}/precios-inflados`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ productoId, canalId, precioInfladoId, activo: true, ...extra }),
    });
    if (!res.ok) throw new Error("Error al asignar precio inflado");
    return res.json();
};

export const quitarPrecioInfladoAPI = async (productoId: number, canalId: number): Promise<void> => {
    const res = await fetchAPI(`${BASE}/${productoId}/canales/${canalId}/precios-inflados`, { method: "DELETE" });
    if (!res.ok) throw new Error("Error al quitar precio inflado");
};

export const getAllPreciosInfladosAPI = async (): Promise<{ id: number; nombre: string }[]> => {
    const res = await fetchAPI(`${API_BASE_URL}/api/precios-inflados?page=0&size=200&sort=codigo,asc`);
    if (!res.ok) throw new Error("Error al cargar precios inflados");
    const data = await res.json();
    const items = data.content ?? data;
    return items.map((p: any) => ({ id: p.id, nombre: `${p.codigo} (${p.tipo} = ${p.valor})` }));
};

export const getAllCanalesAPI = async (): Promise<{ id: number; nombre: string }[]> => {
    const res = await fetchAPI(`${API_BASE_URL}/api/canales?page=0&size=200&sort=nombre,asc`);
    if (!res.ok) throw new Error("Error al cargar canales");
    const data = await res.json();
    const items = data.content ?? data;
    return items.map((c: any) => ({ id: c.id, nombre: c.nombre }));
};

// ---- Helpers para cargar todos (para el dropdown) ----
export const getAllAptosAPI = async (): Promise<{ id: number; nombre: string }[]> => {
    const res = await fetchAPI(`${API_BASE_URL}/api/aptos?page=0&size=200&sort=nombre,asc`);
    if (!res.ok) throw new Error("Error al cargar aptos");
    const data = await res.json();
    const items = data.content ?? data;
    return items.map((a: any) => ({ id: a.id, nombre: a.nombre }));
};

export const getAllCatalogosAPI = async (): Promise<{ id: number; nombre: string }[]> => {
    const res = await fetchAPI(`${API_BASE_URL}/api/catalogos?page=0&size=200&sort=nombre,asc`);
    if (!res.ok) throw new Error("Error al cargar catálogos");
    const data = await res.json();
    const items = data.content ?? data;
    return items.map((c: any) => ({ id: c.id, nombre: c.nombre }));
};

export const getAllClientesAPI = async (): Promise<{ id: number; nombre: string }[]> => {
    const res = await fetchAPI(`${API_BASE_URL}/api/clientes?page=0&size=200&sort=nombre,asc`);
    if (!res.ok) throw new Error("Error al cargar clientes");
    const data = await res.json();
    const items = data.content ?? data;
    return items.map((c: any) => ({ id: c.id, nombre: c.nombre }));
};

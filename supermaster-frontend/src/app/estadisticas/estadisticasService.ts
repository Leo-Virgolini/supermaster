import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import {
    CuotaDisponibleDTO,
    EstadisticasDTO,
    EstadisticasResumenDTO,
    MargenesPorCuotasDTO,
    ProductoMargenNegativo,
    ProductosPorCatalogo,
    ProductosPorProveedor,
} from "./types";

export async function fetchEstadisticas(): Promise<EstadisticasDTO> {
    const res = await fetchAPI(`${API_BASE_URL}/api/estadisticas`);
    return res.json();
}

export async function fetchEstadisticasResumen(): Promise<EstadisticasResumenDTO> {
    const res = await fetchAPI(`${API_BASE_URL}/api/estadisticas/resumen`);
    return res.json();
}

export async function fetchMargenesPorCuotas(cuotas?: number): Promise<MargenesPorCuotasDTO> {
    const params = cuotas != null ? `?cuotas=${cuotas}` : "";
    const res = await fetchAPI(`${API_BASE_URL}/api/estadisticas/margenes${params}`);
    return res.json();
}

export async function fetchCuotasDisponibles(): Promise<CuotaDisponibleDTO[]> {
    const res = await fetchAPI(`${API_BASE_URL}/api/estadisticas/cuotas`);
    return res.json();
}

export async function fetchProductosPorProveedor(): Promise<ProductosPorProveedor[]> {
    const res = await fetchAPI(`${API_BASE_URL}/api/estadisticas/proveedores`);
    return res.json();
}

export async function fetchProductosPorCatalogo(): Promise<ProductosPorCatalogo[]> {
    const res = await fetchAPI(`${API_BASE_URL}/api/estadisticas/catalogos`);
    return res.json();
}

export async function fetchProductosConMargenNegativo(): Promise<ProductoMargenNegativo[]> {
    const res = await fetchAPI(`${API_BASE_URL}/api/estadisticas/margen-negativo`);
    return res.json();
}

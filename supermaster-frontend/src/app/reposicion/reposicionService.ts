import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";

const API_URL = `${API_BASE_URL}/api/reposiciones`;

// ---- Config ----

export interface ReposicionConfigDTO {
    mesesCobertura: number;
    diasPorPeriodo: number;
    pesoMes1: number;
    pesoMes2: number;
    pesoMes3: number;
    idEmpresaDux: number;
    idsSucursalDux: number[];
}

// ---- Estado del proceso ----

export interface ProcesoMasivoEstadoDTO {
    enEjecucion: boolean;
    total: number;
    procesados: number;
    exitosos: number;
    errores: number;
    estado: string; // "idle" | "ejecutando" | "completado" | "cancelado" | "error"
    iniciadoEn?: string;
    finalizadoEn?: string | null;
    mensaje?: string;
}

// ---- Resultado ----

export interface SugerenciaReposicionDTO {
    productoId: number;
    sku: string;
    codExt?: string;
    descripcion: string;
    proveedorNombre?: string;
    uxb?: number;
    moq?: number | null;
    tagReposicion?: 'PRIO' | 'LIQ' | null;
    stockActual: number;
    pendienteClientes: number;
    pendienteProveedores: number;
    saldoDisponible: number;
    ventasMes1: number;
    ventasMes2: number;
    ventasMes3: number;
    promedioVentas: number;
    promedioDiario: number;
    puntoReorden: number;
    urgente: boolean;
    sugerencia: number;
    pedido: number;
    ultimaCompraFecha?: string | null;
    ultimaCompraCantidad?: number;
}

export interface ReposicionResultDTO {
    sugerencias: SugerenciaReposicionDTO[];
    totalProductos: number;
    productosConSugerencia: number;
    advertencias: string[];
}

// ---- POST /api/reposiciones/iniciar → 202 Accepted ----
export const calcularReposicionAPI = async (): Promise<void> => {
    const response = await fetchAPI(`${API_URL}/iniciar`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-Audit-Origin": "PROCESS" },
    });
    if (!response.ok) throw new Error("Error al iniciar el cálculo de reposición");
};

// ---- GET /api/reposiciones/estado ----
export const getEstadoJobAPI = async (): Promise<ProcesoMasivoEstadoDTO> => {
    const response = await fetchAPI(`${API_URL}/estado`);
    if (!response.ok) throw new Error("Error al consultar el estado del proceso");
    return await response.json();
};

// ---- POST /api/reposiciones/cancelar ----
export const cancelarJobAPI = async (): Promise<void> => {
    const response = await fetchAPI(`${API_URL}/cancelar`, {
        method: "POST",
        headers: { "X-Audit-Origin": "PROCESS" },
    });
    if (!response.ok) throw new Error("Error al cancelar el proceso");
};

// ---- GET /api/reposiciones/resultado ----
export const getResultadoAPI = async (): Promise<ReposicionResultDTO | null> => {
    const response = await fetchAPI(`${API_URL}/resultado`);
    if (response.status === 204) return null;
    if (!response.ok) throw new Error("Error al obtener los resultados");
    return await response.json();
};

// ---- PUT /api/reposiciones/resultado/ajustar ----
export const ajustarPedidosAPI = async (
    ajustes: { productoId: number; pedido: number }[]
): Promise<ReposicionResultDTO> => {
    const response = await fetchAPI(`${API_URL}/resultado/ajustar`, {
        method: "PUT",
        headers: { "Content-Type": "application/json", "X-Audit-Origin": "PROCESS" },
        body: JSON.stringify({ ajustes }),
    });
    if (!response.ok) throw new Error("Error al ajustar pedidos");
    return await response.json();
};

// ---- POST /api/reposiciones/generar-ordenes ----
export const generarOrdenesAPI = async (proveedorId?: number): Promise<void> => {
    const params = proveedorId ? `?proveedorId=${proveedorId}` : "";
    const response = await fetchAPI(`${API_URL}/generar-ordenes${params}`, {
        method: "POST",
        headers: { "X-Audit-Origin": "PROCESS" },
    });
    if (!response.ok) throw new Error("Error al generar las órdenes de compra");
};

// ---- GET /api/reposiciones/resultado/excel ----
export const descargarExcelAPI = async (): Promise<Blob> => {
    const res = await fetchAPI(`${API_URL}/resultado/excel`);
    return res.blob();
};

// ---- GET /api/reposiciones/config ----
export const getConfigAPI = async (): Promise<ReposicionConfigDTO> => {
    const response = await fetchAPI(`${API_URL}/config`);
    if (!response.ok) throw new Error("Error al obtener la configuración");
    return await response.json();
};

// ---- PUT /api/reposiciones/config ----
export const updateConfigAPI = async (config: ReposicionConfigDTO): Promise<ReposicionConfigDTO> => {
    const response = await fetchAPI(`${API_URL}/config`, {
        method: "PUT",
        headers: { "Content-Type": "application/json", "X-Audit-Origin": "FORM" },
        body: JSON.stringify(config),
    });
    if (!response.ok) throw new Error("Error al guardar la configuración");
    return await response.json();
};

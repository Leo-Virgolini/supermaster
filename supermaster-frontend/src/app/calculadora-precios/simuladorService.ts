// Servicio para la calculadora "what-if" de precio de canal con producto hipotético.
// Reusa el endpoint POST /api/precios/simular-completo del backend, que construye un producto
// transitorio (sin persistir) y devuelve la fórmula paso a paso + los indicadores calculados.

import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { getProductosAPI, getNombreById } from "../productos/productosService";
import { getProductoMargenAPI } from "../productos/productoMargenService";
import { getProductoPrecioInfladoPorCanalAPI } from "../productos/productoSubRecursosService";
import type { FormulaCalculo } from "../producto-canal-precios/types";
import type { ProductoDTO, Tag } from "../productos/types";
import type { TipoPrecioInflado } from "../precios-inflados/types";

export interface IndicadoresCalculados {
    canalId: number;
    canalNombre: string;
    cuotas: number | null;
    pvp: number;
    pvpInflado: number | null;
    costoProducto: number;
    costosVenta: number;
    ingresoNetoVendedor: number;
    ganancia: number;
    margenSobreIngresoNeto: number;
    margenSobrePvp: number;
    markupPorcentaje: number;
    fechaUltimoCalculo: string | null;
}

export interface SimulacionResultado {
    formula: FormulaCalculo;
    indicadores: IndicadoresCalculados;
}

export interface SimulacionPrecioInput {
    canalId: number;
    cuotas?: number | null;        // -1=transf, 0=contado, >0=cuotas, null=sin cuotas

    costo: number;
    iva: number;

    marcaId?: number | null;
    tipoId?: number | null;
    clasifGralId?: number | null;
    clasifGastroId?: number | null;
    tag?: "MAQUINA" | "REPUESTO" | "MENAJE" | null;

    proveedorFinanciacionPorcentaje?: number | null;
    mlaPrecioEnvio?: number | null;
    mlaComisionPorcentaje?: number | null;

    margenMinorista: number;
    margenMayorista: number;
    margenFijoMinorista?: number | null;
    margenFijoMayorista?: number | null;

    precioInfladoTipo?: TipoPrecioInflado | null;
    precioInfladoValor?: number | null;
}

// Snapshot del producto + margen + proveedor.financiación + mla (envío y comisión)
// para precargar el form del simulador con todos los atributos relevantes.
export interface ProductoSnapshot {
    productoId: number;
    sku: string;
    label: string; // "[SKU] descripcion"
    costo: number | null;
    iva: number | null;
    marcaId: number | null;
    marcaLabel: string;
    tipoId: number | null;
    tipoLabel: string;
    clasifGralId: number | null;
    clasifGralLabel: string;
    clasifGastroId: number | null;
    clasifGastroLabel: string;
    tag: Tag | null;
    proveedorFinanciacionPorcentaje: number | null;
    mlaPrecioEnvio: number | null;
    mlaComisionPorcentaje: number | null;
    margenMinorista: number | null;
    margenMayorista: number | null;
    margenFijoMinorista: number | null;
    margenFijoMayorista: number | null;
    precioInfladoTipo: TipoPrecioInflado | null;
    precioInfladoValor: number | null;
    precioInfladoCodigo: string | null;
}

// Búsqueda de productos por SKU/MLA/descripción para el AsyncSelect.
export const searchProductosForSimulacionAPI = async (query: string): Promise<{ id: number; label: string }[]> => {
    try {
        const res = await getProductosAPI(0, 15, { search: query }, "id,desc");
        return (res.content || []).map((p: ProductoDTO) => ({
            id: p.id,
            label: `[${p.sku}] ${p.tituloWeb || p.descripcion || ""}`.trim() + (p.mlaNombre ? ` · ${p.mlaNombre}` : ""),
        }));
    } catch {
        return [];
    }
};

// Resuelve un nombre a partir del ID si el producto no lo trae flattened.
// Si ya tenemos el nombre, no hacemos fetch.
async function resolveNombre(endpoint: string, id: number | null | undefined, existing?: string | null): Promise<string> {
    if (existing) return existing;
    if (id == null) return "";
    const info = await getNombreById(endpoint, id, "nombre");
    return info.nombre;
}

// Trae los datos del producto (con sus IDs y nombres flattened), su margen, los datos
// de proveedor/MLA, y la regla de precio inflado asignada al canal (si existe).
// Devuelve un snapshot listo para precargar el form.
export const loadProductoSnapshotAPI = async (productoId: number, canalId?: number | null): Promise<ProductoSnapshot> => {
    const productoRes = await fetchAPI(`${API_BASE_URL}/api/productos/${productoId}`);
    if (!productoRes.ok) throw new Error("Producto no encontrado");
    const producto: ProductoDTO = await productoRes.json();

    // En paralelo: margen, proveedor, mla, nombres de clasifs, y precio inflado del canal.
    const [margen, proveedor, mla, marcaLabel, tipoLabel, clasifGralLabel, clasifGastroLabel, infladoCanal] = await Promise.all([
        getProductoMargenAPI(productoId).catch(() => null),
        producto.proveedorId
            ? fetchAPI(`${API_BASE_URL}/api/proveedores/${producto.proveedorId}`)
                .then((r) => (r.ok ? r.json() : null))
                .catch(() => null)
            : Promise.resolve(null),
        producto.mlaId
            ? fetchAPI(`${API_BASE_URL}/api/mlas/${producto.mlaId}`)
                .then((r) => (r.ok ? r.json() : null))
                .catch(() => null)
            : Promise.resolve(null),
        resolveNombre("marcas", producto.marcaId, producto.marcaNombre),
        resolveNombre("tipos", producto.tipoId, producto.tipoNombre),
        resolveNombre("clasif-gral", producto.clasifGralId, producto.clasifGralNombre),
        resolveNombre("clasif-gastro", producto.clasifGastroId, producto.clasifGastroNombre),
        canalId != null
            ? getProductoPrecioInfladoPorCanalAPI(productoId, canalId).catch(() => null)
            : Promise.resolve(null),
    ]);

    // El precio inflado se considera solo si está activo y dentro del rango de fechas (si tiene).
    const reglaInflada = (() => {
        if (!infladoCanal || !infladoCanal.activo) return null;
        const hoy = new Date();
        if (infladoCanal.fechaDesde && new Date(infladoCanal.fechaDesde) > hoy) return null;
        if (infladoCanal.fechaHasta && new Date(infladoCanal.fechaHasta) < hoy) return null;
        return infladoCanal.precioInflado ?? null;
    })();

    return {
        productoId: producto.id,
        sku: producto.sku,
        label: `[${producto.sku}] ${producto.tituloWeb || producto.descripcion || ""}`.trim(),
        costo: producto.costo ?? null,
        iva: producto.iva ?? null,
        marcaId: producto.marcaId ?? null,
        marcaLabel,
        tipoId: producto.tipoId ?? null,
        tipoLabel,
        clasifGralId: producto.clasifGralId ?? null,
        clasifGralLabel,
        clasifGastroId: producto.clasifGastroId ?? null,
        clasifGastroLabel,
        tag: producto.tag ?? null,
        proveedorFinanciacionPorcentaje: proveedor?.financiacionPorcentaje ?? null,
        mlaPrecioEnvio: mla?.precioEnvio ?? null,
        mlaComisionPorcentaje: mla?.comisionPorcentaje ?? null,
        margenMinorista: margen?.margenMinorista ?? null,
        margenMayorista: margen?.margenMayorista ?? null,
        margenFijoMinorista: margen?.margenFijoMinorista ?? null,
        margenFijoMayorista: margen?.margenFijoMayorista ?? null,
        precioInfladoTipo: (reglaInflada?.tipo as TipoPrecioInflado) ?? null,
        precioInfladoValor: reglaInflada?.valor ?? null,
        precioInfladoCodigo: reglaInflada?.codigo ?? null,
    };
};

export const simularPrecioAPI = async (input: SimulacionPrecioInput): Promise<SimulacionResultado> => {
    const res = await fetchAPI(`${API_BASE_URL}/api/precios/simular-completo`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(input),
    });
    if (!res.ok) {
        let msg = "Error al simular precio";
        try {
            const body = await res.json();
            msg = body?.message ?? body?.mensaje ?? msg;
        } catch { /* ignore */ }
        throw new Error(msg);
    }
    return res.json();
};

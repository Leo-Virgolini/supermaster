import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import type { AuditoriaCambioDTO } from "../auditoria/types";
import { ProductoCreateDTO, ProductoPatchDTO } from "./types";

const API_URL = `${API_BASE_URL}/api/productos`;
type ProductoAuditOrigin = "FORM" | "INLINE" | "TABLE" | "MONITOR_PRECIOS" | "API";

const withAuditOrigin = (origin?: ProductoAuditOrigin, headers?: HeadersInit): HeadersInit => ({
	...(headers as Record<string, string> ?? {}),
	...(origin ? { "X-Audit-Origin": origin } : {}),
});

// Cache de lookups de relaciones (ID → nombre)
type RelationLookups = {
	marcas: Record<number, string>;
	tipos: Record<number, string>;
	clasifGral: Record<number, string>;
	clasifGastro: Record<number, string>;
	proveedores: Record<number, string>;
	origenes: Record<number, string>;
	materiales: Record<number, string>;
	mlas: Record<number, string>;
};

type LookupItem = Record<string, unknown> & {
	id: number;
	nombre?: string;
	mla?: string;
};

type SearchOption = {
	id: number;
	label: string;
};

let _lookupsCache: RelationLookups | null = null;
let _lookupsCacheTime = 0;
const CACHE_TTL = 5 * 60 * 1000; // 5 minutos

async function fetchLookup(endpoint: string, labelKey: string): Promise<Record<number, string>> {
	try {
		const res = await fetchAPI(`${API_BASE_URL}/api/${endpoint}?page=0&size=9999&sort=id,asc`);
		if (!res.ok) return {};
		const json = await res.json();
		const map: Record<number, string> = {};
		(json.content as LookupItem[] | undefined || []).forEach((item) => {
			map[item.id] = String(
				item[labelKey] ??
				item.nombre ??
				item.mla ??
				"",
			);
		});
		return map;
	} catch { return {}; }
}

async function loadRelationLookups(): Promise<RelationLookups> {
	if (_lookupsCache && Date.now() - _lookupsCacheTime < CACHE_TTL) {
		return _lookupsCache;
	}
	const [marcas, tipos, clasifGral, clasifGastro, proveedores, origenes, materiales, mlas] = await Promise.all([
		fetchLookup("marcas", "nombre"),
		fetchLookup("tipos", "nombre"),
		fetchLookup("clasif-gral", "nombre"),
		fetchLookup("clasif-gastro", "nombre"),
		fetchLookup("proveedores", "nombre"),
		fetchLookup("origenes", "nombre"),
		fetchLookup("materiales", "nombre"),
		fetchLookup("mlas", "mla"),
	]);
	_lookupsCache = { marcas, tipos, clasifGral, clasifGastro, proveedores, origenes, materiales, mlas };
	_lookupsCacheTime = Date.now();
	return _lookupsCache;
}

const cleanPayload = <T extends object>(data: T): T => {
	const clean = { ...(data as Record<string, unknown>) };

	// Borramos nombres visuales
	delete clean.marcaNombre;
	delete clean.origenNombre;
	delete clean.clasifGralNombre;
	delete clean.clasifGastroNombre;
	delete clean.tipoNombre;
	delete clean.proveedorNombre;
	delete clean.materialNombre;
	delete clean.mlaNombre;
	delete clean.fechaCreacion;
	delete clean.fechaModificacion;
	delete clean.fechaUltimoCosto;

	// Borramos objetos anidados para evitar conflictos en el backend
	delete clean.marca;
	delete clean.rubro;
	delete clean.clasifGral;
	delete clean.clasifGastro;
	delete clean.tipo;
	delete clean.proveedor;
	delete clean.origen;
	delete clean.material;
	delete clean.mla;

	return clean as T;
};

// GET PRODUCTOS
export const getProductosAPI = async (
	page: number,
	size: number,
	filters: Record<string, unknown> = {},
	sort: string | string[] = "id,asc",
	signal?: AbortSignal,
) => {
	const params = new URLSearchParams({
		page: page.toString(),
		size: size.toString(),
	});

	const sortArray = Array.isArray(sort) ? sort : [sort];
	sortArray.forEach((s) => params.append("sort", s));

	Object.entries(filters).forEach(([key, value]) => {
		if (value !== undefined && value !== null && value !== "") {
			if (Array.isArray(value)) {
				params.append(key, value.join(","));
			} else {
				params.append(key, String(value));
			}
		}
	});

	const response = await fetchAPI(`${API_URL}?${params.toString()}`, { signal });
	if (!response.ok) throw new Error("Error al conectar");

	return await response.json();
};

// GET PRODUCTOS CON NOMBRES RESUELTOS (para export)
export const getProductosForExportAPI = async (page: number, size: number, filters: Record<string, unknown> = {}, sort: string | string[] = "id,asc") => {
	const json = await getProductosAPI(page, size, filters, sort);
	const lookups = await loadRelationLookups();

	const content = json.content.map((p: Record<string, unknown>) => ({
		...p,
		marcaNombre: lookups.marcas[Number(p.marcaId)] || "---",
		clasifGralNombre: lookups.clasifGral[Number(p.clasifGralId)] || "---",
		clasifGastroNombre: lookups.clasifGastro[Number(p.clasifGastroId)] || "---",
		tipoNombre: lookups.tipos[Number(p.tipoId)] || "---",
		proveedorNombre: lookups.proveedores[Number(p.proveedorId)] || "---",
		origenNombre: lookups.origenes[Number(p.origenId)] || "---",
		materialNombre: lookups.materiales[Number(p.materialId)] || "---",
		mlaNombre: lookups.mlas[Number(p.mlaId)] || "---",
	}));

	return { ...json, content };
};
export const createProductoAPI = async (data: ProductoCreateDTO, origin: ProductoAuditOrigin = "API") => {
	const response = await fetchAPI(API_URL, {
		method: "POST",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(cleanPayload(data)),
	});
	if (!response.ok) throw new Error("Error al crear producto");
	return await response.json();
};
export const updateProductoAPI = async (id: number, data: ProductoPatchDTO, origin: ProductoAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(cleanPayload(data)),
	});
	if (!response.ok) throw new Error("Error al actualizar");
	return await response.json();
};
export const deleteProductoAPI = async (id: number, origin: ProductoAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "DELETE",
		headers: withAuditOrigin(origin),
	});
	if (!response.ok) throw new Error("Error al eliminar");
	return true;
};

export const getProductoAuditoriaAPI = async (
	id: number,
	page = 0,
	size = 50,
	sort: string | string[] = "fechaHora,desc",
) => {
	const params = new URLSearchParams({
		page: String(page),
		size: String(size),
	});
	const sortArray = Array.isArray(sort) ? sort : [sort];
	sortArray.forEach((item) => params.append("sort", item));

	const res = await fetchAPI(`${API_URL}/${id}/auditoria?${params.toString()}`);
	if (!res.ok) throw new Error("Error al cargar historial del producto");
	return await res.json() as { content: AuditoriaCambioDTO[]; page?: { totalElements?: number } };
};

// --- HELPER PARA BUSCAR NOMBRE POR ID (Auto-corrección visual) ---
export const getNombreById = async (endpoint: string, id: number, labelKey: string = "nombre") => {
	try {
		const res = await fetchAPI(`${API_BASE_URL}/api/${endpoint}/${id}`);
		if (!res.ok) return "---";
		const json = await res.json();
		return json[labelKey] || json.nombre || json.mla || "---";
	} catch {
		return "Error";
	}
};
// --- HELPERS PARA ASYNC SELECT (Buscadores) ---
const fetchOptions = async (endpoint: string, query: string, labelKey: string = "nombre", size: number = 20) => {
	const url = `${API_BASE_URL}/api/${endpoint}?page=0&size=${size}&search=${query}`;
	const res = await fetchAPI(url);
	if (!res.ok) return [];
	const json = await res.json();

	const q = query.toLowerCase();
	return (json.content as LookupItem[]).map((item) => ({
		id: item.id,
		label:
			String(item[labelKey] ?? item.nombre ?? item.mla ?? "Sin Nombre"),
	} satisfies SearchOption)).sort((a, b) => {
		const aStarts = a.label.toLowerCase().startsWith(q) ? 0 : 1;
		const bStarts = b.label.toLowerCase().startsWith(q) ? 0 : 1;
		return aStarts - bStarts;
	});
};

export const searchMarcas = (q: string, size?: number) => fetchOptions("marcas", q, "nombre", size);
export const searchOrigenes = (q: string, size?: number) => fetchOptions("origenes", q, "nombre", size);
export const searchClasifGral = (q: string, size?: number) => fetchOptions("clasif-gral", q, "nombre", size);
export const searchClasifGastro = (q: string, size?: number) => fetchOptions("clasif-gastro", q, "nombre", size);
export const searchTipos = (q: string, size?: number) => fetchOptions("tipos", q, "nombre", size);
export const searchProveedores = (q: string, size?: number) => fetchOptions("proveedores", q, "nombre", size);
export const searchMateriales = (q: string, size?: number) => fetchOptions("materiales", q, "nombre", size);
export const searchMlas = (q: string, size?: number) => fetchOptions("mlas", q, "mla", size);
export const searchCanales = (q: string) => fetchOptions("canales", q, "nombre");
export const searchCatalogos = (q: string, size?: number) => fetchOptions("catalogos", q, "nombre", size);
export const searchAptos = (q: string, size?: number) => fetchOptions("aptos", q, "nombre", size);
export const searchClientes = (q: string, size?: number) => fetchOptions("clientes", q, "nombre", size);

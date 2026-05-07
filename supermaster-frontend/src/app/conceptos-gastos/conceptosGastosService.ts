import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";

const API_URL = `${API_BASE_URL}/api/conceptos-calculo`;
type ConceptoAuditOrigin = "FORM" | "INLINE" | "TABLE" | "API";

const withAuditOrigin = (origin?: ConceptoAuditOrigin, headers?: HeadersInit): HeadersInit => ({
	...(headers as Record<string, string> ?? {}),
	...(origin ? { "X-Audit-Origin": origin } : {}),
});

export type NaturalezaConcepto =
	| "COSTO_PRODUCTO"
	| "COSTO_VENTA"
	| "IMPUESTO"
	| "MARKUP"
	| "INFLACION"
	| "DESCUENTO"
	| "BASE"
	| "COSMETICO";

export interface ConceptoGastoDTO {
	id: number;
	nombre: string;
	porcentaje: number | null;
	aplicaSobre: string; // 'PVP' | 'COSTO' | etc.
	etapa: string; // 'COSTO' | 'MARGEN' | 'IMPUESTOS' | 'PRECIO' | 'POST_PRECIO' — derivado por backend desde aplicaSobre
	naturaleza: NaturalezaConcepto; // naturaleza contable (override de la columna o default del aplicaSobre)
	descripcion: string | null;
}

// GET
export const getConceptosGastoAPI = async (page: number, size: number, filters: Record<string, any> = {}, sort = "id,asc") => {
	const params = new URLSearchParams({
		page: page.toString(),
		size: size.toString(),
		sort,
	});

	Object.entries(filters).forEach(([key, value]) => {
		if (value !== undefined && value !== null && value !== "") {
			if (Array.isArray(value)) {
				params.append(key, value.join(","));
			} else {
				params.append(key, String(value));
			}
		}
	});

	const response = await fetchAPI(`${API_URL}?${params.toString()}`);
	if (!response.ok) throw new Error("Error al conectar");
	return await response.json();
};

export const searchConceptosAPI = async (query: string) => {
	// Si la API soporta filtrado por nombre/descripcion
	const searchParam = query ? `&search=${encodeURIComponent(query)}` : "";

	// Pedimos una lista corta (page 0, size 20) ordenada por nombre/descripción
	const response = await fetchAPI(`${API_URL}?page=0&size=20&sort=id,desc${searchParam}`);

	if (!response.ok) {
		throw new Error("Error al buscar conceptos");
	}

	return await response.json();
};

// CREATE — `etapa` es derivada por el backend desde aplicaSobre, no se envía.
// `naturaleza` es opcional: si no se manda, el backend la deriva del default
// del aplicaSobre. Se envía cuando el usuario quiere override.
export type ConceptoGastoCreatePayload =
	Omit<ConceptoGastoDTO, "id" | "etapa" | "naturaleza"> & { naturaleza?: NaturalezaConcepto | null };

export const createConceptoGastoAPI = async (
	data: ConceptoGastoCreatePayload,
	origin: ConceptoAuditOrigin = "API",
) => {
	const response = await fetchAPI(API_URL, {
		method: "POST",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});
	if (!response.ok) throw new Error("Error al crear");
	return await response.json();
};

// UPDATE
export const updateConceptoGastoAPI = async (id: number, data: Partial<ConceptoGastoDTO>, origin: ConceptoAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});
	if (!response.ok) throw new Error("Error al actualizar");
	return await response.json();
};

// CANALES DE UN CONCEPTO
export const getCanalesDelConceptoAPI = async (conceptoId: number): Promise<string[]> => {
	const response = await fetchAPI(`${API_URL}/${conceptoId}/canales`);
	if (!response.ok) throw new Error("Error al obtener canales");
	return await response.json();
};

// DELETE
export const deleteConceptoGastoAPI = async (id: number, origin: ConceptoAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, { method: "DELETE", headers: withAuditOrigin(origin) });
	if (!response.ok) throw new Error("Error al eliminar");
	return true;
};

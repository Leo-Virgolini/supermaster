// 1. CONSTANTES
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";

const API_URL = `${API_BASE_URL}/api/clasif-gastro`;
type ClasifGastroAuditOrigin = "FORM" | "INLINE" | "TABLE" | "API";

const withAuditOrigin = (origin?: ClasifGastroAuditOrigin, headers?: HeadersInit): HeadersInit => ({
	...(headers as Record<string, string> ?? {}),
	...(origin ? { "X-Audit-Origin": origin } : {}),
});

// READ
export const getClasifGastroAPI = async (
	page: number,
	size: number,
	filters: Record<string, any> = {},
	sort = "id,asc",
) => {
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
	if (!response.ok) throw new Error("Error al conectar con el servidor");
	return await response.json();
};

// BUSCAR
export const searchClasifGastroAPI = async (query: string) => {
	const searchParam = query ? `&search=${encodeURIComponent(query)}` : "";
	const params = `?page=0&size=10&sort=nombre,asc${searchParam}`; // sort by 'nombre'
	const response = await fetchAPI(API_URL + params);
	if (!response.ok)
		throw new Error("Error al buscar clasificaciones gastronómicas");
	return await response.json();
};

// CREATE
export const createClasifGastroAPI = async (data: {
	nombre: string;
	esMaquina?: boolean;
	padreId?: number | null;
}, origin: ClasifGastroAuditOrigin = "API") => {
	const payload: Record<string, unknown> = {
		nombre: data.nombre,
		esMaquina: data.esMaquina ?? false,
	};
	if (data.padreId != null) payload.padreId = data.padreId;
	const response = await fetchAPI(API_URL, {
		method: "POST",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(payload),
	});

	if (!response.ok) {
		const errorData = await response.json();
		throw new Error(errorData.message || "Error al crear clasificación");
	}

	return await response.json();
};

// DELETE
export const deleteClasifGastroAPI = async (id: number, origin: ClasifGastroAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, { method: "DELETE", headers: withAuditOrigin(origin) });
	if (!response.ok) throw new Error("Error al eliminar la clasificación");
	return true;
};

// UPDATE
export const updateClasifGastroAPI = async (id: number, data: Partial<{ nombre: string; esMaquina: boolean; padreId: number | null }>, origin: ClasifGastroAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});
	if (!response.ok) throw new Error("Error al actualizar la clasificación");
	return await response.json();
};

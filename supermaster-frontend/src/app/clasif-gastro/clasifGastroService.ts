// 1. CONSTANTES
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { AuditOrigin, FilterValue, buildListParams, withAuditOrigin } from "../utils/apiHelpers";

const API_URL = `${API_BASE_URL}/api/clasif-gastro`;
type ClasifGastroAuditOrigin = AuditOrigin;

// READ
export const getClasifGastroAPI = async (
	page: number,
	size: number,
	filters: Record<string, FilterValue> = {},
	sort = "id,asc",
) => {
	const response = await fetchAPI(`${API_URL}?${buildListParams(page, size, sort, filters)}`);
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
	idDux?: number | null;
}, origin: ClasifGastroAuditOrigin = "API") => {
	const payload: Record<string, unknown> = {
		nombre: data.nombre,
		esMaquina: data.esMaquina ?? false,
	};
	if (data.padreId != null) payload.padreId = data.padreId;
	if (data.idDux != null) payload.idDux = data.idDux;
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
export const updateClasifGastroAPI = async (id: number, data: Partial<{ nombre: string; esMaquina: boolean; padreId: number | null; idDux: number | null }>, origin: ClasifGastroAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});
	if (!response.ok) throw new Error("Error al actualizar la clasificación");
	return await response.json();
};

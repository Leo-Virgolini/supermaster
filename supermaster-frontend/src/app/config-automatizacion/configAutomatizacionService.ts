import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { ConfigAutomatizacionDTO } from "./types";

const API_URL = `${API_BASE_URL}/api/config-automatizacion`;
export type ConfigAutomatizacionAuditOrigin = "FORM" | "INLINE" | "TABLE" | "API";

const withAuditOrigin = (origin: ConfigAutomatizacionAuditOrigin, extraHeaders: HeadersInit = {}) => ({
	"X-Audit-Origin": origin,
	...extraHeaders,
});

// READ (paginated)
export const getConfigAutomatizacionAPI = async (
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
export const searchConfigAutomatizacionAPI = async (query: string) => {
	const searchParam = query ? `&search=${encodeURIComponent(query)}` : "";
	const params = `?page=0&size=20&sort=id,asc${searchParam}`;
	const response = await fetchAPI(API_URL + params);
	if (!response.ok) throw new Error("Error al buscar configuraciones");
	return await response.json();
};

// CREATE
export const createConfigAutomatizacionAPI = async (data: {
	clave: string;
	valor: string;
	descripcion?: string | null;
}, origin: ConfigAutomatizacionAuditOrigin = "API") => {
	const response = await fetchAPI(API_URL, {
		method: "POST",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});
	if (!response.ok) throw new Error("Error al crear la configuración");
	return await response.json();
};

// UPDATE
export const updateConfigAutomatizacionAPI = async (
	id: number,
	data: Partial<ConfigAutomatizacionDTO>,
	origin: ConfigAutomatizacionAuditOrigin = "API",
) => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});
	if (!response.ok) throw new Error("Error al actualizar la configuración");
	return await response.json();
};

// DELETE
export const deleteConfigAutomatizacionAPI = async (
	id: number,
	origin: ConfigAutomatizacionAuditOrigin = "API",
) => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "DELETE",
		headers: withAuditOrigin(origin),
	});
	if (!response.ok) throw new Error("Error al eliminar la configuración");
	return true;
};

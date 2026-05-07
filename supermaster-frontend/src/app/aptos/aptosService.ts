// 1. CONSTANTES
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { AptoDTO } from "./types";

const API_URL = `${API_BASE_URL}/api/aptos`;
type AptoAuditOrigin = "FORM" | "INLINE" | "TABLE" | "API";

const withAuditOrigin = (origin?: AptoAuditOrigin, headers?: HeadersInit): HeadersInit => ({
	...(headers as Record<string, string> ?? {}),
	...(origin ? { "X-Audit-Origin": origin } : {}),
});

// READ
export const getAptosAPI = async (
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
export const searchAptosAPI = async (query: string) => {
	const searchParam = query ? `&search=${encodeURIComponent(query)}` : "";
	const params = `?page=0&size=10&sort=nombre,asc${searchParam}`; // sort por 'nombre'
	const response = await fetchAPI(API_URL + params);
	if (!response.ok) throw new Error("Error al buscar aptos");
	return await response.json();
};

// CREATE
export const createAptoAPI = async (apto: string, origin: AptoAuditOrigin = "API") => {
	const response = await fetchAPI(API_URL, {
		method: "POST",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify({ nombre: apto }),
	});
	if (!response.ok) throw new Error("Error al crear el apto");
	return await response.json();
};

// DELETE
export const deleteAptoAPI = async (id: number, origin: AptoAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, { method: "DELETE", headers: withAuditOrigin(origin) });
	if (!response.ok) throw new Error("Error al eliminar el apto");
	return true;
};

// UPDATE
export const updateAptoAPI = async (
	id: number,
	data: Partial<Pick<AptoDTO, "nombre">>,
	origin: AptoAuditOrigin = "API",
) => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});
	if (!response.ok) throw new Error("Error al actualizar el apto");
	return await response.json();
};

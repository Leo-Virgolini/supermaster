import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { AuditOrigin, FilterValue, buildListParams, withAuditOrigin } from "../utils/apiHelpers";

const API_URL = `${API_BASE_URL}/api/clientes`;
type ClienteAuditOrigin = AuditOrigin;

export interface ClienteDTO {
	id: number;
	nombre: string;
}

// GET
export const getClientesAPI = async (
	page: number,
	size: number,
	filters: Record<string, FilterValue> = {},
	sort = "id,desc",
) => {
	const response = await fetchAPI(`${API_URL}?${buildListParams(page, size, sort, filters)}`);
	if (!response.ok) throw new Error("Error al conectar");
	return await response.json();
};

// CREATE
export const createClienteAPI = async (data: Omit<ClienteDTO, "id">, origin: ClienteAuditOrigin = "API") => {
	const response = await fetchAPI(API_URL, {
		method: "POST",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});
	if (!response.ok) throw new Error("Error al crear cliente");
	return await response.json();
};

// UPDATE
export const updateClienteAPI = async (id: number, data: Partial<Omit<ClienteDTO, "id">>, origin: ClienteAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});
	if (!response.ok) throw new Error("Error al actualizar");
	return await response.json();
};

// DELETE
export const deleteClienteAPI = async (id: number, origin: ClienteAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, { method: "DELETE", headers: withAuditOrigin(origin) });
	if (!response.ok) throw new Error("Error al eliminar");
	return true;
};

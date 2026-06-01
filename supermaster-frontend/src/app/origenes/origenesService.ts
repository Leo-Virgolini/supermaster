// 1. CONSTANTES
// Definimos la URL base acá para no repetirla. Si cambia, se cambia solo acá.
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { AuditOrigin, FilterValue, buildListParams, withAuditOrigin } from "../utils/apiHelpers";
import { OrigenDTO } from "./types";

const API_URL = `${API_BASE_URL}/api/origenes`;
type OrigenAuditOrigin = AuditOrigin;

// READ: Traer la lista paginada
export const getOrigenesAPI = async (
	page: number,
	size: number,
	filters: Record<string, FilterValue> = {},
	sort = "id,asc",
) => {
	const response = await fetchAPI(`${API_URL}?${buildListParams(page, size, sort, filters)}`);

	if (!response.ok) throw new Error("Error al conectar con el servidor");
	return await response.json();
};

// BUSCAR: Filtra por texto (Select)
export const searchOrigenesAPI = async (query: string) => {
	// encodeURIComponent es clave por si buscan "DyG" o espacios
	const searchParam = query ? `&search=${encodeURIComponent(query)}` : "";

	// Pedimos página 0 y solo 10 resultados (no necesitamos 1000 para un dropdown)
	const params = `?page=0&size=10&sort=nombre,asc${searchParam}`;

	const response = await fetchAPI(API_URL + params);

	if (!response.ok) {
		throw new Error("Error al buscar orígenes");
	}
	return await response.json();
};

// CREATE: Crear un nuevo origen
export const createOrigenAPI = async (origen: string, origin: OrigenAuditOrigin = "API") => {
	const response = await fetchAPI(API_URL, {
		method: "POST",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify({ nombre: origen }),
	});

	if (!response.ok) throw new Error("Error al crear el origen");
	return await response.json();
};

// DELETE: Borrar un origen por ID
export const deleteOrigenAPI = async (id: number, origin: OrigenAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "DELETE",
		headers: withAuditOrigin(origin),
	});

	if (!response.ok) throw new Error("Error al eliminar el origen");
	return true;
};

// UPDATE: Editar un origen existente
export const updateOrigenAPI = async (
	id: number,
	data: Partial<Pick<OrigenDTO, "nombre">>,
	origin: OrigenAuditOrigin = "API",
) => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});

	if (!response.ok) throw new Error("Error al actualizar el origen");
	return await response.json();
};

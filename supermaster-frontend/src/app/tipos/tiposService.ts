// 1. CONSTANTES
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { AuditOrigin, FilterValue, buildListParams, withAuditOrigin } from "../utils/apiHelpers";

const API_URL = `${API_BASE_URL}/api/tipos`;
type TipoAuditOrigin = AuditOrigin;

// READ: Traer la lista paginada
export const getTiposAPI = async (
	page: number,
	size: number,
	filters: Record<string, FilterValue> = {},
	sort = "id,asc",
) => {
	const response = await fetchAPI(`${API_URL}?${buildListParams(page, size, sort, filters)}`);

	if (!response.ok) throw new Error("Error al conectar con el servidor");
	return await response.json();
};

// BUSCAR: Filtra por texto
export const searchTiposAPI = async (query: string) => {
	const searchParam = query ? `&search=${encodeURIComponent(query)}` : "";
	const params = `?page=0&size=10&sort=nombre,asc${searchParam}`;

	const response = await fetchAPI(API_URL + params);

	if (!response.ok) {
		throw new Error("Error al buscar tipos");
	}
	return await response.json();
};

// CREATE: Crear un nuevo tipo
export const createTipoAPI = async (nombre: string, padreId: number | null = null, origin: TipoAuditOrigin = "API") => {
	const response = await fetchAPI(API_URL, {
		method: "POST",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify({ nombre, padreId }),
	});

	if (!response.ok) throw new Error("Error al crear el tipo");
	return await response.json();
};

// DELETE: Borrar un tipo por ID
export const deleteTipoAPI = async (id: number, origin: TipoAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "DELETE",
		headers: withAuditOrigin(origin),
	});

	if (!response.ok) {
		if (response.status === 409) {
			throw new Error(`No se puede eliminar: el tipo #${id} tiene subtipos o productos asociados. Eliminá primero los dependientes.`);
		}
		const text = await response.text().catch(() => "");
		throw new Error(text || `Error al eliminar el tipo #${id}`);
	}
	return true;
};

// UPDATE: Editar un tipo existente
export const updateTipoAPI = async (id: number, data: { nombre?: string; padreId?: number | null }, origin: TipoAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});

	if (!response.ok) throw new Error("Error al actualizar el tipo");
	return await response.json();
};

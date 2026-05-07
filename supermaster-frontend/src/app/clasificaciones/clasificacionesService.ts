// 1. CONSTANTES
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";

// Definimos la URL base acá para no repetirla. Si cambia, se cambia solo acá.
const API_URL = `${API_BASE_URL}/api/clasif-gral`;
type ClasifGralAuditOrigin = "FORM" | "INLINE" | "TABLE" | "API";

const withAuditOrigin = (origin?: ClasifGralAuditOrigin, headers?: HeadersInit): HeadersInit => ({
	...(headers as Record<string, string> ?? {}),
	...(origin ? { "X-Audit-Origin": origin } : {}),
});

// READ: Traer la lista paginada
export const getClasificacionesAPI = async (
	page: number,
	size: number,
	filters: Record<string, any> = {},
	sort = "id,asc",
) => {
	// Armamos la query string con paginación y orden por ID
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

// BUSCAR: Filtra por texto (Select)
export const searchClasificacionesAPI = async (query: string) => {
	// encodeURIComponent es clave por si buscan "DyG" o espacios
	const searchParam = query ? `&search=${encodeURIComponent(query)}` : "";

	// Pedimos página 0 y solo 10 resultados (no necesitamos 1000 para un dropdown)
	const params = `?page=0&size=10&sort=nombre,asc${searchParam}`;

	const response = await fetchAPI(API_URL + params);

	if (!response.ok) {
		throw new Error("Error al buscar clasificaciones");
	}
	return await response.json();
};

// CREATE: Crear una nueva clasificación
export const createClasificacionAPI = async (
	nombre: string,
	padreId: number | null,
	origin: ClasifGralAuditOrigin = "API",
) => {
	const response = await fetchAPI(API_URL, {
		method: "POST",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify({ nombre, padreId }),
	});

	if (!response.ok) throw new Error("Error al crear la clasificación");
	return await response.json();
};

// DELETE: Borrar una clasificación por ID
export const deleteClasificacionAPI = async (id: number, origin: ClasifGralAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "DELETE",
		headers: withAuditOrigin(origin),
	});

	if (!response.ok) throw new Error("Error al eliminar la clasificación");
	return true;
};

// UPDATE: Editar una clasificación existente
export const updateClasificacionAPI = async (
	id: number,
	data: { nombre?: string; padreId?: number | null },
	origin: ClasifGralAuditOrigin = "API",
) => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});

	if (!response.ok) throw new Error("Error al actualizar la clasificación");
	return await response.json();
};

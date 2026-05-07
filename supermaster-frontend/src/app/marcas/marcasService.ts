// 1. CONSTANTES
// Definimos la URL base acá para no repetirla. Si cambia, se cambia solo acá.
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";

const API_URL = `${API_BASE_URL}/api/marcas`;
type MarcaAuditOrigin = "FORM" | "INLINE" | "TABLE" | "API";

const withAuditOrigin = (origin?: MarcaAuditOrigin, headers?: HeadersInit): HeadersInit => ({
	...(headers as Record<string, string> ?? {}),
	...(origin ? { "X-Audit-Origin": origin } : {}),
});

// READ: Traer la lista paginada
// READ: Traer la lista paginada
export const getMarcasAPI = async (
	page: number,
	size: number,
	filters: Record<string, any> = {},
	sort = "id,desc",
) => {
	const params = new URLSearchParams({
		page: page.toString(),
		size: size.toString(),
		sort,
	});

	Object.entries(filters).forEach(([key, value]) => {
		if (value !== undefined && value !== null && value !== "") {
			if (Array.isArray(value)) {
				const joined = value.join(",");
				params.append(key, joined);
			} else {
				params.append(key, String(value));
			}
		}
	});

	const finalUrl = `${API_URL}?${params.toString()}`;
	const response = await fetchAPI(finalUrl);
	if (!response.ok) throw new Error("Error al conectar");
	return await response.json();
};

// BUSCAR: Filtra por texto (Select)
export const searchMarcasAPI = async (query: string) => {
	// encodeURIComponent es clave por si buscan "DyG" o espacios
	const searchParam = query ? `&search=${encodeURIComponent(query)}` : "";

	// Pedimos página 0 y solo 10 resultados (no necesitamos 1000 para un dropdown)
	const params = `?page=0&size=10&sort=nombre,asc${searchParam}`;

	const response = await fetchAPI(API_URL + params);

	if (!response.ok) {
		throw new Error("Error al buscar marcas");
	}
	return await response.json();
};

// CREATE: Crear una nueva marca
export const createMarcaAPI = async (
	nombre: string,
	padreId: number | null,
	origin: MarcaAuditOrigin = "API",
) => {
	const response = await fetchAPI(API_URL, {
		method: "POST",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify({ nombre, padreId }),
	});

	if (!response.ok) throw new Error("Error al crear la marca");
	return await response.json();
};

// DELETE: Borrar una marca por ID
export const deleteMarcaAPI = async (id: number, origin: MarcaAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "DELETE",
		headers: withAuditOrigin(origin),
	});

	if (!response.ok) throw new Error("Error al eliminar la marca");
	return true;
};

// UPDATE: Editar una marca existente
export const updateMarcaAPI = async (
	id: number,
	data: { nombre?: string; padreId?: number | null },
	origin: MarcaAuditOrigin = "API",
) => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});

	if (!response.ok) throw new Error("Error al actualizar la marca");
	return await response.json();
};

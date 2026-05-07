import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { CatalogoDTO } from "./types";

const API_URL = `${API_BASE_URL}/api/catalogos`;

// READ
export const getCatalogosAPI = async (
	page: number,
	size: number,
	filters: Record<string, unknown> = {},
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
export const searchCatalogosAPI = async (query: string) => {
	const searchParam = query ? `&search=${encodeURIComponent(query)}` : "";
	const params = `?page=0&size=10&sort=nombre,asc${searchParam}`; // sort by 'nombre'
	const response = await fetchAPI(API_URL + params);
	if (!response.ok) throw new Error("Error al buscar catálogos");
	return await response.json();
};

// CREATE
// Ahora recibimos un objeto con la config completa, no solo el string
export const createCatalogoAPI = async (data: {
	nombre: string;
	exportarConIva?: boolean;
	recargoPorcentaje?: number;
}) => {
	const payload = {
		nombre: data.nombre,
		exportarConIva: data.exportarConIva ?? true,
		recargoPorcentaje: data.recargoPorcentaje ?? 0,
	};

	const response = await fetchAPI(API_URL, {
		method: "POST",
		headers: { "Content-Type": "application/json", "X-Audit-Origin": "FORM" },
		body: JSON.stringify(payload),
	});

	if (!response.ok) throw new Error("Error al crear el catálogo");
	return await response.json();
};

// DELETE
export const deleteCatalogoAPI = async (id: number) => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "DELETE",
		headers: { "X-Audit-Origin": "TABLE" },
	});
	if (!response.ok) throw new Error("Error al eliminar el catálogo");
	return true;
};

// UPDATE
export const updateCatalogoAPI = async (id: number, data: Partial<CatalogoDTO>) => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: { "Content-Type": "application/json", "X-Audit-Origin": "INLINE" },
		body: JSON.stringify(data),
	});
	if (!response.ok) throw new Error("Error al actualizar el catálogo");
	return await response.json();
};

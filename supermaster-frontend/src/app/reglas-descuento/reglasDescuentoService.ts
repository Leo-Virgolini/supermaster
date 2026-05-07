import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { ReglaDescuentoDTO } from "./types";

const API_URL = `${API_BASE_URL}/api/reglas-descuento`;

// GET
export const getReglasAPI = async (page: number, size: number, filters: Record<string, unknown> = {}, sort = "id,asc") => {
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
	if (!response.ok) throw new Error("Error al conectar");
	return await response.json();
};

// CREATE
export const createReglaAPI = async (data: Omit<ReglaDescuentoDTO, "id">) => {
	const response = await fetchAPI(API_URL, {
		method: "POST",
		headers: { "Content-Type": "application/json", "X-Audit-Origin": "FORM" },
		body: JSON.stringify(data),
	});
	if (!response.ok) throw new Error("Error al crear la regla");
	return await response.json();
};

// UPDATE
export const updateReglaAPI = async (id: number, data: Partial<ReglaDescuentoDTO>) => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: { "Content-Type": "application/json", "X-Audit-Origin": "INLINE" },
		body: JSON.stringify(data),
	});
	if (!response.ok) throw new Error("Error al actualizar");
	return await response.json();
};

// DELETE
export const deleteReglaAPI = async (id: number) => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "DELETE",
		headers: { "X-Audit-Origin": "TABLE" },
	});
	if (!response.ok) throw new Error("Error al eliminar");
	return true;
};

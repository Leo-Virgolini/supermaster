import { PrecioInfladoDTO, PageResponse } from "./types";
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";

const API_URL = `${API_BASE_URL}/api/precios-inflados`;

export const getPreciosInfladosAPI = async (
	page: number,
	size: number,
	search: string = "",
	sort = "id,desc",
): Promise<PageResponse<PrecioInfladoDTO>> => {
	const params = new URLSearchParams({
		page: page.toString(),
		size: size.toString(),
		sort,
	});
	if (search) params.append("search", search);

	const res = await fetchAPI(`${API_URL}?${params.toString()}`);
	if (!res.ok) throw new Error("Error al obtener precios inflados");
	return await res.json();
};

export const createPrecioInfladoAPI = async (data: Partial<PrecioInfladoDTO>) => {
	const res = await fetchAPI(API_URL, {
		method: "POST",
		headers: { "Content-Type": "application/json", "X-Audit-Origin": "FORM" },
		body: JSON.stringify(data),
	});
	if (!res.ok) throw new Error("Error al crear escenario");
	return await res.json();
};

export const updatePrecioInfladoAPI = async (id: number, data: Partial<PrecioInfladoDTO>) => {
	const res = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: { "Content-Type": "application/json", "X-Audit-Origin": "INLINE" },
		body: JSON.stringify(data),
	});
	if (!res.ok) throw new Error("Error al actualizar escenario");
	return await res.json();
};

export const deletePrecioInfladoAPI = async (id: number) => {
	const res = await fetchAPI(`${API_URL}/${id}`, {
		method: "DELETE",
		headers: { "X-Audit-Origin": "TABLE" },
	});
	if (!res.ok) throw new Error("Error al eliminar escenario");
	return true;
};

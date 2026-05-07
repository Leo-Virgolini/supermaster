import { CanalConceptoReglaDTO, CanalConceptoReglaPatchDTO, CanalConceptoReglaUpsertDTO, PageResponse } from "./types";
import { searchCanalesAPI } from "../canales/canalesService";
import { searchConceptosAPI } from "../conceptos-gastos/conceptosGastosService";
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";

const API_URL = `${API_BASE_URL}/api/canal-concepto-reglas`;

export const getReglasAPI = async (
	page: number,
	size: number,
	search: string = "",
	filters: Record<string, unknown> = {},
	sort = "id,desc",
): Promise<PageResponse<CanalConceptoReglaDTO>> => {
	const params = new URLSearchParams({
		page: page.toString(),
		size: size.toString(),
		sort,
	});
	if (search) params.append("search", search);

	Object.entries(filters).forEach(([k, v]) => {
		if (v) params.append(k, String(v));
	});

	const res = await fetchAPI(`${API_URL}?${params.toString()}`);
	if (!res.ok) throw new Error("Error al obtener reglas");
	return await res.json();
};

export const createReglaAPI = async (data: CanalConceptoReglaUpsertDTO) => {
	const res = await fetchAPI(API_URL, {
		method: "POST",
		headers: { "Content-Type": "application/json", "X-Audit-Origin": "FORM" },
		body: JSON.stringify(data),
	});
	if (!res.ok) throw new Error("Error al crear regla");
	return await res.json();
};

export const updateReglaAPI = async (id: number, data: CanalConceptoReglaPatchDTO) => {
	const res = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: { "Content-Type": "application/json", "X-Audit-Origin": "INLINE" },
		body: JSON.stringify(data),
	});
	if (!res.ok) throw new Error("Error al actualizar regla");
	return await res.json();
};

export const deleteReglaAPI = async (id: number) => {
	const res = await fetchAPI(`${API_URL}/${id}`, {
		method: "DELETE",
		headers: { "X-Audit-Origin": "TABLE" },
	});
	if (!res.ok) throw new Error("Error al eliminar regla");
	return true;
};

export { searchCanalesAPI, searchConceptosAPI };

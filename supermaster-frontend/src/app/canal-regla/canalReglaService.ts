import { CanalReglaDTO, CanalReglaPatchDTO, CanalReglaUpsertDTO, PageResponse } from "./types";
import { searchCanales } from "../productos/productosService";
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";

const API_URL = `${API_BASE_URL}/api/canal-reglas`;

export const getCanalReglasAPI = async (
	page: number,
	size: number,
	search: string = "",
	filters: Record<string, unknown> = {},
	sort = "id,desc",
): Promise<PageResponse<CanalReglaDTO>> => {
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
	if (!res.ok) throw new Error("Error al obtener reglas de canal");
	return await res.json();
};

export const createCanalReglaAPI = async (data: CanalReglaUpsertDTO) => {
	const res = await fetchAPI(API_URL, {
		method: "POST",
		headers: { "Content-Type": "application/json", "X-Audit-Origin": "FORM" },
		body: JSON.stringify(data),
	});
	if (!res.ok) throw new Error("Error al crear regla de canal");
	return await res.json();
};

export const updateCanalReglaAPI = async (id: number, data: CanalReglaPatchDTO) => {
	const res = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: { "Content-Type": "application/json", "X-Audit-Origin": "INLINE" },
		body: JSON.stringify(data),
	});
	if (!res.ok) throw new Error("Error al actualizar regla de canal");
	return await res.json();
};

export const deleteCanalReglaAPI = async (id: number) => {
	const res = await fetchAPI(`${API_URL}/${id}`, {
		method: "DELETE",
		headers: { "X-Audit-Origin": "TABLE" },
	});
	if (!res.ok) throw new Error("Error al eliminar regla de canal");
	return true;
};

// El endpoint de productos no está en los helpers genéricos (labelKey es "sku"+"descripcion"),
// así que lo resolvemos acá con un formato útil para el AsyncSelect.
export const searchProductosAPI = async (query: string): Promise<{ id: number; label: string }[]> => {
	try {
		const res = await fetchAPI(`${API_BASE_URL}/api/productos?page=0&size=15&search=${encodeURIComponent(query)}`);
		const json = await res.json();
		return (json.content || []).map((item: any) => ({
			id: item.id,
			label: `[${item.sku}] ${item.tituloDux || ""}`.trim(),
		}));
	} catch {
		return [];
	}
};

export { searchCanales };

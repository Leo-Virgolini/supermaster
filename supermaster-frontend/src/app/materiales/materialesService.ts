// 1. CONSTANTES
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { AuditOrigin, FilterValue, buildListParams, withAuditOrigin } from "../utils/apiHelpers";
import { MaterialDTO } from "./types";

const API_URL = `${API_BASE_URL}/api/materiales`;
type MaterialAuditOrigin = AuditOrigin;

// READ
export const getMaterialesAPI = async (
	page: number,
	size: number,
	filters: Record<string, FilterValue> = {},
	sort = "id,asc",
) => {
	const response = await fetchAPI(`${API_URL}?${buildListParams(page, size, sort, filters)}`);
	if (!response.ok) throw new Error("Error al conectar con el servidor");
	return await response.json();
};

// BUSCAR
export const searchMaterialesAPI = async (query: string) => {
	const searchParam = query ? `&search=${encodeURIComponent(query)}` : "";
	const params = `?page=0&size=10&sort=nombre,asc${searchParam}`; // sort por 'nombre'
	const response = await fetchAPI(API_URL + params);
	if (!response.ok) throw new Error("Error al buscar materiales");
	return await response.json();
};

// CREATE
export const createMaterialAPI = async (material: string, origin: MaterialAuditOrigin = "API") => {
	const response = await fetchAPI(API_URL, {
		method: "POST",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify({ nombre: material }),
	});
	if (!response.ok) throw new Error("Error al crear el material");
	return await response.json();
};

// DELETE
export const deleteMaterialAPI = async (id: number, origin: MaterialAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, { method: "DELETE", headers: withAuditOrigin(origin) });
	if (!response.ok) throw new Error("Error al eliminar el material");
	return true;
};

// UPDATE
export const updateMaterialAPI = async (
	id: number,
	data: Partial<Pick<MaterialDTO, "nombre">>,
	origin: MaterialAuditOrigin = "API",
) => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});
	if (!response.ok) throw new Error("Error al actualizar el material");
	return await response.json();
};

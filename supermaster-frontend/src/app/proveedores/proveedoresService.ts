import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { AuditOrigin, FilterValue, buildListParams, withAuditOrigin } from "../utils/apiHelpers";

const API_URL = `${API_BASE_URL}/api/proveedores`;
type ProveedorAuditOrigin = AuditOrigin;

export interface ProveedorDTO {
	id: number;
	nombre: string;
	apodo?: string | null;
	plazoPago?: string | null;
	entrega?: boolean | null;
	financiacionPorcentaje?: number | null;
	leadTimeDias?: number | null;
}

// GET
export const getProveedoresAPI = async (
	page: number,
	size: number,
	filters: Record<string, FilterValue> = {},
	sort = "id,desc",
) => {
	const response = await fetchAPI(`${API_URL}?${buildListParams(page, size, sort, filters)}`);
	if (!response.ok) throw new Error("Error al conectar");
	return await response.json();
};

// CREATE
export const createProveedorAPI = async (data: Omit<ProveedorDTO, "id">, origin: ProveedorAuditOrigin = "API") => {
	const response = await fetchAPI(API_URL, {
		method: "POST",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});
	if (!response.ok) throw new Error("Error al crear proveedor");
	return await response.json();
};

// UPDATE
export const updateProveedorAPI = async (id: number, data: Partial<Omit<ProveedorDTO, "id">>, origin: ProveedorAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});
	if (!response.ok) throw new Error("Error al actualizar");
	return await response.json();
};

// DELETE
export const deleteProveedorAPI = async (id: number, origin: ProveedorAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, { method: "DELETE", headers: withAuditOrigin(origin) });
	if (!response.ok) throw new Error("Error al eliminar");
	return true;
};

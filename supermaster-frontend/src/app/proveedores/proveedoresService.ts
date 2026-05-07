import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";

const API_URL = `${API_BASE_URL}/api/proveedores`;
type ProveedorAuditOrigin = "FORM" | "INLINE" | "TABLE" | "API";
const withAuditOrigin = (origin?: ProveedorAuditOrigin, headers?: HeadersInit): HeadersInit => ({
	...(headers as Record<string, string> ?? {}),
	...(origin ? { "X-Audit-Origin": origin } : {}),
});

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

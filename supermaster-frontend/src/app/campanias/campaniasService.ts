import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { AuditOrigin, FilterValue, buildListParams, withAuditOrigin } from "../utils/apiHelpers";

const API_URL = `${API_BASE_URL}/api/campanias`;

// READ: lista paginada de campañas
export const getCampaniasAPI = async (
	page: number,
	size: number,
	filters: Record<string, FilterValue> = {},
	sort = "id,desc",
) => {
	const response = await fetchAPI(`${API_URL}?${buildListParams(page, size, sort, filters)}`);
	if (!response.ok) throw new Error("Error al conectar");
	return await response.json();
};

// READ: una campaña por id
export const getCampaniaAPI = async (id: number) => {
	const response = await fetchAPI(`${API_URL}/${id}`);
	if (!response.ok) throw new Error("Error al conectar");
	return await response.json();
};

// READ: productos de una campaña (paginado)
export const getCampaniaProductosAPI = async (
	id: number,
	page: number,
	size: number,
	sort = "id,asc",
) => {
	const response = await fetchAPI(`${API_URL}/${id}/productos?${buildListParams(page, size, sort, {})}`);
	if (!response.ok) throw new Error("Error al conectar");
	return await response.json();
};

// UPDATE: vigencia / estado / observaciones de una campaña
export const updateCampaniaAPI = async (
	id: number,
	data: { fechaDesde?: string | null; fechaHasta?: string | null; activa?: boolean; observaciones?: string | null },
	origin: AuditOrigin = "API",
) => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});
	if (!response.ok) throw new Error("Error al actualizar la campaña");
	return await response.json();
};

// UPDATE: precio manual de un producto dentro de la campaña
export const updateCampaniaPrecioAPI = async (
	campaniaProductoId: number,
	precioManual: number | null,
	origin: AuditOrigin = "INLINE",
) => {
	const response = await fetchAPI(`${API_URL}/productos/${campaniaProductoId}/precio`, {
		method: "PATCH",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify({ precioManual }),
	});
	if (!response.ok) throw new Error("Error al actualizar el precio");
	return await response.json();
};

// ACTION: disparar la sincronización con Tienda Nube
export const sincronizarCampaniasAPI = async (origin: AuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/sincronizar`, {
		method: "POST",
		headers: withAuditOrigin(origin),
	});
	if (!response.ok) throw new Error("Error al sincronizar con Tienda Nube");
	return await response.json();
};

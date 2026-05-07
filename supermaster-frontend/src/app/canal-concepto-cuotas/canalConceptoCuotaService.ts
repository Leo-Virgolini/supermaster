import { CanalConceptoCuotaDTO, CanalConceptoCuotaPatchDTO, CanalConceptoCuotaUpsertDTO, PageResponse } from "./types";
import { searchCanalesAPI } from "../canales/canalesService";
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";

const API_URL = `${API_BASE_URL}/api/canal-concepto-cuotas`;
type CuotaAuditOrigin = "FORM" | "INLINE" | "TABLE" | "API";

const withAuditOrigin = (origin?: CuotaAuditOrigin, headers?: HeadersInit): HeadersInit => ({
	...(headers as Record<string, string> ?? {}),
	...(origin ? { "X-Audit-Origin": origin } : {}),
});

export const getCuotasAPI = async (
	page: number,
	size: number,
	search: string = "",
	filters: Record<string, any> = {},
	sort = "id,desc",
): Promise<PageResponse<CanalConceptoCuotaDTO>> => {
	const params = new URLSearchParams({
		page: page.toString(),
		size: size.toString(),
		sort,
	});

	if (search) params.append("search", search);

	Object.entries(filters).forEach(([key, value]) => {
		if (value !== undefined && value !== null && value !== "") {
			params.append(key, String(value));
		}
	});

	const response = await fetchAPI(`${API_URL}?${params.toString()}`);
	if (!response.ok) throw new Error("Error al obtener cuotas");
	const raw = await response.json();
	const content = (raw.content || []).map((item: any) => ({
		...item,
		canalNombre: item.canal?.nombre || item.canalNombre || undefined,
	}));
	return { ...raw, content };
};

export const getCuotasPorCanalAPI = async (canalId: number): Promise<CanalConceptoCuotaDTO[]> => {
	const response = await fetchAPI(`${API_URL}/canal/${canalId}`);
	if (!response.ok) throw new Error("Error al obtener cuotas del canal");
	return await response.json();
};

export const createCuotaAPI = async (data: CanalConceptoCuotaUpsertDTO, origin: CuotaAuditOrigin = "API") => {
	const response = await fetchAPI(API_URL, {
		method: "POST",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});
	if (!response.ok) throw new Error("Error al crear cuota");
	return await response.json();
};

export const updateCuotaAPI = async (id: number, data: CanalConceptoCuotaPatchDTO, origin: CuotaAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});
	if (!response.ok) throw new Error("Error al actualizar cuota");
	return await response.json();
};

export const deleteCuotaAPI = async (id: number, origin: CuotaAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, { method: "DELETE", headers: withAuditOrigin(origin) });
	if (!response.ok) throw new Error("Error al eliminar cuota");
	return true;
};

export { searchCanalesAPI };

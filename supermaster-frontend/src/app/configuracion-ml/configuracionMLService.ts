import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { ConfiguracionMlDTO } from "./types";

const API_URL = `${API_BASE_URL}/api/ml/configuracion`;
export type ConfiguracionMlAuditOrigin = "FORM" | "API";

const withAuditOrigin = (origin: ConfiguracionMlAuditOrigin) => ({
	"Content-Type": "application/json",
	"X-Audit-Origin": origin,
});

export const getConfigMLAPI = async (): Promise<ConfiguracionMlDTO> => {
	const res = await fetchAPI(API_URL);
	if (!res.ok) throw new Error("Error al obtener configuración ML");
	return await res.json();
};

export const updateConfigMLAPI = async (
	dto: ConfiguracionMlDTO,
	origin: ConfiguracionMlAuditOrigin = "API",
): Promise<ConfiguracionMlDTO> => {
	const res = await fetchAPI(API_URL, {
		method: "PUT",
		headers: withAuditOrigin(origin),
		body: JSON.stringify(dto),
	});
	if (!res.ok) throw new Error("Error al actualizar configuración ML");
	return await res.json();
};

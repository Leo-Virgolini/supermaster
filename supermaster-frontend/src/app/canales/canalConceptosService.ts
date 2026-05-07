import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";

export type CanalConceptoAuditOrigin = "FORM" | "INLINE" | "TABLE" | "API";

const withAuditOrigin = (
	origin?: CanalConceptoAuditOrigin,
	headers?: HeadersInit,
): HeadersInit => ({
	...(headers ?? {}),
	...(origin ? { "X-Audit-Origin": origin } : {}),
});

export interface CanalConceptoDTO {
	canalId: number;
	conceptoId: number;
	nombre: string;
	porcentaje: number;
	aplicaSobre: string;
	etapa: string; // 'COSTO' | 'MARGEN' | 'IMPUESTOS' | 'PRECIO' | 'POST_PRECIO' — derivado por backend desde aplicaSobre
	naturaleza: string; // 'COSTO_VENTA' | 'INFLACION' | 'IMPUESTO' | etc. — override del concepto o default del aplicaSobre
	descripcion: string | null;
}

// GET all conceptos assigned to a canal
export const getConceptosPorCanalAPI = async (
	canalId: number,
): Promise<CanalConceptoDTO[]> => {
	const response = await fetchAPI(
		`${API_BASE_URL}/api/canales/${canalId}/conceptos`,
	);
	if (!response.ok) throw new Error("Error al obtener los conceptos del canal");
	return await response.json();
};

// POST assign a concepto to a canal
export const asignarConceptoAPI = async (
	canalId: number,
	conceptoId: number,
	origin: CanalConceptoAuditOrigin = "API",
): Promise<CanalConceptoDTO> => {
	const response = await fetchAPI(
		`${API_BASE_URL}/api/canales/${canalId}/conceptos/${conceptoId}`,
		{ method: "POST", headers: withAuditOrigin(origin) },
	);
	if (!response.ok) throw new Error("Error al asignar el concepto al canal");
	return await response.json();
};

// DELETE remove a concepto from a canal
export const eliminarConceptoDelCanalAPI = async (
	canalId: number,
	conceptoId: number,
	origin: CanalConceptoAuditOrigin = "API",
): Promise<void> => {
	const response = await fetchAPI(
		`${API_BASE_URL}/api/canales/${canalId}/conceptos/${conceptoId}`,
		{ method: "DELETE", headers: withAuditOrigin(origin) },
	);
	if (!response.ok) throw new Error("Error al eliminar el concepto del canal");
};

// POST clone conceptos + reglas de otro canal hacia el canal destino
export const clonarConceptosDeCanalAPI = async (
	targetCanalId: number,
	srcCanalId: number,
	origin: CanalConceptoAuditOrigin = "API",
): Promise<{ copiadas: number }> => {
	const response = await fetchAPI(
		`${API_BASE_URL}/api/canales/${targetCanalId}/conceptos/clonar-de/${srcCanalId}`,
		{ method: "POST", headers: withAuditOrigin(origin) },
	);
	if (!response.ok) throw new Error("Error al clonar conceptos del canal");
	return await response.json();
};

export interface ConceptoCalculoOption {
	id: number;
	label: string;
	aplicaSobre: string;
	etapa: string;
	naturaleza: string;
	porcentaje: number | null;
}

// Search conceptos-calculo for the select dropdown
export const searchConceptosCalculoAPI = async (
	query: string,
): Promise<ConceptoCalculoOption[]> => {
	const searchParam = query ? `&search=${encodeURIComponent(query)}` : "";
	const response = await fetchAPI(
		`${API_BASE_URL}/api/conceptos-calculo?page=0&size=20${searchParam}`,
	);
	if (!response.ok) throw new Error("Error al buscar conceptos de cálculo");
	const json = await response.json();
	const content = json.content || [];
	return content.map((item: any) => ({
		id: item.id,
		label: item.nombre,
		aplicaSobre: item.aplicaSobre,
		etapa: item.etapa,
		naturaleza: item.naturaleza,
		porcentaje: item.porcentaje ?? null,
	}));
};

// Load all conceptos-calculo (for dropdown on mount)
export const getAllConceptosCalculoAPI = async (): Promise<ConceptoCalculoOption[]> => {
	const response = await fetchAPI(
		`${API_BASE_URL}/api/conceptos-calculo?page=0&size=100`,
	);
	if (!response.ok) throw new Error("Error al cargar conceptos de cálculo");
	const json = await response.json();
	const content = json.content || [];
	return content.map((item: any) => ({
		id: item.id,
		label: item.nombre,
		aplicaSobre: item.aplicaSobre,
		etapa: item.etapa,
		naturaleza: item.naturaleza,
		porcentaje: item.porcentaje ?? null,
	}));
};

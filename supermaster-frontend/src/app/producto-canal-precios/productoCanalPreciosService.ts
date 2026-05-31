import { FormulaCalculo, PageResponse, ProductoCanalPrecioDTO, RecalculoMasivoResultDTO } from "./types";
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";

const API_URL = `${API_BASE_URL}/api/precios`;

export const getProductoCanalPreciosAPI = async (
	page: number,
	size: number,
	search: string = "",
	filters: Record<string, any> = {},
	sort: string[] = ["id,desc"],
): Promise<PageResponse<ProductoCanalPrecioDTO>> => {
	const params = new URLSearchParams({
		page: page.toString(),
		size: size.toString(),
	});
	for (const s of sort) {
		params.append("sort", s);
	}

	if (search) {
		params.append("search", search);
	}

	// Lógica para procesar filtros de columna (si los hubiera)
	Object.entries(filters).forEach(([key, value]) => {
		if (value !== undefined && value !== null && value !== "") {
			params.append(key, String(value));
		}
	});

	const response = await fetchAPI(`${API_URL}?${params.toString()}`);

	if (!response.ok) {
		throw new Error("Error al obtener el monitor de precios");
	}

	return await response.json();
};

export const calcularPreciosAPI = async (productoId: number): Promise<void> => {
	const response = await fetchAPI(`${API_URL}/calcular?productoId=${productoId}`, { method: "POST" });
	if (!response.ok) throw new Error("Error al calcular precios");
};

export const recalcularTodosAPI = async (): Promise<RecalculoMasivoResultDTO> => {
	const response = await fetchAPI(`${API_URL}/calcular`, { method: "POST" });
	if (!response.ok) throw new Error("Error al recalcular precios");
	return await response.json();
};

/**
 * Devuelve el resultado del último recálculo masivo (sincrónico o async),
 * con la lista detallada de SKUs problemáticos:
 *  - skusConErrores, skusSinCosto, skusSinMargen (combinado),
 *    skusSinMargenMayorista y skusSinMargenMinorista (desglose por tipo requerido).
 * Devuelve null si el endpoint responde 204 (no hay resultado guardado todavía).
 */
export const getResultadoRecalculoMasivoAPI = async (): Promise<RecalculoMasivoResultDTO | null> => {
	const response = await fetchAPI(`${API_URL}/recalculo-masivo/resultado`, { allowedStatuses: [204] });
	if (response.status === 204) return null;
	if (!response.ok) throw new Error("Error al obtener resultado del recálculo masivo");
	return await response.json();
};

export const getProductoPreciosAPI = async (productoId: number): Promise<ProductoCanalPrecioDTO | null> => {
	const response = await fetchAPI(`${API_URL}?productoId=${productoId}&page=0&size=1`, { allowedStatuses: [404] });
	if (response.status === 404) return null;
	if (!response.ok) throw new Error("Error al obtener precios");
	const data = await response.json();
	return data.content?.[0] ?? null;
};

export const getFormulaAPI = async (
	productoId: number,
	canalId: number,
	cuotas: number,
): Promise<FormulaCalculo> => {
	const response = await fetchAPI(
		`${API_URL}/formula?productoId=${productoId}&canalId=${canalId}&cuotas=${cuotas}`,
	);
	if (!response.ok) throw new Error("Error al obtener la fórmula");
	return await response.json();
};

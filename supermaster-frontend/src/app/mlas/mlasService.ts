import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";

const API_URL = `${API_BASE_URL}/api/mlas`;

export interface MlaDTO {
	id: number;
	mla: string;
	mlau?: string | null;
	precioEnvio?: number | null;
	comisionPorcentaje?: number | null;
	fechaCalculoEnvio?: string | null;
	fechaCalculoComision?: string | null;
	topePromocion?: number | null;
}

// GET (Paginado)
export const getMlasAPI = async (
	page: number,
	size: number,
	filters: Record<string, unknown> = {},
	sort = "id,asc",
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
	if (!response.ok) throw new Error("Error al conectar con el servidor");
	return await response.json();
};

// GET by id (para refrescar una sola fila sin recargar la tabla entera)
export const getMlaByIdAPI = async (id: number): Promise<MlaDTO> => {
	const response = await fetchAPI(`${API_URL}/${id}`);
	if (!response.ok) throw new Error("Error al obtener MLA");
	return response.json();
};

// CREATE
export const createMlaAPI = async (data: Omit<MlaDTO, "id" | "fechaCalculoEnvio" | "fechaCalculoComision">) => {
	const response = await fetchAPI(API_URL, {
		method: "POST",
		headers: { "Content-Type": "application/json", "X-Audit-Origin": "FORM" },
		body: JSON.stringify(data),
	});
	if (!response.ok) throw new Error("Error al crear MLA");
	return await response.json();
};

// UPDATE
export const updateMlaAPI = async (id: number, data: Partial<Omit<MlaDTO, "id" | "fechaCalculoEnvio" | "fechaCalculoComision">>) => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: { "Content-Type": "application/json", "X-Audit-Origin": "INLINE" },
		body: JSON.stringify(data),
	});
	if (!response.ok) throw new Error("Error al actualizar MLA");
	return await response.json();
};

// DELETE
export const deleteMlaAPI = async (id: number) => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "DELETE",
		headers: { "X-Audit-Origin": "TABLE" },
	});
	if (!response.ok) throw new Error("Error al eliminar MLA");
	return true;
};

export interface CostoEnvioResponseDTO {
	mla: string;
	status: string;
	precioConsultado: number;
	costoEnvioConIva: number;
	costoEnvioSinIva: number;
	mensaje: string;
}

export interface CostoVentaResponseDTO {
	mla: string;
	status: string;
	precioConsultado: number;
	comisionVentaTotal: number;
	costoFijo: number;
	cargoFinanciacion: number;
	porcentajeMeli: number;
	porcentajeTotal: number;
	listingTypeId: string;
	listingTypeName: string;
	mensaje: string;
}

// CALCULAR COSTO ENVÍO individual (sincrónico — POST /api/ml/costo-envio?mla=xxx)
export const calcularCostoEnvioMlaAPI = async (mla: string): Promise<CostoEnvioResponseDTO> => {
	const response = await fetchAPI(`${API_BASE_URL}/api/ml/costo-envio?mla=${encodeURIComponent(mla)}`, { method: "POST" });
	if (!response.ok) throw new Error("Error al calcular costo de envío");
	return response.json();
};

// CALCULAR COSTO VENTA / COMISIÓN individual (sincrónico — POST /api/ml/costo-venta?mla=xxx)
export const calcularCostoVentaMlaAPI = async (mla: string): Promise<CostoVentaResponseDTO> => {
	const response = await fetchAPI(`${API_BASE_URL}/api/ml/costo-venta?mla=${encodeURIComponent(mla)}`, { method: "POST" });
	if (!response.ok) throw new Error("Error al calcular comisión");
	return response.json();
};

// Productos asociados a un MLA (GET /api/productos?mlaId=X)
export interface ProductoResumenDTO {
	id: number;
	sku: string;
	descripcion: string;
	costo: number | null;
	activo: boolean;
}

export const getProductosPorMlaAPI = async (mlaId: number): Promise<ProductoResumenDTO[]> => {
	const response = await fetchAPI(`${API_BASE_URL}/api/productos?mlaIds=${mlaId}&page=0&size=500&sort=sku,asc`);
	if (!response.ok) throw new Error("Error al cargar productos del MLA");
	const data = await response.json();
	return data.content ?? data;
};

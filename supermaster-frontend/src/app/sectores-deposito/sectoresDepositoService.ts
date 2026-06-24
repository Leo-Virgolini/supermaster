// 1. CONSTANTES
// Definimos la URL base acá para no repetirla. Si cambia, se cambia solo acá.
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { AuditOrigin, FilterValue, buildListParams, withAuditOrigin } from "../utils/apiHelpers";
import { SectorDepositoCreateDTO, SectorDepositoPatchDTO } from "./types";

const API_URL = `${API_BASE_URL}/api/sectores-deposito`;
type SectorDepositoAuditOrigin = AuditOrigin;

// READ: Traer la lista paginada
export const getSectoresDepositoAPI = async (
	page: number,
	size: number,
	filters: Record<string, FilterValue> = {},
	sort = "id,asc",
) => {
	const response = await fetchAPI(`${API_URL}?${buildListParams(page, size, sort, filters)}`);

	if (!response.ok) throw new Error("Error al conectar con el servidor");
	return await response.json();
};

// BUSCAR: Filtra por texto (Select)
export const searchSectoresDepositoAPI = async (query: string) => {
	// encodeURIComponent es clave por si buscan con espacios o caracteres especiales
	const searchParam = query ? `&search=${encodeURIComponent(query)}` : "";

	// Pedimos página 0 y solo 10 resultados (no necesitamos 1000 para un dropdown)
	const params = `?page=0&size=10&sort=codigo,asc${searchParam}`;

	const response = await fetchAPI(API_URL + params);

	if (!response.ok) {
		throw new Error("Error al buscar sectores de depósito");
	}
	return await response.json();
};

// CREATE: Crear un nuevo sector de depósito
export const createSectorDepositoAPI = async (
	data: SectorDepositoCreateDTO,
	origin: SectorDepositoAuditOrigin = "API",
) => {
	const response = await fetchAPI(API_URL, {
		method: "POST",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});

	if (!response.ok) throw new Error("Error al crear el sector de depósito");
	return await response.json();
};

// DELETE: Borrar un sector de depósito por ID
export const deleteSectorDepositoAPI = async (id: number, origin: SectorDepositoAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "DELETE",
		headers: withAuditOrigin(origin),
	});

	if (!response.ok) throw new Error("Error al eliminar el sector de depósito");
	return true;
};

// UPDATE: Editar un sector de depósito existente
export const updateSectorDepositoAPI = async (
	id: number,
	data: SectorDepositoPatchDTO,
	origin: SectorDepositoAuditOrigin = "API",
) => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(data),
	});

	if (!response.ok) throw new Error("Error al actualizar el sector de depósito");
	return await response.json();
};

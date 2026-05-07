import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import type { AuditoriaCambioDTO } from "./types";

const API_URL = `${API_BASE_URL}/api/auditorias`;

export const getAuditoriaGlobalAPI = async ({
	page = 0,
	size = 50,
	sort = "fechaHora,desc",
	search,
	entidad,
	accion,
	campo,
	origen,
	usuario,
	entidadId,
	fechaDesde,
	fechaHasta,
}: {
	page?: number;
	size?: number;
	sort?: string | string[];
	search?: string;
	entidad?: string;
	accion?: string;
	campo?: string;
	origen?: string;
	usuario?: string;
	entidadId?: number | null;
	fechaDesde?: string;
	fechaHasta?: string;
}) => {
	const params = new URLSearchParams({
		page: String(page),
		size: String(size),
	});

	const sortArray = Array.isArray(sort) ? sort : [sort];
	sortArray.forEach((item) => params.append("sort", item));

	if (search?.trim()) params.append("search", search.trim());
	if (entidad?.trim()) params.append("entidad", entidad.trim());
	if (accion?.trim()) params.append("accion", accion.trim());
	if (campo?.trim()) params.append("campo", campo.trim());
	if (origen?.trim()) params.append("origen", origen.trim());
	if (usuario?.trim()) params.append("usuario", usuario.trim());
	if (entidadId != null) params.append("entidadId", String(entidadId));
	if (fechaDesde?.trim()) params.append("fechaDesde", fechaDesde.trim());
	if (fechaHasta?.trim()) params.append("fechaHasta", fechaHasta.trim());

	const res = await fetchAPI(`${API_URL}?${params.toString()}`);
	if (!res.ok) throw new Error("Error al cargar auditoría global");
	return await res.json() as { content: AuditoriaCambioDTO[]; page?: { totalElements?: number } };
};

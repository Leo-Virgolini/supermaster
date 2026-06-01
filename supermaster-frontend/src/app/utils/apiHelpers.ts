/**
 * Helpers compartidos por los *Service.ts de cada entidad.
 *
 * Centralizan los bloques que estaban duplicados byte a byte en ~15 servicios CRUD
 * (armado de query params de listado y header de auditoría), sin cambiar la API
 * pública de cada servicio: las funciones siguen llamándose y comportándose igual.
 */

/** Origen de la acción, para la auditoría del backend (header X-Audit-Origin). */
export type AuditOrigin = "FORM" | "INLINE" | "TABLE" | "API";

/** Agrega el header X-Audit-Origin a los headers existentes (si se especifica origen). */
export const withAuditOrigin = (origin?: AuditOrigin, headers?: HeadersInit): HeadersInit => ({
	...((headers as Record<string, string>) ?? {}),
	...(origin ? { "X-Audit-Origin": origin } : {}),
});

/** Valor admitido en un filtro de listado. */
export type FilterValue = string | number | boolean | string[] | null | undefined;

/**
 * Arma el query string de un listado paginado: page, size, sort y los filtros.
 * Ignora filtros vacíos (undefined, null, ""); los arrays se unen con coma.
 */
export const buildListParams = (
	page: number,
	size: number,
	sort: string,
	filters: Record<string, FilterValue> = {},
): string => {
	const params = new URLSearchParams({
		page: page.toString(),
		size: size.toString(),
		sort,
	});

	Object.entries(filters).forEach(([key, value]) => {
		if (value !== undefined && value !== null && value !== "") {
			params.append(key, Array.isArray(value) ? value.join(",") : String(value));
		}
	});

	return params.toString();
};

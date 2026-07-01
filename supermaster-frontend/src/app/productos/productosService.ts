import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import type { AuditoriaCambioDTO } from "../auditoria/types";
import { ProductoCreateDTO, ProductoPatchDTO } from "./types";

const API_URL = `${API_BASE_URL}/api/productos`;
type ProductoAuditOrigin = "FORM" | "INLINE" | "TABLE" | "MONITOR_PRECIOS" | "API";

const withAuditOrigin = (origin?: ProductoAuditOrigin, headers?: HeadersInit): HeadersInit => ({
	...(headers as Record<string, string> ?? {}),
	...(origin ? { "X-Audit-Origin": origin } : {}),
});

// Cache de lookups de relaciones (ID → nombre)
type RelationLookups = {
	marcas: Record<number, string>;
	tipos: Record<number, string>;
	clasifGral: Record<number, string>;
	clasifGastro: Record<number, string>;
	proveedores: Record<number, string>;
	origenes: Record<number, string>;
	materiales: Record<number, string>;
	mlas: Record<number, string>;
};

type LookupItem = Record<string, unknown> & {
	id: number;
	nombre?: string;
	nombreCompleto?: string;
	mla?: string;
};

type SearchOption = {
	id: number;
	/** Texto a mostrar en el dropdown. Para entidades jerárquicas es el path completo "A > B > C". */
	label: string;
	/** Nombre corto (solo el hijo), opcional. Usado en la celda después de elegir. */
	nombreCorto?: string;
};

let _lookupsCache: RelationLookups | null = null;
let _lookupsCacheTime = 0;
const CACHE_TTL = 5 * 60 * 1000; // 5 minutos

async function fetchLookup(endpoint: string, labelKey: string): Promise<Record<number, string>> {
	try {
		const res = await fetchAPI(`${API_BASE_URL}/api/${endpoint}?page=0&size=9999&sort=id,asc`);
		if (!res.ok) return {};
		const json = await res.json();
		const map: Record<number, string> = {};
		(json.content as LookupItem[] | undefined || []).forEach((item) => {
			map[item.id] = String(
				item[labelKey] ??
				item.nombre ??
				item.mla ??
				"",
			);
		});
		return map;
	} catch { return {}; }
}

async function loadRelationLookups(): Promise<RelationLookups> {
	if (_lookupsCache && Date.now() - _lookupsCacheTime < CACHE_TTL) {
		return _lookupsCache;
	}
	const [marcas, tipos, clasifGral, clasifGastro, proveedores, origenes, materiales, mlas] = await Promise.all([
		fetchLookup("marcas", "nombre"),
		fetchLookup("tipos", "nombre"),
		fetchLookup("clasif-gral", "nombre"),
		fetchLookup("clasif-gastro", "nombre"),
		fetchLookup("proveedores", "nombre"),
		fetchLookup("origenes", "nombre"),
		fetchLookup("materiales", "nombre"),
		fetchLookup("mlas", "mla"),
	]);
	_lookupsCache = { marcas, tipos, clasifGral, clasifGastro, proveedores, origenes, materiales, mlas };
	_lookupsCacheTime = Date.now();
	return _lookupsCache;
}

const cleanPayload = <T extends object>(data: T): T => {
	const clean = { ...(data as Record<string, unknown>) };

	// Borramos nombres visuales
	delete clean.marcaNombre;
	delete clean.origenNombre;
	delete clean.clasifGralNombre;
	delete clean.clasifGastroNombre;
	delete clean.tipoNombre;
	delete clean.proveedorNombre;
	delete clean.materialNombre;
	delete clean.mlaNombre;
	delete clean.marcaNombreCompleto;
	delete clean.tipoNombreCompleto;
	delete clean.clasifGralNombreCompleto;
	delete clean.clasifGastroNombreCompleto;
	delete clean.fechaCreacion;
	delete clean.fechaModificacion;
	delete clean.fechaUltimoCosto;

	// Borramos objetos anidados para evitar conflictos en el backend
	delete clean.marca;
	delete clean.rubro;
	delete clean.clasifGral;
	delete clean.clasifGastro;
	delete clean.tipo;
	delete clean.proveedor;
	delete clean.origen;
	delete clean.material;
	delete clean.mla;

	return clean as T;
};

// GET PRODUCTOS
export const getProductosAPI = async (
	page: number,
	size: number,
	filters: Record<string, unknown> = {},
	sort: string | string[] = "id,asc",
	signal?: AbortSignal,
) => {
	const params = new URLSearchParams({
		page: page.toString(),
		size: size.toString(),
	});

	const sortArray = Array.isArray(sort) ? sort : [sort];
	sortArray.forEach((s) => params.append("sort", s));

	Object.entries(filters).forEach(([key, value]) => {
		if (value !== undefined && value !== null && value !== "") {
			if (Array.isArray(value)) {
				params.append(key, value.join(","));
			} else {
				params.append(key, String(value));
			}
		}
	});

	const response = await fetchAPI(`${API_URL}?${params.toString()}`, { signal });
	if (!response.ok) throw new Error("Error al conectar");

	return await response.json();
};

// GET PRODUCTOS CON NOMBRES RESUELTOS (para export)
export const getProductosForExportAPI = async (page: number, size: number, filters: Record<string, unknown> = {}, sort: string | string[] = "id,asc") => {
	const json = await getProductosAPI(page, size, filters, sort);
	const lookups = await loadRelationLookups();

	const content = json.content.map((p: Record<string, unknown>) => ({
		...p,
		marcaNombre: lookups.marcas[Number(p.marcaId)] || "---",
		clasifGralNombre: lookups.clasifGral[Number(p.clasifGralId)] || "---",
		clasifGastroNombre: lookups.clasifGastro[Number(p.clasifGastroId)] || "---",
		tipoNombre: lookups.tipos[Number(p.tipoId)] || "---",
		proveedorNombre: lookups.proveedores[Number(p.proveedorId)] || "---",
		origenNombre: lookups.origenes[Number(p.origenId)] || "---",
		materialNombre: lookups.materiales[Number(p.materialId)] || "---",
		mlaNombre: lookups.mlas[Number(p.mlaId)] || "---",
	}));

	return { ...json, content };
};
export type MlaResumenDTO = {
	id: number;
	mla: string;
	mlau: string | null;
	precioEnvio: number | null;
	comisionPorcentaje: number | null;
	topePromocion: number | null;
};

// Detalle completo de un MLA (incluye fechas de cálculo de envío/comisión).
export type MlaDetalleDTO = MlaResumenDTO & {
	fechaCalculoEnvio: string | null;
	fechaCalculoComision: string | null;
};

// Trae el detalle de un MLA por id (para mostrar MLAU, envío y comisión al editar).
export const getMlaPorIdAPI = async (id: number): Promise<MlaDetalleDTO> => {
	const res = await fetchAPI(`${API_BASE_URL}/api/mlas/${id}`);
	if (!res.ok) throw new Error(await extraerMensajeError(res, "No se pudo obtener el MLA"));
	return await res.json();
};

// PATCH parcial de un MLA existente (persistir ediciones de MLAU/envío/comisión/tope).
export const patchMlaAPI = async (id: number, data: { mlau?: string | null; precioEnvio?: number | null; comisionPorcentaje?: number | null; topePromocion?: number | null }): Promise<MlaDetalleDTO> => {
	const res = await fetchAPI(`${API_BASE_URL}/api/mlas/${id}`, {
		method: "PATCH",
		headers: { "Content-Type": "application/json", "X-Audit-Origin": "FORM" },
		body: JSON.stringify(data),
	});
	if (!res.ok) throw new Error(await extraerMensajeError(res, "No se pudo actualizar el MLA"));
	return await res.json();
};

const extraerMensajeError = async (res: Response, fallback: string): Promise<string> => {
	try {
		const j = await res.json();
		if (j?.message) return j.message as string;
	} catch { /* sin body JSON */ }
	return fallback;
};

// Busca en MercadoLibre la publicación del SKU, crea/asegura el MLA y le calcula
// envío + comisión. Devuelve el MLA resultante (ya persistido).
export const getMlaPorSkuAPI = async (sku: string): Promise<MlaResumenDTO> => {
	const res = await fetchAPI(`${API_BASE_URL}/api/mlas/por-sku-ml?sku=${encodeURIComponent(sku)}`);
	if (!res.ok) throw new Error(await extraerMensajeError(res, "No se pudo obtener el MLA desde MercadoLibre"));
	return await res.json();
};

// Trae el MLA desde MercadoLibre por su código (activa o pausada), crea/asegura el MLA y
// le calcula envío + comisión. Devuelve el MLA y si la publicación es de catálogo (para avisar).
export const getMlaPorCodigoAPI = async (mla: string): Promise<{ mla: MlaResumenDTO; esCatalogo: boolean }> => {
	const res = await fetchAPI(`${API_BASE_URL}/api/mlas/por-mla-ml?mla=${encodeURIComponent(mla)}`);
	if (!res.ok) throw new Error(await extraerMensajeError(res, "No se pudo obtener el MLA desde MercadoLibre"));
	return await res.json();
};

// Crea un MLA manualmente con los datos cargados en el alta de producto.
export const createMlaAPI = async (data: { mla: string; mlau?: string | null; precioEnvio?: number | null; comisionPorcentaje?: number | null; topePromocion?: number | null }): Promise<MlaResumenDTO> => {
	const res = await fetchAPI(`${API_BASE_URL}/api/mlas`, {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify(data),
	});
	if (!res.ok) throw new Error(await extraerMensajeError(res, "No se pudo crear el MLA"));
	return await res.json();
};

// Dispara en ML el cálculo del precio de envío del MLA del producto. Requiere que el
// producto YA esté asociado al MLA (por eso se llama después de crear el producto):
// el cálculo de envío necesita el PVP del producto.
export const calcularEnvioMlaAPI = async (productoId: number): Promise<void> => {
	const res = await fetchAPI(`${API_BASE_URL}/api/ml/costo-envio?productoId=${productoId}`, { method: "POST" });
	if (!res.ok) throw new Error(await extraerMensajeError(res, "No se pudo calcular el envío del MLA"));
};

// Sube (exporta) productos a Dux por SKU. El backend mapea los datos del producto
// a los campos del ítem de Dux (cod_item, descripción, costo, IVA, combo, etc.).
// Devuelve el mismo DTO que Nube/ML (ExportCanalResultDTO).
export const exportarProductosADuxAPI = async (skus: string[], habilitado?: "S" | "N"): Promise<ExportCanalResultDTO> => {
	const res = await fetchAPI(`${API_BASE_URL}/api/dux/exportar-productos/confirmar`, {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ skus, habilitado }),
	});
	if (!res.ok) throw new Error(await extraerMensajeError(res, "No se pudo subir el producto a Dux"));
	return await res.json();
};

export type SeoNube = { seoTitle: string; seoDescription: string; seoTags: string };

export type DestinoNube = { tienda: "KT HOGAR" | "KT GASTRO"; cuotas: number; seo?: SeoNube; descripcion?: string | null };

export type SeoContexto = {
	tituloNube: string;
	marca: string | null;
	material: string | null;
	aptos: string[];
	dimensiones: string[];
};

// Genera con IA los campos SEO de Tienda Nube (title, description, tags) a partir
// del contexto del producto. El canal define el tono/segmento (GASTRO vs HOGAR).
export const generarSeoAPI = async (canal: "GASTRO" | "HOGAR", contexto: SeoContexto): Promise<SeoNube> => {
	const res = await fetchAPI(`${API_BASE_URL}/api/productos/generar-seo`, {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ canal, contexto }),
	});
	if (!res.ok) throw new Error(await extraerMensajeError(res, "No se pudo generar el SEO con IA"));
	return await res.json();
};

// Resultado unificado de exportar/sincronizar a un canal (Nube/ML). Refleja el ExportCanalResultDTO del backend.
export type ExportCanalResultDTO = {
	creados: number;
	actualizados: string[];
	yaExistian: string[];
	errores: string[];
	advertencias: string[];
};

export const exportarProductosANubeAPI = async (
	skus: string[], tiendas: DestinoNube[],
	dims?: { nubePeso: string; nubeProfundidad: string; nubeAncho: string; nubeAlto: string },
): Promise<ExportCanalResultDTO> => {
	const res = await fetchAPI(`${API_BASE_URL}/api/nube/exportar-productos`, {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ skus, tiendas, ...dims }),
	});
	if (!res.ok) throw new Error(await extraerMensajeError(res, "No se pudo subir el producto a Tienda Nube"));
	return await res.json();
};

/**
 * Recalcula SÍNCRONAMENTE el precio del producto en todos los canales (todas las cuotas).
 * Se usa antes de exportar a Tienda Nube para garantizar que el PVP esté calculado y fresco,
 * cerrando el race con los recálculos asíncronos (alta/margen/precio inflado/envío MLA).
 */
export const recalcularProductoAPI = async (productoId: number): Promise<void> => {
	const res = await fetchAPI(`${API_BASE_URL}/api/precios/calcular?productoId=${productoId}`, {
		method: "POST",
	});
	if (!res.ok) throw new Error(await extraerMensajeError(res, "No se pudo recalcular el precio del producto"));
};

export const exportarProductosAMlAPI = async (
	skus: string[], cuotas: number,
	mlCategoryId?: string | null,
	mlAtributos?: ProductoMlAtributo[],
	descripcionMl?: string | null,
): Promise<ExportCanalResultDTO> => {
	const res = await fetchAPI(`${API_BASE_URL}/api/ml/exportar-productos`, {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ skus, cuotas, mlCategoryId, mlAtributos, descripcionMl }),
	});
	if (!res.ok) throw new Error(await extraerMensajeError(res, "No se pudo subir el producto a Mercado Libre"));
	return await res.json();
};

export type MlAtributoValor = { id: string; name: string; rgb: string | null };
export type MlAtributoDef = {
	id: string; name: string; valueType: "string" | "number" | "number_unit" | "boolean" | "list";
	values: MlAtributoValor[]; allowedUnits: string[]; defaultUnit: string | null;
	required: boolean; conditional: boolean; multivalued: boolean;
	relevance: number; valueMaxLength: number | null; example: string | null; hint: string | null;
};
export type ProductoMlAtributo = { attributeId: string; valueId: string | null; valueName: string; noAplica: boolean };

// Ficha técnica estructurada (technical_specs/input): secciones → componentes → atributos.
export type MlComponente = {
	tipo: string; label: string;
	hint: string | null; tooltip: string | null; example: string | null;
	allowCustomValue: boolean; allowFiltering: boolean;
	atributos: MlAtributoDef[];
};
export type MlSeccion = { id: "VARIANTE" | "PRINCIPALES" | "SECUNDARIAS"; label: string; componentes: MlComponente[] };
export type MlFicha = { secciones: MlSeccion[] };

export const getMlCategoriaFichaAPI = async (categoryId: string): Promise<MlFicha> => {
	const res = await fetchAPI(`${API_BASE_URL}/api/ml/categorias/${encodeURIComponent(categoryId)}/ficha`);
	if (!res.ok) throw new Error("No se pudo obtener la ficha técnica de la categoría");
	return await res.json();
};

export const getMlCategoriaMaxTitleAPI = async (categoryId: string): Promise<number> => {
	try {
		const res = await fetchAPI(`${API_BASE_URL}/api/ml/categorias/${encodeURIComponent(categoryId)}/max-title-length`);
		if (!res.ok) return 60;
		const json = await res.json();
		return typeof json?.maxTitleLength === "number" ? json.maxTitleLength : 60;
	} catch {
		return 60;
	}
};

export type PrediccionCategoriaMl = { categoryId: string; categoryName: string; categoryPath: string };

export const predecirCategoriasMlAPI = async (titulo: string): Promise<PrediccionCategoriaMl[]> => {
	const res = await fetchAPI(`${API_BASE_URL}/api/ml/predecir-categorias?titulo=${encodeURIComponent(titulo)}`);
	if (!res.ok) throw new Error(await extraerMensajeError(res, "No se pudieron predecir categorías"));
	return await res.json();
};

// Sugiere el menor SKU libre del rango (individual vs combo). Devuelve null si el rango está lleno.
export const getSiguienteSkuAPI = async (esCombo: boolean): Promise<string | null> => {
	const res = await fetchAPI(`${API_URL}/siguiente-sku?esCombo=${esCombo}`);
	if (!res.ok) throw new Error("Error al obtener el siguiente SKU");
	const json = await res.json();
	return typeof json?.sku === "string" ? json.sku : null;
};

/** Verifica si ya existe un producto con ese SKU (para avisar en el alta). */
export const existeSkuAPI = async (sku: string, signal?: AbortSignal): Promise<boolean> => {
	const res = await fetchAPI(`${API_URL}/existe-sku?sku=${encodeURIComponent(sku)}`, { signal });
	if (!res.ok) throw new Error("Error al verificar el SKU");
	return (await res.json()) === true;
};

export const createProductoAPI = async (data: ProductoCreateDTO, origin: ProductoAuditOrigin = "API") => {
	const response = await fetchAPI(API_URL, {
		method: "POST",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(cleanPayload(data)),
	});
	if (!response.ok) throw new Error(await extraerMensajeError(response, "Error al crear producto"));
	return await response.json();
};
export const updateProductoAPI = async (id: number, data: ProductoPatchDTO, origin: ProductoAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "PATCH",
		headers: withAuditOrigin(origin, { "Content-Type": "application/json" }),
		body: JSON.stringify(cleanPayload(data)),
	});
	if (!response.ok) throw new Error("Error al actualizar");
	return await response.json();
};
export const deleteProductoAPI = async (id: number, origin: ProductoAuditOrigin = "API") => {
	const response = await fetchAPI(`${API_URL}/${id}`, {
		method: "DELETE",
		headers: withAuditOrigin(origin),
	});
	if (!response.ok) throw new Error("Error al eliminar");
	return true;
};

// Asocia una relación N-a-N al producto (catalogos | aptos | segmentos).
// Ignora 409 (ya existe) para que la operación sea idempotente.
const asociarRelacion = async (productoId: number, tipo: "catalogos" | "aptos" | "segmentos", relId: number) => {
	const res = await fetchAPI(`${API_URL}/${productoId}/${tipo}/${relId}`, { method: "POST", allowedStatuses: [409] });
	if (!res.ok && res.status !== 409) throw new Error(`Error al asociar ${tipo}`);
};

export const addProductoCatalogoAPI = (productoId: number, catalogoId: number) => asociarRelacion(productoId, "catalogos", catalogoId);
export const addProductoAptoAPI = (productoId: number, aptoId: number) => asociarRelacion(productoId, "aptos", aptoId);
export const addProductoSegmentoAPI = (productoId: number, segmentoId: number) => asociarRelacion(productoId, "segmentos", segmentoId);

// Quita una relación N-a-N del producto. Ignora 404 (ya no existe) para idempotencia.
const desasociarRelacion = async (productoId: number, tipo: "catalogos" | "aptos" | "segmentos", relId: number) => {
	const res = await fetchAPI(`${API_URL}/${productoId}/${tipo}/${relId}`, { method: "DELETE", allowedStatuses: [404] });
	if (!res.ok && res.status !== 404) throw new Error(`Error al quitar ${tipo}`);
};

export const removeProductoCatalogoAPI = (productoId: number, catalogoId: number) => desasociarRelacion(productoId, "catalogos", catalogoId);
export const removeProductoAptoAPI = (productoId: number, aptoId: number) => desasociarRelacion(productoId, "aptos", aptoId);
export const removeProductoSegmentoAPI = (productoId: number, segmentoId: number) => desasociarRelacion(productoId, "segmentos", segmentoId);

export const getProductoAuditoriaAPI = async (
	id: number,
	page = 0,
	size = 50,
	sort: string | string[] = "fechaHora,desc",
) => {
	const params = new URLSearchParams({
		page: String(page),
		size: String(size),
	});
	const sortArray = Array.isArray(sort) ? sort : [sort];
	sortArray.forEach((item) => params.append("sort", item));

	const res = await fetchAPI(`${API_URL}/${id}/auditoria?${params.toString()}`);
	if (!res.ok) throw new Error("Error al cargar historial del producto");
	return await res.json() as { content: AuditoriaCambioDTO[]; page?: { totalElements?: number } };
};

// --- HELPER PARA BUSCAR NOMBRE POR ID (Auto-corrección visual) ---
/**
 * Devuelve el nombre corto (para mostrar en la celda) y el nombre completo
 * (path "A > B > C" para tooltip), si la entidad expone `nombreCompleto`.
 */
export const getNombreById = async (
	endpoint: string,
	id: number,
	labelKey: string = "nombre",
): Promise<{ nombre: string; nombreCompleto?: string }> => {
	try {
		const res = await fetchAPI(`${API_BASE_URL}/api/${endpoint}/${id}`);
		if (!res.ok) return { nombre: "---" };
		const json = await res.json();
		const nombre = json[labelKey] || json.nombre || json.mla || "---";
		const nombreCompleto = typeof json.nombreCompleto === "string" ? json.nombreCompleto : undefined;
		return { nombre, nombreCompleto };
	} catch {
		return { nombre: "Error" };
	}
};
// --- HELPERS PARA ASYNC SELECT (Buscadores) ---
const fetchOptions = async (endpoint: string, query: string, labelKey: string = "nombre", size: number = 20) => {
	const url = `${API_BASE_URL}/api/${endpoint}?page=0&size=${size}&search=${query}`;
	const res = await fetchAPI(url);
	if (!res.ok) return [];
	const json = await res.json();

	const q = query.toLowerCase();
	// Para entidades jerárquicas el dropdown muestra el path completo
	// "A > B > C" (más informativo al elegir); el nombre corto queda
	// disponible aparte para mostrar en la celda después de seleccionar.
	return (json.content as LookupItem[]).map((item) => {
		const nombreCorto = String(item[labelKey] ?? item.nombre ?? item.mla ?? "Sin Nombre");
		const nombreCompleto = typeof item.nombreCompleto === "string" ? item.nombreCompleto : undefined;
		return {
			id: item.id,
			label: nombreCompleto ?? nombreCorto,
			nombreCorto,
		} satisfies SearchOption;
	}).sort((a, b) => {
		// El sort sigue priorizando matches por prefijo del nombre corto
		// (más útil que el path para tipear "abat" y ver "ABATIDORES" arriba).
		const aMatch = (a.nombreCorto ?? a.label).toLowerCase().startsWith(q) ? 0 : 1;
		const bMatch = (b.nombreCorto ?? b.label).toLowerCase().startsWith(q) ? 0 : 1;
		return aMatch - bMatch;
	});
};

export const searchMarcas = (q: string, size?: number) => fetchOptions("marcas", q, "nombre", size);
export const searchSectoresDeposito = (q: string, size?: number) => fetchOptions("sectores-deposito", q, "codigo", size);
export const searchOrigenes = (q: string, size?: number) => fetchOptions("origenes", q, "nombre", size);
export const searchClasifGral = (q: string, size?: number) => fetchOptions("clasif-gral", q, "nombre", size);
export const searchClasifGastro = (q: string, size?: number) => fetchOptions("clasif-gastro", q, "nombre", size);
export const searchTipos = (q: string, size?: number) => fetchOptions("tipos", q, "nombre", size);
export const searchProveedores = (q: string, size?: number) => fetchOptions("proveedores", q, "nombre", size);
export const searchMateriales = (q: string, size?: number) => fetchOptions("materiales", q, "nombre", size);
export const searchMlas = (q: string, size?: number) => fetchOptions("mlas", q, "mla", size);
export const searchCanales = (q: string, size?: number) => fetchOptions("canales", q, "nombre", size);
export const searchCatalogos = (q: string, size?: number) => fetchOptions("catalogos", q, "nombre", size);
export const searchAptos = (q: string, size?: number) => fetchOptions("aptos", q, "nombre", size);
export const searchSegmentos = (q: string, size?: number) => fetchOptions("segmentos", q, "nombre", size);

export type ImagenDetalle = { nombre: string; extension: string; bytes: number };

export async function getImagenDetalleAPI(sku: string): Promise<ImagenDetalle[]> {
	const res = await fetchAPI(`${API_BASE_URL}/api/imagenes/detalle/${encodeURIComponent(sku)}`);
	return res.json();
}

export type EstadoCanal = {
	publicado: boolean;
	estado: string | null;
	precio: number | null;
	promo: number | null;
	stock: number | null;
	peso: string | null;
	dimensiones: string | null;
	imagenes: number | null;
	imagenesUrls: string[] | null;
	error: boolean;
};
export type SeoCanal = { title: string | null; description: string | null; tags: string | null };
export type MlCanal = { estado: EstadoCanal; categoryId: string | null; categoryNombre: string | null; atributos: ProductoMlAtributo[]; descripcion: string | null; mlaResuelto: string | null; mlPaqAlto: number | null; mlPaqAncho: number | null; mlPaqLargo: number | null; mlPaqPeso: number | null };
export type NubeCanal = { estado: EstadoCanal; descripcion: string | null; seo: SeoCanal | null; titulo: string | null; peso: string | null; profundidad: string | null; ancho: string | null; alto: string | null };
export type DuxCanal = { estado: EstadoCanal };
export type EstadoPublicacionUpdate = { ml?: string | null; hogar?: boolean | null; gastro?: boolean | null };

async function getEstadoCanal<T>(id: number, canal: string): Promise<T> {
	const r = await fetchAPI(`${API_BASE_URL}/api/productos/${id}/estado-publicacion/${canal}`);
	if (!r.ok) throw new Error(await extraerMensajeError(r, "No se pudo leer el estado del canal"));
	return r.json();
}
export const getEstadoMlAPI = (id: number) => getEstadoCanal<MlCanal>(id, "ml");
export const getEstadoHogarAPI = (id: number) => getEstadoCanal<NubeCanal>(id, "hogar");
export const getEstadoGastroAPI = (id: number) => getEstadoCanal<NubeCanal>(id, "gastro");
export const getEstadoDuxAPI = (id: number) => getEstadoCanal<DuxCanal>(id, "dux");

export async function getDescripcionSugeridaAPI(id: number, canal: "ml" | "nube"): Promise<string> {
	const r = await fetchAPI(`${API_BASE_URL}/api/productos/${id}/descripcion-sugerida?canal=${canal}`);
	if (!r.ok) throw new Error(await extraerMensajeError(r, "No se pudo obtener la descripción sugerida"));
	const data = await r.json() as { texto: string };
	return data.texto;
}
export type CanalAplicado = { ok: boolean; detalle: string } | null;
export type EstadoAplicar = { ml: CanalAplicado; hogar: CanalAplicado; gastro: CanalAplicado };

export async function putEstadoPublicacionAPI(id: number, body: EstadoPublicacionUpdate): Promise<EstadoAplicar> {
	const r = await fetchAPI(`${API_BASE_URL}/api/productos/${id}/estado-publicacion`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
	if (!r.ok) throw new Error(await extraerMensajeError(r, "No se pudo aplicar el estado de publicación"));
	return r.json() as Promise<EstadoAplicar>;
}

export type EstadoCarpeta = { ruta: string; existe: boolean; esDirectorio: boolean; legible: boolean; escribible: boolean };
export type CrudasDisponibles = { crudaDir: EstadoCarpeta; destinoDir: EstadoCarpeta; imagenes: string[] };

export async function getCrudasAPI(sku: string): Promise<CrudasDisponibles> {
	const r = await fetchAPI(`${API_BASE_URL}/api/imagenes/caratula/crudas/${encodeURIComponent(sku)}`);
	if (!r.ok) throw new Error(await extraerMensajeError(r, "No se pudieron leer las imágenes crudas"));
	return r.json();
}

export function crudaMiniaturaURL(nombre: string): string {
	return `${API_BASE_URL}/api/imagenes/cruda/${encodeURIComponent(nombre)}`;
}

// "MLA1234" -> "https://articulo.mercadolibre.com.ar/MLA-1234" (ML redirige al artículo)
export function mlVerURL(codigo: string): string {
	const conGuion = codigo.replace(/^(MLAU?|MLA)(\d)/, "$1-$2");
	return `https://articulo.mercadolibre.com.ar/${conGuion}`;
}
export function mlEditarURL(codigo: string): string {
	return `https://www.mercadolibre.com.ar/publicaciones/${codigo}/modificar`;
}

export async function generarCaratulaAPI(sku: string, crudaNombre?: string): Promise<{ imagenBase64: string; formato: string; crudaBase64: string; crudaFormato: string }> {
	const q = crudaNombre ? `?cruda=${encodeURIComponent(crudaNombre)}` : "";
	const r = await fetchAPI(`${API_BASE_URL}/api/imagenes/caratula/generar/${encodeURIComponent(sku)}${q}`, { method: "POST" });
	if (!r.ok) throw new Error(await extraerMensajeError(r, "No se pudo generar la carátula"));
	return r.json();
}

export async function guardarCaratulaAPI(sku: string, imagenBase64: string): Promise<void> {
	const r = await fetchAPI(`${API_BASE_URL}/api/imagenes/caratula/guardar/${encodeURIComponent(sku)}`,
		{ method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ imagenBase64 }) });
	if (!r.ok) throw new Error(await extraerMensajeError(r, "No se pudo guardar la carátula"));
}

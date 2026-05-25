"use client";
import { getErrorMessage } from "@/lib/errors";
import { useState, useEffect, useCallback, useRef } from "react";
import { notificar } from "../utils/notificar";
import {
	getCanalReglasAPI,
	createCanalReglaAPI,
	updateCanalReglaAPI,
	deleteCanalReglaAPI,
	searchCanales,
	searchProductosAPI,
} from "./canalReglaService";
import { CanalReglaDTO, CanalReglaPatchDTO, CanalReglaUpsertDTO } from "./types";
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";

async function fetchAllItems(url: string): Promise<any[]> {
	try {
		const res = await fetchAPI(`${url}?page=0&size=500&sort=id,asc`);
		const data = await res.json();
		return data.content ?? [];
	} catch { return []; }
}

export function useCanalRegla(
	pageIndex: number,
	pageSize: number,
	search: string,
	filters: Record<string, any> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	// Mapea ids de columnas del front a campos de ordenamiento del backend.
	const SORT_MAP: Record<string, string> = {
		canalNombre: "canal.nombre",
		productoLabel: "producto.sku",
	};
	const sortParam = sorting.length > 0
		? `${SORT_MAP[sorting[0].id] || sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,desc";

	const [data, setData] = useState<CanalReglaDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [pageCount, setPageCount] = useState(0);
	const [error, setError] = useState<string | null>(null);
	const [isLoading, setIsLoading] = useState(true);

	// Maps de lookup para enriquecer los ids del backend con nombres legibles.
	const canalMapRef = useRef<Record<number, string>>({});
	const tipoMapRef = useRef<Record<number, string>>({});
	const marcaMapRef = useRef<Record<number, string>>({});
	const clasifGralMapRef = useRef<Record<number, string>>({});
	const clasifGastroMapRef = useRef<Record<number, string>>({});
	// Productos no se precargan por volumen: se resuelven on-demand y se cachean.
	const productoMapRef = useRef<Record<number, string>>({});
	const mapsLoaded = useRef(false);

	const loadMaps = useCallback(async () => {
		if (mapsLoaded.current) return;
		const [canales, tipos, marcas, clasifGral, clasifGastro] = await Promise.all([
			fetchAllItems(`${API_BASE_URL}/api/canales`),
			fetchAllItems(`${API_BASE_URL}/api/tipos`),
			fetchAllItems(`${API_BASE_URL}/api/marcas`),
			fetchAllItems(`${API_BASE_URL}/api/clasif-gral`),
			fetchAllItems(`${API_BASE_URL}/api/clasif-gastro`),
		]);
		canales.forEach((c: any) => { canalMapRef.current[c.id] = c.nombre; });
		tipos.forEach((c: any) => { tipoMapRef.current[c.id] = c.nombre; });
		marcas.forEach((c: any) => { marcaMapRef.current[c.id] = c.nombre; });
		clasifGral.forEach((c: any) => { clasifGralMapRef.current[c.id] = c.nombre; });
		clasifGastro.forEach((c: any) => { clasifGastroMapRef.current[c.id] = c.nombre; });
		mapsLoaded.current = true;
	}, []);

	const resolverProductos = useCallback(async (ids: number[]) => {
		const faltantes = Array.from(new Set(ids.filter((id) => !productoMapRef.current[id])));
		if (faltantes.length === 0) return;
		await Promise.all(faltantes.map(async (id) => {
			try {
				const res = await fetchAPI(`${API_BASE_URL}/api/productos/${id}`);
				if (!res.ok) return;
				const p = await res.json();
				productoMapRef.current[id] = `[${p.sku ?? id}] ${p.tituloWeb || p.descripcion || ""}`.trim();
			} catch {
				productoMapRef.current[id] = `Producto #${id}`;
			}
		}));
	}, []);

	const fetchData = useCallback(async () => {
		setIsLoading(true);
		setError(null);
		try {
			await loadMaps();
			const res = await getCanalReglasAPI(pageIndex, pageSize, search, filters, sortParam);
			const productoIds = (res.content || [])
				.map((r: any) => r.productoId)
				.filter((id: number | null | undefined): id is number => id != null);
			await resolverProductos(productoIds);

			const enriched = (res.content || []).map((r: any) => ({
				...r,
				canalNombre: canalMapRef.current[r.canalId] ?? `Canal #${r.canalId}`,
				tipoNombre: r.tipoId != null ? (tipoMapRef.current[r.tipoId] ?? `Tipo #${r.tipoId}`) : undefined,
				marcaNombre: r.marcaId != null ? (marcaMapRef.current[r.marcaId] ?? `Marca #${r.marcaId}`) : undefined,
				clasifGralNombre: r.clasifGralId != null ? (clasifGralMapRef.current[r.clasifGralId] ?? `#${r.clasifGralId}`) : undefined,
				clasifGastroNombre: r.clasifGastroId != null ? (clasifGastroMapRef.current[r.clasifGastroId] ?? `#${r.clasifGastroId}`) : undefined,
				productoLabel: r.productoId != null ? (productoMapRef.current[r.productoId] ?? `Producto #${r.productoId}`) : undefined,
			}));
			setData(enriched);
			setTotalRecords(res.page?.totalElements || 0);
			setPageCount(res.page?.totalPages || 0);
		} catch (e: unknown) {
			setError(getErrorMessage(e));
		} finally {
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, search, JSON.stringify(filters), sortParam, loadMaps, resolverProductos]);

	useEffect(() => { fetchData(); }, [fetchData]);

	const createRegla = async (item: CanalReglaUpsertDTO) => {
		try {
			const result = await createCanalReglaAPI(item);
			await fetchData();
			notificar.success(`[Reglas de Canal] Registro #${result.id} creado`);
			notificar.info("Los precios del canal se están recalculando en segundo plano...");
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al crear"));
			throw e;
		}
	};

	const updateRegla = async (id: number, item: CanalReglaPatchDTO) => {
		try {
			// El PATCH devuelve el objeto actualizado. Reemplazamos solo esa fila
			// en lugar de refetchar toda la página: evita el skeleton de loading
			// y mantiene scroll / selección intactos.
			const actualizado: CanalReglaDTO = await updateCanalReglaAPI(id, item);
			setData((prev) => prev.map((d) => (d.id === id ? { ...d, ...actualizado } : d)));
			notificar.success(`[Reglas de Canal] Registro #${id} actualizado`);
			notificar.info("Los precios del canal se están recalculando en segundo plano...");
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al actualizar"));
			throw e;
		}
	};

	const deleteRegla = async (ids: number[]) => {
		try {
			await Promise.all(ids.map((id) => deleteCanalReglaAPI(id)));
			await fetchData();
			notificar.success(ids.length === 1
				? `[Reglas de Canal] Registro #${ids[0]} eliminado`
				: `[Reglas de Canal] ${ids.length} registros eliminados`);
			notificar.info("Los precios del canal se están recalculando en segundo plano...");
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al eliminar"));
			throw e;
		}
	};

	return {
		data,
		totalRecords,
		pageCount,
		error,
		isLoading,
		createRegla,
		updateRegla,
		deleteRegla,
		searchCanales,
		searchProductos: searchProductosAPI,
		refetch: fetchData,
	};
}

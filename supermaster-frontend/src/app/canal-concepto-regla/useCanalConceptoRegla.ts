"use client";
import { useState, useEffect, useCallback, useRef } from "react";
import { notificar } from "../utils/notificar";
import {
	getReglasAPI,
	createReglaAPI,
	updateReglaAPI,
	deleteReglaAPI,
	searchCanalesAPI,
	searchConceptosAPI,
} from "./canalConceptoReglaService";
import { CanalConceptoReglaDTO, CanalConceptoReglaPatchDTO, CanalConceptoReglaUpsertDTO } from "./types";
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";

async function fetchAllItems(url: string): Promise<any[]> {
	try {
		const res = await fetchAPI(`${url}?page=0&size=500&sort=id,asc`);
		const data = await res.json();
		return data.content ?? [];
	} catch { return []; }
}

export function useCanalConceptoRegla(
	pageIndex: number,
	pageSize: number,
	search: string,
	filters: Record<string, any> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	const SORT_MAP: Record<string, string> = { canalNombre: "canal.nombre", conceptoNombre: "concepto.nombre" };
	const sortParam = sorting.length > 0
		? `${SORT_MAP[sorting[0].id] || sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,asc";
	const [data, setData] = useState<CanalConceptoReglaDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [pageCount, setPageCount] = useState(0);
	const [error, setError] = useState<string | null>(null);
	const [isLoading, setIsLoading] = useState(true);

	const canalMapRef = useRef<Record<number, string>>({});
	const conceptoMapRef = useRef<Record<number, string>>({});
	const conceptoDescripcionMapRef = useRef<Record<number, string>>({});
	const conceptoPorcentajeMapRef = useRef<Record<number, number>>({});
	const conceptoAplicaSobreMapRef = useRef<Record<number, string>>({});
	const tipoMapRef = useRef<Record<number, string>>({});
	const clasifGastroMapRef = useRef<Record<number, string>>({});
	const clasifGralMapRef = useRef<Record<number, string>>({});
	const marcaMapRef = useRef<Record<number, string>>({});
	const mapsLoaded = useRef(false);

	const loadMaps = useCallback(async () => {
		if (mapsLoaded.current) return;
		const [canales, conceptos, tipos, clasifGastro, clasifGral, marcas] = await Promise.all([
			fetchAllItems(`${API_BASE_URL}/api/canales`),
			fetchAllItems(`${API_BASE_URL}/api/conceptos-calculo`),
			fetchAllItems(`${API_BASE_URL}/api/tipos`),
			fetchAllItems(`${API_BASE_URL}/api/clasif-gastro`),
			fetchAllItems(`${API_BASE_URL}/api/clasif-gral`),
			fetchAllItems(`${API_BASE_URL}/api/marcas`),
		]);
		canales.forEach((c: any) => { canalMapRef.current[c.id] = c.nombre; });
		conceptos.forEach((c: any) => {
			conceptoMapRef.current[c.id] = c.nombre;
			if (c.descripcion) conceptoDescripcionMapRef.current[c.id] = c.descripcion;
			if (c.porcentaje != null) conceptoPorcentajeMapRef.current[c.id] = Number(c.porcentaje);
			if (c.aplicaSobre) conceptoAplicaSobreMapRef.current[c.id] = c.aplicaSobre;
		});
		tipos.forEach((c: any) => { tipoMapRef.current[c.id] = c.nombre; });
		clasifGastro.forEach((c: any) => { clasifGastroMapRef.current[c.id] = c.nombre; });
		clasifGral.forEach((c: any) => { clasifGralMapRef.current[c.id] = c.nombre; });
		marcas.forEach((c: any) => { marcaMapRef.current[c.id] = c.nombre; });
		mapsLoaded.current = true;
	}, []);

	const fetchData = useCallback(async () => {
		setIsLoading(true);
		setError(null);
		try {
			await loadMaps();
			const res = await getReglasAPI(pageIndex, pageSize, search, filters, sortParam);
			const enriched = (res.content || []).map((r: any) => ({
				...r,
				canalNombre: canalMapRef.current[r.canalId] ?? `Canal #${r.canalId}`,
				conceptoNombre: conceptoMapRef.current[r.conceptoId] ?? `Concepto #${r.conceptoId}`,
				conceptoDescripcion: conceptoDescripcionMapRef.current[r.conceptoId],
				conceptoPorcentaje: conceptoPorcentajeMapRef.current[r.conceptoId],
				conceptoAplicaSobre: conceptoAplicaSobreMapRef.current[r.conceptoId],
				tipoNombre: r.tipoId != null ? (tipoMapRef.current[r.tipoId] ?? `Tipo #${r.tipoId}`) : undefined,
				clasifGastroNombre: r.clasifGastroId != null ? (clasifGastroMapRef.current[r.clasifGastroId] ?? `#${r.clasifGastroId}`) : undefined,
				clasifGralNombre: r.clasifGralId != null ? (clasifGralMapRef.current[r.clasifGralId] ?? `#${r.clasifGralId}`) : undefined,
				marcaNombre: r.marcaId != null ? (marcaMapRef.current[r.marcaId] ?? `Marca #${r.marcaId}`) : undefined,
			}));
			setData(enriched);
			setTotalRecords(res.page?.totalElements || 0);
			setPageCount(res.page?.totalPages || 0);
		} catch (e: any) {
			setError(e.message);
		} finally {
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, search, JSON.stringify(filters), sortParam, loadMaps]);

	useEffect(() => {
		fetchData();
	}, [fetchData]);

	const createRegla = async (item: CanalConceptoReglaUpsertDTO) => {
		try {
			const result = await createReglaAPI(item);
			await fetchData();
			notificar.success(`[Reglas de Excepción] Registro #${result.id} creado`);
			notificar.info("Los precios del canal se están recalculando en segundo plano...");
		} catch (e: any) {
			notificar.error(e?.message || "Error al crear");
			throw e;
		}
	};
	const updateRegla = async (id: number, item: CanalConceptoReglaPatchDTO) => {
		try {
			await updateReglaAPI(id, item);
			await fetchData();
			notificar.success(`[Reglas de Excepción] Registro #${id} actualizado`);
			notificar.info("Los precios del canal se están recalculando en segundo plano...");
		} catch (e: any) {
			notificar.error(e?.message || "Error al actualizar");
			throw e;
		}
	};
	const deleteRegla = async (ids: number[]) => {
		try {
			await Promise.all(ids.map((id) => deleteReglaAPI(id)));
			await fetchData();
			notificar.success(ids.length === 1 ? `[Reglas de Excepción] Registro #${ids[0]} eliminado` : `[Reglas de Excepción] ${ids.length} registros eliminados`);
			notificar.info("Los precios del canal se están recalculando en segundo plano...");
		} catch (e: any) {
			notificar.error(e?.message || "Error al eliminar");
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
		searchCanales: searchCanalesAPI,
		searchConceptos: searchConceptosAPI,
		refetch: fetchData,
	};
}

"use client";
import { getErrorMessage } from "@/lib/errors";
import { useState, useEffect, useCallback } from "react";
import { notificar } from "../utils/notificar";
import {
	getCuotasAPI,
	createCuotaAPI,
	updateCuotaAPI,
	deleteCuotaAPI,
	searchCanalesAPI,
} from "./canalConceptoCuotaService";
import { CanalConceptoCuotaDTO, CanalConceptoCuotaPatchDTO, CanalConceptoCuotaUpsertDTO } from "./types";

export function useCanalConceptoCuota(
	pageIndex: number,
	pageSize: number,
	search: string,
	filters: Record<string, any> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	const SORT_MAP: Record<string, string> = { canalNombre: "canal.nombre" };
	const sortParam = sorting.length > 0
		? `${SORT_MAP[sorting[0].id] || sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,asc";
	const [data, setData] = useState<CanalConceptoCuotaDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [pageCount, setPageCount] = useState(0);
	const [isLoading, setIsLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);

	const fetchData = useCallback(async () => {
		setIsLoading(true);
		setError(null);
		try {
			const res = await getCuotasAPI(pageIndex, pageSize, search, filters, sortParam);
			setData(res.content || []);
			setTotalRecords(res.page?.totalElements || 0);
			setPageCount(res.page?.totalPages || 0);
		} catch (err: unknown) {
			setError(getErrorMessage(err, "Error al cargar cuotas"));
		} finally {
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, search, JSON.stringify(filters), sortParam]);

	useEffect(() => {
		fetchData();
	}, [fetchData]);

	const createCuota = async (item: CanalConceptoCuotaUpsertDTO) => {
		try {
			const result = await createCuotaAPI(item, "FORM");
			await fetchData();
			notificar.success(`[Cuotas por Canal] Registro #${result.id} creado`);
			notificar.info("Los precios del canal se están recalculando en segundo plano...");
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al crear"));
			throw e;
		}
	};

	const updateCuota = async (id: number, item: CanalConceptoCuotaPatchDTO) => {
		try {
			// El PATCH devuelve el objeto actualizado. Reemplazamos solo esa fila
			// en lugar de refetchar toda la página: evita el skeleton de loading
			// y mantiene scroll / selección intactos.
			const actualizado: CanalConceptoCuotaDTO = await updateCuotaAPI(id, item, "INLINE");
			setData((prev) => prev.map((d) => (d.id === id ? { ...d, ...actualizado } : d)));
			notificar.success(`[Cuotas por Canal] Registro #${id} actualizado`);
			notificar.info("Los precios del canal se están recalculando en segundo plano...");
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al actualizar"));
			throw e;
		}
	};

	const deleteCuota = async (ids: number[]) => {
		try {
			await Promise.all(ids.map((id) => deleteCuotaAPI(id, "TABLE")));
			await fetchData();
			notificar.success(ids.length === 1 ? `[Cuotas por Canal] Registro #${ids[0]} eliminado` : `[Cuotas por Canal] ${ids.length} registros eliminados`);
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al eliminar"));
			throw e;
		}
	};

	const searchCanales = async (q: string) => {
		return await searchCanalesAPI(q);
	};

	return {
		data,
		totalRecords,
		pageCount,
		isLoading,
		error,
		createCuota,
		updateCuota,
		deleteCuota,
		searchCanales,
		refetch: fetchData,
	};
}

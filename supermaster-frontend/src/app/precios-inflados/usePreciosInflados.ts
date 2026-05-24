"use client";
import { useState, useEffect, useCallback } from "react";
import { notificar } from "../utils/notificar";
import {
	getPreciosInfladosAPI,
	createPrecioInfladoAPI,
	updatePrecioInfladoAPI,
	deletePrecioInfladoAPI,
} from "./preciosInfladosService";
import { PrecioInfladoDTO } from "./types";

export function usePreciosInflados(pageIndex: number, pageSize: number, search: string, sorting: { id: string; desc: boolean }[] = []) {
	const sortParam = sorting.length > 0
		? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,desc";
	const [data, setData] = useState<PrecioInfladoDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [pageCount, setPageCount] = useState(0);
	const [isLoading, setIsLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);

	const fetchData = useCallback(async () => {
		setIsLoading(true);
		setError(null);
		try {
			const res = await getPreciosInfladosAPI(pageIndex, pageSize, search, sortParam);
			setData(res.content || []);
			setTotalRecords(res.page?.totalElements || 0);
			setPageCount(res.page?.totalPages || 0);
		} catch (e: any) {
			setError(e.message);
		} finally {
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, search, sortParam]);

	useEffect(() => {
		fetchData();
	}, [fetchData]);

	const createItem = async (item: Partial<PrecioInfladoDTO>) => {
		try {
			const result = await createPrecioInfladoAPI(item);
			await fetchData();
			notificar.success(`[Precios Inflados] Registro #${result.id} creado`);
		} catch (e: any) {
			notificar.error(e?.message || "Error al crear");
			throw e;
		}
	};
	const updateItem = async (id: number, item: Partial<PrecioInfladoDTO>) => {
		try {
			// El PATCH devuelve el objeto actualizado. Reemplazamos solo esa fila
			// en lugar de refetchar toda la página: evita el skeleton de loading
			// y mantiene scroll / selección intactos.
			const actualizado: PrecioInfladoDTO = await updatePrecioInfladoAPI(id, item);
			setData((prev) => prev.map((d) => (d.id === id ? { ...d, ...actualizado } : d)));
			notificar.success(`[Precios Inflados] Registro #${id} actualizado`);
		} catch (e: any) {
			notificar.error(e?.message || "Error al actualizar");
			throw e;
		}
	};
	const deleteItem = async (ids: number[]) => {
		try {
			await Promise.all(ids.map((id) => deletePrecioInfladoAPI(id)));
			await fetchData();
			notificar.success(ids.length === 1 ? `[Precios Inflados] Registro #${ids[0]} eliminado` : `[Precios Inflados] ${ids.length} registros eliminados`);
		} catch (e: any) {
			notificar.error(e?.message || "Error al eliminar");
			throw e;
		}
	};

	return {
		data,
		totalRecords,
		pageCount,
		isLoading,
		error,
		createItem,
		updateItem,
		deleteItem,
		refetch: fetchData,
	};
}

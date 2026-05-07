// app/producto_canal_precios/useProductoCanalPrecios.ts
"use client";
import { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { getProductoCanalPreciosAPI } from "./productoCanalPreciosService";
import { ProductoCanalPrecioDTO } from "./types";
import { serializeForDeps } from "../utils/serializeForDeps";

export function useProductoCanalPrecios(
	pageIndex: number,
	pageSize: number,
	search: string,
	filters: Record<string, unknown> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	const sortParams = sorting.length > 0
		? sorting.map((s) => `${s.id},${s.desc ? "desc" : "asc"}`)
		: ["id,desc"];
	const filtersKey = serializeForDeps(filters);
	const sortKey = sortParams.join("|");
	const requestFilters = useMemo<Record<string, unknown>>(
		() => ({ ...filters }),
		[filtersKey],
	);
	const requestSortParams = useMemo(() => [...sortParams], [sortKey]);
	const [data, setData] = useState<ProductoCanalPrecioDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [pageCount, setPageCount] = useState(0);
	const [isLoading, setIsLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);

	// Contador de requests para ignorar respuestas obsoletas (race condition)
	const requestIdRef = useRef(0);

	// No hacer fetch hasta que se haya inicializado (ready=true cuando hay canalId o está en "todos")
	const ready = filters._ready !== false;

	const fetchData = useCallback(async () => {
		if (!ready) return;
		const currentRequestId = ++requestIdRef.current;
		setIsLoading(true);
		setError(null);
		try {
			const res = await getProductoCanalPreciosAPI(pageIndex, pageSize, search, requestFilters, requestSortParams);
			// Ignorar si ya hubo otro request más reciente
			if (requestIdRef.current !== currentRequestId) return;
			setData(res.content || []);
			setTotalRecords(res.page?.totalElements || 0);
			setPageCount(res.page?.totalPages || 0);
		} catch (err: unknown) {
			if (requestIdRef.current !== currentRequestId) return;
			setError(err instanceof Error ? err.message : "Error al cargar precios");
			setData([]);
		} finally {
			if (requestIdRef.current === currentRequestId) {
				setIsLoading(false);
			}
		}
	}, [pageIndex, pageSize, search, requestFilters, requestSortParams, ready]);

	useEffect(() => {
		fetchData();
	}, [fetchData]);

	const refreshRowLocal = useCallback(async (productoId: number) => {
		try {
			// El endpoint pagina sobre ProductoCanalPrecio (una fila = producto+canal+cuota),
			// así que un mismo producto puede ocupar N filas en la tabla. Pedimos size grande
			// para cubrir todas las combinaciones del producto que matchean los filtros actuales.
			const res = await getProductoCanalPreciosAPI(
				0,
				500,
				"",
				{ ...requestFilters, productoId },
				requestSortParams,
			);
			const freshRows = res.content ?? [];
			setData((prev) => {
				const firstIdx = prev.findIndex((r) => r.id === productoId);
				if (firstIdx === -1) return prev;
				const withoutOld = prev.filter((r) => r.id !== productoId);
				return [
					...withoutOld.slice(0, firstIdx),
					...freshRows,
					...withoutOld.slice(firstIdx),
				];
			});
		} catch {
			// Si falla el refresh puntual, dejamos las filas con los datos previos.
			// El usuario siempre puede forzar un refetch global con otra acción.
		}
	}, [requestFilters, requestSortParams]);

	return {
		data,
		totalRecords,
		pageCount,
		isLoading,
		error,
		refetch: fetchData,
		refreshRowLocal,
	};
}

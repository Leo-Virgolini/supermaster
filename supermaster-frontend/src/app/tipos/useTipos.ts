"use client";

import { getErrorMessage } from "@/lib/errors";
import { useState, useEffect, useCallback, useRef } from "react";
import { notificar } from "../utils/notificar";
import { TipoDTO } from "./types";

import {
	createTipoAPI,
	deleteTipoAPI,
	getTiposAPI,
	searchTiposAPI,
	updateTipoAPI,
} from "./tiposService";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

export function useTipos(
	pageIndex: number,
	pageSize: number,
	filters: Record<string, any> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	const sortParam = sorting.length > 0
		? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,asc";
	const [tipos, setTipos] = useState<TipoDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);
	const latestRequestIdRef = useRef(0);

	const getTipos = useCallback(async () => {
		const requestId = ++latestRequestIdRef.current;
		setIsLoading(true);
		setError(null);
		try {
			const json: PageResponse<TipoDTO> = await getTiposAPI(
				pageIndex,
				pageSize,
				filters,
				sortParam,
			);
			if (latestRequestIdRef.current !== requestId) return;
			setTipos(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch (err: unknown) {
			if (latestRequestIdRef.current !== requestId) return;
			setError(getErrorMessage(err, "Error desconocido"));
			setTipos([]);
		} finally {
			if (latestRequestIdRef.current !== requestId) return;
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, JSON.stringify(filters), sortParam]);

	useEffect(() => {
		getTipos();
	}, [getTipos]);

	const createTipo = async (nombre: string, padreId: number | null = null) => {
		try {
			const result = await createTipoAPI(nombre, padreId, "FORM");
			await getTipos();
			notificar.success(`[Tipos] Registro #${result.id} creado`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al crear"));
			throw e;
		}
	};

	const deleteTipo = async (ids: number[]) => {
		try {
			const promesas = ids.map((id) => deleteTipoAPI(id, "TABLE"));
			await Promise.all(promesas);
			await getTipos();
			notificar.success(ids.length === 1 ? `[Tipos] Registro #${ids[0]} eliminado` : `[Tipos] ${ids.length} registros eliminados`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al eliminar"));
			throw e;
		}
	};

	const updateTipo = async (id: number, data: Partial<Pick<TipoDTO, "nombre" | "padreId">>) => {
		try {
			// El PATCH devuelve el objeto actualizado. Reemplazamos solo esa fila
			// en lugar de refetchar toda la página: evita el skeleton de loading
			// y mantiene scroll / selección intactos.
			const actualizado: TipoDTO = await updateTipoAPI(id, data, "INLINE");
			setTipos((prev) => prev.map((t) => (t.id === id ? { ...t, ...actualizado } : t)));
			notificar.success(`[Tipos] Registro #${id} actualizado`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al actualizar"));
			throw e;
		}
	};

	const searchTipos = async (query: string) => {
		try {
			return await searchTiposAPI(query);
		} catch (e) {
			console.warn("[Tipos] Error en búsqueda:", e);
			return { content: [] };
		}
	};

	return {
		tipos,
		totalRecords,
		isLoading,
		error,
		createTipo,
		deleteTipo,
		updateTipo,
		searchTipos,
		refresh: getTipos,
	};
}

"use client";

import { getErrorMessage } from "@/lib/errors";
import { useState, useEffect, useCallback, useRef } from "react";
import { notificar } from "../utils/notificar";
import { CanalDTO } from "./types";
import {
	createCanalAPI,
	deleteCanalAPI,
	getCanalesAPI,
	searchCanalesAPI,
	updateCanalAPI,
} from "./canalesService";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

export function useCanales(
	pageIndex: number,
	pageSize: number,
	filters: Record<string, any> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	const sortParam = sorting.length > 0
		? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,asc";
	const [canales, setCanales] = useState<CanalDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);
	const latestRequestIdRef = useRef(0);

	const getCanales = useCallback(async () => {
		const requestId = ++latestRequestIdRef.current;
		setIsLoading(true);
		setError(null);
		try {
			const json: PageResponse<CanalDTO> = await getCanalesAPI(
				pageIndex,
				pageSize,
				filters,
				sortParam,
			);
			if (latestRequestIdRef.current !== requestId) return;
			setCanales(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch (err: unknown) {
			if (latestRequestIdRef.current !== requestId) return;
			setError(getErrorMessage(err, "Error desconocido"));
			setCanales([]);
		} finally {
			if (latestRequestIdRef.current !== requestId) return;
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, JSON.stringify(filters), sortParam]);

	useEffect(() => {
		getCanales();
	}, [getCanales]);

	const createCanal = async (data: {
		nombre: string;
		canalBaseId?: number;
	}): Promise<CanalDTO> => {
		try {
			const result = await createCanalAPI(data, "FORM");
			await getCanales();
			notificar.success(`[Canales] Registro #${result.id} creado`);
			return result;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al crear"));
			throw e;
		}
	};

	const deleteCanal = async (ids: number[]) => {
		try {
			const promesas = ids.map((id) => deleteCanalAPI(id, "TABLE"));
			await Promise.all(promesas);
			await getCanales();
			notificar.success(ids.length === 1 ? `[Canales] Registro #${ids[0]} eliminado` : `[Canales] ${ids.length} registros eliminados`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al eliminar"));
			throw e;
		}
	};

	const updateCanal = async (id: number, data: Partial<CanalDTO>) => {
		try {
			// El PATCH devuelve el objeto actualizado. Reemplazamos solo esa fila
			// en lugar de refetchar toda la página: evita el skeleton de loading
			// y mantiene scroll / selección intactos.
			const actualizado: CanalDTO = await updateCanalAPI(id, data, "INLINE");
			setCanales((prev) => prev.map((c) => (c.id === id ? { ...c, ...actualizado } : c)));
			notificar.success(`[Canales] Registro #${id} actualizado`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al actualizar"));
			throw e;
		}
	};

	const searchCanales = async (query: string) => {
		try {
			return await searchCanalesAPI(query);
		} catch (e) {
			console.warn("[Canales] Error en búsqueda:", e);
			return { content: [] };
		}
	};

	return {
		canales,
		totalRecords,
		isLoading,
		error,
		createCanal,
		deleteCanal,
		updateCanal,
		searchCanales,
		refresh: getCanales,
	};
}

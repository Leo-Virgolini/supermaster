"use client";

import { useState, useEffect, useCallback } from "react";
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

	const getCanales = useCallback(async () => {
		setIsLoading(true);
		setError(null);
		try {
			const json: PageResponse<CanalDTO> = await getCanalesAPI(
				pageIndex,
				pageSize,
				filters,
				sortParam,
			);
			setCanales(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch (err: any) {
			setError(err.message || "Error desconocido");
			setCanales([]);
		} finally {
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
		} catch (e: any) {
			notificar.error(e?.message || "Error al crear");
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
		} catch (e: any) {
			notificar.error(e?.message || "Error al eliminar");
			throw e;
		}
	};

	const updateCanal = async (id: number, data: Partial<CanalDTO>) => {
		try {
			await updateCanalAPI(id, data, "INLINE");
			await getCanales();
			notificar.success(`[Canales] Registro #${id} actualizado`);
			return true;
		} catch (e: any) {
			notificar.error(e?.message || "Error al actualizar");
			throw e;
		}
	};

	const searchCanales = async (query: string) => {
		try {
			return await searchCanalesAPI(query);
		} catch (e) {
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

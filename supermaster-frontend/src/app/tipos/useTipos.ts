"use client";

import { useState, useEffect, useCallback } from "react";
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

	const getTipos = useCallback(async () => {
		setIsLoading(true);
		setError(null);
		try {
			const json: PageResponse<TipoDTO> = await getTiposAPI(
				pageIndex,
				pageSize,
				filters,
				sortParam,
			);
			setTipos(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch (err: any) {
			setError(err.message || "Error desconocido");
			setTipos([]);
		} finally {
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
		} catch (e: any) {
			notificar.error(e?.message || "Error al crear");
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
		} catch (e: any) {
			notificar.error(e?.message || "Error al eliminar");
			throw e;
		}
	};

	const updateTipo = async (id: number, data: Partial<Pick<TipoDTO, "nombre" | "padreId">>) => {
		try {
			await updateTipoAPI(id, data, "INLINE");
			await getTipos();
			notificar.success(`[Tipos] Registro #${id} actualizado`);
			return true;
		} catch (e: any) {
			notificar.error(e?.message || "Error al actualizar");
			throw e;
		}
	};

	const searchTipos = async (query: string) => {
		try {
			return await searchTiposAPI(query);
		} catch (e) {
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

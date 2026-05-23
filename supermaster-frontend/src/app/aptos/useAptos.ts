"use client";

import { useState, useEffect, useCallback } from "react";
import { notificar } from "../utils/notificar";
import { AptoDTO } from "./types";
import {
	createAptoAPI,
	deleteAptoAPI,
	getAptosAPI,
	searchAptosAPI,
	updateAptoAPI,
} from "./aptosService";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

export function useAptos(
	pageIndex: number,
	pageSize: number,
	filters: Record<string, any> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	const sortParam = sorting.length > 0
		? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,asc";
	const [aptos, setAptos] = useState<AptoDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);

	const getAptos = useCallback(async () => {
		setIsLoading(true);
		setError(null);
		try {
			const json: PageResponse<AptoDTO> = await getAptosAPI(
				pageIndex,
				pageSize,
				filters,
				sortParam,
			);
			setAptos(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch (err: unknown) {
			setError(err instanceof Error ? err.message : "Error desconocido");
			setAptos([]);
		} finally {
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, JSON.stringify(filters), sortParam]);

	useEffect(() => {
		getAptos();
	}, [getAptos]);

	const createApto = async (apto: string) => {
		try {
			const result = await createAptoAPI(apto, "FORM");
			await getAptos();
			notificar.success(`[Aptos] Registro #${result.id} creado`);
			return true;
		} catch (e: unknown) {
			notificar.error(e instanceof Error ? e.message : "Error al crear");
			throw e;
		}
	};

	const deleteApto = async (ids: number[]) => {
		try {
			const promesas = ids.map((id) => deleteAptoAPI(id, "TABLE"));
			await Promise.all(promesas);
			await getAptos();
			notificar.success(ids.length === 1 ? `[Aptos] Registro #${ids[0]} eliminado` : `[Aptos] ${ids.length} registros eliminados`);
			return true;
		} catch (e: unknown) {
			notificar.error(e instanceof Error ? e.message : "Error al eliminar");
			throw e;
		}
	};

	const updateApto = async (
		id: number,
		payload: Partial<AptoDTO>,
	) => {
		try {
			await updateAptoAPI(id, payload, "INLINE");
			await getAptos();
			notificar.success(`[Aptos] Registro #${id} actualizado`);
			return true;
		} catch (e: unknown) {
			notificar.error(e instanceof Error ? e.message : "Error al actualizar");
			throw e;
		}
	};

	const searchAptos = async (query: string) => {
		try {
			return await searchAptosAPI(query);
		} catch (e) {
			return { content: [] };
		}
	};

	return {
		aptos,
		totalRecords,
		isLoading,
		error,
		createApto,
		deleteApto,
		updateApto,
		searchAptos,
		refresh: getAptos,
	};
}

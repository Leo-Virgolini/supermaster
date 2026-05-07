"use client";

import { useState, useEffect, useCallback } from "react";
import { notificar } from "../utils/notificar";
import { ConfigAutomatizacionDTO } from "./types";
import {
	getConfigAutomatizacionAPI,
	createConfigAutomatizacionAPI,
	updateConfigAutomatizacionAPI,
	deleteConfigAutomatizacionAPI,
} from "./configAutomatizacionService";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

export function useConfigAutomatizacion(
	pageIndex: number,
	pageSize: number,
	filters: Record<string, any> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	const sortParam = sorting.length > 0
		? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,asc";
	const [data, setData] = useState<ConfigAutomatizacionDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);

	const fetchData = useCallback(async () => {
		setIsLoading(true);
		setError(null);
		try {
			const json: PageResponse<ConfigAutomatizacionDTO> =
				await getConfigAutomatizacionAPI(pageIndex, pageSize, filters, sortParam);
			setData(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch (err: any) {
			setError(err.message || "Error desconocido");
			setData([]);
		} finally {
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, JSON.stringify(filters), sortParam]);

	useEffect(() => {
		fetchData();
	}, [fetchData]);

	const createConfig = async (payload: {
		clave: string;
		valor: string;
		descripcion?: string | null;
	}) => {
		try {
			const result = await createConfigAutomatizacionAPI(payload, "FORM");
			await fetchData();
			notificar.success(`[Config. Automatización] Registro #${result.id} creado`);
			return true;
		} catch (e: any) {
			notificar.error(e?.message || "Error al crear");
			throw e;
		}
	};

	const updateConfig = async (id: number, payload: Partial<ConfigAutomatizacionDTO>) => {
		try {
			await updateConfigAutomatizacionAPI(id, payload, "INLINE");
			await fetchData();
			notificar.success(`[Config. Automatización] Registro #${id} actualizado`);
			return true;
		} catch (e: any) {
			notificar.error(e?.message || "Error al actualizar");
			throw e;
		}
	};

	const deleteConfig = async (ids: number[]) => {
		try {
			await Promise.all(ids.map((id) => deleteConfigAutomatizacionAPI(id, "TABLE")));
			await fetchData();
			notificar.success(ids.length === 1 ? `[Config. Automatización] Registro #${ids[0]} eliminado` : `[Config. Automatización] ${ids.length} registros eliminados`);
			return true;
		} catch (e: any) {
			notificar.error(e?.message || "Error al eliminar");
			throw e;
		}
	};

	return {
		data,
		totalRecords,
		isLoading,
		error,
		createConfig,
		updateConfig,
		deleteConfig,
		refresh: fetchData,
	};
}

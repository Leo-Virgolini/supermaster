"use client";

import { getErrorMessage } from "@/lib/errors";
import { useState, useEffect, useCallback, useRef } from "react";
import { notificar } from "../utils/notificar";
import { ClasifGastroDTO } from "./types";
import {
	createClasifGastroAPI,
	deleteClasifGastroAPI,
	getClasifGastroAPI,
	searchClasifGastroAPI,
	updateClasifGastroAPI,
} from "./clasifGastroService";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

export function useClasifGastro(
	pageIndex: number,
	pageSize: number,
	filters: Record<string, any> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	const sortParam = sorting.length > 0
		? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,asc";
	const [clasifGastros, setClasifGastros] = useState<ClasifGastroDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);
	const latestRequestIdRef = useRef(0);

	const getClasifGastros = useCallback(async () => {
		const requestId = ++latestRequestIdRef.current;
		setIsLoading(true);
		setError(null);
		try {
			const json: PageResponse<ClasifGastroDTO> =
				await getClasifGastroAPI(pageIndex, pageSize, filters, sortParam);
			if (latestRequestIdRef.current !== requestId) return;
			setClasifGastros(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch (err: unknown) {
			if (latestRequestIdRef.current !== requestId) return;
			setError(getErrorMessage(err, "Error desconocido"));
			setClasifGastros([]);
		} finally {
			if (latestRequestIdRef.current !== requestId) return;
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, JSON.stringify(filters), sortParam]);

	useEffect(() => {
		getClasifGastros();
	}, [getClasifGastros]);

	const createClasifGastro = async (data: {
		nombre: string;
		esMaquina?: boolean;
		padreId?: number | null;
	}) => {
		try {
			const result = await createClasifGastroAPI(data, "FORM");
			await getClasifGastros();
			notificar.success(`[Clasif. Gastro] Registro #${result.id} creado`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al crear"));
			throw e;
		}
	};

	const deleteClasifGastro = async (ids: number[]) => {
		try {
			const promesas = ids.map((id) => deleteClasifGastroAPI(id, "TABLE"));
			await Promise.all(promesas);
			await getClasifGastros();
			notificar.success(ids.length === 1 ? `[Clasif. Gastro] Registro #${ids[0]} eliminado` : `[Clasif. Gastro] ${ids.length} registros eliminados`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al eliminar"));
			throw e;
		}
	};

	const updateClasifGastro = async (id: number, data: Partial<ClasifGastroDTO>) => {
		try {
			// El PATCH devuelve el objeto actualizado. Reemplazamos solo esa fila
			// en lugar de refetchar toda la página: evita el skeleton de loading
			// y mantiene scroll / selección intactos.
			const actualizado: ClasifGastroDTO = await updateClasifGastroAPI(id, data, "INLINE");
			setClasifGastros((prev) => prev.map((c) => (c.id === id ? { ...c, ...actualizado } : c)));
			notificar.success(`[Clasif. Gastro] Registro #${id} actualizado`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al actualizar"));
			throw e;
		}
	};

	const searchClasifGastros = async (query: string) => {
		try {
			return await searchClasifGastroAPI(query);
		} catch (e) {
			console.warn("[Clasif. Gastro] Error en búsqueda:", e);
			return { content: [] };
		}
	};

	return {
		clasifGastros,
		totalRecords,
		isLoading,
		error,
		createClasifGastro,
		deleteClasifGastro,
		updateClasifGastro,
		searchClasifGastros,
		refresh: getClasifGastros,
	};
}

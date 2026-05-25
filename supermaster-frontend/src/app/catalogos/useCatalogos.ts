"use client";

import { getErrorMessage } from "@/lib/errors";
import { useState, useEffect, useCallback } from "react";
import { notificar } from "../utils/notificar";
import { CatalogoDTO } from "./types";
import {
	createCatalogoAPI,
	deleteCatalogoAPI,
	getCatalogosAPI,
	searchCatalogosAPI,
	updateCatalogoAPI,
} from "./catalogosService";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

export function useCatalogos(
	pageIndex: number,
	pageSize: number,
	filters: Record<string, any> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	const sortParam = sorting.length > 0
		? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,asc";
	const [catalogos, setCatalogos] = useState<CatalogoDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);

	const getCatalogos = useCallback(async () => {
		setIsLoading(true);
		setError(null);
		try {
			const json: PageResponse<CatalogoDTO> = await getCatalogosAPI(
				pageIndex,
				pageSize,
				filters,
				sortParam,
			);
			setCatalogos(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch (err: unknown) {
			setError(getErrorMessage(err, "Error desconocido"));
			setCatalogos([]);
		} finally {
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, JSON.stringify(filters), sortParam]);

	useEffect(() => {
		getCatalogos();
	}, [getCatalogos]);

	const createCatalogo = async (data: {
		nombre: string;
		exportarConIva?: boolean;
		recargoPorcentaje?: number;
	}) => {
		try {
			const result = await createCatalogoAPI(data);
			await getCatalogos();
			notificar.success(`[Catálogos] Registro #${result.id} creado`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al crear"));
			throw e;
		}
	};

	const deleteCatalogo = async (ids: number[]) => {
		try {
			const promesas = ids.map((id) => deleteCatalogoAPI(id));
			await Promise.all(promesas);
			await getCatalogos();
			notificar.success(ids.length === 1 ? `[Catálogos] Registro #${ids[0]} eliminado` : `[Catálogos] ${ids.length} registros eliminados`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al eliminar"));
			throw e;
		}
	};

	const updateCatalogo = async (id: number, data: Partial<CatalogoDTO>) => {
		try {
			// El PATCH devuelve el objeto actualizado. Reemplazamos solo esa fila
			// en lugar de refetchar toda la página: evita el skeleton de loading
			// y mantiene scroll / selección intactos.
			const actualizado: CatalogoDTO = await updateCatalogoAPI(id, data);
			setCatalogos((prev) => prev.map((c) => (c.id === id ? { ...c, ...actualizado } : c)));
			notificar.success(`[Catálogos] Registro #${id} actualizado`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al actualizar"));
			throw e;
		}
	};

	const searchCatalogos = async (query: string) => {
		try {
			return await searchCatalogosAPI(query);
		} catch (e) {
			return { content: [] };
		}
	};

	return {
		catalogos,
		totalRecords,
		isLoading,
		error,
		createCatalogo,
		deleteCatalogo,
		updateCatalogo,
		searchCatalogos,
		refresh: getCatalogos,
	};
}

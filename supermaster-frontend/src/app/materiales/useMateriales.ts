"use client";

import { getErrorMessage } from "@/lib/errors";
import { useState, useEffect, useCallback } from "react";
import { notificar } from "../utils/notificar";
import { MaterialDTO } from "./types";
import {
	createMaterialAPI,
	deleteMaterialAPI,
	getMaterialesAPI,
	searchMaterialesAPI,
	updateMaterialAPI,
} from "./materialesService";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

export function useMateriales(
	pageIndex: number,
	pageSize: number,
	filters: Record<string, any> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	const sortParam = sorting.length > 0
		? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,asc";
	const [materiales, setMateriales] = useState<MaterialDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);

	const getMateriales = useCallback(async () => {
		setIsLoading(true);
		setError(null);
		try {
			const json: PageResponse<MaterialDTO> = await getMaterialesAPI(
				pageIndex,
				pageSize,
				filters,
				sortParam,
			);
			setMateriales(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch (err: unknown) {
			setError(getErrorMessage(err, "Error desconocido"));
			setMateriales([]);
		} finally {
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, JSON.stringify(filters), sortParam]);

	useEffect(() => {
		getMateriales();
	}, [getMateriales]);

	const createMaterial = async (material: string) => {
		try {
			const result = await createMaterialAPI(material, "FORM");
			await getMateriales();
			notificar.success(`[Materiales] Registro #${result.id} creado`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al crear"));
			throw e;
		}
	};

	const deleteMaterial = async (ids: number[]) => {
		try {
			const promesas = ids.map((id) => deleteMaterialAPI(id, "TABLE"));
			await Promise.all(promesas);
			await getMateriales();
			notificar.success(ids.length === 1 ? `[Materiales] Registro #${ids[0]} eliminado` : `[Materiales] ${ids.length} registros eliminados`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al eliminar"));
			throw e;
		}
	};

	const updateMaterial = async (
		id: number,
		payload: Partial<MaterialDTO>,
	) => {
		try {
			// El PATCH devuelve el objeto actualizado. Reemplazamos solo esa fila
			// en lugar de refetchar toda la página: evita el skeleton de loading
			// y mantiene scroll / selección intactos.
			const actualizado: MaterialDTO = await updateMaterialAPI(id, payload, "INLINE");
			setMateriales((prev) => prev.map((m) => (m.id === id ? { ...m, ...actualizado } : m)));
			notificar.success(`[Materiales] Registro #${id} actualizado`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al actualizar"));
			throw e;
		}
	};

	const searchMateriales = async (query: string) => {
		try {
			return await searchMaterialesAPI(query);
		} catch (e) {
			return { content: [] };
		}
	};

	return {
		materiales,
		totalRecords,
		isLoading,
		error,
		createMaterial,
		deleteMaterial,
		updateMaterial,
		searchMateriales,
		refresh: getMateriales,
	};
}

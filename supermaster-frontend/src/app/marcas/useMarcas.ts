"use client";
import { useState, useEffect, useCallback } from "react";
import { notificar } from "../utils/notificar";
import { MarcaDTO } from "./types";
import {
	createMarcaAPI,
	deleteMarcaAPI,
	getMarcasAPI,
	searchMarcasAPI,
	updateMarcaAPI,
} from "./marcasService";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

export function useMarcas(
	pageIndex: number,
	pageSize: number,
	filters: Record<string, any> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	const sortParam = sorting.length > 0
		? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,asc";
	const [marcas, setMarcas] = useState<MarcaDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);

	const getMarcas = useCallback(async () => {
		setIsLoading(true);
		setError(null);
		try {
			const json: PageResponse<MarcaDTO> = await getMarcasAPI(
				pageIndex,
				pageSize,
				filters,
				sortParam,
			);
			setMarcas(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch (err: any) {
			setError(err.message || "Error desconocido");
			setMarcas([]);
		} finally {
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, JSON.stringify(filters), sortParam]);

	useEffect(() => {
		getMarcas();
	}, [getMarcas]);

	const pageCount = totalRecords > 0 ? Math.ceil(totalRecords / pageSize) : 1;

	const createMarca = async (nombre: string, padreId: number | null) => {
		try {
			const result = await createMarcaAPI(nombre, padreId, "FORM"); // Llama al Service
			await getMarcas(); // Refresca la tabla solo
			notificar.success(`[Marcas] Registro #${result.id} creado`);
			return true;
		} catch (e: any) {
			notificar.error(e?.message || "Error al crear");
			throw e;
		}
	};
	const deleteMarca = async (ids: number[]) => {
		try {
			// Disparamos todos los borrados en paralelo (motos simultáneas)
			const promesas = ids.map((id) => deleteMarcaAPI(id, "TABLE"));
			await Promise.all(promesas);

			await getMarcas(); // Refresca la tabla solo
			notificar.success(ids.length === 1 ? `[Marcas] Registro #${ids[0]} eliminado` : `[Marcas] ${ids.length} registros eliminados`);
			return true;
		} catch (e: any) {
			notificar.error(e?.message || "Error al eliminar");
			throw e;
		}
	};
	const updateMarca = async (
		id: number,
		data: Partial<Pick<MarcaDTO, "nombre" | "padreId">>,
	) => {
		try {
			await updateMarcaAPI(id, data, "INLINE");

			await getMarcas(); // Refresca la tabla solo
			notificar.success(`[Marcas] Registro #${id} actualizado`);
			return true;
		} catch (e: any) {
			notificar.error(e?.message || "Error al actualizar");
			throw e;
		}
	};
	const searchMarcas = async (query: string) => {
		try {
			return await searchMarcasAPI(query);
		} catch (e) {
			return { content: [] }; // Devolvemos vacío si falla para no romper el select
		}
	};

	return {
		marcas,
		totalRecords,
		isLoading,
		error,
		pageCount,
		createMarca,
		deleteMarca,
		updateMarca,
		searchMarcas,
		refresh: getMarcas,
	};
}

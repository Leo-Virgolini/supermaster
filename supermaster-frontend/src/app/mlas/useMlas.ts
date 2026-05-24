"use client";

import { useState, useEffect, useCallback } from "react";
import { notificar } from "../utils/notificar";
import {
	MlaDTO,
	getMlasAPI,
	getMlaByIdAPI,
	createMlaAPI,
	updateMlaAPI,
	deleteMlaAPI,
} from "./mlasService";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

export function useMlas(
	pageIndex: number,
	pageSize: number,
	filters: Record<string, any> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	const sortParam = sorting.length > 0
		? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,asc";
	const [mlas, setMlas] = useState<MlaDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);

	const getMlas = useCallback(async (silent = false) => {
		if (!silent) setIsLoading(true);
		setError(null);
		try {
			const json: PageResponse<MlaDTO> = await getMlasAPI(
				pageIndex,
				pageSize,
				filters,
				sortParam,
			);
			setMlas(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch (err: any) {
			setError(err.message || "Error desconocido");
			setMlas([]);
		} finally {
			if (!silent) setIsLoading(false);
		}
	}, [pageIndex, pageSize, JSON.stringify(filters), sortParam]);

	useEffect(() => {
		getMlas();
	}, [getMlas]);

	const createMla = async (data: Omit<MlaDTO, "id" | "fechaCalculoEnvio" | "fechaCalculoComision">) => {
		try {
			const result = await createMlaAPI(data);
			await getMlas();
			notificar.success(`[MLAs] Registro #${result.id} creado`);
			return true;
		} catch (e: any) {
			notificar.error(e?.message || "Error al crear");
			throw e;
		}
	};

	const deleteMla = async (ids: number[]) => {
		try {
			const promesas = ids.map((id) => deleteMlaAPI(id));
			await Promise.all(promesas);
			await getMlas();
			notificar.success(ids.length === 1 ? `[MLAs] Registro #${ids[0]} eliminado` : `[MLAs] ${ids.length} registros eliminados`);
			return true;
		} catch (e: any) {
			notificar.error(e?.message || "Error al eliminar");
			throw e;
		}
	};

	const updateMla = async (id: number, data: Partial<Omit<MlaDTO, "id" | "fechaCalculoEnvio" | "fechaCalculoComision">>) => {
		try {
			// El PATCH devuelve el objeto actualizado. Reemplazamos solo esa fila
			// en lugar de refetchar toda la página: evita el skeleton de loading
			// y mantiene scroll / selección intactos.
			const actualizado: MlaDTO = await updateMlaAPI(id, data);
			setMlas((prev) => prev.map((m) => (m.id === id ? { ...m, ...actualizado } : m)));
			notificar.success(`[MLAs] Registro #${id} actualizado`);
			return true;
		} catch (e: any) {
			notificar.error(e?.message || "Error al actualizar");
			throw e;
		}
	};

	const refreshMlaLocal = useCallback(async (id: number) => {
		try {
			const fresh = await getMlaByIdAPI(id);
			setMlas((prev) => prev.map((m) => (m.id === id ? fresh : m)));
		} catch {
			// Si falla el refresh puntual, no reventamos la UI: el usuario sigue viendo
			// la fila con los datos previos. Un refresh global lo corregiría.
		}
	}, []);

	return {
		mlas,
		totalRecords,
		isLoading,
		error,
		createMla,
		deleteMla,
		updateMla,
		getMlas, // Exportamos por si hace falta recargar manual
		refreshMlaLocal, // Refresca una sola fila sin recargar la tabla entera
	};
}

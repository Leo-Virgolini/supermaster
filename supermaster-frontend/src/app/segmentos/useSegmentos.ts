"use client";
import { getErrorMessage } from "@/lib/errors";
import { useState, useEffect, useCallback, useRef } from "react";
import { notificar } from "../utils/notificar";
import {
	SegmentoDTO,
	getSegmentosAPI,
	createSegmentoAPI,
	updateSegmentoAPI,
	deleteSegmentoAPI,
} from "./segmentosService";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

export function useSegmentos(
	pageIndex: number,
	pageSize: number,
	filters: Record<string, any> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	const sortParam = sorting.length > 0
		? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,asc";
	const [segmentos, setSegmentos] = useState<SegmentoDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);
	const latestRequestIdRef = useRef(0);

	const getSegmentos = useCallback(async () => {
		const requestId = ++latestRequestIdRef.current;
		setIsLoading(true);
		try {
			const json: PageResponse<SegmentoDTO> = await getSegmentosAPI(
				pageIndex,
				pageSize,
				filters,
				sortParam,
			);
			if (latestRequestIdRef.current !== requestId) return;
			setSegmentos(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch (err) {
			if (latestRequestIdRef.current !== requestId) return;
			setSegmentos([]);
		} finally {
			if (latestRequestIdRef.current !== requestId) return;
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, JSON.stringify(filters), sortParam]);

	useEffect(() => {
		getSegmentos();
	}, [getSegmentos]);

	const createSegmento = async (data: Omit<SegmentoDTO, "id">) => {
		try {
			const result = await createSegmentoAPI(data, "FORM");
			await getSegmentos();
			notificar.success(`[Segmentos] Registro #${result.id} creado`);
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al crear"));
			throw e;
		}
	};

	const updateSegmento = async (id: number, data: Partial<Omit<SegmentoDTO, "id">>) => {
		try {
			// El PATCH devuelve el objeto actualizado. Reemplazamos solo esa fila
			// en lugar de refetchar toda la página: evita el skeleton de loading
			// y mantiene scroll / selección intactos.
			const actualizado: SegmentoDTO = await updateSegmentoAPI(id, data, "INLINE");
			setSegmentos((prev) => prev.map((s) => (s.id === id ? { ...s, ...actualizado } : s)));
			notificar.success(`[Segmentos] Registro #${id} actualizado`);
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al actualizar"));
			throw e;
		}
	};

	const deleteSegmento = async (ids: number[]) => {
		try {
			await Promise.all(ids.map((id) => deleteSegmentoAPI(id, "TABLE")));
			await getSegmentos();
			notificar.success(ids.length === 1 ? `[Segmentos] Registro #${ids[0]} eliminado` : `[Segmentos] ${ids.length} registros eliminados`);
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al eliminar"));
			throw e;
		}
	};

	return {
		segmentos,
		totalRecords,
		isLoading,
		createSegmento,
		updateSegmento,
		deleteSegmento,
	};
}

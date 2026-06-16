"use client";
import { getErrorMessage } from "@/lib/errors";
import { useState, useEffect, useCallback, useRef } from "react";
import { notificar } from "../utils/notificar";
import { CampaniaDTO, SincronizacionResultadoDTO } from "./types";
import { getCampaniasAPI, updateCampaniaAPI, sincronizarCampaniasAPI } from "./campaniasService";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

export function useCampanias(
	pageIndex: number,
	pageSize: number,
	filters: Record<string, any> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	const sortParam = sorting.length > 0
		? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,desc";
	const [campanias, setCampanias] = useState<CampaniaDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);
	const [isSyncing, setIsSyncing] = useState(false);
	const latestRequestIdRef = useRef(0);

	const getCampanias = useCallback(async () => {
		const requestId = ++latestRequestIdRef.current;
		setIsLoading(true);
		setError(null);
		try {
			const json: PageResponse<CampaniaDTO> = await getCampaniasAPI(pageIndex, pageSize, filters, sortParam);
			if (latestRequestIdRef.current !== requestId) return;
			setCampanias(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch (err: unknown) {
			if (latestRequestIdRef.current !== requestId) return;
			setError(getErrorMessage(err, "Error desconocido"));
			setCampanias([]);
		} finally {
			if (latestRequestIdRef.current !== requestId) return;
			setIsLoading(false);
		}
	// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [pageIndex, pageSize, JSON.stringify(filters), sortParam]);

	useEffect(() => {
		getCampanias();
	}, [getCampanias]);

	const pageCount = totalRecords > 0 ? Math.ceil(totalRecords / pageSize) : 1;

	const updateCampania = async (
		id: number,
		data: Partial<Pick<CampaniaDTO, "fechaDesde" | "fechaHasta" | "activa" | "observaciones">>,
	) => {
		try {
			const actualizado: CampaniaDTO = await updateCampaniaAPI(id, data, "INLINE");
			setCampanias((prev) => prev.map((c) => (c.id === id ? { ...c, ...actualizado } : c)));
			notificar.success(`[Campañas] Registro #${id} actualizado`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al actualizar"));
			throw e;
		}
	};

	const sincronizar = async () => {
		setIsSyncing(true);
		try {
			const r: SincronizacionResultadoDTO = await sincronizarCampaniasAPI("API");
			await getCampanias();
			const sinMatch = r.skusSinMatch.length;
			notificar.success(
				`[Campañas] Sincronizado: ${r.categoriasImportadas} categorías, ${r.productosVinculados} productos` +
				(sinMatch > 0 ? `. ${sinMatch} SKU(s) sin match en la BD.` : "."),
			);
			return r;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al sincronizar"));
			throw e;
		} finally {
			setIsSyncing(false);
		}
	};

	return {
		campanias,
		totalRecords,
		isLoading,
		error,
		pageCount,
		isSyncing,
		updateCampania,
		sincronizar,
		refresh: getCampanias,
	};
}

"use client";
import { useState, useEffect, useCallback } from "react";
import { notificar } from "../utils/notificar";
import {
	ConceptoGastoDTO,
	getConceptosGastoAPI,
	createConceptoGastoAPI,
	updateConceptoGastoAPI,
	deleteConceptoGastoAPI,
} from "./conceptosGastosService";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

export function useConceptosGasto(
	pageIndex: number,
	pageSize: number,
	filters: Record<string, any> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	const sortParam = sorting.length > 0
		? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,asc";
	const [conceptos, setConceptos] = useState<ConceptoGastoDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);

	const getConceptos = useCallback(async () => {
		setIsLoading(true);
		try {
			const json: PageResponse<ConceptoGastoDTO> =
				await getConceptosGastoAPI(pageIndex, pageSize, filters, sortParam);
			setConceptos(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch (err) {
			setConceptos([]);
		} finally {
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, JSON.stringify(filters), sortParam]);

	useEffect(() => {
		getConceptos();
	}, [getConceptos]);

	const createConcepto = async (
		data: Omit<ConceptoGastoDTO, "id" | "etapa" | "naturaleza"> & { naturaleza?: ConceptoGastoDTO["naturaleza"] | null },
	) => {
		try {
			const result = await createConceptoGastoAPI(data, "FORM");
			await getConceptos();
			notificar.success(`[Conceptos Cálculo] Registro #${result.id} creado`);
		} catch (e: any) {
			notificar.error(e?.message || "Error al crear");
			throw e;
		}
	};

	const updateConcepto = async (
		id: number,
		data: Partial<ConceptoGastoDTO>,
	) => {
		try {
			// El PATCH devuelve el objeto actualizado. Reemplazamos solo esa fila
			// en lugar de refetchar toda la página: evita el skeleton de loading
			// y mantiene scroll / selección intactos.
			const actualizado: ConceptoGastoDTO = await updateConceptoGastoAPI(id, data, "INLINE");
			setConceptos((prev) => prev.map((c) => (c.id === id ? { ...c, ...actualizado } : c)));
			notificar.success(`[Conceptos Cálculo] Registro #${id} actualizado`);
		} catch (e: any) {
			notificar.error(e?.message || "Error al actualizar");
			throw e;
		}
	};

	const deleteConcepto = async (ids: number[]) => {
		try {
			await Promise.all(ids.map((id) => deleteConceptoGastoAPI(id, "TABLE")));
			await getConceptos();
			notificar.success(ids.length === 1 ? `[Conceptos Cálculo] Registro #${ids[0]} eliminado` : `[Conceptos Cálculo] ${ids.length} registros eliminados`);
		} catch (e: any) {
			notificar.error(e?.message || "Error al eliminar");
			throw e;
		}
	};

	return {
		conceptos,
		totalRecords,
		isLoading,
		createConcepto,
		updateConcepto,
		deleteConcepto,
	};
}

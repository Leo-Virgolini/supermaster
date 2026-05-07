"use client";
import { useState, useEffect, useCallback } from "react";
import { notificar } from "../utils/notificar";
import { getReglasAPI, createReglaAPI, updateReglaAPI, deleteReglaAPI } from "./reglasDescuentoService";
import { ReglaDescuentoDTO } from "./types";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

export function useReglasDescuento(pageIndex: number, pageSize: number, filters: Record<string, any> = {}, sorting: { id: string; desc: boolean }[] = []) {
	const SORT_MAP: Record<string, string> = { canalId: "canal.nombre" };
	const sortParam = sorting.length > 0
		? `${SORT_MAP[sorting[0].id] || sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,asc";
	const [reglas, setReglas] = useState<ReglaDescuentoDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);

	const getReglas = useCallback(async () => {
		setIsLoading(true);
		try {
			const json: PageResponse<ReglaDescuentoDTO> = await getReglasAPI(pageIndex, pageSize, filters, sortParam);
			setReglas(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch (err) {
			setReglas([]);
		} finally {
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, JSON.stringify(filters), sortParam]);

	useEffect(() => {
		getReglas();
	}, [getReglas]);

	const createRegla = async (data: Omit<ReglaDescuentoDTO, "id">) => {
		try {
			const result = await createReglaAPI(data);
			await getReglas();
			notificar.success(`[Reglas Descuento] Registro #${result.id} creado`);
			notificar.info("Los precios del canal se están recalculando en segundo plano...");
		} catch (e: any) {
			notificar.error(e?.message || "Error al crear");
			throw e;
		}
	};

	const updateRegla = async (id: number, data: Partial<ReglaDescuentoDTO>) => {
		try {
			await updateReglaAPI(id, data);
			await getReglas();
			notificar.success(`[Reglas Descuento] Registro #${id} actualizado`);
			notificar.info("Los precios del canal se están recalculando en segundo plano...");
		} catch (e: any) {
			notificar.error(e?.message || "Error al actualizar");
			throw e;
		}
	};

	const deleteRegla = async (ids: number[]) => {
		try {
			await Promise.all(ids.map((id) => deleteReglaAPI(id)));
			await getReglas();
			notificar.success(ids.length === 1 ? `[Reglas Descuento] Registro #${ids[0]} eliminado` : `[Reglas Descuento] ${ids.length} registros eliminados`);
			notificar.info("Los precios del canal se están recalculando en segundo plano...");
		} catch (e: any) {
			notificar.error(e?.message || "Error al eliminar");
			throw e;
		}
	};

	return {
		reglas,
		totalRecords,
		isLoading,
		createRegla,
		updateRegla,
		deleteRegla,
	};
}

"use client";
import { getErrorMessage } from "@/lib/errors";
import { useState, useEffect, useCallback, useRef } from "react";
import { notificar } from "../../utils/notificar";
import { CampaniaDTO, CampaniaProductoDTO } from "../types";
import { getCampaniaAPI, getCampaniaProductosAPI, updateCampaniaPrecioAPI } from "../campaniasService";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

export function useCampaniaDetalle(
	campaniaId: number,
	pageIndex: number,
	pageSize: number,
	sorting: { id: string; desc: boolean }[] = [],
) {
	const sortParam = sorting.length > 0
		? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,asc";
	const [campania, setCampania] = useState<CampaniaDTO | null>(null);
	const [productos, setProductos] = useState<CampaniaProductoDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);
	const latestRequestIdRef = useRef(0);

	const cargar = useCallback(async () => {
		const requestId = ++latestRequestIdRef.current;
		setIsLoading(true);
		setError(null);
		try {
			const [cab, page]: [CampaniaDTO, PageResponse<CampaniaProductoDTO>] = await Promise.all([
				getCampaniaAPI(campaniaId),
				getCampaniaProductosAPI(campaniaId, pageIndex, pageSize, sortParam),
			]);
			if (latestRequestIdRef.current !== requestId) return;
			setCampania(cab);
			setProductos(page.content || []);
			setTotalRecords(page.page?.totalElements || 0);
		} catch (err: unknown) {
			if (latestRequestIdRef.current !== requestId) return;
			setError(getErrorMessage(err, "Error desconocido"));
		} finally {
			if (latestRequestIdRef.current !== requestId) return;
			setIsLoading(false);
		}
	}, [campaniaId, pageIndex, pageSize, sortParam]);

	useEffect(() => {
		cargar();
	}, [cargar]);

	const pageCount = totalRecords > 0 ? Math.ceil(totalRecords / pageSize) : 1;

	const updatePrecio = async (campaniaProductoId: number, precio: number | null) => {
		try {
			const actualizado: CampaniaProductoDTO = await updateCampaniaPrecioAPI(campaniaProductoId, precio, "INLINE");
			setProductos((prev) => prev.map((p) => (p.id === campaniaProductoId ? { ...p, ...actualizado } : p)));
			notificar.success(`[Campañas] Precio actualizado`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al actualizar el precio"));
			throw e;
		}
	};

	return { campania, productos, totalRecords, isLoading, error, pageCount, updatePrecio, refresh: cargar };
}

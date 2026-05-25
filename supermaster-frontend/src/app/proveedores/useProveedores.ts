"use client";
import { getErrorMessage } from "@/lib/errors";
import { useState, useEffect, useCallback } from "react";
import { notificar } from "../utils/notificar";
import {
	ProveedorDTO,
	getProveedoresAPI,
	createProveedorAPI,
	updateProveedorAPI,
	deleteProveedorAPI,
} from "./proveedoresService";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

export function useProveedores(
	pageIndex: number,
	pageSize: number,
	filters: Record<string, any> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	const sortParam = sorting.length > 0
		? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,asc";
	const [proveedores, setProveedores] = useState<ProveedorDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);

	const getProveedores = useCallback(async () => {
		setIsLoading(true);
		try {
			const json: PageResponse<ProveedorDTO> = await getProveedoresAPI(
				pageIndex,
				pageSize,
				filters,
				sortParam,
			);
			setProveedores(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch (err) {
			setProveedores([]);
		} finally {
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, JSON.stringify(filters), sortParam]);

	useEffect(() => {
		getProveedores();
	}, [getProveedores]);

	const createProveedor = async (data: Omit<ProveedorDTO, "id">) => {
		try {
			const result = await createProveedorAPI(data, "FORM");
			await getProveedores();
			notificar.success(`[Proveedores] Registro #${result.id} creado`);
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al crear"));
			throw e;
		}
	};

	const updateProveedor = async (id: number, data: Partial<Omit<ProveedorDTO, "id">>) => {
		try {
			// El PATCH devuelve el objeto actualizado. Reemplazamos solo esa fila
			// en lugar de refetchar toda la página: evita el skeleton de loading
			// y mantiene scroll / selección intactos.
			const actualizado: ProveedorDTO = await updateProveedorAPI(id, data, "INLINE");
			setProveedores((prev) => prev.map((p) => (p.id === id ? { ...p, ...actualizado } : p)));
			notificar.success(`[Proveedores] Registro #${id} actualizado`);
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al actualizar"));
			throw e;
		}
	};

	const deleteProveedor = async (ids: number[]) => {
		try {
			await Promise.all(ids.map((id) => deleteProveedorAPI(id, "TABLE")));
			await getProveedores();
			notificar.success(ids.length === 1 ? `[Proveedores] Registro #${ids[0]} eliminado` : `[Proveedores] ${ids.length} registros eliminados`);
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al eliminar"));
			throw e;
		}
	};

	return {
		proveedores,
		totalRecords,
		isLoading,
		createProveedor,
		updateProveedor,
		deleteProveedor,
	};
}

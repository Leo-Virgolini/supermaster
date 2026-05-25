"use client";
import { getErrorMessage } from "@/lib/errors";
import { useState, useEffect, useCallback } from "react";
import { notificar } from "../utils/notificar";
import {
	ClienteDTO,
	getClientesAPI,
	createClienteAPI,
	updateClienteAPI,
	deleteClienteAPI,
} from "./clientesService";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

export function useClientes(
	pageIndex: number,
	pageSize: number,
	filters: Record<string, any> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	const sortParam = sorting.length > 0
		? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,asc";
	const [clientes, setClientes] = useState<ClienteDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);

	const getClientes = useCallback(async () => {
		setIsLoading(true);
		try {
			const json: PageResponse<ClienteDTO> = await getClientesAPI(
				pageIndex,
				pageSize,
				filters,
				sortParam,
			);
			setClientes(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch (err) {
			setClientes([]);
		} finally {
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, JSON.stringify(filters), sortParam]);

	useEffect(() => {
		getClientes();
	}, [getClientes]);

	const createCliente = async (data: Omit<ClienteDTO, "id">) => {
		try {
			const result = await createClienteAPI(data, "FORM");
			await getClientes();
			notificar.success(`[Clientes] Registro #${result.id} creado`);
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al crear"));
			throw e;
		}
	};

	const updateCliente = async (id: number, data: Partial<Omit<ClienteDTO, "id">>) => {
		try {
			// El PATCH devuelve el objeto actualizado. Reemplazamos solo esa fila
			// en lugar de refetchar toda la página: evita el skeleton de loading
			// y mantiene scroll / selección intactos.
			const actualizado: ClienteDTO = await updateClienteAPI(id, data, "INLINE");
			setClientes((prev) => prev.map((c) => (c.id === id ? { ...c, ...actualizado } : c)));
			notificar.success(`[Clientes] Registro #${id} actualizado`);
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al actualizar"));
			throw e;
		}
	};

	const deleteCliente = async (ids: number[]) => {
		try {
			await Promise.all(ids.map((id) => deleteClienteAPI(id, "TABLE")));
			await getClientes();
			notificar.success(ids.length === 1 ? `[Clientes] Registro #${ids[0]} eliminado` : `[Clientes] ${ids.length} registros eliminados`);
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al eliminar"));
			throw e;
		}
	};

	return {
		clientes,
		totalRecords,
		isLoading,
		createCliente,
		updateCliente,
		deleteCliente,
	};
}

"use client";

import { getErrorMessage } from "@/lib/errors";
import { useState, useEffect, useCallback, useRef } from "react";
import { notificar } from "../utils/notificar";
import { OrigenDTO } from "./types";

import {
	createOrigenAPI,
	deleteOrigenAPI,
	getOrigenesAPI,
	searchOrigenesAPI,
	updateOrigenAPI,
} from "./origenesService";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

export function useOrigenes(
	pageIndex: number,
	pageSize: number,
	filters: Record<string, any> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	const sortParam = sorting.length > 0
		? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,asc";
	// --- 1. ESTADOS (La Memoria) ---
	const [origenes, setOrigenes] = useState<OrigenDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);
	const latestRequestIdRef = useRef(0);

	// --- 2. FUNCIÓN DE CARGA (El Refresco) ---
	const getOrigenes = useCallback(async () => {
		const requestId = ++latestRequestIdRef.current;
		setIsLoading(true);
		setError(null);
		try {
			const json: PageResponse<OrigenDTO> = await getOrigenesAPI(
				pageIndex,
				pageSize,
				filters,
				sortParam,
			);
			if (latestRequestIdRef.current !== requestId) return;
			setOrigenes(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch (err: unknown) {
			if (latestRequestIdRef.current !== requestId) return;
			setError(getErrorMessage(err, "Error desconocido"));
			setOrigenes([]);
		} finally {
			if (latestRequestIdRef.current !== requestId) return;
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, JSON.stringify(filters), sortParam]);

	// Efecto automático: Cargar al inicio o al cambiar de página
	useEffect(() => {
		getOrigenes();
	}, [getOrigenes]);

	// --- 3. ACCIONES (Lo que puede hacer el usuario) ---
	// A. CREAR
	const createOrigen = async (origen: string) => {
		try {
			const result = await createOrigenAPI(origen, "FORM"); // Llama al Service
			await getOrigenes(); // Refresca la tabla solo
			notificar.success(`[Orígenes] Registro #${result.id} creado`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al crear"));
			throw e;
		}
	};

	// B. BORRAR (Masivo)
	const deleteOrigen = async (ids: number[]) => {
		try {
			// Disparamos todos los borrados en paralelo (motos simultáneas)
			const promesas = ids.map((id) => deleteOrigenAPI(id, "TABLE"));
			await Promise.all(promesas);

			await getOrigenes(); // Refresca la tabla solo
			notificar.success(ids.length === 1 ? `[Orígenes] Registro #${ids[0]} eliminado` : `[Orígenes] ${ids.length} registros eliminados`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al eliminar"));
			throw e;
		}
	};

	// C. ACTUALIZAR
	const updateOrigen = async (
		id: number,
		payload: Partial<OrigenDTO>,
	) => {
		try {
			// El PATCH devuelve el objeto actualizado. Reemplazamos solo esa fila
			// en lugar de refetchar toda la página: evita el skeleton de loading
			// y mantiene scroll / selección intactos.
			const actualizado: OrigenDTO = await updateOrigenAPI(id, payload, "INLINE");
			setOrigenes((prev) => prev.map((o) => (o.id === id ? { ...o, ...actualizado } : o)));
			notificar.success(`[Orígenes] Registro #${id} actualizado`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al actualizar"));
			throw e;
		}
	};

	// D. ACCIÓN: Buscar Orígenes (Para selects si fuera necesario)
	const searchOrigenes = async (query: string) => {
		try {
			return await searchOrigenesAPI(query);
		} catch (e) {
			console.warn("[Orígenes] Error en búsqueda:", e);
			return { content: [] };
		}
	};

	// Retornamos todo lo que la Pantalla necesita
	return {
		origenes,
		totalRecords,
		isLoading,
		error,
		createOrigen,
		deleteOrigen,
		updateOrigen,
		searchOrigenes,
		refresh: getOrigenes,
	};
}

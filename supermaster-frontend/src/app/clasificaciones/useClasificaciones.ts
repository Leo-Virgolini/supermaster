"use client";

import { getErrorMessage } from "@/lib/errors";
import { useState, useEffect, useCallback, useRef } from "react";
import { notificar } from "../utils/notificar";
import { ClasificacionDTO } from "./types";

import {
	createClasificacionAPI,
	deleteClasificacionAPI,
	getClasificacionesAPI,
	searchClasificacionesAPI,
	updateClasificacionAPI,
} from "./clasificacionesService";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

export function useClasificaciones(
	pageIndex: number,
	pageSize: number,
	filters: Record<string, any> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	const sortParam = sorting.length > 0
		? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,asc";
	// --- 1. ESTADOS (La Memoria) ---
	const [clasificaciones, setClasificaciones] = useState<ClasificacionDTO[]>(
		[],
	);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);
	const latestRequestIdRef = useRef(0);

	// --- 2. FUNCIÓN DE CARGA (El Refresco) ---
	const getClasificaciones = useCallback(async () => {
		const requestId = ++latestRequestIdRef.current;
		setIsLoading(true);
		setError(null);
		try {
			const json: PageResponse<ClasificacionDTO> =
				await getClasificacionesAPI(pageIndex, pageSize, filters, sortParam);
			if (latestRequestIdRef.current !== requestId) return;
			setClasificaciones(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch (err: unknown) {
			if (latestRequestIdRef.current !== requestId) return;
			setError(getErrorMessage(err, "Error desconocido"));
			setClasificaciones([]);
		} finally {
			if (latestRequestIdRef.current !== requestId) return;
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, JSON.stringify(filters), sortParam]);

	// Efecto automático: Cargar al inicio o al cambiar de página
	useEffect(() => {
		getClasificaciones();
	}, [getClasificaciones]);

	// --- 3. ACCIONES (Lo que puede hacer el usuario) ---
	// A. CREAR
	const createClasificacion = async (
		nombre: string,
		padreId: number | null,
		idDux: number | null = null,
	) => {
		try {
			const result = await createClasificacionAPI(nombre, padreId, idDux, "FORM"); // Llama al Service
			await getClasificaciones(); // Refresca la tabla solo
			notificar.success(`[Clasificaciones] Registro #${result.id} creado`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al crear"));
			throw e;
		}
	};

	// B. BORRAR (Masivo)
	const deleteClasificacion = async (ids: number[]) => {
		try {
			// Disparamos todos los borrados en paralelo (motos simultáneas)
			const promesas = ids.map((id) => deleteClasificacionAPI(id, "TABLE"));
			await Promise.all(promesas);

			await getClasificaciones(); // Refresca la tabla solo
			notificar.success(ids.length === 1 ? `[Clasificaciones] Registro #${ids[0]} eliminado` : `[Clasificaciones] ${ids.length} registros eliminados`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al eliminar"));
			throw e;
		}
	};

	// C. ACTUALIZAR
	const updateClasificacion = async (
		id: number,
		data: Partial<Pick<ClasificacionDTO, "nombre" | "padreId" | "idDux">>,
	) => {
		try {
			// El PATCH devuelve el objeto actualizado. Reemplazamos solo esa fila
			// en lugar de refetchar toda la página: evita el skeleton de loading
			// y mantiene scroll / selección intactos.
			const actualizado: ClasificacionDTO = await updateClasificacionAPI(id, data, "INLINE");
			setClasificaciones((prev) => prev.map((c) => (c.id === id ? { ...c, ...actualizado } : c)));
			notificar.success(`[Clasificaciones] Registro #${id} actualizado`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al actualizar"));
			throw e;
		}
	};

	// D. ACCIÓN: Buscar Marcas (Para el AsyncSelect)
	// No necesita tocar el estado 'marcas' de la tabla, solo actúa de puente.
	const searchClasificaciones = async (query: string) => {
		try {
			return await searchClasificacionesAPI(query);
		} catch (e) {
			console.warn("[Clasificaciones] Error en búsqueda:", e);
			return { content: [] }; // Devolvemos vacío si falla para no romper el select
		}
	};

	// Retornamos todo lo que la Pantalla necesita
	return {
		clasificaciones,
		totalRecords,
		isLoading,
		error,
		createClasificacion,
		deleteClasificacion,
		updateClasificacion,
		searchClasificaciones,
		refresh: getClasificaciones,
	};
}

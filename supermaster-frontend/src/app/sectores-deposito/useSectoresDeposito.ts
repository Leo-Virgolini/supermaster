"use client";

import { getErrorMessage } from "@/lib/errors";
import { useState, useEffect, useCallback, useRef } from "react";
import { notificar } from "../utils/notificar";
import { SectorDepositoCreateDTO, SectorDepositoDTO, SectorDepositoPatchDTO } from "./types";

import {
	createSectorDepositoAPI,
	deleteSectorDepositoAPI,
	getSectoresDepositoAPI,
	searchSectoresDepositoAPI,
	updateSectorDepositoAPI,
} from "./sectoresDepositoService";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

export function useSectoresDeposito(
	pageIndex: number,
	pageSize: number,
	filters: Record<string, any> = {},
	sorting: { id: string; desc: boolean }[] = [],
) {
	const sortParam = sorting.length > 0
		? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
		: "id,asc";
	// --- 1. ESTADOS (La Memoria) ---
	const [sectores, setSectores] = useState<SectorDepositoDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);
	const latestRequestIdRef = useRef(0);

	// --- 2. FUNCIÓN DE CARGA (El Refresco) ---
	const getSectores = useCallback(async () => {
		const requestId = ++latestRequestIdRef.current;
		setIsLoading(true);
		setError(null);
		try {
			const json: PageResponse<SectorDepositoDTO> = await getSectoresDepositoAPI(
				pageIndex,
				pageSize,
				filters,
				sortParam,
			);
			if (latestRequestIdRef.current !== requestId) return;
			setSectores(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch (err: unknown) {
			if (latestRequestIdRef.current !== requestId) return;
			setError(getErrorMessage(err, "Error desconocido"));
			setSectores([]);
		} finally {
			if (latestRequestIdRef.current !== requestId) return;
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, JSON.stringify(filters), sortParam]);

	// Efecto automático: Cargar al inicio o al cambiar de página
	useEffect(() => {
		getSectores();
	}, [getSectores]);

	// --- 3. ACCIONES (Lo que puede hacer el usuario) ---
	// A. CREAR
	const createSector = async (data: SectorDepositoCreateDTO) => {
		try {
			const result = await createSectorDepositoAPI(data, "FORM"); // Llama al Service
			await getSectores(); // Refresca la tabla solo
			notificar.success(`[Sectores de depósito] Registro #${result.id} creado`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al crear"));
			throw e;
		}
	};

	// B. BORRAR (Masivo)
	const deleteSector = async (ids: number[]) => {
		try {
			// Disparamos todos los borrados en paralelo (motos simultáneas)
			const promesas = ids.map((id) => deleteSectorDepositoAPI(id, "TABLE"));
			await Promise.all(promesas);

			await getSectores(); // Refresca la tabla solo
			notificar.success(ids.length === 1 ? `[Sectores de depósito] Registro #${ids[0]} eliminado` : `[Sectores de depósito] ${ids.length} registros eliminados`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al eliminar"));
			throw e;
		}
	};

	// C. ACTUALIZAR
	const updateSector = async (
		id: number,
		payload: SectorDepositoPatchDTO,
	) => {
		try {
			// El PATCH devuelve el objeto actualizado. Reemplazamos solo esa fila
			// en lugar de refetchar toda la página: evita el skeleton de loading
			// y mantiene scroll / selección intactos.
			const actualizado: SectorDepositoDTO = await updateSectorDepositoAPI(id, payload, "INLINE");
			setSectores((prev) => prev.map((s) => (s.id === id ? { ...s, ...actualizado } : s)));
			notificar.success(`[Sectores de depósito] Registro #${id} actualizado`);
			return true;
		} catch (e: unknown) {
			notificar.error(getErrorMessage(e, "Error al actualizar"));
			throw e;
		}
	};

	// D. ACCIÓN: Buscar Sectores (Para selects si fuera necesario)
	const searchSectores = async (query: string) => {
		try {
			return await searchSectoresDepositoAPI(query);
		} catch (e) {
			console.warn("[Sectores de depósito] Error en búsqueda:", e);
			return { content: [] };
		}
	};

	// Retornamos todo lo que la Pantalla necesita
	return {
		sectores,
		totalRecords,
		isLoading,
		error,
		createSector,
		deleteSector,
		updateSector,
		searchSectores,
		refresh: getSectores,
	};
}

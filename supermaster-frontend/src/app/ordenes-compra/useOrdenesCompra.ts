"use client";
import { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { notificar } from "../utils/notificar";
import { serializeForDeps } from "../utils/serializeForDeps";
import {
  OrdenCompraCreateDTO,
  OrdenCompraDTO,
  OrdenCompraPatchDTO,
} from "./types";
import {
  getOrdenesCompraAPI,
  createOrdenCompraAPI,
  updateOrdenCompraAPI,
  deleteOrdenCompraAPI,
} from "./ordenesCompraService";

type PageResponse<T> = {
  content: T[];
  page: { totalElements: number; totalPages: number };
};

export function useOrdenesCompra(
  pageIndex: number,
  pageSize: number,
  filters: Record<string, unknown> = {},
  sorting: { id: string; desc: boolean }[] = [],
) {
  const sortParam = sorting.length > 0
      ? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
      : "id,asc";
  const filtersKey = serializeForDeps(filters);
  const requestFilters = useMemo<Record<string, unknown>>(
    () => ({ ...filters }),
    [filtersKey],
  );
  const [ordenes, setOrdenes] = useState<OrdenCompraDTO[]>([]);
  const [totalRecords, setTotalRecords] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const latestRequestIdRef = useRef(0);

  const getOrdenes = useCallback(async () => {
    const requestId = ++latestRequestIdRef.current;
    setIsLoading(true);
    setError(null);
    try {
      const json: PageResponse<OrdenCompraDTO> = await getOrdenesCompraAPI(
        pageIndex,
        pageSize,
        requestFilters,
        sortParam,
      );
      if (latestRequestIdRef.current !== requestId) return;
      setOrdenes(json.content || []);
      setTotalRecords(json.page?.totalElements || 0);
    } catch (err: unknown) {
      if (latestRequestIdRef.current !== requestId) return;
      setError(err instanceof Error ? err.message : "Error al cargar órdenes de compra");
      setOrdenes([]);
    } finally {
      if (latestRequestIdRef.current !== requestId) return;
      setIsLoading(false);
    }
  }, [pageIndex, pageSize, requestFilters, sortParam]);

  useEffect(() => {
    getOrdenes();
  }, [getOrdenes]);

  const createOrden = async (dto: OrdenCompraCreateDTO) => {
    try {
      const result = await createOrdenCompraAPI(dto);
      await getOrdenes();
      notificar.success(`[Órdenes de Compra] Registro #${result.id} creado`);
    } catch (e: unknown) {
      notificar.error(e instanceof Error ? e.message : "Error al crear");
      throw e;
    }
  };

  const updateOrden = async (id: number, dto: OrdenCompraPatchDTO) => {
    try {
      // El PATCH devuelve el objeto actualizado. Reemplazamos solo esa fila
      // en lugar de refetchar toda la página: evita el skeleton de loading
      // y mantiene scroll / selección intactos.
      const actualizado: OrdenCompraDTO = await updateOrdenCompraAPI(id, dto);
      setOrdenes((prev) => prev.map((o) => (o.id === id ? { ...o, ...actualizado } : o)));
      notificar.success(`[Órdenes de Compra] Registro #${id} actualizado`);
    } catch (e: unknown) {
      notificar.error(e instanceof Error ? e.message : "Error al actualizar");
      throw e;
    }
  };

  const deleteOrden = async (ids: number[]) => {
    try {
      await Promise.all(ids.map((id) => deleteOrdenCompraAPI(id)));
      await getOrdenes();
      notificar.success(ids.length === 1 ? `[Órdenes de Compra] Registro #${ids[0]} eliminado` : `[Órdenes de Compra] ${ids.length} registros eliminados`);
    } catch (e: unknown) {
      notificar.error(e instanceof Error ? e.message : "Error al eliminar");
      throw e;
    }
  };

  return {
    ordenes,
    totalRecords,
    isLoading,
    error,
    createOrden,
    updateOrden,
    deleteOrden,
    refresh: getOrdenes,
  };
}

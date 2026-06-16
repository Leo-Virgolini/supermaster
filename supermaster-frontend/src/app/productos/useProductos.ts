"use client";
import { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { notificar } from "../utils/notificar";
import { getProductosAPI, createProductoAPI, updateProductoAPI, deleteProductoAPI } from "./productosService";
import { updateProductoMargenAPI, ProductoMargenDTO } from "./productoMargenService";
import { ProductoCreateDTO, ProductoDTO, ProductoPatchDTO } from "./types";
import { serializeForDeps } from "../utils/serializeForDeps";

type PageResponse<T> = {
	content: T[];
	page: { totalElements: number; totalPages: number };
};

const sortFieldMapping: Record<string, string> = {
	rubro: "clasifGral",
	subrubro: "clasifGastro",
	marca: "marca",
	tipo: "tipo",
	proveedor: "proveedor",
	origen: "origen",
	material: "material",
	mlaId: "mla.mla",
};

export function useProductos(pageIndex: number, pageSize: number, filters: Record<string, unknown> = {}, sorting: { id: string; desc: boolean }[] = []) {
	const sortParam = sorting.length > 0
		? sorting.map(s => `${sortFieldMapping[s.id] || s.id},${s.desc ? "desc" : "asc"}`)
		: ["id,asc"];
	const sortKey = sortParam.join("|");
	const filtersKey = serializeForDeps(filters);
	const requestFilters = useMemo<Record<string, unknown>>(
		() => ({ ...filters }),
		[filtersKey],
	);
	const requestSortParam = useMemo(() => [...sortParam], [sortKey]);
	const [productos, setProductos] = useState<ProductoDTO[]>([]);
	const [totalRecords, setTotalRecords] = useState(0);
	const [isLoading, setIsLoading] = useState(true);
	const latestRequestIdRef = useRef(0);

	const getProductos = useCallback(async () => {
		const requestId = ++latestRequestIdRef.current;
		setIsLoading(true);
		try {
			const json: PageResponse<ProductoDTO> = await getProductosAPI(pageIndex, pageSize, requestFilters, requestSortParam);
			if (latestRequestIdRef.current !== requestId) return;
			setProductos(json.content || []);
			setTotalRecords(json.page?.totalElements || 0);
		} catch {
			if (latestRequestIdRef.current !== requestId) return;
			setProductos([]);
		} finally {
			if (latestRequestIdRef.current !== requestId) return;
			setIsLoading(false);
		}
	}, [pageIndex, pageSize, requestFilters, requestSortParam]);

	useEffect(() => {
		void getProductos();
	}, [getProductos]);

	const createProducto = async (
		data: ProductoCreateDTO,
		afterCreate?: (productoId: number) => Promise<void>,
	) => {
		try {
			const result = await createProductoAPI(data, "FORM");
			// Asociaciones (margen, catálogos, aptos, clientes) ANTES del refetch
			// para que la tabla ya las refleje al recargar.
			if (afterCreate) await afterCreate(result.id);
			await getProductos();
			notificar.success(`[Productos] Registro #${result.id} creado`);
			return result;
		} catch (e: unknown) {
			notificar.error(e instanceof Error ? e.message : "Error al crear");
			throw e;
		}
	};

	const updateProducto = async (id: number, data: ProductoPatchDTO) => {
		try {
			// El PATCH devuelve el producto actualizado. Reemplazamos solo esa fila
			// en lugar de refetchar toda la página: evita el skeleton de loading
			// y mantiene scroll / selección intactos.
			const actualizado: ProductoDTO = await updateProductoAPI(id, data, "INLINE");
			setProductos((prev) => prev.map((p) => (p.id === id ? { ...p, ...actualizado } : p)));
			notificar.success(`[Productos] Registro #${id} actualizado`);
		} catch (e: unknown) {
			notificar.error(e instanceof Error ? e.message : "Error al actualizar");
			throw e;
		}
	};

	const updateProductoMargen = async (
		id: number,
		data: Partial<Omit<ProductoMargenDTO, "id" | "productoId">>
	) => {
		try {
			// El PATCH del margen devuelve ProductoMargenDTO con los campos de
			// margen. Mergeamos solo esos campos en la fila local — el resto del
			// producto no cambia.
			const margenActualizado = await updateProductoMargenAPI(id, data);
			setProductos((prev) => prev.map((p) => (p.id === id ? {
				...p,
				margenMinorista: margenActualizado.margenMinorista,
				margenMayorista: margenActualizado.margenMayorista,
			} : p)));
			notificar.success(`[Productos] Margen #${id} actualizado`);
		} catch (e: unknown) {
			notificar.error(e instanceof Error ? e.message : "Error al actualizar margen");
			throw e;
		}
	};

	const deleteProducto = async (ids: number[]) => {
		try {
			await Promise.all(ids.map((id) => deleteProductoAPI(id, "TABLE")));
			await getProductos();
			notificar.success(ids.length === 1 ? `[Productos] Registro #${ids[0]} eliminado` : `[Productos] ${ids.length} registros eliminados`);
		} catch (e: unknown) {
			notificar.error(e instanceof Error ? e.message : "Error al eliminar");
			throw e;
		}
	};

	return {
		productos,
		totalRecords,
		isLoading,
		createProducto,
		updateProducto,
		updateProductoMargen,
		deleteProducto,
		refresh: getProductos,
	};
}

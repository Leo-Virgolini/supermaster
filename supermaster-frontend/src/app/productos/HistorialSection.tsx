"use client";

import { getErrorMessage } from "@/lib/errors";
import Link from "next/link";
import { useState, useEffect, useCallback } from "react";
import { ArrowPathIcon } from "@heroicons/react/24/outline";
import { formatFechaAR } from "../utils/formatDate";
import { getProductoAuditoriaAPI } from "./productosService";
import type { AuditoriaCambioDTO } from "../auditoria/types";

// Historial de cambios (auditoría) de un producto.
export function HistorialSection({ productoId, productoSku }: { productoId: number; productoSku: string }) {
    const [items, setItems] = useState<AuditoriaCambioDTO[]>([]);
    const [total, setTotal] = useState(0);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const load = useCallback(async () => {
        setIsLoading(true);
        setError(null);
        try {
            const response = await getProductoAuditoriaAPI(productoId, 0, 50, "fechaHora,desc");
            setItems(response.content ?? []);
            setTotal(response.page?.totalElements ?? response.content?.length ?? 0);
        } catch (e: unknown) {
            setError(getErrorMessage(e));
        } finally {
            setIsLoading(false);
        }
    }, [productoId]);

    useEffect(() => {
        void load();
    }, [load]);

    const renderValue = (value: string | null) => {
        if (value == null || value === "") return <span className="text-gray-400">—</span>;
        return <span className="break-words text-gray-700">{value}</span>;
    };

    const fieldLabelMap: Record<string, string> = {
        sku: "SKU",
        codExt: "Cód. Ext.",
        descripcion: "Descripción",
        tituloWeb: "Título web",
        esCombo: "Es combo",
        uxb: "UxB",
        moq: "MOQ",
        imagenUrl: "Imagen",
        stock: "Stock",
        activo: "Activo",
        tagReposicion: "Tag reposición",
        tag: "Tag",
        costo: "Costo",
        iva: "IVA",
        marca: "Marca",
        origen: "Origen",
        clasifGral: "Clasif. general",
        clasifGastro: "Clasif. gastro",
        tipo: "Tipo",
        proveedor: "Proveedor",
        material: "Material",
        mla: "MLA",
        capacidad: "Capacidad",
        largo: "Largo",
        ancho: "Ancho",
        alto: "Alto",
        diamboca: "Diam. boca",
        diambase: "Diam. base",
        espesor: "Espesor",
    };

    const actionLabelMap: Record<string, string> = {
        CREATE: "Alta",
        UPDATE: "Edición",
        DELETE: "Baja",
    };

    if (isLoading) return <p className="text-sm text-gray-400 text-center py-8">Cargando historial...</p>;

    return (
        <div className="flex flex-col gap-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex flex-col gap-1">
                    <div className="text-xs text-gray-500">
                        {total > items.length ? `Mostrando los últimos ${items.length} cambios de ${total}.` : `${items.length} cambio(s) registrados.`}
                    </div>
                    <div className="text-xs text-slate-400">
                        Esta vista sirve como contexto rápido del producto. Para investigar más, conviene usar la auditoría global.
                    </div>
                </div>
                <div className="flex items-center gap-2">
                    <Link
                        href={`/auditoria?entidad=PRODUCTO&search=${encodeURIComponent(productoSku)}`}
                        className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 transition hover:bg-slate-50"
                    >
                        Ver en auditoría global
                    </Link>
                    <button
                        onClick={() => void load()}
                        className="inline-flex items-center gap-1.5 rounded-lg border border-blue-200 bg-blue-50 px-3 py-1.5 text-xs font-medium text-blue-700 transition hover:bg-blue-100"
                    >
                        <ArrowPathIcon className="h-3.5 w-3.5" />
                        Actualizar
                    </button>
                </div>
            </div>

            {error && <div className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div>}

            {!error && items.length === 0 ? (
                <div className="flex flex-col items-center gap-3 py-8 text-center">
                    <p className="text-sm text-gray-400">Todavía no hay cambios auditados para este producto.</p>
                    <Link
                        href={`/auditoria?entidad=PRODUCTO&search=${encodeURIComponent(productoSku)}`}
                        className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 transition hover:bg-slate-50"
                    >
                        Abrir auditoría global
                    </Link>
                </div>
            ) : (
                <div className="overflow-hidden rounded-2xl border border-slate-200">
                    <div className="max-h-[420px] overflow-auto">
                        <table className="w-full text-sm">
                            <thead className="sticky top-0 z-10 bg-slate-50 text-slate-600">
                                <tr>
                                    <th className="px-3 py-2 text-left font-semibold">Fecha</th>
                                    <th className="px-3 py-2 text-left font-semibold">Usuario</th>
                                    <th className="px-3 py-2 text-left font-semibold">Acción</th>
                                    <th className="px-3 py-2 text-left font-semibold">Campo</th>
                                    <th className="px-3 py-2 text-left font-semibold">Antes</th>
                                    <th className="px-3 py-2 text-left font-semibold">Después</th>
                                    <th className="px-3 py-2 text-left font-semibold">Origen</th>
                                </tr>
                            </thead>
                            <tbody>
                                {items.map((item, index) => (
                                    <tr key={item.id} className={index % 2 === 0 ? "bg-white" : "bg-slate-50/60"}>
                                        <td className="px-3 py-2 whitespace-nowrap text-slate-600">{formatFechaAR(item.fechaHora)}</td>
                                        <td className="px-3 py-2">
                                            <div className="font-medium text-slate-700">{item.usuarioNombreCompleto || item.usuarioUsername || "Sistema"}</div>
                                            {item.usuarioNombreCompleto && item.usuarioUsername && (
                                                <div className="text-xs text-slate-400">{item.usuarioUsername}</div>
                                            )}
                                        </td>
                                        <td className="px-3 py-2">
                                            <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-semibold ${
                                                item.accion === "CREATE"
                                                    ? "bg-emerald-50 text-emerald-700"
                                                    : item.accion === "DELETE"
                                                        ? "bg-red-50 text-red-700"
                                                        : "bg-blue-50 text-blue-700"
                                            }`}>
                                                {actionLabelMap[item.accion] || item.accion}
                                            </span>
                                        </td>
                                        <td className="px-3 py-2 font-medium text-slate-700">{fieldLabelMap[item.campo] || item.campo}</td>
                                        <td className="px-3 py-2">{renderValue(item.valorAnterior)}</td>
                                        <td className="px-3 py-2">{renderValue(item.valorNuevo)}</td>
                                        <td className="px-3 py-2 whitespace-nowrap text-slate-500">{item.origen || "API"}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}
        </div>
    );
}

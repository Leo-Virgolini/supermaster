"use client";

import { ColumnDef } from "@tanstack/react-table";
import { useState, useEffect } from "react";
import { EyeIcon } from "@heroicons/react/24/outline";
import {
    searchMarcas, searchClasifGral, searchClasifGastro, searchTipos,
    searchProveedores, searchOrigenes, searchMateriales, searchMlas
} from "./productosService";
import { ProductoDTO } from "./types";
import { API_BASE_URL } from "../config/runtime";
import EditableCell from "../components/Table/core/EditableCell";
import { EditableRelationCell } from "../components/EditableRelationCell/EditableRelationCell";
import { formatFechaAR, EMPTY } from "../utils/formatDate";
import { getCatalogoColor, CATALOGO_BADGE_CLASS } from "../utils/catalogoColors";
import TableActionButton from "../components/Table/core/TableActionButton";

const FONT = {
    code: "font-mono text-[13px] tracking-tight font-semibold",
    codeSoft: "font-mono text-[13px] tracking-tight text-gray-600 dark:text-slate-300",
    money: "font-mono text-[13px] font-semibold text-emerald-700 dark:text-emerald-300 whitespace-nowrap",
    numeric: "font-mono text-[13px] tabular-nums text-gray-700 dark:text-slate-200",
    numericSoft: "font-mono text-[13px] tabular-nums text-gray-500 dark:text-slate-400",
    relation: "font-medium text-gray-700 dark:text-slate-200",
    text: "text-gray-700 dark:text-slate-200",
    title: "font-medium text-gray-800 dark:text-slate-100",
} as const;

function ImagePickerModal({ onSelect, onClose, currentUrl }: { onSelect: (name: string) => void; onClose: () => void; currentUrl?: string }) {
    const [search, setSearch] = useState("");
    const [files, setFiles] = useState<string[]>([]);
    const [total, setTotal] = useState(0);
    const [loading, setLoading] = useState(false);

    const MOSTRAR = 60;

    useEffect(() => {
        if (search.trim().length < 2) { setFiles([]); setTotal(0); return; }
        setLoading(true);
        const t = setTimeout(() => {
            fetch(`${API_BASE_URL}/api/imagenes/listar?search=${encodeURIComponent(search)}`)
                .then((res) => res.json())
                .then((data) => {
                    setFiles(Array.isArray(data?.archivos) ? data.archivos.slice(0, MOSTRAR) : []);
                    setTotal(typeof data?.total === "number" ? data.total : 0);
                })
                .catch(() => { setFiles([]); setTotal(0); })
                .finally(() => setLoading(false));
        }, 300);
        return () => clearTimeout(t);
    }, [search]);

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center bg-black/60" onClick={onClose}>
            <div className="bg-white dark:bg-slate-800 rounded-2xl shadow-2xl w-[560px] max-h-[75vh] flex flex-col border border-gray-200 dark:border-slate-700" onClick={(e) => e.stopPropagation()}>
                {/* Header */}
                <div className="px-5 py-4 border-b border-gray-100 dark:border-slate-700 flex items-center justify-between">
                    <div>
                        <h3 className="font-bold text-base text-gray-800 dark:text-slate-100">Seleccionar imagen</h3>
                        <p className="text-xs text-gray-400 dark:text-slate-500 mt-0.5">Busca por nombre de archivo</p>
                    </div>
                    <button onClick={onClose} className="w-8 h-8 rounded-lg flex items-center justify-center text-gray-400 hover:text-gray-600 hover:bg-gray-100 dark:hover:text-slate-300 dark:hover:bg-slate-700 transition">
                        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" /></svg>
                    </button>
                </div>

                {/* Search */}
                <div className="px-5 py-3 border-b border-gray-100 dark:border-slate-700">
                    <div className="relative">
                        <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400 dark:text-slate-500 pointer-events-none" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" /></svg>
                        <input
                            autoFocus
                            type="text"
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            placeholder="Buscar imagen..."
                            className="w-full text-sm border border-gray-200 dark:border-slate-600 rounded-xl pl-9 pr-3 py-2.5 bg-gray-50/50 dark:bg-slate-700/50 text-gray-800 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-blue-400 focus:border-transparent focus:bg-white dark:focus:bg-slate-700 transition"
                        />
                    </div>
                    {!loading && total > files.length && (
                        <div className="mt-2 text-xs text-amber-600 dark:text-amber-400">
                            Mostrando {files.length} de {total} — refiná la búsqueda para acotar.
                        </div>
                    )}
                </div>

                {/* Grid */}
                <div className="flex-1 overflow-y-auto p-4 min-h-[240px]">
                    {search.trim().length < 2 ? (
                        <div className="h-full flex flex-col items-center justify-center gap-2 text-gray-300 dark:text-slate-600">
                            <svg className="w-12 h-12" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1}><path strokeLinecap="round" strokeLinejoin="round" d="M2.25 15.75l5.159-5.159a2.25 2.25 0 013.182 0l5.159 5.159m-1.5-1.5l1.409-1.41a2.25 2.25 0 013.182 0l2.909 2.909M3.75 21h16.5a1.5 1.5 0 001.5-1.5V4.5a1.5 1.5 0 00-1.5-1.5H3.75a1.5 1.5 0 00-1.5 1.5v15a1.5 1.5 0 001.5 1.5z" /></svg>
                            <span className="text-sm">Escribi al menos 2 caracteres para buscar</span>
                        </div>
                    ) : loading ? (
                        <div className="h-full flex items-center justify-center">
                            <svg className="animate-spin h-6 w-6 text-blue-500" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" /><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" /></svg>
                        </div>
                    ) : files.length === 0 ? (
                        <div className="h-full flex flex-col items-center justify-center gap-1 text-gray-400 dark:text-slate-500">
                            <span className="text-sm">Sin resultados para &quot;{search}&quot;</span>
                        </div>
                    ) : (
                        <div className="grid grid-cols-4 gap-3">
                            {files.map((f) => {
                                const isSelected = currentUrl === f;
                                return (
                                    <button
                                        key={f}
                                        onClick={() => { onSelect(f); onClose(); }}
                                        className={`flex flex-col items-center gap-1.5 p-2 rounded-xl border-2 transition group ${
                                            isSelected
                                                ? "border-blue-500 bg-blue-50 dark:bg-blue-900/20"
                                                : "border-transparent hover:border-blue-300 hover:bg-blue-50/50 dark:hover:bg-blue-900/10"
                                        }`}
                                        title={f}
                                    >
                                        <div className="w-full aspect-square rounded-lg bg-gray-50 dark:bg-slate-700/50 overflow-hidden flex items-center justify-center">
                                            <img
                                                src={`${API_BASE_URL}/api/imagenes/${f}`}
                                                alt={f}
                                                loading="lazy"
                                                className="w-full h-full object-contain"
                                                onError={(e) => { (e.target as HTMLImageElement).style.display = "none"; }}
                                            />
                                        </div>
                                        <span className={`text-[10px] truncate w-full text-center leading-tight ${
                                            isSelected
                                                ? "font-semibold text-blue-600 dark:text-blue-300"
                                                : "text-gray-500 dark:text-slate-400 group-hover:text-blue-600 dark:group-hover:text-blue-300"
                                        }`}>{f.replace(/\.[^.]+$/, "")}</span>
                                    </button>
                                );
                            })}
                        </div>
                    )}
                </div>

                {/* Footer */}
                {files.length > 0 && (
                    <div className="px-5 py-2.5 border-t border-gray-100 dark:border-slate-700 text-xs text-gray-400 dark:text-slate-500 text-center">
                        {files.length} imagen{files.length !== 1 ? "es" : ""} encontrada{files.length !== 1 ? "s" : ""}
                    </div>
                )}
            </div>
        </div>
    );
}

function ImageUrlCell({ currentUrl, sku, onSave, disabled = false }: {
    currentUrl: string;
    sku: string;
    onSave: (url: string) => void;
    disabled?: boolean;
}) {
    const [pickerOpen, setPickerOpen] = useState(false);
    // Imagen efectiva: la manual (currentUrl) tiene prioridad; si no hay, se resuelve por SKU.
    const imgSrc = currentUrl
        ? `${API_BASE_URL}/api/imagenes/${currentUrl}`
        : sku
          ? `${API_BASE_URL}/api/imagenes/producto/${encodeURIComponent(sku)}`
          : "";
    // Si la imagen no carga (no hay archivo manual ni por SKU), se muestra el placeholder.
    const [imgError, setImgError] = useState(false);
    useEffect(() => { setImgError(false); }, [imgSrc]);
    const mostrarImg = !!imgSrc && !imgError;

    return (
        <>
            <div className="flex items-center gap-1.5">
                <button
                    onClick={() => { if (!disabled) setPickerOpen(true); }}
                    disabled={disabled}
                    className={`shrink-0 rounded-lg overflow-hidden border transition ${
                        mostrarImg
                            ? "border-gray-200 dark:border-slate-600 hover:border-blue-400 hover:shadow-md"
                            : "border-dashed border-gray-300 dark:border-slate-600 hover:border-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20"
                    } ${disabled ? "cursor-default" : "cursor-pointer"}`}
                    title={disabled ? "Imagen" : "Elegir imagen"}
                >
                    {mostrarImg ? (
                        <img
                            src={imgSrc}
                            alt=""
                            className="w-10 h-10 object-contain bg-gray-50 dark:bg-slate-700/50"
                            onError={() => setImgError(true)}
                        />
                    ) : (
                        <div className="w-10 h-10 flex items-center justify-center bg-gray-50 dark:bg-slate-800 text-gray-400 dark:text-slate-500">
                            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M2.25 15.75l5.159-5.159a2.25 2.25 0 013.182 0l5.159 5.159m-1.5-1.5l1.409-1.41a2.25 2.25 0 013.182 0l2.909 2.909M3.75 21h16.5a1.5 1.5 0 001.5-1.5V4.5a1.5 1.5 0 00-1.5-1.5H3.75a1.5 1.5 0 00-1.5 1.5v15a1.5 1.5 0 001.5 1.5z" /></svg>
                        </div>
                    )}
                </button>
                {currentUrl && !disabled && (
                    <button
                        onClick={() => onSave("")}
                        className="w-5 h-5 rounded-full flex items-center justify-center text-red-400 hover:text-white hover:bg-red-500 transition"
                        title="Quitar imagen"
                    >
                        <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}><path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" /></svg>
                    </button>
                )}
            </div>
            {pickerOpen && !disabled && (
                <ImagePickerModal
                    onSelect={(name) => onSave(name)}
                    onClose={() => setPickerOpen(false)}
                    currentUrl={currentUrl}
                />
            )}
        </>
    );
}

export function getColumns(onOpenDetalle: (producto: ProductoDTO) => void, canEdit = true): ColumnDef<ProductoDTO>[] {
  return [
    {
        id: "select",
        header: ({ table }) => (<input type="checkbox" checked={table.getIsAllPageRowsSelected()} onChange={(e) => table.toggleAllPageRowsSelected(!!e.target.checked)} className="w-4 h-4 cursor-pointer align-middle" />),
        cell: ({ row }) => (<input type="checkbox" checked={row.getIsSelected()} onChange={(e) => row.toggleSelected(!!e.target.checked)} className="w-4 h-4 cursor-pointer align-middle" />),
        size: 30, enableSorting: false, enableColumnFilter: false,
    },
    {
        accessorKey: "id", header: "ID", size: 50, enableColumnFilter: false,
        cell: ({ getValue }) => <span className={FONT.codeSoft}>{String(getValue())}</span>,
    },
    {
        id: "detalle",
        header: "Detalle",
        size: 60,
        enableSorting: false,
        enableColumnFilter: false,
        cell: ({ row }) => (
            <TableActionButton
                onClick={() => onOpenDetalle(row.original)}
                title="Ver detalle del producto"
                icon={<EyeIcon className="w-3.5 h-3.5" />}
                tone="primary"
            >
                Detalle
            </TableActionButton>
        ),
    },

    // --- IMAGEN ---
    {
        accessorKey: "imagenUrl", header: "Imagen", size: 100, enableColumnFilter: false, meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (
            <ImageUrlCell
                currentUrl={String(getValue() || "")}
                sku={row.original.sku || ""}
                disabled={!canEdit}
                onSave={(url) => (table.options.meta as any)?.updateData?.(row.index, column.id, url)}
            />
        )
    },

    // --- IDENTIFICACIÓN ---
    {
        accessorKey: "sku", header: "SKU", meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={getValue() as string} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)} className={FONT.code} disabled={!canEdit} />),
    },
    {
        accessorKey: "codExt", header: "Cód. Ext.", meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={getValue() as string || ""} nullable onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)} className={FONT.codeSoft} disabled={!canEdit} />),
    },
    {
        accessorKey: "mlaId", header: "MLA", meta: { editable: true },
        cell: ({ row, table }) => (
            <EditableRelationCell
                initialName={(row.original as any).mlaNombre}
                initialId={row.original.mlaId}
                loadOptions={searchMlas}
                placeholder="Buscar MLA..."
                endpoint="mlas"
                labelKey="mla"
                nullable
                displayClassName={FONT.code}
                inputClassName={FONT.code}
                disabled={!canEdit}
                onSave={(newId) => (table.options.meta as any)?.updateData?.(row.index, "mlaId", newId)}
            />
        )
    },
    {
        accessorKey: "descripcion", header: "Descripción", size: 250, meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={getValue() as string} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)} className={FONT.text} disabled={!canEdit} />)
    },
    {
        accessorKey: "tituloWeb", header: "Título Web", meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={getValue() as string} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)} className={FONT.title} disabled={!canEdit} />)
    },
    {
        accessorKey: "esCombo", header: "Combo", meta: { editable: true },
        cell: ({ getValue, row, column, table }) => {
            const val = getValue() as boolean;
            return (
                <label className="inline-flex items-center cursor-pointer" title={val ? "Es combo — clic para quitar" : "No es combo — clic para marcar"}>
                    <input type="checkbox" checked={val} onChange={(e) => (table.options.meta as any)?.updateData?.(row.index, column.id, e.target.checked)} className="sr-only peer" disabled={!canEdit} />
                    <span className={`inline-block px-2 py-0.5 rounded text-xs font-semibold transition-colors ${val ? "bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300" : "bg-gray-100 dark:bg-slate-700 text-gray-500 dark:text-slate-400"}`}>
                        {val ? "Sí" : "No"}
                    </span>
                </label>
            );
        }
    },
    // --- ECONÓMICOS ---
    {
        accessorKey: "costo", header: "Costo", enableColumnFilter: false, meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={String(getValue())} type="number" prefix="$ " className="font-mono text-[13px] font-semibold text-gray-500 dark:text-slate-400 whitespace-nowrap" onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, Number(val))} disabled={!canEdit} />)
    },
    {
        accessorKey: "iva", header: "IVA", enableColumnFilter: false, meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={String(getValue())} type="number" suffix="%" className={FONT.numericSoft} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, Number(val))} disabled={!canEdit} />)
    },
    {
        accessorKey: "margenMinorista", header: "Mrg Min", size: 85, enableColumnFilter: false, meta: { editable: true, center: true },
        cell: ({ getValue, row, column, table }) => {
            const value = getValue() as number | null;
            const enCero = value === 0;
            return (<EditableCell initialValue={value ?? ""} type="number" nullable suffix="%" className={enCero ? "font-mono font-semibold text-red-600 dark:text-red-400" : "font-mono font-semibold text-yellow-600 dark:text-yellow-400"} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val === null ? null : Number(val))} disabled={!canEdit} />);
        }
    },
    {
        accessorKey: "margenMayorista", header: "Mrg May", size: 85, enableColumnFilter: false, meta: { editable: true, center: true },
        cell: ({ getValue, row, column, table }) => {
            const value = getValue() as number | null;
            const enCero = value === 0;
            return (<EditableCell initialValue={value ?? ""} type="number" nullable suffix="%" className={enCero ? "font-mono font-semibold text-red-600 dark:text-red-400" : "font-mono font-semibold text-blue-700 dark:text-blue-400"} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val === null ? null : Number(val))} disabled={!canEdit} />);
        }
    },
    {
        accessorKey: "margenFijoMinorista", header: "Fijo Min", size: 95, enableColumnFilter: false, meta: { editable: true, center: true },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={(getValue() as number | null) ?? ""} type="number" nullable prefix="$ " className="font-mono font-semibold text-yellow-600 dark:text-yellow-400" onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val === null ? null : Number(val))} disabled={!canEdit} />)
    },
    {
        accessorKey: "margenFijoMayorista", header: "Fijo May", size: 95, enableColumnFilter: false, meta: { editable: true, center: true },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={(getValue() as number | null) ?? ""} type="number" nullable prefix="$ " className="font-mono font-semibold text-blue-700 dark:text-blue-400" onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val === null ? null : Number(val))} disabled={!canEdit} />)
    },
    {
        accessorKey: "stock", header: "Stock", enableColumnFilter: false, meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={(getValue() as number | null) ?? ""} type="number" nullable className={FONT.numeric} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val === null ? null : Number(val))} disabled={!canEdit} />)
    },

    // --- CLASIFICACIÓN ---
    {
        id: "marca", accessorFn: (row) => row.marcaId, header: "Marca", size: 140, meta: { editable: true },
        cell: ({ row, table }) => (
            <EditableRelationCell
                initialName={(row.original as any).marcaNombre}
                initialId={row.original.marcaId}
                loadOptions={searchMarcas}
                placeholder="Marca..."
                endpoint="marcas"
                nullable
                displayClassName={FONT.relation}
                disabled={!canEdit}
                onSave={(newId) => (table.options.meta as any)?.updateData?.(row.index, "marcaId", newId)}
            />
        )
    },
    {
        id: "tipo", accessorFn: (row) => row.tipoId, header: "Tipo", meta: { editable: true },
        cell: ({ row, table }) => (
            <EditableRelationCell
                initialName={(row.original as any).tipoNombre}
                initialId={row.original.tipoId}
                loadOptions={searchTipos}
                placeholder="Tipo..."
                endpoint="tipos"
                displayClassName={FONT.relation}
                disabled={!canEdit}
                onSave={(newId) => (table.options.meta as any)?.updateData?.(row.index, "tipoId", newId)}
            />
        )
    },
    {
        id: "rubro", accessorFn: (row) => row.clasifGralId, header: "Clasif. Gral.", size: 150, meta: { editable: true },
        cell: ({ row, table }) => (
            <EditableRelationCell
                initialName={(row.original as any).clasifGralNombre}
                initialId={row.original.clasifGralId}
                loadOptions={searchClasifGral}
                placeholder="Rubro..."
                endpoint="clasif-gral"
                displayClassName={FONT.relation}
                disabled={!canEdit}
                onSave={(newId) => (table.options.meta as any)?.updateData?.(row.index, "clasifGralId", newId)}
            />
        )
    },
    {
        id: "subrubro", accessorFn: (row) => row.clasifGastroId, header: "Clasif. Gastro", size: 150, meta: { editable: true },
        cell: ({ row, table }) => (
            <EditableRelationCell
                initialName={(row.original as any).clasifGastroNombre}
                initialId={row.original.clasifGastroId}
                loadOptions={searchClasifGastro}
                placeholder="Gastro..."
                endpoint="clasif-gastro"
                nullable
                displayClassName={FONT.relation}
                disabled={!canEdit}
                onSave={(newId) => (table.options.meta as any)?.updateData?.(row.index, "clasifGastroId", newId)}
            />
        )
    },
    {
        id: "proveedor", accessorFn: (row) => row.proveedorId, header: "Proveedor", size: 180, meta: { editable: true },
        cell: ({ row, table }) => (
            <EditableRelationCell
                initialName={(row.original as any).proveedorNombre}
                initialId={row.original.proveedorId}
                loadOptions={searchProveedores}
                placeholder="Proveedor..."
                endpoint="proveedores"
                labelKey="nombre"
                nullable
                displayClassName={FONT.relation}
                disabled={!canEdit}
                onSave={(newId) => (table.options.meta as any)?.updateData?.(row.index, "proveedorId", newId)}
            />
        )
    },
    {
        id: "origen", accessorFn: (row) => row.origenId, header: "Origen", meta: { editable: true },
        cell: ({ row, table }) => (
            <EditableRelationCell
                initialName={(row.original as any).origenNombre}
                initialId={row.original.origenId}
                loadOptions={searchOrigenes}
                placeholder="Origen..."
                endpoint="origenes"
                labelKey="nombre"
                displayClassName={FONT.relation}
                disabled={!canEdit}
                onSave={(newId) => (table.options.meta as any)?.updateData?.(row.index, "origenId", newId)}
            />
        )
    },
    {
        id: "material", accessorFn: (row) => row.materialId, header: "Material", meta: { editable: true },
        cell: ({ row, table }) => (
            <EditableRelationCell
                initialName={(row.original as any).materialNombre}
                initialId={row.original.materialId}
                loadOptions={searchMateriales}
                placeholder="Material..."
                endpoint="materiales"
                labelKey="nombre"
                nullable
                displayClassName={FONT.relation}
                disabled={!canEdit}
                onSave={(newId) => (table.options.meta as any)?.updateData?.(row.index, "materialId", newId)}
            />
        )
    },

    // --- RELACIONES MANY-TO-MANY ---
    {
        id: "catalogo", accessorKey: "catalogos", header: "Catálogo", size: 160,
        cell: ({ getValue }) => {
            const vals = getValue() as string[] | null;
            if (!vals?.length) return <span className="text-gray-400 dark:text-slate-500">{EMPTY}</span>;
            return (
                <div className="flex flex-wrap gap-1">
                    {vals.map((cat) => (
                        <span key={cat} className={`inline-flex items-center ${CATALOGO_BADGE_CLASS} ${getCatalogoColor(cat)}`}>
                            {cat}
                        </span>
                    ))}
                </div>
            );
        }
    },
    {
        id: "apto", accessorKey: "aptos", header: "Apto", size: 100, enableSorting: false,
        cell: ({ getValue }) => {
            const vals = getValue() as string[] | null;
            return vals?.length ? <span className="text-xs text-gray-600 dark:text-slate-300">{vals.join(", ")}</span> : <span className="text-gray-400 dark:text-slate-500">{EMPTY}</span>;
        }
    },
    {
        id: "cliente", accessorKey: "clientes", header: "Cliente", size: 120, enableSorting: false,
        cell: ({ getValue }) => {
            const vals = getValue() as string[] | null;
            return vals?.length ? <span className="text-xs text-gray-600 dark:text-slate-300">{vals.join(", ")}</span> : <span className="text-gray-400 dark:text-slate-500">{EMPTY}</span>;
        }
    },

    // --- LOGÍSTICA ---
    {
        accessorKey: "uxb", header: "UxB", size: 50, enableColumnFilter: false, meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={String(getValue() || "")} type="number" className={FONT.numeric} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, Number(val))} disabled={!canEdit} />)
    },
    {
        accessorKey: "moq", header: "MOQ", size: 60, enableColumnFilter: false, meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={String(getValue() || "")} type="number" className={FONT.numericSoft} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val === "" ? null : Number(val))} disabled={!canEdit} />)
    },
    {
        accessorKey: "tagReposicion", header: "Tag Rep.", size: 90, meta: { editable: true },
        cell: ({ getValue, row, column, table }) => {
            const val = getValue() as string | null;
            const BADGE: Record<string, string> = {
                PRIO: "bg-amber-100 dark:bg-amber-900/40 text-amber-800 dark:text-amber-300",
                LIQ:  "bg-red-100 dark:bg-red-900/40 text-red-800 dark:text-red-300",
            };
            return (
                <select
                    className={`text-xs font-bold px-2 py-0.5 rounded border-0 outline-none ${canEdit ? "cursor-pointer" : "cursor-default appearance-none"} ${val ? (BADGE[val] ?? "bg-gray-100 dark:bg-slate-700 text-gray-700 dark:text-slate-300") : "bg-gray-50 dark:bg-slate-800 text-gray-400 dark:text-slate-500"}`}
                    value={val || ""}
                    disabled={!canEdit}
                    onChange={(e) => (table.options.meta as any)?.updateData?.(row.index, column.id, e.target.value || null)}
                >
                    <option value="">-</option>
                    <option value="PRIO">PRIO</option>
                    <option value="LIQ">LIQ</option>
                </select>
            );
        }
    },
    {
        accessorKey: "tag", header: "Tag", size: 110, meta: { editable: true },
        cell: ({ getValue, row, column, table }) => {
            const val = getValue() as string | null;
            const BADGE: Record<string, string> = {
                MAQUINA:  "bg-indigo-100 dark:bg-indigo-900/40 text-indigo-800 dark:text-indigo-300",
                REPUESTO: "bg-sky-100 dark:bg-sky-900/40 text-sky-800 dark:text-sky-300",
                MENAJE:   "bg-emerald-100 dark:bg-emerald-900/40 text-emerald-800 dark:text-emerald-300",
            };
            return (
                <select
                    className={`text-xs font-bold px-2 py-0.5 rounded border-0 outline-none ${canEdit ? "cursor-pointer" : "cursor-default appearance-none"} ${val ? (BADGE[val] ?? "bg-gray-100 dark:bg-slate-700 text-gray-700 dark:text-slate-300") : "bg-gray-50 dark:bg-slate-800 text-gray-400 dark:text-slate-500"}`}
                    value={val || ""}
                    disabled={!canEdit}
                    onChange={(e) => (table.options.meta as any)?.updateData?.(row.index, column.id, e.target.value || null)}
                >
                    <option value="">-</option>
                    <option value="MAQUINA">MÁQUINA</option>
                    <option value="REPUESTO">REPUESTO</option>
                    <option value="MENAJE">MENAJE</option>
                </select>
            );
        }
    },

    // --- DIMENSIONES ---
    {
        accessorKey: "capacidad", header: "Capacidad", size: 80, enableColumnFilter: false, meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={String(getValue() || "")} nullable className={FONT.codeSoft} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)} disabled={!canEdit} />)
    },
    {
        accessorKey: "largo", header: "Largo", size: 70, enableColumnFilter: false, meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={String(getValue() || "")} nullable className={FONT.codeSoft} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)} disabled={!canEdit} />)
    },
    {
        accessorKey: "ancho", header: "Ancho", size: 70, enableColumnFilter: false, meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={String(getValue() || "")} nullable className={FONT.codeSoft} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)} disabled={!canEdit} />)
    },
    {
        accessorKey: "alto", header: "Alto", size: 70, enableColumnFilter: false, meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={String(getValue() || "")} nullable className={FONT.codeSoft} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)} disabled={!canEdit} />)
    },
    {
        accessorKey: "diamboca", header: "Ø Boca", size: 70, enableColumnFilter: false, meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={String(getValue() || "")} className={FONT.codeSoft} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)} disabled={!canEdit} />)
    },
    {
        accessorKey: "diambase", header: "Ø Base", size: 70, enableColumnFilter: false, meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={String(getValue() || "")} className={FONT.codeSoft} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)} disabled={!canEdit} />)
    },
    {
        accessorKey: "espesor", header: "Espesor", size: 70, enableColumnFilter: false, meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={String(getValue() || "")} className={FONT.codeSoft} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)} disabled={!canEdit} />)
    },

    // --- FECHAS (al final) ---
    {
        accessorKey: "fechaUltimoCosto", header: "F. Últ. Costo", size: 120, enableColumnFilter: false,
        cell: ({ getValue }) => <span className="text-xs font-mono text-gray-500 dark:text-slate-400">{formatFechaAR(getValue() as string)}</span>
    },
    {
        accessorKey: "fechaModificacion", header: "F. Modificación", size: 120, enableColumnFilter: false,
        cell: ({ getValue }) => <span className="text-xs font-mono text-gray-500 dark:text-slate-400">{formatFechaAR(getValue() as string)}</span>
    },
    {
        accessorKey: "activo", header: "Activo",
        cell: ({ getValue, row, column, table }) => {
            const val = getValue() as boolean;
            return (
                <label className={`inline-flex items-center ${canEdit ? "cursor-pointer" : "cursor-default"}`} title={canEdit ? (val ? "Activo — clic para desactivar" : "Inactivo — clic para activar") : (val ? "Activo" : "Inactivo")}>
                    <input type="checkbox" checked={val} onChange={(e) => (table.options.meta as any)?.updateData?.(row.index, column.id, e.target.checked)} className="sr-only peer" disabled={!canEdit} />
                    <span className={`inline-block px-2 py-0.5 rounded text-xs font-semibold transition-colors ${val ? "bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-300" : "bg-gray-100 dark:bg-slate-700 text-gray-500 dark:text-slate-400"}`}>
                        {val ? "Sí" : "No"}
                    </span>
                </label>
            );
        }
    },
  ];
}

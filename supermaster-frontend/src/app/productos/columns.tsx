"use client";

import { ColumnDef } from "@tanstack/react-table";
import { useState, useEffect } from "react";
import Link from "next/link";
import { CurrencyDollarIcon, PencilSquareIcon } from "@heroicons/react/24/outline";
import {
    searchMarcas, searchClasifGral, searchClasifGastro, searchTipos,
    searchProveedores, searchOrigenes, searchMateriales, searchMlas, searchSectoresDeposito
} from "./productosService";
import { ProductoDTO } from "./types";
import { API_BASE_URL } from "../config/runtime";
import ImagenesCarousel from "./ImagenesCarousel";
import EditableCell from "../components/Table/core/EditableCell";
import { EditableRelationCell } from "../components/EditableRelationCell/EditableRelationCell";
import { formatFechaAR, EMPTY } from "../utils/formatDate";
import { getCatalogoColor, CATALOGO_BADGE_CLASS } from "../utils/catalogoColors";
import TableActionButton, { getTableActionButtonClasses } from "../components/Table/core/TableActionButton";
import ConfirmToggleCell from "../components/Table/core/ConfirmToggleCell";

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

// Tono ámbar para los headers de columnas que pertenecen a Mercado Libre — las distingue del resto.
// El `!` asegura prioridad sobre el bg-gray-100 base del <th>.
const HEADER_ML = "!bg-amber-100 hover:!bg-amber-200 dark:!bg-amber-900/40 dark:hover:!bg-amber-900/60 text-amber-900 dark:text-amber-200";

// Las imágenes se resuelven siempre por SKU; el visor multi-imagen vive en ImagenesCarousel.

function ImagenCeldaSku({ sku }: { sku: string }) {
    const [carouselOpen, setCarouselOpen] = useState(false);
    const [imgError, setImgError] = useState(false);
    const src = sku ? `${API_BASE_URL}/api/imagenes/producto/${encodeURIComponent(sku)}` : "";
    useEffect(() => { setImgError(false); }, [src]);
    const mostrar = !!src && !imgError;
    return (
        <>
            <button
                onClick={() => { if (mostrar) setCarouselOpen(true); }}
                title={mostrar ? "Ver imágenes" : "Sin imagen"}
                className={`shrink-0 overflow-hidden rounded-lg border transition ${mostrar ? "border-gray-200 dark:border-slate-600 hover:border-blue-400 hover:shadow-md cursor-pointer" : "border-dashed border-gray-300 dark:border-slate-600 cursor-default"}`}
            >
                {mostrar ? (
                    <img src={src} alt="" loading="lazy" className="h-8 w-8 bg-gray-50 object-contain dark:bg-slate-700/50" onError={() => setImgError(true)} />
                ) : (
                    <div className="flex h-8 w-8 items-center justify-center bg-gray-50 text-gray-400 dark:bg-slate-800 dark:text-slate-500">
                        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M2.25 15.75l5.159-5.159a2.25 2.25 0 013.182 0l5.159 5.159m-1.5-1.5l1.409-1.41a2.25 2.25 0 013.182 0l2.909 2.909M3.75 21h16.5a1.5 1.5 0 001.5-1.5V4.5a1.5 1.5 0 00-1.5-1.5H3.75a1.5 1.5 0 00-1.5 1.5v15a1.5 1.5 0 001.5 1.5z" /></svg>
                    </div>
                )}
            </button>
            {carouselOpen && <ImagenesCarousel sku={sku} onClose={() => setCarouselOpen(false)} />}
        </>
    );
}

export function getColumns(onEditarProducto: (producto: ProductoDTO) => void, canEdit = true): ColumnDef<ProductoDTO>[] {
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
        size: 76,
        enableSorting: false,
        enableColumnFilter: false,
        cell: ({ row }) => (
            <div className="flex items-center justify-center gap-1">
                <TableActionButton
                    onClick={() => onEditarProducto(row.original)}
                    title="Editar producto"
                    aria-label="Editar producto"
                    icon={<PencilSquareIcon className="w-4 h-4" />}
                    tone="primary"
                    disabled={!canEdit}
                    className="!px-2"
                />
                <Link
                    href={`/producto-canal-precios?q=${encodeURIComponent(row.original.sku)}`}
                    title="Ver precios de este producto en el Monitor"
                    aria-label="Ver precios"
                    className={getTableActionButtonClasses("success", "!px-2")}
                >
                    <CurrencyDollarIcon className="w-4 h-4" />
                </Link>
            </div>
        ),
    },

    // --- IMAGEN (siempre por SKU; click → carousel) ---
    {
        id: "imagen", header: "Imagen", size: 100, enableSorting: false, enableColumnFilter: false,
        cell: ({ row }) => <ImagenCeldaSku sku={row.original.sku || ""} />
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
        accessorKey: "mlaId", header: "MLA", meta: { editable: true, headerClassName: HEADER_ML },
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
        accessorKey: "tituloDux", header: "Título Dux", size: 250, meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={getValue() as string} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)} className={FONT.title} disabled={!canEdit} />)
    },
    {
        accessorKey: "tituloMl", header: "Título ML", size: 220, meta: { editable: true, headerClassName: HEADER_ML },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={(getValue() as string) ?? ""} nullable onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)} className={FONT.text} disabled={!canEdit} />)
    },
    {
        accessorKey: "tituloNube", header: "Título Nube", size: 220, meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={(getValue() as string) ?? ""} nullable onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)} className={FONT.text} disabled={!canEdit} />)
    },
    {
        accessorKey: "esCombo", header: "Combo", meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (
            <ConfirmToggleCell
                value={getValue() as boolean}
                disabled={!canEdit}
                trueClassName="bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300"
                falseClassName="bg-gray-100 dark:bg-slate-700 text-gray-500 dark:text-slate-400"
                titleFor={(v) => v ? "Es combo" : "No es combo"}
                onConfirm={(newVal) => (table.options.meta as any)?.updateData?.(row.index, column.id, newVal)}
            />
        )
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
        accessorKey: "stock", header: "Stock", enableColumnFilter: false, meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={(getValue() as number | null) ?? ""} type="number" nullable className={FONT.numeric} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val === null ? null : Number(val))} disabled={!canEdit} />)
    },

    // --- CLASIFICACIÓN ---
    {
        id: "marca", accessorFn: (row) => row.marcaId, header: "Marca", size: 140, meta: { editable: true },
        cell: ({ row, table }) => (
            <EditableRelationCell
                fullName={row.original.marcaNombreCompleto}
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
                fullName={row.original.tipoNombreCompleto}
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
                fullName={row.original.clasifGralNombreCompleto}
                initialId={row.original.clasifGralId}
                loadOptions={searchClasifGral}
                placeholder="Rubro..."
                endpoint="clasif-gral"
                nullable
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
                fullName={row.original.clasifGastroNombreCompleto}
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
    {
        id: "sectorDeposito", accessorFn: (row) => row.sectorDepositoId, header: "Sector Depósito", size: 150, meta: { editable: true },
        cell: ({ row, table }) => (
            <EditableRelationCell
                initialId={row.original.sectorDepositoId}
                loadOptions={searchSectoresDeposito}
                placeholder="Sector..."
                endpoint="sectores-deposito"
                labelKey="codigo"
                nullable
                displayClassName={FONT.relation}
                disabled={!canEdit}
                onSave={(newId) => (table.options.meta as any)?.updateData?.(row.index, "sectorDepositoId", newId)}
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
        id: "apto", accessorKey: "aptos", header: "Apto", size: 100,
        cell: ({ getValue }) => {
            const vals = getValue() as string[] | null;
            const text = vals?.length ? vals.join(", ") : null;
            return text ? <span className="text-xs text-gray-600 dark:text-slate-300 whitespace-nowrap" title={text}>{text}</span> : <span className="text-gray-400 dark:text-slate-500">{EMPTY}</span>;
        }
    },
    {
        id: "cliente", accessorKey: "clientes", header: "Cliente", size: 120,
        cell: ({ getValue }) => {
            const vals = getValue() as string[] | null;
            const text = vals?.length ? vals.join(", ") : null;
            return text ? <span className="text-xs text-gray-600 dark:text-slate-300 whitespace-nowrap" title={text}>{text}</span> : <span className="text-gray-400 dark:text-slate-500">{EMPTY}</span>;
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
                INSUMO:   "bg-amber-100 dark:bg-amber-900/40 text-amber-800 dark:text-amber-300",
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
                    <option value="INSUMO">INSUMO</option>
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

    // --- PAQUETE ML (envío): alto/ancho/largo en cm, peso en kg ---
    {
        accessorKey: "mlPaqAlto", header: "Alto ML", size: 70, enableColumnFilter: false, meta: { editable: true, headerClassName: HEADER_ML },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={String(getValue() ?? "")} type="number" nullable className={FONT.codeSoft} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val === null ? null : Number(val))} disabled={!canEdit} />)
    },
    {
        accessorKey: "mlPaqAncho", header: "Ancho ML", size: 70, enableColumnFilter: false, meta: { editable: true, headerClassName: HEADER_ML },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={String(getValue() ?? "")} type="number" nullable className={FONT.codeSoft} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val === null ? null : Number(val))} disabled={!canEdit} />)
    },
    {
        accessorKey: "mlPaqLargo", header: "Largo ML", size: 70, enableColumnFilter: false, meta: { editable: true, headerClassName: HEADER_ML },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={String(getValue() ?? "")} type="number" nullable className={FONT.codeSoft} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val === null ? null : Number(val))} disabled={!canEdit} />)
    },
    {
        accessorKey: "mlPaqPeso", header: "Peso ML", size: 70, enableColumnFilter: false, meta: { editable: true, headerClassName: HEADER_ML },
        cell: ({ getValue, row, column, table }) => (<EditableCell initialValue={String(getValue() ?? "")} type="number" nullable className={FONT.codeSoft} onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val === null ? null : Number(val))} disabled={!canEdit} />)
    },
    {
        // Categoría de Mercado Libre: solo lectura (se elige con el predictor en el formulario).
        accessorKey: "mlCategoryNombre", header: "Categoría ML", size: 170, enableColumnFilter: false, meta: { headerClassName: HEADER_ML },
        cell: ({ getValue }) => {
            const v = getValue() as string | null;
            if (!v) return <span className="block truncate text-xs text-slate-600 dark:text-slate-300">—</span>;
            // Resalta la hoja (último segmento del path "A > B > Hoja").
            const partes = v.split(">").map(s => s.trim());
            const hoja = partes.pop() ?? "";
            const prefijo = partes.join(" > ");
            return (
                <span className="block truncate text-xs text-slate-600 dark:text-slate-300" title={v}>
                    {prefijo && <>{prefijo} {">"} </>}
                    <span className="font-semibold text-slate-800 dark:text-slate-100">{hoja}</span>
                </span>
            );
        }
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
        cell: ({ getValue, row, column, table }) => (
            <ConfirmToggleCell
                value={getValue() as boolean}
                disabled={!canEdit}
                trueClassName="bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-300"
                falseClassName="bg-gray-100 dark:bg-slate-700 text-gray-500 dark:text-slate-400"
                titleFor={(v) => v ? "Activo" : "Inactivo"}
                onConfirm={(newVal) => (table.options.meta as any)?.updateData?.(row.index, column.id, newVal)}
            />
        )
    },
  ];
}

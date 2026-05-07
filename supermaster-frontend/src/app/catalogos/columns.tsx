"use client";

import Link from "next/link";
import { EyeIcon } from "@heroicons/react/24/outline";
import { ColumnDef } from "@tanstack/react-table";
import { CatalogoDTO } from "./types";
import EditableCell from "../components/Table/core/EditableCell";
import { getCatalogoColor, CATALOGO_BADGE_CLASS } from "../utils/catalogoColors";
import { getTableActionButtonClasses } from "../components/Table/core/TableActionButton";

export const getColumns = (canEdit = true): ColumnDef<CatalogoDTO>[] => [
    {
        id: "select",
        header: ({ table }) => (
            <input
                type="checkbox"
                checked={table.getIsAllPageRowsSelected()}
                onChange={(e) => table.toggleAllPageRowsSelected(!!e.target.checked)}
                className="w-4 h-4 cursor-pointer align-middle"
            />
        ),
        cell: ({ row }) => (
            <input
                type="checkbox"
                checked={row.getIsSelected()}
                onChange={(e) => row.toggleSelected(!!e.target.checked)}
                className="w-4 h-4 cursor-pointer align-middle"
            />
        ),
        enableSorting: false,
        enableHiding: true,
        size: 40,
    },
    {
        accessorKey: "id",
        header: "ID",
        size: 50,
    },
    {
        accessorKey: "nombre",
        header: "Nombre",
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (
            <EditableCell
                initialValue={getValue() as string}
                disabled={!canEdit}
                onSave={(newValue) => {
                    table.options.meta?.updateData?.(row.index, column.id, newValue);
                }}
                renderDisplay={(value, onClick) => {
                    const colors = getCatalogoColor(String(value));
                    return (
                        <button
                            type="button"
                            onClick={onClick}
                            disabled={!canEdit}
                            className={`inline-flex min-h-[28px] items-center ${CATALOGO_BADGE_CLASS} transition ${
                                canEdit ? "cursor-pointer hover:brightness-[0.97]" : "cursor-default"
                            } ${colors}`}
                            title={canEdit ? `${value} — Click para editar` : String(value)}
                        >
                            {value}
                        </button>
                    );
                }}
            />
        ),
    },
    {
        accessorKey: "exportarConIva",
        header: "¿Incluye IVA?",
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => {
            const val = getValue() as boolean;
            return (
                <label className={`inline-flex items-center ${canEdit ? "cursor-pointer" : "cursor-default"}`} title={canEdit ? (val ? "Incluye IVA — clic para quitar" : "Sin IVA — clic para incluir") : (val ? "Incluye IVA" : "Sin IVA")}>
                    <input type="checkbox" checked={val} onChange={(e) => table.options.meta?.updateData?.(row.index, column.id, e.target.checked)} className="sr-only peer" disabled={!canEdit} />
                    <span className={`inline-block px-2 py-0.5 rounded text-xs font-semibold transition-colors ${val ? "bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-300" : "bg-gray-100 dark:bg-slate-700 text-gray-500 dark:text-slate-400"}`}>
                        {val ? "Sí" : "No"}
                    </span>
                </label>
            );
        },
        enableSorting: false,
        enableHiding: false,
        size: 40,
    },
    {
        accessorKey: "recargoPorcentaje",
        header: "Recargo (%)",
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => {
            const initialValue = getValue() as number;
            return (
                <EditableCell
                    initialValue={initialValue.toString()}
                    type="number"
                    suffix="%"
                    disabled={!canEdit}
                    onSave={(newValue) => {
                        table.options.meta?.updateData?.(
                            row.index,
                            column.id,
                            Number(newValue)
                        );
                    }}
                />
            );
        },
        size: 100,
    },
    {
        id: "productos",
        header: "Productos",
        size: 100,
        enableSorting: false,
        cell: ({ row }) => (
            <Link
                href={`/productos?catalogoIds=${row.original.id}`}
                title="Ver productos de este catálogo"
                className={getTableActionButtonClasses("primary")}
            >
                <EyeIcon className="w-3.5 h-3.5" />
                Ver productos
            </Link>
        ),
    },
];

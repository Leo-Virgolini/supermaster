"use client";
import { ColumnDef } from "@tanstack/react-table";
import { ProveedorDTO } from "./proveedoresService";
import EditableCell from "../components/Table/core/EditableCell";

export const getColumns = (canEdit = true): ColumnDef<ProveedorDTO>[] => [
    {
        id: "select",
        header: ({ table }) => (
            <input type="checkbox" checked={table.getIsAllPageRowsSelected()} onChange={(e) => table.toggleAllPageRowsSelected(!!e.target.checked)} className="w-4 h-4 cursor-pointer align-middle" />
        ),
        cell: ({ row }) => (
            <input type="checkbox" checked={row.getIsSelected()} onChange={(e) => row.toggleSelected(!!e.target.checked)} className="w-4 h-4 cursor-pointer align-middle" />
        ),
        size: 40,
    },
    { accessorKey: "id", header: "ID", size: 50 },
    {
        accessorKey: "nombre",
        header: "Nombre",
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (
            <EditableCell initialValue={getValue() as string} disabled={!canEdit} onSave={(val) => table.options.meta?.updateData?.(row.index, column.id, val)} />
        ),
    },
    {
        accessorKey: "apodo",
        header: "Apodo / Alias",
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (
            <EditableCell initialValue={(getValue() as string) || ""} disabled={!canEdit} onSave={(val) => table.options.meta?.updateData?.(row.index, column.id, val)} />
        ),
    },
    {
        accessorKey: "plazoPago",
        header: "Plazo Pago",
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (
            <EditableCell initialValue={(getValue() as string) || ""} disabled={!canEdit} onSave={(val) => table.options.meta?.updateData?.(row.index, column.id, val)} />
        ),
    },
    {
        accessorKey: "financiacionPorcentaje",
        header: "Financiación (%)",
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (
            <EditableCell initialValue={String(getValue() ?? "")} type="number" suffix="%" disabled={!canEdit} onSave={(val) => table.options.meta?.updateData?.(row.index, column.id, val === "" ? null : Number(val))} />
        ),
    },
    {
        accessorKey: "leadTimeDias",
        header: "Lead Time (días)",
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (
            <EditableCell initialValue={String(getValue() ?? "")} type="number" disabled={!canEdit} onSave={(val) => table.options.meta?.updateData?.(row.index, column.id, val === "" ? null : Number(val))} />
        ),
    },
    {
        accessorKey: "entrega",
        header: "Entrega",
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => {
            const val = !!getValue();
            return (
                <label className={`inline-flex items-center ${canEdit ? "cursor-pointer" : "cursor-default"}`} title={canEdit ? (val ? "Entrega — clic para quitar" : "No entrega — clic para marcar") : (val ? "Entrega" : "No entrega")}>
                    <input type="checkbox" checked={val} onChange={(e) => table.options.meta?.updateData?.(row.index, column.id, e.target.checked)} className="sr-only peer" disabled={!canEdit} />
                    <span className={`inline-block px-2 py-0.5 rounded text-xs font-semibold transition-colors ${val ? "bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-300" : "bg-gray-100 dark:bg-slate-700 text-gray-500 dark:text-slate-400"}`}>
                        {val ? "Sí" : "No"}
                    </span>
                </label>
            );
        },
    },
];

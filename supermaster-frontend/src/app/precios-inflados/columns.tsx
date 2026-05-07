"use client";
import { ColumnDef } from "@tanstack/react-table";
import { PrecioInfladoDTO, TipoPrecioInflado } from "./types";
import EditableCell from "../components/Table/core/EditableCell";

const TIPO_CONFIG: Record<string, { label: string; classes: string }> = {
    MULTIPLICADOR:  { label: "Multiplicador", classes: "bg-blue-100 dark:bg-blue-900/40 text-blue-800 dark:text-blue-300" },
    DESCUENTO_PORC: { label: "Descuento %",   classes: "bg-green-100 dark:bg-green-900/40 text-green-800 dark:text-green-300" },
    DIVISOR:        { label: "Divisor",        classes: "bg-purple-100 dark:bg-purple-900/40 text-purple-800 dark:text-purple-300" },
    PRECIO_FIJO:    { label: "Precio Fijo",    classes: "bg-amber-100 dark:bg-amber-900/40 text-amber-800 dark:text-amber-300" },
};

const TIPO_OPTIONS: { value: TipoPrecioInflado; label: string }[] = [
    { value: "MULTIPLICADOR", label: "Multiplicador" },
    { value: "DESCUENTO_PORC", label: "Descuento %" },
    { value: "DIVISOR", label: "Divisor" },
    { value: "PRECIO_FIJO", label: "Precio Fijo" },
];

export const getColumns = (canEdit = true): ColumnDef<PrecioInfladoDTO>[] => [
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
        size: 40,
    },
    {
        accessorKey: "id",
        header: "ID",
        size: 60,
        enableColumnFilter: false,
    },
    {
        accessorKey: "codigo",
        header: "Código",
        enableColumnFilter: true,
        meta: { filterVariant: "text", editable: true },
        cell: ({ getValue, row, column, table }) => (
            <EditableCell
                initialValue={getValue() as string}
                disabled={!canEdit}
                onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, String(val).toUpperCase())}
            />
        ),
    },
    {
        accessorKey: "tipo",
        header: "Tipo",
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => {
            const val = getValue() as string;
            const cfg = TIPO_CONFIG[val] ?? { label: val, classes: "bg-gray-100 dark:bg-slate-700 text-gray-700 dark:text-slate-300" };
            return (
                <select
                    className={`text-xs font-bold px-2 py-0.5 rounded border-0 outline-none ${canEdit ? "cursor-pointer" : "cursor-default appearance-none"} ${cfg.classes}`}
                    value={val}
                    disabled={!canEdit}
                    onChange={(e) => (table.options.meta as any)?.updateData?.(row.index, column.id, e.target.value)}
                >
                    {TIPO_OPTIONS.map((t) => (
                        <option key={t.value} value={t.value}>{t.label}</option>
                    ))}
                </select>
            );
        },
    },
    {
        accessorKey: "valor",
        header: "Valor",
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (
            <EditableCell
                initialValue={getValue() as number}
                type="number"
                disabled={!canEdit}
                onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, Number(val))}
            />
        ),
    },
];

"use client";

import { ColumnDef } from "@tanstack/react-table";
import { ConfigAutomatizacionDTO } from "./types";
import EditableCell from "../components/Table/core/EditableCell";

export const getColumns = (canEdit = true): ColumnDef<ConfigAutomatizacionDTO>[] => [
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
        accessorKey: "clave",
        header: "Clave",
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (
            <EditableCell
                initialValue={getValue() as string}
                disabled={!canEdit}
                onSave={(val) =>
                    (table.options.meta as any)?.updateData?.(row.index, column.id, val)
                }
            />
        ),
    },
    {
        accessorKey: "valor",
        header: "Valor",
        size: 200,
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (
            <EditableCell
                initialValue={getValue() as string}
                disabled={!canEdit}
                onSave={(val) =>
                    (table.options.meta as any)?.updateData?.(row.index, column.id, val)
                }
            />
        ),
    },
    {
        accessorKey: "descripcion",
        header: "Descripción",
        size: 250,
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (
            <EditableCell
                initialValue={(getValue() as string) || ""}
                disabled={!canEdit}
                onSave={(val) =>
                    (table.options.meta as any)?.updateData?.(row.index, column.id, val)
                }
            />
        ),
    },
];

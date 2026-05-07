"use client";

import Link from "next/link";
import { EyeIcon } from "@heroicons/react/24/outline";
import { ColumnDef } from "@tanstack/react-table";
import { MaterialDTO } from "./types";
import EditableCell from "../components/Table/core/EditableCell";
import { getTableActionButtonClasses } from "../components/Table/core/TableActionButton";

export const getColumns = (canEdit = true): ColumnDef<MaterialDTO>[] => [
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
        cell: ({ getValue, row, column, table }) => {
            const initialValue = getValue() as string;
            return (
                <EditableCell
                    initialValue={initialValue}
                    disabled={!canEdit}
                    onSave={(newValue) => {
                        (table.options.meta as any)?.updateData?.(
                            row.index,
                            column.id,
                            newValue
                        );
                    }}
                />
            );
        },
    },
    {
        id: "productos",
        header: "Productos",
        size: 100,
        enableSorting: false,
        cell: ({ row }) => (
            <Link
                href={`/productos?materialIds=${row.original.id}`}
                title="Ver productos de este material"
                className={getTableActionButtonClasses("primary")}
            >
                <EyeIcon className="w-3.5 h-3.5" />
                Ver productos
            </Link>
        ),
    },
];

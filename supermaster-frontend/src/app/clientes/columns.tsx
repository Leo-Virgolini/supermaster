"use client";
import Link from "next/link";
import { EyeIcon } from "@heroicons/react/24/outline";
import { ColumnDef } from "@tanstack/react-table";
import { ClienteDTO } from "./clientesService";
import EditableCell from "../components/Table/core/EditableCell";
import { getTableActionButtonClasses } from "../components/Table/core/TableActionButton";

export const getColumns = (canEdit = true): ColumnDef<ClienteDTO>[] => [
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
            <EditableCell
                initialValue={getValue() as string}
                disabled={!canEdit}
                onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)}
            />
        ),
    },
    {
        id: "productos",
        header: "Productos",
        size: 100,
        enableSorting: false,
        cell: ({ row }) => (
            <Link
                href={`/productos?clienteIds=${row.original.id}`}
                title="Ver productos de este cliente"
                className={getTableActionButtonClasses("primary")}
            >
                <EyeIcon className="w-3.5 h-3.5" />
                Ver productos
            </Link>
        ),
    },
];

"use client";

import Link from "next/link";
import { EyeIcon } from "@heroicons/react/24/outline";
import { ColumnDef } from "@tanstack/react-table";
import { ClasificacionDTO } from "./types";
import EditableCell from "../components/Table/core/EditableCell";
import { EditableRelationCell } from "../components/EditableRelationCell/EditableRelationCell";
import { getTableActionButtonClasses } from "../components/Table/core/TableActionButton";

export function getColumns(
    searchClasificacionesFn: (q: string) => Promise<{ id: number; label: string }[]>,
    canEdit = true,
): ColumnDef<ClasificacionDTO>[] {
    return [
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
                    onSave={(newValue) =>
                        (table.options.meta as any)?.updateData?.(row.index, column.id, newValue)
                    }
                />
            ),
        },
        {
            accessorKey: "padreId",
            header: "Pertenece a",
            meta: { editable: true },
            cell: ({ row, table }) => (
                <EditableRelationCell
                    initialId={row.original.padreId ?? null}
                    initialName=""
                    onSave={(newId) =>
                        (table.options.meta as any)?.updateData?.(row.index, "padreId", newId)
                    }
                    loadOptions={searchClasificacionesFn}
                    placeholder="Buscar clasificación padre..."
                    endpoint="clasif-gral"
                    labelKey="nombre"
                    nullable
                    disabled={!canEdit}
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
                    href={`/productos?clasifGralIds=${row.original.id}`}
                    title="Ver productos de esta clasificación"
                    className={getTableActionButtonClasses("primary")}
                >
                    <EyeIcon className="w-3.5 h-3.5" />
                    Ver productos
                </Link>
            ),
        },
    ];
}

"use client";

import Link from "next/link";
import { EyeIcon } from "@heroicons/react/24/outline";
import { ColumnDef } from "@tanstack/react-table";
import { ClasifGastroDTO } from "./types";
import EditableCell from "../components/Table/core/EditableCell";
import { EditableRelationCell } from "../components/EditableRelationCell/EditableRelationCell";
import { getTableActionButtonClasses } from "../components/Table/core/TableActionButton";

export function getColumns(
    searchClasifGastroFn: (q: string) => Promise<{ id: number; label: string }[]>,
    canEdit = true,
): ColumnDef<ClasifGastroDTO>[] {
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
                    loadOptions={searchClasifGastroFn}
                    placeholder="Buscar clasificación padre..."
                    endpoint="clasif-gastro"
                    labelKey="nombre"
                    nullable
                    disabled={!canEdit}
                />
            ),
        },
        {
            accessorKey: "esMaquina",
            header: "Máquina",
            meta: { editable: true },
            cell: ({ getValue, row, column, table }) => (
                <input
                    type="checkbox"
                    checked={getValue() as boolean}
                    disabled={!canEdit}
                    onChange={(e) =>
                        (table.options.meta as any)?.updateData?.(row.index, column.id, e.target.checked)
                    }
                    className={`w-3.5 h-3.5 align-middle ${canEdit ? "cursor-pointer" : "cursor-default"}`}
                />
            ),
            enableHiding: false,
            size: 80,
        },
        {
            accessorKey: "idDux",
            header: "ID Dux",
            size: 90,
            meta: { editable: true },
            cell: ({ getValue, row, column, table }) => (
                <EditableCell
                    initialValue={getValue() as number ?? ""}
                    type="number"
                    nullable
                    disabled={!canEdit}
                    onSave={(newValue) => {
                        const parsed = newValue === null || newValue === "" ? null : Number(newValue);
                        if (parsed !== null && isNaN(parsed)) return;
                        (table.options.meta as any)?.updateData?.(row.index, column.id, parsed);
                    }}
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
                    href={`/productos?clasifGastroIds=${row.original.id}`}
                    title="Ver productos de esta clasificación gastro"
                    className={getTableActionButtonClasses("primary")}
                >
                    <EyeIcon className="w-3.5 h-3.5" />
                    Ver productos
                </Link>
            ),
        },
    ];
}

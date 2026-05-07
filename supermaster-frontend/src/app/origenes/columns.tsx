"use client";

import Link from "next/link";
import { EyeIcon } from "@heroicons/react/24/outline";
import { ColumnDef } from "@tanstack/react-table";
import { OrigenDTO } from "./types";
import EditableCell from "../components/Table/core/EditableCell";
import { getTableActionButtonClasses } from "../components/Table/core/TableActionButton";

export const getColumns = (canEdit = true): ColumnDef<OrigenDTO>[] => [
    // --- COLUMNA 1: CHECKBOX DE SELECCIÓN ---
    {
        id: "select",
        header: ({ table }) => (
            <input
                type="checkbox"
                // Selecciona TODO si apretás el de arriba
                checked={table.getIsAllPageRowsSelected()}
                onChange={(e) => table.toggleAllPageRowsSelected(!!e.target.checked)}
                className="w-4 h-4 cursor-pointer align-middle"
            />
        ),
        cell: ({ row }) => (
            <input
                type="checkbox"
                // Selecciona LA FILA si apretás este
                checked={row.getIsSelected()}
                onChange={(e) => row.toggleSelected(!!e.target.checked)}
                className="w-4 h-4 cursor-pointer align-middle"
            />
        ),
        enableSorting: false,
        enableHiding: true,
        size: 40,
    },

    // --- COLUMNA 2: ID (Solo lectura) ---
    {
        accessorKey: "id",
        header: "ID",
        size: 50,
    },

    // --- COLUMNA 3: NOMBRE (Editable estilo Excel) ---
    {
        accessorKey: "nombre",
        header: "Nombre",
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => {
            const initialValue = getValue() as string;

            // Usamos tu componente EditableCell
            return (
                <EditableCell
                    initialValue={initialValue}
                    disabled={!canEdit}
                    onSave={(newValue) => {
                        // TRUCO: Buscamos la función 'updateData' dentro del "Buzón" (meta) de la tabla
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
                href={`/productos?origenIds=${row.original.id}`}
                title="Ver productos de este origen"
                className={getTableActionButtonClasses("primary")}
            >
                <EyeIcon className="w-3.5 h-3.5" />
                Ver productos
            </Link>
        ),
    },
];

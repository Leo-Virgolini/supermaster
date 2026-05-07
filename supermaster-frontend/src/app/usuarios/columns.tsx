"use client";

import { ColumnDef } from "@tanstack/react-table";
import { KeyIcon, PencilSquareIcon } from "@heroicons/react/24/outline";
import { formatFechaAR } from "../utils/formatDate";
import type { UsuarioDTO } from "./types";
import { getRoleBadgeClasses } from "../utils/roleBadge";
import TableActionButton from "../components/Table/core/TableActionButton";

function EstadoBadge({ activo }: { activo: boolean }) {
    return (
        <span className={`inline-flex rounded-full border px-2 py-0.5 text-[11px] font-semibold ${
            activo
                ? "border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-500/15 dark:text-emerald-200"
                : "border-slate-200 bg-slate-100 text-slate-600 dark:border-slate-600 dark:bg-slate-700/80 dark:text-slate-200"
        }`}>
            {activo ? "Activo" : "Inactivo"}
        </span>
    );
}

function RolBadge({ rol }: { rol?: string | null }) {
    const value = (rol || "Sin rol").toUpperCase();

    return (
        <span className={`inline-flex rounded-full border px-2 py-0.5 text-[11px] font-semibold ${getRoleBadgeClasses(value)}`}>
            {value}
        </span>
    );
}

export function getColumns(
    onEdit: (usuario: UsuarioDTO) => void,
    onPassword: (usuario: UsuarioDTO) => void,
    canEdit: boolean,
): ColumnDef<UsuarioDTO>[] {
    return [
        {
            id: "select",
            header: ({ table }) => (
                <input type="checkbox" checked={table.getIsAllPageRowsSelected()} onChange={(e) => table.toggleAllPageRowsSelected(!!e.target.checked)} className="w-4 h-4 cursor-pointer align-middle" />
            ),
            cell: ({ row }) => (
                <input type="checkbox" checked={row.getIsSelected()} onChange={(e) => row.toggleSelected(!!e.target.checked)} className="w-4 h-4 cursor-pointer align-middle" />
            ),
            enableSorting: false,
            size: 40,
        },
        { accessorKey: "id", header: "ID", size: 50 },
        { accessorKey: "username", header: "Usuario", size: 150 },
        { accessorKey: "nombreCompleto", header: "Nombre completo", size: 220 },
        {
            id: "rol",
            header: "Rol",
            cell: ({ row }) => <RolBadge rol={row.original.rol?.nombre} />,
            size: 150,
        },
        {
            id: "activo",
            header: "Activo",
            cell: ({ row }) => <EstadoBadge activo={!!row.original.activo} />,
            size: 90,
        },
        {
            id: "permisos",
            header: "Permisos",
            cell: ({ row }) => <span className="text-xs text-gray-500 dark:text-slate-400">{row.original.permisos?.length || 0}</span>,
            size: 80,
        },
        {
            accessorKey: "ultimoLogin",
            header: "Último login",
            cell: ({ getValue }) => {
                const val = getValue() as string | null;
                return val
                    ? <span className="text-xs text-gray-500 dark:text-slate-400">{formatFechaAR(val)}</span>
                    : <span className="text-xs text-gray-300 dark:text-slate-600">Nunca</span>;
            },
            size: 150,
        },
        {
            accessorKey: "fechaModificacion",
            header: "F. Modificación",
            cell: ({ getValue }) => <span className="text-xs text-gray-500 dark:text-slate-400">{formatFechaAR(getValue() as string)}</span>,
            size: 150,
        },
        {
            id: "acciones",
            header: "Acciones",
            enableSorting: false,
            size: 150,
            cell: ({ row }) => (
                <div className="flex items-center justify-center gap-1.5">
                    <TableActionButton
                        onClick={() => onEdit(row.original)}
                        disabled={!canEdit}
                        title="Editar usuario"
                        icon={<PencilSquareIcon className="w-3.5 h-3.5" />}
                        tone="primary"
                    >
                        Editar
                    </TableActionButton>
                    <TableActionButton
                        onClick={() => onPassword(row.original)}
                        disabled={!canEdit}
                        title="Cambiar clave"
                        icon={<KeyIcon className="w-3.5 h-3.5" />}
                        tone="warning"
                    >
                        Clave
                    </TableActionButton>
                </div>
            ),
        },
    ];
}

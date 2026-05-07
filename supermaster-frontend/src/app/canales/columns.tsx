"use client";

import { ColumnDef } from "@tanstack/react-table";
import { EyeIcon, ShareIcon } from "@heroicons/react/24/outline";
import Link from "next/link";
import { CanalDTO } from "./types";
import EditableCell from "../components/Table/core/EditableCell";
import { EditableRelationCell } from "../components/EditableRelationCell/EditableRelationCell";
import { getCanalColor, CANAL_BADGE_CLASS } from "../utils/canalColors";
import TableActionButton, { getTableActionButtonClasses } from "../components/Table/core/TableActionButton";

export function getColumns(
    onOpenConceptos: (canal: CanalDTO) => void,
    searchCanalesFn: (q: string) => Promise<{ id: number; label: string }[]>,
    canEdit = true,
): ColumnDef<CanalDTO>[] {
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
                    onSave={(newValue) => {
                        (table.options.meta as any)?.updateData?.(row.index, column.id, newValue);
                    }}
                    renderDisplay={(value, onClick) => {
                        const colors = getCanalColor(String(value));
                        return (
                            <button
                                type="button"
                                onClick={onClick}
                                disabled={!canEdit}
                                className={`inline-flex min-h-[28px] items-center ${CANAL_BADGE_CLASS} transition ${
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
            accessorKey: "canalBaseId",
            header: "Canal Base",
            meta: { editable: true },
            cell: ({ row, table }) => (
                <EditableRelationCell
                    initialId={row.original.canalBaseId ?? null}
                    initialName=""
                    onSave={(newId) =>
                        (table.options.meta as any)?.updateData?.(row.index, "canalBaseId", newId)
                    }
                    loadOptions={searchCanalesFn}
                    placeholder="Buscar canal base..."
                    endpoint="canales"
                    labelKey="nombre"
                    nullable
                    disabled={!canEdit}
                    renderDisplay={(name, onClick) => {
                        const colors = getCanalColor(name || "Sin canal");
                        return (
                            <button
                                type="button"
                                onClick={onClick}
                                disabled={!canEdit}
                                className={`inline-flex min-h-[28px] items-center ${CANAL_BADGE_CLASS} transition ${
                                    canEdit ? "cursor-pointer hover:brightness-[0.97]" : "cursor-default"
                                } ${colors}`}
                                title={canEdit ? `${name} — Click para editar` : name}
                            >
                                {name}
                            </button>
                        );
                    }}
                />
            ),
        },
        {
            id: "acciones",
            header: "Acciones",
            size: 200,
            enableSorting: false,
            cell: ({ row }) => (
                <div className="flex items-center gap-1.5">
                    <TableActionButton
                        onClick={() => onOpenConceptos(row.original)}
                        title="Ver conceptos de cálculo del canal"
                        icon={<EyeIcon className="w-3.5 h-3.5" />}
                        tone="primary"
                    >
                        Conceptos
                    </TableActionButton>
                    <Link
                        href={`/canal-formula?canalId=${row.original.id}`}
                        title="Ver Fórmula del Canal"
                        className={getTableActionButtonClasses("accent")}
                    >
                        <ShareIcon className="w-3.5 h-3.5" />
                        Fórmula
                    </Link>
                </div>
            ),
        },
    ];
}

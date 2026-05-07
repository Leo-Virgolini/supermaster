"use client";
import { ColumnDef } from "@tanstack/react-table";
import { CanalConceptoCuotaDTO } from "./types";
import EditableCell from "../components/Table/core/EditableCell";
import { EditableRelationCell } from "../components/EditableRelationCell/EditableRelationCell";
import { searchCanalesAPI } from "../canales/canalesService";

import { getCanalColor, CANAL_BADGE_CLASS } from "../utils/canalColors";

const formatInteres = (value: number) =>
    `${value.toLocaleString("es-AR", Number.isInteger(value)
        ? { minimumFractionDigits: 0, maximumFractionDigits: 0 }
        : { minimumFractionDigits: 1, maximumFractionDigits: 3 })}%`;

const getInteresClassName = (value: number) => {
    if (value < 0) return "font-semibold text-rose-700 dark:text-rose-300";
    if (value > 0) return "font-semibold text-emerald-700 dark:text-emerald-300";
    return "font-semibold text-slate-500 dark:text-slate-400";
};

export const getColumns = (canEdit = true): ColumnDef<CanalConceptoCuotaDTO>[] => [
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
    { accessorKey: "id", header: "ID", size: 50 },
    {
        accessorKey: "canalNombre",
        header: "Canal",
        enableColumnFilter: true,
        meta: { filterVariant: "text", editable: true },
        cell: ({ row, table }) => (
            <EditableRelationCell
                initialName={row.original.canalNombre || ""}
                initialId={row.original.canalId}
                endpoint="canales"
                labelKey="nombre"
                placeholder="Buscar canal..."
                loadOptions={async (q) => {
                    const res = await searchCanalesAPI(q);
                    return res.content.map((c: any) => ({ id: c.id, label: c.nombre }));
                }}
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
                            title={canEdit ? `${name} - Click para editar` : name}
                        >
                            {name}
                        </button>
                    );
                }}
                disabled={!canEdit}
                onSave={(newId) => (table.options.meta as any)?.updateData?.(row.index, "canalId", newId)}
            />
        ),
        size: 180,
    },
    {
        accessorKey: "descripcion",
        header: "Descripción",
        enableColumnFilter: true,
        meta: { filterVariant: "text", editable: true },
        cell: ({ getValue, row, column, table }) => (
            <EditableCell
                initialValue={getValue() as string || ""}
                className="font-medium text-slate-700 dark:text-slate-200"
                disabled={!canEdit}
                onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)}
            />
        ),
        size: 280,
    },
    {
        accessorKey: "cuotas",
        header: "Cuotas",
        size: 140,
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (
            <EditableCell
                initialValue={getValue() as number}
                type="number"
                className="font-semibold text-slate-700 dark:text-slate-200"
                disabled={!canEdit}
                onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, Number(val))}
            />
        ),
    },
    {
        accessorKey: "porcentaje",
        header: "Interés (%)",
        size: 100,
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => {
            const currentValue = Number(getValue() ?? 0);
            return (
            <EditableCell
                initialValue={currentValue}
                type="number"
                displayFormatter={(value) => formatInteres(Number(value))}
                className={getInteresClassName(currentValue)}
                disabled={!canEdit}
                onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, Number(val))}
            />
            );
        },
    },
];

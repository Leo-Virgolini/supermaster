"use client";
import { ColumnDef } from "@tanstack/react-table";
import { CanalConceptoReglaDTO } from "./types";
import { PencilSquareIcon } from "@heroicons/react/24/outline";
import TableActionButton from "../components/Table/core/TableActionButton";
import Tooltip from "../components/Tooltip/Tooltip";

import { getCanalColor, CANAL_BADGE_CLASS } from "../utils/canalColors";

interface ColumnProps {
    onEdit: (item: CanalConceptoReglaDTO) => void;
    canEdit?: boolean;
}

export const getColumns = ({ onEdit, canEdit = true }: ColumnProps): ColumnDef<CanalConceptoReglaDTO>[] => [
    {
        id: "select",
        header: ({ table }) => (
            <input
                type="checkbox"
                checked={table.getIsAllPageRowsSelected()}
                onChange={(e) => table.toggleAllPageRowsSelected(!!e.target.checked)}
                className={`w-3.5 h-3.5 align-middle ${canEdit ? "cursor-pointer" : "cursor-default opacity-60"}`}
                disabled={!canEdit}
            />
        ),
        cell: ({ row }) => (
            <input
                type="checkbox"
                checked={row.getIsSelected()}
                onChange={(e) => row.toggleSelected(!!e.target.checked)}
                className={`w-3.5 h-3.5 align-middle ${canEdit ? "cursor-pointer" : "cursor-default opacity-60"}`}
                disabled={!canEdit}
            />
        ),
        size: 40,
    },
    { accessorKey: "id", header: "ID", size: 50 },
    {
        accessorKey: "canalNombre",
        header: "Canal",
        enableColumnFilter: true,
        meta: { filterVariant: "text" },
        cell: ({ getValue }) => {
            const name = (getValue() as string) ?? "";
            const colors = getCanalColor(name || "Sin canal");
            return (
                <span className={`${CANAL_BADGE_CLASS} ${colors}`}>
                    {name || "Sin canal"}
                </span>
            );
        },
    },
    {
        accessorKey: "conceptoNombre",
        header: "Concepto Afectado",
        enableColumnFilter: true,
        meta: { filterVariant: "text" },
        cell: ({ getValue, row }) => {
            const nombre = (getValue() as string) ?? "";
            const descripcion = row.original.conceptoDescripcion;
            const porcentaje = row.original.conceptoPorcentaje;
            const aplicaSobre = row.original.conceptoAplicaSobre;
            const tienePorcentaje = porcentaje != null;
            const tieneTooltip = !!descripcion || tienePorcentaje;
            const porcentajeFormateado = tienePorcentaje
                ? `${porcentaje > 0 ? "+" : ""}${porcentaje}%`
                : null;
            const tooltipContent = tieneTooltip ? (
                <div className="flex flex-col gap-1">
                    {tienePorcentaje && (
                        <div className="flex items-center gap-2">
                            <span
                                className={`font-mono text-sm font-bold ${
                                    porcentaje! < 0 ? "text-red-300" : porcentaje! > 0 ? "text-emerald-300" : "text-slate-200"
                                }`}
                            >
                                {porcentajeFormateado}
                            </span>
                            {aplicaSobre && (
                                <span className="text-[10px] uppercase tracking-wide text-slate-300">
                                    sobre {aplicaSobre.replace(/_/g, " ").toLowerCase()}
                                </span>
                            )}
                        </div>
                    )}
                    {descripcion && <span>{descripcion}</span>}
                </div>
            ) : null;
            return (
                <Tooltip
                    content={tooltipContent}
                    disabled={!tieneTooltip}
                    className={tieneTooltip ? "cursor-help underline decoration-dotted decoration-gray-400 underline-offset-4" : undefined}
                >
                    {nombre}
                </Tooltip>
            );
        }
    },
    {
        accessorKey: "tipoRegla", // Corregido de 'tipoAccion'
        header: "Regla",
        cell: ({ getValue }) => {
            const val = getValue() as string;
            const color = val === "INCLUIR" ? "bg-green-100 text-green-800" : "bg-red-100 text-red-800";
            return (
                <span className={`px-2 py-1 rounded text-xs font-bold ${color}`}>
                    {val}
                </span>
            );
        },
        meta: { filterVariant: "select" }
    },
    {
        accessorKey: "tipoNombre",
        header: "Tipo",
        cell: ({ getValue }) => <span className="text-gray-600">{(getValue() as string) ?? "-"}</span>,
        enableColumnFilter: false,
    },
    {
        accessorKey: "clasifGralNombre",
        header: "Rubro",
        cell: ({ getValue }) => <span className="text-gray-600">{(getValue() as string) ?? "-"}</span>,
        enableColumnFilter: false,
    },
    {
        accessorKey: "clasifGastroNombre",
        header: "Gastro",
        cell: ({ getValue }) => <span className="text-gray-600">{(getValue() as string) ?? "-"}</span>,
        enableColumnFilter: false,
    },
    {
        accessorKey: "marcaNombre",
        header: "Marca",
        cell: ({ getValue }) => <span className="text-gray-600">{(getValue() as string) ?? "-"}</span>,
        enableColumnFilter: false,
    },
    {
        accessorKey: "tag",
        header: "Tag",
        cell: ({ getValue }) => {
            const val = getValue() as string | null;
            if (!val) return <span className="text-gray-400">-</span>;
            const BADGE: Record<string, string> = {
                MAQUINA:  "bg-indigo-100 text-indigo-800",
                REPUESTO: "bg-sky-100 text-sky-800",
                MENAJE:   "bg-emerald-100 text-emerald-800",
            };
            return (
                <span className={`px-2 py-0.5 rounded text-xs font-bold ${BADGE[val] ?? "bg-gray-100 text-gray-700"}`}>
                    {val}
                </span>
            );
        },
        enableColumnFilter: false,
    },
    {
        accessorKey: "tieneEnvio",
        header: "Tiene Envío",
        cell: ({ getValue }) => {
            const v = getValue() as boolean | null;
            if (v === null || v === undefined) return <span className="text-gray-400">-</span>;
            return <span className={`text-xs font-semibold ${v ? "text-blue-700" : "text-gray-400"}`}>{v ? "Sí" : "No"}</span>;
        },
        enableColumnFilter: false,
    },
    {
        id: "actions",
        header: "Acciones",
        cell: ({ row }) => (
            <TableActionButton
                onClick={() => onEdit(row.original)}
                disabled={!canEdit}
                title="Editar regla"
                icon={<PencilSquareIcon className="w-3.5 h-3.5" />}
                tone="primary"
            >
                Editar
            </TableActionButton>
        )
    }
];

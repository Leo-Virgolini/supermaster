"use client";
import { ColumnDef } from "@tanstack/react-table";
import { ConceptoGastoDTO } from "./conceptosGastosService";
import EditableCell from "../components/Table/core/EditableCell";
import { BuildingStorefrontIcon } from "@heroicons/react/24/outline";
import { NATURALEZAS_INFO, getNaturalezaInfo } from "../canal-formula/naturaleza";
import { APLICA_SOBRE_INFO, getAplicaSobreInfo, getAplicaSobreBadgeClass } from "../canal-formula/aplica-sobre";
import { ETAPAS_INFO } from "../canal-formula/etapas";

export const getColumns = (canEdit = true, onVerCanales?: (id: number) => void): ColumnDef<ConceptoGastoDTO>[] => [
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
        size: 220,
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (
            <EditableCell
                initialValue={getValue() as string}
                className="font-bold text-slate-800 dark:text-slate-100"
                disabled={!canEdit}
                onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)}
            />
        ),
    },
    {
        accessorKey: "porcentaje",
        header: "Porcentaje (%)",
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => {
            const val = Number(getValue() ?? 0);
            const isFlag = (row.original.aplicaSobre || "").startsWith("FLAG_");
            if (isFlag) {
                return (
                    <span className="flex items-center justify-center text-base" title="Flag — el porcentaje no aplica">
                        🚩
                    </span>
                );
            }
            const colorClass = val > 0
                ? "font-semibold text-emerald-700 dark:text-emerald-300"
                : val < 0
                    ? "font-semibold text-rose-700 dark:text-rose-300"
                    : "font-semibold text-slate-400 dark:text-slate-500";
            return (
                <EditableCell
                    initialValue={val}
                    type="number"
                    suffix="%"
                    className={colorClass}
                    disabled={!canEdit}
                    onSave={(v) => (table.options.meta as any)?.updateData?.(row.index, column.id, Number(v))}
                />
            );
        },
    },
    {
        accessorKey: "aplicaSobre",
        header: "Aplica Sobre",
        size: 170,
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => {
            const val = getValue() as string;
            const info = getAplicaSobreInfo(val);
            const badgeClass = getAplicaSobreBadgeClass(val);
            return (
                <div className="flex items-center justify-center gap-1">
                    <select
                        className={`text-xs font-bold px-2 py-0.5 rounded border-0 outline-none ${canEdit ? "cursor-pointer" : "cursor-default appearance-none"} ${badgeClass}`}
                        value={info.id}
                        disabled={!canEdit}
                        title={`${info.label} — ${info.descripcion}`}
                        onChange={(e) => (table.options.meta as any)?.updateData?.(row.index, column.id, e.target.value)}
                    >
                        {ETAPAS_INFO.map((etapa) => (
                            <optgroup key={etapa.id} label={`${etapa.icon} ${etapa.label}`}>
                                {APLICA_SOBRE_INFO.filter((a) => a.etapa === etapa.id).map((a) => (
                                    <option key={a.id} value={a.id}>{a.icon} {a.labelCorto}</option>
                                ))}
                            </optgroup>
                        ))}
                    </select>
                </div>
            );
        },
    },
    {
        accessorKey: "naturaleza",
        header: "Naturaleza",
        size: 180,
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => {
            const val = getValue() as string;
            const info = getNaturalezaInfo(val);
            return (
                <div className="flex items-center justify-center">
                    <select
                        className={`text-xs font-bold px-2 py-0.5 rounded border-0 outline-none ${canEdit ? "cursor-pointer" : "cursor-default appearance-none"} ${info.badgeClass}`}
                        value={info.id}
                        disabled={!canEdit}
                        title={`${info.descripcion}\n\nSeleccioná "↺ Auto" para que vuelva a usar el default del aplicaSobre.`}
                        onChange={(e) => {
                            // value === "" significa "↺ Auto" → enviar null para que el backend
                            // borre el override y la columna vuelva a NULL.
                            const next = e.target.value === "" ? null : e.target.value;
                            (table.options.meta as any)?.updateData?.(row.index, column.id, next);
                        }}
                    >
                        <option value="">↺ Auto (default del aplicaSobre)</option>
                        {NATURALEZAS_INFO.map((n) => (
                            <option key={n.id} value={n.id}>{n.icon} {n.label}</option>
                        ))}
                    </select>
                </div>
            );
        },
    },
    {
        accessorKey: "descripcion",
        header: "Descripción",
        size: 350,
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (
            <EditableCell
                initialValue={getValue() as string || ""}
                className="text-slate-500 dark:text-slate-400 italic"
                disabled={!canEdit}
                onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)}
            />
        ),
    },
    {
        id: "acciones",
        header: "Canales",
        size: 70,
        enableSorting: false,
        meta: { center: true },
        cell: ({ row }) => (
            <div className="flex justify-center">
                <button
                    onClick={() => onVerCanales?.(row.original.id)}
                    className="flex items-center gap-1 px-2 py-1 rounded-md text-xs font-medium text-cyan-700 dark:text-cyan-300 bg-cyan-50 dark:bg-cyan-900/30 hover:bg-cyan-100 dark:hover:bg-cyan-900/50 border border-cyan-200 dark:border-cyan-800 transition-colors"
                    title="Ver canales que usan este concepto"
                >
                    <BuildingStorefrontIcon className="w-3.5 h-3.5" />
                    Ver
                </button>
            </div>
        ),
    },
];

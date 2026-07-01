"use client";

import { ColumnDef } from "@tanstack/react-table";
import { MlaDTO } from "./mlasService";
import EditableCell from "../components/Table/core/EditableCell";
import { formatFechaAR } from "../utils/formatDate";
import { ArrowPathIcon, EyeIcon } from "@heroicons/react/24/outline";
import TableActionButton from "../components/Table/core/TableActionButton";
import { mlVerURL } from "../productos/productosService";

interface CalcLoading {
    envio: boolean;
    comision: boolean;
}

const CELL = {
    code: "font-mono font-medium tracking-tight whitespace-nowrap",
    money: "font-mono font-semibold text-emerald-700 dark:text-emerald-300 whitespace-nowrap",
    numeric: "font-mono text-gray-600 dark:text-slate-300 whitespace-nowrap",
    date: "text-xs font-mono text-gray-500 dark:text-slate-400 whitespace-nowrap",
} as const;

export const getColumns = (
    onCalcEnvio: (mla: MlaDTO) => void,
    onCalcComision: (mla: MlaDTO) => void,
    loadingMap: Record<number, CalcLoading>,
    onVerSkus: (mla: MlaDTO) => void,
    canEdit = true,
): ColumnDef<MlaDTO>[] => [
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
    {
        accessorKey: "mla",
        header: "MLA",
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (
            <EditableCell initialValue={getValue() as string} className={CELL.code} disabled={!canEdit} onSave={(val) => table.options.meta?.updateData?.(row.index, column.id, val)} />
        ),
    },
    {
        accessorKey: "mlau",
        header: "MLAU",
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (
            <EditableCell initialValue={(getValue() as string) || ""} className={CELL.code} disabled={!canEdit} onSave={(val) => table.options.meta?.updateData?.(row.index, column.id, val || null)} />
        ),
    },
    {
        accessorKey: "precioEnvio",
        header: "Precio Envío ($)",
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (
            <EditableCell initialValue={String(getValue() ?? "")} type="number" prefix="$ " className={CELL.money} disabled={!canEdit} onSave={(val) => table.options.meta?.updateData?.(row.index, column.id, val === "" ? null : Number(val))} />
        ),
    },
    {
        accessorKey: "comisionPorcentaje",
        header: "Comisión (%)",
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (
            <EditableCell initialValue={String(getValue() ?? "")} type="number" suffix="%" className={CELL.numeric} disabled={!canEdit} onSave={(val) => table.options.meta?.updateData?.(row.index, column.id, val === "" ? null : Number(val))} />
        ),
    },
    {
        accessorKey: "topePromocion",
        header: "Tope Promo",
        meta: { editable: true },
        cell: ({ getValue, row, column, table }) => (
            <EditableCell initialValue={String(getValue() ?? "")} type="number" suffix="%" className={CELL.numeric} disabled={!canEdit} onSave={(val) => table.options.meta?.updateData?.(row.index, column.id, val === "" ? null : Number(val))} />
        ),
    },
    {
        accessorKey: "fechaCalculoEnvio",
        header: "F. Cálc. Envío",
        size: 130,
        cell: ({ getValue }) => <span className={CELL.date}>{formatFechaAR(getValue() as string)}</span>,
    },
    {
        accessorKey: "fechaCalculoComision",
        header: "F. Cálc. Comisión",
        size: 140,
        cell: ({ getValue }) => <span className={CELL.date}>{formatFechaAR(getValue() as string)}</span>,
    },
    {
        id: "skus",
        header: "SKUs",
        size: 90,
        enableSorting: false,
        cell: ({ row }) => (
            <div className="flex justify-center">
                <TableActionButton
                    onClick={() => onVerSkus(row.original)}
                    title="Ver productos asociados a este MLA"
                    icon={<EyeIcon className="w-3.5 h-3.5" />}
                    tone="primary"
                >
                    Ver SKUs
                </TableActionButton>
            </div>
        ),
    },
    {
        id: "ver",
        header: "Ver",
        size: 70,
        enableSorting: false,
        cell: ({ row }) => {
            const codigo = row.original.mla?.trim();
            if (!codigo) return null;
            return (
                <div className="flex justify-center">
                    <a href={mlVerURL(codigo)} target="_blank" rel="noreferrer" className="text-xs text-blue-600 hover:underline dark:text-blue-400">Ver ↗</a>
                </div>
            );
        },
    },
    {
        id: "acciones",
        header: "Calcular ML",
        size: 170,
        enableSorting: false,
        cell: ({ row }) => {
            const mla = row.original;
            const loading = loadingMap[mla.id] ?? { envio: false, comision: false };
            const sinMla = !mla.mla?.trim();

            return (
                <div className="flex items-center justify-center gap-1.5 whitespace-nowrap">
                    <TableActionButton
                        title={sinMla ? "Sin código MLA" : "Calcular costo de envío desde ML"}
                        disabled={loading.envio || sinMla}
                        onClick={() => onCalcEnvio(mla)}
                        icon={<ArrowPathIcon className={`w-3.5 h-3.5 ${loading.envio ? "animate-spin" : ""}`} />}
                        tone="success"
                    >
                        Envío
                    </TableActionButton>
                    <TableActionButton
                        title={sinMla ? "Sin código MLA" : "Calcular comisión desde ML"}
                        disabled={loading.comision || sinMla}
                        onClick={() => onCalcComision(mla)}
                        icon={<ArrowPathIcon className={`w-3.5 h-3.5 ${loading.comision ? "animate-spin" : ""}`} />}
                        tone="accent"
                    >
                        Comisión
                    </TableActionButton>
                </div>
            );
        },
    },
];

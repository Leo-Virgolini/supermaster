"use client";
import { ColumnDef } from "@tanstack/react-table";
import { ProductoCanalPrecioDTO } from "./types";
import { EyeIcon, ArrowPathIcon, ChevronDownIcon, ChevronRightIcon } from "@heroicons/react/24/outline";
import TableActionButton from "../components/Table/core/TableActionButton";

interface ColumnProps {
    onViewPrices: (producto: ProductoCanalPrecioDTO) => void;
    onRecalcular: (productoId: number) => void;
    calcLoading: Record<number, boolean>;
    globalLoading?: boolean;
    expandedRows: Set<number>;
    onToggleExpand: (id: number) => void;
}

export const getColumns = ({ onViewPrices, onRecalcular, calcLoading, globalLoading, expandedRows, onToggleExpand }: ColumnProps): ColumnDef<ProductoCanalPrecioDTO>[] => [
    {
        id: "expand",
        size: 40,
        header: "",
        cell: ({ row }) => {
            const isExpanded = expandedRows.has(row.original.id);
            return (
                <button
                    onClick={() => onToggleExpand(row.original.id)}
                    className="text-gray-400 hover:text-blue-600 transition-colors"
                    title={isExpanded ? "Colapsar" : "Expandir precios"}
                >
                    {isExpanded
                        ? <ChevronDownIcon className="w-4 h-4" />
                        : <ChevronRightIcon className="w-4 h-4" />
                    }
                </button>
            );
        }
    },
    { accessorKey: "id", header: "ID", size: 50 },
    {
        accessorKey: "sku",
        header: "SKU",
        size: 80,
        enableColumnFilter: true,
    },
    {
        accessorKey: "descripcion",
        header: "Producto",
        enableColumnFilter: true,
    },
    {
        accessorKey: "costo",
        header: "Costo Base",
        enableColumnFilter: false,
        cell: ({ getValue }) => {
            const val = getValue() as number;
            return val ? `$ ${val.toLocaleString("es-AR")}` : "-";
        }
    },
    {
        id: "acciones",
        header: "Acciones",
        enableColumnFilter: false,
        cell: ({ row }) => {
            const prod = row.original;
            const loading = calcLoading[prod.id] ?? false;
            const disabled = loading || !!globalLoading;
            return (
                <div className="flex gap-2">
                    <TableActionButton
                        onClick={() => onViewPrices(prod)}
                        title="Ver precios"
                        icon={<EyeIcon className="w-3.5 h-3.5" />}
                        tone="primary"
                    >
                        Ver Precios
                    </TableActionButton>
                    <TableActionButton
                        onClick={() => onRecalcular(prod.id)}
                        disabled={disabled}
                        title="Recalcular precio"
                        icon={<ArrowPathIcon className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />}
                        tone="success"
                    >
                        {loading ? "Calculando..." : "Recalcular"}
                    </TableActionButton>
                </div>
            );
        }
    }
];

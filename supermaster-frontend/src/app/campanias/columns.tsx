"use client";

import Link from "next/link";
import { EyeIcon } from "@heroicons/react/24/outline";
import { ColumnDef } from "@tanstack/react-table";
import { CampaniaDTO } from "./types";
import { getTableActionButtonClasses } from "../components/Table/core/TableActionButton";

export function getColumns(canEdit = true): ColumnDef<CampaniaDTO>[] {
	return [
		{ accessorKey: "id", header: "ID", size: 50, enableColumnFilter: false },
		{ accessorKey: "nombre", header: "Campaña", enableColumnFilter: false },
		{
			accessorKey: "cantidadProductos",
			header: "Productos",
			size: 90,
			enableSorting: false,
			enableColumnFilter: false,
		},
		{
			accessorKey: "fechaDesde",
			header: "Desde",
			size: 130,
			meta: { editable: true },
			cell: ({ getValue, row, table }) => (
				<input
					type="date"
					defaultValue={(getValue() as string) ?? ""}
					disabled={!canEdit}
					onChange={(e) =>
						(table.options.meta as any)?.updateData?.(row.index, "fechaDesde", e.target.value || null)
					}
					className="w-full text-sm text-center bg-transparent border-0 focus:ring-1 focus:ring-blue-400 rounded px-1 py-0.5 cursor-pointer disabled:cursor-default disabled:opacity-60"
				/>
			),
			enableColumnFilter: false,
		},
		{
			accessorKey: "fechaHasta",
			header: "Hasta",
			size: 130,
			meta: { editable: true },
			cell: ({ getValue, row, table }) => (
				<input
					type="date"
					defaultValue={(getValue() as string) ?? ""}
					disabled={!canEdit}
					onChange={(e) =>
						(table.options.meta as any)?.updateData?.(row.index, "fechaHasta", e.target.value || null)
					}
					className="w-full text-sm text-center bg-transparent border-0 focus:ring-1 focus:ring-blue-400 rounded px-1 py-0.5 cursor-pointer disabled:cursor-default disabled:opacity-60"
				/>
			),
			enableColumnFilter: false,
		},
		{
			accessorKey: "activa",
			header: "Activa",
			size: 80,
			enableSorting: false,
			cell: ({ getValue, row, table }) => (
				<input
					type="checkbox"
					checked={!!getValue()}
					disabled={!canEdit}
					onChange={(e) =>
						(table.options.meta as any)?.updateData?.(row.index, "activa", e.target.checked)
					}
					className="w-4 h-4 cursor-pointer align-middle"
				/>
			),
			enableColumnFilter: false,
		},
		{
			accessorKey: "fechaUltimaSync",
			header: "Última sync",
			size: 160,
			enableColumnFilter: false,
			cell: ({ getValue }) => {
				const v = getValue() as string | null;
				return v ? new Date(v).toLocaleString("es-AR") : "—";
			},
		},
		{
			id: "detalle",
			header: "Productos",
			size: 110,
			enableSorting: false,
			cell: ({ row }) => (
				<Link
					href={`/campanias/${row.original.id}`}
					title="Ver productos de la campaña"
					className={getTableActionButtonClasses("primary")}
				>
					<EyeIcon className="w-3.5 h-3.5" />
					Ver productos
				</Link>
			),
		},
	];
}

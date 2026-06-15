"use client";

import { ColumnDef } from "@tanstack/react-table";
import { CampaniaProductoDTO } from "../types";
import EditableCell from "../../components/Table/core/EditableCell";

export function getColumns(canEdit = true): ColumnDef<CampaniaProductoDTO>[] {
	return [
		// sku/descripcion/costo viven en el Producto asociado, no en CampaniaProducto:
		// el backend no puede ordenar por ellas (PropertyReferenceException) → no ordenables.
		{ accessorKey: "sku", header: "SKU", size: 120, enableColumnFilter: false, enableSorting: false },
		{ accessorKey: "descripcion", header: "Descripción", enableColumnFilter: false, enableSorting: false },
		{
			accessorKey: "costo",
			header: "Costo",
			size: 110,
			enableColumnFilter: false,
			enableSorting: false,
			cell: ({ getValue }) => {
				const v = getValue() as number | null;
				return v != null ? v.toLocaleString("es-AR", { style: "currency", currency: "ARS" }) : "—";
			},
		},
		{
			accessorKey: "precioManual",
			header: "Precio campaña",
			size: 140,
			meta: { editable: true },
			cell: ({ getValue, row, table }) => (
				<EditableCell
					initialValue={getValue() != null ? String(getValue()) : ""}
					type="number"
					disabled={!canEdit}
					onSave={(v) => {
						const parsed = v === "" || v == null ? null : Number(v);
						(table.options.meta as any)?.updateData?.(row.index, "precioManual", parsed);
					}}
				/>
			),
			enableColumnFilter: false,
		},
	];
}

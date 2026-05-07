"use client";
import { ColumnDef } from "@tanstack/react-table";
import { ReglaDescuentoDTO } from "./types";
import EditableCell from "../components/Table/core/EditableCell";
import { EditableRelationCell } from "../components/EditableRelationCell/EditableRelationCell";
import { getCanalColor, CANAL_BADGE_CLASS } from "../utils/canalColors";

type SearchFn = (q: string) => Promise<{ id: number; label: string }[]>;

export function getColumns(
    searchCanalesFn: SearchFn,
    searchCatalogosFn: SearchFn,
    searchClasifGralFn: SearchFn,
    searchClasifGastroFn: SearchFn,
    canEdit = true,
): ColumnDef<ReglaDescuentoDTO>[] {
    return [
        {
            id: "select",
            header: ({ table }) => (
                <input type="checkbox" checked={table.getIsAllPageRowsSelected()} onChange={(e) => table.toggleAllPageRowsSelected(!!e.target.checked)} className="w-4 h-4 cursor-pointer align-middle" />
            ),
            cell: ({ row }) => (
                <input type="checkbox" checked={row.getIsSelected()} onChange={(e) => row.toggleSelected(!!e.target.checked)} className="w-4 h-4 cursor-pointer align-middle" />
            ),
            enableHiding: true,
            size: 40,
        },
        { accessorKey: "id", header: "ID", size: 50 },
        {
            accessorKey: "canalId",
            header: "Canal",
            meta: { editable: true },
            cell: ({ row, table }) => (
                <EditableRelationCell
                    initialId={row.original.canalId ?? null}
                    initialName=""
                    onSave={(newId) => (table.options.meta as any)?.updateData?.(row.index, "canalId", newId)}
                    loadOptions={searchCanalesFn}
                    placeholder="Buscar canal..."
                    endpoint="canales"
                    labelKey="nombre"
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
                                title={canEdit ? `${name} - Click para editar` : name}
                            >
                                {name}
                            </button>
                        );
                    }}
                />
            ),
        },
        {
            accessorKey: "catalogoId",
            header: "Catálogo",
            meta: { editable: true },
            cell: ({ row, table }) => (
                <EditableRelationCell
                    initialId={row.original.catalogoId ?? null}
                    initialName=""
                    onSave={(newId) => (table.options.meta as any)?.updateData?.(row.index, "catalogoId", newId)}
                    loadOptions={searchCatalogosFn}
                    placeholder="Buscar catálogo..."
                    endpoint="catalogos"
                    labelKey="nombre"
                    nullable
                    disabled={!canEdit}
                />
            ),
        },
        {
            accessorKey: "clasifGralId",
            header: "Clasif. Gral.",
            meta: { editable: true },
            cell: ({ row, table }) => (
                <EditableRelationCell
                    initialId={row.original.clasifGralId ?? null}
                    initialName=""
                    onSave={(newId) => (table.options.meta as any)?.updateData?.(row.index, "clasifGralId", newId)}
                    loadOptions={searchClasifGralFn}
                    placeholder="Buscar clasificación..."
                    endpoint="clasif-gral"
                    labelKey="nombre"
                    nullable
                    disabled={!canEdit}
                />
            ),
        },
        {
            accessorKey: "clasifGastroId",
            header: "Clasif. Gastro",
            meta: { editable: true },
            cell: ({ row, table }) => (
                <EditableRelationCell
                    initialId={row.original.clasifGastroId ?? null}
                    initialName=""
                    onSave={(newId) => (table.options.meta as any)?.updateData?.(row.index, "clasifGastroId", newId)}
                    loadOptions={searchClasifGastroFn}
                    placeholder="Buscar clasif. gastro..."
                    endpoint="clasif-gastro"
                    labelKey="nombre"
                    nullable
                    disabled={!canEdit}
                />
            ),
        },
        {
            accessorKey: "montoMinimo",
            header: "Monto Mínimo",
            meta: { editable: true },
            cell: ({ getValue, row, column, table }) => (
                <EditableCell
                    initialValue={getValue() as number}
                    type="number"
                    prefix="$ "
                    disabled={!canEdit}
                    onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, Number(val))}
                />
            ),
        },
        {
            accessorKey: "descuentoPorcentaje",
            header: "Descuento (%)",
            meta: { editable: true },
            cell: ({ getValue, row, column, table }) => (
                <EditableCell
                    initialValue={getValue() as number}
                    type="number"
                    suffix="%"
                    disabled={!canEdit}
                    onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, Number(val))}
                />
            ),
        },
        {
            accessorKey: "prioridad",
            header: "Prioridad",
            meta: { editable: true },
            cell: ({ getValue, row, column, table }) => (
                <EditableCell
                    initialValue={getValue() as number}
                    type="number"
                    suffix="°"
                    disabled={!canEdit}
                    onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, Number(val))}
                />
            ),
        },
        {
            accessorKey: "activo",
            header: "Activo",
            meta: { editable: true },
            cell: ({ getValue, row, column, table }) => (
                <input
                    type="checkbox"
                    checked={getValue() as boolean}
                    disabled={!canEdit}
                    onChange={(e) => (table.options.meta as any)?.updateData?.(row.index, column.id, e.target.checked)}
                    className={`w-3.5 h-3.5 align-middle ${canEdit ? "cursor-pointer" : "cursor-default"}`}
                />
            ),
        },
        {
            accessorKey: "descripcion",
            header: "Descripción",
            meta: { editable: true },
            cell: ({ getValue, row, column, table }) => (
                <EditableCell
                    initialValue={getValue() as string || ""}
                    nullable
                    disabled={!canEdit}
                    onSave={(val) => (table.options.meta as any)?.updateData?.(row.index, column.id, val)}
                />
            ),
        },
    ];
}

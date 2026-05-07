"use client";
import { ColumnDef } from "@tanstack/react-table";
import { ArchiveBoxArrowDownIcon, EyeIcon, PaperAirplaneIcon } from "@heroicons/react/24/outline";
import { OrdenCompraDTO, EstadoOrdenCompra } from "./types";
import { formatFechaAR, EMPTY } from "../utils/formatDate";
import TableActionButton from "../components/Table/core/TableActionButton";

const ESTADO_BADGE: Record<EstadoOrdenCompra, { label: string; classes: string }> = {
  BORRADOR:         { label: "Borrador",         classes: "bg-gray-100 dark:bg-slate-700 text-gray-700 dark:text-slate-300 border border-gray-300 dark:border-slate-600" },
  ENVIADA:          { label: "Enviada",           classes: "bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300 border border-blue-300 dark:border-blue-800" },
  RECIBIDA_PARCIAL: { label: "Recibida Parcial",  classes: "bg-yellow-100 dark:bg-yellow-900/40 text-yellow-700 dark:text-yellow-300 border border-yellow-300 dark:border-yellow-800" },
  COMPLETA:         { label: "Completa",          classes: "bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-300 border border-green-300 dark:border-green-800" },
  CANCELADA:        { label: "Cancelada",         classes: "bg-red-100 dark:bg-red-900/40 text-red-700 dark:text-red-300 border border-red-300 dark:border-red-800" },
};

export function getColumns(
  onEnviar: (orden: OrdenCompraDTO) => void,
  onRecepcion: (orden: OrdenCompraDTO) => void,
  onVerDetalle: (orden: OrdenCompraDTO) => void,
  canEdit = true,
): ColumnDef<OrdenCompraDTO>[] {
  return [
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
      enableSorting: false,
      size: 40,
    },
    {
      accessorKey: "id",
      header: "ID",
      size: 50,
    },
    {
      accessorKey: "proveedorNombre",
      header: "Proveedor",
      cell: ({ getValue }) => (
        <span className="font-medium text-gray-800 dark:text-slate-200">{(getValue() as string) || EMPTY}</span>
      ),
    },
    {
      accessorKey: "estado",
      header: "Estado",
      cell: ({ getValue }) => {
        const estado = getValue() as EstadoOrdenCompra;
        const badge = ESTADO_BADGE[estado] ?? { label: estado, classes: "bg-gray-100 dark:bg-slate-700 text-gray-700 dark:text-slate-300 border border-gray-300 dark:border-slate-600" };
        return (
          <span className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${badge.classes}`}>
            {badge.label}
          </span>
        );
      },
    },
    {
      accessorKey: "fechaCreacion",
      header: "F. Creación",
      size: 140,
      cell: ({ getValue }) => (
        <span className="text-xs text-gray-500 dark:text-slate-400">{formatFechaAR(getValue() as string)}</span>
      ),
    },
    {
      accessorKey: "observaciones",
      header: "Observaciones",
      cell: ({ getValue }) => (
        <span className="text-xs text-gray-600 dark:text-slate-400 truncate max-w-xs block">{(getValue() as string) || EMPTY}</span>
      ),
    },
    {
      id: "acciones",
      header: "Acciones",
      enableSorting: false,
      cell: ({ row }) => {
        const orden = row.original;
        return (
          <div className="flex gap-2 items-center justify-center">
            <TableActionButton
              onClick={() => onVerDetalle(orden)}
              title="Ver detalle de la orden"
              icon={<EyeIcon className="w-3.5 h-3.5" />}
              tone="primary"
            >
              Detalle
            </TableActionButton>
            {orden.estado === "BORRADOR" && canEdit && (
              <TableActionButton
                onClick={() => onEnviar(orden)}
                title="Enviar orden al proveedor"
                icon={<PaperAirplaneIcon className="w-3.5 h-3.5" />}
                tone="success"
              >
                Enviar
              </TableActionButton>
            )}
            {(orden.estado === "ENVIADA" || orden.estado === "RECIBIDA_PARCIAL") && canEdit && (
              <TableActionButton
                onClick={() => onRecepcion(orden)}
                title="Registrar recepción de mercadería"
                icon={<ArchiveBoxArrowDownIcon className="w-3.5 h-3.5" />}
                tone="warning"
              >
                Recepción
              </TableActionButton>
            )}
          </div>
        );
      },
    },
  ];
}

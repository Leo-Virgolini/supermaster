"use client";
import { ColumnDef } from "@tanstack/react-table";
import { ConceptoGastoDTO } from "./conceptosGastosService";
import EditableCell from "../components/Table/core/EditableCell";
import { BuildingStorefrontIcon } from "@heroicons/react/24/outline";
import { NATURALEZAS_INFO, getNaturalezaInfo } from "../canal-formula/naturaleza";

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
                        ⚑
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
            // Color por etapa
            const COLOR: Record<string, string> = {
                GASTO_SOBRE_COSTO: "bg-blue-100 dark:bg-blue-900/40 text-blue-800 dark:text-blue-300", FLAG_FINANCIACION_PROVEEDOR: "bg-blue-100 dark:bg-blue-900/40 text-blue-800 dark:text-blue-300",
                AJUSTE_MARGEN_PUNTOS: "bg-green-100 dark:bg-green-900/40 text-green-800 dark:text-green-300", AJUSTE_MARGEN_PROPORCIONAL: "bg-green-100 dark:bg-green-900/40 text-green-800 dark:text-green-300",
                FLAG_USAR_MARGEN_MINORISTA: "bg-green-100 dark:bg-green-900/40 text-green-800 dark:text-green-300", FLAG_USAR_MARGEN_MAYORISTA: "bg-green-100 dark:bg-green-900/40 text-green-800 dark:text-green-300",
                GASTO_POST_GANANCIA: "bg-green-100 dark:bg-green-900/40 text-green-800 dark:text-green-300",
                FLAG_APLICAR_IVA: "bg-amber-100 dark:bg-amber-900/40 text-amber-800 dark:text-amber-300", IMPUESTO_ADICIONAL: "bg-amber-100 dark:bg-amber-900/40 text-amber-800 dark:text-amber-300",
                GASTO_POST_IMPUESTOS: "bg-amber-100 dark:bg-amber-900/40 text-amber-800 dark:text-amber-300",
                FLAG_INCLUIR_ENVIO: "bg-purple-100 dark:bg-purple-900/40 text-purple-800 dark:text-purple-300", COMISION_SOBRE_PVP: "bg-purple-100 dark:bg-purple-900/40 text-purple-800 dark:text-purple-300",
                FLAG_COMISION_ML: "bg-purple-100 dark:bg-purple-900/40 text-purple-800 dark:text-purple-300", FLAG_INFLACION_ML: "bg-purple-100 dark:bg-purple-900/40 text-purple-800 dark:text-purple-300",
                INFLACION_SOBRE_PVP: "bg-purple-100 dark:bg-purple-900/40 text-purple-800 dark:text-purple-300",
                CALCULO_SOBRE_CANAL_BASE: "bg-purple-100 dark:bg-purple-900/40 text-purple-800 dark:text-purple-300",
                CALCULO_SOBRE_CANAL_BASE_RESELLER: "bg-fuchsia-100 dark:bg-fuchsia-900/40 text-fuchsia-800 dark:text-fuchsia-300",
                RECARGO_CUPON: "bg-orange-100 dark:bg-orange-900/40 text-orange-800 dark:text-orange-300", DESCUENTO_PORCENTUAL: "bg-orange-100 dark:bg-orange-900/40 text-orange-800 dark:text-orange-300",
                INFLACION_DIVISOR: "bg-orange-100 dark:bg-orange-900/40 text-orange-800 dark:text-orange-300", FLAG_APLICAR_PRECIO_INFLADO: "bg-orange-100 dark:bg-orange-900/40 text-orange-800 dark:text-orange-300",
                GASTO_FUERA_PVP: "bg-orange-100 dark:bg-orange-900/40 text-orange-800 dark:text-orange-300",
            };
            const LABEL: Record<string, string> = {
                GASTO_SOBRE_COSTO: "% sobre Costo",         FLAG_FINANCIACION_PROVEEDOR: "⚑ Financ. Prov.",
                AJUSTE_MARGEN_PUNTOS: "Margen pts",         AJUSTE_MARGEN_PROPORCIONAL: "Margen %",
                FLAG_USAR_MARGEN_MINORISTA: "⚑ Mg. Minorista", FLAG_USAR_MARGEN_MAYORISTA: "⚑ Mg. Mayorista",
                GASTO_POST_GANANCIA: "Post Ganancia",
                FLAG_APLICAR_IVA: "⚑ Aplicar IVA",         IMPUESTO_ADICIONAL: "Impuesto Adicional",
                GASTO_POST_IMPUESTOS: "Post Impuestos",
                FLAG_INCLUIR_ENVIO: "⚑ Incluir Envío",     COMISION_SOBRE_PVP: "Comisión s/PVP",
                FLAG_COMISION_ML: "⚑ Comisión ML",         FLAG_INFLACION_ML: "⚑ Inflación ML",
                INFLACION_SOBRE_PVP: "Inflación s/PVP",
                CALCULO_SOBRE_CANAL_BASE: "Canal Base",
                CALCULO_SOBRE_CANAL_BASE_RESELLER: "Canal Base (Reseller)",
                RECARGO_CUPON: "Recargo Cupón",             DESCUENTO_PORCENTUAL: "Descuento %",
                INFLACION_DIVISOR: "Inflación Divisor",     FLAG_APLICAR_PRECIO_INFLADO: "⚑ Precio Inflado",
                GASTO_FUERA_PVP: "Gasto fuera de PVP",
            };
            const isFlag = val?.startsWith("FLAG_");
            return (
                <div className="flex items-center justify-center gap-1">
                    <select
                        className={`text-xs font-bold px-2 py-0.5 rounded border-0 outline-none ${canEdit ? "cursor-pointer" : "cursor-default appearance-none"} ${isFlag ? "italic " : ""}${COLOR[val] ?? "bg-gray-100 dark:bg-slate-700 text-gray-700 dark:text-slate-300"}`}
                        value={val}
                        disabled={!canEdit}
                        onChange={(e) => (table.options.meta as any)?.updateData?.(row.index, column.id, e.target.value)}
                    >
                        <optgroup label="Costo">
                            <option value="GASTO_SOBRE_COSTO">% sobre Costo</option>
                            <option value="FLAG_FINANCIACION_PROVEEDOR">⚑ Financiación Proveedor</option>
                        </optgroup>
                        <optgroup label="Margen">
                            <option value="AJUSTE_MARGEN_PUNTOS">Ajuste Margen (puntos)</option>
                            <option value="AJUSTE_MARGEN_PROPORCIONAL">Ajuste Margen (%)</option>
                            <option value="FLAG_USAR_MARGEN_MINORISTA">⚑ Margen Minorista</option>
                            <option value="FLAG_USAR_MARGEN_MAYORISTA">⚑ Margen Mayorista</option>
                            <option value="GASTO_POST_GANANCIA">% post Ganancia</option>
                        </optgroup>
                        <optgroup label="Impuestos">
                            <option value="FLAG_APLICAR_IVA">⚑ Aplicar IVA</option>
                            <option value="IMPUESTO_ADICIONAL">Impuesto Adicional</option>
                            <option value="GASTO_POST_IMPUESTOS">% post Impuestos</option>
                        </optgroup>
                        <optgroup label="Precio">
                            <option value="FLAG_INCLUIR_ENVIO">⚑ Incluir Envío</option>
                            <option value="COMISION_SOBRE_PVP">Comisión s/PVP</option>
                            <option value="FLAG_COMISION_ML">⚑ Comisión ML</option>
                            <option value="FLAG_INFLACION_ML">⚑ Inflación ML (% del MLA)</option>
                            <option value="INFLACION_SOBRE_PVP">Inflación s/PVP (% propio)</option>
                            <option value="CALCULO_SOBRE_CANAL_BASE">Canal Base (canal propio)</option>
                            <option value="CALCULO_SOBRE_CANAL_BASE_RESELLER">Canal Base (reseller)</option>
                        </optgroup>
                        <optgroup label="Post-precio">
                            <option value="RECARGO_CUPON">Recargo Cupón</option>
                            <option value="DESCUENTO_PORCENTUAL">Descuento %</option>
                            <option value="INFLACION_DIVISOR">Inflación Divisor</option>
                            <option value="GASTO_FUERA_PVP">Gasto fuera de PVP</option>
                            <option value="FLAG_APLICAR_PRECIO_INFLADO">⚑ Precio Inflado</option>
                        </optgroup>
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

"use client";
import { useEffect, useMemo, useRef, useState } from "react";
import {
    useReactTable,
    getCoreRowModel,
    flexRender,
    type ColumnDef,
    type SortingState,
} from "@tanstack/react-table";
import { ProductoCanalPrecioDTO, PrecioCalculado, DescuentoAplicable } from "./types";
import { toggleCanal, cuotasDeshabilitadas, claveCanalPersistencia } from "./canalFiltro";
import { getCanalesAPI } from "../canales/canalesService";
import { getProductoMargenAPI, type ProductoMargenDTO } from "../productos/productoMargenService";
import SearchInput from "../components/SearchInput/SearchInput";
import Button from "../components/Button/Button";
import Link from "next/link";
import {
    ChevronUpIcon,
    ChevronDownIcon,
    ChevronUpDownIcon,
    ArrowPathIcon,
    CalculatorIcon,
    InformationCircleIcon,
    XMarkIcon,
    EyeIcon,
    ArrowDownTrayIcon,
    ClipboardDocumentIcon,
    ChartBarIcon,
    CreditCardIcon,
    AdjustmentsVerticalIcon,
    PencilSquareIcon,
} from "@heroicons/react/24/outline";
import Modal from "../components/Modal/Modal";
import PaginationControls from "../components/Table/core/PaginationControls";
import EditableCell from "../components/Table/core/EditableCell";
import TableActionButton, { getTableActionButtonClasses } from "../components/Table/core/TableActionButton";
import { EditableRelationCell } from "../components/EditableRelationCell/EditableRelationCell";
import { EditingCellProvider } from "../components/Table/core/EditingCellContext";
import Tooltip from "../components/Tooltip/Tooltip";
import { getAllPreciosInfladosAPI } from "../productos/productoSubRecursosService";
import { getCuotasAPI, getCuotasPorCanalAPI } from "../canal-concepto-cuotas/canalConceptoCuotaService";
import { toast } from "sonner";
import { notificar } from "../utils/notificar";
import { useAuth } from "../context/AuthContext";
import { exportToExcel } from "../utils/exportCSV";

const pad2 = (n: number) => String(n).padStart(2, "0");
function formatFecha(d: Date): string {
    return `${pad2(d.getDate())}/${pad2(d.getMonth() + 1)}/${String(d.getFullYear()).slice(-2)} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`;
}

// Fila aplanada para la tabla comparadora
interface FilaComparador {
    _rowKey: string;
    id: number;
    sku: string;
    mla: string | null;
    descripcion: string;
    costo: number;
    iva: number;
    cuotas: number;
    cuotasDescripcion: string;
    canalId: number;
    canalNombre: string;
    pvp: number;
    pvpInflado: number | null;
    precioInfladoCodigo: string | null;
    precioInfladoTipo: string | null;
    precioInfladoValor: number | null;
    ganancia: number;
    margenSobrePvp: number;
    margenSobreIngresoNeto: number | null;
    markupPorcentaje: number | null;
    costosVenta: number | null;
    ingresoNetoVendedor: number | null;
    margenMinorista: number | null;
    margenMayorista: number | null;
    margenFijoMinorista: number | null;
    margenFijoMayorista: number | null;
    descuentos: DescuentoAplicable[];
    // Primer descuento aplanado para columnas individuales
    descPorcentaje: number | null;
    descMontoMinimo: number | null;
    descPvp: number | null;
    descGanancia: number | null;
    descCostosVenta: number | null;
    descIngresoNeto: number | null;
    descMargenSobreIN: number | null;
    descMargenSobrePvp: number | null;
    descMarkup: number | null;
    fechaUltimoCosto: string | null;
    fechaUltimoCalculo: string | null;
    tag: "MAQUINA" | "REPUESTO" | "MENAJE" | null;
    _original: ProductoCanalPrecioDTO;
}

interface Props {
    data: ProductoCanalPrecioDTO[];
    isLoading: boolean;
    pageIndex: number;
    pageSize: number;
    pageCount: number;
    totalRecords: number;
    onPageChange: (page: number) => void;
    onPageSizeChange: (size: number) => void;
    search: string;
    onSearch: (search: string) => void;
    onRecalcular: (productoId: number) => void;
    onVerFormula: (productoId: number, canalId: number, cuotas: number) => void;
    calcLoading: Record<number, boolean>;
    globalLoading: boolean;
    onCanalChange?: (canales: number[]) => void;
    onCuotasChange?: (cuotas: number | "all" | null) => void;
    onSortingChange?: (sorting: SortingState) => void;
    error?: string | null;
    onEditField?: (productoId: number, canalId: number, field: string, value: number | string) => Promise<void>;
    onEditReglaInflada?: (productoId: number, canalId: number, precioInfladoId: number | null) => Promise<void>;
    onExportAll?: () => Promise<ProductoCanalPrecioDTO[]>;
}

// Mapeo de columna frontend → campo sort backend

const formatARS = (n: number) =>
    `$${n.toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const DISCOUNT_HEADER_CLASS = "bg-orange-50/80 text-orange-700 dark:bg-orange-500/10 dark:text-orange-300";

const formatThousands = (n: string | number) =>
    Number(n).toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const pctCell = (val: number | null | undefined) =>
    val != null ? `${val.toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%` : "—";

const mrgColor = (val: number | null | undefined) => {
    if (val == null) return "text-gray-300";
    if (val >= 40) return "text-emerald-600 font-semibold";
    if (val >= 25) return "text-green-600";
    if (val >= 15) return "text-yellow-600";
    if (val >= 0) return "text-orange-500";
    return "text-red-600 font-semibold";
};

/**
 * Devuelve el ícono de alerta para MRG S/IN: el indicador clave de rentabilidad
 * real (post IVA + impuestos + costos venta). Si el dueño no se queda con suficiente
 * de lo que cobra, el negocio no es viable aunque el PVP se vea alto.
 *  - < 0%:  🔻 (rojo) — pierde plata, vende a pérdida
 *  - < 15%: ⚠ (naranja) — rentabilidad ajustada, revisar
 *  - ≥ 15%: sin ícono
 */
const mrgAlertIcon = (val: number | null | undefined): string | null => {
    if (val == null) return null;
    if (val < 0) return "🔻";
    if (val < 15) return "⚠";
    return null;
};

const markupColor = (val: number | null | undefined) => {
    if (val == null) return "text-gray-300";
    if (val >= 100) return "text-emerald-600 font-semibold";
    if (val >= 50) return "text-green-600";
    if (val >= 20) return "text-green-600";
    if (val >= 0) return "text-orange-500";
    return "text-red-600 font-semibold";
};

/**
 * Aplana la respuesta jerárquica del backend (producto → canales → precios) a
 * una lista plana de filas. Se usa tanto para renderizar la tabla como para
 * exportar el Excel — antes el export pasaba los datos crudos sin aplanar y
 * por eso las columnas anidadas aparecían vacías.
 *
 * El filtro por canal/cuotas se aplica acá si se pasa. Para el export "todo",
 * el backend ya devuelve filtrado, así que pasar {canal:"all", cuotas:"all"}
 * solo aplana sin filtrar de nuevo.
 */
function aplanarParaExport(
    data: ProductoCanalPrecioDTO[],
    margenesMap: Record<number, ProductoMargenDTO | null>,
    filtro: { canales: number[]; cuotas: number | "all" | null },
): FilaComparador[] {
    // [] = todos; [n…] = solo esos canales.
    const canalSet = filtro.canales.length > 0 ? new Set(filtro.canales) : null;
    const result: FilaComparador[] = [];
    for (const prod of data) {
        const canales = canalSet === null
            ? (prod.canales ?? [])
            : (prod.canales ?? []).filter((c) => canalSet.has(c.canalId));
        const margen = margenesMap[prod.id];

        const buildFila = (precio: PrecioCalculado, canalId: number, canalNombre: string, precioIdx: number): FilaComparador => ({
            _rowKey: `${prod.id}-${canalId}-${precio.cuotas}-${precioIdx}`,
            id: prod.id,
            sku: prod.sku,
            mla: prod.mla ?? null,
            descripcion: prod.descripcion,
            costo: prod.costo,
            iva: prod.iva,
            cuotas: precio.cuotas,
            cuotasDescripcion: precio.descripcion,
            canalId,
            canalNombre,
            pvp: precio.pvp,
            pvpInflado: precio.pvpInflado ?? null,
            precioInfladoCodigo: precio.precioInfladoCodigo ?? null,
            precioInfladoTipo: precio.precioInfladoTipo ?? null,
            precioInfladoValor: precio.precioInfladoValor ?? null,
            ganancia: precio.ganancia,
            margenSobrePvp: precio.margenSobrePvp,
            margenSobreIngresoNeto: precio.margenSobreIngresoNeto ?? null,
            markupPorcentaje: precio.markupPorcentaje ?? null,
            costosVenta: precio.costosVenta ?? null,
            ingresoNetoVendedor: precio.ingresoNetoVendedor ?? null,
            margenMinorista: margen?.margenMinorista ?? null,
            margenMayorista: margen?.margenMayorista ?? null,
            margenFijoMinorista: margen?.margenFijoMinorista ?? null,
            margenFijoMayorista: margen?.margenFijoMayorista ?? null,
            descuentos: precio.descuentos ?? [],
            descPorcentaje: precio.descuentos?.[0]?.descuentoPorcentaje ?? null,
            descMontoMinimo: precio.descuentos?.[0]?.montoMinimo ?? null,
            descPvp: precio.descuentos?.[0]?.pvpConDescuento ?? null,
            descGanancia: precio.descuentos?.[0]?.gananciaConDescuento ?? null,
            descCostosVenta: precio.descuentos?.[0]?.costosVentaConDescuento ?? null,
            descIngresoNeto: precio.descuentos?.[0]?.ingresoNetoConDescuento ?? null,
            descMargenSobreIN: precio.descuentos?.[0]?.margenSobreIngresoNetoConDescuento ?? null,
            descMargenSobrePvp: precio.descuentos?.[0]?.margenSobrePvpConDescuento ?? null,
            descMarkup: precio.descuentos?.[0]?.markupConDescuento ?? null,
            fechaUltimoCosto: prod.fechaUltimoCosto ?? null,
            fechaUltimoCalculo: precio.fechaUltimoCalculo ?? null,
            tag: prod.tag ?? null,
            _original: prod,
        });

        for (const canal of canales) {
            if (!canal.precios?.length) continue;
            if (filtro.cuotas != null && filtro.cuotas !== "all") {
                const idx = canal.precios.findIndex((p) => p.cuotas === filtro.cuotas);
                if (idx >= 0) result.push(buildFila(canal.precios[idx], canal.canalId, canal.canalNombre, idx));
            } else {
                canal.precios.forEach((precio, idx) => {
                    result.push(buildFila(precio, canal.canalId, canal.canalNombre, idx));
                });
            }
        }
    }

    // El backend pagina por (producto, canal, cuota) → cada DTO trae 1 sola cuota,
    // así que las distintas cuotas del mismo producto+canal aparecen como filas
    // separadas en el orden de id (no por cuotas). Reordenar las filas para que,
    // dentro de cada grupo producto+canal, las cuotas queden ASC (transferencia
    // -1, contado 0, 3, 6, 9, 12…) preservando el orden de grupos del backend.
    const grupoRank = new Map<string, number>();
    let nextRank = 0;
    for (const row of result) {
        const key = `${row.id}-${row.canalId}`;
        if (!grupoRank.has(key)) grupoRank.set(key, nextRank++);
    }
    result.sort((a, b) => {
        const rankA = grupoRank.get(`${a.id}-${a.canalId}`) ?? 0;
        const rankB = grupoRank.get(`${b.id}-${b.canalId}`) ?? 0;
        if (rankA !== rankB) return rankA - rankB;
        return Number(a.cuotas ?? 0) - Number(b.cuotas ?? 0);
    });
    return result;
}

const ganColor = (val: number) =>
    val >= 0 ? "text-teal-600 font-semibold" : "text-red-500 font-semibold";

// Fuente única de descripciones de campos. Cada entrada incluye:
//  - campo:       nombre largo (usado en el modal de ayuda).
//  - headers:     aliases del header de la tabla que apuntan a esta descripción.
//  - descripcion: texto que aparece en tooltip + modal.
//  - color:       categoría visual (badge + borde en el modal).
// HEADER_DESCRIPTION_MAP se deriva automáticamente — no duplicar contenido.
const CAMPO_DESCRIPCIONES: { campo: string; headers: string[]; descripcion: string; color: string }[] = [
    // ──────────────────────────────────────────────────────────────────────────
    // Identificación del producto / contexto
    // ──────────────────────────────────────────────────────────────────────────
    { campo: "SKU", headers: ["SKU"], descripcion: "Código único interno del producto. Identificador principal en el catálogo.", color: "slate" },
    { campo: "Producto", headers: ["Producto", "Descripción"], descripcion: "Descripción del producto. Si no tiene título web, se usa la descripción interna.", color: "slate" },
    { campo: "Canal", headers: ["Canal"], descripcion: "Canal de venta (ej: Local, MercadoLibre, Mayorista). Cada canal puede tener distinta configuración de márgenes, comisiones e impuestos.", color: "slate" },
    { campo: "Cuotas", headers: ["Cuotas"], descripcion: "Plan de cuotas: 0 = contado, -1 = transferencia, N = N cuotas. Cada combinación canal+cuotas puede tener su propio recargo.", color: "slate" },

    // ──────────────────────────────────────────────────────────────────────────
    // Costo + IVA del producto (datos crudos)
    // ──────────────────────────────────────────────────────────────────────────
    { campo: "Costo Producto", headers: ["Costo"], descripcion: "Costo base del producto en moneda local, sin financiación ni recargos. La financiación del proveedor se aplica internamente cuando el cálculo lo requiere (no se ve acá).", color: "blue" },
    { campo: "Última actualización de costo", headers: ["Últ. Costo"], descripcion: "Fecha y hora de la última vez que se actualizó el costo del producto. Útil para detectar costos desactualizados.", color: "blue" },
    { campo: "IVA", headers: ["IVA"], descripcion: "Alícuota de IVA del producto (en %). Se descuenta del PVP al calcular Ingreso Neto.", color: "blue" },

    // ──────────────────────────────────────────────────────────────────────────
    // Configuración de márgenes (editables inline)
    // ──────────────────────────────────────────────────────────────────────────
    { campo: "Margen Minorista", headers: ["Mrg Min"], descripcion: "Porcentaje de margen para venta minorista. Editable inline. El canal define cuál de los dos márgenes usar (minorista o mayorista).", color: "indigo" },
    { campo: "Margen Mayorista", headers: ["Mrg May"], descripcion: "Porcentaje de margen para venta mayorista. Editable inline. El canal define cuál de los dos márgenes usar (minorista o mayorista).", color: "indigo" },
    { campo: "Fijo Minorista", headers: ["Fijo Min"], descripcion: "Monto fijo (en $) que se suma al costo para venta minorista, en lugar del porcentaje. Si está cargado, tiene precedencia sobre el % minorista.", color: "indigo" },
    { campo: "Fijo Mayorista", headers: ["Fijo May"], descripcion: "Monto fijo (en $) que se suma al costo para venta mayorista, en lugar del porcentaje. Si está cargado, tiene precedencia sobre el % mayorista.", color: "indigo" },

    // ──────────────────────────────────────────────────────────────────────────
    // Precios calculados
    // ──────────────────────────────────────────────────────────────────────────
    { campo: "PVP", headers: ["PVP"], descripcion: "Precio de venta al público (lo que paga el cliente). Es el precio final para este canal y cuotas, ya con IVA + impuestos + comisiones + recargo cuotas incluidos.", color: "green" },
    { campo: "PVP Inflado", headers: ["Inflado", "PVP Inflado"], descripcion: "Precio mostrado como referencia tachada cuando hay regla de precio inflado configurada (× multiplicador, − % descuento, ÷ divisor o $ precio fijo). Solo aparece si difiere del PVP.", color: "indigo" },
    { campo: "Regla Inflado", headers: ["Regla Inflado"], descripcion: "Código de la regla de inflado aplicada. Tipo: × = multiplicador, -% = descuento, ÷ = divisor, $ = precio fijo. Editable inline.", color: "purple" },

    // ──────────────────────────────────────────────────────────────────────────
    // Resultados financieros
    // ──────────────────────────────────────────────────────────────────────────
    { campo: "Ganancia", headers: ["Ganancia"], descripcion: "Beneficio real de la venta (en $). Fórmula: Ingreso Neto Vendedor − Costo Producto. Es lo que efectivamente entra al bolsillo después de pagar todo.", color: "teal" },
    { campo: "Costos Venta", headers: ["Costos Venta"], descripcion: "Suma de gastos que se llevan terceros en la venta: comisiones del canal, recargo por cupón / cuotas, envío. Se descuentan del PVP para llegar al Ingreso Neto.", color: "orange" },
    { campo: "Ingreso Neto Vendedor", headers: ["Ingreso Neto"], descripcion: "Lo que efectivamente cobrás después de IVA + impuestos + costos de venta. Fórmula: PVP − IVA − Impuestos − Costos Venta. Es la base correcta para evaluar rentabilidad.", color: "cyan" },

    // ──────────────────────────────────────────────────────────────────────────
    // Indicadores porcentuales (con leyenda de colores común)
    // ──────────────────────────────────────────────────────────────────────────
    { campo: "Margen s/PVP", headers: ["Mrg s/PVP"], descripcion: "Qué porcentaje del precio final es ganancia. Fórmula: (Ganancia / PVP) × 100. Útil para comparar contra la competencia (ellos también miden contra PVP).", color: "emerald" },
    { campo: "Margen s/Ingreso Neto", headers: ["Mrg s/IN"], descripcion: "Margen real sobre lo que cobrás. Fórmula: (Ganancia / Ingreso Neto) × 100. Es el % más fiel porque excluye IVA e impuestos del denominador.", color: "emerald" },
    { campo: "Markup", headers: ["Markup%"], descripcion: "Cuánto recargás sobre el costo del producto. Fórmula: (Ganancia / Costo Producto) × 100. Útil para definir precios de venta a partir del costo.", color: "purple" },

    // ──────────────────────────────────────────────────────────────────────────
    // Descuentos automáticos del canal
    // ──────────────────────────────────────────────────────────────────────────
    { campo: "Descuento", headers: ["Descuento"], descripcion: "Porcentaje de descuento y monto mínimo de compra requerido. Si hay múltiples reglas, muestra la mayor y el resto en tooltip al pasar el mouse.", color: "orange" },
    { campo: "PVP c/Desc", headers: ["PVP c/Desc"], descripcion: "PVP final si se aplica el descuento del canal (PVP − % de descuento).", color: "orange" },
    { campo: "Gan c/Desc", headers: ["Gan c/Desc"], descripcion: "Ganancia resultante después de aplicar el descuento. Suele ser menor que la Ganancia base.", color: "orange" },
    { campo: "CV c/Desc", headers: ["CV c/Desc"], descripcion: "Costos de Venta recalculados sobre el PVP con descuento (las comisiones se aplican sobre el precio efectivo, no sobre el PVP de lista).", color: "orange" },
    { campo: "IN c/Desc", headers: ["IN c/Desc"], descripcion: "Ingreso Neto Vendedor si se aplica el descuento. Es lo que cobrás cuando el cliente usa el descuento.", color: "orange" },
    { campo: "Mrg IN c/D", headers: ["Mrg IN c/D"], descripcion: "Margen sobre Ingreso Neto con el descuento aplicado. La métrica de rentabilidad real bajo descuento.", color: "orange" },
    { campo: "Mrg PVP c/D", headers: ["Mrg PVP c/D"], descripcion: "Margen sobre PVP con el descuento aplicado.", color: "orange" },
    { campo: "Mkup c/Desc", headers: ["Mkup c/Desc"], descripcion: "Markup con el descuento aplicado.", color: "orange" },
];

// Mapa header → descripción derivado de la fuente única.
const HEADER_DESCRIPTION_MAP: Record<string, string> = Object.fromEntries(
    CAMPO_DESCRIPCIONES.flatMap((c) => c.headers.map((h) => [h, c.descripcion] as const)),
);

const CAMPO_COLOR_MAP: Record<string, { border: string; bg: string; badge: string; text: string }> = {
    blue:    { border: "border-l-blue-500",    bg: "bg-blue-50 dark:bg-blue-500/10",    badge: "bg-blue-100 text-blue-700 dark:bg-blue-500/20 dark:text-blue-300",    text: "text-blue-900 dark:text-blue-200" },
    green:   { border: "border-l-green-500",   bg: "bg-green-50 dark:bg-green-500/10",   badge: "bg-green-100 text-green-700 dark:bg-green-500/20 dark:text-green-300",   text: "text-green-900 dark:text-green-200" },
    indigo:  { border: "border-l-indigo-500",  bg: "bg-indigo-50 dark:bg-indigo-500/10",  badge: "bg-indigo-100 text-indigo-700 dark:bg-indigo-500/20 dark:text-indigo-300",  text: "text-indigo-900 dark:text-indigo-200" },
    teal:    { border: "border-l-teal-500",    bg: "bg-teal-50 dark:bg-teal-500/10",    badge: "bg-teal-100 text-teal-700 dark:bg-teal-500/20 dark:text-teal-300",    text: "text-teal-900 dark:text-teal-200" },
    orange:  { border: "border-l-orange-500",  bg: "bg-orange-50 dark:bg-orange-500/10",  badge: "bg-orange-100 text-orange-700 dark:bg-orange-500/20 dark:text-orange-300",  text: "text-orange-900 dark:text-orange-200" },
    cyan:    { border: "border-l-cyan-500",    bg: "bg-cyan-50 dark:bg-cyan-500/10",    badge: "bg-cyan-100 text-cyan-700 dark:bg-cyan-500/20 dark:text-cyan-300",    text: "text-cyan-900 dark:text-cyan-200" },
    emerald: { border: "border-l-emerald-500", bg: "bg-emerald-50 dark:bg-emerald-500/10", badge: "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-300", text: "text-emerald-900 dark:text-emerald-200" },
    purple:  { border: "border-l-purple-500",  bg: "bg-purple-50 dark:bg-purple-500/10",  badge: "bg-purple-100 text-purple-700 dark:bg-purple-500/20 dark:text-purple-300",  text: "text-purple-900 dark:text-purple-200" },
    slate:   { border: "border-l-slate-400",   bg: "bg-slate-50 dark:bg-slate-500/10",    badge: "bg-slate-100 text-slate-700 dark:bg-slate-500/20 dark:text-slate-300",    text: "text-slate-900 dark:text-slate-200" },
};

// Columnas cuyo valor es propiedad del producto (o del producto+canal) y por lo tanto
// se repite idéntico en todas las filas de cuotas del mismo grupo. Sólo las renderizamos
// en la primera fila de cada grupo para evitar ruido visual.
const PRODUCT_LEVEL_COLS = new Set([
    "sku", "mla", "descripcion", "canalNombre",
    "costo", "fechaUltimoCosto", "iva",
    "margenMinorista", "margenMayorista", "margenFijoMinorista", "margenFijoMayorista",
    "precioInfladoCodigo",
]);

// Columnas que se pueden ocultar/mostrar (id → label)
const TOGGLEABLE_COLUMNS: { id: string; label: string }[] = [
    { id: "sku", label: "SKU" },
    { id: "descripcion", label: "Producto" },
    { id: "canalNombre", label: "Canal" },
    { id: "cuotasDescripcion", label: "Cuotas" },
    { id: "costo", label: "Costo" },
    { id: "fechaUltimoCosto", label: "Últ. Costo" },
    { id: "iva", label: "IVA" },
    { id: "margenMinorista", label: "Mrg Min" },
    { id: "margenMayorista", label: "Mrg May" },
    { id: "margenFijoMinorista", label: "Fijo Min" },
    { id: "margenFijoMayorista", label: "Fijo May" },
    { id: "pvp", label: "PVP" },
    { id: "pvpInflado", label: "Inflado" },
    { id: "precioInfladoCodigo", label: "Regla Inflado" },
    { id: "ganancia", label: "Ganancia" },
    { id: "costosVenta", label: "Costos Venta" },
    { id: "ingresoNetoVendedor", label: "Ingreso Neto" },
    { id: "margenSobrePvp", label: "Mrg s/PVP" },
    { id: "margenSobreIngresoNeto", label: "Mrg s/IN" },
    { id: "markupPorcentaje", label: "Markup%" },
    { id: "descResumen", label: "Descuento" },
    { id: "descPvp", label: "PVP c/Desc" },
    { id: "descGanancia", label: "Gan c/Desc" },
    { id: "descCostosVenta", label: "CV c/Desc" },
    { id: "descIngresoNeto", label: "IN c/Desc" },
    { id: "descMargenSobreIN", label: "Mrg IN c/D" },
    { id: "descMargenSobrePvp", label: "Mrg PVP c/D" },
    { id: "descMarkup", label: "Mkup c/Desc" },
    { id: "fechaUltimoCalculo", label: "Últ. Cálculo" },
    { id: "acciones", label: "Acciones" },
];

const MONITOR_PRESETS = [
    {
        id: "rentabilidad",
        label: "Rentabilidad",
        visibleColumns: [
            "sku", "descripcion", "canalNombre", "cuotasDescripcion", "costo", "iva", "pvp",
            "ganancia", "costosVenta", "ingresoNetoVendedor", "margenSobrePvp", "margenSobreIngresoNeto",
            "markupPorcentaje", "fechaUltimoCalculo", "acciones",
        ],
    },
    {
        id: "edicion",
        label: "Edición",
        visibleColumns: [
            "sku", "descripcion", "canalNombre", "cuotasDescripcion", "costo", "iva",
            "margenMinorista", "margenMayorista", "margenFijoMinorista", "margenFijoMayorista",
            "pvp", "pvpInflado", "precioInfladoCodigo", "acciones",
        ],
    },
    {
        id: "descuentos",
        label: "Descuentos",
        visibleColumns: [
            "sku", "descripcion", "canalNombre", "cuotasDescripcion", "pvp", "descResumen",
            "descPvp", "descGanancia", "descCostosVenta", "descIngresoNeto",
            "descMargenSobreIN", "descMargenSobrePvp", "descMarkup", "acciones",
        ],
    },
    {
        id: "completo",
        label: "Completo",
        visibleColumns: TOGGLEABLE_COLUMNS.map((col) => col.id),
    },
] as const;

const MONITOR_EXPORT_COLUMNS: Array<{ header: string; accessor: keyof FilaComparador | "descResumen" }> = [
    { header: "SKU", accessor: "sku" },
    { header: "MLA", accessor: "mla" },
    { header: "Producto", accessor: "descripcion" },
    { header: "Canal", accessor: "canalNombre" },
    { header: "Cuotas", accessor: "cuotasDescripcion" },
    { header: "Costo", accessor: "costo" },
    { header: "Últ. Costo", accessor: "fechaUltimoCosto" },
    { header: "IVA", accessor: "iva" },
    { header: "Mrg Min", accessor: "margenMinorista" },
    { header: "Mrg May", accessor: "margenMayorista" },
    { header: "Fijo Min", accessor: "margenFijoMinorista" },
    { header: "Fijo May", accessor: "margenFijoMayorista" },
    { header: "PVP", accessor: "pvp" },
    { header: "Inflado", accessor: "pvpInflado" },
    { header: "Regla Inflado", accessor: "precioInfladoCodigo" },
    { header: "Ganancia", accessor: "ganancia" },
    { header: "Costos Venta", accessor: "costosVenta" },
    { header: "Ingreso Neto", accessor: "ingresoNetoVendedor" },
    { header: "Mrg s/PVP", accessor: "margenSobrePvp" },
    { header: "Mrg s/IN", accessor: "margenSobreIngresoNeto" },
    { header: "Markup%", accessor: "markupPorcentaje" },
    { header: "Descuento", accessor: "descResumen" },
    { header: "PVP c/Desc", accessor: "descPvp" },
    { header: "Gan c/Desc", accessor: "descGanancia" },
    { header: "CV c/Desc", accessor: "descCostosVenta" },
    { header: "IN c/Desc", accessor: "descIngresoNeto" },
    { header: "Mrg IN c/D", accessor: "descMargenSobreIN" },
    { header: "Mrg PVP c/D", accessor: "descMargenSobrePvp" },
    { header: "Mkup c/Desc", accessor: "descMarkup" },
    { header: "Últ. Cálculo", accessor: "fechaUltimoCalculo" },
];

function buildHiddenColumnsFromPreset(presetId: string) {
    const preset = MONITOR_PRESETS.find((item) => item.id === presetId);
    if (!preset) return new Set<string>();
    const visible = new Set(preset.visibleColumns);
    return new Set(TOGGLEABLE_COLUMNS.map((col) => col.id).filter((id) => !visible.has(id)));
}

function resolvePresetFromHiddenColumns(hiddenColumns: Set<string>) {
    const current = [...hiddenColumns].sort().join("|");
    const matched = MONITOR_PRESETS.find((preset) => {
        const presetHidden = [...buildHiddenColumnsFromPreset(preset.id)].sort().join("|");
        return presetHidden === current;
    });
    return matched?.id ?? "custom";
}

// Cache de reglas infladas para el AsyncSelect
let _reglasCache: { id: number | string; label: string }[] | null = null;
const loadReglasInflado = async (query: string): Promise<{ id: number | string; label: string }[]> => {
    if (!_reglasCache) {
        const all = await getAllPreciosInfladosAPI();
        _reglasCache = all.map((r) => ({ id: r.id, label: r.nombre }));
    }
    const q = query.toLowerCase();
    return _reglasCache.filter((r) => r.label.toLowerCase().includes(q));
};

// Colores de canal compartidos — misma cache global para consistencia entre tablas
import { getCanalColor, getCanalBorderColor, CANAL_BADGE_CLASS } from "../utils/canalColors";

// Paleta de colores para badges de cuotas — tonos fríos, cada valor único recibe un color distinto
const CUOTAS_COLORS = [
    "text-slate-600 bg-slate-100 dark:text-slate-300 dark:bg-slate-700",
    "text-sky-700 bg-sky-100 dark:text-sky-300 dark:bg-sky-500/20",
    "text-blue-700 bg-blue-100 dark:text-blue-300 dark:bg-blue-500/20",
    "text-indigo-700 bg-indigo-100 dark:text-indigo-300 dark:bg-indigo-500/20",
    "text-violet-700 bg-violet-100 dark:text-violet-300 dark:bg-violet-500/20",
    "text-cyan-700 bg-cyan-100 dark:text-cyan-300 dark:bg-cyan-500/20",
];
const _cuotasColorCache = new Map<number, string>();
let _cuotasColorIndex = 0;
function getCuotasColor(cuotas: number): string {
    if (_cuotasColorCache.has(cuotas)) return _cuotasColorCache.get(cuotas)!;
    const color = CUOTAS_COLORS[_cuotasColorIndex % CUOTAS_COLORS.length];
    _cuotasColorIndex++;
    _cuotasColorCache.set(cuotas, color);
    return color;
}

const getColumns = (
    onRecalcular: (productoId: number) => void,
    onVerFormula: (productoId: number, canalId: number, cuotas: number) => void,
    calcLoading: Record<number, boolean>,
    globalLoading: boolean,
    onEditField?: (productoId: number, canalId: number, field: string, value: number | string) => void,
    onEditReglaInflada?: (productoId: number, canalId: number, precioInfladoId: number | null) => void,
    canEdit = true,
    lastEditedCell?: { productoId: number; field: string } | null,
): ColumnDef<FilaComparador>[] => {
    const highlightClass = (productoId: number, field: string) =>
        lastEditedCell && lastEditedCell.productoId === productoId && lastEditedCell.field === field
            ? "!bg-emerald-100 !ring-2 !ring-emerald-400 dark:!bg-emerald-500/20 dark:!ring-emerald-400"
            : "";
    return [
    {
        accessorKey: "sku",
        header: "SKU",
        size: 90,
        enableSorting: true,
        meta: { center: true },
        cell: ({ getValue }) => (
            <span className="font-bold">{getValue() as string}</span>
        ),
    },
    {
        accessorKey: "mla",
        header: "MLA",
        size: 110,
        enableSorting: true,
        meta: { center: true },
        cell: ({ getValue }) => {
            const val = getValue() as string | null;
            return val ? <span className="text-xs">{val}</span> : <span className="text-gray-400">—</span>;
        },
    },
    {
        accessorKey: "descripcion",
        header: "Producto",
        size: 220,
        enableSorting: true,
        meta: { center: true },
        cell: ({ getValue, row }) => {
            const desc = getValue() as string;
            const tag = row.original.tag;
            if (tag === "MAQUINA") {
                return <span className="text-orange-700 font-semibold" title="Máquina">🔧 {desc}</span>;
            }
            if (tag === "REPUESTO") {
                return <span className="text-sky-700 font-semibold" title="Repuesto">🔩 {desc}</span>;
            }
            return <span>{desc}</span>;
        },
    },
    {
        accessorKey: "canalNombre",
        header: "Canal",
        size: 110,
        enableSorting: true,
        meta: { center: true },
        cell: ({ getValue }) => {
            const name = getValue() as string;
            const colors = getCanalColor(name);
            return (
                <span className={`${CANAL_BADGE_CLASS} ${colors}`}>
                    {name}
                </span>
            );
        },
    },
    {
        accessorKey: "cuotasDescripcion",
        header: "Cuotas",
        size: 100,
        enableSorting: true,
        meta: { center: true },
        cell: ({ row }) => {
            const desc = row.original.cuotasDescripcion as string;
            const cuotas = row.original.cuotas as number;
            const colors = getCuotasColor(cuotas);
            return (
                <span className={`text-xs font-medium px-1.5 py-0.5 rounded ${colors}`}>
                    {desc}
                </span>
            );
        },
    },
    // --- Precios calculados (visibles) ---
    {
        accessorKey: "pvp",
        header: "PVP",
        size: 110,
        enableSorting: true,
        meta: { center: true },
        cell: ({ getValue }) => (
            <span className="font-mono font-semibold text-green-700">{formatARS(getValue() as number)}</span>
        ),
    },
    {
        accessorKey: "pvpInflado",
        header: "Inflado",
        size: 110,
        enableSorting: true,
        meta: { center: true },
        cell: ({ getValue }) => {
            const val = getValue() as number | null;
            return val != null
                ? <span className="font-mono text-blue-600">{formatARS(val)}</span>
                : <span className="text-gray-300">—</span>;
        },
    },
    {
        accessorKey: "costo",
        header: "Costo",
        size: 100,
        enableSorting: true,
        meta: { center: true, editable: true },
        cell: ({ getValue, row }) => (
            <EditableCell
                initialValue={getValue() as number}
                type="number"
                prefix="$ "
                displayFormatter={formatThousands}
                className={`font-mono ${highlightClass(row.original.id, "costo")}`}
                disabled={!canEdit}
                onSave={(val) => onEditField?.(row.original.id, row.original.canalId, "costo", val!)}
            />
        ),
    },
    {
        accessorKey: "fechaUltimoCosto",
        header: "Últ. Costo",
        size: 120,
        enableSorting: true,
        meta: { center: true },
        cell: ({ getValue }) => {
            const val = getValue() as string | null;
            if (!val) return <span className="text-gray-300">—</span>;
            const d = new Date(val);
            return (
                <span className="text-xs text-gray-500" title={d.toLocaleString("es-AR")}>
                    {formatFecha(d)}
                </span>
            );
        },
    },
    {
        accessorKey: "iva",
        header: "IVA",
        size: 60,
        enableSorting: true,
        meta: { center: true, editable: true },
        cell: ({ getValue, row }) => (
            <EditableCell
                initialValue={getValue() as number}
                type="number"
                suffix="%"
                className={`font-mono text-gray-500 ${highlightClass(row.original.id, "iva")}`}
                disabled={!canEdit}
                onSave={(val) => onEditField?.(row.original.id, row.original.canalId, "iva", val!)}
            />
        ),
    },
    // --- Márgenes configurados (editables) ---
    {
        accessorKey: "margenMinorista",
        header: "Mrg Min",
        size: 85,
        enableSorting: true,
        meta: { center: true, editable: true },
        cell: ({ getValue, row }) => {
            const value = getValue() as number | null;
            const enCero = value === 0;
            return (
            <EditableCell
                initialValue={value ?? ""}
                type="number"
                suffix="%"
                className={`font-mono font-semibold ${enCero ? "text-red-600 dark:text-red-400" : "text-yellow-600 dark:text-yellow-400"} ${highlightClass(row.original.id, "margenMinorista")}`}
                disabled={!canEdit}
                onSave={(val) => onEditField?.(row.original.id, row.original.canalId, "margenMinorista", val!)}
            />
            );
        },
    },
    {
        accessorKey: "margenMayorista",
        header: "Mrg May",
        size: 85,
        enableSorting: true,
        meta: { center: true, editable: true },
        cell: ({ getValue, row }) => {
            const value = getValue() as number | null;
            const enCero = value === 0;
            return (
            <EditableCell
                initialValue={value ?? ""}
                type="number"
                suffix="%"
                className={`font-mono font-semibold ${enCero ? "text-red-600 dark:text-red-400" : "text-blue-700 dark:text-blue-400"} ${highlightClass(row.original.id, "margenMayorista")}`}
                disabled={!canEdit}
                onSave={(val) => onEditField?.(row.original.id, row.original.canalId, "margenMayorista", val!)}
            />
            );
        },
    },
    {
        accessorKey: "margenFijoMinorista",
        header: "Fijo Min",
        size: 90,
        enableSorting: true,
        meta: { center: true, editable: true },
        cell: ({ getValue, row }) => (
            <EditableCell
                initialValue={(getValue() as number | null) ?? ""}
                type="number"
                prefix="$ "
                displayFormatter={formatThousands}
                className={`font-mono font-semibold text-yellow-600 dark:text-yellow-400 ${highlightClass(row.original.id, "margenFijoMinorista")}`}
                disabled={!canEdit}
                onSave={(val) => onEditField?.(row.original.id, row.original.canalId, "margenFijoMinorista", val!)}
            />
        ),
    },
    {
        accessorKey: "margenFijoMayorista",
        header: "Fijo May",
        size: 90,
        enableSorting: true,
        meta: { center: true, editable: true },
        cell: ({ getValue, row }) => (
            <EditableCell
                initialValue={(getValue() as number | null) ?? ""}
                type="number"
                prefix="$ "
                displayFormatter={formatThousands}
                className={`font-mono font-semibold text-blue-700 dark:text-blue-400 ${highlightClass(row.original.id, "margenFijoMayorista")}`}
                disabled={!canEdit}
                onSave={(val) => onEditField?.(row.original.id, row.original.canalId, "margenFijoMayorista", val!)}
            />
        ),
    },
    // --- Precios calculados ---
    {
        accessorKey: "precioInfladoCodigo",
        header: "Regla Inflado",
        size: 180,
        enableSorting: true,
        meta: { center: true, editable: true },
        cell: ({ row }) => {
            const codigo = row.original.precioInfladoCodigo;
            const tipo = row.original.precioInfladoTipo;
            const valor = row.original.precioInfladoValor;
            const tipoLabel = tipo === "MULTIPLICADOR" ? "\u00d7" : tipo === "DESCUENTO_PORC" ? "-%" : tipo === "DIVISOR" ? "\u00f7" : tipo === "PRECIO_FIJO" ? "$" : "";
            const displayName = codigo ? `${codigo} (${tipoLabel}${valor})` : "---";

            const badge = (codigo: string | null, onClick?: () => void) => {
                if (!codigo) return (
                    <span onClick={onClick} className={`text-gray-300 ${onClick ? "cursor-pointer hover:text-violet-400" : ""}`}>
                        {"\u2014"}
                    </span>
                );
                return (
                    <span
                        onClick={onClick}
                        className={`inline-flex items-center gap-1 text-xs font-medium text-violet-600 bg-violet-50 border border-violet-200 px-2 py-0.5 rounded-full ${onClick ? "cursor-pointer hover:bg-violet-100 hover:border-violet-300 transition-colors" : ""}`}
                        title={`${tipo}: ${valor}`}
                    >
                        {codigo} <span className="text-violet-400">({tipoLabel}{valor})</span>
                    </span>
                );
            };

            if (!onEditReglaInflada) return badge(codigo);

            return (
                <div className="flex items-center justify-center gap-1">
                    <EditableRelationCell
                        initialName={displayName}
                        initialId={null}
                        onSave={(newId) => onEditReglaInflada(row.original.id, row.original.canalId, newId)}
                        loadOptions={loadReglasInflado}
                        placeholder="Buscar regla..."
                        endpoint="precios-inflados"
                        labelKey="codigo"
                        nullable
                        disabled={!canEdit}
                        renderDisplay={(_name, onClick) => badge(codigo, onClick)}
                    />
                    {codigo && (
                        <button
                            onClick={() => onEditReglaInflada(row.original.id, row.original.canalId, null)}
                            disabled={!canEdit}
                            className="shrink-0 text-gray-300 hover:text-red-500 transition-colors p-0.5 rounded hover:bg-red-50 disabled:cursor-default disabled:hover:text-gray-300 disabled:hover:bg-transparent"
                            title="Quitar regla inflada"
                        >
                            <XMarkIcon className="w-3.5 h-3.5" />
                        </button>
                    )}
                </div>
            );
        },
    },
    // --- Resultados financieros ---
    {
        accessorKey: "ganancia",
        header: "Ganancia",
        size: 110,
        enableSorting: true,
        meta: { center: true },
        cell: ({ getValue }) => (
            <span className={`font-mono ${ganColor(getValue() as number)}`}>{formatARS(getValue() as number)}</span>
        ),
    },
    {
        accessorKey: "costosVenta",
        header: "Costos Venta",
        size: 110,
        enableSorting: true,
        meta: { center: true },
        cell: ({ getValue }) => {
            const val = getValue() as number | null;
            return val != null
                ? <span className="font-mono text-gray-500">{formatARS(val)}</span>
                : <span className="text-gray-300">—</span>;
        },
    },
    {
        accessorKey: "ingresoNetoVendedor",
        header: "Ingreso Neto",
        size: 110,
        enableSorting: true,
        meta: { center: true },
        cell: ({ getValue }) => {
            const val = getValue() as number | null;
            return val != null
                ? <span className="font-mono text-gray-700">{formatARS(val)}</span>
                : <span className="text-gray-300">—</span>;
        },
    },
    // --- Márgenes calculados ---
    {
        accessorKey: "margenSobrePvp",
        header: "Mrg s/PVP",
        size: 95,
        enableSorting: true,
        meta: { center: true },
        cell: ({ getValue }) => {
            const val = getValue() as number;
            return <span className={mrgColor(val)}>{pctCell(val)}</span>;
        },
    },
    {
        accessorKey: "margenSobreIngresoNeto",
        header: "Mrg s/IN",
        size: 105,
        enableSorting: true,
        meta: { center: true },
        cell: ({ getValue }) => {
            const val = getValue() as number | null;
            const alerta = mrgAlertIcon(val);
            return (
                <span className={`inline-flex items-center gap-1 ${mrgColor(val)}`}
                    title={
                        val == null ? undefined
                            : val < 0 ? "Vende a pérdida — el ingreso neto no cubre el costo del producto"
                                : val < 15 ? "Rentabilidad ajustada — el dueño se queda con poco después de impuestos y costos"
                                    : undefined
                    }
                >
                    {alerta && <span aria-hidden>{alerta}</span>}
                    <span>{pctCell(val)}</span>
                </span>
            );
        },
    },
    {
        accessorKey: "markupPorcentaje",
        header: "Markup%",
        size: 95,
        enableSorting: true,
        meta: { center: true },
        cell: ({ getValue }) => {
            const val = getValue() as number | null;
            return <span className={markupColor(val)}>{pctCell(val)}</span>;
        },
    },
    // --- Descuentos ---
    {
        id: "descResumen",
        header: "Descuento",
        size: 140,
        enableSorting: false,
        meta: { center: true, headerClassName: DISCOUNT_HEADER_CLASS },
        cell: ({ row }) => {
            const pct = row.original.descPorcentaje;
            const min = row.original.descMontoMinimo;
            if (pct == null) return <span className="text-gray-300">—</span>;
            const extras = row.original.descuentos.slice(1);
            return (
                <span className="text-xs font-medium text-orange-600 bg-orange-50 px-1.5 py-0.5 rounded" title={extras.length > 0 ? `+${extras.length} más: ${extras.map((d) => `${d.descuentoPorcentaje}% (mín $${d.montoMinimo?.toLocaleString("es-AR")})`).join(", ")}` : undefined}>
                    {pct}% <span className="text-orange-400">(mín {formatARS(min ?? 0)})</span>
                </span>
            );
        },
    },
    {
        accessorKey: "descPvp",
        header: "PVP c/Desc",
        size: 110,
        enableSorting: false,
        meta: { center: true, headerClassName: DISCOUNT_HEADER_CLASS },
        cell: ({ getValue }) => {
            const val = getValue() as number | null;
            return val != null ? <span className="font-mono text-orange-700">{formatARS(val)}</span> : <span className="text-gray-300">—</span>;
        },
    },
    {
        accessorKey: "descGanancia",
        header: "Gan c/Desc",
        size: 110,
        enableSorting: false,
        meta: { center: true, headerClassName: DISCOUNT_HEADER_CLASS },
        cell: ({ getValue }) => {
            const val = getValue() as number | null;
            if (val == null) return <span className="text-gray-300">—</span>;
            return <span className={`font-mono ${ganColor(val)}`}>{formatARS(val)}</span>;
        },
    },
    {
        accessorKey: "descCostosVenta",
        header: "CV c/Desc",
        size: 110,
        enableSorting: false,
        meta: { center: true, headerClassName: DISCOUNT_HEADER_CLASS },
        cell: ({ getValue }) => {
            const val = getValue() as number | null;
            return val != null ? <span className="font-mono text-gray-500">{formatARS(val)}</span> : <span className="text-gray-300">—</span>;
        },
    },
    {
        accessorKey: "descIngresoNeto",
        header: "IN c/Desc",
        size: 110,
        enableSorting: false,
        meta: { center: true, headerClassName: DISCOUNT_HEADER_CLASS },
        cell: ({ getValue }) => {
            const val = getValue() as number | null;
            return val != null ? <span className="font-mono text-gray-700">{formatARS(val)}</span> : <span className="text-gray-300">—</span>;
        },
    },
    {
        accessorKey: "descMargenSobreIN",
        header: "Mrg IN c/D",
        size: 105,
        enableSorting: false,
        meta: { center: true, headerClassName: DISCOUNT_HEADER_CLASS },
        cell: ({ getValue }) => {
            const val = getValue() as number | null;
            const alerta = mrgAlertIcon(val);
            return (
                <span className={`inline-flex items-center gap-1 ${mrgColor(val)}`}
                    title={
                        val == null ? undefined
                            : val < 0 ? "Con descuento se vende a pérdida"
                                : val < 15 ? "Con descuento la rentabilidad queda ajustada"
                                    : undefined
                    }
                >
                    {alerta && <span aria-hidden>{alerta}</span>}
                    <span>{pctCell(val)}</span>
                </span>
            );
        },
    },
    {
        accessorKey: "descMargenSobrePvp",
        header: "Mrg PVP c/D",
        size: 95,
        enableSorting: false,
        meta: { center: true, headerClassName: DISCOUNT_HEADER_CLASS },
        cell: ({ getValue }) => {
            const val = getValue() as number | null;
            return <span className={mrgColor(val)}>{pctCell(val)}</span>;
        },
    },
    {
        accessorKey: "descMarkup",
        header: "Mkup c/Desc",
        size: 95,
        enableSorting: false,
        meta: { center: true, headerClassName: DISCOUNT_HEADER_CLASS },
        cell: ({ getValue }) => {
            const val = getValue() as number | null;
            return <span className={markupColor(val)}>{pctCell(val)}</span>;
        },
    },
    {
        accessorKey: "fechaUltimoCalculo",
        header: "Últ. Cálculo",
        size: 130,
        enableSorting: true,
        meta: { center: true },
        cell: ({ getValue }) => {
            const val = getValue() as string | null;
            if (!val) return <span className="text-gray-300">—</span>;
            const d = new Date(val);
            return (
                <span className="text-xs text-gray-500" title={d.toLocaleString("es-AR")}>
                    {formatFecha(d)}
                </span>
            );
        },
    },
    {
        id: "acciones",
        header: "Acciones",
        size: 250,
        enableSorting: false,
        meta: { center: true },
        cell: ({ row }) => {
            const prod = row.original;
            const loading = calcLoading[prod.id] ?? false;
            const disabled = loading || globalLoading;
            return (
                <div className="flex gap-1.5">
                    <Link
                        href={`/productos?q=${encodeURIComponent(prod.sku)}`}
                        className={getTableActionButtonClasses("primary")}
                        title="Ver producto"
                    >
                        <EyeIcon className="w-3.5 h-3.5" />
                    </Link>
                    <TableActionButton
                        onClick={() => onVerFormula(prod.id, prod.canalId, prod.cuotas)}
                        title="Ver fórmula de cálculo"
                        icon={<CalculatorIcon className="w-3.5 h-3.5" />}
                        tone="accent"
                    >
                        Fórmula
                    </TableActionButton>
                    <TableActionButton
                        onClick={() => onRecalcular(prod.id)}
                        disabled={disabled}
                        title="Recalcular precio"
                        icon={<ArrowPathIcon className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />}
                        tone="success"
                    >
                        {loading ? "..." : "Recalcular"}
                    </TableActionButton>
                </div>
            );
        },
    },
    ];
};

/* ─── Badge-style dropdown (replaces native <select>) ─── */
type BadgeOption = { value: string | number; label: string; [k: string]: unknown };

function BadgeSelect({
    icon, label, value, options, renderBadge, onChange, disabled = false,
}: {
    icon: React.ReactNode;
    label: string;
    value: string | number;
    options: BadgeOption[];
    renderBadge: (label: string, option?: BadgeOption) => React.ReactNode;
    onChange: (value: string | number) => void;
    disabled?: boolean;
}) {
    const [open, setOpen] = useState(false);
    const ref = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!open) return;
        const handleClick = (e: MouseEvent) => {
            if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
        };
        document.addEventListener("mousedown", handleClick);
        return () => document.removeEventListener("mousedown", handleClick);
    }, [open]);

    const selected = options.find((o) => String(o.value) === String(value)) ?? options[0];

    return (
        <div className="flex items-center gap-1.5 relative" ref={ref}>
            {icon}
            <span className="text-xs font-semibold text-gray-500 dark:text-slate-400 uppercase tracking-wide">{label}</span>
            <button
                type="button"
                disabled={disabled}
                onClick={() => { if (!disabled) setOpen(!open); }}
                className={`flex items-center gap-1.5 text-sm border border-gray-300 dark:border-slate-600 rounded-md px-2.5 py-1.5 bg-white dark:bg-slate-800 transition min-w-[100px] ${
                    disabled ? "opacity-50 cursor-not-allowed" : "hover:border-blue-400 focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                }`}
            >
                {renderBadge(selected.label, selected)}
                <svg className="w-3.5 h-3.5 text-gray-400 ml-auto shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
            </button>
            {open && !disabled && (
                <div className="absolute top-full left-0 mt-1 z-50 min-w-[180px] max-h-72 overflow-auto rounded-lg border border-gray-200 dark:border-slate-600 bg-white dark:bg-slate-800 shadow-lg py-1">
                    {options.map((opt) => (
                        <button
                            key={String(opt.value)}
                            type="button"
                            onClick={() => { onChange(opt.value); setOpen(false); }}
                            className={`w-full px-3 py-1.5 flex items-center justify-center gap-2 hover:bg-gray-100 dark:hover:bg-slate-700 transition ${
                                String(opt.value) === String(value) ? "bg-blue-50 dark:bg-blue-900/20" : ""
                            }`}
                        >
                            {renderBadge(opt.label, opt)}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
}

/** Casilla de verificación visual (no es un <input>; el click lo maneja el botón padre). */
function CheckBox({ checked }: { checked: boolean }) {
    return (
        <span className={`flex h-4 w-4 shrink-0 items-center justify-center rounded border ${
            checked ? "bg-blue-600 border-blue-600" : "border-gray-300 dark:border-slate-500"
        }`}>
            {checked && (
                <svg className="h-3 w-3 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                </svg>
            )}
        </span>
    );
}

/**
 * Selector de canales con multi-selección por checkboxes y opción "TODOS"
 * exclusiva. `selected` es la lista de canales elegidos (`[]` = TODOS).
 * No cierra el dropdown al elegir (permite marcar varios); cierra al click afuera.
 * Cada cambio se notifica vía `onToggle(value | "all")`; la regla TODOS-exclusivo
 * la resuelve el padre con `toggleCanal`.
 */
function BadgeMultiSelect({
    icon, label, selected, options, allLabel, renderBadge, onToggle,
}: {
    icon: React.ReactNode;
    label: string;
    selected: number[];
    options: { value: number; label: string }[];
    allLabel: string;
    renderBadge: (label: string) => React.ReactNode;
    onToggle: (value: number | "all") => void;
}) {
    const [open, setOpen] = useState(false);
    const ref = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!open) return;
        const handleClick = (e: MouseEvent) => {
            if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
        };
        document.addEventListener("mousedown", handleClick);
        return () => document.removeEventListener("mousedown", handleClick);
    }, [open]);

    const isAll = selected.length === 0;
    const selectedSet = new Set(selected);

    return (
        <div className="flex items-center gap-1.5 relative" ref={ref}>
            {icon}
            <span className="text-xs font-semibold text-gray-500 dark:text-slate-400 uppercase tracking-wide">{label}</span>
            <button
                type="button"
                onClick={() => setOpen(!open)}
                className="flex items-center gap-1.5 text-sm border border-gray-300 dark:border-slate-600 rounded-md px-2.5 py-1.5 bg-white dark:bg-slate-800 hover:border-blue-400 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition min-w-[100px]"
            >
                {isAll ? (
                    renderBadge(allLabel)
                ) : (
                    <span className="flex flex-wrap items-center gap-1">
                        {options.filter((o) => selectedSet.has(o.value)).map((o) => (
                            <span key={o.value}>{renderBadge(o.label)}</span>
                        ))}
                    </span>
                )}
                <svg className="w-3.5 h-3.5 text-gray-400 ml-auto shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
            </button>
            {open && (
                <div className="absolute top-full left-0 mt-1 z-50 min-w-[180px] max-h-72 overflow-auto rounded-lg border border-gray-200 dark:border-slate-600 bg-white dark:bg-slate-800 shadow-lg py-1">
                    <button
                        type="button"
                        onClick={() => onToggle("all")}
                        className={`w-full px-3 py-1.5 flex items-center gap-2 hover:bg-gray-100 dark:hover:bg-slate-700 transition ${isAll ? "bg-blue-50 dark:bg-blue-900/20" : ""}`}
                    >
                        <CheckBox checked={isAll} />
                        {renderBadge(allLabel)}
                    </button>
                    {options.map((opt) => {
                        const checked = selectedSet.has(opt.value);
                        return (
                            <button
                                key={opt.value}
                                type="button"
                                onClick={() => onToggle(opt.value)}
                                className={`w-full px-3 py-1.5 flex items-center gap-2 hover:bg-gray-100 dark:hover:bg-slate-700 transition ${checked ? "bg-blue-50 dark:bg-blue-900/20" : ""}`}
                            >
                                <CheckBox checked={checked} />
                                {renderBadge(opt.label)}
                            </button>
                        );
                    })}
                </div>
            )}
        </div>
    );
}

export default function MonitorPrecios({
    data, isLoading, pageIndex, pageSize, pageCount, totalRecords,
    onPageChange, onPageSizeChange, search, onSearch,
    onRecalcular, onVerFormula, calcLoading, globalLoading,
    onCanalChange, onCuotasChange, onSortingChange, error,
    onEditField, onEditReglaInflada, onExportAll,
}: Props) {
    const { hasPermiso } = useAuth();
    const canEdit = hasPermiso("PRECIOS_EDITAR");
    const [sorting, setSorting] = useState<SortingState>([]);
    const scrollContainerRef = useRef<HTMLDivElement>(null);
    const savedScrollPos = useRef<{ left: number; top: number } | null>(null);
    const [lastEditedCell, setLastEditedCell] = useState<{ productoId: number; field: string } | null>(null);
    const lastEditedTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    useEffect(() => () => {
        if (lastEditedTimeoutRef.current) clearTimeout(lastEditedTimeoutRef.current);
    }, []);

    const saveScrollPosition = () => {
        if (scrollContainerRef.current) {
            savedScrollPos.current = {
                left: scrollContainerRef.current.scrollLeft,
                top: scrollContainerRef.current.scrollTop,
            };
        }
    };
    // null = no inicializado, [] = todos, [n…] = canales específicos.
    const [selectedCanales, setSelectedCanales] = useState<number[] | null>(null);
    const [selectedCuotas, setSelectedCuotas] = useState<number | "all" | null>(null);
    const [showHelp, setShowHelp] = useState(false);
    const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
    const canalPersistKey = selectedCanales === null ? "none" : claveCanalPersistencia(selectedCanales);
    const storageKey = `hiddenColumns_monitor-precios_${canalPersistKey}`;
    const presetStorageKey = `columnPreset_monitor-precios_${canalPersistKey}`;
    const [hiddenColumns, setHiddenColumns] = useState<Set<string>>(() => {
        if (typeof window === "undefined") return new Set();
        try {
            const saved = localStorage.getItem(storageKey);
            return saved ? new Set(JSON.parse(saved)) : new Set();
        } catch {
            return new Set();
        }
    });
    const [showColumnMenu, setShowColumnMenu] = useState(false);
    const columnMenuRef = useRef<HTMLDivElement>(null);
    const [isFullscreen, setIsFullscreen] = useState(false);
    const [selectedPreset, setSelectedPreset] = useState<string>(() => {
        if (typeof window === "undefined") return "rentabilidad";
        return localStorage.getItem(presetStorageKey) || "rentabilidad";
    });

    useEffect(() => {
        const handleKey = (e: KeyboardEvent) => {
            if (e.key === "F11") { e.preventDefault(); setIsFullscreen((p) => !p); }
            if (e.key === "Escape" && isFullscreen) setIsFullscreen(false);
        };
        window.addEventListener("keydown", handleKey);
        return () => window.removeEventListener("keydown", handleKey);
    }, [isFullscreen]);

    // Cargar canales desde la API
    const [canalesDisponibles, setCanalesDisponibles] = useState<{ id: number; nombre: string }[]>([]);

    useEffect(() => {
        getCanalesAPI(0, 100, {}, "nombre,asc")
            .then((res) => {
                const canales = (res.content ?? []).map((c: any) => ({ id: c.id, nombre: c.nombre }));
                setCanalesDisponibles(canales);
                if (selectedCanales == null) {
                    setSelectedCanales([]);
                    onCanalChange?.([]);
                }
            })
            .catch((e) => console.warn("No se pudieron cargar canales:", e));
    }, []);

    // Cargar cuotas disponibles — solo se actualiza cuando cuotas="all" (datos completos del canal)
    const [efectiveCuotas, setEfectiveCuotas] = useState<{ cuotas: number; descripcion: string }[]>([]);

    // Cargar cuotas disponibles desde la configuración canal-concepto-cuotas (fuente de verdad)
    useEffect(() => {
        if (selectedCanales == null) return;
        setEfectiveCuotas([]);

        // Con 2+ canales el filtro de cuotas se deshabilita y se fuerza a "Todas":
        // no tiene sentido listar cuotas mezcladas entre canales distintos.
        if (cuotasDeshabilitadas(selectedCanales)) return;

        // Token de cancelación: si el canal cambia antes de que llegue la respuesta,
        // marcamos esta request como obsoleta para no pisar los datos del nuevo canal.
        let cancelado = false;

        // [] = todos → cuotas globales; [n] = un canal → cuotas de ese canal.
        const promise = selectedCanales.length === 0
            ? getCuotasAPI(0, 200, "", {}, "cuotas,asc").then((res) => res.content ?? [])
            : getCuotasPorCanalAPI(selectedCanales[0]);

        promise
            .then((items) => {
                if (cancelado) return;
                const cuotasMap = new Map<number, string>();
                for (const item of items) {
                    if (!cuotasMap.has(item.cuotas)) {
                        cuotasMap.set(item.cuotas, item.descripcion);
                    }
                }
                setEfectiveCuotas(
                    Array.from(cuotasMap.entries())
                        .map(([cuotas, descripcion]) => ({ cuotas, descripcion }))
                        .sort((a, b) => a.cuotas - b.cuotas)
                );
            })
            .catch(() => { if (!cancelado) setEfectiveCuotas([]); });

        return () => { cancelado = true; };
    }, [selectedCanales]);

    // Cargar márgenes configurados para los productos de la página
    const [margenesMap, setMargenesMap] = useState<Record<number, ProductoMargenDTO | null>>({});
    useEffect(() => {
        if (data.length === 0) return;
        let cancelado = false;
        const ids = data.map((p) => p.id);
        Promise.all(
            ids.map((id) =>
                getProductoMargenAPI(id)
                    .then((m) => [id, m] as const)
                    .catch(() => [id, null] as const)
            )
        ).then((results) => {
            if (cancelado) return;
            const map: Record<number, ProductoMargenDTO | null> = {};
            for (const [id, m] of results) map[id] = m;
            setMargenesMap(map);
        });
        return () => { cancelado = true; };
    }, [data]);

    // Auto-seleccionar "Todos" en cuotas cuando cambian
    useEffect(() => {
        if (efectiveCuotas.length > 0) {
            if (selectedCuotas === null) {
                setSelectedCuotas("all");
                onCuotasChange?.("all");
            } else if (selectedCuotas !== "all") {
                const currentValid = efectiveCuotas.some((c) => c.cuotas === selectedCuotas);
                if (!currentValid) {
                    setSelectedCuotas("all");
                    onCuotasChange?.("all");
                }
            }
        } else {
            setSelectedCuotas(null);
        }
    }, [efectiveCuotas]);

    // Aplanar datos: buscar precio según canal/cuotas seleccionados.
    // Comparte la transformación con `aplanarParaExport` (ver helper externo).
    const filas: FilaComparador[] = useMemo(() => {
        if (selectedCanales == null) return [];
        return aplanarParaExport(data, margenesMap, {
            canales: selectedCanales,
            cuotas: selectedCuotas,
        });
    }, [data, selectedCanales, selectedCuotas, margenesMap]);

    // Limpiar selección cuando cambian las filas
    useEffect(() => { setSelectedIds(new Set()); }, [data, selectedCanales, selectedCuotas]);

    // Cargar columnas ocultas cuando cambia el canal
    useEffect(() => {
        if (typeof window === "undefined") return;
        try {
            const saved = localStorage.getItem(storageKey);
            setHiddenColumns(saved ? new Set(JSON.parse(saved)) : new Set());
            setSelectedPreset(localStorage.getItem(presetStorageKey) || resolvePresetFromHiddenColumns(saved ? new Set(JSON.parse(saved)) : new Set()));
        } catch {
            setHiddenColumns(new Set());
            setSelectedPreset("rentabilidad");
        }
    }, [presetStorageKey, storageKey]);

    // Persistir columnas ocultas en localStorage (por canal)
    useEffect(() => {
        try {
            localStorage.setItem(storageKey, JSON.stringify([...hiddenColumns]));
        } catch { /* QuotaExceededError o modo privado — ignorar */ }
    }, [hiddenColumns, storageKey]);

    useEffect(() => {
        if (typeof window === "undefined") return;
        try {
            localStorage.setItem(presetStorageKey, selectedPreset);
        } catch { /* QuotaExceededError o modo privado — ignorar */ }
    }, [presetStorageKey, selectedPreset]);

    useEffect(() => {
        const resolved = resolvePresetFromHiddenColumns(hiddenColumns);
        if (resolved !== selectedPreset) {
            setSelectedPreset(resolved);
        }
    }, [hiddenColumns, selectedPreset]);

    // Cerrar menú de columnas al hacer click fuera
    useEffect(() => {
        if (!showColumnMenu) return;
        const handler = (e: MouseEvent) => {
            if (columnMenuRef.current && !columnMenuRef.current.contains(e.target as Node)) {
                setShowColumnMenu(false);
            }
        };
        document.addEventListener("mousedown", handler);
        return () => document.removeEventListener("mousedown", handler);
    }, [showColumnMenu]);

    const toggleColumn = (colId: string) => {
        setHiddenColumns((prev) => {
            const next = new Set(prev);
            next.has(colId) ? next.delete(colId) : next.add(colId);
            return next;
        });
    };

    const applyPreset = (presetId: string) => {
        setSelectedPreset(presetId);
        setHiddenColumns(buildHiddenColumnsFromPreset(presetId));
    };

    const wrappedOnEditField = useMemo(() => {
        if (!onEditField) return undefined;
        return (productoId: number, canalId: number, field: string, value: number | string) => {
            saveScrollPosition();
            // Marcar todas las filas del producto como "recién editado" en este field
            // (costo/iva/márgenes son a nivel producto, se reflejan en todas sus filas canal/cuotas)
            if (lastEditedTimeoutRef.current) clearTimeout(lastEditedTimeoutRef.current);
            setLastEditedCell({ productoId, field });
            lastEditedTimeoutRef.current = setTimeout(() => setLastEditedCell(null), 2500);
            return onEditField(productoId, canalId, field, value);
        };
    }, [onEditField]);

    const wrappedOnEditReglaInflada = useMemo(() => {
        if (!onEditReglaInflada) return undefined;
        return (productoId: number, canalId: number, precioInfladoId: number | null) => {
            saveScrollPosition();
            return onEditReglaInflada(productoId, canalId, precioInfladoId);
        };
    }, [onEditReglaInflada]);

    const allColumns = useMemo(
        () => getColumns(onRecalcular, onVerFormula, calcLoading, globalLoading, wrappedOnEditField, wrappedOnEditReglaInflada, canEdit, lastEditedCell),
        [onRecalcular, onVerFormula, calcLoading, globalLoading, wrappedOnEditField, wrappedOnEditReglaInflada, canEdit, lastEditedCell]
    );

    const columns = useMemo(
        () => allColumns.filter((col) => {
            const colId = col.id ?? (col as any).accessorKey;
            return !hiddenColumns.has(colId);
        }),
        [allColumns, hiddenColumns]
    );

    const handleSortingChange = (updater: SortingState | ((old: SortingState) => SortingState)) => {
        saveScrollPosition();
        const next = typeof updater === "function" ? updater(sorting) : updater;
        setSorting(next);
        onSortingChange?.(next);
    };

    const handleHeaderClick = (columnId: string, multi: boolean) => {
        saveScrollPosition();
        const current = sorting.find((s) => s.id === columnId);
        let next: SortingState;
        if (!current) {
            next = multi ? [...sorting, { id: columnId, desc: false }] : [{ id: columnId, desc: false }];
        } else if (!current.desc) {
            next = sorting.map((s) => s.id === columnId ? { ...s, desc: true } : s);
        } else {
            next = sorting.filter((s) => s.id !== columnId);
        }
        setSorting(next);
        onSortingChange?.(next);
    };

    useEffect(() => {
        if (savedScrollPos.current !== null && scrollContainerRef.current) {
            scrollContainerRef.current.scrollLeft = savedScrollPos.current.left;
            scrollContainerRef.current.scrollTop = savedScrollPos.current.top;
            savedScrollPos.current = null;
        }
    }, [filas]);

    const table = useReactTable({
        data: filas,
        columns,
        state: { sorting },
        onSortingChange: handleSortingChange,
        getCoreRowModel: getCoreRowModel(),
        manualSorting: true,
        enableSortingRemoval: true,
        sortDescFirst: false,
        isMultiSortEvent: (e: unknown) => (e as MouseEvent).ctrlKey || (e as MouseEvent).metaKey,
        getRowId: (row) => row._rowKey,
    });

    const toggleSelect = (key: string) => {
        setSelectedIds((prev) => {
            const next = new Set(prev);
            next.has(key) ? next.delete(key) : next.add(key);
            return next;
        });
    };

    const toggleSelectAll = () => {
        if (selectedIds.size === filas.length) {
            setSelectedIds(new Set());
        } else {
            setSelectedIds(new Set(filas.map((f) => f._rowKey)));
        }
    };

    const num = (v: number | null) => v != null ? v.toString().replace(".", ",") : "";

    const handleCopy = () => {
        const rows = selectedIds.size > 0
            ? filas.filter((f) => selectedIds.has(f._rowKey))
            : filas;
        if (rows.length === 0) return;

        const headers = ["SKU", "Producto", "Canal", "Cuotas", "Costo", "IVA%", "PVP", "PVP Inflado", "Regla Inflado", "Descuento", "PVP c/Desc", "Gan c/Desc", "CV c/Desc", "IN c/Desc", "Mrg IN c/D%", "Mrg PVP c/D%", "Mkup c/Desc%", "Ganancia", "Costos Venta", "Ingreso Neto", "Mrg s/PVP%", "Mrg s/IN%", "Markup%", "Mrg Minorista%", "Mrg Mayorista%", "Fijo Minorista", "Fijo Mayorista"];
        const lines = rows.map((r) => [
            r.sku, r.descripcion, r.canalNombre, r.cuotasDescripcion, num(r.costo), num(r.iva), num(r.pvp), num(r.pvpInflado),
            r.precioInfladoCodigo ?? "",
            r.descPorcentaje != null ? `${r.descPorcentaje}% (mín ${r.descMontoMinimo})` : "",
            num(r.descPvp), num(r.descGanancia),
            num(r.descCostosVenta), num(r.descIngresoNeto), num(r.descMargenSobreIN), num(r.descMargenSobrePvp), num(r.descMarkup),
            num(r.ganancia), num(r.costosVenta), num(r.ingresoNetoVendedor),
            num(r.margenSobrePvp), num(r.margenSobreIngresoNeto), num(r.markupPorcentaje),
            num(r.margenMinorista), num(r.margenMayorista), num(r.margenFijoMinorista), num(r.margenFijoMayorista),
        ].join("\t"));

        const tsv = [headers.join("\t"), ...lines].join("\n");
        navigator.clipboard.writeText(tsv).then(() => {
            toast.success(`${rows.length} producto(s) copiado(s) al portapapeles`);
        });
    };

    const handleExport = async () => {
        const visibleHeaders = new Set(
            table
                .getVisibleLeafColumns()
                .map((col) => (typeof col.columnDef.header === "string" ? col.columnDef.header : null))
                .filter(Boolean) as string[]
        );
        const exportColumns = MONITOR_EXPORT_COLUMNS.filter((col) => visibleHeaders.has(col.header));

        if (selectedIds.size > 0) {
            // Exportar solo seleccionados
            const rows = filas.filter((f) => selectedIds.has(f._rowKey));
            const exportRows = rows.map((r) => ({
                ...r,
                descResumen: r.descPorcentaje != null ? `${r.descPorcentaje}% (mín ${formatARS(r.descMontoMinimo ?? 0)})` : "—",
            }));
            exportToExcel(
                exportRows as unknown as Record<string, unknown>[],
                exportColumns.map((col) => ({ header: col.header, accessor: String(col.accessor) })),
                "monitor-precios"
            );
            notificar.success(`${rows.length} registro(s) exportados`);
        } else if (onExportAll) {
            // Exportar todos via fetch. Hay que APLANAR la respuesta jerárquica
            // (producto → canales → precios) a filas, igual que para renderizar.
            // Si no, los accessors anidados quedan vacíos en el Excel.
            try {
                const allData = await onExportAll();
                const filasExport = aplanarParaExport(allData, margenesMap, {
                    canales: selectedCanales ?? [],
                    cuotas: selectedCuotas,
                }).map((r) => ({
                    ...r,
                    descResumen: r.descPorcentaje != null
                        ? `${r.descPorcentaje}% (mín ${formatARS(r.descMontoMinimo ?? 0)})`
                        : "—",
                }));

                // Excel tiene un límite duro de 1.048.576 filas por hoja.
                const EXCEL_ROW_LIMIT = 1_048_576 - 1; // -1 por el header
                if (filasExport.length > EXCEL_ROW_LIMIT) {
                    notificar.warning(
                        `El resultado tiene ${filasExport.length.toLocaleString("es-AR")} filas, ` +
                        `excede el límite de Excel (${EXCEL_ROW_LIMIT.toLocaleString("es-AR")}). ` +
                        `Refiná filtros (canal, cuotas, búsqueda) y volvé a exportar.`
                    );
                    return;
                }

                exportToExcel(
                    filasExport as unknown as Record<string, unknown>[],
                    exportColumns.map((col) => ({ header: col.header, accessor: String(col.accessor) })),
                    "monitor-precios"
                );
                notificar.success(`${filasExport.length.toLocaleString("es-AR")} registro(s) exportados`);
            } catch {
                notificar.error("Error al exportar");
            }
        } else {
            // Fallback: exportar página actual
            const exportRows = filas.map((r) => ({
                ...r,
                descResumen: r.descPorcentaje != null ? `${r.descPorcentaje}% (mín ${formatARS(r.descMontoMinimo ?? 0)})` : "—",
            }));
            exportToExcel(
                exportRows as unknown as Record<string, unknown>[],
                exportColumns.map((col) => ({ header: col.header, accessor: String(col.accessor) })),
                "monitor-precios"
            );
            notificar.success(`${filas.length} registro(s) exportados`);
        }
    };

    const SortIcon = ({ column }: { column: any }) => {
        const sorted = column.getIsSorted();
        if (sorted === "asc") return <ChevronUpIcon className="w-3.5 h-3.5 text-blue-600" />;
        if (sorted === "desc") return <ChevronDownIcon className="w-3.5 h-3.5 text-blue-600" />;
        return <ChevronUpDownIcon className="w-3.5 h-3.5 text-gray-300" />;
    };

    return (
        <EditingCellProvider>
        <div className={`flex flex-col h-full bg-white dark:bg-slate-900 ${isFullscreen ? "fixed inset-0 z-[100]" : ""}`}>
            {/* Toolbar: búsqueda + filtros canal/cuotas */}
            <div className="shrink-0 px-4 py-2.5 border-b border-gray-200 dark:border-slate-700 bg-gray-50/80 dark:bg-slate-800/80">
                <div className="flex items-center gap-2">
                    {/* Izquierda: búsqueda + filtros */}
                    <div className="flex-1 flex items-center gap-3 flex-wrap">
                        <SearchInput
                            placeholder="Buscar por SKU, MLA o Nombre..."
                            initialValue={search}
                            onSearch={(val) => { onSearch(val); onPageChange(0); }}
                        />
                        <BadgeMultiSelect
                            icon={<ChartBarIcon className="w-4 h-4 text-gray-400 dark:text-slate-500" />}
                            label="Canal"
                            selected={selectedCanales ?? []}
                            allLabel="Todos"
                            options={canalesDisponibles.map((c) => ({ value: c.id, label: c.nombre }))}
                            renderBadge={(label) => {
                                if (label === "Todos") return <span className={`${CANAL_BADGE_CLASS} text-gray-700 bg-gray-100 dark:text-slate-200 dark:bg-slate-700`}>{label}</span>;
                                const colors = getCanalColor(label);
                                return <span className={`${CANAL_BADGE_CLASS} ${colors}`}>{label}</span>;
                            }}
                            onToggle={(v) => {
                                const next = toggleCanal(selectedCanales ?? [], v);
                                setSelectedCanales(next);
                                onCanalChange?.(next);
                                // Con 2+ canales el filtro de cuotas se fuerza a "Todas".
                                if (cuotasDeshabilitadas(next)) {
                                    setSelectedCuotas("all");
                                    onCuotasChange?.("all");
                                }
                                onPageChange(0);
                            }}
                        />
                        <BadgeSelect
                            icon={<CreditCardIcon className="w-4 h-4 text-gray-400 dark:text-slate-500" />}
                            label="Cuotas"
                            disabled={cuotasDeshabilitadas(selectedCanales ?? [])}
                            value={selectedCuotas ?? "all"}
                            options={[
                                { value: "all", label: "Todas" },
                                ...efectiveCuotas.map((c) => ({
                                    value: c.cuotas,
                                    label: (selectedCanales?.length ?? 0) === 0
                                        ? (c.cuotas === -1 ? "Transferencia"
                                            : c.cuotas === 0 ? "Contado"
                                            : `${c.cuotas} ${c.cuotas === 1 ? "cuota" : "cuotas"}`)
                                        : c.descripcion,
                                    cuotas: c.cuotas,
                                })),
                            ]}
                            renderBadge={(label, opt) => {
                                if (label === "Todas") return <span className={`${CANAL_BADGE_CLASS} text-gray-700 bg-gray-100 dark:text-slate-200 dark:bg-slate-700`}>{label}</span>;
                                const cuotas = (opt as any)?.cuotas ?? 0;
                                const colors = getCuotasColor(cuotas);
                                return <span className={`text-xs font-bold px-2.5 py-0.5 rounded-full uppercase tracking-wide ${colors}`}>{label}</span>;
                            }}
                            onChange={(v) => {
                                const val = v === "all" ? "all" as const : Number(v);
                                setSelectedCuotas(val);
                                onCuotasChange?.(val);
                                onPageChange?.(0);
                            }}
                        />
                        {selectedIds.size > 0 && (
                            <div className="text-xs text-blue-600 font-medium">{selectedIds.size} seleccionado(s)</div>
                        )}
                        <div className="flex items-center gap-2 flex-wrap">
                            <span className="text-[10px] font-semibold uppercase tracking-wider text-gray-400 dark:text-slate-500">Vista</span>
                            {MONITOR_PRESETS.map((preset) => {
                                const active = selectedPreset === preset.id;
                                return (
                                    <button
                                        key={preset.id}
                                        type="button"
                                        onClick={() => applyPreset(preset.id)}
                                        className={`rounded-full border px-2.5 py-1 text-xs font-medium transition-colors ${
                                            active
                                                ? "border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-500/30 dark:bg-blue-500/15 dark:text-blue-200"
                                                : "border-gray-200 bg-white text-gray-500 hover:bg-gray-50 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-400 dark:hover:bg-slate-700"
                                        }`}
                                    >
                                        {preset.label}
                                    </button>
                                );
                            })}
                            {selectedPreset === "custom" && (
                                <span className="rounded-full border border-amber-200 bg-amber-50 px-2.5 py-1 text-xs font-medium text-amber-700 dark:border-amber-500/30 dark:bg-amber-500/15 dark:text-amber-200">
                                    Personalizada
                                </span>
                            )}
                            <div className="relative" ref={columnMenuRef}>
                                <button
                                    onClick={() => setShowColumnMenu((v) => !v)}
                                    className={`p-1.5 rounded-lg border transition-colors ${showColumnMenu
                                        ? "border-blue-300 bg-blue-50 text-blue-600 dark:border-blue-500/30 dark:bg-blue-500/15 dark:text-blue-300"
                                        : "border-gray-200 bg-white hover:bg-gray-100 text-gray-500 dark:border-slate-600 dark:bg-slate-800 dark:hover:bg-blue-500/20 dark:hover:border-blue-500/40 dark:text-slate-400 dark:hover:text-blue-300"
                                        }`}
                                    title="Mostrar/ocultar columnas"
                                >
                                    <AdjustmentsVerticalIcon className="w-4 h-4" />
                                    {hiddenColumns.size > 0 && (
                                        <span className="absolute -top-1.5 -right-1.5 w-4 h-4 text-[9px] font-bold bg-blue-500 text-white rounded-full flex items-center justify-center">
                                            {hiddenColumns.size}
                                        </span>
                                    )}
                                </button>
                                {showColumnMenu && (
                                    <div className="absolute top-full mt-1 right-0 bg-white border border-gray-200 rounded-lg shadow-lg z-50 w-52 py-1 max-h-80 overflow-y-auto dark:bg-slate-800 dark:border-slate-600">
                                        <div className="px-3 py-1.5 text-[10px] font-bold uppercase tracking-wider text-gray-400 border-b border-gray-100 dark:border-slate-700 dark:text-slate-500">Columnas visibles</div>
                                        {TOGGLEABLE_COLUMNS.map((col) => (
                                            <label
                                                key={col.id}
                                                className="flex items-center gap-2 px-3 py-1.5 hover:bg-gray-50 cursor-pointer text-sm text-gray-700 dark:hover:bg-slate-700 dark:text-slate-300"
                                            >
                                                <input
                                                    type="checkbox"
                                                    checked={!hiddenColumns.has(col.id)}
                                                    onChange={() => toggleColumn(col.id)}
                                                    className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                                                />
                                                {col.label}
                                            </label>
                                        ))}
                                        {hiddenColumns.size > 0 && (
                                            <div className="border-t border-gray-100 dark:border-slate-700 px-3 py-1.5">
                                                <button
                                                    onClick={() => setHiddenColumns(new Set())}
                                                    className="text-xs text-blue-600 hover:text-blue-800 dark:text-blue-400 dark:hover:text-blue-300"
                                                >
                                                    Mostrar todas
                                                </button>
                                            </div>
                                        )}
                                    </div>
                                )}
                            </div>
                        </div>
                    </div>
                    {/* Derecha: botones de utilidad (mismo orden que Table estándar) */}
                    <button
                        onClick={handleExport}
                        disabled={filas.length === 0}
                        className="shrink-0 p-1.5 rounded-lg border border-gray-200 bg-white hover:bg-gray-100 text-gray-500 transition-colors dark:border-slate-600 dark:bg-slate-800 dark:hover:bg-blue-500/20 dark:hover:border-blue-500/40 dark:text-slate-400 dark:hover:text-blue-300 disabled:opacity-30"
                        title={selectedIds.size > 0 ? `Exportar ${selectedIds.size} seleccionado(s)` : "Exportar página actual"}
                    >
                        <ArrowDownTrayIcon className="w-4 h-4" />
                    </button>
                    <button
                        onClick={handleCopy}
                        disabled={filas.length === 0}
                        className="shrink-0 p-1.5 rounded-lg border border-gray-200 bg-white hover:bg-gray-100 text-gray-500 transition-colors dark:border-slate-600 dark:bg-slate-800 dark:hover:bg-blue-500/20 dark:hover:border-blue-500/40 dark:text-slate-400 dark:hover:text-blue-300 disabled:opacity-30"
                        title={selectedIds.size > 0 ? `Copiar ${selectedIds.size} seleccionado(s)` : "Copiar toda la página"}
                    >
                        <ClipboardDocumentIcon className="w-4 h-4" />
                    </button>
                    <button
                        onClick={() => setShowHelp(true)}
                        className="shrink-0 p-1.5 rounded-lg border border-gray-200 bg-white hover:bg-gray-100 text-gray-500 transition-colors dark:border-slate-600 dark:bg-slate-800 dark:hover:bg-blue-500/20 dark:hover:border-blue-500/40 dark:text-slate-400 dark:hover:text-blue-300"
                        title="Ayuda: descripción de campos"
                    >
                        <InformationCircleIcon className="w-4 h-4" />
                    </button>
                    <button
                        onClick={() => setIsFullscreen((p) => !p)}
                        className="shrink-0 p-1.5 rounded-lg border border-gray-200 bg-white hover:bg-gray-100 text-gray-500 transition-colors dark:border-slate-600 dark:bg-slate-800 dark:hover:bg-blue-500/20 dark:hover:border-blue-500/40 dark:text-slate-400 dark:hover:text-blue-300"
                        title={isFullscreen ? "Salir de pantalla completa (Esc)" : "Pantalla completa (F11)"}
                    >
                        {isFullscreen ? (
                            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-4 h-4">
                                <path strokeLinecap="round" strokeLinejoin="round" d="M9 9V4.5M9 9H4.5M9 9 3.75 3.75M9 15v4.5M9 15H4.5M9 15l-5.25 5.25M15 9h4.5M15 9V4.5M15 9l5.25-5.25M15 15h4.5M15 15v4.5m0-4.5 5.25 5.25" />
                            </svg>
                        ) : (
                            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-4 h-4">
                                <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 3.75v4.5m0-4.5h4.5m-4.5 0L9 9M3.75 20.25v-4.5m0 4.5h4.5m-4.5 0L9 15M20.25 3.75h-4.5m4.5 0v4.5m0-4.5L15 9m5.25 11.25h-4.5m4.5 0v-4.5m0 4.5L15 15" />
                            </svg>
                        )}
                    </button>
                </div>
            </div>

            {(search || sorting.length > 0) && (
                <div className="shrink-0 px-4 py-1.5 border-b border-gray-200 dark:border-slate-700 bg-gray-50/50 dark:bg-slate-800/40 flex items-center gap-3 flex-wrap">
                    {search && (
                        <>
                            <span className="text-[10px] uppercase tracking-wider text-gray-400 dark:text-slate-500 font-semibold">Filtros:</span>
                            <span className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-800 text-amber-700 dark:text-amber-300">
                                Búsqueda: {search}
                                <button
                                    onClick={() => onSearch("")}
                                    className="ml-0.5 hover:text-red-500 dark:hover:text-red-400 transition-colors"
                                    title="Quitar búsqueda"
                                >×</button>
                            </span>
                            <button
                                onClick={() => onSearch("")}
                                className="text-[11px] text-gray-400 dark:text-slate-500 hover:text-red-500 dark:hover:text-red-400 transition-colors"
                            >
                                Limpiar filtros
                            </button>
                        </>
                    )}
                    {search && sorting.length > 0 && (
                        <span className="w-px h-4 bg-gray-300 dark:bg-slate-600" />
                    )}
                    {sorting.length > 0 && (
                        <>
                            <span className="text-[10px] uppercase tracking-wider text-gray-400 dark:text-slate-500 font-semibold">Orden:</span>
                            {sorting.map((s, i) => {
                                const col = table.getAllLeafColumns().find(c => c.id === s.id);
                                const label = col && typeof col.columnDef.header === "string" ? col.columnDef.header : s.id;
                                return (
                                    <span key={s.id} className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-800 text-blue-700 dark:text-blue-300">
                                        {sorting.length > 1 && <span className="text-[10px] font-bold text-blue-400 dark:text-blue-500">{i + 1}</span>}
                                        {label} {s.desc ? "↓" : "↑"}
                                        <button
                                            onClick={() => handleSortingChange(prev => prev.filter(item => item.id !== s.id))}
                                            className="ml-0.5 hover:text-red-500 dark:hover:text-red-400 transition-colors"
                                            title={`Quitar orden por ${label}`}
                                        >×</button>
                                    </span>
                                );
                            })}
                            <button
                                onClick={() => handleSortingChange([])}
                                className="text-[11px] text-gray-400 dark:text-slate-500 hover:text-red-500 dark:hover:text-red-400 transition-colors"
                            >
                                Limpiar orden
                            </button>
                        </>
                    )}
                </div>
            )}

            {error && (
                <div className="mx-4 mt-3 flex items-start gap-3 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-200">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" className="h-5 w-5 shrink-0 mt-0.5 text-red-500 dark:text-red-400">
                        <path fillRule="evenodd" d="M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Zm-8-5a.75.75 0 0 1 .75.75v4.5a.75.75 0 0 1-1.5 0v-4.5A.75.75 0 0 1 10 5Zm0 10a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z" clipRule="evenodd" />
                    </svg>
                    <div>
                        <p className="font-semibold">Error al cargar datos</p>
                        <p className="mt-1 text-red-600 dark:text-red-300">{error}</p>
                    </div>
                </div>
            )}

            {/* Tabla */}
            <div ref={scrollContainerRef} className="flex-1 overflow-auto">
                {isLoading ? (
                    <table className="w-full text-sm border-collapse">
                        <tbody>
                            {Array.from({ length: 10 }).map((_, i) => (
                                <tr key={i} className="border-b border-gray-100 even:bg-gray-50/30">
                                    <td className="px-2 py-2.5"><div className="h-4 w-4 rounded bg-gray-200 animate-pulse" /></td>
                                    {columns.map((col, j) => (
                                        <td key={j} className="px-3 py-2.5">
                                            <div className="h-4 rounded bg-gray-200 animate-pulse mx-auto max-w-[80%]" />
                                        </td>
                                    ))}
                                </tr>
                            ))}
                        </tbody>
                    </table>
                ) : filas.length === 0 ? (
                    <div className="flex items-center justify-center h-40 text-gray-400">
                        No hay productos con precios calculados para esta combinación.
                    </div>
                ) : (
                    <table className="w-full text-sm border-collapse">
                        <thead className="sticky top-0 z-10">
                            {table.getHeaderGroups().map((hg) => (
                                <tr key={hg.id} className="border-b border-gray-200 dark:border-slate-700">
                                    <th className="px-2 py-2 w-8 bg-gray-100 dark:bg-slate-800">
                                        <input
                                            type="checkbox"
                                            checked={filas.length > 0 && selectedIds.size === filas.length}
                                            onChange={toggleSelectAll}
                                            className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                                        />
                                    </th>
                                    {hg.headers.map((header) => {
                                        const isCenter = (header.column.columnDef.meta as any)?.center;
                                        const headerClassName = (header.column.columnDef.meta as any)?.headerClassName ?? "";
                                        const isEditable = !!(header.column.columnDef.meta as any)?.editable;
                                        const headerLabel = typeof header.column.columnDef.header === "string" ? header.column.columnDef.header : null;
                                        const headerDescription = headerLabel ? HEADER_DESCRIPTION_MAP[headerLabel] : undefined;
                                        const tooltipContent = headerDescription ? (
                                            <div className="flex flex-col gap-1.5">
                                                {headerLabel && (
                                                    <span className="text-[10px] font-semibold uppercase tracking-wider text-blue-300">
                                                        {headerLabel}
                                                    </span>
                                                )}
                                                <span className="text-xs leading-snug">{headerDescription}</span>
                                                {isEditable && (
                                                    <span className="text-[10px] italic text-blue-200">
                                                        ✎ Columna editable: hacé clic en una celda para modificar.
                                                    </span>
                                                )}
                                            </div>
                                        ) : null;
                                        return (
                                            <th
                                                key={header.id}
                                                // bg-gray-100 va en el <th> (no solo en el <thead>) porque
                                                // position:sticky en thead crea capas independientes por celda;
                                                // sin background propio el contenido se ve transparente.
                                                className={`px-3 py-2 text-xs font-semibold text-gray-600 dark:text-slate-300 uppercase tracking-wide cursor-pointer select-none bg-gray-100 dark:bg-slate-800 hover:bg-gray-200 dark:hover:bg-slate-700 transition-colors whitespace-nowrap ${isCenter ? "text-center" : "text-left"} ${headerClassName}`}
                                                style={{ width: header.getSize() }}
                                                onClick={header.column.getCanSort() ? (e: React.MouseEvent) => handleHeaderClick(header.column.id, e.ctrlKey || e.metaKey) : undefined}
                                            >
                                                <Tooltip content={tooltipContent} placement="bottom" maxWidth={300}>
                                                    <span className={`flex items-center gap-1 ${isCenter ? "justify-center" : ""}`}>
                                                        {isEditable && (
                                                            <PencilSquareIcon className="w-3 h-3 text-blue-400" />
                                                        )}
                                                        {flexRender(header.column.columnDef.header, header.getContext())}
                                                        {header.column.getCanSort() && <SortIcon column={header.column} />}
                                                    </span>
                                                </Tooltip>
                                            </th>
                                        );
                                    })}
                                </tr>
                            ))}
                        </thead>
                        <tbody>
                            {(() => {
                                // Pre-calculamos metadata por fila: si es inicio de grupo/producto
                                // y a qué "grupo" pertenece, para poder hacer zebra stripe POR GRUPO
                                // (todas las filas del mismo producto+canal comparten color).
                                const modelRows = table.getRowModel().rows;
                                const rowMeta: { isNewProduct: boolean; isNewGroup: boolean; groupIdx: number }[] = [];
                                let groupIdx = -1;
                                let prevOrig: typeof modelRows[number]["original"] | null = null;
                                for (const r of modelRows) {
                                    const cur = r.original;
                                    const isNewProduct = !!prevOrig && prevOrig.id !== cur.id;
                                    const isNewGroup = !prevOrig || prevOrig.id !== cur.id || prevOrig.canalId !== cur.canalId;
                                    if (isNewGroup) groupIdx++;
                                    rowMeta.push({ isNewProduct, isNewGroup, groupIdx });
                                    prevOrig = cur;
                                }
                                return modelRows.map((row, idx) => {
                                    const { isNewProduct, isNewGroup, groupIdx } = rowMeta[idx];
                                    const evenGroup = groupIdx % 2 === 0;
                                    // Cinta lateral del color del canal, coloreada en todas las
                                    // filas del grupo, para que al scanear una fila "continuada"
                                    // sepas a qué canal pertenece sin volver arriba.
                                    const canalBorder = getCanalBorderColor(row.original.canalNombre);
                                    return (
                                <tr
                                    key={row.id}
                                    className={`hover:bg-blue-50/50 dark:hover:bg-blue-900/15 transition-colors ${
                                        evenGroup
                                            ? "bg-white dark:bg-slate-900"
                                            : "bg-gray-100/60 dark:bg-slate-700/50"
                                    } ${
                                        isNewProduct
                                            ? "border-t-2 border-t-gray-300 dark:border-t-slate-600"
                                            : isNewGroup
                                                ? "border-t border-t-gray-200 dark:border-t-slate-700"
                                                : ""
                                    }`}
                                >
                                    <td className={`px-2 py-1.5 border-l-4 ${canalBorder}`}>
                                        <input
                                            type="checkbox"
                                            checked={selectedIds.has(row.original._rowKey)}
                                            onChange={() => toggleSelect(row.original._rowKey)}
                                            className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                                        />
                                    </td>
                                    {row.getVisibleCells().map((cell) => {
                                        const isCenter = (cell.column.columnDef.meta as any)?.center;
                                        const isProductLevel = PRODUCT_LEVEL_COLS.has(cell.column.id);
                                        const hideDuplicate = isProductLevel && !isNewGroup;
                                        return (
                                            <td key={cell.id} className={`px-3 py-1.5 whitespace-nowrap ${isCenter ? "text-center" : ""}`}>
                                                {hideDuplicate
                                                    ? null
                                                    : flexRender(cell.column.columnDef.cell, cell.getContext())}
                                            </td>
                                        );
                                    })}
                                </tr>
                                );
                                });
                            })()}
                        </tbody>
                    </table>
                )}
            </div>

            {/* Footer: info + paginación + leyenda */}
            <div className="relative flex items-center justify-center px-4 py-2 bg-gray-50 dark:bg-slate-800/80 border-t dark:border-slate-700">
                <div className="absolute left-4 text-xs text-gray-500 dark:text-slate-400">
                    {filas.length} filas · {totalRecords.toLocaleString("es-AR")} totales
                </div>
                <PaginationControls
                    pageIndex={pageIndex}
                    pageSize={pageSize}
                    pageCount={Math.max(pageCount, 1)}
                    onPageSizeChange={(s) => { saveScrollPosition(); onPageSizeChange(s); }}
                    onPageChange={(p) => { saveScrollPosition(); onPageChange(p); }}
                />
                <div className="absolute right-4 flex items-center gap-4 text-[10px] text-gray-400 dark:text-slate-500">
                    <span>Margen/Markup:</span>
                    <span className="text-red-600">● &lt;0%</span>
                    <span className="text-orange-500">● 0-15%</span>
                    <span className="text-yellow-600">● 15-25%</span>
                    <span className="text-green-600">● 25-40%</span>
                    <span className="text-emerald-600">● &gt;40%</span>
                </div>
            </div>

            {/* Modal Ayuda: Descripción de campos */}
            <Modal
                isOpen={showHelp}
                onClose={() => setShowHelp(false)}
                title="Descripción de campos"
            >
                <div className="max-h-[60vh] overflow-y-auto pr-1 space-y-2.5">
                    {CAMPO_DESCRIPCIONES.map((item) => {
                        const c = CAMPO_COLOR_MAP[item.color] ?? CAMPO_COLOR_MAP.blue;
                        // Mostrar los aliases del header sólo cuando difieren del nombre largo:
                        // evita ruido visual (ej: "PVP" se llama igual en ambos lados).
                        const aliases = item.headers.filter((h) => h !== item.campo);
                        return (
                            <div key={item.campo} className={`rounded-lg border-l-4 ${c.border} ${c.bg} p-3`}>
                                <div className="flex flex-wrap items-center gap-1.5">
                                    <span className={`inline-block text-xs font-bold px-2 py-0.5 rounded-full ${c.badge}`}>
                                        {item.campo}
                                    </span>
                                    {aliases.length > 0 && (
                                        <span className="text-[10px] text-gray-500 dark:text-slate-400">
                                            en la tabla:{" "}
                                            {aliases.map((h, i) => (
                                                <span key={h}>
                                                    <span className="font-mono font-semibold text-gray-700 dark:text-slate-300">{h}</span>
                                                    {i < aliases.length - 1 ? ", " : ""}
                                                </span>
                                            ))}
                                        </span>
                                    )}
                                </div>
                                <p className={`text-xs mt-1.5 leading-relaxed ${c.text} opacity-80`}>{item.descripcion}</p>
                            </div>
                        );
                    })}
                </div>
                <div className="mt-4 flex justify-end border-t pt-4">
                    <Button variant="light" onClick={() => setShowHelp(false)}>
                        <XMarkIcon className="w-4 h-4" />
                        Cerrar
                    </Button>
                </div>
            </Modal>
        </div>
        </EditingCellProvider>
    );
}

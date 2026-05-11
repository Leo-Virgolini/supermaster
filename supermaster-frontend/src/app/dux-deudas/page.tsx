"use client";
import { useState, useEffect, useRef, Fragment } from "react";
import { notificar } from "../utils/notificar";
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import Button from "../components/Button/Button";
import { useProcesoActivo } from "../context/ProcesoActivoContext";
import {
    MagnifyingGlassIcon,
    ArrowPathIcon,
    ArrowDownTrayIcon,
    XMarkIcon,
    ServerStackIcon,
    BuildingOfficeIcon,
    MapPinIcon,
    CalendarDaysIcon,
    BanknotesIcon,
    UserIcon,
    NoSymbolIcon,
} from "@heroicons/react/24/outline";

// ── Types ────────────────────────────────────────────────────────────────────

interface DeudaMovimiento {
    tipoDeValor: string;
    referencia: string;
    monto: number;
}

interface DeudaCobro {
    numeroPuntoDeVenta: string;
    numeroComprobante: number;
    personal: string;
    caja: string;
    movimientos: DeudaMovimiento[];
}

interface DeudaDetalle {
    codItem: string;
    item: string;
    cantidad: number;
    precioUni: number;
    porcDesc: number;
    porcIva: number;
}

interface DeudaComprobante {
    id: number;
    idCliente: number;
    cliente: string;
    cuit: string;
    tipoComp: string;
    letraComp: string;
    nroComp: number;
    nroPtoVta: string;
    fecha: string;
    total: number;
    montoGravado: number;
    montoIva: number;
    montoExento: number;
    montoDesc: number;
    anulada: boolean;
    urlFactura: string;
    vendedor: string | null;
    cobrado: number;
    saldo: number;
    detalles: DeudaDetalle[];
    cobros: DeudaCobro[];
    facturasReferenciadas: { numeroPuntoDeVenta: string; numeroComprobante: number; letraComprobante: string; tipoComprobante: string }[];
}

interface DeudasResponse {
    total: number;
    resumen: {
        facturas: number;
        notasCredito: number;
        notasDebito: number;
    };
    comprobantes: DeudaComprobante[];
}

// ── Service helpers ──────────────────────────────────────────────────────────

async function fetchEmpresas(): Promise<any[]> {
    const res = await fetchAPI(`${API_BASE_URL}/api/dux/empresas`);
    const data = await res.json();
    return Array.isArray(data) ? data : [];
}

async function fetchSucursales(idEmpresa: number): Promise<any[]> {
    const res = await fetchAPI(`${API_BASE_URL}/api/dux/empresas/${idEmpresa}/sucursales`);
    const data = await res.json();
    return Array.isArray(data) ? data : [];
}

async function iniciarConsultaDeudas(params: {
    fechaDesde: string;
    fechaHasta: string;
    idEmpresa: number;
    idsSucursal: number[];
    conCobro?: boolean;
    cliente?: string;
    anuladas?: boolean;
}): Promise<void> {
    const qs = new URLSearchParams({
        fechaDesde: params.fechaDesde,
        fechaHasta: params.fechaHasta,
        idEmpresa: String(params.idEmpresa),
    });
    for (const id of params.idsSucursal) qs.append("idsSucursal", String(id));
    if (params.conCobro !== undefined) qs.set("conCobro", String(params.conCobro));
    if (params.cliente) qs.set("cliente", params.cliente);
    if (params.anuladas !== undefined) qs.set("anuladas", String(params.anuladas));
    const res = await fetchAPI(`${API_BASE_URL}/api/dux/deudas-clientes/iniciar?${qs}`, { method: "POST" });
    if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.message ?? body?.mensaje ?? "Error al iniciar consulta");
    }
}

async function fetchEstadoDeudas(): Promise<{ enEjecucion: boolean; total: number; procesados: number; exitosos: number; errores: number; estado: string; mensaje?: string }> {
    const res = await fetchAPI(`${API_BASE_URL}/api/dux/deudas-clientes/estado`);
    return res.json();
}

async function cancelarConsultaDeudas(): Promise<void> {
    await fetchAPI(`${API_BASE_URL}/api/dux/deudas-clientes/cancelar`, { method: "POST" });
}

async function fetchResultadoDeudas(): Promise<DeudasResponse | null> {
    const res = await fetchAPI(`${API_BASE_URL}/api/dux/deudas-clientes/resultado`);
    const data = await res.json();
    if (data.disponible === false) return null;
    return data;
}

// ── Helpers ──────────────────────────────────────────────────────────────────

const TIPO_COMP_LABELS: Record<string, string> = {
    FACTURA: "Factura",
    FACTURA_FCE_MIPYMES: "Factura FCE MiPymes",
    COMPROBANTE_VENTA: "Comp. Venta",
    NOTA_CREDITO: "Nota Credito",
    NOTA_CREDITO_FCE_MI_PYMES: "NC FCE MiPymes",
    NOTA_DEBITO: "Nota Debito",
    NOTA_DEBITO_FCE_MI_PYMES: "ND FCE MiPymes",
};

function parseDuxDate(raw: string): Date | null {
    if (!raw) return null;
    const d = new Date(raw);
    return isNaN(d.getTime()) ? null : d;
}

function formatDate(raw: string): string {
    const d = parseDuxDate(raw);
    if (!d) return raw;
    return d.toLocaleDateString("es-AR", { day: "2-digit", month: "2-digit", year: "numeric" });
}

function formatCurrency(n: number): string {
    return n.toLocaleString("es-AR", { style: "currency", currency: "ARS", minimumFractionDigits: 2 });
}

function InfoRow({ label, value }: { label: string; value?: string | null }) {
    if (!value) return null;
    return (
        <div className="flex flex-col rounded-2xl border border-slate-200 bg-white px-3 py-2 shadow-sm dark:border-slate-700 dark:bg-slate-800 dark:shadow-none">
            <span className="text-[10px] font-semibold uppercase tracking-wide text-slate-400 dark:text-slate-500">{label}</span>
            <span className="text-sm text-slate-800 dark:text-slate-100">{value}</span>
        </div>
    );
}

const shellCardClassName = "rounded-3xl border border-slate-200 bg-white shadow-[0_10px_30px_-18px_rgba(15,23,42,0.25)] dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-black/30";

// ── Excel agrupado por cliente ───────────────────────────────────────────────

type ExportCol = { header: string; accessor: string };

async function exportarDeudasAgrupado(
    rows: Record<string, any>[],
    columns: ExportCol[],
    filename: string
) {
    const ExcelJS = await import("exceljs");
    const { saveAs } = await import("file-saver");

    const wb = new ExcelJS.Workbook();
    const ws = wb.addWorksheet("Deudas");

    const mediumBorder = { style: "medium" as const, color: { argb: "FF000000" } };
    const thinBorder = { style: "thin" as const, color: { argb: "FF000000" } };

    // Calcular ancho auto-size basado en el contenido
    const colWidths = columns.map((c) => {
        let max = c.header.length;
        for (const row of rows) {
            const val = row[c.accessor];
            if (val == null) continue;
            const len = String(val).length;
            if (len > max) max = len;
        }
        return Math.min(Math.max(max + 2, 6), 60);
    });

    ws.columns = columns.map((c, i) => ({
        header: c.header.toUpperCase(),
        key: c.accessor,
        width: colWidths[i],
    }));

    // Header - gris, Arial 10 bold, centrado
    const headerRow = ws.getRow(1);
    headerRow.font = { bold: true, size: 10, name: "Arial" };
    headerRow.fill = { type: "pattern", pattern: "solid", fgColor: { argb: "FFC0C0C0" } };
    headerRow.alignment = { horizontal: "center", vertical: "middle" };
    headerRow.height = 20;
    headerRow.eachCell((cell) => {
        cell.border = { bottom: mediumBorder, top: mediumBorder, left: mediumBorder, right: mediumBorder };
    });

    // Colores
    const fillSuma = { type: "pattern" as const, pattern: "solid" as const, fgColor: { argb: "FFFCE4EC" } }; // rojo claro (debe)
    const fillResta = { type: "pattern" as const, pattern: "solid" as const, fgColor: { argb: "FFE8F5E9" } }; // verde claro (NC)
    const fillNcParcial = { type: "pattern" as const, pattern: "solid" as const, fgColor: { argb: "FFFFF9C4" } }; // amarillo claro (NC parcial)
    const fillTotal = { type: "pattern" as const, pattern: "solid" as const, fgColor: { argb: "FFFFF3E0" } }; // naranja claro
    const fillComp = { type: "pattern" as const, pattern: "solid" as const, fgColor: { argb: "FFC6EFCE" } }; // verde comp

    // Datos agrupados
    const groupRanges: { start: number; end: number }[] = [];
    let groupStart = 2;

    for (let i = 0; i < rows.length; i++) {
        const row = rows[i];
        const isTotal = row._tipo === "total";
        const isResta = row._resta;
        const isNcParcial = row._ncParcial;

        // Separador entre clientes (antes del total del grupo anterior ya está, el blank va después del total)
        if (isTotal) {
            // Fila de total
            const totalValues: Record<string, any> = {};
            columns.forEach((c) => {
                if (c.accessor === "cliente") totalValues[c.accessor] = `TOTAL ${row._label}`;
                else if (c.accessor === "total") totalValues[c.accessor] = row.total;
                else if (c.accessor === "cobrado") totalValues[c.accessor] = row.cobrado;
                else if (c.accessor === "saldo") totalValues[c.accessor] = row.saldo;
                else totalValues[c.accessor] = "";
            });
            const excelRow = ws.addRow(totalValues);
            excelRow.font = { size: 10, name: "Arial", bold: true };
            excelRow.height = 20;
            excelRow.eachCell((cell) => { cell.fill = fillTotal; });

            // Formato total
            columns.forEach((c, ci) => {
                const cell = excelRow.getCell(ci + 1);
                if (c.accessor === "total" || c.accessor === "cobrado" || c.accessor === "saldo") {
                    cell.numFmt = '_-* "$" * #,##0_-;-* "$" * #,##0_-;_-* "-"_-;_-@_-';
                    cell.alignment = { horizontal: "center" };
                }
                if (c.accessor === "saldo") {
                    const saldoVal = Number(row.saldo ?? 0);
                    cell.fill = saldoVal > 0
                        ? { type: "pattern", pattern: "solid", fgColor: { argb: "FFFF6666" } }   // rojo — debe
                        : { type: "pattern", pattern: "solid", fgColor: { argb: "FF66BB6A" } };   // verde — a favor
                    cell.font = { size: 10, name: "Arial", bold: true, color: { argb: "FFFFFFFF" } };
                }
            });

            groupRanges.push({ start: groupStart, end: ws.rowCount });

            // Fila en blanco después del total
            if (i < rows.length - 1) {
                const blank = ws.addRow({});
                blank.height = 16;
                groupStart = ws.rowCount + 1;
            }
            continue;
        }

        const values: Record<string, any> = {};
        columns.forEach((c) => { values[c.accessor] = row[c.accessor] ?? ""; });
        const excelRow = ws.addRow(values);
        excelRow.font = { size: 10, name: "Arial" };
        excelRow.height = 18;

        // Color de fondo según tipo
        const rowFill = isNcParcial ? fillNcParcial : isResta ? fillResta : fillSuma;

        // Formatear celdas individualmente
        columns.forEach((c, ci) => {
            const cell = excelRow.getCell(ci + 1);
            if (c.accessor === "comprobante") {
                cell.fill = isNcParcial ? fillNcParcial : fillComp;
                cell.alignment = { horizontal: "center" };
                if (isNcParcial) {
                    cell.value = row[c.accessor] + " ⚠";
                    cell.note = "NC parcial: referencia facturas fuera del periodo consultado";
                }
            } else {
                cell.fill = rowFill;
            }

            if (c.accessor === "dias") {
                cell.alignment = { horizontal: "center" };
                const diasVal = Number(row[c.accessor]);
                cell.font = { size: 10, name: "Arial", bold: true };
                if (diasVal >= 30) {
                    cell.fill = { type: "pattern", pattern: "solid", fgColor: { argb: "FFFFFF00" } };
                    cell.font = { size: 10, name: "Arial", bold: true, color: { argb: "FF000000" } };
                }
            } else if (c.accessor === "fecha") {
                cell.alignment = { horizontal: "center" };
            } else if (c.accessor === "total" || c.accessor === "cobrado" || c.accessor === "saldo") {
                cell.numFmt = '_-* "$" * #,##0_-;-* "$" * #,##0_-;_-* "-"_-;_-@_-';
                cell.alignment = { horizontal: "center" };
            } else if (c.accessor === "link" && row[c.accessor]) {
                cell.value = { text: "Ver", hyperlink: String(row[c.accessor]) };
                cell.font = { size: 10, name: "Arial", color: { argb: "FF2563EB" }, underline: true };
                cell.alignment = { horizontal: "center" };
            }
        });
    }

    // Aplicar bordes por grupo (medium en exterior, thin en interior)
    for (const { start, end } of groupRanges) {
        for (let r = start; r <= end; r++) {
            const row = ws.getRow(r);
            const colCount = columns.length;
            for (let c = 1; c <= colCount; c++) {
                const cell = row.getCell(c);
                cell.border = {
                    top: r === start ? mediumBorder : thinBorder,
                    bottom: r === end ? mediumBorder : thinBorder,
                    left: c === 1 ? mediumBorder : thinBorder,
                    right: c === colCount ? mediumBorder : thinBorder,
                };
            }
        }
    }

    // Auto-size columnas basado en contenido
    columns.forEach((c, ci) => {
        const col = ws.getColumn(ci + 1);
        let maxLen = c.header.length;
        rows.forEach((row) => {
            const val = String(row[c.accessor] ?? "");
            if (val.length > maxLen) maxLen = val.length;
        });
        col.width = Math.min(maxLen + 3, 50);
    });

    ws.autoFilter = { from: { row: 1, column: 1 }, to: { row: 1, column: columns.length } };
    ws.views = [{ state: "frozen", ySplit: 1 }];

    const buffer = await wb.xlsx.writeBuffer();
    saveAs(new Blob([buffer]), `${filename}.xlsx`);
}

// ── Page ─────────────────────────────────────────────────────────────────────

export default function DuxDeudasPage() {
    const { refresh: refreshProcesosActivos } = useProcesoActivo();
    const [empresas, setEmpresas] = useState<any[]>([]);
    const [sucursales, setSucursales] = useState<any[]>([]);
    const [isLoadingEmpresas, setIsLoadingEmpresas] = useState(false);
    const [isLoadingSucursales, setIsLoadingSucursales] = useState(false);

    const [selectedEmpresa, setSelectedEmpresa] = useState<number | "">("");
    const [selectedSucursal, setSelectedSucursal] = useState<number | "" | "todas">("");

    const hoy = new Date();
    const hace3Meses = new Date(hoy.getFullYear(), hoy.getMonth() - 3, hoy.getDate());
    const hace3MesesStr = `${hace3Meses.getFullYear()}-${String(hace3Meses.getMonth() + 1).padStart(2, "0")}-${String(hace3Meses.getDate()).padStart(2, "0")}`;
    const hoyStr = hoy.toISOString().slice(0, 10);
    const [fechaDesde, setFechaDesde] = useState(hace3MesesStr);
    const [fechaHasta, setFechaHasta] = useState(hoyStr);
    const [conCobro, setConCobro] = useState<"todos" | "pendientes" | "cobrados">("pendientes");
    const [filtroCliente, setFiltroCliente] = useState("");
    const [filtroAnuladas, setFiltroAnuladas] = useState<"no" | "si" | "todas">("no");
    const [filtroVendedor, setFiltroVendedor] = useState("");
    const [filtroClienteTabla, setFiltroClienteTabla] = useState("");

    type PanelEstado = "IDLE" | "EN_PROCESO" | "COMPLETADO" | "ERROR";
    const [panelEstado, setPanelEstado] = useState<PanelEstado>("IDLE");
    const [procesoMsg, setProcesoMsg] = useState<string | null>(null);
    const [procesoInfo, setProcesoInfo] = useState<{ procesados: number; total: number } | null>(null);

    const [resultado, setResultado] = useState<DeudasResponse | null>(null);
    const [fechasBusqueda, setFechasBusqueda] = useState<{ desde: string; hasta: string } | null>(null);
    const [error, setError] = useState<string | null>(null);
    const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

    const [expandedId, setExpandedId] = useState<number | null>(null);
    const [jsonVisibleId, setJsonVisibleId] = useState<number | null>(null);
    const ALL_TIPOS = ["FACTURA", "COMPROBANTE_VENTA", "FACTURA_FCE_MIPYMES", "NOTA_CREDITO", "NOTA_CREDITO_FCE_MI_PYMES", "NOTA_DEBITO", "NOTA_DEBITO_FCE_MI_PYMES"] as const;
    const [filtroTipos, setFiltroTipos] = useState<Set<string>>(new Set(ALL_TIPOS));
    const [sortKey, setSortKey] = useState<string | null>(null);
    const [sortDesc, setSortDesc] = useState(false);

    const handleSort = (key: string) => {
        if (sortKey === key) {
            if (!sortDesc) { setSortDesc(true); }
            else { setSortKey(null); setSortDesc(false); }
        } else {
            setSortKey(key);
            setSortDesc(false);
        }
    };

    const toggleFiltroTipo = (tipo: string) => {
        setFiltroTipos((prev) => {
            const next = new Set(prev);
            if (next.has(tipo)) next.delete(tipo); else next.add(tipo);
            return next;
        });
    };

    const filteredComprobantes = resultado
        ? resultado.comprobantes.filter((c) => {
            if (!filtroTipos.has(c.tipoComp)) return false;
            if (filtroClienteTabla && c.cliente !== filtroClienteTabla) return false;
            if (filtroVendedor && c.vendedor !== filtroVendedor) return false;
            return true;
        })
        : [];

    const sortedComprobantes = [...filteredComprobantes].sort((a, b) => {
        if (!sortKey) return 0;
        let va: any, vb: any;
        switch (sortKey) {
            case "dias":
            case "fecha": va = parseDuxDate(a.fecha)?.getTime() ?? 0; vb = parseDuxDate(b.fecha)?.getTime() ?? 0; break;
            case "tipo": va = a.tipoComp; vb = b.tipoComp; break;
            case "comprobante": va = `${a.letraComp}${a.nroPtoVta}${a.nroComp}`; vb = `${b.letraComp}${b.nroPtoVta}${b.nroComp}`; break;
            case "cliente": va = a.cliente ?? ""; vb = b.cliente ?? ""; break;
            case "cuit": va = a.cuit ?? ""; vb = b.cuit ?? ""; break;
            case "total": va = a.total; vb = b.total; break;
            case "cobrado": va = a.cobrado ?? 0; vb = b.cobrado ?? 0; break;
            case "saldo": va = a.saldo ?? 0; vb = b.saldo ?? 0; break;
            case "vendedor": va = a.vendedor ?? ""; vb = b.vendedor ?? ""; break;
            case "motivo": {
                const esNC = (c: DeudaComprobante) => c.tipoComp === "NOTA_CREDITO" || c.tipoComp === "NOTA_CREDITO_FCE_MI_PYMES";
                va = esNC(a) ? (a.detalles?.some((d) => d.codItem === "9999998") ? 1 : 2) : 0;
                vb = esNC(b) ? (b.detalles?.some((d) => d.codItem === "9999998") ? 1 : 2) : 0;
                break;
            }
            default: return 0;
        }
        if (va < vb) return sortDesc ? 1 : -1;
        if (va > vb) return sortDesc ? -1 : 1;
        return 0;
    });

    const limpiarInterval = () => {
        if (intervalRef.current !== null) {
            clearInterval(intervalRef.current);
            intervalRef.current = null;
        }
    };

    useEffect(() => {
        setIsLoadingEmpresas(true);
        fetchEmpresas()
            .then(setEmpresas)
            .catch(() => {})
            .finally(() => setIsLoadingEmpresas(false));
    }, []);

    useEffect(() => {
        const checkEstadoInicial = async () => {
            try {
                const data = await fetchEstadoDeudas();
                if (data.estado === "ejecutando" || data.enEjecucion) {
                    setPanelEstado("EN_PROCESO");
                    setProcesoMsg(data.mensaje ?? null);
                    setProcesoInfo({ procesados: data.procesados, total: data.total });
                    intervalRef.current = setInterval(consultarEstado, 3000);
                } else if (data.estado === "completado") {
                    setPanelEstado("COMPLETADO");
                    setProcesoMsg(data.mensaje ?? null);
                    await cargarResultado();
                }
            } catch { /* ignorar */ }
        };
        checkEstadoInicial();
        return () => limpiarInterval();
    }, []);

    useEffect(() => {
        setSucursales([]);
        setSelectedSucursal("");
        if (!selectedEmpresa) return;
        setIsLoadingSucursales(true);
        fetchSucursales(Number(selectedEmpresa))
            .then(setSucursales)
            .catch(() => {})
            .finally(() => setIsLoadingSucursales(false));
    }, [selectedEmpresa]);

    const cargarResultado = async () => {
        try {
            const data = await fetchResultadoDeudas();
            if (data) setResultado(data);
        } catch { /* ignorar */ }
    };

    const pollingRef = useRef(false);
    const completadoRef = useRef(false);
    const consultarEstado = async () => {
        if (pollingRef.current || completadoRef.current) return;
        pollingRef.current = true;
        try {
            const data = await fetchEstadoDeudas();
            setProcesoMsg(data.mensaje ?? null);
            setProcesoInfo({ procesados: data.procesados, total: data.total });

            if (data.estado === "completado") {
                completadoRef.current = true;
                limpiarInterval();
                setPanelEstado("COMPLETADO");
                await cargarResultado();
                notificar.success("Deudas obtenidas correctamente.");
            } else if (data.estado === "cancelado") {
                limpiarInterval();
                setPanelEstado("IDLE");
                setProcesoMsg("Consulta cancelada.");
            } else if (data.estado === "error") {
                limpiarInterval();
                setPanelEstado("ERROR");
                setError(data.mensaje ?? "Error desconocido");
                notificar.error("Error al consultar deudas.");
            }
        } catch (err: any) {
            limpiarInterval();
            setPanelEstado("ERROR");
            setError("Error al consultar el estado: " + (err?.message ?? "desconocido"));
        } finally {
            pollingRef.current = false;
        }
    };

    const esNC = (c: DeudaComprobante) => c.tipoComp === "NOTA_CREDITO" || c.tipoComp === "NOTA_CREDITO_FCE_MI_PYMES";
    const exportarDeudas = (modo: "cliente" | "vendedor" | "todos", valor: string) => {
        if (!resultado) return;

        // Filtrar comprobantes según el modo
        let comprobantes = modo === "cliente"
            ? resultado.comprobantes.filter((c) => c.cliente === valor)
            : modo === "vendedor"
            ? resultado.comprobantes.filter((c) => c.vendedor === valor)
            : [...resultado.comprobantes];

        // Armar set de facturas con saldo pendiente para verificar NC
        const facturasPendientes = new Set<string>();
        for (const c of comprobantes) {
            if (!esNC(c)) {
                facturasPendientes.add(`${c.letraComp}-${c.nroPtoVta}-${c.nroComp}`);
            }
        }

        // Filtrar NC:
        // - Si tiene facturasReferenciadas: mostrar solo si la factura referenciada está pendiente
        // - Si no tiene facturasReferenciadas (NC por descuento): mostrar siempre
        comprobantes = comprobantes.filter((c) => {
            if (!esNC(c)) return true;
            if (!c.facturasReferenciadas || c.facturasReferenciadas.length === 0) return true;
            // Mostrar si al menos una factura referenciada está pendiente
            return c.facturasReferenciadas.some((ref) =>
                facturasPendientes.has(`${ref.letraComprobante}-${ref.numeroPuntoDeVenta}-${ref.numeroComprobante}`)
            );
        });

        const resta = (c: DeudaComprobante) => esNC(c);

        const rows = comprobantes.map((c) => {
            const d = parseDuxDate(c.fecha);
            const dias = d ? Math.floor((Date.now() - d.getTime()) / 86400000) : 0;

            // Detectar NC parcial: referencia facturas pero no todas están en el período
            let ncParcial = false;
            if (esNC(c) && c.facturasReferenciadas?.length > 0) {
                const refsEnPeriodo = c.facturasReferenciadas.filter((ref) =>
                    facturasPendientes.has(`${ref.letraComprobante}-${ref.numeroPuntoDeVenta}-${ref.numeroComprobante}`)
                ).length;
                ncParcial = refsEnPeriodo < c.facturasReferenciadas.length;
            }

            const sign = resta(c) ? -1 : 1;
            return {
                vendedor: c.vendedor ?? "",
                cliente: c.cliente,
                comprobante: `${c.letraComp} ${c.nroPtoVta}-${String(c.nroComp).padStart(8, "0")}`,
                fecha: formatDate(c.fecha),
                dias,
                total: sign * c.total,
                cobrado: sign * (c.cobrado ?? 0),
                saldo: sign * (c.saldo ?? c.total),
                link: c.urlFactura ?? "",
                _resta: resta(c),
                _tipo: "row" as const,
                _ncParcial: ncParcial,
            };
        });

        // Ordenar por vendedor asc, luego cliente asc, luego fecha desc (dias desc = más viejo primero)
        rows.sort((a, b) => {
            const venCmp = a.vendedor.localeCompare(b.vendedor);
            if (venCmp !== 0) return venCmp;
            const cliCmp = a.cliente.localeCompare(b.cliente);
            if (cliCmp !== 0) return cliCmp;
            return b.dias - a.dias;
        });

        // Insertar fila de total por cliente
        const rowsConTotal: Record<string, any>[] = [];
        let currentCliente = "";
        let totalCliente = 0, cobradoCliente = 0, saldoCliente = 0;
        for (const row of rows) {
            if (currentCliente && row.cliente !== currentCliente) {
                rowsConTotal.push({ vendedor: "", cliente: "", comprobante: "", fecha: "", dias: "", total: totalCliente, cobrado: cobradoCliente, saldo: saldoCliente, link: "", _resta: false, _tipo: "total", _label: currentCliente });
                totalCliente = 0; cobradoCliente = 0; saldoCliente = 0;
            }
            currentCliente = row.cliente;
            totalCliente += row.total;
            cobradoCliente += row.cobrado;
            saldoCliente += row.saldo;
            rowsConTotal.push(row);
        }
        if (currentCliente) {
            rowsConTotal.push({ vendedor: "", cliente: "", comprobante: "", fecha: "", dias: "", total: totalCliente, cobrado: cobradoCliente, saldo: saldoCliente, link: "", _resta: false, _tipo: "total", _label: currentCliente });
        }

        const columnas = [
            { header: "Vendedor", accessor: "vendedor" },
            { header: "Cliente", accessor: "cliente" },
            { header: "Comp.", accessor: "comprobante" },
            { header: "Fecha", accessor: "fecha" },
            { header: "Dias", accessor: "dias" },
            { header: "Total", accessor: "total" },
            { header: "Cobrado", accessor: "cobrado" },
            { header: "Saldo", accessor: "saldo" },
            { header: "Link", accessor: "link" },
        ];

        const fechaHoy = new Date().toLocaleDateString("es-AR", { day: "2-digit", month: "2-digit", year: "numeric" }).replace(/\//g, "-");
        const filename = modo === "todos"
            ? `deudas-todos-${fechaHoy}`
            : modo === "cliente"
            ? `deudas-${valor.replace(/\s+/g, "-").toLowerCase()}-${fechaHoy}`
            : `deudas-${valor.replace(/\s+/g, "-").toLowerCase()}-${fechaHoy}`;

        exportarDeudasAgrupado(rowsConTotal, columnas, filename);
    };

    const canSearch = selectedEmpresa && selectedSucursal && fechaDesde && fechaHasta;
    const enProceso = panelEstado === "EN_PROCESO";

    const handleBuscar = async () => {
        if (!canSearch) return;
        limpiarInterval();
        completadoRef.current = false;
        setPanelEstado("EN_PROCESO");
        setError(null);
        setResultado(null);
        setExpandedId(null);
        setSortKey(null);
        setSortDesc(false);
        setFiltroTipos(new Set(ALL_TIPOS));
        setProcesoMsg(null);
        setProcesoInfo(null);
        setFechasBusqueda({ desde: fechaDesde, hasta: fechaHasta });

        const conCobroValue = conCobro === "todos" ? undefined : conCobro === "cobrados";
        const idEmpresa = Number(selectedEmpresa);

        let idsSucursal: number[];
        if (selectedSucursal === "todas") {
            idsSucursal = sucursales.map((suc) => Number(suc.id ?? suc.id_sucursal ?? suc.idSucursal));
        } else {
            idsSucursal = [Number(selectedSucursal)];
        }

        const anuladasValue = filtroAnuladas === "todas" ? undefined : filtroAnuladas === "si";

        try {
            await iniciarConsultaDeudas({ fechaDesde, fechaHasta, idEmpresa, idsSucursal, conCobro: conCobroValue, cliente: filtroCliente || undefined, anuladas: anuladasValue });
            notificar.info("Consulta de deudas iniciada. Puede tardar varios minutos.");
            intervalRef.current = setInterval(consultarEstado, 3000);
        } catch (e: any) {
            setPanelEstado("ERROR");
            setError(e.message ?? "No se pudo iniciar la consulta");
            notificar.error("No se pudo iniciar la consulta: " + (e.message ?? "desconocido"));
        }
    };

    const handleCancelar = async () => {
        limpiarInterval();
        try {
            await cancelarConsultaDeudas();
        } catch { /* ignorar */ }
        setPanelEstado("IDLE");
        setProcesoMsg("Consulta cancelada.");
        // El backend libera el proceso cuando el thread async termina la llamada HTTP
        // en curso. Disparamos varios refresh para limpiar el badge del header rápido.
        refreshProcesosActivos();
        setTimeout(refreshProcesosActivos, 3000);
        setTimeout(refreshProcesosActivos, 8000);
    };

    const inputClassName = "rounded-xl border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 shadow-sm outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-500/20";
    const labelClassName = "text-xs font-semibold text-slate-600 dark:text-slate-300";

    const porcentaje = procesoInfo && procesoInfo.total > 0
        ? Math.min(Math.round((procesoInfo.procesados / procesoInfo.total) * 100), 100) : 0;

    return (
        <main className="p-4 bg-gray-50 dark:bg-slate-900 min-h-0 flex flex-col gap-6 overflow-auto">
            <div className="flex items-center gap-3">
                <ServerStackIcon className="w-8 h-8 text-gray-600 dark:text-slate-400" />
                <div>
                    <h1 className="text-3xl font-bold text-gray-800 dark:text-slate-100">Deudas de Clientes</h1>
                    <p className="text-sm text-gray-500 dark:text-slate-400">Consulta de comprobantes de venta pendientes y cobrados desde DUX ERP</p>
                </div>
            </div>

            <div className={`${shellCardClassName} p-6`}>
                {/* Filtros */}
                <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5">
                    <div className="flex flex-col gap-1">
                        <label className={labelClassName}><BuildingOfficeIcon className="w-3.5 h-3.5 inline mr-1" />Empresa</label>
                        <select
                            className={inputClassName}
                            value={selectedEmpresa}
                            onChange={(e) => setSelectedEmpresa(e.target.value ? Number(e.target.value) : "")}
                            disabled={isLoadingEmpresas || enProceso}
                        >
                            <option value="">{isLoadingEmpresas ? "Cargando..." : "Seleccionar"}</option>
                            {empresas.map((emp) => {
                                const id = emp.id ?? emp.id_empresa ?? emp.idEmpresa;
                                const nombre = emp.nombre ?? emp.descripcion ?? emp.razon_social ?? String(id);
                                return <option key={id} value={id}>{nombre}</option>;
                            })}
                        </select>
                    </div>

                    <div className="flex flex-col gap-1">
                        <label className={labelClassName}><MapPinIcon className="w-3.5 h-3.5 inline mr-1" />Sucursal</label>
                        <select
                            className={inputClassName}
                            value={selectedSucursal}
                            onChange={(e) => {
                                const v = e.target.value;
                                setSelectedSucursal(v === "todas" ? "todas" : v ? Number(v) : "");
                            }}
                            disabled={!selectedEmpresa || isLoadingSucursales || enProceso}
                        >
                            <option value="">{isLoadingSucursales ? "Cargando..." : "Seleccionar"}</option>
                            {sucursales.length > 1 && <option value="todas">Todas</option>}
                            {sucursales.map((suc) => {
                                const id = suc.id ?? suc.id_sucursal ?? suc.idSucursal;
                                const nombre = suc.sucursal ?? suc.nombre ?? suc.descripcion ?? String(id);
                                return <option key={id} value={id}>{nombre}</option>;
                            })}
                        </select>
                    </div>

                    <div className="flex flex-col gap-1">
                        <label className={labelClassName}><CalendarDaysIcon className="w-3.5 h-3.5 inline mr-1" />Desde</label>
                        <input type="date" className={inputClassName} value={fechaDesde} onChange={(e) => setFechaDesde(e.target.value)} disabled={enProceso} />
                    </div>

                    <div className="flex flex-col gap-1">
                        <label className={labelClassName}><CalendarDaysIcon className="w-3.5 h-3.5 inline mr-1" />Hasta</label>
                        <input type="date" className={inputClassName} value={fechaHasta} onChange={(e) => setFechaHasta(e.target.value)} disabled={enProceso} />
                    </div>

                    <div className="flex flex-col gap-1">
                        <label className={labelClassName}><BanknotesIcon className="w-3.5 h-3.5 inline mr-1" />Cobro</label>
                        <select
                            className={inputClassName}
                            value={conCobro}
                            onChange={(e) => setConCobro(e.target.value as "todos" | "pendientes" | "cobrados")}
                            disabled={enProceso}
                        >
                            <option value="todos">Todos</option>
                            <option value="pendientes">Pendientes</option>
                            <option value="cobrados">Cobrados</option>
                        </select>
                    </div>
                </div>

                <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
                    <div className="flex flex-col gap-1">
                        <label className={labelClassName}><UserIcon className="w-3.5 h-3.5 inline mr-1" />Cliente</label>
                        <input
                            type="text"
                            className={inputClassName}
                            value={filtroCliente}
                            onChange={(e) => setFiltroCliente(e.target.value)}
                            placeholder="Buscar por nombre..."
                            disabled={enProceso}
                        />
                    </div>
                    <div className="flex flex-col gap-1">
                        <label className={labelClassName}><NoSymbolIcon className="w-3.5 h-3.5 inline mr-1" />Anuladas</label>
                        <select
                            className={inputClassName}
                            value={filtroAnuladas}
                            onChange={(e) => setFiltroAnuladas(e.target.value as "no" | "si" | "todas")}
                            disabled={enProceso}
                        >
                            <option value="no">No</option>
                            <option value="si">Si</option>
                            <option value="todas">Todas</option>
                        </select>
                    </div>
                </div>

                <div className="mb-4 flex gap-2">
                    <Button variant="dark" onClick={handleBuscar} disabled={!canSearch || enProceso}>
                        {enProceso
                            ? <><ArrowPathIcon className="w-4 h-4 animate-spin" /> Consultando...</>
                            : <><MagnifyingGlassIcon className="w-4 h-4" /> Consultar deudas</>
                        }
                    </Button>
                    {enProceso && (
                        <Button variant="danger" onClick={handleCancelar}>
                            Cancelar
                        </Button>
                    )}
                    {!enProceso && resultado && (
                        <Button variant="light" onClick={cargarResultado}>
                            Ver ultimo resultado
                        </Button>
                    )}
                </div>

                {/* Progreso */}
                {enProceso && procesoInfo && procesoInfo.total > 0 && (
                    <div className="mb-3">
                        <div className="flex justify-between text-xs text-gray-500 dark:text-slate-400 mb-1">
                            <span>{procesoInfo.procesados} / {procesoInfo.total} sucursales</span>
                            <span>{porcentaje}%</span>
                        </div>
                        <div className="w-full bg-gray-200 dark:bg-slate-700 rounded-full h-3 overflow-hidden">
                            <div className="bg-blue-600 h-3 rounded-full transition-all duration-500" style={{ width: `${porcentaje}%` }} />
                        </div>
                    </div>
                )}
                {enProceso && procesoMsg && (
                    <p className="mb-3 text-xs text-gray-500 dark:text-slate-400">{procesoMsg}</p>
                )}

                {panelEstado === "COMPLETADO" && procesoMsg && !resultado && (
                    <div className="mb-3 rounded-2xl border border-green-200 bg-green-50 p-3 text-sm text-green-700 dark:border-green-500/30 dark:bg-green-500/10 dark:text-green-200">{procesoMsg}</div>
                )}

                {panelEstado === "ERROR" && error && (
                    <div className="rounded-2xl border border-red-200 bg-red-50 p-3 text-sm text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-200">{error}</div>
                )}

                {panelEstado === "IDLE" && procesoMsg && (
                    <div className="mb-3 rounded-2xl border border-yellow-200 bg-yellow-50 p-3 text-sm text-yellow-700 dark:border-yellow-500/30 dark:bg-yellow-500/10 dark:text-yellow-200">{procesoMsg}</div>
                )}

                {resultado && (
                    <div className="flex flex-col gap-4">
                        {/* Periodo */}
                        <p className="text-sm text-slate-600 dark:text-slate-400">
                            Resultados del <span className="font-semibold">{formatDate(fechasBusqueda?.desde ?? fechaDesde)}</span> al <span className="font-semibold">{formatDate(fechasBusqueda?.hasta ?? fechaHasta)}</span>
                        </p>
                        {/* Resumen */}
                        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                            <div className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 dark:border-slate-700 dark:bg-slate-900/70">
                                <div className="text-[11px] font-semibold uppercase tracking-[0.12em] text-slate-500 dark:text-slate-400">Total comprobantes</div>
                                <div className="mt-1 text-xl font-bold text-slate-800 dark:text-slate-100">{resultado.total}</div>
                            </div>
                            <div className="rounded-2xl border border-blue-200 bg-blue-50 px-4 py-3 dark:border-blue-500/30 dark:bg-blue-500/10">
                                <div className="text-[11px] font-semibold uppercase tracking-[0.12em] text-blue-700 dark:text-blue-200">Facturas</div>
                                <div className="mt-1 text-xl font-bold text-blue-800 dark:text-blue-100">{resultado.resumen.facturas}</div>
                            </div>
                            <div className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 dark:border-emerald-500/30 dark:bg-emerald-500/10">
                                <div className="text-[11px] font-semibold uppercase tracking-[0.12em] text-emerald-700 dark:text-emerald-200">Notas de Credito</div>
                                <div className="mt-1 text-xl font-bold text-emerald-800 dark:text-emerald-100">{resultado.resumen.notasCredito}</div>
                            </div>
                            <div className="rounded-2xl border border-orange-200 bg-orange-50 px-4 py-3 dark:border-orange-500/30 dark:bg-orange-500/10">
                                <div className="text-[11px] font-semibold uppercase tracking-[0.12em] text-orange-700 dark:text-orange-200">Notas de Debito</div>
                                <div className="mt-1 text-xl font-bold text-orange-800 dark:text-orange-100">{resultado.resumen.notasDebito}</div>
                            </div>
                        </div>

                        {/* Filtro por tipo */}
                        <div className="flex flex-wrap items-center gap-2 text-sm">
                            <span className="text-xs font-semibold text-slate-500 dark:text-slate-400">Filtrar:</span>
                            {([
                                ["FACTURA", "Factura", "bg-blue-100 text-blue-700 border-blue-300 dark:bg-blue-500/20 dark:text-blue-200 dark:border-blue-500/40"],
                                ["COMPROBANTE_VENTA", "Comp. Venta", "bg-blue-100 text-blue-700 border-blue-300 dark:bg-blue-500/20 dark:text-blue-200 dark:border-blue-500/40"],
                                ["FACTURA_FCE_MIPYMES", "FCE MiPymes", "bg-blue-100 text-blue-700 border-blue-300 dark:bg-blue-500/20 dark:text-blue-200 dark:border-blue-500/40"],
                                ["NOTA_CREDITO", "Nota Credito", "bg-emerald-100 text-emerald-700 border-emerald-300 dark:bg-emerald-500/20 dark:text-emerald-200 dark:border-emerald-500/40"],
                                ["NOTA_CREDITO_FCE_MI_PYMES", "Nota Credito FCE", "bg-emerald-100 text-emerald-700 border-emerald-300 dark:bg-emerald-500/20 dark:text-emerald-200 dark:border-emerald-500/40"],
                                ["NOTA_DEBITO", "Nota Debito", "bg-orange-100 text-orange-700 border-orange-300 dark:bg-orange-500/20 dark:text-orange-200 dark:border-orange-500/40"],
                                ["NOTA_DEBITO_FCE_MI_PYMES", "Nota Debito FCE", "bg-orange-100 text-orange-700 border-orange-300 dark:bg-orange-500/20 dark:text-orange-200 dark:border-orange-500/40"],
                            ] as [string, string, string][]).map(([key, label, colors]) => {
                                const count = resultado ? resultado.comprobantes.filter((c) => c.tipoComp === key).length : 0;
                                if (count === 0 && resultado) return null;
                                return (
                                    <button
                                        key={key}
                                        onClick={() => toggleFiltroTipo(key)}
                                        className={`rounded-full border px-3 py-1 text-xs font-medium transition ${
                                            filtroTipos.has(key)
                                                ? colors
                                                : "border-slate-200 bg-slate-50 text-slate-400 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-500"
                                        }`}
                                    >
                                        {label}
                                        <span className="ml-1.5 font-bold">{count}</span>
                                    </button>
                                );
                            })}
                            <span className="text-xs text-slate-400 dark:text-slate-500">
                                {filteredComprobantes.length} de {resultado.comprobantes.length}
                            </span>
                        </div>

                        {/* Filtros de tabla + Exportar */}
                        <div className="flex flex-wrap items-center gap-3">
                            <div className="relative">
                                <select
                                    className="rounded-lg border border-slate-300 bg-white pl-3 pr-14 py-1.5 text-sm text-slate-700 shadow-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100 w-80"
                                    value={filtroClienteTabla}
                                    onChange={(e) => setFiltroClienteTabla(e.target.value)}
                                >
                                    <option value="">Todos los clientes</option>
                                    {[...new Set(resultado.comprobantes.map((c) => c.cliente).filter(Boolean))].sort().map((cli) => (
                                        <option key={cli} value={cli}>{cli}</option>
                                    ))}
                                </select>
                                {filtroClienteTabla && (
                                    <button onClick={() => setFiltroClienteTabla("")} className="absolute right-7 top-1/2 -translate-y-1/2 p-0.5 rounded-full text-slate-400 hover:text-slate-600 hover:bg-slate-100 dark:hover:text-slate-200 dark:hover:bg-slate-700">
                                        <XMarkIcon className="w-4 h-4" />
                                    </button>
                                )}
                            </div>
                            <div className="relative">
                                <select
                                    className="rounded-lg border border-slate-300 bg-white pl-3 pr-14 py-1.5 text-sm text-slate-700 shadow-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100 w-80"
                                    value={filtroVendedor}
                                    onChange={(e) => setFiltroVendedor(e.target.value)}
                                >
                                    <option value="">Todos los vendedores</option>
                                    {[...new Set(resultado.comprobantes.map((c) => c.vendedor).filter((v): v is string => !!v))].sort().map((v) => (
                                        <option key={v} value={v}>{v}</option>
                                    ))}
                                </select>
                                {filtroVendedor && (
                                    <button onClick={() => setFiltroVendedor("")} className="absolute right-7 top-1/2 -translate-y-1/2 p-0.5 rounded-full text-slate-400 hover:text-slate-600 hover:bg-slate-100 dark:hover:text-slate-200 dark:hover:bg-slate-700">
                                        <XMarkIcon className="w-4 h-4" />
                                    </button>
                                )}
                            </div>
                            {filtroClienteTabla && (
                                <Button variant="light" onClick={() => exportarDeudas("cliente", filtroClienteTabla)}>
                                    <ArrowDownTrayIcon className="w-4 h-4" /> Deudas de {filtroClienteTabla}
                                </Button>
                            )}
                            {filtroVendedor && (
                                <Button variant="light" onClick={() => exportarDeudas("vendedor", filtroVendedor)}>
                                    <ArrowDownTrayIcon className="w-4 h-4" /> Deudas vendedor {filtroVendedor}
                                </Button>
                            )}
                            {!filtroClienteTabla && !filtroVendedor && (
                                <Button variant="light" onClick={() => exportarDeudas("todos", "")}>
                                    <ArrowDownTrayIcon className="w-4 h-4" /> Exportar Todos
                                </Button>
                            )}
                        </div>

                        {/* Tabla de comprobantes */}
                        {resultado.comprobantes.length === 0 ? (
                            <div className="rounded-2xl border border-dashed border-slate-300 bg-slate-50 px-4 py-8 text-center text-sm text-slate-400 dark:border-slate-700 dark:bg-slate-900/70 dark:text-slate-500">
                                No se encontraron comprobantes para los filtros seleccionados.
                            </div>
                        ) : (
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm border-collapse">
                                    <thead>
                                        <tr className="bg-gray-100 text-left text-gray-600 dark:bg-slate-700 dark:text-slate-200">
                                            <th className="border border-gray-200 px-3 py-2 font-semibold dark:border-slate-700 w-8"></th>
                                            {([
                                                ["dias", "Dias", "text-center"],
                                                ["fecha", "Fecha", ""],
                                                ["tipo", "Tipo", ""],
                                                ["comprobante", "Comprobante", ""],
                                                ["cliente", "Cliente", ""],
                                                ["cuit", "CUIT", ""],
                                                ["total", "Total", "text-right"],
                                                ["cobrado", "Cobrado", "text-right"],
                                                ["saldo", "Saldo", "text-right"],
                                                ["vendedor", "Vendedor", ""],
                                                ["motivo", "Motivo", ""],
                                            ] as [string, string, string][]).map(([key, label, align]) => (
                                                <th
                                                    key={key}
                                                    className={`border border-gray-200 px-3 py-2 font-semibold dark:border-slate-700 cursor-pointer select-none hover:bg-gray-200 dark:hover:bg-slate-700 ${align}`}
                                                    onClick={() => handleSort(key)}
                                                >
                                                    {label}
                                                    {sortKey === key && (
                                                        <span className="ml-1 text-xs">{sortDesc ? "▼" : "▲"}</span>
                                                    )}
                                                </th>
                                            ))}
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {sortedComprobantes.map((comp) => {
                                            const isExpanded = expandedId === comp.id;
                                            const tipoLabel = TIPO_COMP_LABELS[comp.tipoComp] ?? comp.tipoComp;
                                            const tipoPrefix: Record<string, string> = {
                                                FACTURA: "F",
                                                FACTURA_FCE_MIPYMES: "F",
                                                COMPROBANTE_VENTA: "C",
                                                NOTA_CREDITO: "",
                                                NOTA_CREDITO_FCE_MI_PYMES: "",
                                                NOTA_DEBITO: "",
                                                NOTA_DEBITO_FCE_MI_PYMES: "",
                                            };
                                            const prefix = tipoPrefix[comp.tipoComp] ?? "";
                                            const compNumero = `${prefix}${comp.letraComp}-${String(comp.nroPtoVta).padStart(5, "0")}-${String(comp.nroComp).padStart(8, "0")}`;

                                            return (
                                                <Fragment key={comp.id}>
                                                    <tr
                                                        className="cursor-pointer odd:bg-white even:bg-gray-50 hover:bg-blue-50/50 dark:odd:bg-slate-900 dark:even:bg-slate-800 dark:hover:bg-slate-700/50"
                                                        onClick={() => setExpandedId(isExpanded ? null : comp.id)}
                                                    >
                                                        <td className="border border-gray-200 px-2 py-1.5 text-center text-xs text-slate-400 dark:border-slate-700">
                                                            {isExpanded ? "▲" : "▼"}
                                                        </td>
                                                        <td className="border border-gray-200 px-3 py-1.5 text-center text-xs font-semibold dark:border-slate-700">
                                                            {(() => {
                                                                const d = parseDuxDate(comp.fecha);
                                                                if (!d) return "—";
                                                                const dias = Math.floor((Date.now() - d.getTime()) / 86400000);
                                                                return <span className={dias > 60 ? "text-red-600" : dias > 30 ? "text-amber-600" : "text-slate-600 dark:text-slate-300"}>{dias}</span>;
                                                            })()}
                                                        </td>
                                                        <td className="border border-gray-200 px-3 py-1.5 text-slate-700 dark:border-slate-700 dark:text-slate-200 whitespace-nowrap">
                                                            {formatDate(comp.fecha)}
                                                        </td>
                                                        <td className="border border-gray-200 px-3 py-1.5 dark:border-slate-700 text-center">
                                                            <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${
                                                                comp.tipoComp.includes("CREDITO")
                                                                    ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-200"
                                                                    : comp.tipoComp.includes("DEBITO")
                                                                        ? "bg-orange-100 text-orange-700 dark:bg-orange-500/20 dark:text-orange-200"
                                                                        : "bg-blue-100 text-blue-700 dark:bg-blue-500/20 dark:text-blue-200"
                                                            }`}>
                                                                {tipoLabel}
                                                            </span>
                                                        </td>
                                                        <td className="border border-gray-200 px-3 py-1.5 font-mono text-xs text-slate-700 dark:border-slate-700 dark:text-slate-200 whitespace-nowrap">
                                                            {compNumero}
                                                        </td>
                                                        <td className="border border-gray-200 px-3 py-1.5 text-slate-700 dark:border-slate-700 dark:text-slate-200">
                                                            {comp.cliente}
                                                        </td>
                                                        <td className="border border-gray-200 px-3 py-1.5 font-mono text-xs text-slate-500 dark:border-slate-700 dark:text-slate-300">
                                                            {comp.cuit}
                                                        </td>
                                                        <td className="border border-gray-200 px-3 py-1.5 text-right font-semibold text-slate-800 dark:border-slate-700 dark:text-slate-100 whitespace-nowrap">
                                                            {formatCurrency(comp.total)}
                                                        </td>
                                                        <td className="border border-gray-200 px-3 py-1.5 text-right text-slate-600 dark:border-slate-700 dark:text-slate-300 whitespace-nowrap">
                                                            {(comp.cobrado ?? 0) > 0 ? formatCurrency(comp.cobrado) : "—"}
                                                        </td>
                                                        <td className="border border-gray-200 px-3 py-1.5 text-right font-semibold whitespace-nowrap">
                                                            <span className={(comp.saldo ?? 0) > 0 ? "text-red-600 dark:text-red-400" : "text-emerald-600 dark:text-emerald-400"}>
                                                                {formatCurrency(comp.saldo ?? 0)}
                                                            </span>
                                                        </td>
                                                        <td className="border border-gray-200 px-3 py-1.5 text-xs text-slate-600 dark:border-slate-700 dark:text-slate-300 whitespace-nowrap">
                                                            {comp.vendedor || "—"}
                                                        </td>
                                                        <td className="border border-gray-200 px-3 py-1.5 dark:border-slate-700 text-xs">
                                                            {(comp.tipoComp === "NOTA_CREDITO" || comp.tipoComp === "NOTA_CREDITO_FCE_MI_PYMES") && (
                                                                <span className={comp.detalles?.some((d) => d.codItem === "9999998") ? "text-amber-600 font-medium" : "text-blue-600 font-medium"}>
                                                                    {comp.detalles?.some((d) => d.codItem === "9999998") ? "POR DESCUENTO" : "POR MERCADERIA"}
                                                                </span>
                                                            )}
                                                            {comp.facturasReferenciadas?.length > 0 && (
                                                                <span className="text-slate-400 font-normal text-xs ml-1">
                                                                    (Ref: {comp.facturasReferenciadas.map((r) =>
                                                                        `${r.letraComprobante}-${String(r.numeroPuntoDeVenta).padStart(5, "0")}-${String(r.numeroComprobante).padStart(8, "0")}`
                                                                    ).join(", ")})
                                                                </span>
                                                            )}
                                                        </td>
                                                    </tr>

                                                    {isExpanded && (
                                                        <tr>
                                                            <td colSpan={16} className="border border-gray-200 bg-slate-50/80 p-4 dark:border-slate-700 dark:bg-slate-900/70">
                                                                <div className="flex flex-col gap-4 lg:flex-row lg:gap-6">
                                                                    <div className="flex flex-wrap gap-3 text-xs">
                                                                        <InfoRow label="ID" value={String(comp.id)} />
                                                                        <InfoRow label="ID Cliente" value={String(comp.idCliente)} />
                                                                        <InfoRow label="Gravado" value={formatCurrency(comp.montoGravado)} />
                                                                        <InfoRow label="IVA" value={formatCurrency(comp.montoIva)} />
                                                                        <InfoRow label="Exento" value={formatCurrency(comp.montoExento)} />
                                                                        <InfoRow label="Descuento" value={formatCurrency(comp.montoDesc)} />
                                                                        <InfoRow label="Anulada" value={comp.anulada ? "Sí" : "No"} />
                                                                        {comp.urlFactura && (
                                                                            <a
                                                                                href={comp.urlFactura}
                                                                                target="_blank"
                                                                                rel="noopener noreferrer"
                                                                                className="inline-flex items-center gap-1 rounded-full border border-blue-200 bg-blue-50 px-3 py-1 text-xs font-medium text-blue-700 hover:bg-blue-100 dark:border-blue-500/30 dark:bg-blue-500/15 dark:text-blue-200 dark:hover:bg-blue-500/25"
                                                                            >
                                                                                Ver factura en DUX
                                                                            </a>
                                                                        )}
                                                                        <button
                                                                            onClick={() => setJsonVisibleId(jsonVisibleId === comp.id ? null : comp.id)}
                                                                            className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-medium text-slate-600 hover:bg-slate-100 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
                                                                        >
                                                                            {jsonVisibleId === comp.id ? "Ocultar JSON" : "Ver JSON"}
                                                                        </button>
                                                                    </div>
                                                                </div>

                                                                {comp.detalles && comp.detalles.length > 0 && (
                                                                    <div className="mt-3">
                                                                        <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">Detalle de items</p>
                                                                        <table className="w-full text-xs border-collapse">
                                                                            <thead>
                                                                                <tr className="bg-gray-100 text-left text-gray-500 dark:bg-slate-800 dark:text-slate-400">
                                                                                    <th className="border border-gray-200 px-2 py-1 font-semibold dark:border-slate-700">Codigo</th>
                                                                                    <th className="border border-gray-200 px-2 py-1 font-semibold dark:border-slate-700">Item</th>
                                                                                    <th className="border border-gray-200 px-2 py-1 text-right font-semibold dark:border-slate-700">Cant.</th>
                                                                                    <th className="border border-gray-200 px-2 py-1 text-right font-semibold dark:border-slate-700">P. Unit.</th>
                                                                                    <th className="border border-gray-200 px-2 py-1 text-right font-semibold dark:border-slate-700">Desc %</th>
                                                                                    <th className="border border-gray-200 px-2 py-1 text-right font-semibold dark:border-slate-700">IVA %</th>
                                                                                </tr>
                                                                            </thead>
                                                                            <tbody>
                                                                                {comp.detalles.map((det, i) => (
                                                                                    <tr key={i} className="odd:bg-white even:bg-gray-50 dark:odd:bg-slate-900 dark:even:bg-slate-800">
                                                                                        <td className="border border-gray-200 px-2 py-1 font-mono dark:border-slate-700 dark:text-slate-200">{det.codItem}</td>
                                                                                        <td className="border border-gray-200 px-2 py-1 dark:border-slate-700 dark:text-slate-200">{det.item}</td>
                                                                                        <td className="border border-gray-200 px-2 py-1 text-right dark:border-slate-700 dark:text-slate-200">{det.cantidad}</td>
                                                                                        <td className="border border-gray-200 px-2 py-1 text-right dark:border-slate-700 dark:text-slate-200">{formatCurrency(det.precioUni)}</td>
                                                                                        <td className="border border-gray-200 px-2 py-1 text-right dark:border-slate-700 dark:text-slate-200">{det.porcDesc}%</td>
                                                                                        <td className="border border-gray-200 px-2 py-1 text-right dark:border-slate-700 dark:text-slate-200">{det.porcIva}%</td>
                                                                                    </tr>
                                                                                ))}
                                                                            </tbody>
                                                                        </table>
                                                                    </div>
                                                                )}

                                                                {comp.cobros && comp.cobros.length > 0 && (
                                                                    <div className="mt-3">
                                                                        <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">Cobros aplicados</p>
                                                                        <div className="flex flex-col gap-2">
                                                                            {comp.cobros.map((cobro, ci) => (
                                                                                <div key={ci} className="rounded-xl border border-slate-200 bg-white p-3 dark:border-slate-700 dark:bg-slate-800">
                                                                                    <div className="mb-1 flex flex-wrap gap-3 text-xs text-slate-600 dark:text-slate-300">
                                                                                        <span><strong>Recibo:</strong> {cobro.numeroPuntoDeVenta}-{String(cobro.numeroComprobante).padStart(8, "0")}</span>
                                                                                        <span><strong>Personal:</strong> {cobro.personal}</span>
                                                                                        <span><strong>Caja:</strong> {cobro.caja}</span>
                                                                                    </div>
                                                                                    {cobro.movimientos && cobro.movimientos.length > 0 && (
                                                                                        <table className="w-full text-xs border-collapse mt-1">
                                                                                            <thead>
                                                                                                <tr className="bg-gray-100 text-left text-gray-500 dark:bg-slate-900 dark:text-slate-400">
                                                                                                    <th className="border border-gray-200 px-2 py-1 font-semibold dark:border-slate-700">Tipo</th>
                                                                                                    <th className="border border-gray-200 px-2 py-1 font-semibold dark:border-slate-700">Referencia</th>
                                                                                                    <th className="border border-gray-200 px-2 py-1 text-right font-semibold dark:border-slate-700">Monto</th>
                                                                                                </tr>
                                                                                            </thead>
                                                                                            <tbody>
                                                                                                {cobro.movimientos.map((mov, mi) => (
                                                                                                    <tr key={mi} className="odd:bg-white even:bg-gray-50 dark:odd:bg-slate-900 dark:even:bg-slate-800">
                                                                                                        <td className="border border-gray-200 px-2 py-1 dark:border-slate-700 dark:text-slate-200">{mov.tipoDeValor}</td>
                                                                                                        <td className="border border-gray-200 px-2 py-1 dark:border-slate-700 dark:text-slate-200">{mov.referencia || "—"}</td>
                                                                                                        <td className="border border-gray-200 px-2 py-1 text-right font-semibold dark:border-slate-700 dark:text-slate-200">{formatCurrency(mov.monto)}</td>
                                                                                                    </tr>
                                                                                                ))}
                                                                                            </tbody>
                                                                                        </table>
                                                                                    )}
                                                                                </div>
                                                                            ))}
                                                                        </div>
                                                                    </div>
                                                                )}

                                                                {(!comp.cobros || comp.cobros.length === 0) && (
                                                                    <p className="mt-3 text-xs text-slate-400 dark:text-slate-500">Sin cobros aplicados.</p>
                                                                )}

                                                                {jsonVisibleId === comp.id && (
                                                                    <pre className="mt-3 max-h-96 overflow-auto rounded-lg border border-slate-200 bg-white p-3 text-[11px] text-slate-700 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-300">
                                                                        {JSON.stringify(comp, null, 2)}
                                                                    </pre>
                                                                )}
                                                            </td>
                                                        </tr>
                                                    )}
                                                </Fragment>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </div>
                )}
            </div>
        </main>
    );
}

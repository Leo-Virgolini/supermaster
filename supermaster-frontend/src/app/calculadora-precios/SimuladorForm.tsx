"use client";
import { getErrorMessage } from "@/lib/errors";
import { useState, type ReactNode } from "react";
import { toast } from "sonner";
import { notificar } from "../utils/notificar";
import { BoltIcon, XMarkIcon, MagnifyingGlassIcon } from "@heroicons/react/24/outline";
import AsyncSelect from "../components/AsyncSelect/AsyncSelect";
import Button from "../components/Button/Button";
import { searchMarcas, searchTipos, searchClasifGral, searchClasifGastro } from "../productos/productosService";
import {
    simularPrecioAPI,
    searchProductosForSimulacionAPI,
    loadProductoSnapshotAPI,
    type SimulacionPrecioInput,
    type SimulacionResultado,
    type IndicadoresCalculados,
} from "./simuladorService";
import type { FormulaCalculo } from "../producto-canal-precios/types";

type Tag = "MAQUINA" | "REPUESTO" | "MENAJE";

interface SimuladorFormProps {
    canalId: number;
    cuotas: number | null;   // controlado desde la página (al lado del selector de canal)
}

type TipoInflado = "" | "MULTIPLICADOR" | "DESCUENTO_PORC" | "DIVISOR" | "PRECIO_FIJO";

interface FormState {
    // Numéricos como string para no perder dígitos al tipear ("100." se preserva).
    costo: string;
    iva: string;
    marcaId: number | null;
    marcaLabel: string;
    tipoId: number | null;
    tipoLabel: string;
    clasifGralId: number | null;
    clasifGralLabel: string;
    clasifGastroId: number | null;
    clasifGastroLabel: string;
    tag: Tag | "";
    proveedorFin: string;
    mlaPrecioEnvio: string;
    mlaComision: string;
    margenMin: string;
    margenMay: string;
    margenFijoMin: string;
    margenFijoMay: string;
    precioInfladoTipo: TipoInflado;
    precioInfladoValor: string;
}

const emptyForm = (): FormState => ({
    costo: "1000",
    iva: "21",
    marcaId: null, marcaLabel: "",
    tipoId: null, tipoLabel: "",
    clasifGralId: null, clasifGralLabel: "",
    clasifGastroId: null, clasifGastroLabel: "",
    tag: "",
    proveedorFin: "",
    mlaPrecioEnvio: "",
    mlaComision: "",
    margenMin: "30",
    margenMay: "20",
    margenFijoMin: "",
    margenFijoMay: "",
    precioInfladoTipo: "",
    precioInfladoValor: "",
});

// Acepta "1,5" y "1.5" como decimal — convierte coma a punto antes de parsear.
function parseNum(s: string): number {
    return Number(s.replace(",", "."));
}
function parseNumOrNull(s: string): number | null {
    if (s === "" || s.trim() === "") return null;
    const n = parseNum(s);
    return isNaN(n) ? null : n;
}

function formatMoney(n: number | null | undefined): string {
    if (n == null) return "—";
    return n.toLocaleString("es-AR", { style: "currency", currency: "ARS", minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatPct(n: number | null | undefined): string {
    if (n == null) return "—";
    return `${n.toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`;
}

// Coloreo de márgenes/markup según rangos del Monitor de Precios.
function colorMargen(pct: number | null | undefined): string {
    if (pct == null) return "text-slate-500 dark:text-slate-400";
    if (pct < 0) return "text-red-600 dark:text-red-400";
    if (pct < 15) return "text-orange-500 dark:text-orange-400";
    if (pct < 25) return "text-yellow-600 dark:text-yellow-400";
    if (pct < 40) return "text-green-600 dark:text-green-400";
    return "text-emerald-600 dark:text-emerald-400";
}

function IndicadorRow({ label, valor, hint }: { label: string; valor: ReactNode; hint?: string }) {
    return (
        <div className="flex items-center justify-between gap-2 border-b border-slate-100 py-1.5 last:border-b-0 dark:border-slate-700/60">
            <div className="min-w-0">
                <p className="text-xs font-semibold text-slate-700 dark:text-slate-200">{label}</p>
                {hint && <p className="text-[10px] italic text-slate-400 dark:text-slate-500">{hint}</p>}
            </div>
            <span className="font-mono text-sm font-bold text-slate-800 dark:text-slate-100">{valor}</span>
        </div>
    );
}

function IndicadoresPanel({ indicadores }: { indicadores: IndicadoresCalculados }) {
    return (
        <div className="overflow-hidden rounded-md border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900">
            <div className="border-b border-slate-200 bg-slate-50 px-3 py-1.5 dark:border-slate-700 dark:bg-slate-800/40">
                <p className="text-[10px] font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                    Indicadores
                </p>
            </div>
            <div className="px-3">
                <IndicadorRow label="Costo del producto" valor={formatMoney(indicadores.costoProducto)} />
                <IndicadorRow label="PVP" valor={<span className="text-emerald-700 dark:text-emerald-300">{formatMoney(indicadores.pvp)}</span>} />
                {indicadores.pvpInflado != null && (
                    <IndicadorRow label="PVP Inflado" valor={formatMoney(indicadores.pvpInflado)} hint="precio tachado" />
                )}
                <IndicadorRow label="Costos de Venta" valor={formatMoney(indicadores.costosVenta)} hint="comisiones, envío, cuotas" />
                <IndicadorRow label="Ingreso Neto Vendedor" valor={formatMoney(indicadores.ingresoNetoVendedor)} hint="PVP − IVA − impuestos − costos venta" />
                <IndicadorRow
                    label="Ganancia"
                    valor={
                        <span className={indicadores.ganancia < 0 ? "text-red-600 dark:text-red-400" : "text-emerald-700 dark:text-emerald-300"}>
                            {formatMoney(indicadores.ganancia)}
                        </span>
                    }
                    hint="ingreso neto − costo producto"
                />
                <IndicadorRow
                    label="Margen s/PVP"
                    valor={<span className={colorMargen(indicadores.margenSobrePvp)}>{formatPct(indicadores.margenSobrePvp)}</span>}
                    hint="ganancia / PVP"
                />
                <IndicadorRow
                    label="Margen s/Ingreso Neto"
                    valor={<span className={colorMargen(indicadores.margenSobreIngresoNeto)}>{formatPct(indicadores.margenSobreIngresoNeto)}</span>}
                    hint="ganancia / ingreso neto"
                />
                <IndicadorRow
                    label="Markup"
                    valor={<span className={colorMargen(indicadores.markupPorcentaje)}>{formatPct(indicadores.markupPorcentaje)}</span>}
                    hint="ganancia / costo producto"
                />
            </div>
        </div>
    );
}

function PasoLine({ paso }: { paso: FormulaCalculo["pasos"][number] }) {
    const valorFmt =
        paso.unidad === "moneda"
            ? formatMoney(paso.valor)
            : paso.unidad === "porcentaje"
                ? `${paso.valor}%`
                : paso.unidad === "factor"
                    ? `×${paso.valor}`
                    : String(paso.valor);
    return (
        <div className="flex items-start gap-3 border-b border-slate-100 py-2 last:border-b-0 dark:border-slate-700/60">
            <span className="mt-0.5 inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-slate-100 text-[10px] font-bold text-slate-600 dark:bg-slate-700 dark:text-slate-300">
                {paso.numeroPaso}
            </span>
            <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                    <span className="text-sm font-semibold text-slate-800 dark:text-slate-100">{paso.descripcion}</span>
                    <span className="font-mono text-sm font-bold text-emerald-700 dark:text-emerald-300">{valorFmt}</span>
                </div>
                {paso.formula && (
                    <p className="mt-0.5 break-all font-mono text-[11px] text-slate-500 dark:text-slate-400">{paso.formula}</p>
                )}
                {paso.detalle && (
                    <p className="mt-0.5 text-[11px] italic text-slate-400 dark:text-slate-500">{paso.detalle}</p>
                )}
            </div>
        </div>
    );
}

export default function SimuladorForm({ canalId, cuotas }: SimuladorFormProps) {
    const [form, setForm] = useState<FormState>(emptyForm);
    const [resultado, setResultado] = useState<SimulacionResultado | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [productoBaseLabel, setProductoBaseLabel] = useState<string | null>(null);
    const [isLoadingProducto, setIsLoadingProducto] = useState(false);

    const update = <K extends keyof FormState>(key: K, value: FormState[K]) =>
        setForm((prev) => ({ ...prev, [key]: value }));

    const handleSelectProducto = async (id: number, label: string) => {
        try {
            setIsLoadingProducto(true);
            const snap = await loadProductoSnapshotAPI(id, canalId);
            setForm((prev) => ({
                ...prev,
                costo: snap.costo != null ? String(snap.costo) : "",
                iva: snap.iva != null ? String(snap.iva) : "",
                marcaId: snap.marcaId,
                marcaLabel: snap.marcaLabel,
                tipoId: snap.tipoId,
                tipoLabel: snap.tipoLabel,
                clasifGralId: snap.clasifGralId,
                clasifGralLabel: snap.clasifGralLabel,
                clasifGastroId: snap.clasifGastroId,
                clasifGastroLabel: snap.clasifGastroLabel,
                tag: snap.tag ?? "",
                proveedorFin: snap.proveedorFinanciacionPorcentaje != null ? String(snap.proveedorFinanciacionPorcentaje) : "",
                mlaPrecioEnvio: snap.mlaPrecioEnvio != null ? String(snap.mlaPrecioEnvio) : "",
                mlaComision: snap.mlaComisionPorcentaje != null ? String(snap.mlaComisionPorcentaje) : "",
                margenMin: snap.margenMinorista != null ? String(snap.margenMinorista) : prev.margenMin,
                margenMay: snap.margenMayorista != null ? String(snap.margenMayorista) : prev.margenMay,
                margenFijoMin: snap.margenFijoMinorista != null ? String(snap.margenFijoMinorista) : "",
                margenFijoMay: snap.margenFijoMayorista != null ? String(snap.margenFijoMayorista) : "",
                precioInfladoTipo: (snap.precioInfladoTipo as TipoInflado) ?? "",
                precioInfladoValor: snap.precioInfladoValor != null ? String(snap.precioInfladoValor) : "",
            }));
            setProductoBaseLabel(snap.label || label);
            setResultado(null);
            setError(null);
            const infladoMsg = snap.precioInfladoCodigo ? ` · regla inflado: ${snap.precioInfladoCodigo}` : "";
            toast.success(`Cargado: ${snap.sku}${infladoMsg}`);
        } catch (e: unknown) {
            toast.error(getErrorMessage(e, "Error al cargar producto"));
        } finally {
            setIsLoadingProducto(false);
        }
    };

    const handleClearProducto = () => {
        setProductoBaseLabel(null);
    };


    const handleSimular = async () => {
        const costoNum = parseNum(form.costo);
        if (!form.costo || isNaN(costoNum) || costoNum <= 0) {
            toast.warning("El costo debe ser mayor a 0");
            return;
        }
        const ivaNum = parseNum(form.iva);
        if (form.iva === "" || isNaN(ivaNum) || ivaNum < 0) {
            toast.warning("Ingresá un IVA válido (≥ 0)");
            return;
        }
        const margenMinNum = parseNum(form.margenMin);
        const margenMayNum = parseNum(form.margenMay);
        if (form.margenMin === "" || isNaN(margenMinNum) || margenMinNum < 0
            || form.margenMay === "" || isNaN(margenMayNum) || margenMayNum < 0) {
            toast.warning("Ingresá márgenes válidos (≥ 0)");
            return;
        }
        setIsLoading(true);
        setError(null);
        try {
            const input: SimulacionPrecioInput = {
                canalId,
                cuotas: cuotas,
                costo: costoNum,
                iva: ivaNum,
                marcaId: form.marcaId,
                tipoId: form.tipoId,
                clasifGralId: form.clasifGralId,
                clasifGastroId: form.clasifGastroId,
                esMaquina: null,
                tag: form.tag === "" ? null : form.tag,
                proveedorFinanciacionPorcentaje: parseNumOrNull(form.proveedorFin),
                mlaPrecioEnvio: parseNumOrNull(form.mlaPrecioEnvio),
                mlaComisionPorcentaje: parseNumOrNull(form.mlaComision),
                margenMinorista: margenMinNum,
                margenMayorista: margenMayNum,
                margenFijoMinorista: parseNumOrNull(form.margenFijoMin),
                margenFijoMayorista: parseNumOrNull(form.margenFijoMay),
                precioInfladoTipo: form.precioInfladoTipo === "" ? null : form.precioInfladoTipo,
                precioInfladoValor: parseNumOrNull(form.precioInfladoValor),
            };
            const res = await simularPrecioAPI(input);
            setResultado(res);
        } catch (e: unknown) {
            const msg = getErrorMessage(e, "Error al simular");
            setError(msg);
            notificar.error(msg);
            setResultado(null);
        } finally {
            setIsLoading(false);
        }
    };

    const handleReset = () => {
        setForm(emptyForm());
        setResultado(null);
        setError(null);
    };

    return (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            {/* Columna izquierda: form */}
            <div className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
                <h2 className="text-sm font-bold uppercase tracking-wide text-slate-500 dark:text-slate-400">Producto hipotético</h2>

                {/* Cargar atributos desde un producto existente */}
                <div className="rounded-md border border-blue-200 bg-blue-50 p-3 dark:border-blue-800/60 dark:bg-blue-900/20">
                    <div className="mb-1.5 flex items-center gap-2 text-xs font-bold text-blue-900 dark:text-blue-200">
                        <MagnifyingGlassIcon className="h-4 w-4" />
                        Cargar desde producto existente
                    </div>
                    <AsyncSelect
                        label=""
                        placeholder={isLoadingProducto ? "Cargando atributos..." : "Buscar por SKU, MLA o nombre..."}
                        loadOptions={searchProductosForSimulacionAPI}
                        onChange={(val, label) => {
                            if (val != null) handleSelectProducto(Number(val), label || "");
                        }}
                        displayValue={productoBaseLabel ?? undefined}
                    />
                    {productoBaseLabel && (
                        <div className="mt-2 flex items-center justify-between gap-2 text-[11px] text-blue-800 dark:text-blue-200">
                            <span>
                                Base: <strong>{productoBaseLabel}</strong>. Modificá los campos abajo para simular variaciones.
                            </span>
                            <button
                                type="button"
                                onClick={handleClearProducto}
                                className="shrink-0 rounded px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-blue-700 hover:bg-blue-100 dark:text-blue-300 dark:hover:bg-blue-900/40"
                            >
                                Quitar referencia
                            </button>
                        </div>
                    )}
                </div>

                <div className="grid grid-cols-2 gap-2">
                    <label className="block">
                        <span className="text-xs font-bold text-slate-700 dark:text-slate-300">Costo $ <span className="text-red-500">*</span></span>
                        <input
                            type="text"
                            inputMode="decimal"
                            className="mt-1 w-full rounded border border-slate-300 bg-white p-1.5 text-sm dark:border-slate-700 dark:bg-slate-800"
                            value={form.costo}
                            onChange={(e) => update("costo", e.target.value)}
                        />
                    </label>
                    <label className="block">
                        <span className="text-xs font-bold text-slate-700 dark:text-slate-300">IVA % <span className="text-red-500">*</span></span>
                        <input
                            type="text"
                            inputMode="decimal"
                            className="mt-1 w-full rounded border border-slate-300 bg-white p-1.5 text-sm dark:border-slate-700 dark:bg-slate-800"
                            value={form.iva}
                            onChange={(e) => update("iva", e.target.value)}
                        />
                    </label>
                </div>

                <div className="grid grid-cols-2 gap-2">
                    <label className="block">
                        <span className="text-xs font-bold text-slate-700 dark:text-slate-300">Margen Min. % <span className="text-red-500">*</span></span>
                        <input
                            type="text"
                            inputMode="decimal"
                            className="mt-1 w-full rounded border border-slate-300 bg-white p-1.5 text-sm dark:border-slate-700 dark:bg-slate-800"
                            value={form.margenMin}
                            onChange={(e) => update("margenMin", e.target.value)}
                        />
                    </label>
                    <label className="block">
                        <span className="text-xs font-bold text-slate-700 dark:text-slate-300">Margen May. % <span className="text-red-500">*</span></span>
                        <input
                            type="text"
                            inputMode="decimal"
                            className="mt-1 w-full rounded border border-slate-300 bg-white p-1.5 text-sm dark:border-slate-700 dark:bg-slate-800"
                            value={form.margenMay}
                            onChange={(e) => update("margenMay", e.target.value)}
                        />
                    </label>
                </div>

                <div className="grid grid-cols-2 gap-2">
                    <label className="block">
                        <span className="text-xs font-bold text-slate-700 dark:text-slate-300">Margen Fijo Min. $</span>
                        <input
                            type="text"
                            inputMode="decimal"
                            className="mt-1 w-full rounded border border-slate-300 bg-white p-1.5 text-sm dark:border-slate-700 dark:bg-slate-800"
                            value={form.margenFijoMin}
                            onChange={(e) => update("margenFijoMin", e.target.value)}
                            placeholder="—"
                        />
                    </label>
                    <label className="block">
                        <span className="text-xs font-bold text-slate-700 dark:text-slate-300">Margen Fijo May. $</span>
                        <input
                            type="text"
                            inputMode="decimal"
                            className="mt-1 w-full rounded border border-slate-300 bg-white p-1.5 text-sm dark:border-slate-700 dark:bg-slate-800"
                            value={form.margenFijoMay}
                            onChange={(e) => update("margenFijoMay", e.target.value)}
                            placeholder="—"
                        />
                    </label>
                </div>


                <details open className="rounded border border-slate-200 dark:border-slate-700">
                    <summary className="cursor-pointer px-3 py-2 text-xs font-bold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                        Clasificaciones (para reglas)
                    </summary>
                    <div className="grid grid-cols-1 gap-2 p-3 sm:grid-cols-2">
                        <AsyncSelect
                            label="Marca"
                            placeholder="Sin marca"
                            loadOptions={async (q) => (await searchMarcas(q)).map((o: any) => ({ id: o.id, label: o.label }))}
                            onChange={(val, label) => { update("marcaId", val ? Number(val) : null); update("marcaLabel", label || ""); }}
                            displayValue={form.marcaId ? form.marcaLabel : undefined}
                        />
                        <AsyncSelect
                            label="Tipo"
                            placeholder="Sin tipo"
                            loadOptions={async (q) => (await searchTipos(q)).map((o: any) => ({ id: o.id, label: o.label }))}
                            onChange={(val, label) => { update("tipoId", val ? Number(val) : null); update("tipoLabel", label || ""); }}
                            displayValue={form.tipoId ? form.tipoLabel : undefined}
                        />
                        <AsyncSelect
                            label="Clasif. Gral"
                            placeholder="Sin rubro"
                            loadOptions={async (q) => (await searchClasifGral(q)).map((o: any) => ({ id: o.id, label: o.label }))}
                            onChange={(val, label) => { update("clasifGralId", val ? Number(val) : null); update("clasifGralLabel", label || ""); }}
                            displayValue={form.clasifGralId ? form.clasifGralLabel : undefined}
                        />
                        <AsyncSelect
                            label="Clasif. Gastro"
                            placeholder="Sin gastro"
                            loadOptions={async (q) => (await searchClasifGastro(q)).map((o: any) => ({ id: o.id, label: o.label }))}
                            onChange={(val, label) => { update("clasifGastroId", val ? Number(val) : null); update("clasifGastroLabel", label || ""); }}
                            displayValue={form.clasifGastroId ? form.clasifGastroLabel : undefined}
                        />
                        <label className="block">
                            <span className="text-xs font-bold text-slate-700 dark:text-slate-300">Tag</span>
                            <select
                                className="mt-1 w-full rounded border border-slate-300 bg-white p-1.5 text-sm dark:border-slate-700 dark:bg-slate-800"
                                value={form.tag}
                                onChange={(e) => update("tag", e.target.value as Tag | "")}
                            >
                                <option value="">Sin tag</option>
                                <option value="MAQUINA">Máquina</option>
                                <option value="REPUESTO">Repuesto</option>
                                <option value="MENAJE">Menaje</option>
                            </select>
                        </label>
                    </div>
                </details>

                <details open className="rounded border border-slate-200 dark:border-slate-700">
                    <summary className="cursor-pointer px-3 py-2 text-xs font-bold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                        Avanzado: financiación y MLA
                    </summary>
                    <div className="grid grid-cols-1 gap-2 p-3 sm:grid-cols-3">
                        <label className="block">
                            <span className="text-xs font-bold text-slate-700 dark:text-slate-300">Financ. Proveedor %</span>
                            <input
                                type="text"
                                inputMode="decimal"
                                className="mt-1 w-full rounded border border-slate-300 bg-white p-1.5 text-sm dark:border-slate-700 dark:bg-slate-800"
                                value={form.proveedorFin}
                                onChange={(e) => update("proveedorFin", e.target.value)}
                                placeholder="—"
                            />
                            <span className="mt-1 block text-[10px] italic text-slate-500 dark:text-slate-400">
                                Aplica solo si el canal tiene <span className="font-mono">FLAG_FINANCIACION_PROVEEDOR</span>.
                            </span>
                        </label>
                        <label className="block">
                            <span className="text-xs font-bold text-slate-700 dark:text-slate-300">MLA Envío $</span>
                            <input
                                type="text"
                                inputMode="decimal"
                                className="mt-1 w-full rounded border border-slate-300 bg-white p-1.5 text-sm dark:border-slate-700 dark:bg-slate-800"
                                value={form.mlaPrecioEnvio}
                                onChange={(e) => update("mlaPrecioEnvio", e.target.value)}
                                placeholder="—"
                            />
                            <span className="mt-1 block text-[10px] italic text-slate-500 dark:text-slate-400">
                                Aplica solo si el canal tiene <span className="font-mono">FLAG_INCLUIR_ENVIO</span>.
                            </span>
                        </label>
                        <label className="block">
                            <span className="text-xs font-bold text-slate-700 dark:text-slate-300">MLA Comisión %</span>
                            <input
                                type="text"
                                inputMode="decimal"
                                className="mt-1 w-full rounded border border-slate-300 bg-white p-1.5 text-sm dark:border-slate-700 dark:bg-slate-800"
                                value={form.mlaComision}
                                onChange={(e) => update("mlaComision", e.target.value)}
                                placeholder="—"
                            />
                            <span className="mt-1 block text-[10px] italic text-slate-500 dark:text-slate-400">
                                Aplica solo si el canal tiene <span className="font-mono">FLAG_COMISION_ML</span>.
                            </span>
                        </label>
                    </div>
                </details>

                <details open className="rounded border border-slate-200 dark:border-slate-700">
                    <summary className="cursor-pointer px-3 py-2 text-xs font-bold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                        Precio Inflado (precio tachado)
                    </summary>
                    <div className="grid grid-cols-1 gap-2 p-3 sm:grid-cols-2">
                        <label className="block">
                            <span className="text-xs font-bold text-slate-700 dark:text-slate-300">Tipo</span>
                            <select
                                className="mt-1 w-full rounded border border-slate-300 bg-white p-1.5 text-sm dark:border-slate-700 dark:bg-slate-800"
                                value={form.precioInfladoTipo}
                                onChange={(e) => update("precioInfladoTipo", e.target.value as TipoInflado)}
                            >
                                <option value="">Sin precio inflado</option>
                                <option value="MULTIPLICADOR">Multiplicador (× valor)</option>
                                <option value="DESCUENTO_PORC">Descuento % (PVP / (1 − v/100))</option>
                                <option value="DIVISOR">Divisor (PVP / valor)</option>
                                <option value="PRECIO_FIJO">Precio Fijo ($)</option>
                            </select>
                        </label>
                        <label className="block">
                            <span className="text-xs font-bold text-slate-700 dark:text-slate-300">Valor</span>
                            <input
                                type="text"
                                inputMode="decimal"
                                className="mt-1 w-full rounded border border-slate-300 bg-white p-1.5 text-sm dark:border-slate-700 dark:bg-slate-800"
                                value={form.precioInfladoValor}
                                onChange={(e) => update("precioInfladoValor", e.target.value)}
                                placeholder="—"
                                disabled={form.precioInfladoTipo === ""}
                            />
                        </label>
                        <span className="col-span-full text-[10px] italic text-slate-500 dark:text-slate-400">
                            Aplica solo si el canal tiene <span className="font-mono">FLAG_APLICAR_PRECIO_INFLADO</span>.
                            Al cargar un producto existente, se autocompleta con la regla asignada al canal (si tiene).
                        </span>
                    </div>
                </details>

                <div className="flex flex-wrap items-center gap-2 pt-2">
                    <Button variant="dark" onClick={handleSimular} disabled={isLoading}>
                        <BoltIcon className="h-4 w-4" />
                        {isLoading ? "Calculando..." : "Calcular"}
                    </Button>
                    <Button variant="light" onClick={handleReset} disabled={isLoading}>
                        <XMarkIcon className="h-4 w-4" />
                        Reset
                    </Button>
                </div>
            </div>

            {/* Columna derecha: resultado */}
            <div className="flex min-h-[300px] flex-col gap-2 rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
                <h2 className="text-sm font-bold uppercase tracking-wide text-slate-500 dark:text-slate-400">Resultado</h2>
                {error ? (
                    <div className="flex flex-1 items-center justify-center rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-800/60 dark:bg-red-900/20 dark:text-red-200">
                        {error}
                    </div>
                ) : !resultado ? (
                    <div className="flex flex-1 items-center justify-center rounded-md border-2 border-dashed border-slate-200 p-6 text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
                        Completá los campos y presioná <strong className="mx-1">Calcular</strong> para ver el desglose.
                    </div>
                ) : (
                    <div className="flex flex-col gap-3">
                        {/* Header con canal y cuotas */}
                        <div className="rounded-md border border-slate-200 bg-white px-3 py-2 dark:border-slate-700 dark:bg-slate-900">
                            <p className="text-[10px] font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                                {resultado.formula.canalNombre}
                                {resultado.formula.descripcionCuotas ? ` · ${resultado.formula.descripcionCuotas}` : ""}
                            </p>
                            <p className="break-all font-mono text-xs text-slate-700 dark:text-slate-300">
                                {resultado.formula.formulaGeneral}
                            </p>
                        </div>

                        {/* Indicadores (mismo set que el Monitor de Precios) */}
                        <IndicadoresPanel indicadores={resultado.indicadores} />

                        {/* Fórmula paso a paso */}
                        <div className="overflow-hidden rounded-md border border-slate-200 bg-slate-50 dark:border-slate-700 dark:bg-slate-800/40">
                            <div className="border-b border-slate-200 bg-white px-3 py-1.5 dark:border-slate-700 dark:bg-slate-900">
                                <p className="text-[10px] font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                                    Fórmula paso a paso
                                </p>
                            </div>
                            <div className="max-h-[24rem] overflow-auto px-3 py-1">
                                {resultado.formula.pasos.map((p) => (
                                    <PasoLine key={p.numeroPaso} paso={p} />
                                ))}
                            </div>
                            <div className="flex items-center justify-between border-t-2 border-emerald-300 bg-emerald-50 px-3 py-2 dark:border-emerald-700 dark:bg-emerald-900/20">
                                <span className="text-xs font-bold uppercase tracking-wider text-emerald-700 dark:text-emerald-300">
                                    PVP Final
                                </span>
                                <span className="font-mono text-lg font-bold text-emerald-800 dark:text-emerald-200">
                                    {formatMoney(resultado.formula.resultadoFinal)}
                                </span>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}

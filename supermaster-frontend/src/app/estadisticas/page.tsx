"use client";
import { useEffect, useMemo, useState } from "react";
import type { ElementType, ReactNode } from "react";
import {
    fetchCuotasDisponibles,
    fetchEstadisticasResumen,
    fetchMargenesPorCuotas,
    fetchProductosConMargenNegativo,
    fetchProductosPorCatalogo,
    fetchProductosPorProveedor,
} from "./estadisticasService";
import {
    CuotaDisponibleDTO,
    EstadisticasResumenDTO,
    MargenesPorCuotasDTO,
    ProductoMargenNegativo,
    ProductosPorCatalogo,
    ProductosPorProveedor,
} from "./types";
import {
    BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, LabelList,
    PieChart, Pie, Cell,
} from "recharts";
import {
    ArrowPathIcon,
    ArrowTrendingUpIcon,
    ChartBarIcon,
    CubeIcon,
    CurrencyDollarIcon,
    ExclamationTriangleIcon,
    InformationCircleIcon,
    ShoppingBagIcon,
    TruckIcon,
    PhotoIcon,
    Square2StackIcon,
    StarIcon,
    CalculatorIcon,
} from "@heroicons/react/24/outline";

const COLORS = ["#3b82f6", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6", "#ec4899", "#06b6d4", "#f97316"];

const CANAL_COLOR_MAP: Record<string, string> = {
    "LINEA GE": "#f59e0b",
    "LIZZY GASTRO": "#ef4444",
    "LIZZY HUDSON": "#8b5cf6",
    "ML": "#ec4899",
};

function getCanalColor(canalNombre: string, index: number) {
    return CANAL_COLOR_MAP[canalNombre.trim().toUpperCase()] ?? COLORS[index % COLORS.length];
}

function formatChartValue(value: number | string) {
    const numericValue = Number(value);
    if (Number.isNaN(numericValue)) return String(value);
    return numericValue.toLocaleString("es-AR", {
        minimumFractionDigits: Number.isInteger(numericValue) ? 0 : 1,
        maximumFractionDigits: Number.isInteger(numericValue) ? 0 : 1,
    });
}

function formatCuotaLabel(cuotas: number) {
    if (cuotas === -1) return "Transferencia";
    if (cuotas === 0) return "Contado";
    return `${cuotas} cuotas`;
}

function InfoTip({ text }: { text: string }) {
    return (
        <span className="relative group inline-flex ml-1.5 cursor-help">
            <InformationCircleIcon className="size-4 text-gray-400 dark:text-slate-500" />
            <span className="pointer-events-none absolute bottom-full left-1/2 z-50 mb-2 w-56 -translate-x-1/2 rounded-lg bg-gray-900 px-3 py-2 text-left text-xs leading-relaxed text-white opacity-0 shadow-lg transition-opacity duration-150 group-hover:opacity-100 dark:bg-slate-700">
                {text}
            </span>
        </span>
    );
}

function StatCard({ label, value, icon: Icon, color, info }: { label: string; value: number; icon: ElementType; color: string; info: string }) {
    return (
        <div className="flex items-center gap-3 rounded-xl border border-gray-100 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-800">
            <div className={`rounded-lg p-2.5 ${color}`}>
                <Icon className="size-5" />
            </div>
            <div>
                <p className="text-2xl font-bold text-gray-800 dark:text-slate-100">{value.toLocaleString("es-AR")}</p>
                <p className="flex items-center text-xs text-gray-500 dark:text-slate-400">
                    {label}
                    <InfoTip text={info} />
                </p>
            </div>
        </div>
    );
}

function SectionTitle({ title, info }: { title: string; info: string }) {
    return (
        <h2 className="mb-4 flex items-center text-sm font-semibold text-gray-700 dark:text-slate-200">
            {title}
            <InfoTip text={info} />
        </h2>
    );
}

function BarValueLabel({ x, y, width, value, suffix = "" }: { x?: number; y?: number; width?: number; value?: number | string; suffix?: string }) {
    if (x == null || y == null || width == null || value == null) return null;

    return (
        <text x={x + width / 2} y={y + 18} fill="#ffffff" textAnchor="middle" fontSize={11} fontWeight={700}>
            {`${formatChartValue(value)}${suffix}`}
        </text>
    );
}

function PieValueLabel(props: {
    cx?: number;
    cy?: number;
    midAngle?: number;
    innerRadius?: number;
    outerRadius?: number;
    value?: number | string;
}) {
    const { cx, cy, midAngle, innerRadius, outerRadius, value } = props;
    if (cx == null || cy == null || midAngle == null || innerRadius == null || outerRadius == null || value == null) {
        return null;
    }

    const radius = innerRadius + (outerRadius - innerRadius) * 0.6;
    const x = cx + radius * Math.cos((-midAngle * Math.PI) / 180);
    const y = cy + radius * Math.sin((-midAngle * Math.PI) / 180);

    return (
        <text x={x} y={y} fill="#ffffff" textAnchor="middle" dominantBaseline="central" fontSize={11} fontWeight={700}>
            {formatChartValue(value)}
        </text>
    );
}

function PieProveedorLabel(props: {
    cx?: number;
    cy?: number;
    midAngle?: number;
    outerRadius?: number;
    name?: string;
    value?: number | string;
    percent?: number;
}) {
    const { cx, cy, midAngle, outerRadius, name, value } = props;
    if (cx == null || cy == null || midAngle == null || outerRadius == null || name == null || value == null) {
        return null;
    }

    const radius = outerRadius + 22;
    const x = cx + radius * Math.cos((-midAngle * Math.PI) / 180);
    const y = cy + radius * Math.sin((-midAngle * Math.PI) / 180);
    const textAnchor = x > cx ? "start" : "end";

    return (
        <text x={x} y={y} fill="#475569" textAnchor={textAnchor} dominantBaseline="central" fontSize={11} fontWeight={600}>
            {`${name} (${formatChartValue(value)})`}
        </text>
    );
}

function LoadButton({
    label,
    loading,
    onClick,
}: {
    label: string;
    loading: boolean;
    onClick: () => void;
}) {
    return (
        <button
            type="button"
            onClick={onClick}
            disabled={loading}
            className="inline-flex items-center gap-2 rounded-lg border border-blue-200 bg-blue-50 px-3 py-2 text-sm font-medium text-blue-700 transition hover:bg-blue-100 disabled:cursor-not-allowed disabled:opacity-60 dark:border-blue-500/30 dark:bg-blue-500/10 dark:text-blue-300 dark:hover:bg-blue-500/20"
        >
            <ArrowPathIcon className={`size-4 ${loading ? "animate-spin" : ""}`} />
            {loading ? "Cargando..." : label}
        </button>
    );
}

function PlaceholderCard({
    title,
    info,
    buttonLabel,
    loading,
    onLoad,
    children,
    actions,
}: {
    title: string;
    info: string;
    buttonLabel: string;
    loading: boolean;
    onLoad: () => void;
    children: ReactNode | null;
    actions?: ReactNode;
}) {
    return (
        <div className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-800">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                <SectionTitle title={title} info={info} />
                <div className="flex items-center gap-2">
                    {actions}
                    <LoadButton label={buttonLabel} loading={loading} onClick={onLoad} />
                </div>
            </div>
            {loading ? (
                <div className="flex min-h-[220px] items-center justify-center rounded-xl border border-dashed border-gray-200 bg-gray-50/80 text-sm text-gray-500 dark:border-slate-700 dark:bg-slate-900/40 dark:text-slate-400">
                    Cargando estadística...
                </div>
            ) : children ?? (
                <div className="flex min-h-[220px] items-center justify-center rounded-xl border border-dashed border-gray-200 bg-gray-50/80 text-sm text-gray-500 dark:border-slate-700 dark:bg-slate-900/40 dark:text-slate-400">
                    Todavía no se cargó esta estadística.
                </div>
            )}
        </div>
    );
}

function ChartPanel({ title, children }: { title: string; children: ReactNode }) {
    return (
        <div>
            <h3 className="mb-3 text-sm font-semibold text-gray-700 dark:text-slate-200">{title}</h3>
            {children}
        </div>
    );
}

export default function EstadisticasPage() {
    const [resumen, setResumen] = useState<EstadisticasResumenDTO | null>(null);
    const [margenes, setMargenes] = useState<MargenesPorCuotasDTO | null>(null);
    const [productosPorProveedor, setProductosPorProveedor] = useState<ProductosPorProveedor[] | null>(null);
    const [productosPorCatalogo, setProductosPorCatalogo] = useState<ProductosPorCatalogo[] | null>(null);
    const [productosConMargenNegativo, setProductosConMargenNegativo] = useState<ProductoMargenNegativo[] | null>(null);
    const [cuotasDisponibles, setCuotasDisponibles] = useState<CuotaDisponibleDTO[]>([{ cuotas: 0, descripcion: null }]);

    const [loadingResumen, setLoadingResumen] = useState(false);
    const [loadingMargenes, setLoadingMargenes] = useState(false);
    const [loadingProveedores, setLoadingProveedores] = useState(false);
    const [loadingCatalogos, setLoadingCatalogos] = useState(false);
    const [loadingNegativos, setLoadingNegativos] = useState(false);

    const [errorResumen, setErrorResumen] = useState("");
    const [cuotasSeleccionada, setCuotasSeleccionada] = useState<number>(0);

    useEffect(() => {
        fetchCuotasDisponibles()
            .then((cuotas) => setCuotasDisponibles(cuotas.length ? cuotas : [{ cuotas: 0, descripcion: null }]))
            .catch(() => setCuotasDisponibles([{ cuotas: 0, descripcion: null }]));
    }, []);

    const cargarResumen = async () => {
        setLoadingResumen(true);
        setErrorResumen("");
        setResumen(null);
        try {
            const data = await fetchEstadisticasResumen();
            setResumen(data);
            setCuotasDisponibles((prev) => {
                if (prev.some((item) => item.descripcion)) return prev;
                return data.cuotasDisponibles.length
                    ? data.cuotasDisponibles.map((cuotas) => ({ cuotas, descripcion: null }))
                    : [{ cuotas: 0, descripcion: null }];
            });
        } catch {
            setErrorResumen("Error al cargar el resumen de estadísticas");
        } finally {
            setLoadingResumen(false);
        }
    };

    const cargarMargenes = async (cuotas = cuotasSeleccionada) => {
        setLoadingMargenes(true);
        setMargenes(null);
        try {
            const data = await fetchMargenesPorCuotas(cuotas);
            setMargenes(data);
        } finally {
            setLoadingMargenes(false);
        }
    };

    const cargarProveedores = async () => {
        setLoadingProveedores(true);
        setProductosPorProveedor(null);
        try {
            setProductosPorProveedor(await fetchProductosPorProveedor());
        } finally {
            setLoadingProveedores(false);
        }
    };

    const cargarCatalogos = async () => {
        setLoadingCatalogos(true);
        setProductosPorCatalogo(null);
        try {
            setProductosPorCatalogo(await fetchProductosPorCatalogo());
        } finally {
            setLoadingCatalogos(false);
        }
    };

    const cargarNegativos = async () => {
        setLoadingNegativos(true);
        setProductosConMargenNegativo(null);
        try {
            setProductosConMargenNegativo(await fetchProductosConMargenNegativo());
        } finally {
            setLoadingNegativos(false);
        }
    };

    const handleCuotasChange = (cuotas: number) => {
        setCuotasSeleccionada(cuotas);
        void cargarMargenes(cuotas);
    };

    const distribucionData = useMemo(() => {
        if (!margenes?.distribucionMargenes) return [];
        return [
            { rango: "< 0%", cantidad: margenes.distribucionMargenes.negativo },
            { rango: "0-10%", cantidad: margenes.distribucionMargenes.rango0a10 },
            { rango: "10-20%", cantidad: margenes.distribucionMargenes.rango10a20 },
            { rango: "20-30%", cantidad: margenes.distribucionMargenes.rango20a30 },
            { rango: "30-50%", cantidad: margenes.distribucionMargenes.rango30a50 },
            { rango: "> 50%", cantidad: margenes.distribucionMargenes.rangoMayor50 },
        ];
    }, [margenes]);

    const distribucionColors = ["#ef4444", "#f59e0b", "#84cc16", "#10b981", "#3b82f6", "#8b5cf6"];

    return (
        <main className="overflow-y-auto bg-gray-50 p-6 dark:bg-slate-900">
            <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
                <h1 className="inline-flex items-center gap-2 leading-none text-3xl font-bold text-gray-800 dark:text-slate-100">
                    <ChartBarIcon className="h-8 w-8 text-gray-600 dark:text-slate-300" />
                    Estadísticas
                </h1>
                <button
                    type="button"
                    onClick={() => {
                        void cargarResumen();
                        void cargarMargenes();
                        void cargarProveedores();
                        void cargarCatalogos();
                        void cargarNegativos();
                    }}
                    className="inline-flex items-center gap-2 rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm font-medium text-gray-700 transition hover:bg-gray-100 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700"
                >
                    <ArrowPathIcon className="size-4" />
                    Cargar todas
                </button>
            </div>

            <div className="mb-8 rounded-xl border border-gray-100 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-800">
                <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                    <SectionTitle title="Resumen general" info="Vista rápida de los indicadores principales del catálogo y las cuotas disponibles para el resto de las estadísticas." />
                    <LoadButton label={resumen ? "Actualizar resumen" : "Cargar resumen"} loading={loadingResumen} onClick={() => void cargarResumen()} />
                </div>
                {errorResumen ? <p className="mb-4 text-sm text-red-500">{errorResumen}</p> : null}
                {loadingResumen ? (
                    <div className="flex min-h-[180px] items-center justify-center rounded-xl border border-dashed border-gray-200 bg-gray-50/80 text-sm text-gray-500 dark:border-slate-700 dark:bg-slate-900/40 dark:text-slate-400">
                        Cargando resumen...
                    </div>
                ) : resumen ? (
                    <div className="grid grid-cols-2 gap-3 lg:grid-cols-3 xl:grid-cols-4">
                        <StatCard label="Total productos" value={resumen.totalProductos} icon={CubeIcon} color="bg-blue-50 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400" info="Cantidad total de productos registrados en el sistema, independientemente de su estado." />
                        <StatCard label="Activos" value={resumen.productosActivos} icon={ArrowTrendingUpIcon} color="bg-emerald-50 text-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-400" info="Productos marcados como activos y disponibles para la venta." />
                        <StatCard label="Con precio calculado" value={resumen.productosConPrecio} icon={CalculatorIcon} color="bg-cyan-50 text-cyan-600 dark:bg-cyan-900/30 dark:text-cyan-400" info="Productos que tienen al menos un precio generado en algún canal." />
                        <StatCard label="Con MLA" value={resumen.productosConMla} icon={ShoppingBagIcon} color="bg-purple-50 text-purple-600 dark:bg-purple-900/30 dark:text-purple-400" info="Productos vinculados a una publicación de MercadoLibre." />
                        <StatCard label="Combos" value={resumen.productosCombos} icon={Square2StackIcon} color="bg-indigo-50 text-indigo-600 dark:bg-indigo-900/30 dark:text-indigo-400" info="Productos marcados como combo (agrupación de varios artículos)." />
                        <StatCard label="Prioritarios" value={resumen.productosPrio} icon={StarIcon} color="bg-yellow-50 text-yellow-600 dark:bg-yellow-900/30 dark:text-yellow-400" info="Productos con tag PRIO para reposición prioritaria." />
                        <StatCard label="Sin stock" value={resumen.productosSinStock} icon={ExclamationTriangleIcon} color="bg-amber-50 text-amber-600 dark:bg-amber-900/30 dark:text-amber-400" info="Productos con stock igual a cero. Pueden necesitar reposición." />
                        <StatCard label="Sin costo" value={resumen.productosSinCosto} icon={CurrencyDollarIcon} color="bg-orange-50 text-orange-600 dark:bg-orange-900/30 dark:text-orange-400" info="Productos que no tienen costo cargado. No se puede calcular margen ni markup." />
                        <StatCard label="Sin proveedor" value={resumen.productosSinProveedor} icon={TruckIcon} color="bg-orange-50 text-orange-600 dark:bg-orange-900/30 dark:text-orange-400" info="Productos sin proveedor asignado. No se pueden reponer automáticamente." />
                        <StatCard label="Sin imagen" value={resumen.productosSinImagen} icon={PhotoIcon} color="bg-amber-50 text-amber-600 dark:bg-amber-900/30 dark:text-amber-400" info="Productos sin imagen cargada. Afecta catálogos PDF y publicaciones." />
                        <StatCard label="Sin margen" value={resumen.productosSinMargen} icon={ExclamationTriangleIcon} color="bg-red-50 text-red-600 dark:bg-red-900/30 dark:text-red-400" info="Productos activos sin precio calculado en ningún canal." />
                        <StatCard label="Margen negativo" value={resumen.productosMargenNegativo} icon={ExclamationTriangleIcon} color="bg-rose-50 text-rose-600 dark:bg-rose-900/30 dark:text-rose-400" info="Productos que se venden por debajo del costo en al menos un canal. Requieren atención urgente." />
                    </div>
                ) : (
                    <div className="flex min-h-[180px] items-center justify-center rounded-xl border border-dashed border-gray-200 bg-gray-50/80 text-sm text-gray-500 dark:border-slate-700 dark:bg-slate-900/40 dark:text-slate-400">
                        Todavía no se cargó el resumen.
                    </div>
                )}
            </div>

            <div className="grid grid-cols-1 gap-6">
                <div className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-800">
                    <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
                        <div>
                            <h2 className="text-base font-semibold text-gray-800 dark:text-slate-100">Rentabilidad por cuotas</h2>
                            <p className="text-sm text-gray-500 dark:text-slate-400">
                                Elegí la cuota y analizá juntos los márgenes por canal y la distribución general.
                            </p>
                        </div>
                        <div className="flex items-center gap-2">
                            <span className="text-sm font-medium text-gray-600 dark:text-slate-300">Cuotas:</span>
                            <select
                                value={cuotasSeleccionada}
                                onChange={(e) => handleCuotasChange(Number(e.target.value))}
                                disabled={loadingMargenes}
                                className="rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-60 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200"
                            >
                                {cuotasDisponibles.map((item) => (
                                    <option key={item.cuotas} value={item.cuotas}>
                                        {formatCuotaLabel(item.cuotas)}
                                    </option>
                                ))}
                            </select>
                        </div>
                    </div>

                    <div className="grid grid-cols-1 gap-6">
                        <PlaceholderCard
                            title={`Márgenes y markup por canal (${cuotasSeleccionada === 0 ? "contado" : `${cuotasSeleccionada} cuotas`})`}
                            info="Comparativa por canal de margen sobre PVP, margen sobre ingreso neto y markup para la cantidad de cuotas seleccionada."
                            buttonLabel={margenes ? "Actualizar márgenes" : "Cargar márgenes"}
                            loading={loadingMargenes}
                            onLoad={() => void cargarMargenes()}
                        >
                            {margenes ? (
                                <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">
                                    <ChartPanel title="Margen s/PVP por canal">
                                        <ResponsiveContainer width="100%" height={300}>
                                            <BarChart data={margenes.margenesPorCanal} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
                                                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                                                <XAxis dataKey="canalNombre" tick={{ fontSize: 11 }} stroke="#94a3b8" />
                                                <YAxis tick={{ fontSize: 11 }} stroke="#94a3b8" unit="%" />
                                                <Tooltip
                                                    contentStyle={{ backgroundColor: "#1e293b", border: "none", borderRadius: 8, fontSize: 12 }}
                                                    labelStyle={{ color: "#e2e8f0" }}
                                                    itemStyle={{ color: "#e2e8f0" }}
                                                    formatter={(value) => [`${Number(value).toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`, "Margen s/PVP"]}
                                                />
                                                <Bar dataKey="margenPromedioSobrePvp" name="Margen % PVP" radius={[4, 4, 0, 0]}>
                                                    {margenes.margenesPorCanal.map((item, i) => (
                                                        <Cell key={item.canalId ?? i} fill={getCanalColor(item.canalNombre, i)} />
                                                    ))}
                                                    <LabelList dataKey="margenPromedioSobrePvp" content={<BarValueLabel suffix="%" />} />
                                                </Bar>
                                            </BarChart>
                                        </ResponsiveContainer>
                                    </ChartPanel>

                                    <ChartPanel title="Margen s/Ingreso Neto por canal">
                                        <ResponsiveContainer width="100%" height={300}>
                                            <BarChart data={margenes.margenesPorCanal} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
                                                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                                                <XAxis dataKey="canalNombre" tick={{ fontSize: 11 }} stroke="#94a3b8" />
                                                <YAxis tick={{ fontSize: 11 }} stroke="#94a3b8" unit="%" />
                                                <Tooltip
                                                    contentStyle={{ backgroundColor: "#1e293b", border: "none", borderRadius: 8, fontSize: 12 }}
                                                    labelStyle={{ color: "#e2e8f0" }}
                                                    itemStyle={{ color: "#e2e8f0" }}
                                                    formatter={(value) => [`${Number(value).toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`, "Margen s/Ingreso Neto"]}
                                                />
                                                <Bar dataKey="margenPromedioSobreIngresoNeto" name="Margen % Ingreso Neto" radius={[4, 4, 0, 0]}>
                                                    {margenes.margenesPorCanal.map((item, i) => (
                                                        <Cell key={item.canalId ?? i} fill={getCanalColor(item.canalNombre, i)} />
                                                    ))}
                                                    <LabelList dataKey="margenPromedioSobreIngresoNeto" content={<BarValueLabel suffix="%" />} />
                                                </Bar>
                                            </BarChart>
                                        </ResponsiveContainer>
                                    </ChartPanel>

                                    <ChartPanel title="Markup por canal">
                                        <ResponsiveContainer width="100%" height={300}>
                                            <BarChart data={margenes.margenesPorCanal} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
                                                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                                                <XAxis dataKey="canalNombre" tick={{ fontSize: 11 }} stroke="#94a3b8" />
                                                <YAxis tick={{ fontSize: 11 }} stroke="#94a3b8" unit="%" />
                                                <Tooltip
                                                    contentStyle={{ backgroundColor: "#1e293b", border: "none", borderRadius: 8, fontSize: 12 }}
                                                    labelStyle={{ color: "#e2e8f0" }}
                                                    itemStyle={{ color: "#e2e8f0" }}
                                                    formatter={(value) => [`${Number(value).toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`, "Markup"]}
                                                />
                                                <Bar dataKey="markupPromedio" name="Markup %" radius={[4, 4, 0, 0]}>
                                                    {margenes.margenesPorCanal.map((item, i) => (
                                                        <Cell key={item.canalId ?? i} fill={getCanalColor(item.canalNombre, i)} />
                                                    ))}
                                                    <LabelList dataKey="markupPromedio" content={<BarValueLabel suffix="%" />} />
                                                </Bar>
                                            </BarChart>
                                        </ResponsiveContainer>
                                    </ChartPanel>
                                </div>
                            ) : null}
                        </PlaceholderCard>

                        <PlaceholderCard
                            title={`Distribución de márgenes (${cuotasSeleccionada === 0 ? "contado" : `${cuotasSeleccionada} cuotas`})`}
                            info="Histograma que muestra cuántos productos caen en cada rango de margen. Permite identificar rápidamente productos con márgenes bajos o negativos."
                            buttonLabel={margenes ? "Actualizar distribución" : "Cargar distribución"}
                            loading={loadingMargenes}
                            onLoad={() => void cargarMargenes()}
                        >
                            {margenes ? (
                                <ResponsiveContainer width="100%" height={300}>
                                    <BarChart data={distribucionData} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
                                        <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                                        <XAxis dataKey="rango" tick={{ fontSize: 11 }} stroke="#94a3b8" />
                                        <YAxis tick={{ fontSize: 11 }} stroke="#94a3b8" />
                                        <Tooltip
                                            contentStyle={{ backgroundColor: "#1e293b", border: "none", borderRadius: 8, fontSize: 12 }}
                                            labelStyle={{ color: "#e2e8f0" }}
                                            itemStyle={{ color: "#e2e8f0" }}
                                            formatter={(value) => [`${value} productos`, "Cantidad"]}
                                        />
                                        <Bar dataKey="cantidad" name="Productos" radius={[4, 4, 0, 0]}>
                                            {distribucionData.map((item, i) => (
                                                <Cell key={item.rango} fill={distribucionColors[i]} />
                                            ))}
                                            <LabelList dataKey="cantidad" content={<BarValueLabel />} />
                                        </Bar>
                                    </BarChart>
                                </ResponsiveContainer>
                            ) : null}
                        </PlaceholderCard>
                    </div>
                </div>

                <PlaceholderCard
                    title="Productos por proveedor (Top 10)"
                    info="Distribución de productos entre los 10 proveedores con más productos. Útil para evaluar dependencia de proveedores."
                    buttonLabel={productosPorProveedor ? "Actualizar proveedores" : "Cargar proveedores"}
                    loading={loadingProveedores}
                    onLoad={() => void cargarProveedores()}
                >
                    {productosPorProveedor ? (
                        <ResponsiveContainer width="100%" height={520}>
                            <PieChart margin={{ top: 20, right: 120, bottom: 20, left: 120 }}>
                                <Pie
                                    data={productosPorProveedor}
                                    dataKey="cantidad"
                                    nameKey="proveedorNombre"
                                    cx="50%"
                                    cy="50%"
                                    outerRadius={155}
                                    label={PieProveedorLabel}
                                    labelLine={{ stroke: "#94a3b8", strokeWidth: 1.25 }}
                                    style={{ fontSize: 11 }}
                                >
                                    {productosPorProveedor.map((item, i) => (
                                        <Cell key={item.proveedorId ?? i} fill={COLORS[i % COLORS.length]} />
                                    ))}
                                    <LabelList dataKey="cantidad" content={<PieValueLabel />} />
                                </Pie>
                                <Tooltip
                                    contentStyle={{ backgroundColor: "#1e293b", border: "none", borderRadius: 8, fontSize: 12 }}
                                    itemStyle={{ color: "#e2e8f0" }}
                                    formatter={(value) => [`${value} productos`]}
                                />
                            </PieChart>
                        </ResponsiveContainer>
                    ) : null}
                </PlaceholderCard>

                <PlaceholderCard
                    title="Productos por catálogo"
                    info="Cantidad de productos distintos asociados a cada catálogo. Un producto puede pertenecer a múltiples catálogos."
                    buttonLabel={productosPorCatalogo ? "Actualizar catálogos" : "Cargar catálogos"}
                    loading={loadingCatalogos}
                    onLoad={() => void cargarCatalogos()}
                >
                    {productosPorCatalogo ? (
                        <ResponsiveContainer width="100%" height={280}>
                            <BarChart data={productosPorCatalogo} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                                <XAxis dataKey="catalogoNombre" tick={{ fontSize: 11 }} stroke="#94a3b8" />
                                <YAxis tick={{ fontSize: 11 }} stroke="#94a3b8" />
                                <Tooltip
                                    contentStyle={{ backgroundColor: "#1e293b", border: "none", borderRadius: 8, fontSize: 12 }}
                                    labelStyle={{ color: "#e2e8f0" }}
                                    itemStyle={{ color: "#e2e8f0" }}
                                    formatter={(value) => [`${value} productos`, "Cantidad"]}
                                />
                                <Bar dataKey="cantidad" name="Productos" radius={[4, 4, 0, 0]}>
                                    {productosPorCatalogo.map((item, i) => (
                                        <Cell key={item.catalogoId ?? i} fill={COLORS[i % COLORS.length]} />
                                    ))}
                                    <LabelList dataKey="cantidad" content={<BarValueLabel />} />
                                </Bar>
                            </BarChart>
                        </ResponsiveContainer>
                    ) : null}
                </PlaceholderCard>

                <PlaceholderCard
                    title="Productos con margen negativo"
                    info="Lista detallada de productos que se venden por debajo del costo. Muestra canal, cuotas, margen y ganancia por unidad."
                    buttonLabel={productosConMargenNegativo ? "Actualizar listado" : "Cargar listado"}
                    loading={loadingNegativos}
                    onLoad={() => void cargarNegativos()}
                >
                    {productosConMargenNegativo ? (
                        productosConMargenNegativo.length > 0 ? (
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm">
                                    <thead>
                                        <tr className="border-b border-gray-200 dark:border-slate-700">
                                            <th className="px-3 py-2 text-left font-medium text-gray-500 dark:text-slate-400">SKU</th>
                                            <th className="px-3 py-2 text-left font-medium text-gray-500 dark:text-slate-400">Descripción</th>
                                            <th className="px-3 py-2 text-left font-medium text-gray-500 dark:text-slate-400">Canal</th>
                                            <th className="px-3 py-2 text-right font-medium text-gray-500 dark:text-slate-400">Cuotas</th>
                                            <th className="px-3 py-2 text-right font-medium text-gray-500 dark:text-slate-400">Margen %</th>
                                            <th className="px-3 py-2 text-right font-medium text-gray-500 dark:text-slate-400">Ganancia</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {productosConMargenNegativo.map((p, i) => (
                                            <tr key={`${p.productoId}-${i}`} className="border-b border-gray-100 hover:bg-gray-50 dark:border-slate-700/50 dark:hover:bg-slate-700/30">
                                                <td className="px-3 py-2 font-mono text-xs text-gray-800 dark:text-slate-200">{p.sku}</td>
                                                <td className="max-w-xs truncate px-3 py-2 text-gray-700 dark:text-slate-300">{p.descripcion}</td>
                                                <td className="px-3 py-2 text-gray-600 dark:text-slate-400">{p.canalNombre}</td>
                                                <td className="px-3 py-2 text-right text-gray-600 dark:text-slate-400">{p.cuotas ?? "Contado"}</td>
                                                <td className="px-3 py-2 text-right font-medium text-red-600 dark:text-red-400">{p.margenSobrePvp?.toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%</td>
                                                <td className="px-3 py-2 text-right font-medium text-red-600 dark:text-red-400">$ {p.ganancia?.toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        ) : (
                            <div className="flex min-h-[120px] items-center justify-center rounded-xl border border-dashed border-emerald-200 bg-emerald-50/60 text-sm text-emerald-700 dark:border-emerald-500/20 dark:bg-emerald-500/10 dark:text-emerald-300">
                                No hay productos con margen negativo.
                            </div>
                        )
                    ) : null}
                </PlaceholderCard>
            </div>
        </main>
    );
}

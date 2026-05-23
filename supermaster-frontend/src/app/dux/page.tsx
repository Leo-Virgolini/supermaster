"use client";
import { useState, useEffect, useRef } from "react";
import { notificar } from "../utils/notificar";
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { exportToExcel } from "../utils/exportCSV";
import { confirmDialog } from "../utils/confirmDialog";
import Button from "../components/Button/Button";
import {
    ServerStackIcon,
    MagnifyingGlassIcon,
    ArrowPathIcon,
    ArrowDownTrayIcon,
    CheckCircleIcon,
} from "@heroicons/react/24/outline";
import { useAuth } from "../context/AuthContext";

// ── Types ─────────────────────────────────────────────────────────────────────

interface DuxStatus {
    configurado: boolean;
    servicio: string;
}

interface DuxItem {
    cod_item?: string;
    item?: string;
    codigos_barra?: string[];
    rubro?: { id?: number; descripcion?: string };
    sub_rubro?: { id?: number; descripcion?: string };
    marca?: { id?: number; descripcion?: string };
    proveedor?: { id?: number; descripcion?: string };
    costo?: string;
    porc_iva?: string;
    habilitado?: string;
    codigo_externo?: string;
    fecha_creacion?: string;
    precios?: Array<{ lista?: string; precio?: string }>;
    stock?: Array<{ sucursal?: string; cantidad?: string }>;
}

// ── Service helpers ───────────────────────────────────────────────────────────

async function fetchStatus(): Promise<DuxStatus> {
    const res = await fetchAPI(`${API_BASE_URL}/api/dux/status`);
    return res.json();
}

async function fetchProducto(codItem: string): Promise<DuxItem> {
    const res = await fetchAPI(`${API_BASE_URL}/api/dux/productos/${encodeURIComponent(codItem)}`);
    return res.json();
}

async function fetchListasPrecios(): Promise<any[]> {
    const res = await fetchAPI(`${API_BASE_URL}/api/dux/listas-precios`);
    const data = await res.json();
    return Array.isArray(data) ? data : [];
}

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


const shellCardClassName = "rounded-3xl border border-slate-200 bg-white shadow-[0_10px_30px_-18px_rgba(15,23,42,0.25)] dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-black/30";
const sectionTitleClassName = "text-lg font-semibold text-slate-800 dark:text-slate-100";
const sectionHintClassName = "text-sm text-slate-500 dark:text-slate-400";

// ── Component ─────────────────────────────────────────────────────────────────

export default function DuxPage() {
    const { usuario } = useAuth();
    const isAdmin = (usuario.rol || "").trim().toUpperCase() === "ADMIN";
    // Status
    const [status, setStatus] = useState<DuxStatus | null>(null);
    const [statusError, setStatusError] = useState<string>("");
    const [isLoadingStatus, setIsLoadingStatus] = useState(true);

    // Rate limit
    const [rateLimit, setRateLimit] = useState<number>(7);
    const [rateLimitInput, setRateLimitInput] = useState<number>(7);
    const [isSavingRate, setIsSavingRate] = useState(false);

    // Buscador de producto
    const [codItem, setCodItem] = useState("");
    const [producto, setProducto] = useState<DuxItem | null>(null);
    const [productoError, setProductoError] = useState<string>("");
    const [isBuscando, setIsBuscando] = useState(false);

    // Listas de precios
    const [listas, setListas] = useState<any[]>([]);
    const [listasError, setListasError] = useState<string>("");
    const [isLoadingListas, setIsLoadingListas] = useState(false);
    const [listasLoaded, setListasLoaded] = useState(false);

    // Empresas
    const [empresas, setEmpresas] = useState<any[]>([]);
    const [empresasError, setEmpresasError] = useState<string>("");
    const [isLoadingEmpresas, setIsLoadingEmpresas] = useState(false);
    const [empresasLoaded, setEmpresasLoaded] = useState(false);
    const [empresaExpanded, setEmpresaExpanded] = useState<number | null>(null);
    const [sucursales, setSucursales] = useState<Record<number, any[]>>({});
    const [isLoadingSucursales, setIsLoadingSucursales] = useState<Record<number, boolean>>({});

    // Load on mount — status + rate limit
    useEffect(() => {
        fetchStatus()
            .then(setStatus)
            .catch((e) => setStatusError(e.message))
            .finally(() => setIsLoadingStatus(false));
        fetchAPI(`${API_BASE_URL}/api/dux/config/rate-limit`)
            .then((r) => r.json())
            .then((d) => { setRateLimit(Math.round(d.segundos)); setRateLimitInput(Math.round(d.segundos)); })
            .catch((e) => console.warn("No se pudo cargar rate-limit de Dux:", e));
    }, []);

    const handleCargarEmpresas = async () => {
        setIsLoadingEmpresas(true);
        setEmpresasError("");
        try {
            const data = await fetchEmpresas();
            setEmpresas(data);
            setEmpresasLoaded(true);
        } catch (e: any) {
            setEmpresasError(e.message);
        } finally {
            setIsLoadingEmpresas(false);
        }
    };

    const handleCargarListas = async () => {
        setIsLoadingListas(true);
        setListasError("");
        try {
            const data = await fetchListasPrecios();
            setListas(data);
            setListasLoaded(true);
        } catch (e: any) {
            setListasError(e.message);
        } finally {
            setIsLoadingListas(false);
        }
    };

    const handleBuscarProducto = async () => {
        if (!codItem.trim()) return;
        setIsBuscando(true);
        setProducto(null);
        setProductoError("");
        try {
            const data = await fetchProducto(codItem.trim());
            setProducto(data);
        } catch (e: any) {
            setProductoError(e.message);
        } finally {
            setIsBuscando(false);
        }
    };

    const handleToggleEmpresa = async (id: number) => {
        if (empresaExpanded === id) {
            setEmpresaExpanded(null);
            return;
        }
        setEmpresaExpanded(id);
        if (sucursales[id]) return; // ya cargadas
        setIsLoadingSucursales((prev) => ({ ...prev, [id]: true }));
        try {
            const data = await fetchSucursales(id);
            setSucursales((prev) => ({ ...prev, [id]: data }));
        } catch {
            setSucursales((prev) => ({ ...prev, [id]: [] }));
        } finally {
            setIsLoadingSucursales((prev) => ({ ...prev, [id]: false }));
        }
    };

    return !isAdmin ? (
        <main className="p-6 bg-gray-50 min-h-0 flex flex-col overflow-hidden dark:bg-slate-900">
            <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-amber-800 dark:border-amber-800 dark:bg-amber-500/10 dark:text-amber-200">
                Solo los administradores pueden acceder a Integraciones.
            </div>
        </main>
    ) : (
        <main className="flex flex-col gap-6 bg-[radial-gradient(circle_at_top_left,_rgba(59,130,246,0.08),_transparent_28%),linear-gradient(180deg,_#f8fafc_0%,_#f1f5f9_100%)] p-6 xl:p-8 dark:bg-[radial-gradient(circle_at_top_left,_rgba(59,130,246,0.12),_transparent_28%),linear-gradient(180deg,_#0f172a_0%,_#020617_100%)]">
            {/* Header */}
            <section className={`${shellCardClassName} overflow-hidden`}>
                <div className="flex flex-col gap-5 p-6 lg:flex-row lg:items-start lg:justify-between">
                    <div className="max-w-3xl">
                        <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-blue-200 bg-blue-50 px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.12em] text-blue-700 dark:border-blue-500/30 dark:bg-blue-500/15 dark:text-blue-200">
                            <ServerStackIcon className="h-3.5 w-3.5" />
                            Integracion DUX
                        </div>
                        <h1 className="flex items-center gap-3 text-3xl font-bold text-slate-800 dark:text-slate-100">
                            <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-200">
                                <ServerStackIcon className="h-7 w-7" />
                            </span>{" "}
                            DUX ERP
                        </h1>
                        <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-500 dark:text-slate-400">
                            Consola de consulta y sincronización con DUX. Desde acá podemos descargar catálogo, importarlo al sistema y revisar datos maestros sin salir de la app.
                        </p>
                    </div>

                    <div className="grid gap-3 sm:grid-cols-3 lg:min-w-[24rem]">
                        <div className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 dark:border-emerald-500/30 dark:bg-emerald-500/10">
                            <div className="text-[11px] font-semibold uppercase tracking-[0.12em] text-emerald-700 dark:text-emerald-200">Estado</div>
                            <div className="mt-1 flex items-center gap-2 text-sm font-semibold text-emerald-800 dark:text-emerald-100">
                                <CheckCircleIcon className="h-4 w-4" />
                                {isLoadingStatus ? "Verificando..." : status?.configurado ? "Configurado" : statusError ? "Sin conexion" : "No configurado"}
                            </div>
                        </div>
                        <div className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 dark:border-slate-700 dark:bg-slate-900/70">
                            <div className="text-[11px] font-semibold uppercase tracking-[0.12em] text-slate-500 dark:text-slate-400">Servicio</div>
                            <div className="mt-1 text-sm font-semibold text-slate-800 dark:text-slate-100">{status?.servicio || "DUX ERP"}</div>
                        </div>
                        <div className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 dark:border-slate-700 dark:bg-slate-900/70">
                            <div className="text-[11px] font-semibold uppercase tracking-[0.12em] text-slate-500 dark:text-slate-400">Rate Limit</div>
                            <div className="mt-1 flex items-center gap-2">
                                <input
                                    type="number"
                                    min={5}
                                    max={60}
                                    className="w-16 rounded border border-slate-300 px-2 py-1 text-sm text-center dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100"
                                    value={rateLimitInput}
                                    onChange={(e) => setRateLimitInput(Number(e.target.value))}
                                />
                                <span className="text-xs text-slate-500 dark:text-slate-400">seg/req</span>
                                {rateLimitInput !== rateLimit && (
                                    <button
                                        className="text-xs font-semibold text-blue-600 hover:text-blue-800 dark:text-blue-400 dark:hover:text-blue-200 disabled:opacity-50"
                                        disabled={isSavingRate}
                                        onClick={async () => {
                                            setIsSavingRate(true);
                                            try {
                                                const res = await fetchAPI(`${API_BASE_URL}/api/dux/config/rate-limit?segundos=${rateLimitInput}`, { method: "PUT" });
                                                const d = await res.json();
                                                setRateLimit(Math.round(d.segundos));
                                                setRateLimitInput(Math.round(d.segundos));
                                                notificar.success(`Rate limit: ${Math.round(d.segundos)}s por request`);
                                            } catch { notificar.error("Error al guardar"); }
                                            finally { setIsSavingRate(false); }
                                        }}
                                    >
                                        {isSavingRate ? "..." : "Guardar"}
                                    </button>
                                )}
                            </div>
                        </div>
                    </div>
                </div>
            </section>

            {/* ── Buscador de producto ──────────────────────── */}
            <section className={`${shellCardClassName} p-6`}>
                <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                    <div>
                        <h2 className={sectionTitleClassName}>Buscar producto por código</h2>
                        <p className={`${sectionHintClassName} mt-1`}>Consulta puntual de un ítem en DUX para validar catálogo, precios y stock por sucursal.</p>
                    </div>
                    <div className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-medium text-slate-500 dark:border-slate-700 dark:bg-slate-900/70 dark:text-slate-300">
                        <MagnifyingGlassIcon className="h-4 w-4" />
                        Consulta directa
                    </div>
                </div>

                <div className="mb-4 flex flex-col gap-3 sm:flex-row">
                    <input
                        type="text"
                        className="min-w-0 flex-1 rounded-2xl border border-slate-300 bg-white px-4 py-3 text-sm text-slate-700 shadow-sm outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-500/20"
                        placeholder="Código de ítem DUX (ej: ABC123)"
                        value={codItem}
                        onChange={(e) => setCodItem(e.target.value)}
                        onKeyDown={(e) => e.key === "Enter" && handleBuscarProducto()}
                    />
                    <Button variant="dark" onClick={handleBuscarProducto} disabled={isBuscando || !codItem.trim()}>
                        <MagnifyingGlassIcon className="w-4 h-4" />
                        {isBuscando ? "Buscando..." : "Buscar"}
                    </Button>
                </div>

                {productoError && (
                    <div className="rounded-2xl border border-red-200 bg-red-50 p-3 text-sm text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-200">
                        {productoError}
                    </div>
                )}

                {producto && (
                    <div className="rounded-3xl border border-slate-200 bg-slate-50/80 p-4 text-sm dark:border-slate-700 dark:bg-slate-900/70">
                        <div className="grid grid-cols-2 md:grid-cols-3 gap-x-6 gap-y-2">
                            <InfoRow label="Código" value={producto.cod_item} />
                            <InfoRow label="Descripción" value={producto.item} />
                            <InfoRow label="Cod. Externo" value={producto.codigo_externo} />
                            <InfoRow label="Rubro" value={producto.rubro?.descripcion} />
                            <InfoRow label="Sub-rubro" value={producto.sub_rubro?.descripcion} />
                            <InfoRow label="Marca" value={producto.marca?.descripcion} />
                            <InfoRow label="Proveedor" value={producto.proveedor?.descripcion} />
                            <InfoRow label="Costo" value={producto.costo} />
                            <InfoRow label="IVA %" value={producto.porc_iva} />
                            <InfoRow label="Habilitado" value={producto.habilitado} />
                            <InfoRow label="Fecha creación" value={producto.fecha_creacion} />
                            {producto.codigos_barra && producto.codigos_barra.length > 0 && (
                                <InfoRow label="Códigos barra" value={producto.codigos_barra.join(", ")} />
                            )}
                        </div>

                        {producto.precios && producto.precios.length > 0 && (
                            <div className="mt-4">
                                <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-slate-400">Precios</p>
                                <div className="overflow-x-auto">
                                    <table className="w-full text-xs border-collapse">
                                        <thead>
                                            <tr className="bg-gray-100 text-left text-gray-600 dark:bg-slate-800 dark:text-slate-300">
                                                <th className="border border-gray-200 px-2 py-1.5 font-semibold dark:border-slate-700">Lista</th>
                                                <th className="border border-gray-200 px-2 py-1.5 text-right font-semibold dark:border-slate-700">Precio</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {producto.precios.map((p, i) => (
                                                <tr key={i} className="odd:bg-white even:bg-gray-50 dark:odd:bg-slate-900 dark:even:bg-slate-800">
                                                    <td className="border border-gray-200 px-2 py-1 dark:border-slate-700 dark:text-slate-200">{p.lista ?? "-"}</td>
                                                    <td className="border border-gray-200 px-2 py-1 text-right dark:border-slate-700 dark:text-slate-200">{p.precio ?? "-"}</td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        )}

                        {producto.stock && producto.stock.length > 0 && (
                            <div className="mt-4">
                                <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-slate-400">Stock por sucursal</p>
                                <div className="overflow-x-auto">
                                    <table className="w-full text-xs border-collapse">
                                        <thead>
                                            <tr className="bg-gray-100 text-left text-gray-600 dark:bg-slate-800 dark:text-slate-300">
                                                <th className="border border-gray-200 px-2 py-1.5 font-semibold dark:border-slate-700">Sucursal</th>
                                                <th className="border border-gray-200 px-2 py-1.5 text-right font-semibold dark:border-slate-700">Cantidad</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {producto.stock.map((s, i) => (
                                                <tr key={i} className="odd:bg-white even:bg-gray-50 dark:odd:bg-slate-900 dark:even:bg-slate-800">
                                                    <td className="border border-gray-200 px-2 py-1 dark:border-slate-700 dark:text-slate-200">{s.sucursal ?? "-"}</td>
                                                    <td className="border border-gray-200 px-2 py-1 text-right dark:border-slate-700 dark:text-slate-200">{s.cantidad ?? "-"}</td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        )}
                    </div>
                )}
            </section>

            {/* ── Listas de precios ─────────────────────────── */}
            <section className={`${shellCardClassName} p-6`}>
                <div className="flex items-center justify-between mb-4">
                    <div>
                        <h2 className={sectionTitleClassName}>Listas de precios</h2>
                        <p className={`${sectionHintClassName} mt-1`}>Traé y revisá las listas disponibles en DUX sin abandonar la consola.</p>
                    </div>
                    <Button variant="dark" onClick={handleCargarListas} disabled={isLoadingListas}>
                        <ArrowPathIcon className={`w-4 h-4 ${isLoadingListas ? "animate-spin" : ""}`} />
                        {isLoadingListas ? "Cargando..." : listasLoaded ? "Actualizar" : "Obtener listas"}
                    </Button>
                </div>
                {listasError && (
                    <div className="rounded-2xl border border-red-200 bg-red-50 p-3 text-sm text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-200">{listasError}</div>
                )}
                {listasLoaded && !isLoadingListas && !listasError && listas.length === 0 && (
                    <div className="rounded-2xl border border-dashed border-slate-300 bg-slate-50 px-4 py-8 text-center text-sm text-slate-400 dark:border-slate-700 dark:bg-slate-900/70 dark:text-slate-500">
                        No hay listas disponibles.
                    </div>
                )}
                {listas.length > 0 && (
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm border-collapse">
                            <thead>
                                <tr className="bg-gray-100 text-left text-gray-600 dark:bg-slate-800 dark:text-slate-300">
                                    {Object.keys(listas[0]).map((key) => (
                                        <th key={key} className="border border-gray-200 px-3 py-2 font-semibold capitalize dark:border-slate-700">
                                            {key.replace(/_/g, " ")}
                                        </th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {listas.map((lista, i) => (
                                    <tr key={i} className="odd:bg-white even:bg-gray-50 dark:odd:bg-slate-900 dark:even:bg-slate-800">
                                        {Object.values(lista).map((val: any, j) => (
                                            <td key={j} className="border border-gray-200 px-3 py-1.5 text-gray-700 dark:border-slate-700 dark:text-slate-200">
                                                {val !== null && val !== undefined ? String(val) : "—"}
                                            </td>
                                        ))}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>

            {/* ── Empresas y sucursales ─────────────────────── */}
            <section className={`${shellCardClassName} p-6`}>
                <div className="flex items-center justify-between mb-4">
                    <div>
                        <h2 className={sectionTitleClassName}>Empresas</h2>
                        <p className={`${sectionHintClassName} mt-1`}>Explorá empresas y sucursales configuradas en DUX para validar estructura y cobertura.</p>
                    </div>
                    <Button onClick={handleCargarEmpresas} disabled={isLoadingEmpresas} variant="dark">
                        <ArrowPathIcon className={`w-4 h-4 ${isLoadingEmpresas ? "animate-spin" : ""}`} />
                        {isLoadingEmpresas ? "Cargando..." : empresasLoaded ? "Actualizar" : "Obtener empresas"}
                    </Button>
                </div>
                {isLoadingEmpresas && <span className="text-sm text-gray-400 dark:text-slate-500">Cargando...</span>}
                {empresasError && (
                    <div className="rounded-2xl border border-red-200 bg-red-50 p-3 text-sm text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-200">{empresasError}</div>
                )}
                {empresasLoaded && !isLoadingEmpresas && !empresasError && empresas.length === 0 && (
                    <div className="rounded-2xl border border-dashed border-slate-300 bg-slate-50 px-4 py-8 text-center text-sm text-slate-400 dark:border-slate-700 dark:bg-slate-900/70 dark:text-slate-500">
                        No hay empresas disponibles.
                    </div>
                )}
                {empresas.length > 0 && (
                    <div className="flex flex-col gap-2">
                        {empresas.map((empresa) => {
                            const id = empresa.id ?? empresa.id_empresa ?? empresa.idEmpresa;
                            const nombre = empresa.nombre ?? empresa.descripcion ?? empresa.razon_social ?? String(id);
                            const isOpen = empresaExpanded === id;
                            const empresaSucursales = sucursales[id] ?? [];
                            const loadingSuc = isLoadingSucursales[id] ?? false;

                            return (
                                <div key={id} className="overflow-hidden rounded-2xl border border-slate-200 dark:border-slate-700">
                                    <button
                                        className="flex w-full items-center justify-between bg-slate-50 px-4 py-3 text-sm font-medium text-slate-800 transition-colors hover:bg-slate-100 dark:bg-slate-900/70 dark:text-slate-100 dark:hover:bg-slate-900"
                                        onClick={() => handleToggleEmpresa(id)}
                                    >
                                        <span className="flex items-center gap-2">
                                            <span className="text-xs font-mono text-slate-400 dark:text-slate-500">#{id}</span>
                                            {nombre}
                                        </span>
                                        <span className="text-xs text-slate-400 dark:text-slate-500">
                                            {isOpen ? "▲ Ocultar sucursales" : "▼ Ver sucursales"}
                                        </span>
                                    </button>

                                    {isOpen && (
                                        <div className="border-t border-slate-200 bg-white px-4 py-3 dark:border-slate-700 dark:bg-slate-800">
                                            {loadingSuc && <span className="text-xs text-gray-400 dark:text-slate-500">Cargando sucursales...</span>}
                                            {!loadingSuc && empresaSucursales.length === 0 && (
                                                <span className="text-xs text-gray-400 dark:text-slate-500">Sin sucursales.</span>
                                            )}
                                            {!loadingSuc && empresaSucursales.length > 0 && (
                                                <div className="overflow-x-auto">
                                                    <table className="w-full text-xs border-collapse">
                                                        <thead>
                                                            <tr className="bg-gray-100 text-left text-gray-600 dark:bg-slate-900 dark:text-slate-300">
                                                                {Object.keys(empresaSucursales[0]).map((key) => (
                                                                    <th key={key} className="border border-gray-200 px-2 py-1.5 font-semibold capitalize dark:border-slate-700">
                                                                        {key.replace(/_/g, " ")}
                                                                    </th>
                                                                ))}
                                                            </tr>
                                                        </thead>
                                                        <tbody>
                                                            {empresaSucursales.map((suc, i) => (
                                                                <tr key={i} className="odd:bg-white even:bg-gray-50 dark:odd:bg-slate-800 dark:even:bg-slate-900">
                                                                    {Object.values(suc).map((val: any, j) => (
                                                                        <td key={j} className="border border-gray-200 px-2 py-1 text-gray-700 dark:border-slate-700 dark:text-slate-200">
                                                                            {val !== null && val !== undefined ? String(val) : "—"}
                                                                        </td>
                                                                    ))}
                                                                </tr>
                                                            ))}
                                                        </tbody>
                                                    </table>
                                                </div>
                                            )}
                                        </div>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                )}
            </section>
        </main>
    );
}

// ── Helper component ──────────────────────────────────────────────────────────

function InfoRow({ label, value }: { label: string; value?: string | null }) {
    if (!value) return null;
    return (
        <div className="flex flex-col rounded-2xl border border-slate-200 bg-white px-3 py-2 shadow-sm dark:border-slate-700 dark:bg-slate-800 dark:shadow-none">
            <span className="text-[10px] font-semibold uppercase tracking-wide text-slate-400 dark:text-slate-500">{label}</span>
            <span className="text-sm text-slate-800 dark:text-slate-100">{value}</span>
        </div>
    );
}

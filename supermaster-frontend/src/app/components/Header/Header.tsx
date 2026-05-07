"use client";
import { useState, useRef, useEffect, useCallback } from "react";
import Link from "next/link";
import { useRouter, usePathname } from "next/navigation";
import { Bars3Icon, MagnifyingGlassIcon, XMarkIcon, ArrowRightOnRectangleIcon, UserCircleIcon, Squares2X2Icon, BoltIcon } from "@heroicons/react/24/outline";
import { useSidebar } from "../../context/SidebarContext";
import { useAuth } from "../../context/AuthContext";
import { API_BASE_URL } from "../../config/runtime";
import { fetchAPI } from "../../utils/fetchAPI";
import { ThemeToggle } from "../ThemeToggle/ThemeToggle";
import MegaMenu from "./MegaMenu";
import RecalculoPendienteBanner from "../RecalculoPendienteBanner/RecalculoPendienteBanner";
import { getRoleBadgeClasses } from "../../utils/roleBadge";
import { useProcesoActivo, getRutaProceso } from "../../context/ProcesoActivoContext";
import NotificacionesDropdown from "./NotificacionesDropdown";

interface ProductoResult {
    id: number;
    sku: string;
    descripcion: string;
    tituloWeb?: string;
    mla?: string;
    codExt?: string;
}

function useDebounce(value: string, delay: number) {
    const [debounced, setDebounced] = useState(value);
    useEffect(() => {
        const t = setTimeout(() => setDebounced(value), delay);
        return () => clearTimeout(t);
    }, [value, delay]);
    return debounced;
}

const Header = () => {
    const { toggle } = useSidebar();
    const { usuario, logout } = useAuth();
    const router = useRouter();
    const pathname = usePathname();
    const { procesos, hayProcesoActivo } = useProcesoActivo();

    // Tiempo transcurrido por proceso activo: el banner del header no muestra avance
    // del backend, así que mostramos elapsed para que el usuario sepa que el proceso
    // sigue vivo y no se quedó trabado.
    const procesoStartTimesRef = useRef<Map<string, number>>(new Map());
    const [tickNow, setTickNow] = useState(() => Date.now());
    useEffect(() => {
        const activos = new Set(procesos.map((p) => p.proceso));
        for (const p of procesos) {
            if (!procesoStartTimesRef.current.has(p.proceso)) {
                procesoStartTimesRef.current.set(p.proceso, Date.now());
            }
        }
        for (const id of Array.from(procesoStartTimesRef.current.keys())) {
            if (!activos.has(id)) procesoStartTimesRef.current.delete(id);
        }
    }, [procesos]);
    useEffect(() => {
        if (!hayProcesoActivo) return;
        const t = setInterval(() => setTickNow(Date.now()), 1000);
        return () => clearInterval(t);
    }, [hayProcesoActivo]);
    const formatElapsed = (start: number) => {
        const sec = Math.max(0, Math.floor((tickNow - start) / 1000));
        if (sec < 60) return `${sec}s`;
        const m = Math.floor(sec / 60);
        const s = sec % 60;
        return `${m}m ${s.toString().padStart(2, "0")}s`;
    };

    const [megaMenuOpen, setMegaMenuOpen] = useState(false);
    const megaMenuBtnRef = useRef<HTMLButtonElement>(null);
    const closeMegaMenu = useCallback(() => setMegaMenuOpen(false), []);

    const [query, setQuery] = useState("");
    const [results, setResults] = useState<ProductoResult[]>([]);
    const [isOpen, setIsOpen] = useState(false);
    const [isLoading, setIsLoading] = useState(false);
    const debouncedQuery = useDebounce(query, 300);
    const inputRef = useRef<HTMLInputElement>(null);
    const containerRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (debouncedQuery.trim().length < 2) {
            setResults([]);
            setIsOpen(false);
            return;
        }
        setIsLoading(true);
        fetchAPI(`${API_BASE_URL}/api/productos?search=${encodeURIComponent(debouncedQuery)}&page=0&size=6&sort=id,desc`)
            .then((r) => r.json())
            .then((data) => {
                const items: ProductoResult[] = (data?.content ?? []).map((p: any) => ({
                    id: p.id,
                    sku: p.sku,
                    descripcion: p.descripcion ?? p.tituloWeb ?? "",
                    tituloWeb: p.tituloWeb,
                    mla: p.mlaNombre,
                    codExt: p.codExt,
                }));
                setResults(items);
                setIsOpen(items.length > 0);
            })
            .catch(() => setResults([]))
            .finally(() => setIsLoading(false));
    }, [debouncedQuery]);

    useEffect(() => {
        const handler = (e: MouseEvent) => {
            if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
                setIsOpen(false);
            }
        };
        document.addEventListener("mousedown", handler);
        return () => document.removeEventListener("mousedown", handler);
    }, []);

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        if (!query.trim()) return;
        setIsOpen(false);
        router.push(`/productos?q=${encodeURIComponent(query.trim())}`);
    };

    const handleSelect = (producto: ProductoResult) => {
        setQuery(producto.sku);
        setIsOpen(false);
        router.push(`/productos?q=${encodeURIComponent(producto.sku)}`);
    };

    const handleClear = () => {
        setQuery("");
        setResults([]);
        setIsOpen(false);
        if (pathname === "/productos") {
            router.push("/productos");
        }
        inputRef.current?.focus();
    };

    return (
        <header className="bg-gradient-to-r from-slate-50 to-gray-100 dark:from-slate-900 dark:to-slate-800 flex justify-between items-center fixed w-full shadow-md dark:shadow-black/30 z-50 border-b border-gray-200/80 dark:border-slate-700/50">
            <div className="flex items-center gap-1">
                <button
                    onClick={toggle}
                    className="p-2 ml-1 rounded hover:bg-gray-100 dark:hover:bg-slate-700 transition-colors"
                    title="Mostrar/ocultar menú (Ctrl+B)"
                >
                    <Bars3Icon className="size-5 text-gray-500 dark:text-slate-400" />
                </button>
                <Link
                    href={"/"}
                    className="h-13 p-2 pl-2 flex items-center gap-2.5 max-w-fit">
                    <p className="text-lg tracking-tight select-none relative inline-block">
                        <span className="font-extrabold text-gray-800 dark:text-white">Super</span>
                        <span className="font-extrabold text-blue-600 dark:text-blue-400">Master</span>
                        <span className="header-shimmer-text" aria-hidden="true">
                            <span className="font-extrabold">Super</span>
                            <span className="font-extrabold">Master</span>
                        </span>
                    </p>
                </Link>
                <button
                    ref={megaMenuBtnRef}
                    onClick={() => setMegaMenuOpen((v) => !v)}
                    className={`flex items-center gap-2 rounded-xl border px-3 py-2 text-sm font-medium shadow-sm transition-all ${
                        megaMenuOpen
                            ? "border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-500/40 dark:bg-blue-900/30 dark:text-blue-300"
                            : "border-transparent bg-white/70 text-gray-500 hover:border-gray-200 hover:bg-white hover:text-gray-700 dark:bg-slate-800/50 dark:text-slate-400 dark:hover:border-slate-600 dark:hover:bg-slate-800 dark:hover:text-slate-200"
                    }`}
                    title="Menú de navegación"
                    aria-expanded={megaMenuOpen}
                    aria-haspopup="dialog"
                >
                    <Squares2X2Icon className="size-5" />
                    <span className="hidden sm:inline">Secciones</span>
                </button>
            </div>

            {/* ── Buscador de productos ── */}
            <div ref={containerRef} className="relative flex-1 max-w-md mx-4">
                <form onSubmit={handleSubmit}>
                    <div className="relative flex items-center">
                        <MagnifyingGlassIcon className="absolute left-3 size-4 text-gray-400 dark:text-slate-400 pointer-events-none" />
                        <input
                            ref={inputRef}
                            type="text"
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                            onFocus={() => results.length > 0 && setIsOpen(true)}
                            placeholder="Buscar producto por SKU, MLA, cód. externo o descripción..."
                            className="w-full pl-9 pr-8 py-1.5 text-sm border border-gray-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 text-gray-800 dark:text-slate-100 placeholder:text-gray-400 dark:placeholder:text-slate-500 shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-400 focus:border-blue-400 dark:focus:bg-slate-700 transition-colors"
                        />
                        {query && (
                            <button
                                type="button"
                                onClick={handleClear}
                                className="absolute right-2 p-0.5 rounded hover:bg-gray-200 dark:hover:bg-slate-600 transition-colors"
                            >
                                <XMarkIcon className="size-3.5 text-gray-400 dark:text-slate-400" />
                            </button>
                        )}
                    </div>
                </form>

                {/* Dropdown de resultados */}
                {isOpen && (
                    <div className="absolute top-full mt-1 left-0 right-0 bg-white dark:bg-slate-800 rounded-lg shadow-lg border border-gray-200 dark:border-slate-700 overflow-hidden z-50">
                        {isLoading && (
                            <div className="px-4 py-2.5 text-xs text-gray-400 dark:text-slate-400">Buscando...</div>
                        )}
                        {!isLoading && results.map((p) => (
                            <button
                                key={p.id}
                                type="button"
                                onClick={() => handleSelect(p)}
                                className="w-full flex items-center gap-3 px-4 py-2.5 hover:bg-blue-50 dark:hover:bg-slate-700 text-left transition-colors border-b border-gray-100 dark:border-slate-700 last:border-0"
                            >
                                <span className="text-xs font-mono font-bold text-blue-700 dark:text-blue-400 shrink-0 bg-blue-50 dark:bg-blue-900/40 px-1.5 py-0.5 rounded">
                                    {p.sku}
                                </span>
                                <div className="flex-1 min-w-0">
                                    <span className="text-sm text-gray-700 dark:text-slate-200 truncate block">{p.descripcion || p.tituloWeb || "-"}</span>
                                    {(p.mla || p.codExt) && (
                                        <span className="text-xs text-gray-400 dark:text-slate-500 flex gap-2 mt-0.5">
                                            {p.mla && <span>MLA: {p.mla}</span>}
                                            {p.codExt && <span>Ext: {p.codExt}</span>}
                                        </span>
                                    )}
                                </div>
                            </button>
                        ))}
                    </div>
                )}
            </div>

            <div className="flex items-center gap-2 pr-2">
                {/* Banner global de cambios pendientes de recálculo */}
                <RecalculoPendienteBanner />
                {/* Indicadores de procesos activos */}
                {hayProcesoActivo && procesos.map((p) => {
                    const start = procesoStartTimesRef.current.get(p.proceso);
                    const elapsed = start ? formatElapsed(start) : null;
                    const total = p.total ?? 0;
                    const procesados = p.procesados ?? 0;
                    const errores = p.errores ?? 0;
                    const mostrarProgreso = total > 0;
                    const pct = mostrarProgreso ? Math.min(100, Math.round((procesados / total) * 100)) : 0;
                    const tooltipParts = [
                        `En ejecución${elapsed ? ` — ${elapsed}` : ""}`,
                        mostrarProgreso ? `${procesados}/${total} (${pct}%)` : null,
                        errores > 0 ? `${errores} errores` : null,
                        p.mensaje || null,
                    ].filter(Boolean);
                    return (
                        <Link
                            key={p.proceso}
                            href={getRutaProceso(p.proceso) || "#"}
                            className="group relative flex items-center gap-2 px-3 py-1.5 rounded-full border border-amber-300/60 bg-gradient-to-r from-amber-50 to-yellow-50 hover:from-amber-100 hover:to-yellow-100 dark:border-amber-600/40 dark:from-amber-900/30 dark:to-yellow-900/20 dark:hover:from-amber-900/50 dark:hover:to-yellow-900/40 shadow-sm hover:shadow transition-all cursor-pointer overflow-hidden"
                            title={tooltipParts.join(" · ") + ". Click para ver detalle."}
                        >
                            {/* Barra de progreso de fondo (solo si hay total) */}
                            {mostrarProgreso && (
                                <span
                                    aria-hidden
                                    className="absolute inset-y-0 left-0 bg-amber-300/40 dark:bg-amber-500/20 transition-[width] duration-500 ease-out"
                                    style={{ width: `${pct}%` }}
                                />
                            )}
                            <span className="relative flex h-2 w-2 shrink-0">
                                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-amber-400 opacity-75" />
                                <span className="relative inline-flex rounded-full h-2 w-2 bg-amber-500 dark:bg-amber-400" />
                            </span>
                            <span className="relative text-xs font-semibold text-amber-700 dark:text-amber-300 whitespace-nowrap">
                                {p.descripcion}
                            </span>
                            {mostrarProgreso && (
                                <span className="relative text-[10px] font-mono font-bold text-amber-800 dark:text-amber-200 bg-white/60 dark:bg-amber-500/20 rounded px-1.5 py-0.5 tabular-nums">
                                    {procesados.toLocaleString("es-AR")}/{total.toLocaleString("es-AR")}
                                    <span className="ml-1 opacity-70">{pct}%</span>
                                </span>
                            )}
                            {errores > 0 && (
                                <span
                                    className="relative inline-flex items-center gap-0.5 text-[10px] font-mono font-bold text-red-700 dark:text-red-300 bg-red-100/80 dark:bg-red-500/20 rounded px-1.5 py-0.5 tabular-nums"
                                    title={`${errores} ${errores === 1 ? "error" : "errores"} hasta ahora`}
                                >
                                    ⚠ {errores}
                                </span>
                            )}
                            {elapsed && (
                                <span
                                    className="relative text-[10px] font-mono font-bold text-amber-800 dark:text-amber-200 bg-amber-100/70 dark:bg-amber-500/20 rounded px-1.5 py-0.5 tabular-nums"
                                    title="Tiempo transcurrido desde que empezó este proceso"
                                >
                                    {elapsed}
                                </span>
                            )}
                            <BoltIcon className="relative w-3.5 h-3.5 text-amber-500 dark:text-amber-400 group-hover:text-amber-600 dark:group-hover:text-amber-300 transition-colors" />
                        </Link>
                    );
                })}
                <img
                    className="h-6 object-contain"
                    src="/logos/linea-ge.webp"
                    alt="Logo Linea GE"
                />
                <img
                    className="h-9 object-contain"
                    src="/logos/kt-logo.webp"
                    alt="Kitchen Tools"
                />
                {/* Notificaciones */}
                <NotificacionesDropdown />
                {/* Info de usuario */}
                <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg border border-gray-200 dark:border-slate-600 bg-gray-50 dark:bg-slate-800">
                    <span className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold text-white shrink-0 ${
                        ["bg-blue-500","bg-emerald-500","bg-violet-500","bg-amber-500","bg-rose-500","bg-cyan-500","bg-indigo-500","bg-teal-500"][
                            (usuario.username || "").split("").reduce((a, c) => a + c.charCodeAt(0), 0) % 8
                        ]
                    }`}>
                        {usuario.nombreCompleto?.charAt(0)?.toUpperCase() || usuario.username?.charAt(0)?.toUpperCase() || "?"}
                    </span>
                    <div className="flex items-center gap-2">
                        <span className="text-sm font-medium leading-none text-gray-700 dark:text-slate-200">
                            {usuario.nombreCompleto}
                        </span>
                        <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-bold uppercase tracking-[0.14em] ${getRoleBadgeClasses(usuario.rol)}`}>
                            {usuario.rol}
                        </span>
                    </div>
                </div>
                {/* Cerrar sesión */}
                <button
                    onClick={logout}
                    title="Cerrar sesión"
                    className="p-1.5 rounded-lg border border-red-200 dark:border-red-800 text-red-500 dark:text-red-400 bg-red-50 dark:bg-red-900/20 hover:bg-red-100 dark:hover:bg-red-900/40 hover:border-red-300 dark:hover:border-red-700 transition-colors"
                >
                    <ArrowRightOnRectangleIcon className="size-4" />
                </button>
                <ThemeToggle />
            </div>

            <MegaMenu isOpen={megaMenuOpen} onClose={closeMegaMenu} toggleRef={megaMenuBtnRef} />
        </header>
    );
};

export default Header;

"use client";

import { useRef, useState, useEffect } from "react";
import { BellIcon, CheckIcon, TrashIcon, ChevronDownIcon, ClipboardDocumentIcon } from "@heroicons/react/24/outline";
import { BellAlertIcon } from "@heroicons/react/24/solid";
import { useNotificaciones, type Notificacion } from "../../context/NotificacionContext";
import { toast } from "sonner";

function tiempoRelativo(iso: string): string {
    const diff = Date.now() - new Date(iso).getTime();
    const segs = Math.floor(diff / 1000);
    if (segs < 60) return "ahora";
    const mins = Math.floor(segs / 60);
    if (mins < 60) return `hace ${mins}m`;
    const horas = Math.floor(mins / 60);
    if (horas < 24) return `hace ${horas}h`;
    const dias = Math.floor(horas / 24);
    return `hace ${dias}d`;
}

const TIPO_CONFIG: Record<string, { color: string; dot: string }> = {
    success: { color: "text-green-600 dark:text-green-400", dot: "bg-green-500" },
    error: { color: "text-red-600 dark:text-red-400", dot: "bg-red-500" },
    info: { color: "text-blue-600 dark:text-blue-400", dot: "bg-blue-500" },
    warning: { color: "text-amber-600 dark:text-amber-400", dot: "bg-amber-500" },
};

function NotificacionItem({ notif, onMarcarLeida }: { notif: Notificacion; onMarcarLeida: () => void }) {
    const config = TIPO_CONFIG[notif.tipo] || TIPO_CONFIG.info;
    const tieneDetalle = !!(notif.detalle && notif.detalle.trim().length > 0);
    const [detalleAbierto, setDetalleAbierto] = useState(false);

    const handleClick = () => {
        if (!notif.leida) onMarcarLeida();
        if (tieneDetalle) setDetalleAbierto((v) => !v);
    };

    const handleCopiar = (e: React.MouseEvent) => {
        e.stopPropagation();
        if (!notif.detalle) return;
        navigator.clipboard.writeText(notif.detalle)
            .then(() => toast.success("Detalle copiado al portapapeles"))
            .catch(() => toast.error("No se pudo copiar al portapapeles"));
    };

    return (
        <div className={`${!notif.leida ? "bg-blue-50/50 dark:bg-blue-500/5" : ""}`}>
            <button
                onClick={handleClick}
                className="w-full text-left px-3 py-2.5 flex gap-3 items-start hover:bg-gray-50 dark:hover:bg-slate-700/50 transition-colors"
            >
                <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${config.dot} ${!notif.leida ? "opacity-100" : "opacity-30"}`} />
                <div className="flex-1 min-w-0">
                    <div className="flex items-start gap-2">
                        <p className={`flex-1 text-sm leading-snug ${!notif.leida ? "font-medium text-gray-900 dark:text-slate-100" : "text-gray-600 dark:text-slate-400"}`}>
                            {notif.mensaje}
                        </p>
                        {tieneDetalle && (
                            <ChevronDownIcon className={`mt-0.5 h-4 w-4 shrink-0 text-gray-400 dark:text-slate-500 transition-transform ${detalleAbierto ? "rotate-180" : ""}`} />
                        )}
                    </div>
                    <div className="mt-0.5 flex items-center gap-2 text-[11px] text-gray-400 dark:text-slate-500">
                        <span>{tiempoRelativo(notif.timestamp)}</span>
                        <span>·</span>
                        <span>{notif.usuario}</span>
                    </div>
                </div>
            </button>
            {tieneDetalle && detalleAbierto && (
                <div className="px-3 pb-2.5 pl-8">
                    <div className="rounded-md border border-gray-200 dark:border-slate-600 bg-gray-50 dark:bg-slate-900/40 overflow-hidden">
                        <div className="flex items-center justify-between px-2 py-1 border-b border-gray-200 dark:border-slate-700">
                            <span className="text-[10px] uppercase tracking-wide font-semibold text-gray-500 dark:text-slate-400">Detalle</span>
                            <button
                                onClick={handleCopiar}
                                className="flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-medium text-blue-600 hover:bg-blue-50 dark:text-blue-400 dark:hover:bg-blue-500/10 transition-colors"
                                title="Copiar al portapapeles"
                            >
                                <ClipboardDocumentIcon className="w-3.5 h-3.5" />
                                Copiar
                            </button>
                        </div>
                        <pre className="px-2 py-1.5 max-h-48 overflow-auto text-[11px] font-mono text-gray-700 dark:text-slate-300 whitespace-pre-wrap break-all">
                            {notif.detalle}
                        </pre>
                    </div>
                </div>
            )}
        </div>
    );
}

export default function NotificacionesDropdown() {
    const { notificaciones, cantidadNoLeidas, marcarLeida, marcarTodasLeidas, limpiar } = useNotificaciones();
    const [isOpen, setIsOpen] = useState(false);
    const containerRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const handler = (e: MouseEvent) => {
            if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
                setIsOpen(false);
            }
        };
        document.addEventListener("mousedown", handler);
        return () => document.removeEventListener("mousedown", handler);
    }, []);

    return (
        <div ref={containerRef} className="relative">
            <button
                onClick={() => setIsOpen(!isOpen)}
                className="relative p-1.5 rounded-lg text-gray-500 hover:text-gray-700 hover:bg-gray-100 dark:text-slate-400 dark:hover:text-slate-200 dark:hover:bg-slate-700 transition-colors"
                title="Notificaciones"
            >
                {cantidadNoLeidas > 0 ? (
                    <BellAlertIcon className="w-5 h-5" />
                ) : (
                    <BellIcon className="w-5 h-5" />
                )}
                {cantidadNoLeidas > 0 && (
                    <span className="absolute -top-0.5 -right-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-bold text-white">
                        {cantidadNoLeidas > 99 ? "99+" : cantidadNoLeidas}
                    </span>
                )}
            </button>

            {isOpen && (
                <div className="absolute right-0 top-full mt-2 w-96 rounded-xl border border-gray-200 bg-white shadow-xl dark:border-slate-700 dark:bg-slate-800 z-50 overflow-hidden">
                    {/* Header */}
                    <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100 dark:border-slate-700">
                        <h3 className="text-sm font-semibold text-gray-800 dark:text-slate-200">
                            Notificaciones
                            {cantidadNoLeidas > 0 && (
                                <span className="ml-2 text-xs font-normal text-gray-400 dark:text-slate-500">
                                    {cantidadNoLeidas} sin leer
                                </span>
                            )}
                        </h3>
                        <div className="flex gap-1">
                            {cantidadNoLeidas > 0 && (
                                <button
                                    onClick={marcarTodasLeidas}
                                    className="p-1 rounded text-gray-400 hover:text-blue-600 dark:text-slate-500 dark:hover:text-blue-400 transition-colors"
                                    title="Marcar todas como leidas"
                                >
                                    <CheckIcon className="w-4 h-4" />
                                </button>
                            )}
                            {notificaciones.length > 0 && (
                                <button
                                    onClick={limpiar}
                                    className="p-1 rounded text-gray-400 hover:text-red-500 dark:text-slate-500 dark:hover:text-red-400 transition-colors"
                                    title="Limpiar todas"
                                >
                                    <TrashIcon className="w-4 h-4" />
                                </button>
                            )}
                        </div>
                    </div>

                    {/* Lista */}
                    <div className="max-h-80 overflow-y-auto divide-y divide-gray-100 dark:divide-slate-700/50">
                        {notificaciones.length === 0 ? (
                            <div className="px-4 py-8 text-center text-sm text-gray-400 dark:text-slate-500">
                                Sin notificaciones
                            </div>
                        ) : (
                            notificaciones.map((n) => (
                                <NotificacionItem
                                    key={n.id}
                                    notif={n}
                                    onMarcarLeida={() => marcarLeida(n.id)}
                                />
                            ))
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}

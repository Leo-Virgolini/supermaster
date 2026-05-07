"use client";

import { createContext, useContext, useState, useEffect, useCallback, useRef } from "react";
import { EVENT_NAME, type NotificacionTipo, type NotificacionEvent } from "../utils/notificar";

export interface Notificacion {
    id: string;
    tipo: NotificacionTipo;
    mensaje: string;
    timestamp: string;
    usuario: string;
    leida: boolean;
}

interface NotificacionContextType {
    notificaciones: Notificacion[];
    cantidadNoLeidas: number;
    agregar: (notif: Omit<Notificacion, "id" | "leida">) => void;
    marcarLeida: (id: string) => void;
    marcarTodasLeidas: () => void;
    limpiar: () => void;
}

const MAX_NOTIFICACIONES = 50;
const STORAGE_KEY = "sm-notificaciones";

function leerDeStorage(): Notificacion[] {
    if (typeof window === "undefined") return [];
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        return raw ? JSON.parse(raw) : [];
    } catch {
        return [];
    }
}

function guardarEnStorage(items: Notificacion[]) {
    if (typeof window === "undefined") return;
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
    } catch { /* storage full, ignore */ }
}

function obtenerUsuario(): string {
    if (typeof window === "undefined") return "sistema";
    try {
        const raw = localStorage.getItem("usuario");
        if (raw) {
            const u = JSON.parse(raw);
            return u.username || u.nombreCompleto || "sistema";
        }
    } catch { /* ignore */ }
    return "sistema";
}

const NotificacionContext = createContext<NotificacionContextType>({
    notificaciones: [],
    cantidadNoLeidas: 0,
    agregar: () => {},
    marcarLeida: () => {},
    marcarTodasLeidas: () => {},
    limpiar: () => {},
});

export function NotificacionProvider({ children }: { children: React.ReactNode }) {
    const [notificaciones, setNotificaciones] = useState<Notificacion[]>([]);
    const initialized = useRef(false);

    // Cargar de localStorage al montar
    useEffect(() => {
        setNotificaciones(leerDeStorage());
        initialized.current = true;
    }, []);

    // Guardar en localStorage cuando cambian
    useEffect(() => {
        if (initialized.current) {
            guardarEnStorage(notificaciones);
        }
    }, [notificaciones]);

    const agregar = useCallback((notif: Omit<Notificacion, "id" | "leida">) => {
        const nueva: Notificacion = {
            ...notif,
            id: crypto.randomUUID(),
            leida: false,
        };
        setNotificaciones((prev) => [nueva, ...prev].slice(0, MAX_NOTIFICACIONES));
    }, []);

    const marcarLeida = useCallback((id: string) => {
        setNotificaciones((prev) => prev.map((n) => (n.id === id ? { ...n, leida: true } : n)));
    }, []);

    const marcarTodasLeidas = useCallback(() => {
        setNotificaciones((prev) => prev.map((n) => ({ ...n, leida: true })));
    }, []);

    const limpiar = useCallback(() => {
        setNotificaciones([]);
    }, []);

    // Escuchar eventos de notificación
    useEffect(() => {
        const handler = (e: Event) => {
            const detail = (e as CustomEvent<NotificacionEvent>).detail;
            agregar({
                tipo: detail.tipo,
                mensaje: detail.mensaje,
                timestamp: new Date().toISOString(),
                usuario: obtenerUsuario(),
            });
        };

        window.addEventListener(EVENT_NAME, handler);
        return () => window.removeEventListener(EVENT_NAME, handler);
    }, [agregar]);

    const cantidadNoLeidas = notificaciones.filter((n) => !n.leida).length;

    return (
        <NotificacionContext.Provider value={{ notificaciones, cantidadNoLeidas, agregar, marcarLeida, marcarTodasLeidas, limpiar }}>
            {children}
        </NotificacionContext.Provider>
    );
}

export function useNotificaciones() {
    return useContext(NotificacionContext);
}

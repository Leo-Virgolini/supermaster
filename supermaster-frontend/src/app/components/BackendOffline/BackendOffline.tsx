"use client";

import { useEffect, useState } from "react";
import { API_BASE_URL } from "../../config/runtime";

export default function BackendOffline() {
    const [offline, setOffline] = useState(false);
    const [retrying, setRetrying] = useState(false);
    const [stillDown, setStillDown] = useState(false);

    useEffect(() => {
        const handler = () => setOffline(true);
        window.addEventListener("backendOffline", handler);
        return () => window.removeEventListener("backendOffline", handler);
    }, []);

    if (!offline) return null;

    const handleRetry = async () => {
        setRetrying(true);
        setStillDown(false);
        try {
            // Intenta conectar con el backend con un timeout de 4s
            const controller = new AbortController();
            const timeout = setTimeout(() => controller.abort(), 4000);
            const res = await fetch(`${API_BASE_URL}/api/auth/login`, {
                method: "OPTIONS",
                signal: controller.signal,
                cache: "no-store",
            }).catch(() => null);
            clearTimeout(timeout);
            // Cualquier respuesta (incluso 4xx) indica que el backend está vivo
            if (res !== null) {
                setOffline(false);
                window.location.reload();
                return;
            }
        } catch {
            // sigue offline
        }
        setStillDown(true);
        setRetrying(false);
    };

    return (
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-gray-950/80">
            <div className="bg-white dark:bg-slate-900 rounded-2xl shadow-2xl border border-gray-200 dark:border-slate-700 p-10 max-w-md w-full mx-4 flex flex-col items-center gap-6 text-center">
                {/* Icono */}
                <div className="w-16 h-16 rounded-full bg-red-100 dark:bg-red-900/30 flex items-center justify-center">
                    <svg className="w-8 h-8 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                        <path strokeLinecap="round" strokeLinejoin="round"
                            d="M5.636 5.636a9 9 0 1 0 12.728 12.728M5.636 5.636A9 9 0 0 1 18.364 18.364M5.636 5.636 18.364 18.364" />
                    </svg>
                </div>

                {/* Título */}
                <div>
                    <h2 className="text-xl font-bold text-gray-900 dark:text-white mb-1">
                        Sin conexión al servidor
                    </h2>
                    <p className="text-sm text-gray-500 dark:text-slate-400">
                        No se pudo comunicar con el backend.<br />
                        Verificá que el servidor esté en línea.
                    </p>
                </div>

                {/* Info de URL */}
                <div className="w-full bg-gray-50 dark:bg-slate-800 rounded-lg px-4 py-2 text-xs text-gray-400 dark:text-slate-500 font-mono break-all">
                    {API_BASE_URL}
                </div>

                {stillDown && (
                    <p className="text-xs text-red-500 -mt-2">
                        El servidor sigue sin responder.
                    </p>
                )}

                {/* Botón reintentar */}
                <button
                    onClick={handleRetry}
                    disabled={retrying}
                    className="w-full py-2.5 rounded-lg bg-indigo-600 hover:bg-indigo-700 disabled:opacity-60 text-white font-semibold text-sm transition"
                >
                    {retrying ? "Verificando..." : "Reintentar conexión"}
                </button>
            </div>
        </div>
    );
}

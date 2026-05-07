"use client";

import { useState } from "react";
import { ThemeToggle } from "../ThemeToggle/ThemeToggle";

interface Props {
    onLogin: (username: string, password: string) => Promise<void>;
}

export default function LoginScreen({ onLogin }: Props) {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);
    const [showPassword, setShowPassword] = useState(false);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError("");
        setLoading(true);
        try {
            await onLogin(username, password);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Error al iniciar sesión");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 via-blue-50/30 to-slate-100 dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 p-4 relative">
            <div className="absolute top-4 right-4">
                <ThemeToggle />
            </div>
            <div className="w-full max-w-[400px]">
                {/* Card principal */}
                <div className="bg-white dark:bg-slate-900 rounded-3xl shadow-2xl shadow-gray-300/40 dark:shadow-black/40 border border-gray-100 dark:border-slate-800 overflow-hidden">
                    {/* Header con gradiente */}
                    <div className="relative bg-gradient-to-br from-blue-600 via-blue-700 to-indigo-800 px-8 pt-10 pb-12 text-center overflow-hidden">
                        {/* Decoraciones */}
                        <div className="absolute top-0 left-0 w-full h-full opacity-10">
                            <div className="absolute -top-10 -right-10 w-40 h-40 rounded-full bg-white" />
                            <div className="absolute -bottom-8 -left-8 w-32 h-32 rounded-full bg-white" />
                        </div>

                        {/* Logos */}
                        <div className="relative flex items-center justify-center gap-4 mb-5">
                            <div className="w-12 h-12 rounded-xl bg-white/95 shadow-md flex items-center justify-center p-1.5">
                                <img src="/logos/linea-ge.webp" alt="Linea GE" className="w-full h-full object-contain" />
                            </div>
                            <div className="w-12 h-12 rounded-xl bg-white/95 shadow-md flex items-center justify-center p-1.5">
                                <img src="/logos/kt-logo.webp" alt="Kitchen Tools" className="w-full h-full object-contain" />
                            </div>
                        </div>

                        <div className="relative inline-block bg-white rounded-xl px-4 py-1.5 shadow-md dark:bg-slate-800">
                            <p className="text-3xl tracking-tight select-none relative inline-block">
                                <span className="font-extrabold text-gray-800 dark:text-white">Super</span>
                                <span className="font-extrabold text-blue-600 dark:text-blue-400">Master</span>
                                <span className="header-shimmer-text" aria-hidden="true">
                                    <span className="font-extrabold">Super</span>
                                    <span className="font-extrabold">Master</span>
                                </span>
                            </p>
                        </div>
                        <p className="relative text-blue-200/70 text-xs mt-1.5 tracking-wide">
                            Sistema de gestión integral
                        </p>
                    </div>

                    {/* Formulario */}
                    <form onSubmit={handleSubmit} className="px-8 pt-6 pb-8 flex flex-col gap-5">

                        {error && (
                            <div role="alert" className="bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 text-sm rounded-xl px-4 py-3 border border-red-200 dark:border-red-800 flex items-center gap-2.5">
                                <svg className="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" /></svg>
                                {error}
                            </div>
                        )}

                        <div className="flex flex-col gap-1.5">
                            <label className="text-[11px] font-semibold text-gray-400 dark:text-slate-500 uppercase tracking-wider">
                                Usuario
                            </label>
                            <div className="relative">
                                <svg className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400 dark:text-slate-500" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z" /></svg>
                                <input
                                    type="text"
                                    value={username}
                                    onChange={e => setUsername(e.target.value)}
                                    className="w-full border border-gray-200 dark:border-slate-700 rounded-xl pl-10 pr-4 py-3 text-sm bg-gray-50/70 dark:bg-slate-800/70 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-blue-400 focus:bg-white dark:focus:bg-slate-800 transition-all"
                                    placeholder="Ingresa tu usuario"
                                    autoComplete="username"
                                    autoFocus
                                    required
                                />
                            </div>
                        </div>

                        <div className="flex flex-col gap-1.5">
                            <label className="text-[11px] font-semibold text-gray-400 dark:text-slate-500 uppercase tracking-wider">
                                Contraseña
                            </label>
                            <div className="relative">
                                <svg className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400 dark:text-slate-500" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z" /></svg>
                                <input
                                    type={showPassword ? "text" : "password"}
                                    value={password}
                                    onChange={e => setPassword(e.target.value)}
                                    className="w-full border border-gray-200 dark:border-slate-700 rounded-xl pl-10 pr-11 py-3 text-sm bg-gray-50/70 dark:bg-slate-800/70 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-blue-400 focus:bg-white dark:focus:bg-slate-800 transition-all"
                                    placeholder="Ingresá tu contraseña"
                                    autoComplete="current-password"
                                    required
                                />
                                <button
                                    type="button"
                                    onClick={() => setShowPassword(v => !v)}
                                    aria-label={showPassword ? "Ocultar contraseña" : "Mostrar contraseña"}
                                    className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:hover:text-slate-300 transition p-1 rounded-lg hover:bg-gray-100 dark:hover:bg-slate-700"
                                >
                                    {showPassword ? (
                                        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M3.98 8.223A10.477 10.477 0 001.934 12C3.226 16.338 7.244 19.5 12 19.5c.993 0 1.953-.138 2.863-.395M6.228 6.228A10.45 10.45 0 0112 4.5c4.756 0 8.773 3.162 10.065 7.498a10.523 10.523 0 01-4.293 5.774M6.228 6.228L3 3m3.228 3.228l3.65 3.65m7.894 7.894L21 21m-3.228-3.228l-3.65-3.65m0 0a3 3 0 10-4.243-4.243m4.242 4.242L9.88 9.88" /></svg>
                                    ) : (
                                        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" /><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" /></svg>
                                    )}
                                </button>
                            </div>
                        </div>

                        <button
                            type="submit"
                            disabled={loading}
                            className="w-full bg-gradient-to-r from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 disabled:opacity-60 disabled:cursor-not-allowed text-white font-semibold py-3 rounded-xl text-sm transition-all shadow-lg shadow-blue-600/25 hover:shadow-xl hover:shadow-blue-600/30 active:scale-[0.98] mt-1"
                        >
                            {loading ? (
                                <span className="flex items-center justify-center gap-2">
                                    <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" /><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" /></svg>
                                    Iniciando sesión...
                                </span>
                            ) : "Iniciar sesión"}
                        </button>
                    </form>
                </div>

                <p className="text-center text-[11px] text-gray-400 dark:text-slate-600 mt-6">
                    &copy; {new Date().getFullYear()} SuperMaster
                </p>
            </div>
        </div>
    );
}

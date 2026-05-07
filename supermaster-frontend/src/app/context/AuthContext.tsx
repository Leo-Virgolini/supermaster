"use client";

import { createContext, useContext, useEffect, useState } from "react";
import { API_BASE_URL } from "../config/runtime";
import LoginScreen from "../components/LoginScreen/LoginScreen";

export type Usuario = {
    id: number;
    username: string;
    nombreCompleto: string;
    rol: string;
    permisos: string[];
};

type AuthContextType = {
    usuario: Usuario;
    logout: () => void;
    hasPermiso: (permiso: string) => boolean;
};

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
    const [usuario, setUsuario] = useState<Usuario | null>(null);
    const [loading, setLoading] = useState(true);

    // Cargar sesión guardada al montar
    useEffect(() => {
        const stored = localStorage.getItem("usuario");
        const token = localStorage.getItem("accessToken");
        if (stored && token) {
            try {
                setUsuario(JSON.parse(stored));
            } catch {
                localStorage.removeItem("usuario");
                localStorage.removeItem("accessToken");
            }
        }
        setLoading(false);
    }, []);

    // Escuchar evento de token expirado/inválido (emitido por fetchAPI)
    useEffect(() => {
        const handler = () => {
            localStorage.removeItem("accessToken");
            localStorage.removeItem("usuario");
            setUsuario(null);
        };
        window.addEventListener("authExpired", handler);
        return () => window.removeEventListener("authExpired", handler);
    }, []);

    const login = async (username: string, password: string) => {
        let res: Response;
        try {
            res = await fetch(`${API_BASE_URL}/api/auth/login`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ username, password }),
            });
        } catch {
            window.dispatchEvent(new CustomEvent("backendOffline"));
            throw new Error("No se puede conectar con el servidor.");
        }
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            throw new Error(data?.message || "Credenciales incorrectas");
        }
        const data = await res.json();
        localStorage.setItem("accessToken", data.accessToken);
        localStorage.setItem("usuario", JSON.stringify(data.usuario));
        setUsuario(data.usuario);
    };

    const logout = () => {
        localStorage.removeItem("accessToken");
        localStorage.removeItem("usuario");
        setUsuario(null);
    };

    const hasPermiso = (permiso: string) => {
        return usuario?.permisos?.includes(permiso) ?? false;
    };

    // Spinner mientras verifica sesión
    if (loading) {
        return (
            <div className="flex items-center justify-center h-screen bg-gray-50 dark:bg-slate-950">
                <div className="w-8 h-8 border-4 border-gray-200 border-t-indigo-500 rounded-full animate-spin" />
            </div>
        );
    }

    // Login screen cuando no hay sesión
    if (!usuario) {
        return <LoginScreen onLogin={login} />;
    }

    return (
        <AuthContext.Provider value={{ usuario, logout, hasPermiso }}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const ctx = useContext(AuthContext);
    if (!ctx) throw new Error("useAuth debe usarse dentro de AuthProvider");
    return ctx;
}

import type { Metadata } from "next";
import { Inter, Geist_Mono } from "next/font/google";
import "./globals.css";
import Header from "./components/Header/Header";
import Sidebar from "./components/Sidebar/Sidebar";

import { SidebarProvider } from "./context/SidebarContext";
import { AuthProvider } from "./context/AuthContext";
import { ProcesoActivoProvider } from "./context/ProcesoActivoContext";
import { NotificacionProvider } from "./context/NotificacionContext";
import { Toaster } from "sonner";
import { ConfirmDialogRoot } from "./components/ConfirmDialog/ConfirmDialog";
import PageTitle from "./components/PageTitle/PageTitle";
import BackendOffline from "./components/BackendOffline/BackendOffline";

const inter = Inter({
    variable: "--font-inter",
    subsets: ["latin"],
});

const geistMono = Geist_Mono({
    variable: "--font-geist-mono",
    subsets: ["latin"],
});

export const metadata: Metadata = {
    title: "SuperMaster",
    description: "Sistema de gestión de productos, precios y canales de venta",
    authors: [{ name: "SuperMaster" }],
    icons: {
        icon: [
            { url: "/favicon.svg", type: "image/svg+xml" },
        ],
    },
};

export default function RootLayout({
    children,
}: Readonly<{
    children: React.ReactNode;
}>) {
    return (
        <html lang="es" suppressHydrationWarning>
            <head>
                <script dangerouslySetInnerHTML={{ __html: `(function(){var t=localStorage.getItem('theme');if(t==='dark'||(t===null&&window.matchMedia('(prefers-color-scheme:dark)').matches)){document.documentElement.classList.add('dark');}})()` }} />
            </head>
            <body className={`${inter.variable} ${geistMono.variable} antialiased`}>
                <SidebarProvider>
                    {/* AuthProvider: muestra LoginScreen si no hay sesión, el layout completo si hay */}
                    <AuthProvider>
                        <ProcesoActivoProvider>
                            <NotificacionProvider>
                                <PageTitle />
                                <div className="layout h-screen overflow-hidden">
                                    <Header />
                                    <Sidebar />
                                    <div className="content-wrapper">
                                        {children}
                                    </div>
                                    <ConfirmDialogRoot />
                                </div>
                            </NotificacionProvider>
                        </ProcesoActivoProvider>
                    </AuthProvider>
                    {/* Toaster y BackendOffline fuera de AuthProvider: siempre montados */}
                    <Toaster position="bottom-right" richColors closeButton />
                    <BackendOffline />
                </SidebarProvider>
            </body>
        </html>
    );
}

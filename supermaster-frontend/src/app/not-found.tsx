import Link from "next/link";
import { HomeIcon } from "@heroicons/react/24/outline";

export default function NotFound() {
    return (
        <main className="flex items-center justify-center flex-1 h-full px-4 bg-gradient-to-br from-slate-50 via-white to-blue-50/30 dark:from-slate-950 dark:via-slate-900 dark:to-slate-950">
            <div className="flex flex-col items-center gap-8 text-center max-w-md">
                {/* 404 grande con gradiente */}
                <div className="relative select-none">
                    <span className="text-[10rem] font-black leading-none bg-gradient-to-b from-gray-200 to-gray-100 dark:from-slate-700 dark:to-slate-800 bg-clip-text text-transparent">
                        404
                    </span>
                    <div className="absolute inset-0 flex items-center justify-center">
                        <div className="w-20 h-20 rounded-2xl bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center shadow-lg shadow-blue-500/25 dark:shadow-blue-500/15">
                            <svg className="w-10 h-10 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                                <path strokeLinecap="round" strokeLinejoin="round" d="M15.182 16.318A4.486 4.486 0 0012.016 15a4.486 4.486 0 00-3.198 1.318M21 12a9 9 0 11-18 0 9 9 0 0118 0zM9.75 9.75c0 .414-.168.75-.375.75S9 10.164 9 9.75 9.168 9 9.375 9s.375.336.375.75zm-.375 0h.008v.015h-.008V9.75zm5.625 0c0 .414-.168.75-.375.75s-.375-.336-.375-.75.168-.75.375-.75.375.336.375.75zm-.375 0h.008v.015h-.008V9.75z" />
                            </svg>
                        </div>
                    </div>
                </div>

                {/* Texto */}
                <div className="flex flex-col gap-2 -mt-4">
                    <h1 className="text-2xl font-bold text-gray-800 dark:text-white">
                        Página no encontrada
                    </h1>
                    <p className="text-sm text-gray-500 dark:text-slate-400 leading-relaxed">
                        La ruta que ingresaste no existe o fue movida. Verificá la URL o volvé al inicio.
                    </p>
                </div>

                {/* Acción */}
                <Link
                    href="/"
                    className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 text-white text-sm font-semibold shadow-lg shadow-blue-600/25 hover:shadow-xl hover:shadow-blue-600/30 transition-all active:scale-[0.98]"
                >
                    <HomeIcon className="w-4 h-4" />
                    Ir al inicio
                </Link>
            </div>
        </main>
    );
}

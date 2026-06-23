"use client";
import { useEffect, useState } from "react";
import { API_BASE_URL } from "../config/runtime";
import { getImagenDetalleAPI } from "./productosService";

/** Visor de todas las imágenes de un SKU (flechas, miniaturas, teclas ←/→/Esc). */
export default function ImagenesCarousel({ sku, onClose }: { sku: string; onClose: () => void }) {
    const [nombres, setNombres] = useState<string[]>([]);
    const [idx, setIdx] = useState(0);
    const [cargando, setCargando] = useState(true);

    useEffect(() => {
        let cancel = false;
        setCargando(true);
        getImagenDetalleAPI(sku)
            .then((d) => { if (!cancel) { setNombres(d.map((i) => i.nombre)); setIdx(0); } })
            .catch(() => { if (!cancel) setNombres([]); })
            .finally(() => { if (!cancel) setCargando(false); });
        return () => { cancel = true; };
    }, [sku]);

    useEffect(() => {
        const onKey = (e: KeyboardEvent) => {
            if (e.key === "Escape") onClose();
            if (e.key === "ArrowRight") setIdx((i) => (nombres.length ? (i + 1) % nombres.length : 0));
            if (e.key === "ArrowLeft") setIdx((i) => (nombres.length ? (i - 1 + nombres.length) % nombres.length : 0));
        };
        window.addEventListener("keydown", onKey);
        return () => window.removeEventListener("keydown", onKey);
    }, [onClose, nombres.length]);

    const ir = (d: number) => setIdx((i) => (nombres.length ? (i + d + nombres.length) % nombres.length : 0));

    return (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/80 p-4" onClick={onClose}>
            <div className="relative flex max-h-full flex-col items-center gap-3" onClick={(e) => e.stopPropagation()}>
                <button
                    onClick={onClose}
                    className="absolute -top-3 -right-3 z-10 flex h-8 w-8 items-center justify-center rounded-full bg-white text-gray-600 shadow-lg hover:bg-gray-100 dark:bg-slate-700 dark:text-slate-200"
                    title="Cerrar (Esc)"
                >
                    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" /></svg>
                </button>
                {cargando ? (
                    <div className="text-sm text-white/80">Cargando…</div>
                ) : nombres.length === 0 ? (
                    <div className="rounded-lg bg-white px-6 py-10 text-sm text-slate-500 dark:bg-slate-800 dark:text-slate-400">Sin imágenes para este SKU</div>
                ) : (
                    <>
                        <div className="flex items-center gap-3">
                            {nombres.length > 1 && (
                                <button onClick={() => ir(-1)} className="flex h-10 w-10 items-center justify-center rounded-full bg-white/90 text-gray-700 shadow hover:bg-white" aria-label="Anterior">
                                    <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" /></svg>
                                </button>
                            )}
                            <img src={`${API_BASE_URL}/api/imagenes/${nombres[idx]}`} alt={nombres[idx]} className="max-h-[70vh] max-w-[80vw] rounded-lg bg-white object-contain shadow-2xl" />
                            {nombres.length > 1 && (
                                <button onClick={() => ir(1)} className="flex h-10 w-10 items-center justify-center rounded-full bg-white/90 text-gray-700 shadow hover:bg-white" aria-label="Siguiente">
                                    <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" /></svg>
                                </button>
                            )}
                        </div>
                        <div className="text-xs text-white/80">{idx + 1} / {nombres.length} · {nombres[idx]}</div>
                        {nombres.length > 1 && (
                            <div className="flex max-w-[80vw] flex-wrap justify-center gap-1.5">
                                {nombres.map((n, i) => (
                                    <button key={n} onClick={() => setIdx(i)} className={`h-12 w-12 overflow-hidden rounded border-2 ${i === idx ? "border-blue-400" : "border-transparent opacity-70 hover:opacity-100"}`}>
                                        <img src={`${API_BASE_URL}/api/imagenes/${n}`} alt={n} className="h-full w-full bg-white object-contain" />
                                    </button>
                                ))}
                            </div>
                        )}
                    </>
                )}
            </div>
        </div>
    );
}

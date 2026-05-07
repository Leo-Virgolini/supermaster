"use client";
import { ExclamationTriangleIcon } from "@heroicons/react/24/outline";

interface ErrorBannerProps {
    message: string;
    onRetry?: () => void;
}

export default function ErrorBanner({ message, onRetry }: ErrorBannerProps) {
    return (
        <div className="mx-auto mt-12 max-w-md rounded-lg border border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-950/40 p-6 text-center shadow-sm">
            <ExclamationTriangleIcon className="mx-auto h-10 w-10 text-red-400 dark:text-red-500 mb-3" />
            <h3 className="text-lg font-semibold text-red-700 dark:text-red-300 mb-1">Error al cargar datos</h3>
            <p className="text-sm text-red-600 dark:text-red-400 mb-4">{message}</p>
            <button
                onClick={onRetry ?? (() => window.location.reload())}
                className="inline-flex items-center gap-1.5 rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 transition"
            >
                Reintentar
            </button>
        </div>
    );
}

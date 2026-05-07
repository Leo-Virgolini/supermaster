"use client";

import { useState } from "react";
import { Table as ReactTable } from "@tanstack/react-table";

type PaginationControlsProps<TData> = {
    table?: ReactTable<TData>;
    pageIndex: number;
    pageSize: number;
    pageCount: number;
    onPageSizeChange: (pageSize: number) => void;
    onPageChange?: (pageIndex: number) => void;
};

const PaginationControls = <TData,>({
    table,
    pageIndex,
    pageSize,
    pageCount,
    onPageSizeChange,
    onPageChange,
}: PaginationControlsProps<TData>) => {
    const [editingPage, setEditingPage] = useState(false);
    const [pageInput, setPageInput] = useState("");

    const canPreviousPage = pageIndex > 0;
    const canNextPage = pageIndex < (pageCount - 1);

    const goToPage = (idx: number) => {
        if (onPageChange) onPageChange(idx);
        else table?.setPageIndex(idx);
    };

    const handlePageSubmit = () => {
        const num = parseInt(pageInput, 10);
        if (!isNaN(num) && num >= 1 && num <= pageCount) {
            goToPage(num - 1);
        }
        setEditingPage(false);
    };

    return (
        <div className="flex items-center justify-between gap-4 text-xs text-gray-600 dark:text-slate-400">
            {/* Info de página (clickeable para editar) */}
            <div className="flex items-center gap-1">
                Página{" "}
                {editingPage ? (
                    <input
                        type="number"
                        min={1}
                        max={pageCount}
                        autoFocus
                        value={pageInput}
                        onChange={(e) => setPageInput(e.target.value)}
                        onBlur={handlePageSubmit}
                        onKeyDown={(e) => {
                            if (e.key === "Enter") handlePageSubmit();
                            if (e.key === "Escape") setEditingPage(false);
                        }}
                        className="w-14 text-center font-semibold border border-blue-400 dark:border-blue-500 rounded px-1 py-0.5 text-xs outline-none focus:ring-1 focus:ring-blue-500 bg-white dark:bg-slate-700 text-gray-800 dark:text-slate-200"
                    />
                ) : (
                    <button
                        type="button"
                        onClick={() => { setPageInput(String(pageIndex + 1)); setEditingPage(true); }}
                        className="font-bold text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-500/10 px-2 py-0.5 rounded-md border border-blue-200 dark:border-blue-700 hover:bg-blue-100 dark:hover:bg-blue-500/20 cursor-pointer transition-colors"
                        title="Click para ir a una página"
                    >
                        {pageIndex + 1}
                    </button>
                )}{" "}
                de <span className="font-semibold text-gray-800 dark:text-slate-200">{pageCount}</span>
            </div>

            {/* Selector de pageSize */}
            <div className="flex items-center gap-2">
                <span>Filas por página:</span>
                <select
                    value={pageSize}
                    onChange={(e) => {
                        const nextSize = Number(e.target.value);
                        onPageSizeChange(nextSize);
                        goToPage(0);
                    }}
                    className="border border-gray-200 dark:border-slate-600 rounded-md px-2 py-1 text-xs focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none bg-white dark:bg-slate-700 text-gray-700 dark:text-slate-200 cursor-pointer">
                    {[10, 20, 50, 100].map((size) => (
                        <option key={size} value={size}>
                            {size}
                        </option>
                    ))}
                </select>
            </div>

            {/* Botones */}
            <div className="flex items-center gap-0.5">
                <button
                    type="button"
                    onClick={() => goToPage(0)}
                    disabled={!canPreviousPage}
                    className="px-2 py-1 rounded-md hover:bg-gray-100 dark:hover:bg-slate-700 disabled:opacity-30 disabled:cursor-not-allowed text-gray-600 dark:text-slate-400 transition-colors"
                    title="Primera página">
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" d="M18.75 19.5l-7.5-7.5 7.5-7.5m-6 15L5.25 12l7.5-7.5" /></svg>
                </button>
                <button
                    type="button"
                    onClick={() => goToPage(pageIndex - 1)}
                    disabled={!canPreviousPage}
                    className="px-2 py-1 rounded-md hover:bg-gray-100 dark:hover:bg-slate-700 disabled:opacity-30 disabled:cursor-not-allowed text-gray-600 dark:text-slate-400 transition-colors"
                    title="Página anterior">
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" /></svg>
                </button>
                <button
                    type="button"
                    onClick={() => goToPage(pageIndex + 1)}
                    disabled={!canNextPage}
                    className="px-2 py-1 rounded-md hover:bg-gray-100 dark:hover:bg-slate-700 disabled:opacity-30 disabled:cursor-not-allowed text-gray-600 dark:text-slate-400 transition-colors"
                    title="Página siguiente">
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" /></svg>
                </button>
                <button
                    type="button"
                    onClick={() => goToPage(pageCount - 1)}
                    disabled={!canNextPage}
                    className="px-2 py-1 rounded-md hover:bg-gray-100 dark:hover:bg-slate-700 disabled:opacity-30 disabled:cursor-not-allowed text-gray-600 dark:text-slate-400 transition-colors"
                    title="Última página">
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" d="M5.25 4.5l7.5 7.5-7.5 7.5m6-15l7.5 7.5-7.5 7.5" /></svg>
                </button>
            </div>
        </div>
    );
};

export default PaginationControls;
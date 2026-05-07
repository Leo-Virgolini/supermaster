"use client";

import { Table as ReactTable } from "@tanstack/react-table";
import { Dispatch, SetStateAction, ReactNode, useRef, useEffect } from "react";
import { AdjustmentsVerticalIcon, CheckIcon, ClipboardDocumentIcon } from "@heroicons/react/24/outline";

type TableToolbarProps<TData> = {
    table: ReactTable<TData>;
    columnsListVisible: boolean;
    onToggleColumnsList: () => void;
    columnVisibility: Record<string, boolean>;
    setColumnVisibility: Dispatch<SetStateAction<Record<string, boolean>>>;
    searchSlot?: ReactNode;
    extra?: ReactNode;
};

const TableToolbar = <TData,>({
    table,
    columnsListVisible,
    onToggleColumnsList,
    columnVisibility,
    setColumnVisibility,
    searchSlot,
    extra,
}: TableToolbarProps<TData>) => {
    const menuRef = useRef<HTMLDivElement>(null);
    const listRef = useRef<HTMLDivElement>(null);

    const renderColumnLabel = (column: ReturnType<ReactTable<TData>["getAllLeafColumns"]>[number]) => {
        if (typeof column.columnDef.header === "string") {
            return column.columnDef.header;
        }

        if (column.id === "select") {
            return (
                <span className="inline-flex items-center gap-1.5">
                    <CheckIcon className="h-3.5 w-3.5 text-gray-500 dark:text-slate-400" />
                    Seleccion
                </span>
            );
        }

        if (column.id === "detalle") {
            return (
                <span className="inline-flex items-center gap-1.5">
                    <ClipboardDocumentIcon className="h-3.5 w-3.5 text-gray-500 dark:text-slate-400" />
                    Detalle
                </span>
            );
        }

        return column.id;
    };

    // Cerrar al hacer click fuera
    useEffect(() => {
        if (!columnsListVisible) return;
        if (listRef.current) {
            listRef.current.scrollTop = 0;
        }
        const handler = (e: MouseEvent) => {
            if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
                onToggleColumnsList();
            }
        };
        document.addEventListener("mousedown", handler);
        return () => document.removeEventListener("mousedown", handler);
    }, [columnsListVisible, onToggleColumnsList]);

    const hideableColumns = table.getAllLeafColumns().filter((col) => col.getCanHide());
    const hiddenCount = hideableColumns.filter((col) => !(columnVisibility[col.id] ?? true)).length;

    return (
        <div className="flex items-center gap-2">
            {searchSlot}
            <div className="relative" ref={menuRef}>
                <button
                    onClick={onToggleColumnsList}
                    className={`p-1.5 rounded-lg border transition-colors ${columnsListVisible
                        ? "border-blue-300 dark:border-blue-700 bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400"
                        : "border-gray-200 dark:border-slate-600 bg-white dark:bg-slate-800 hover:bg-gray-100 dark:hover:bg-blue-500/20 dark:hover:border-blue-500/40 text-gray-500 dark:text-slate-400 dark:hover:text-blue-300"
                        }`}
                    title="Mostrar/ocultar columnas"
                >
                    <AdjustmentsVerticalIcon className="w-4 h-4" />
                    {hiddenCount > 0 && (
                        <span className="absolute -top-1.5 -right-1.5 w-4 h-4 text-[9px] font-bold bg-blue-500 text-white rounded-full flex items-center justify-center">
                            {hiddenCount}
                        </span>
                    )}
                </button>
                {columnsListVisible && (
                    <div ref={listRef} className="absolute top-full mt-1 right-0 bg-white dark:bg-slate-800 border border-gray-200 dark:border-slate-700 rounded-lg shadow-lg z-50 w-52 py-1 max-h-80 overflow-y-auto">
                        <div className="px-3 py-1.5 text-[10px] font-bold uppercase tracking-wider text-gray-400 dark:text-slate-500 border-b border-gray-100 dark:border-slate-700">
                            Columnas visibles
                        </div>
                        {hideableColumns.map((column) => {
                            const isVisible = columnVisibility[column.id] ?? true;
                            return (
                                <label
                                    key={column.id}
                                    className="flex items-center gap-2 px-3 py-1.5 hover:bg-gray-50 dark:hover:bg-slate-700 cursor-pointer text-sm text-gray-700 dark:text-slate-300"
                                >
                                    <input
                                        type="checkbox"
                                        checked={isVisible}
                                        onChange={(e) => {
                                            const checked = e.target.checked;
                                            setColumnVisibility((prev) => ({
                                                ...prev,
                                                [column.id]: checked,
                                            }));
                                            column.toggleVisibility(checked);
                                        }}
                                        className="rounded border-gray-300 dark:border-slate-600 text-blue-600 focus:ring-blue-500"
                                    />
                                    {renderColumnLabel(column)}
                                </label>
                            );
                        })}
                        {hiddenCount > 0 && (
                            <div className="border-t border-gray-100 dark:border-slate-700 px-3 py-1.5">
                                <button
                                    onClick={() => {
                                        const next: Record<string, boolean> = {};
                                        hideableColumns.forEach((col) => { next[col.id] = true; });
                                        setColumnVisibility(next);
                                        table.setColumnVisibility(next);
                                    }}
                                    className="text-xs text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300"
                                >
                                    Mostrar todas
                                </button>
                            </div>
                        )}
                    </div>
                )}
            </div>
            {extra}
        </div>
    );
};

export default TableToolbar;

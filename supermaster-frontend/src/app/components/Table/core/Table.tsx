"use client";
import { getErrorMessage } from "@/lib/errors";
import {
    useReactTable,
    getCoreRowModel,
    flexRender,
    ColumnDef,
    type SortingState,
    type Row,
} from "@tanstack/react-table";
import { useEffect, useState, useCallback, Fragment, useRef } from "react";
import { ClipboardDocumentIcon, ArrowDownTrayIcon } from "@heroicons/react/24/outline";
import { toast } from "sonner";
import { notificar } from "../../../utils/notificar";
import TableToolbar from "./TableToolbar";
import PaginationControls from "./PaginationControls";
import ColumnContextMenu from "../../ColumnContextMenu/ColumnContextMenu";
import { EditingCellProvider } from "./EditingCellContext";
import { exportToExcel, buildExportColumns } from "../../../utils/exportCSV";

/** Lee el pageSize guardado en localStorage para evitar un re-fetch al montar. */
export function getInitialPageSize(tableId: string, fallback = 100): number {
    if (typeof window === "undefined") return fallback;
    try {
        const saved = localStorage.getItem(`pageSize_v2_${tableId}`);
        if (saved) {
            const parsed = Number(saved);
            if (parsed > 0) return parsed;
        }
    } catch {}
    return fallback;
}

type Props = {
    tableId: string;
    data: any[];
    columns: ColumnDef<any, any>[];
    globalFilter: string;
    setGlobalFilter: (value: string) => void;
    searchSlot?: React.ReactNode;
    /** Contenido que se muestra como fila debajo de la toolbar (p. ej. un panel de filtros desplegable). */
    belowToolbarSlot?: React.ReactNode;
    /** Estilo con líneas de grilla (bordes verticales entre columnas) y zebra más marcado. */
    bordered?: boolean;

    // Fase 3: paginación y sorting controlados desde fuera
    pageIndex: number;
    pageSize: number;
    pageCount: number;
    onPageChange: (pageIndex: number) => void;
    onPageSizeChange: (pageSize: number) => void;
    sorting: SortingState;
    setSorting: React.Dispatch<React.SetStateAction<SortingState>>;
    rowSelection: Record<string, boolean>;
    setRowSelection: React.Dispatch<React.SetStateAction<Record<string, boolean>>>;
    updateData?: (rowIndex: number, columnId: string, value: unknown) => void | Promise<void>;

    onColumnFilterChange?: (columnId: string, value: any, labels?: Record<string, string>) => void;
    getActiveFilter?: (columnId: string) => any;
    filterSlot?: React.ReactNode;
    /** Indica si la página tiene filtros por columna activos (además del globalFilter).
     * Si es true y la tabla está vacía, mostramos un empty state contextual. */
    hasFiltersActive?: boolean;
    /** Callback para limpiar filtros desde el empty state. Si viene, aparece un botón. */
    onClearAllFilters?: () => void;
    emptyMessage?: string;
    isLoading?: boolean;
    renderSubRow?: (row: Row<any>) => React.ReactNode | null;
    totalRecords?: number;
    onExportAll?: () => Promise<any[]>;
    exportFilename?: string;
    toolbarExtra?: React.ReactNode;
    getRowClassName?: (row: Row<any>) => string;
    columnVisibilityStorageVersion?: number;
};

const Table = ({
    tableId,
    data,
    columns,
    globalFilter,
    setGlobalFilter,
    pageIndex,
    pageSize,
    pageCount,
    onPageChange,
    onPageSizeChange,
    sorting,
    setSorting,
    rowSelection,
    setRowSelection,
    updateData,

    onColumnFilterChange,
    getActiveFilter,
    filterSlot,
    hasFiltersActive = false,
    onClearAllFilters,
    emptyMessage = "No hay registros para mostrar.",
    isLoading = false,
    searchSlot,
    belowToolbarSlot,
    bordered = true,
    renderSubRow,
    totalRecords,
    onExportAll,
    exportFilename,
    toolbarExtra,
    getRowClassName,
    columnVisibilityStorageVersion,
}: Props) => {

    const [columnsListVisibility, setColumnsListVisibility] = useState(false);
    const [menu, setMenu] = useState<{ x: number; y: number; columnId: string } | null>(null);
    const [isFullscreen, setIsFullscreen] = useState(false);
    const [updatedCellKey, setUpdatedCellKey] = useState<string | null>(null);
    const scrollContainerRef = useRef<HTMLDivElement>(null);
    const savedScrollPos = useRef<{ left: number; top: number } | null>(null);

    const saveScrollPosition = () => {
        if (scrollContainerRef.current) {
            savedScrollPos.current = {
                left: scrollContainerRef.current.scrollLeft,
                top: scrollContainerRef.current.scrollTop,
            };
        }
    };

    const setSortingPreservingScroll: typeof setSorting = (updater) => {
        saveScrollPosition();
        // Cambiar el sort reordena el dataset entero: quedarse en página 5 con un
        // orden distinto da resultados confusos (filas que el usuario no esperaba).
        // Volvemos siempre a la página 0 y limpiamos la selección porque los ids
        // ya elegidos pueden no representar lo mismo en el nuevo orden.
        if (pageIndex !== 0) onPageChange(0);
        if (Object.keys(rowSelection).length > 0) setRowSelection({});
        setSorting((prev) => {
            let next = typeof updater === "function" ? updater(prev) : updater;
            // Forzar ciclo ASC → DESC → quitar (TanStack por defecto hace ASC → quitar en click simple)
            if (next.length < prev.length) {
                const removed = prev.find((s) => !next.some((n) => n.id === s.id));
                if (removed && !removed.desc) {
                    next = prev.map((s) => s.id === removed.id ? { ...s, desc: true } : s);
                }
            }
            return next;
        });
    };

    // Limpiar IDs de selección que ya no están en data (típicamente porque cambió
    // un filtro o se paginó). El `rowSelection` de TanStack se indexa por id global
    // (gracias a getRowId), así que conservar ids que no están visibles lleva a
    // bugs sutiles: bulk delete sobre filas que el usuario no está viendo.
    useEffect(() => {
        const seleccionados = Object.keys(rowSelection);
        if (seleccionados.length === 0) return;
        const visibles = new Set(data.map((r) => String((r as { id: number | string }).id)));
        const stale = seleccionados.filter((id) => !visibles.has(id));
        if (stale.length === 0) return;
        const limpia: Record<string, boolean> = {};
        for (const id of seleccionados) {
            if (visibles.has(id)) limpia[id] = rowSelection[id];
        }
        setRowSelection(limpia);
    }, [data, rowSelection, setRowSelection]);

    useEffect(() => {
        if (savedScrollPos.current !== null && scrollContainerRef.current) {
            scrollContainerRef.current.scrollLeft = savedScrollPos.current.left;
            scrollContainerRef.current.scrollTop = savedScrollPos.current.top;
            savedScrollPos.current = null;
        }
    }, [data]);

    useEffect(() => {
        const handleKey = (e: KeyboardEvent) => {
            if (e.key === "F11") { e.preventDefault(); setIsFullscreen(prev => !prev); }
            if (e.key === "Escape" && isFullscreen) setIsFullscreen(false);
        };
        window.addEventListener("keydown", handleKey);
        return () => window.removeEventListener("keydown", handleKey);
    }, [isFullscreen]);
    const [columnVisibility, setColumnVisibility] = useState<Record<string, boolean>>(() => {
        if (typeof window === "undefined") return {};
        try {
            const saved = localStorage.getItem(`columnVisibility_${tableId}`);
            return saved ? JSON.parse(saved) : {};
        } catch {
            return {};
        }
    });

    useEffect(() => {
        if (typeof window === "undefined") return;
        localStorage.setItem(`columnVisibility_${tableId}`, JSON.stringify(columnVisibility));
    }, [columnVisibility, tableId]);

    useEffect(() => {
        if (typeof window === "undefined") return;
        try {
            const saved = localStorage.getItem(`columnVisibility_${tableId}`);
            setColumnVisibility(saved ? JSON.parse(saved) : {});
        } catch {
            setColumnVisibility({});
        }
    }, [columnVisibilityStorageVersion, tableId]);

    // Persistir pageSize por tabla
    useEffect(() => {
        if (typeof window === "undefined") return;
        try {
            const saved = localStorage.getItem(`pageSize_v2_${tableId}`);
            if (saved) {
                const parsed = Number(saved);
                if (parsed > 0 && parsed !== pageSize) onPageSizeChange(parsed);
            }
        } catch {}
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [tableId]);

    useEffect(() => {
        if (typeof window === "undefined") return;
        localStorage.setItem(`pageSize_v2_${tableId}`, String(pageSize));
    }, [pageSize, tableId]);

    const table = useReactTable({
        data,
        columns,
        state: {
            globalFilter,
            columnVisibility,
            sorting,
            pagination: { pageIndex, pageSize },
            rowSelection,
        },
        getCoreRowModel: getCoreRowModel(),
        manualPagination: true,
        pageCount,
        manualSorting: true,
        // Por defecto TanStack infiere sortDescFirst=true para columnas numéricas/Date,
        // lo que hace que algunas columnas empiecen DESC al primer click y otras ASC.
        // Forzamos ASC→DESC→none para todas, que es el comportamiento que esperan los usuarios.
        sortDescFirst: false,
        onSortingChange: setSortingPreservingScroll,
        isMultiSortEvent: (e: unknown) => (e as MouseEvent).ctrlKey || (e as MouseEvent).metaKey,
        getRowId: (row) => row.id.toString(),
        enableRowSelection: true,
        onRowSelectionChange: setRowSelection,
        onPaginationChange: (updater) => {
            const next =
                typeof updater === "function"
                    ? updater({ pageIndex, pageSize })
                    : updater;

            if (next.pageIndex !== pageIndex || next.pageSize !== pageSize) {
                // Preservar la posición horizontal/vertical del scroll al cambiar
                // de página o de pageSize — la tabla se vuelve a renderizar con
                // datos nuevos y por defecto volvía al inicio.
                saveScrollPosition();
            }
            if (next.pageIndex !== pageIndex) {
                onPageChange(next.pageIndex);
            }
            if (next.pageSize !== pageSize) {
                onPageSizeChange(next.pageSize);
            }
        },
        onGlobalFilterChange: setGlobalFilter,
        onColumnVisibilityChange: setColumnVisibility,
        enableColumnResizing: true,
        columnResizeMode: "onChange",
        meta: {
            updateData: async (rowIndex: number, columnId: string, value: unknown) => {
                if (!updateData) return;
                saveScrollPosition();
                const rowRecord = data[rowIndex];
                const rowId = rowRecord?.id != null ? String(rowRecord.id) : String(rowIndex);
                await Promise.resolve(updateData(rowIndex, columnId, value));
                setUpdatedCellKey(`${rowId}_${columnId}`);
            },
        },
    });

    const selectedRowCount = Object.keys(rowSelection).length;

    const handleCopy = useCallback(() => {
        const selectedRows = table.getSelectedRowModel().rows;
        const targetRows = selectedRows.length > 0 ? selectedRows : table.getRowModel().rows;
        if (targetRows.length === 0) return;

        const visibleCols = table.getVisibleLeafColumns().filter((col) => {
            if (col.id === "select" || col.id === "detalle") return false;
            if (col.columnDef.enableColumnFilter === false && !("accessorKey" in col.columnDef) && !col.accessorFn) return false;
            return typeof col.columnDef.header === "string";
        });

        const headers = visibleCols.map((col) => col.columnDef.header as string);

        const rows = targetRows.map((row) =>
            visibleCols.map((col) => {
                const val = row.getValue(col.id);
                if (val === null || val === undefined) return "";
                if (typeof val === "boolean") return val ? "Sí" : "No";
                return String(val);
            }).join("\t")
        );

        const tsv = headers.join("\t") + "\n" + rows.join("\n");
        navigator.clipboard.writeText(tsv);
        const count = targetRows.length;
        const label = selectedRows.length > 0 ? `${count} seleccionado(s)` : `${count} fila(s)`;
        toast.success(`${label} copiado(s) al portapapeles`);
    }, [table]);

    const [isExporting, setIsExporting] = useState(false);
    const defaultFilterSlot = globalFilter ? (
        <>
            <span className="text-[10px] uppercase tracking-wider text-gray-400 dark:text-slate-500 font-semibold">Filtros:</span>
            <span className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-800 text-amber-700 dark:text-amber-300">
                Búsqueda: {globalFilter}
                <button
                    onClick={() => setGlobalFilter("")}
                    className="ml-0.5 hover:text-red-500 dark:hover:text-red-400 transition-colors"
                    title="Quitar búsqueda"
                >
                    ×
                </button>
            </span>
            <button
                onClick={() => setGlobalFilter("")}
                className="text-[11px] text-gray-400 dark:text-slate-500 hover:text-red-500 dark:hover:text-red-400 transition-colors"
            >
                Limpiar filtros
            </button>
        </>
    ) : null;
    const effectiveFilterSlot = filterSlot ?? defaultFilterSlot;

    const handleExport = useCallback(async () => {
        if (!onExportAll) return;
        setIsExporting(true);
        try {
            const allData = await onExportAll();
            const cols = buildExportColumns(table);
            exportToExcel(allData, cols, exportFilename || tableId);
            notificar.success(`${allData.length} registro(s) exportados`);
        } catch (e: unknown) {
            notificar.error("Error al exportar: " + (getErrorMessage(e, "desconocido")));
        } finally {
            setIsExporting(false);
        }
    }, [onExportAll, table, exportFilename, tableId]);

    // Estilo "bordered" (default): líneas verticales entre columnas, separadores
    // horizontales marcados y zebra azul. Pasar bordered={false} para el estilo sutil.
    const cellBorder = bordered ? "border-r border-gray-200 dark:border-slate-700 last:border-r-0" : "";
    const rowBorder = bordered
        ? "border-b border-gray-200 dark:border-slate-700"
        : "border-b border-gray-100/80 dark:border-slate-700/60";
    const zebra = bordered
        ? "even:bg-blue-50/60 dark:even:bg-blue-900/15 hover:bg-blue-100/70 dark:hover:bg-blue-900/30"
        : "even:bg-gray-50/60 dark:even:bg-slate-700/40 hover:bg-blue-50/70 dark:hover:bg-blue-900/20";

    return (
        <EditingCellProvider>
        <div className={`relative flex flex-col w-full flex-1 rounded-xl bg-white dark:bg-slate-900 shadow-sm overflow-hidden ${isFullscreen ? "!fixed !inset-0 !z-[100] !rounded-none !m-0" : ""}`}>
            {/* Toolbar */}
            <div className="shrink-0 px-4 py-2.5 border-b border-gray-200 dark:border-slate-700 bg-gray-50/80 dark:bg-slate-800/60">
                <div className="flex items-center gap-2">
                    <div className="flex-1">
                        <TableToolbar
                            table={table}
                            columnsListVisible={columnsListVisibility}
                            onToggleColumnsList={() =>
                                setColumnsListVisibility((prev) => !prev)
                            }
                            columnVisibility={columnVisibility}
                            setColumnVisibility={setColumnVisibility}
                            searchSlot={searchSlot}
                            extra={toolbarExtra}
                        />
                    </div>
                    {onExportAll && (
                        <button
                            onClick={handleExport}
                            disabled={isExporting}
                            className="shrink-0 p-1.5 rounded-lg border border-gray-200 dark:border-slate-600 bg-white dark:bg-slate-800 hover:bg-gray-100 dark:hover:bg-blue-500/20 dark:hover:border-blue-500/40 text-gray-500 dark:text-slate-400 dark:hover:text-blue-300 transition-colors disabled:opacity-50"
                            title="Exportar todo a CSV"
                        >
                            <ArrowDownTrayIcon className={`w-4 h-4 ${isExporting ? "animate-pulse" : ""}`} />
                        </button>
                    )}
                    <button
                        onClick={handleCopy}
                        className="shrink-0 p-1.5 rounded-lg border border-gray-200 dark:border-slate-600 bg-white dark:bg-slate-800 hover:bg-gray-100 dark:hover:bg-blue-500/20 dark:hover:border-blue-500/40 text-gray-500 dark:text-slate-400 dark:hover:text-blue-300 transition-colors"
                        title={selectedRowCount > 0 ? `Copiar ${selectedRowCount} seleccionado(s)` : "Copiar toda la página"}
                    >
                        <ClipboardDocumentIcon className="w-4 h-4" />
                    </button>
                    <button
                        onClick={() => setIsFullscreen(prev => !prev)}
                        className="shrink-0 p-1.5 rounded-lg border border-gray-200 dark:border-slate-600 bg-white dark:bg-slate-800 hover:bg-gray-100 dark:hover:bg-blue-500/20 dark:hover:border-blue-500/40 text-gray-500 dark:text-slate-400 dark:hover:text-blue-300 transition-colors"
                        title={isFullscreen ? "Salir de pantalla completa (Esc)" : "Pantalla completa"}
                    >
                        {isFullscreen ? (
                            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-4 h-4">
                                <path strokeLinecap="round" strokeLinejoin="round" d="M9 9V4.5M9 9H4.5M9 9 3.75 3.75M9 15v4.5M9 15H4.5M9 15l-5.25 5.25M15 9h4.5M15 9V4.5M15 9l5.25-5.25M15 15h4.5M15 15v4.5m0-4.5 5.25 5.25" />
                            </svg>
                        ) : (
                            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-4 h-4">
                                <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 3.75v4.5m0-4.5h4.5m-4.5 0L9 9M3.75 20.25v-4.5m0 4.5h4.5m-4.5 0L9 15M20.25 3.75h-4.5m4.5 0v4.5m0-4.5L15 9m5.25 11.25h-4.5m4.5 0v-4.5m0 4.5L15 15" />
                            </svg>
                        )}
                    </button>
                </div>
            </div>

            {/* Panel desplegable opcional debajo de la toolbar (p. ej. filtros) */}
            {belowToolbarSlot}

            {/* Barra de filtros y ordenamiento activos */}
            {(effectiveFilterSlot || sorting.length > 0) && (
                <div className="shrink-0 px-4 py-1.5 border-b border-gray-200 dark:border-slate-700 bg-gray-50/50 dark:bg-slate-800/40 flex items-center gap-3 flex-wrap">
                    {effectiveFilterSlot}
                    {effectiveFilterSlot && sorting.length > 0 && (
                        <span className="w-px h-4 bg-gray-300 dark:bg-slate-600" />
                    )}
                    {sorting.length > 0 && (
                        <>
                            <span className="text-[10px] uppercase tracking-wider text-gray-400 dark:text-slate-500 font-semibold">Orden:</span>
                            {sorting.map((s, i) => {
                                const col = table.getAllLeafColumns().find(c => c.id === s.id);
                                const label = col && typeof col.columnDef.header === "string" ? col.columnDef.header : s.id;
                                return (
                                    <span key={s.id} className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-800 text-blue-700 dark:text-blue-300">
                                        {sorting.length > 1 && <span className="text-[10px] font-bold text-blue-400 dark:text-blue-500">{i + 1}</span>}
                                        {label} {s.desc ? "↓" : "↑"}
                                        <button
                                            onClick={() => setSortingPreservingScroll(prev => prev.filter(item => item.id !== s.id))}
                                            className="ml-0.5 hover:text-red-500 dark:hover:text-red-400 transition-colors"
                                            title={`Quitar orden por ${label}`}
                                        >×</button>
                                    </span>
                                );
                            })}
                            <button
                                onClick={() => setSorting([])}
                                className="text-[11px] text-gray-400 dark:text-slate-500 hover:text-red-500 dark:hover:text-red-400 transition-colors"
                            >
                                Limpiar orden
                            </button>
                        </>
                    )}
                </div>
            )}

            {/* Tabla con Scroll Interno */}
            <div ref={scrollContainerRef} className="flex-1 overflow-auto min-h-0 bg-white dark:bg-slate-900">
                <table className="min-w-full text-[13px]" style={{ width: table.getCenterTotalSize() }}>
                    <thead className="text-gray-600 dark:text-slate-300 text-center font-semibold sticky top-0 z-10 uppercase text-[11px] tracking-wider">
                        {table.getHeaderGroups().map((headerGroup) => (
                            <tr key={headerGroup.id} className="border-b border-gray-200 dark:border-slate-700">
                                {headerGroup.headers.map((header) => (
                                    <th
                                        key={header.id}
                                        suppressHydrationWarning
                                        // bg-gray-100 va en el <th> (no solo en el <thead>) porque position:sticky
                                        // en thead crea capas independientes por celda — sin bg propio se ve el
                                        // contenido al hacer scroll. Hover usa un tono más oscuro para feedback.
                                        className={`relative py-1 px-3 whitespace-nowrap group text-center transition-colors leading-none align-middle bg-gray-100 dark:bg-slate-700 ${cellBorder} ${(header.column.columnDef.meta as any)?.headerClassName ?? ""} ${
                                            isLoading
                                                ? "cursor-wait opacity-70"
                                                : "cursor-pointer hover:bg-gray-200 dark:hover:bg-slate-600"
                                        }`}
                                        onClick={isLoading ? undefined : header.column.getToggleSortingHandler()}
                                        onContextMenu={(e) => {
                                            if (!onColumnFilterChange) return;
                                            if (header.column.columnDef.enableColumnFilter === false) return;
                                            e.preventDefault();
                                            setMenu({
                                                x: e.clientX,
                                                y: e.clientY,
                                                columnId: header.column.id
                                            });
                                        }}
                                        style={{
                                            width: header.getSize(),
                                        }}>
                                        <div className="flex items-center justify-center gap-1">
                                            {(header.column.columnDef.meta as any)?.editable && (
                                                <svg className="w-3 h-3 text-blue-400 dark:text-blue-500 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                                    <path d="M9 11l3 3L22 4" /><path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11" />
                                                </svg>
                                            )}
                                            {flexRender(
                                                header.column.columnDef.header,
                                                header.getContext()
                                            )}

                                            {/* Indicador visual de orden */}
                                            {{
                                                asc: <span className="text-blue-500 text-xs">↑</span>,
                                                desc: <span className="text-blue-500 text-xs">↓</span>,
                                            }[header.column.getIsSorted() as string] ??
                                                null}

                                            {/* Indicador visual de filtro activo */}
                                            {(() => {
                                                if (!getActiveFilter) return null;
                                                const f = getActiveFilter(header.column.id);
                                                const hasFilter = f !== undefined && f !== null && f !== "" && (!Array.isArray(f) || f.length > 0);
                                                if (!hasFilter) return null;
                                                return <span className="w-1.5 h-1.5 rounded-full bg-blue-500 shrink-0" title="Filtro activo" />;
                                            })()}
                                        </div>

                                        {/* Handler de resize */}
                                        {header.column.getCanResize() && (
                                            <div
                                                onDoubleClick={
                                                    header.column.resetSize
                                                }
                                                onMouseDown={header.getResizeHandler()}
                                                onTouchStart={header.getResizeHandler()}
                                                className={`absolute right-0 top-0 h-full w-0.5 cursor-col-resize select-none transition rounded-2xl ${header.column.getIsResizing()
                                                    ? "bg-blue-400 w-1.5"
                                                    : "bg-gray-200 dark:bg-slate-600 w-0.5 group-hover:bg-blue-400 group-hover:w-1.5"
                                                    }`}
                                            />
                                        )}
                                    </th>
                                ))}
                            </tr>
                        ))}
                    </thead>

                    <tbody>
                        {isLoading ? (
                            Array.from({ length: 10 }).map((_, i) => (
                                <tr key={i} className={`${rowBorder} ${zebra}`}>
                                    {table.getVisibleLeafColumns().map((col) => (
                                        <td key={col.id} className={`py-1 px-3 text-center leading-none align-middle ${cellBorder}`}>
                                            <div className="h-4 rounded bg-gray-200 dark:bg-slate-700 animate-pulse mx-auto max-w-[80%]" />
                                        </td>
                                    ))}
                                </tr>
                            ))
                        ) : table.getRowModel().rows.length === 0 ? (
                            (() => {
                                // Empty state contextual: si hay búsqueda o filtros activos, el problema
                                // es probable que sea el filtro y no que la tabla esté vacía. Distinguimos
                                // para que el usuario sepa qué acción tomar.
                                const hayFiltros = (globalFilter?.trim().length ?? 0) > 0 || hasFiltersActive;
                                return (
                                    <tr>
                                        <td colSpan={table.getVisibleLeafColumns().length} className="p-0">
                                            <div className="sticky left-0 w-screen max-w-full">
                                                <div className="flex flex-col items-center justify-center gap-3 text-gray-300 dark:text-slate-600 min-h-[300px]">
                                                    <svg className="w-14 h-14" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1}>
                                                        <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                                                    </svg>
                                                    <span className="text-sm font-medium text-gray-400 dark:text-slate-500">
                                                        {hayFiltros ? "No hay resultados para los filtros actuales." : emptyMessage}
                                                    </span>
                                                    {hayFiltros && onClearAllFilters && (
                                                        <button
                                                            type="button"
                                                            onClick={() => {
                                                                onClearAllFilters();
                                                                if (globalFilter) setGlobalFilter("");
                                                            }}
                                                            className="rounded-md border border-gray-300 bg-white px-3 py-1 text-xs font-medium text-gray-600 shadow-sm hover:bg-gray-50 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700"
                                                        >
                                                            Limpiar filtros
                                                        </button>
                                                    )}
                                                </div>
                                            </div>
                                        </td>
                                    </tr>
                                );
                            })()
                        ) : (
                            <>
                                {table.getRowModel().rows.map((row) => {
                                    const subRow = renderSubRow ? renderSubRow(row) : null;
                                    const customRowClassName = getRowClassName ? getRowClassName(row) : "";
                                    const hasCustomBackground = customRowClassName.trim().length > 0;
                                    return (
                                        <Fragment key={row.id}>
                                            <tr className={`${rowBorder} text-gray-700 dark:text-slate-200 transition-colors ${hasCustomBackground ? "" : zebra} ${customRowClassName}`}>
                                                {row.getVisibleCells().map((cell) => (
                                                    <td
                                                        key={cell.id}
                                                        className={`py-1 px-3 text-center leading-none align-middle transition-colors ${cellBorder} ${
                                                            updatedCellKey === cell.id
                                                                ? "bg-emerald-50/80 ring-1 ring-inset ring-emerald-200 dark:bg-emerald-500/10 dark:ring-emerald-500/30"
                                                                : ""
                                                        }`}
                                                        style={{ width: cell.column.getSize() }}
                                                    >
                                                        {flexRender(
                                                            cell.column.columnDef.cell,
                                                            cell.getContext()
                                                        )}
                                                    </td>
                                                ))}
                                            </tr>
                                            {subRow && (
                                                <tr className="border-t-0">
                                                    <td colSpan={table.getVisibleLeafColumns().length} className="p-0">
                                                        {subRow}
                                                    </td>
                                                </tr>
                                            )}
                                        </Fragment>
                                    );
                                })}
                                {/* No rellenamos con filas vacías: la tabla muestra solo las filas
                                    reales de la página actual, sin "fantasmas" en blanco. */}
                            </>
                        )}
                    </tbody>
                </table>
            </div>

            {/* Controles de paginación FIXED BOTTOM */}
            <div className="relative shrink-0 px-4 py-2 border-t border-gray-200 dark:border-slate-700 bg-gray-50/80 dark:bg-slate-800/60 flex justify-center items-center">
                {totalRecords != null && (
                    <div className="absolute left-4 text-xs text-gray-500 dark:text-slate-400">
                        {data.length} filas · {totalRecords.toLocaleString("es-AR")} totales
                    </div>
                )}
                <PaginationControls
                    table={table}
                    pageIndex={pageIndex}
                    pageSize={pageSize}
                    pageCount={pageCount}
                    onPageSizeChange={onPageSizeChange}
                />
            </div>

            {/* Menú contextual */}
            {menu && (
                <div style={{ top: menu.y, left: menu.x, position: 'fixed', zIndex: 9999 }}>
                    <ColumnContextMenu
                        columnId={menu.columnId}
                        onClose={() => setMenu(null)}
                        onFilter={(value, labels) => {
                            if (onColumnFilterChange) {
                                onColumnFilterChange(menu.columnId, value, labels);
                            }
                            setMenu(null);
                        }}
                        currentFilters={getActiveFilter ? getActiveFilter(menu.columnId) : []}
                    />
                </div>
            )}
        </div>
        </EditingCellProvider>
    );
};

export default Table;

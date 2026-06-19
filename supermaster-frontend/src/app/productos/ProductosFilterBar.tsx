"use client";

import { AdjustmentsHorizontalIcon, ChevronDownIcon } from "@heroicons/react/24/outline";
import MultiSelectFilter from "./filters/MultiSelectFilter";
import SegmentedFilter from "./filters/SegmentedFilter";
import {
    searchMarcas, searchClasifGral, searchClasifGastro, searchTipos, searchMateriales,
    searchProveedores, searchOrigenes, searchCatalogos, searchAptos, searchClientes,
    searchCanales, searchMlas,
} from "./productosService";

type PanelProps = {
    filters: Record<string, unknown>;
    filterValueLabels: Record<string, Record<string, string>>;
    /** Aplica un cambio de filtro. value: array (multi) o valor único/null (segmentado). */
    onFilterChange: (apiParam: string, value: unknown, labels?: Record<string, string>) => void;
    onClearAll: () => void;
    /** Cantidad de filtros activos (excluye la búsqueda de texto). */
    activeCount: number;
};

type MultiDef = {
    label: string;
    apiParam: string;
    loadOptions: (q: string, size?: number) => Promise<{ id: number | string; label: string }[]>;
};

// Agrupaciones visuales del panel.
const CLASIFICACION: MultiDef[] = [
    { label: "Rubro", apiParam: "clasifGralIds", loadOptions: searchClasifGral },
    { label: "Gastro", apiParam: "clasifGastroIds", loadOptions: searchClasifGastro },
    { label: "Tipo", apiParam: "tipoIds", loadOptions: searchTipos },
    { label: "Material", apiParam: "materialIds", loadOptions: searchMateriales },
];

const COMERCIAL: MultiDef[] = [
    { label: "Marca", apiParam: "marcaIds", loadOptions: searchMarcas },
    { label: "Proveedor", apiParam: "proveedorIds", loadOptions: searchProveedores },
    { label: "Origen", apiParam: "origenIds", loadOptions: searchOrigenes },
    { label: "Catálogo", apiParam: "catalogoIds", loadOptions: searchCatalogos },
    { label: "Apto", apiParam: "aptoIds", loadOptions: searchAptos },
    { label: "Cliente", apiParam: "clienteIds", loadOptions: searchClientes },
    { label: "Canal", apiParam: "canalIds", loadOptions: searchCanales },
    { label: "MLA", apiParam: "mlaIds", loadOptions: searchMlas },
];

// Tags es una lista en el backend (List<Tag>): multi-select de opciones fijas.
const searchTags = async (): Promise<{ id: string; label: string }[]> => [
    { id: "MAQUINA", label: "Máquina" },
    { id: "REPUESTO", label: "Repuesto" },
    { id: "MENAJE", label: "Menaje" },
    { id: "INSUMO", label: "Insumo" },
];

function asIdArray(value: unknown): (number | string)[] {
    if (Array.isArray(value)) return value as (number | string)[];
    if (value === undefined || value === null || value === "") return [];
    return [value as number | string];
}

function GroupTitle({ children }: { children: React.ReactNode }) {
    return <h3 className="mb-0.5 text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">{children}</h3>;
}

/**
 * Botón compacto para la toolbar de la tabla. Abre/cierra el panel de filtros
 * y muestra cuántos filtros hay activos.
 */
export function ProductosFilterToggle({ expanded, onToggle, activeCount }: { expanded: boolean; onToggle: () => void; activeCount: number }) {
    return (
        <button
            type="button"
            onClick={onToggle}
            className={`inline-flex shrink-0 items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-sm font-medium transition-colors ${
                expanded || activeCount > 0
                    ? "border-blue-300 bg-blue-50 text-blue-600 dark:border-blue-700 dark:bg-blue-900/30 dark:text-blue-400"
                    : "border-gray-200 bg-white text-gray-500 hover:bg-gray-100 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-400 dark:hover:bg-blue-500/20 dark:hover:text-blue-300"
            }`}
            title="Filtros"
        >
            <AdjustmentsHorizontalIcon className="h-4 w-4" />
            <span>Filtros</span>
            {activeCount > 0 && (
                <span className="inline-flex h-4 min-w-[1rem] items-center justify-center rounded-full bg-blue-600 px-1 text-[10px] font-bold text-white">
                    {activeCount}
                </span>
            )}
            <ChevronDownIcon className={`h-3.5 w-3.5 transition-transform ${expanded ? "rotate-180" : ""}`} />
        </button>
    );
}

/**
 * Panel de filtros (grilla). Pensado para mostrarse como fila desplegable debajo
 * de la toolbar de la tabla. El estado de abierto/cerrado lo controla la página.
 */
export default function ProductosFilterPanel({ filters, filterValueLabels, onFilterChange, onClearAll, activeCount }: PanelProps) {
    const renderMulti = (def: MultiDef) => (
        <MultiSelectFilter
            key={def.apiParam}
            label={def.label}
            apiParam={def.apiParam}
            loadOptions={def.loadOptions}
            value={asIdArray(filters[def.apiParam])}
            valueLabels={filterValueLabels[def.apiParam]}
            onChange={(apiParam, ids, labels) => onFilterChange(apiParam, ids, labels)}
        />
    );

    return (
        <div className="shrink-0 space-y-1 border-b border-gray-200 bg-gray-50/60 px-4 py-1.5 dark:border-slate-700 dark:bg-slate-800/40">
            <div>
                <GroupTitle>Clasificación</GroupTitle>
                <div className="grid grid-cols-2 gap-x-2 gap-y-1 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
                    {CLASIFICACION.map(renderMulti)}
                </div>
            </div>

            <div>
                <GroupTitle>Comercial</GroupTitle>
                <div className="grid grid-cols-2 gap-x-2 gap-y-1 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
                    {COMERCIAL.map(renderMulti)}
                </div>
            </div>

            <div>
                <GroupTitle>Atributos</GroupTitle>
                <div className="grid grid-cols-1 gap-x-2 gap-y-1 sm:grid-cols-2 lg:grid-cols-4">
                    <SegmentedFilter
                        label="Compuesto"
                        value={filters.esCombo as boolean | undefined}
                        onChange={(v) => onFilterChange("esCombo", v)}
                        options={[
                            { value: null, label: "Todos" },
                            { value: false, label: "Individual" },
                            { value: true, label: "Combo" },
                        ]}
                    />
                    <SegmentedFilter
                        label="Estado"
                        value={filters.activo as boolean | undefined}
                        onChange={(v) => onFilterChange("activo", v)}
                        options={[
                            { value: null, label: "Todos" },
                            { value: true, label: "Activos" },
                            { value: false, label: "Inactivos" },
                        ]}
                    />
                    <SegmentedFilter
                        label="Tag reposición"
                        value={filters.tagReposicion as string | undefined}
                        onChange={(v) => onFilterChange("tagReposicion", v)}
                        options={[
                            { value: null, label: "Todos" },
                            { value: "PRIO", label: "PRIO" },
                            { value: "LIQ", label: "LIQ" },
                        ]}
                    />
                    <MultiSelectFilter
                        label="Tag"
                        apiParam="tags"
                        loadOptions={searchTags}
                        value={asIdArray(filters.tags)}
                        valueLabels={filterValueLabels.tags}
                        onChange={(apiParam, ids, labels) => onFilterChange(apiParam, ids, labels)}
                    />
                </div>
            </div>

            {activeCount > 0 && (
                <div className="flex items-center justify-between border-t border-gray-200 pt-1.5 dark:border-slate-700">
                    <span className="text-xs text-slate-500 dark:text-slate-400">{activeCount} filtro{activeCount !== 1 ? "s" : ""} activo{activeCount !== 1 ? "s" : ""}</span>
                    <button
                        type="button"
                        onClick={onClearAll}
                        className="text-xs font-medium text-slate-500 hover:text-red-500 dark:text-slate-400 dark:hover:text-red-400"
                    >
                        Limpiar todo
                    </button>
                </div>
            )}
        </div>
    );
}

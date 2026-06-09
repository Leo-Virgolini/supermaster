"use client";
import { useCallback, useEffect, useMemo, useState } from "react";
import { notificar } from "../utils/notificar";
import { CalculatorIcon, InformationCircleIcon, CheckIcon, XMarkIcon, ChevronDownIcon, FunnelIcon } from "@heroicons/react/24/outline";
import { confirmDialog } from "../utils/confirmDialog";
import CreateButton from "../components/Button/CreateButton";
import DeleteButton from "../components/Button/DeleteButton";
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import SearchInput from "../components/SearchInput/SearchInput";
import Button from "../components/Button/Button";
import Modal from "../components/Modal/Modal";
import { useAuth } from "../context/AuthContext";
import { useConceptosGasto } from "./useConceptosGastos";
import { getConceptosGastoAPI, getCanalesDelConceptoAPI, type NaturalezaConcepto } from "./conceptosGastosService";
import { NATURALEZAS_INFO } from "../canal-formula/naturaleza";
import { APLICA_SOBRE_INFO, getAplicaSobreInfo } from "../canal-formula/aplica-sobre";
import { ETAPAS_INFO } from "../canal-formula/etapas";
import { BuildingStorefrontIcon } from "@heroicons/react/24/outline";
import { getColumns } from "./columns";
import { type SortingState } from "@tanstack/react-table";
import { getCanalesAPI } from "../canales/canalesService";
import { getConceptosPorCanalAPI } from "../canales/canalConceptosService";
import CanalSelectBadge from "../components/CanalSelectBadge/CanalSelectBadge";

export default function ConceptosGastoPage() {
    const { hasPermiso } = useAuth();
    const canEdit = hasPermiso("PRECIOS_EDITAR");
    const [pageIndex, setPageIndex] = useState(0);
    const [pageSize, setPageSize] = useState(() => getInitialPageSize("conceptos-gastos"));
    const [filters, setFilters] = useState<any>({ search: "" });
    const [sorting, setSorting] = useState<SortingState>([{ id: "nombre", desc: false }]);
    const [rowSelection, setRowSelection] = useState({});

    // Modal y Form
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [concepto, setConcepto] = useState("");
    const [porcentaje, setPorcentaje] = useState(0);
    const [aplicaSobre, setAplicaSobre] = useState("GASTO_SOBRE_COSTO");
    const [naturaleza, setNaturaleza] = useState<NaturalezaConcepto | "">(""); // "" = usar default del aplicaSobre
    const [descripcion, setDescripcion] = useState("");

    const [showAplicaSobreHelp, setShowAplicaSobreHelp] = useState(false);
    const [showNaturalezaHelp, setShowNaturalezaHelp] = useState(false);
    const [ayudaAbierta, setAyudaAbierta] = useState(false);

    // Modal canales del concepto
    const [canalesModal, setCanalesModal] = useState<{ isOpen: boolean; loading: boolean; conceptoNombre: string; canales: string[] }>({
        isOpen: false, loading: false, conceptoNombre: "", canales: [],
    });

    // Filtro por canal: muestra solo los conceptos asignados al canal elegido.
    const [canalesList, setCanalesList] = useState<{ id: number; nombre: string }[]>([]);
    const [canalFilterId, setCanalFilterId] = useState<number | null>(null);
    const [canalConceptoIds, setCanalConceptoIds] = useState<Set<number> | null>(null);
    const [isLoadingCanalConceptos, setIsLoadingCanalConceptos] = useState(false);

    useEffect(() => {
        let cancelled = false;
        getCanalesAPI(0, 500, {}, "nombre,asc")
            .then((json) => {
                if (cancelled) return;
                const items = (json.content ?? []).map((c: any) => ({ id: c.id, nombre: c.nombre }));
                setCanalesList(items);
            })
            .catch(() => { /* silencio: filtro queda deshabilitado si falla */ });
        return () => { cancelled = true; };
    }, []);

    useEffect(() => {
        if (canalFilterId == null) {
            setCanalConceptoIds(null);
            return;
        }
        let cancelled = false;
        setIsLoadingCanalConceptos(true);
        getConceptosPorCanalAPI(canalFilterId)
            .then((items) => {
                if (cancelled) return;
                setCanalConceptoIds(new Set(items.map((i) => i.conceptoId)));
            })
            .catch(() => {
                if (cancelled) return;
                setCanalConceptoIds(new Set());
                notificar.error("Error al obtener los conceptos del canal");
            })
            .finally(() => { if (!cancelled) setIsLoadingCanalConceptos(false); });
        return () => { cancelled = true; };
    }, [canalFilterId]);

    // Cuando filtramos por canal, traemos todos los conceptos del backend en una sola página
    // y filtramos client-side por los IDs del canal. Para listas pequeñas (<500) es suficiente.
    const effectivePageSize = canalFilterId != null ? 500 : pageSize;
    const effectivePageIndex = canalFilterId != null ? 0 : pageIndex;

    const { conceptos: conceptosRaw, totalRecords: totalRecordsRaw, isLoading: isLoadingRaw, createConcepto, deleteConcepto, updateConcepto } =
        useConceptosGasto(effectivePageIndex, effectivePageSize, filters, sorting);

    const conceptos = useMemo(() => {
        if (canalFilterId == null || canalConceptoIds == null) return conceptosRaw;
        return conceptosRaw.filter((c) => canalConceptoIds.has(c.id));
    }, [conceptosRaw, canalFilterId, canalConceptoIds]);

    const totalRecords = canalFilterId != null ? conceptos.length : totalRecordsRaw;
    const isLoading = isLoadingRaw || (canalFilterId != null && isLoadingCanalConceptos);
    const pageCount = canalFilterId != null
        ? 1
        : (totalRecords > 0 ? Math.ceil(totalRecords / pageSize) : 1);
    const selectedIds = Object.keys(rowSelection).map(Number);

    const handleVerCanales = useCallback(async (conceptoId: number) => {
        const nombre = conceptos.find(c => c.id === conceptoId)?.nombre ?? "";
        setCanalesModal({ isOpen: true, loading: true, conceptoNombre: nombre, canales: [] });
        try {
            const canales = await getCanalesDelConceptoAPI(conceptoId);
            setCanalesModal(prev => ({ ...prev, loading: false, canales }));
        } catch {
            setCanalesModal(prev => ({ ...prev, loading: false, canales: [] }));
            notificar.error("Error al obtener canales");
        }
    }, [conceptos]);

    const columns = useMemo(() => getColumns(canEdit, handleVerCanales), [canEdit, handleVerCanales]);

    const handleExportAll = async () => {
        const sortParam = sorting.length > 0 ? sorting[0].id + "," + (sorting[0].desc ? "desc" : "asc") : "id,asc";
        const res = await getConceptosGastoAPI(0, 99999, filters, sortParam);
        return res.content;
    };

    const aplicaSobreEsFlag = aplicaSobre.startsWith("FLAG_");

    const handleCreate = async () => {
        try {
            setIsSaving(true);
            // Para flags el porcentaje se ignora — mandamos null explícito.
            // El backend igual lo normaliza, pero así el frontend es consistente.
            const porcentajeAEnviar = aplicaSobreEsFlag ? null : porcentaje;
            // naturaleza === "" significa "usar default del aplicaSobre" → no la enviamos.
            const naturalezaAEnviar = naturaleza === "" ? null : naturaleza;
            await createConcepto({ nombre: concepto, porcentaje: porcentajeAEnviar, aplicaSobre, naturaleza: naturalezaAEnviar, descripcion });
            setConcepto(""); setPorcentaje(0); setAplicaSobre("GASTO_SOBRE_COSTO"); setNaturaleza(""); setDescripcion(""); setIsModalOpen(false);
        } catch (e) { /* hook already toasts */ } finally { setIsSaving(false); }
    };

    const handleDelete = async () => {
        const nombres = selectedIds.map(i => conceptos.find(c => c.id === i)?.nombre).filter(Boolean);
        const detalle = nombres.length <= 3 ? nombres.join(", ") : `${nombres.slice(0, 3).join(", ")} y ${nombres.length - 3} más`;
        if (!(await confirmDialog({ title: "Eliminar conceptos", message: `¿Eliminar ${selectedIds.length === 1 ? `"${detalle}"` : `${selectedIds.length} conceptos (${detalle})`}?`, confirmText: "Eliminar", variant: "danger" }))) return;
        try { await deleteConcepto(selectedIds); setRowSelection({}); } catch (e) { /* hook already toasts */ }
    };

    const handleUpdate = async (rowIndex: number, columnId: string, value: unknown) => {
        const itemOriginal = conceptos[rowIndex];
        try { await updateConcepto(itemOriginal.id, { [columnId]: value }); } catch (e) { /* hook already toasts */ }
    };

    const apiMapping: Record<string, string> = {
        // "nombreColumnaFrontend": "nombreParametroBackend"
    };

    const handleGlobalSearch = (valor: string) => {
        setFilters((prev: any) => ({
            ...prev,
            search: valor
        }));
        setPageIndex(0);
    };

    const handleColumnFilterChange = (columnId: string, value: any) => {
        const apiParam = apiMapping[columnId] || columnId;
        setFilters((prev: any) => {
            const newFilters = { ...prev };
            if (value === undefined || value === null || value === "") {
                delete newFilters[apiParam];
            } else {
                newFilters[apiParam] = value;
            }
            return newFilters;
        });
        setPageIndex(0);
    };

    // `hasActiveFilters`: hay filtros por columna activos (no contamos search,
    // porque eso ya lo detecta el Table vía globalFilter).
    const hasActiveFilters = Object.entries(filters).some(([key, value]) => {
        if (key === "search") return false;
        if (value === undefined || value === null || value === "") return false;
        if (Array.isArray(value) && value.length === 0) return false;
        return true;
    });

    const clearAllFilters = () => {
        setFilters({});
        setPageIndex(0);
    };

    const hasSelection = selectedIds.length > 0;

    return (
        <main className="p-4 bg-gray-50 min-h-0 flex flex-col overflow-hidden">
            <div className="flex justify-between items-center mb-3">
                <div>
                    <h1 className="text-3xl font-bold text-gray-800 dark:text-slate-100 flex items-center gap-2">
                        <CalculatorIcon className="w-8 h-8 text-gray-600 dark:text-slate-400" />
                        Conceptos de Cálculo
                    </h1>
                </div>

                <div className="flex gap-2 items-center">
                    {canEdit && hasSelection && (
                        <DeleteButton onClick={handleDelete}>
                            Borrar ({selectedIds.length})
                        </DeleteButton>
                    )}
                    <CreateButton onClick={() => setIsModalOpen(true)} disabled={!canEdit}>
                        Crear Concepto
                    </CreateButton>
                </div>
            </div>

            <div className="mb-3 rounded-lg border border-blue-200 bg-blue-50 dark:border-blue-800/60 dark:bg-blue-900/20">
                <button
                    type="button"
                    onClick={() => setAyudaAbierta((v) => !v)}
                    className="flex w-full items-center justify-between gap-2 px-4 py-2 text-left text-sm font-semibold text-blue-900 dark:text-blue-200"
                >
                    <span className="flex items-center gap-2">
                        <InformationCircleIcon className="h-5 w-5" />
                        ¿Para qué sirven los Conceptos de Cálculo?
                    </span>
                    <ChevronDownIcon className={`h-4 w-4 transition-transform ${ayudaAbierta ? "rotate-180" : ""}`} />
                </button>
                {ayudaAbierta && (
                    <div className="border-t border-blue-200 px-4 py-3 text-sm text-blue-900 dark:border-blue-800/60 dark:text-blue-100">
                        <p className="mb-3">
                            Son las <strong>piezas con las que se arma el cálculo de precio de cada canal</strong>: gastos, comisiones, impuestos,
                            márgenes y descuentos. Cada concepto se asigna a uno o varios canales y aporta su efecto en una etapa específica del
                            cálculo (Costo → Margen → Impuestos → Precio → Post-Precio).
                        </p>

                        <div className="mb-3 rounded-md bg-white/60 p-3 text-xs dark:bg-blue-950/40">
                            <p className="mb-2 font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
                                Ejemplo
                            </p>
                            <p>
                                El concepto <span className="font-mono">ML_COMI</span> con porcentaje{" "}
                                <span className="font-mono">13%</span> y &quot;Aplica Sobre = Comisión sobre PVP&quot;, asignado al canal{" "}
                                <span className="font-mono">ML</span>, hace que el motor calcule:{" "}
                                <span className="font-mono">PVP = costo / (1 − 13/100)</span>.
                            </p>
                        </div>

                        <div className="mb-3">
                            <p className="mb-1 font-semibold">Campos clave:</p>
                            <ul className="ml-4 list-disc space-y-0.5">
                                <li><strong>Nombre</strong>: identificador corto (ej: <span className="font-mono">ML_COMI</span>, <span className="font-mono">KG_DESC_5</span>).</li>
                                <li><strong>Porcentaje</strong>: valor numérico que aplica al cálculo. Para <em>flags</em> (🚩) este valor se ignora.</li>
                                <li><strong>Aplica Sobre</strong>: define la <em>matemática del PVP</em> — en qué etapa y con qué fórmula se aplica. Mirá la guía detallada en el botón <strong>ℹ</strong> de la toolbar para ver los 20 valores agrupados por etapa.</li>
                                <li>
                                    <strong>Naturaleza</strong>: define cómo el concepto <em>impacta los indicadores</em>{" "}
                                    (ganancia, márgenes, markup) — independiente de Aplica Sobre. Por defecto cada Aplica
                                    Sobre tiene su naturaleza coherente (⚙ Auto); podés sobreescribirla cuando dos conceptos
                                    comparten matemática pero distinto efecto contable. Mirá la guía detallada en el botón{" "}
                                    <strong>ℹ</strong> de la toolbar para ver los 8 valores con su impacto contable.
                                </li>
                                <li><strong>Descripción</strong>: nota interna para documentar para qué se usa. Se muestra como tooltip en otras pantallas.</li>
                            </ul>
                        </div>

                        <div className="rounded-md border border-amber-300 bg-amber-50 p-2.5 text-xs text-amber-900 dark:border-amber-700/60 dark:bg-amber-900/30 dark:text-amber-100">
                            <strong>Crear el concepto acá no lo activa por sí solo.</strong> Hay que <em>asignarlo a un canal</em> desde
                            <strong> Canales → Conceptos</strong>. Hasta que no esté asignado, no afecta a ningún precio.
                        </div>
                    </div>
                )}
            </div>

            <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                <Table
                    searchSlot={
                        <div className="flex flex-wrap items-center gap-2">
                            <SearchInput
                                placeholder="Buscar concepto por nombre..."
                                onSearch={(val) => { if (val !== filters.search) handleGlobalSearch(val); }}
                                initialValue={filters.search}
                                className="w-64"
                            />
                            <div className="flex items-center gap-1.5">
                                <FunnelIcon className="h-4 w-4 text-slate-400" />
                                <CanalSelectBadge
                                    canales={canalesList}
                                    value={canalFilterId}
                                    onChange={(id) => { setCanalFilterId(id); setPageIndex(0); }}
                                    allowAll
                                    className="w-56"
                                />
                            </div>
                        </div>
                    }
                    tableId="conceptos-gastos"
                    isLoading={isLoading}
                    globalFilter={filters.search}
                    setGlobalFilter={handleGlobalSearch}
                    data={conceptos}
                    columns={columns}
                    pageCount={pageCount}
                    pageIndex={pageIndex}
                    pageSize={pageSize}
                    onPageChange={setPageIndex}
                    onPageSizeChange={setPageSize}
                    totalRecords={totalRecords}
                    sorting={sorting}
                    setSorting={setSorting}
                    rowSelection={rowSelection}
                    setRowSelection={setRowSelection}
                    updateData={handleUpdate}
                    onColumnFilterChange={handleColumnFilterChange}
                    hasFiltersActive={hasActiveFilters}
                    onClearAllFilters={clearAllFilters}
                    getActiveFilter={(columnId) => filters[apiMapping[columnId] || columnId]}
                    onExportAll={handleExportAll}
                    exportFilename="conceptos-gastos"
                    toolbarExtra={
                        <div className="flex items-center gap-1">
                            <button
                                onClick={() => setShowAplicaSobreHelp(true)}
                                className="shrink-0 p-1.5 rounded-lg border border-blue-200 dark:border-blue-800 bg-blue-50 dark:bg-blue-900/20 hover:bg-blue-100 dark:hover:bg-blue-900/40 text-blue-600 dark:text-blue-400 transition-colors flex items-center gap-1"
                                title="Guía: Aplica Sobre"
                            >
                                <InformationCircleIcon className="w-4 h-4" />
                                <span className="text-xs font-semibold">Aplica Sobre</span>
                            </button>
                            <button
                                onClick={() => setShowNaturalezaHelp(true)}
                                className="shrink-0 p-1.5 rounded-lg border border-purple-200 dark:border-purple-800 bg-purple-50 dark:bg-purple-900/20 hover:bg-purple-100 dark:hover:bg-purple-900/40 text-purple-600 dark:text-purple-400 transition-colors flex items-center gap-1"
                                title="Guía: Naturaleza"
                            >
                                <InformationCircleIcon className="w-4 h-4" />
                                <span className="text-xs font-semibold">Naturaleza</span>
                            </button>
                        </div>
                    }
                />
            </div>

            <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title="Nuevo Concepto"
                footer={<><Button variant="light" onClick={() => setIsModalOpen(false)}><XMarkIcon className="w-4 h-4" /> Cancelar</Button><Button variant="dark" onClick={handleCreate} disabled={!concepto || isSaving}><CheckIcon className="w-4 h-4" /> {isSaving ? "Creando Concepto..." : "Crear Concepto"}</Button></>}>
                <div className="flex flex-col gap-4">
                    <label className="block">
                        <span className="font-bold text-gray-700">Concepto <span className="text-red-500">*</span></span>
                        <input type="text" className="w-full border p-2 rounded" value={concepto} onChange={e => setConcepto(e.target.value)} placeholder="Ej: IVA, IIBB" required />
                    </label>
                    <label className="block">
                        <span className="font-bold text-gray-700">
                            Porcentaje {!aplicaSobreEsFlag && <span className="text-red-500">*</span>}
                        </span>
                        <input
                            type="number"
                            className={`w-full border p-2 rounded ${aplicaSobreEsFlag ? "bg-gray-100 text-gray-400 cursor-not-allowed" : ""}`}
                            value={aplicaSobreEsFlag ? "" : porcentaje}
                            onChange={e => setPorcentaje(Number(e.target.value))}
                            disabled={aplicaSobreEsFlag}
                            placeholder={aplicaSobreEsFlag ? "El flag no usa porcentaje" : ""}
                            required={!aplicaSobreEsFlag}
                        />
                        {aplicaSobreEsFlag && (
                            <span className="text-xs text-gray-500 italic">
                                Los flags solo activan una funcionalidad — el porcentaje se ignora.
                            </span>
                        )}
                    </label>
                    <label className="block">
                        <span className="font-bold text-gray-700">Aplica Sobre <span className="text-red-500">*</span></span>
                        <select className="w-full border p-2 rounded text-sm" required value={aplicaSobre} onChange={e => setAplicaSobre(e.target.value)}>
                            {ETAPAS_INFO.map((etapa) => (
                                <optgroup key={etapa.id} label={`${etapa.icon} ${etapa.label}`}>
                                    {APLICA_SOBRE_INFO.filter((a) => a.etapa === etapa.id).map((a) => (
                                        <option key={a.id} value={a.id}>{a.icon} {a.label}</option>
                                    ))}
                                </optgroup>
                            ))}
                        </select>
                    </label>
                    <label className="block">
                        <span className="font-bold text-gray-700">Naturaleza contable</span>
                        <select
                            className="w-full border p-2 rounded text-sm"
                            value={naturaleza}
                            onChange={e => setNaturaleza(e.target.value as NaturalezaConcepto | "")}
                        >
                            <option value="">⚙ Auto (usar default del &quot;Aplica Sobre&quot;)</option>
                            {NATURALEZAS_INFO.map((n) => (
                                <option key={n.id} value={n.id}>{n.icon} {n.label}</option>
                            ))}
                        </select>
                        <span className="text-xs text-gray-500 italic">
                            Define cómo el concepto impacta la GANANCIA en los indicadores. Dejá &quot;Auto&quot; salvo que necesites override.
                        </span>
                    </label>
                    <label className="block">
                        <span className="font-bold text-gray-700">Descripción</span>
                        <input type="text" className="w-full border p-2 rounded" value={descripcion} onChange={e => setDescripcion(e.target.value)} placeholder="Opcional..." />
                    </label>
                </div>
            </Modal>

            {/* Modal canales del concepto */}
            <Modal
                isOpen={canalesModal.isOpen}
                onClose={() => setCanalesModal(prev => ({ ...prev, isOpen: false }))}
                title={`Canales que usan "${canalesModal.conceptoNombre}"`}
                size="sm"
            >
                {canalesModal.loading ? (
                    <div className="flex justify-center py-8">
                        <div className="w-6 h-6 border-2 border-gray-200 border-t-blue-500 rounded-full animate-spin" />
                    </div>
                ) : canalesModal.canales.length === 0 ? (
                    <p className="text-sm text-gray-500 dark:text-slate-400 py-4 text-center">
                        Este concepto no esta asignado a ningun canal.
                    </p>
                ) : (
                    <div className="flex flex-col gap-1.5">
                        {canalesModal.canales.map((canal) => (
                            <div
                                key={canal}
                                className="flex items-center gap-2 px-3 py-2 rounded-lg bg-gray-50 dark:bg-slate-800 border border-gray-100 dark:border-slate-700"
                            >
                                <BuildingStorefrontIcon className="w-4 h-4 text-cyan-500 dark:text-cyan-400 shrink-0" />
                                <span className="text-sm font-medium text-gray-800 dark:text-slate-200">{canal}</span>
                            </div>
                        ))}
                        <p className="text-xs text-gray-400 dark:text-slate-500 mt-2 text-center">
                            {canalesModal.canales.length} {canalesModal.canales.length === 1 ? "canal" : "canales"}
                        </p>
                    </div>
                )}
            </Modal>

            {/* Modal de ayuda: Aplica Sobre */}
            <Modal isOpen={showAplicaSobreHelp} onClose={() => setShowAplicaSobreHelp(false)} title="Guía: Aplica Sobre" size="lg">
                <div className="max-h-[65vh] overflow-y-auto space-y-5 text-sm">
                    <p className="text-gray-500 dark:text-slate-400">
                        Cada concepto se aplica en una etapa específica del cálculo de precio. Los conceptos marcados con <span className="font-bold">🚩</span> son <em>flags</em>: solo habilitan una funcionalidad, el porcentaje se ignora.
                    </p>

                    <div className="rounded-xl bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 p-4 space-y-2">
                        <h3 className="font-bold text-blue-900 dark:text-blue-300">{ETAPAS_INFO[0].icon} 1. Costo</h3>
                        <dl className="space-y-1.5 text-gray-700 dark:text-slate-300">
                            <div><dt className="font-semibold inline">{getAplicaSobreInfo("GASTO_SOBRE_COSTO").icon} % sobre Costo:</dt><dd className="inline ml-1">Multiplica el costo base. Ej: Embalaje +2% → COSTO × (1 + 2/100)</dd></div>
                            <div><dt className="font-semibold inline">{getAplicaSobreInfo("FLAG_FINANCIACION_PROVEEDOR").icon} Financiación Proveedor:</dt><dd className="inline ml-1">Usa el % de financiación del proveedor del producto. El valor viene de la ficha del proveedor.</dd></div>
                        </dl>
                    </div>

                    <div className="rounded-xl bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 p-4 space-y-2">
                        <h3 className="font-bold text-green-900 dark:text-green-300">{ETAPAS_INFO[1].icon} 2. Margen</h3>
                        <dl className="space-y-1.5 text-gray-700 dark:text-slate-300">
                            <div><dt className="font-semibold inline">{getAplicaSobreInfo("AJUSTE_MARGEN_PUNTOS").icon} Ajuste Margen (puntos):</dt><dd className="inline ml-1">Suma/resta puntos al margen. Ej: margen 60% + 25pts = 85%</dd></div>
                            <div><dt className="font-semibold inline">{getAplicaSobreInfo("AJUSTE_MARGEN_PROPORCIONAL").icon} Ajuste Margen (%):</dt><dd className="inline ml-1">Ajusta proporcionalmente. Ej: margen 60% × (1 - 12/100) = 52.8%</dd></div>
                            <div><dt className="font-semibold inline">{getAplicaSobreInfo("FLAG_USAR_MARGEN_MINORISTA").icon} Margen Minorista:</dt><dd className="inline ml-1">Usa el margen minorista del producto (por defecto si no hay flag).</dd></div>
                            <div><dt className="font-semibold inline">{getAplicaSobreInfo("FLAG_USAR_MARGEN_MAYORISTA").icon} Margen Mayorista:</dt><dd className="inline ml-1">Usa el margen mayorista del producto en vez del minorista.</dd></div>
                            <div><dt className="font-semibold inline">{getAplicaSobreInfo("GASTO_POST_GANANCIA").icon} % post Ganancia:</dt><dd className="inline ml-1">Se aplica después de calcular ganancia, antes de impuestos.</dd></div>
                        </dl>
                    </div>

                    <div className="rounded-xl bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 p-4 space-y-2">
                        <h3 className="font-bold text-amber-900 dark:text-amber-300">{ETAPAS_INFO[2].icon} 3. Impuestos</h3>
                        <dl className="space-y-1.5 text-gray-700 dark:text-slate-300">
                            <div><dt className="font-semibold inline">{getAplicaSobreInfo("FLAG_APLICAR_IVA").icon} Aplicar IVA:</dt><dd className="inline ml-1">Habilita el IVA del producto para este canal. Sin este flag, IVA = 0%.</dd></div>
                            <div><dt className="font-semibold inline">{getAplicaSobreInfo("IMPUESTO_EN_FACTOR_IMP").icon} Impuesto en factor IMP:</dt><dd className="inline ml-1">Se suma al factor de impuestos junto al IVA. Ej: IIBB 5% → IMP = 1 + IVA/100 + 5/100</dd></div>
                            <div><dt className="font-semibold inline">{getAplicaSobreInfo("GASTO_POST_IMPUESTOS").icon} % post Impuestos:</dt><dd className="inline ml-1">Se aplica después de impuestos, antes de comisiones.</dd></div>
                        </dl>
                    </div>

                    <div className="rounded-xl bg-purple-50 dark:bg-purple-900/20 border border-purple-200 dark:border-purple-800 p-4 space-y-2">
                        <h3 className="font-bold text-purple-900 dark:text-purple-300">{ETAPAS_INFO[3].icon} 4. Precio</h3>
                        <dl className="space-y-1.5 text-gray-700 dark:text-slate-300">
                            <div><dt className="font-semibold inline">{getAplicaSobreInfo("FLAG_INCLUIR_ENVIO").icon} Incluir Envío:</dt><dd className="inline ml-1">Suma el costo de envío del MLA al precio.</dd></div>
                            <div><dt className="font-semibold inline">{getAplicaSobreInfo("COMISION_SOBRE_PVP").icon} Comisión s/PVP:</dt><dd className="inline ml-1">Se aplica como divisor: PVP / (1 - %/100). Se combina con cuotas. Por defecto cuenta como <em>costo de venta</em>; si querés que infle el PVP sin reducir ganancia (gasto que no asumís como costo del canal, ej: embalaje cosmético), <strong>sobreescribí la naturaleza a Inflación</strong> en el concepto.</dd></div>
                            <div><dt className="font-semibold inline">{getAplicaSobreInfo("FLAG_COMISION_ML").icon} Comisión ML:</dt><dd className="inline ml-1">Variante de Comisión s/PVP que toma el porcentaje del MLA (<span className="font-mono">mla.comisionPorcentaje</span>) en vez de usar uno propio. Mismo divisor, misma lógica de naturaleza override.</dd></div>
                            <div><dt className="font-semibold inline">{getAplicaSobreInfo("CALCULO_SOBRE_CANAL_BASE").icon} Canal Base (canal propio):</dt><dd className="inline ml-1">Calcula el PVP a partir del PVP de otro canal: PVP = PVP_base × (1 + %/100). El factor escala <em>tanto el PVP como el ingreso del dueño</em> — usalo cuando el canal hijo es del propio negocio.</dd></div>
                            <div><dt className="font-semibold inline">{getAplicaSobreInfo("CALCULO_SOBRE_CANAL_BASE_RESELLER").icon} Canal Base (reseller):</dt><dd className="inline ml-1">Variante para revendedores. El factor escala el PVP final, pero el ingreso del dueño se &quot;corta&quot; en este punto. Ej: <span className="font-mono">LIZZY GASTRO</span> compra a mayorista×0,72 (reseller) y vende ×1,5 (canal propio). El dueño solo cobra hasta el corte reseller.</dd></div>
                        </dl>
                        <div className="mt-2 rounded-md border border-amber-300 bg-amber-50 p-2 text-xs text-amber-900 dark:border-amber-700/60 dark:bg-amber-900/30 dark:text-amber-100">
                            <strong>⚠ Importante:</strong> cuando el canal tiene <em>canal base</em> configurado, <strong>solo los conceptos &quot;Canal Base&quot;</strong> del canal hijo (variantes <em>canal propio</em> y <em>reseller</em>) se aplican efectivamente. Los demás conceptos asignados (gastos, IVA, comisiones, márgenes, etc.), el margen del producto y los porcentajes de cuotas <strong>quedan ignorados</strong>. Los conceptos del canal padre tampoco se heredan al hijo.
                        </div>
                        <div className="mt-2 rounded-md border border-purple-300 bg-purple-50 p-2 text-xs text-purple-900 dark:border-purple-700/60 dark:bg-purple-900/30 dark:text-purple-100">
                            <strong>Fórmula reseller:</strong>{" "}
                            <span className="font-mono">PVP_hijo = PVP_base × ∏(factores RESELLER) × ∏(factores no-RESELLER)</span>;{" "}
                            <span className="font-mono">Ingreso_dueño = PVP_base × ∏(factores RESELLER)</span>. Es decir, los factores
                            no-RESELLER (canal propio) posteriores agregan markup pero el dueño no captura ese tramo extra.
                        </div>
                    </div>

                    <div className="rounded-xl bg-orange-50 dark:bg-orange-900/20 border border-orange-200 dark:border-orange-800 p-4 space-y-2">
                        <h3 className="font-bold text-orange-900 dark:text-orange-300">{ETAPAS_INFO[4].icon} 5. Post-precio</h3>
                        <dl className="space-y-1.5 text-gray-700 dark:text-slate-300">
                            <div><dt className="font-semibold inline">{getAplicaSobreInfo("COSTO_OCULTO_PVP").icon} Costo Oculto s/PVP:</dt><dd className="inline ml-1">Divisor adicional sobre el PVP: PVP / (1 - %/100). Representa una <em>retención adicional</em> de la plataforma que infla el precio y SÍ cuenta como costo de venta (reduce el ingreso del dueño). Ej: ML_CO_MAQCENV, KH_CO.</dd></div>
                            <div><dt className="font-semibold inline">{getAplicaSobreInfo("DESCUENTO_PORCENTUAL").icon} Descuento %:</dt><dd className="inline ml-1">Reduce el PVP: PVP × (1 - %/100). Ej: descuento máquinas.</dd></div>
                            <div><dt className="font-semibold inline">{getAplicaSobreInfo("INFLACION_DIVISOR_FINAL").icon} Inflación Divisor Final:</dt><dd className="inline ml-1">Bucket divisor independiente al final del cálculo: PVP / (1 - %/100). Infla el PVP pero NO se cuenta como costo (el dueño se queda con la plata extra). Ej: precio tachado cosmético, cupón visual.</dd></div>
                            <div><dt className="font-semibold inline">{getAplicaSobreInfo("GASTO_SIN_INFLAR_PVP").icon} Gasto sin inflar PVP:</dt><dd className="inline ml-1">Costo del dueño que NO se traslada al PVP (el precio al cliente no cambia) pero SÍ cuenta como costo de venta (reduce ingreso neto y ganancia). Ejemplo: comisión interna del vendedor que el dueño absorbe.</dd></div>
                            <div><dt className="font-semibold inline">{getAplicaSobreInfo("FLAG_APLICAR_PRECIO_INFLADO").icon} Precio Inflado:</dt><dd className="inline ml-1">Habilita la aplicación de reglas de precio inflado para este canal.</dd></div>
                        </dl>
                    </div>

                    <div className="rounded-lg bg-gray-50 dark:bg-slate-800 border border-gray-200 dark:border-slate-700 p-3">
                        <p className="text-xs text-gray-500 dark:text-slate-400 font-mono">
                            PVP = ((COSTO × gastos × (1 + GANANCIA/100) + ENVIO) × IMP) / (1 - COMISIONES/100) + PRECIO_INFLADO
                        </p>
                    </div>
                </div>
            </Modal>

            {/* Modal de ayuda: Naturaleza */}
            <Modal isOpen={showNaturalezaHelp} onClose={() => setShowNaturalezaHelp(false)} title="Guía: Naturaleza" size="lg">
                <div className="max-h-[65vh] overflow-y-auto space-y-5 text-sm">
                    <p className="text-gray-500 dark:text-slate-400">
                        La <strong>naturaleza</strong> define cómo cada concepto <em>impacta los indicadores contables</em>
                        (costo de venta, ingreso neto, ganancia, markup). Es <strong>independiente de Aplica Sobre</strong>
                        — éste último decide la <em>matemática del PVP</em>. Por defecto cada Aplica Sobre tiene su naturaleza
                        coherente (<span className="font-mono">⚙ Auto</span>); podés sobreescribirla cuando dos conceptos
                        comparten matemática pero deben tener distinto efecto contable.
                    </p>

                    <div className="rounded-md bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 p-3 text-xs">
                        <p className="font-semibold text-blue-900 dark:text-blue-200 mb-1">¿Cuándo sobreescribir el ⚙ Auto?</p>
                        <p className="text-blue-900 dark:text-blue-100">
                            Ejemplo típico: <span className="font-mono">LG_LOG</span> y <span className="font-mono">LG_MKT</span>{" "}
                            usan <span className="font-mono">GASTO_POST_GANANCIA</span> (default naturaleza{" "}
                            <span className="font-mono">Inflación</span>), pero son gastos reales del dueño (logística,
                            marketing pagados al exterior). Se sobreescribe la naturaleza a{" "}
                            <span className="font-mono">Costo de Venta</span> para que reduzcan la ganancia en los indicadores.
                        </p>
                    </div>

                    {NATURALEZAS_INFO.map((n) => (
                        <div key={n.id} className="rounded-xl border border-gray-200 dark:border-slate-700 p-4 space-y-2">
                            <div className="flex items-center gap-2">
                                <span className={`text-xs font-bold px-2 py-0.5 rounded ${n.badgeClass}`}>
                                    {n.icon} {n.label}
                                </span>
                                <span className="font-mono text-xs text-gray-500 dark:text-slate-400">{n.id}</span>
                            </div>
                            <p className="text-gray-700 dark:text-slate-300 text-sm">{n.descripcion}</p>
                        </div>
                    ))}

                    <div className="rounded-md border border-amber-300 bg-amber-50 p-2.5 text-xs text-amber-900 dark:border-amber-700/60 dark:bg-amber-900/30 dark:text-amber-100">
                        <strong>Recordá:</strong> Aplica Sobre = qué hace en la <em>fórmula del PVP</em>. Naturaleza ={" "}
                        cómo se refleja en los <em>indicadores</em> (costo de venta, ganancia, markup, etc.). Un mismo
                        concepto puede afectar el PVP de una forma y reportarse contablemente de otra.
                    </div>
                </div>
            </Modal>
        </main>
    );
}

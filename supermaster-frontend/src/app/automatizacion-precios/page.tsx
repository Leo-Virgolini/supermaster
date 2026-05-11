"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import { useAuth } from "../context/AuthContext";
import { useAutomatizacionPrecios } from "./useAutomatizacionPrecios";
import { getLog, getLogFile, getTopesPromocion, saveTopesPromocion, searchMlas, updateConfigByClave, type TopePromocionDTO } from "./automatizacionPreciosService";
import { OperacionPanel } from "../components/OperacionPanel/OperacionPanel";
import AsyncSelect from "../components/AsyncSelect/AsyncSelect";
import { getCanalesAPI } from "../canales/canalesService";
import { getCuotasPorCanalAPI } from "../canal-concepto-cuotas/canalConceptoCuotaService";
import type { CanalDTO } from "../canales/types";
import { camelToSnake } from "../utils/caseConversion";
import {
    BoltIcon,
    Cog6ToothIcon,
    ArrowTopRightOnSquareIcon,
    ShieldExclamationIcon,
    CommandLineIcon,
    DocumentTextIcon,
    TrashIcon,
    CheckIcon,
    TagIcon,
    PencilIcon,
    ArrowDownTrayIcon,
    TruckIcon,
    XCircleIcon,
    ArrowUpTrayIcon,
    CloudArrowUpIcon,
    CurrencyDollarIcon,
    StarIcon,
    MegaphoneIcon,
    GiftIcon,
    ShoppingBagIcon,
    BuildingStorefrontIcon,
    HomeModernIcon,
    CreditCardIcon,
    ClipboardDocumentListIcon,
    SparklesIcon,
} from "@heroicons/react/24/outline";
import { toast } from "sonner";
import { notificar } from "../utils/notificar";
import Link from "next/link";
import type { SyncRequest } from "./types";

type StepDef = { key: keyof SyncRequest; label: string; description: string; icon: React.ComponentType<React.SVGProps<SVGSVGElement>> };

const STEPS: StepDef[] = [
    { key: "importarCostosDux", label: "Importar costos de DUX", description: "Descarga productos desde DUX ERP y actualiza costos, IVA, proveedor y descripción", icon: ArrowDownTrayIcon },
    { key: "generarEnvio", label: "Generar precios de envío en Super Master", description: "Calcula el costo de envío para MLAs que aún no tienen precio de envío asignado", icon: TruckIcon },
    { key: "excluirPromociones", label: "Quitar promociones en ML", description: "Remueve items de todas las promociones activas antes de actualizar precios", icon: XCircleIcon },
    { key: "duxMl", label: "Subir a DUX (Mercado Libre)", description: "Actualiza la lista de precios de Mercado Libre en DUX", icon: ArrowUpTrayIcon },
    { key: "duxGastro", label: "Subir a DUX (KT Gastro)", description: "Actualiza la lista de precios de KT Gastro en DUX", icon: ArrowUpTrayIcon },
    { key: "duxNube", label: "Subir a DUX (KT Hogar)", description: "Actualiza la lista de precios de KT Hogar en DUX", icon: ArrowUpTrayIcon },
    { key: "preciosNube", label: "Subir a Tienda Nube (KT Hogar)", description: "Actualiza precios directamente en TiendaNube KT Hogar", icon: CloudArrowUpIcon },
    { key: "preciosMl", label: "Modificar precios en ML", description: "Modifica los precios de las publicaciones en Mercado Libre", icon: CurrencyDollarIcon },
    { key: "incluirPromociones", label: "Incluir en promociones ML", description: "Agrega items a las promociones disponibles (DEAL, Seller Campaign, Smart)", icon: StarIcon },
];

const configChannels = [
    {
        label: "Mercado Libre",
        canal: "canalMl", cuotas: "cuotasMl", lista: "listaPreciosMl", sinIva: "sinIvaMl",
        icon: ShoppingBagIcon,
        accent: "bg-amber-500",
        iconBg: "bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300",
    },
    {
        label: "KT Gastro",
        canal: "canalGastro", cuotas: "cuotasGastro", lista: "listaPreciosGastro", sinIva: "sinIvaGastro",
        icon: BuildingStorefrontIcon,
        accent: "bg-rose-500",
        iconBg: "bg-rose-100 text-rose-700 dark:bg-rose-500/15 dark:text-rose-300",
    },
    {
        label: "KT Hogar",
        canal: "canalHogar", cuotas: "cuotasHogar", lista: "listaPreciosHogar", sinIva: "sinIvaHogar",
        icon: HomeModernIcon,
        accent: "bg-indigo-500",
        iconBg: "bg-indigo-100 text-indigo-700 dark:bg-indigo-500/15 dark:text-indigo-300",
    },
] as const;

const promoStyles: Record<string, { icon: React.ComponentType<React.SVGProps<SVGSVGElement>>; iconBg: string; valueColor: string }> = {
    "Seller Campaign": {
        icon: MegaphoneIcon,
        iconBg: "bg-rose-100 text-rose-700 dark:bg-rose-500/15 dark:text-rose-300",
        valueColor: "text-rose-700 dark:text-rose-300",
    },
    Deal: {
        icon: GiftIcon,
        iconBg: "bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300",
        valueColor: "text-amber-700 dark:text-amber-300",
    },
    Smart: {
        icon: SparklesIcon,
        iconBg: "bg-violet-100 text-violet-700 dark:bg-violet-500/15 dark:text-violet-300",
        valueColor: "text-violet-700 dark:text-violet-300",
    },
};

export default function AutomatizacionPreciosPage() {
    const { usuario } = useAuth();
    const isAdmin = (usuario.rol || "").trim().toUpperCase() === "ADMIN";
    const { config, isLoading, error, request, toggleStep, refreshConfig } = useAutomatizacionPrecios();
    const [enProceso, setEnProceso] = useState(false);

    if (!isAdmin) {
        return (
            <main className="p-4 md:p-5 bg-gray-50 min-h-0 flex flex-col overflow-hidden dark:bg-slate-900">
                <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-amber-800 dark:border-amber-800 dark:bg-amber-500/10 dark:text-amber-200">
                    No tiene permisos para acceder a esta seccion.
                </div>
            </main>
        );
    }

    return (
        <main className="p-4 md:p-5 bg-gray-50 min-h-0 flex flex-col overflow-auto dark:bg-slate-900">
            <div className="w-full flex flex-col gap-4">
                {/* Header */}
                <div className="flex items-center gap-3">
                    <div className="flex items-center justify-center w-10 h-10 rounded-xl bg-orange-100 dark:bg-orange-500/15">
                        <BoltIcon className="w-5 h-5 text-orange-600 dark:text-orange-300" />
                    </div>
                    <div>
                        <h1 className="text-xl font-bold text-gray-900 dark:text-white">Automatización de Precios KT</h1>
                        <p className="text-sm text-gray-500 dark:text-slate-400">
                            Sincronización de precios entre Mercado Libre, DUX y TiendaNube
                        </p>
                    </div>
                </div>

                {/*
                    Banner de seguridad. El disabled={true} del OperacionPanel más abajo es INTENCIONAL:
                    mientras se valide en producción el flujo completo (DUX + ML + Tienda Nube), los
                    disparos manuales desde la UI quedan bloqueados y la sincronización corre
                    únicamente desde n8n vía POST /api/automatizacion-precios/ejecutar con X-API-Key.
                    Para re-habilitar, quitar el disabled={true} y disabledReason del OperacionPanel.
                */}
                <div className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-4 dark:border-red-800 dark:bg-red-500/10">
                    <ShieldExclamationIcon className="w-5 h-5 text-red-600 dark:text-red-400 mt-0.5 shrink-0" />
                    <div>
                        <p className="text-sm font-semibold text-red-800 dark:text-red-300">
                            Ejecución manual deshabilitada
                        </p>
                        <p className="text-xs text-red-600 dark:text-red-400 mt-0.5">
                            El disparo desde la UI está bloqueado mientras se valida el flujo en producción.
                            La sincronización corre automáticamente desde n8n. Los valores de configuración
                            siguen siendo editables.
                        </p>
                    </div>
                </div>

                {/* Configuracion activa */}
                <ConfigPanel config={config} isLoading={isLoading} error={error} onRefresh={refreshConfig} />

                {/* Topes de promocion por MLA */}
                <TopesPromocionPanel />

                {/* Pasos de sincronizacion */}
                <section className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-800/90">
                    <h2 className="text-sm font-bold text-gray-800 dark:text-slate-100 mb-4">Pasos de Sincronización</h2>
                    <div className="flex flex-col gap-2">
                        {STEPS.map(({ key, label, description, icon: Icon }, index) => (
                            <label
                                key={key}
                                className={`flex items-center gap-3 rounded-lg border border-gray-100 bg-gray-50/50 px-4 py-3 transition dark:border-slate-700 dark:bg-slate-800 ${
                                    enProceso
                                        ? "cursor-not-allowed opacity-60"
                                        : "cursor-pointer hover:bg-gray-100/80 dark:hover:bg-slate-700/50"
                                }`}
                            >
                                <input
                                    type="checkbox"
                                    checked={request[key]}
                                    onChange={() => toggleStep(key)}
                                    disabled={enProceso}
                                    className="h-4 w-4 shrink-0 rounded border-gray-300 text-blue-600 focus:ring-blue-500 disabled:cursor-not-allowed dark:border-slate-600 dark:bg-slate-700"
                                />
                                <span className="flex items-center justify-center w-7 h-7 shrink-0 rounded-lg bg-blue-50 text-blue-600 dark:bg-blue-500/10 dark:text-blue-400">
                                    <Icon className="w-4 h-4" />
                                </span>
                                <div className="flex-1 min-w-0">
                                    <div className="flex items-center gap-2">
                                        <span className="text-[10px] font-bold text-blue-500 dark:text-blue-400">{index + 1}</span>
                                        <span className="text-sm font-medium text-gray-800 dark:text-slate-200">{label}</span>
                                    </div>
                                    <p className="text-xs text-gray-500 dark:text-slate-400 mt-0.5">{description}</p>
                                </div>
                            </label>
                        ))}
                    </div>
                </section>

                {/* Panel de ejecucion */}
                <OperacionPanel
                    titulo="Sincronizar Precios"
                    descripcion="Ejecuta los pasos seleccionados en orden secuencial. Puede tardar varios minutos dependiendo de la cantidad de productos."
                    endpointIniciar="/api/automatizacion-precios/iniciar"
                    endpointEstado="/api/automatizacion-precios/estado"
                    endpointCancelar="/api/automatizacion-precios/cancelar"
                    endpointResultado="/api/automatizacion-precios/resultado"
                    confirmMessage="¿Iniciar la sincronización de precios? Este proceso puede modificar precios en ML, DUX y TiendaNube."
                    disabled={true}
                    disabledReason="Ejecución deshabilitada por seguridad. Todos los botones de acción están bloqueados hasta que se habilite manualmente."
                    requestBody={() => request}
                    onRunningChange={setEnProceso}
                    procesoId="automatizacion-precios"
                />

                {/* Log en tiempo real */}
                <LogPanel enProceso={enProceso} />

                {/* Archivo de log historico */}
                <LogFileViewer />

                {/* Links utiles */}
                <div className="flex flex-wrap gap-3">
                    <Link
                        href="/operaciones-ml"
                        className="flex items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 transition dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
                    >
                        <BoltIcon className="w-4 h-4" />
                        Operaciones ML (envios y comisiones)
                        <ArrowTopRightOnSquareIcon className="w-3 h-3" />
                    </Link>
                    <Link
                        href="/config-automatizacion"
                        className="flex items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 transition dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
                    >
                        <Cog6ToothIcon className="w-4 h-4" />
                        Configuración de Automatización Precios KT
                        <ArrowTopRightOnSquareIcon className="w-3 h-3" />
                    </Link>
                </div>
            </div>
        </main>
    );
}

function EditablePromoCard({ label, value, dbKey, onSaved }: { label: string; value: number | null; dbKey: string; onSaved: () => void }) {
    const [editing, setEditing] = useState(false);
    const [input, setInput] = useState("");
    const [saving, setSaving] = useState(false);

    const style = promoStyles[label] ?? { icon: TagIcon, iconBg: "bg-gray-100 text-gray-600", valueColor: "text-gray-800 dark:text-slate-200" };
    const Icon = style.icon;

    const startEdit = () => {
        setInput(value != null ? String(value) : "");
        setEditing(true);
    };

    const save = async () => {
        if (input === String(value ?? "")) { setEditing(false); return; }
        setSaving(true);
        try {
            await updateConfigByClave(dbKey, input.trim());
            onSaved();
            setEditing(false);
        } catch {
            notificar.error("Error guardando " + label);
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="group relative rounded-xl border border-gray-200 bg-white px-4 py-3 shadow-xs hover:border-gray-300 hover:shadow-sm transition dark:border-slate-700 dark:bg-slate-800/60 dark:hover:border-slate-600">
            <div className="flex items-center gap-2.5">
                <span className={`flex items-center justify-center w-9 h-9 shrink-0 rounded-lg ${style.iconBg}`}>
                    <Icon className="w-4 h-4" />
                </span>
                <div className="min-w-0 flex-1">
                    <div className="text-[10px] text-gray-400 dark:text-slate-500 uppercase tracking-wider font-semibold">{label}</div>
                    {editing ? (
                        <input
                            type="number"
                            value={input}
                            onChange={(e) => setInput(e.target.value)}
                            onBlur={save}
                            onKeyDown={(e) => {
                                if (e.key === "Enter") save();
                                if (e.key === "Escape") setEditing(false);
                            }}
                            disabled={saving}
                            autoFocus
                            className="w-full text-xl font-bold rounded border border-blue-400 px-1.5 py-0 outline-none focus:ring-1 focus:ring-blue-500 bg-white dark:bg-slate-700 dark:border-blue-500 text-gray-800 dark:text-slate-200 tabular-nums"
                        />
                    ) : (
                        <button
                            onClick={startEdit}
                            className={`text-xl font-bold tabular-nums transition-colors cursor-pointer flex items-center gap-1 ${style.valueColor} hover:opacity-80`}
                            title="Click para editar"
                        >
                            {value != null ? `${value}%` : <span className="text-gray-300 dark:text-slate-600">-</span>}
                            <PencilIcon className="w-3 h-3 text-gray-300 dark:text-slate-600 opacity-0 group-hover:opacity-100 transition-opacity" />
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
}

function EditableSelectCell({ label, icon: Icon, value, options, isLoading, onSave }: {
    label: string;
    icon?: React.ComponentType<React.SVGProps<SVGSVGElement>>;
    value: string | number | null;
    options: { value: string; label: string }[];
    isLoading?: boolean;
    onSave: (value: string) => Promise<void>;
}) {
    const [editing, setEditing] = useState(false);
    const [saving, setSaving] = useState(false);

    const handleChange = async (newVal: string) => {
        if (newVal === String(value ?? "")) { setEditing(false); return; }
        setSaving(true);
        try {
            await onSave(newVal);
            setEditing(false);
        } catch {
            notificar.error("Error guardando " + label);
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="group/row flex justify-between items-center text-xs rounded-md px-1.5 py-1 hover:bg-white dark:hover:bg-slate-700/50 transition">
            <span className="flex items-center gap-1.5 text-gray-500 dark:text-slate-400">
                {Icon && <Icon className="w-3.5 h-3.5" />}
                {label}
            </span>
            {editing ? (
                <select
                    value={value == null ? "" : String(value)}
                    onChange={(e) => handleChange(e.target.value)}
                    onBlur={() => setEditing(false)}
                    disabled={saving || isLoading}
                    autoFocus
                    className="w-32 text-right rounded border border-blue-400 px-1.5 py-0.5 text-xs font-semibold outline-none focus:ring-1 focus:ring-blue-500 bg-white dark:bg-slate-700 dark:border-blue-500 text-gray-800 dark:text-slate-200"
                >
                    {value != null && value !== "" && !options.some((o) => o.value === String(value)) && (
                        <option value={String(value)}>{String(value)}</option>
                    )}
                    {options.map((o) => (
                        <option key={o.value} value={o.value}>{o.label}</option>
                    ))}
                </select>
            ) : (
                <button
                    onClick={() => setEditing(true)}
                    disabled={isLoading}
                    className="inline-flex items-center gap-1 font-semibold text-gray-800 dark:text-slate-200 hover:text-blue-600 dark:hover:text-blue-400 cursor-pointer transition-colors tabular-nums disabled:opacity-50"
                    title={isLoading ? "Cargando..." : "Click para editar"}
                >
                    {value != null && value !== "" ? value : <span className="text-gray-300 dark:text-slate-600">-</span>}
                    <PencilIcon className="w-3 h-3 text-gray-300 dark:text-slate-600 opacity-0 group-hover/row:opacity-100 transition-opacity" />
                </button>
            )}
        </div>
    );
}

function ChannelConfigCard({ label, canalField, cuotasField, listaField, sinIvaField, icon: Icon, accent, iconBg, config, canales, onRefresh }: {
    label: string;
    canalField: "canalMl" | "canalGastro" | "canalHogar";
    cuotasField: "cuotasMl" | "cuotasGastro" | "cuotasHogar";
    listaField: "listaPreciosMl" | "listaPreciosGastro" | "listaPreciosHogar";
    sinIvaField: "sinIvaMl" | "sinIvaGastro" | "sinIvaHogar";
    icon: React.ComponentType<React.SVGProps<SVGSVGElement>>;
    accent: string;
    iconBg: string;
    config: import("./types").SincronizacionConfig;
    canales: CanalDTO[];
    onRefresh: () => void;
}) {
    const canalValue = config[canalField];
    const cuotasValue = config[cuotasField];
    const listaValue = config[listaField];
    const sinIvaValue = config[sinIvaField];

    const canalDbKey = camelToSnake(canalField);
    const cuotasDbKey = camelToSnake(cuotasField);
    const listaDbKey = camelToSnake(listaField);
    const sinIvaDbKey = camelToSnake(sinIvaField);

    const [cuotasOptions, setCuotasOptions] = useState<number[]>([]);
    const [cuotasLoading, setCuotasLoading] = useState(false);

    useEffect(() => {
        if (!canalValue || canales.length === 0) {
            setCuotasOptions([]);
            return;
        }
        const canal = canales.find((c) => c.nombre === canalValue);
        if (!canal) {
            setCuotasOptions([]);
            return;
        }
        setCuotasLoading(true);
        getCuotasPorCanalAPI(canal.id)
            .then((cuotas) => setCuotasOptions(cuotas.map((c) => c.cuotas).sort((a, b) => a - b)))
            .catch(() => setCuotasOptions([]))
            .finally(() => setCuotasLoading(false));
    }, [canalValue, canales]);

    const canalOptions = canales.map((c) => ({ value: c.nombre, label: c.nombre }));
    const cuotasOpts = cuotasOptions.map((c) => ({ value: String(c), label: String(c) }));

    const toggleSinIva = async () => {
        try {
            await updateConfigByClave(sinIvaDbKey, sinIvaValue ? "false" : "true");
            onRefresh();
        } catch {
            notificar.error("Error guardando configuración");
        }
    };

    return (
        <div className="group relative rounded-xl border border-gray-200 bg-white shadow-xs hover:shadow-sm transition overflow-hidden dark:border-slate-700 dark:bg-slate-800/60">
            <div className={`h-1 ${accent}`} />
            <div className="p-4">
                <div className="flex items-center justify-between mb-3">
                    <div className="flex items-center gap-2.5 min-w-0">
                        <span className={`flex items-center justify-center w-9 h-9 shrink-0 rounded-lg ${iconBg}`}>
                            <Icon className="w-4 h-4" />
                        </span>
                        <span className="text-sm font-bold text-gray-800 dark:text-slate-100 truncate">{label}</span>
                    </div>
                    <button
                        type="button"
                        role="switch"
                        aria-checked={!!sinIvaValue}
                        onClick={toggleSinIva}
                        className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[10px] font-semibold transition cursor-pointer ${
                            sinIvaValue
                                ? "bg-emerald-100 text-emerald-700 hover:bg-emerald-200 dark:bg-emerald-500/15 dark:text-emerald-300 dark:hover:bg-emerald-500/25"
                                : "bg-gray-100 text-gray-500 hover:bg-gray-200 dark:bg-slate-700 dark:text-slate-400 dark:hover:bg-slate-600"
                        }`}
                        title="Click para alternar"
                    >
                        <span className={`w-1.5 h-1.5 rounded-full ${sinIvaValue ? "bg-emerald-500" : "bg-gray-400 dark:bg-slate-500"}`} />
                        Sin IVA
                    </button>
                </div>
                <div className="space-y-0.5 rounded-lg bg-gray-50/70 dark:bg-slate-800/60 p-1.5">
                    <EditableSelectCell
                        label="Canal"
                        icon={TagIcon}
                        value={canalValue}
                        options={canalOptions}
                        onSave={async (v) => { await updateConfigByClave(canalDbKey, v); onRefresh(); }}
                    />
                    <EditableSelectCell
                        label="Cuotas"
                        icon={CreditCardIcon}
                        value={cuotasValue}
                        options={cuotasOpts}
                        isLoading={cuotasLoading}
                        onSave={async (v) => { await updateConfigByClave(cuotasDbKey, v); onRefresh(); }}
                    />
                    <EditableValue label="Lista DUX" icon={ClipboardDocumentListIcon} value={listaValue} dbKey={listaDbKey} onSaved={onRefresh} />
                </div>
            </div>
        </div>
    );
}

function EditableValue({ label, icon: Icon, value, dbKey, onSaved }: { label: string; icon?: React.ComponentType<React.SVGProps<SVGSVGElement>>; value: string | number | null; dbKey: string; onSaved: () => void }) {
    const [editing, setEditing] = useState(false);
    const [input, setInput] = useState("");
    const [saving, setSaving] = useState(false);

    const startEdit = () => {
        setInput(value != null ? String(value) : "");
        setEditing(true);
    };

    const save = async () => {
        if (input === String(value ?? "")) {
            setEditing(false);
            return;
        }
        setSaving(true);
        try {
            await updateConfigByClave(dbKey, input.trim());
            onSaved();
            setEditing(false);
        } catch {
            notificar.error("Error guardando " + label);
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="group/row flex justify-between items-center text-xs rounded-md px-1.5 py-1 hover:bg-white dark:hover:bg-slate-700/50 transition">
            <span className="flex items-center gap-1.5 text-gray-500 dark:text-slate-400">
                {Icon && <Icon className="w-3.5 h-3.5" />}
                {label}
            </span>
            {editing ? (
                <input
                    type="text"
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    onBlur={save}
                    onKeyDown={(e) => {
                        if (e.key === "Enter") save();
                        if (e.key === "Escape") setEditing(false);
                    }}
                    disabled={saving}
                    autoFocus
                    className="w-32 text-right rounded border border-blue-400 px-1.5 py-0.5 text-xs font-semibold outline-none focus:ring-1 focus:ring-blue-500 bg-white dark:bg-slate-700 dark:border-blue-500 text-gray-800 dark:text-slate-200"
                />
            ) : (
                <button
                    onClick={startEdit}
                    className="inline-flex items-center gap-1 font-semibold text-gray-800 dark:text-slate-200 hover:text-blue-600 dark:hover:text-blue-400 cursor-pointer transition-colors tabular-nums"
                    title="Click para editar"
                >
                    {value ?? <span className="text-gray-300 dark:text-slate-600">-</span>}
                    <PencilIcon className="w-3 h-3 text-gray-300 dark:text-slate-600 opacity-0 group-hover/row:opacity-100 transition-opacity" />
                </button>
            )}
        </div>
    );
}

function ConfigPanel({ config, isLoading, error, onRefresh }: {
    config: import("./types").SincronizacionConfig | null;
    isLoading: boolean;
    error: string | null;
    onRefresh: () => void;
}) {
    const promoFields = [
        { label: "Seller Campaign", dbKey: "seller_campaign_pct", value: config?.sellerCampaignPct ?? null },
        { label: "Deal", dbKey: "deal_pct", value: config?.dealPct ?? null },
        { label: "Smart", dbKey: "smart_pct", value: config?.smartPct ?? null },
    ];

    const [canales, setCanales] = useState<CanalDTO[]>([]);

    useEffect(() => {
        getCanalesAPI(0, 500, {}, "nombre,asc")
            .then((r) => setCanales(r.content || []))
            .catch(() => setCanales([]));
    }, []);

    return (
        <section className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-800/90">
            <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-2">
                    <Cog6ToothIcon className="w-4 h-4 text-gray-500 dark:text-slate-400" />
                    <h2 className="text-sm font-bold text-gray-800 dark:text-slate-100">Configuración</h2>
                    <span className="text-xs text-gray-400 dark:text-slate-500">Click en un valor para editarlo</span>
                </div>
                <Link
                    href="/config-automatizacion"
                    className="flex items-center gap-1 text-xs text-blue-600 hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300"
                >
                    Ver todos <ArrowTopRightOnSquareIcon className="w-3 h-3" />
                </Link>
            </div>

            {isLoading && (
                <div className="flex items-center gap-2 text-sm text-gray-400 dark:text-slate-500">
                    <div className="w-4 h-4 border-2 border-gray-200 border-t-blue-500 rounded-full animate-spin" />
                    Cargando...
                </div>
            )}

            {error && <div className="text-sm text-red-600 dark:text-red-400">{error}</div>}

            {config && (
                <div className="space-y-4">
                    {/* Promociones */}
                    <div>
                        <span className="text-[10px] font-semibold uppercase tracking-wider text-gray-400 dark:text-slate-500">Promociones</span>
                        <div className="mt-2 grid grid-cols-3 gap-3">
                            {promoFields.map(({ label, dbKey, value }) => (
                                <EditablePromoCard key={dbKey} label={label} value={value} dbKey={dbKey} onSaved={onRefresh} />
                            ))}
                        </div>
                    </div>

                    {/* Canales */}
                    <div>
                        <span className="text-[10px] font-semibold uppercase tracking-wider text-gray-400 dark:text-slate-500">Canales y Listas DUX</span>
                        <div className="mt-2 grid grid-cols-1 lg:grid-cols-3 gap-3">
                            {configChannels.map(({ label, canal, cuotas, lista, sinIva, icon, accent, iconBg }) => (
                                <ChannelConfigCard
                                    key={label}
                                    label={label}
                                    canalField={canal}
                                    cuotasField={cuotas}
                                    listaField={lista}
                                    sinIvaField={sinIva}
                                    icon={icon}
                                    accent={accent}
                                    iconBg={iconBg}
                                    config={config}
                                    canales={canales}
                                    onRefresh={onRefresh}
                                />
                            ))}
                        </div>
                    </div>
                </div>
            )}
        </section>
    );
}

function TopesPromocionPanel() {
    const [topes, setTopes] = useState<TopePromocionDTO[]>([]);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [dirty, setDirty] = useState(false);
    const [selectedMla, setSelectedMla] = useState<{ id: number; label: string } | null>(null);
    const [newTope, setNewTope] = useState("");
    const [editingIndex, setEditingIndex] = useState<number | null>(null);
    const [editValue, setEditValue] = useState("");

    useEffect(() => {
        getTopesPromocion()
            .then(setTopes)
            .catch(() => notificar.error("Error cargando topes de promoción"))
            .finally(() => setLoading(false));
    }, []);

    const handleAdd = () => {
        if (!selectedMla) {
            toast.error("Selecciona un MLA");
            return;
        }
        const tope = parseInt(newTope, 10);
        if (isNaN(tope) || tope <= 0 || tope > 100) {
            toast.error("El porcentaje debe estar entre 1 y 100");
            return;
        }
        const mla = selectedMla.label.toUpperCase();
        const exists = topes.findIndex((t) => t.mla === mla);
        if (exists >= 0) {
            setTopes((prev) => prev.map((t, i) => (i === exists ? { ...t, topePromocion: tope } : t)));
        } else {
            setTopes((prev) => [...prev, { id: selectedMla.id, mla, topePromocion: tope }]);
        }
        setSelectedMla(null);
        setNewTope("");
        setDirty(true);
    };

    const handleRemove = (index: number) => {
        setTopes((prev) => prev.filter((_, i) => i !== index));
        setDirty(true);
    };

    const startEdit = (index: number) => {
        setEditingIndex(index);
        setEditValue(String(topes[index].topePromocion));
    };

    const confirmEdit = () => {
        if (editingIndex === null) return;
        const tope = parseInt(editValue, 10);
        if (isNaN(tope) || tope <= 0 || tope > 100) {
            toast.error("El porcentaje debe estar entre 1 y 100");
            return;
        }
        setTopes((prev) => prev.map((t, i) => (i === editingIndex ? { ...t, topePromocion: tope } : t)));
        setEditingIndex(null);
        setEditValue("");
        setDirty(true);
    };

    const cancelEdit = () => {
        setEditingIndex(null);
        setEditValue("");
    };

    const handleSave = async () => {
        setSaving(true);
        try {
            const updated = await saveTopesPromocion(topes);
            setTopes(updated);
            setDirty(false);
            notificar.success("Topes de promoción guardados");
        } catch {
            notificar.error("Error guardando topes");
        } finally {
            setSaving(false);
        }
    };

    return (
        <section className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-800/90">
            <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-2">
                    <TagIcon className="w-4 h-4 text-gray-500 dark:text-slate-400" />
                    <h2 className="text-sm font-bold text-gray-800 dark:text-slate-100">Topes de Promoción por MLA</h2>
                    <span className="text-xs text-gray-400 dark:text-slate-500">
                        Porcentaje maximo de descuento individual (sobreescribe el global)
                    </span>
                </div>
                {dirty && (
                    <button
                        onClick={handleSave}
                        disabled={saving}
                        className="flex items-center gap-1 rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-50 transition"
                    >
                        <CheckIcon className="w-3.5 h-3.5" />
                        {saving ? "Guardando..." : "Guardar"}
                    </button>
                )}
            </div>

            {loading ? (
                <div className="text-sm text-gray-400 dark:text-slate-500">Cargando...</div>
            ) : (
                <>
                    {/* Lista de topes existentes */}
                    {topes.length > 0 && (
                        <div className="flex flex-wrap gap-2 mb-4">
                            {topes.map((t, i) => (
                                <div
                                    key={t.mla}
                                    className="inline-flex items-center gap-2 rounded-lg border border-gray-200 bg-gray-50 px-3 py-1.5 text-sm dark:border-slate-700 dark:bg-slate-800"
                                >
                                    <span className="font-medium text-gray-800 dark:text-slate-200">{t.mla}</span>
                                    {editingIndex === i ? (
                                        <input
                                            type="number"
                                            value={editValue}
                                            onChange={(e) => setEditValue(e.target.value)}
                                            onBlur={confirmEdit}
                                            onKeyDown={(e) => {
                                                if (e.key === "Enter") confirmEdit();
                                                if (e.key === "Escape") cancelEdit();
                                            }}
                                            min={1}
                                            max={100}
                                            autoFocus
                                            className="w-14 text-center rounded border border-blue-400 px-1 py-0.5 text-xs font-semibold outline-none focus:ring-1 focus:ring-blue-500 bg-white dark:bg-slate-700 dark:border-blue-500 text-gray-800 dark:text-slate-200"
                                        />
                                    ) : (
                                        <button
                                            onClick={() => startEdit(i)}
                                            className="inline-flex items-center gap-1 text-xs text-orange-600 dark:text-orange-400 font-semibold hover:text-orange-700 dark:hover:text-orange-300 transition cursor-pointer"
                                            title="Click para editar"
                                        >
                                            {t.topePromocion}%
                                            <PencilIcon className="w-3 h-3 opacity-0 group-hover:opacity-100" />
                                        </button>
                                    )}
                                    <button
                                        onClick={() => handleRemove(i)}
                                        className="text-gray-400 hover:text-red-500 dark:text-slate-500 dark:hover:text-red-400 transition"
                                        title="Quitar"
                                    >
                                        <TrashIcon className="w-3.5 h-3.5" />
                                    </button>
                                </div>
                            ))}
                        </div>
                    )}

                    {/* Agregar nuevo */}
                    <div className="flex items-center gap-2">
                        <div className="w-56">
                            <AsyncSelect
                                label=""
                                loadOptions={searchMlas}
                                value={selectedMla?.id ?? null}
                                displayValue={selectedMla?.label ?? ""}
                                onChange={(id, label) => {
                                    if (id == null) { setSelectedMla(null); return; }
                                    setSelectedMla({ id: Number(id), label: label ?? "" });
                                }}
                                placeholder="Buscar MLA..."
                            />
                        </div>
                        <input
                            type="number"
                            value={newTope}
                            onChange={(e) => setNewTope(e.target.value)}
                            placeholder="%"
                            min={1}
                            max={100}
                            className="w-20 rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm text-center outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-200 dark:focus:border-blue-500 dark:focus:ring-blue-500/20"
                            onKeyDown={(e) => e.key === "Enter" && handleAdd()}
                        />
                        <button
                            onClick={handleAdd}
                            disabled={!selectedMla || !newTope}
                            className="flex items-center gap-1 rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed transition dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-slate-600"
                        >
                            Agregar
                        </button>
                    </div>

                    {topes.length === 0 && (
                        <p className="mt-3 text-xs text-gray-400 dark:text-slate-500">
                            Sin topes individuales. Se usarán los porcentajes globales de la configuración.
                        </p>
                    )}
                </>
            )}
        </section>
    );
}

function LogPanel({ enProceso }: { enProceso: boolean }) {
    const [lines, setLines] = useState<string[]>([]);
    const offsetRef = useRef(0);
    const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
    const scrollRef = useRef<HTMLDivElement>(null);
    const [autoScroll, setAutoScroll] = useState(true);

    const fetchLogs = useCallback(async () => {
        try {
            const newLines = await getLog(offsetRef.current);
            if (newLines.length > 0) {
                setLines((prev) => [...prev, ...newLines]);
                offsetRef.current += newLines.length;
            }
        } catch {
            /* ignorar */
        }
    }, []);

    // Iniciar/detener polling según estado del proceso
    useEffect(() => {
        if (enProceso) {
            // Reset al iniciar nuevo proceso
            setLines([]);
            offsetRef.current = 0;
            fetchLogs();
            intervalRef.current = setInterval(fetchLogs, 2000);
        } else {
            // Limpiar intervalo y hacer último fetch con delay para capturar líneas finales
            if (intervalRef.current) {
                clearInterval(intervalRef.current);
                intervalRef.current = null;
            }
            const timeout = setTimeout(fetchLogs, 500);
            return () => clearTimeout(timeout);
        }
        return () => {
            if (intervalRef.current) {
                clearInterval(intervalRef.current);
                intervalRef.current = null;
            }
        };
    }, [enProceso, fetchLogs]);

    // Auto-scroll al fondo cuando llegan líneas nuevas
    useEffect(() => {
        if (autoScroll && scrollRef.current) {
            scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
    }, [lines, autoScroll]);

    // Detectar si el usuario scrolleó manualmente hacia arriba
    const handleScroll = () => {
        if (!scrollRef.current) return;
        const { scrollTop, scrollHeight, clientHeight } = scrollRef.current;
        setAutoScroll(scrollHeight - scrollTop - clientHeight < 40);
    };

    if (lines.length === 0 && !enProceso) return null;

    return (
        <section className="rounded-xl border border-gray-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-800/90 overflow-hidden">
            <div className="flex items-center gap-2 px-5 py-3 border-b border-gray-100 dark:border-slate-700">
                <CommandLineIcon className="w-4 h-4 text-gray-500 dark:text-slate-400" />
                <h2 className="text-sm font-bold text-gray-800 dark:text-slate-100">Log</h2>
                {enProceso && (
                    <span className="ml-auto flex items-center gap-1.5 text-xs text-blue-600 dark:text-blue-400">
                        <span className="w-1.5 h-1.5 rounded-full bg-blue-500 animate-pulse" />
                        En vivo
                    </span>
                )}
                {lines.length > 0 && !enProceso && (
                    <span className="ml-auto text-xs text-gray-400 dark:text-slate-500">
                        {lines.length} lineas
                    </span>
                )}
            </div>
            <div
                ref={scrollRef}
                onScroll={handleScroll}
                className="max-h-64 overflow-y-auto p-4 bg-gray-950 font-mono text-xs leading-relaxed"
            >
                {lines.map((line, i) => (
                    <div
                        key={i}
                        className={`py-0.5 ${
                            line.startsWith("ERROR")
                                ? "text-red-400"
                                : line.includes("ok,") || line.includes("completada")
                                  ? "text-green-400"
                                  : line.includes("omitido") || line.includes("omitidos")
                                    ? "text-yellow-400"
                                    : "text-gray-300"
                        }`}
                    >
                        <span className="text-gray-600 select-none mr-2">{String(i + 1).padStart(3)}</span>
                        {line}
                    </div>
                ))}
                {enProceso && (
                    <div className="py-0.5 text-gray-500 animate-pulse">...</div>
                )}
            </div>
        </section>
    );
}

const LOG_LINE_OPTIONS = [200, 500, 1000, 2000, 5000] as const;

function LogFileViewer() {
    const [content, setContent] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [expanded, setExpanded] = useState(false);
    const [lineCount, setLineCount] = useState<number>(500);
    const scrollRef = useRef<HTMLDivElement>(null);

    const loadLog = useCallback(async (lineas: number) => {
        setIsLoading(true);
        try {
            const text = await getLogFile(lineas);
            setContent(text);
        } catch {
            setContent("Error al cargar el archivo de log");
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        if (expanded && content === null) {
            loadLog(lineCount);
        }
    }, [expanded, content, loadLog, lineCount]);

    // Scroll al fondo cuando se carga
    useEffect(() => {
        if (content && scrollRef.current) {
            scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
    }, [content]);

    return (
        <section className="rounded-xl border border-gray-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-800/90 overflow-hidden">
            <button
                onClick={() => setExpanded((v) => !v)}
                className="w-full flex items-center gap-2 px-5 py-3 text-left hover:bg-gray-50 dark:hover:bg-slate-700/50 transition"
            >
                <DocumentTextIcon className="w-4 h-4 text-gray-500 dark:text-slate-400" />
                <span className="text-sm font-bold text-gray-800 dark:text-slate-100">Archivo de Log</span>
                <span className="text-xs text-gray-400 dark:text-slate-500 ml-1">automatizacion-precios.log</span>
                <span className="ml-auto text-gray-400 dark:text-slate-500 text-xs">{expanded ? "▲" : "▼"}</span>
            </button>
            {expanded && (
                <div className="border-t border-gray-100 dark:border-slate-700">
                    <div className="flex items-center gap-2 px-5 py-2 bg-gray-50 dark:bg-slate-800 border-b border-gray-100 dark:border-slate-700">
                        <button
                            onClick={() => loadLog(lineCount)}
                            disabled={isLoading}
                            className="text-xs text-blue-600 hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300 disabled:opacity-50"
                        >
                            {isLoading ? "Cargando..." : "Recargar"}
                        </button>
                        <span className="text-xs text-gray-400 dark:text-slate-500">Últimas</span>
                        <select
                            value={lineCount}
                            onChange={(e) => {
                                const n = Number(e.target.value);
                                setLineCount(n);
                                loadLog(n);
                            }}
                            disabled={isLoading}
                            className="text-xs rounded border border-gray-200 bg-white px-1.5 py-0.5 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-200"
                        >
                            {LOG_LINE_OPTIONS.map((n) => (
                                <option key={n} value={n}>{n.toLocaleString("es-AR")}</option>
                            ))}
                        </select>
                        <span className="text-xs text-gray-400 dark:text-slate-500">líneas</span>
                    </div>
                    <div
                        ref={scrollRef}
                        className="max-h-80 overflow-y-auto p-4 bg-gray-950 font-mono text-xs leading-relaxed whitespace-pre text-gray-300"
                    >
                        {isLoading && !content && (
                            <span className="text-gray-500">Cargando...</span>
                        )}
                        {content !== null && (content || <span className="text-gray-500">El archivo de log esta vacio.</span>)}
                    </div>
                </div>
            )}
        </section>
    );
}

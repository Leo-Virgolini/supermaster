"use client";

import { useState, useEffect, useCallback } from "react";
import Modal from "../components/Modal/Modal";
import Button from "../components/Button/Button";
import AsyncSelect from "../components/AsyncSelect/AsyncSelect";
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import {
    CanalConceptoDTO,
    ConceptoCalculoOption,
    getConceptosPorCanalAPI,
    asignarConceptoAPI,
    eliminarConceptoDelCanalAPI,
    getAllConceptosCalculoAPI,
} from "./canalConceptosService";
import { ETAPAS_INFO, toEtapaId, APLICA_SOBRE_LABEL } from "../canal-formula/etapas";
import { getAplicaSobreInfo, getAplicaSobreBadgeClass } from "../canal-formula/aplica-sobre";
import { getNaturalezaInfo } from "../canal-formula/naturaleza";
import type { EtapaId } from "../canal-formula/types";
import {
    createCuotaAPI,
    deleteCuotaAPI,
} from "../canal-concepto-cuotas/canalConceptoCuotaService";
import {
    createReglaAPI as createReglaExcepcionAPI,
    deleteReglaAPI as deleteReglaExcepcionAPI,
} from "../canal-concepto-regla/canalConceptoReglaService";
import {
    createReglaAPI as createDescuentoAPI,
    deleteReglaAPI as deleteDescuentoAPI,
} from "../reglas-descuento/reglasDescuentoService";
import { searchMarcas, searchClasifGral, searchClasifGastro, searchTipos, searchCatalogos } from "../productos/productosService";
import { confirmDialog } from "../utils/confirmDialog";

type TabId = "conceptos" | "cuotas" | "reglas-canal" | "reglas-conceptos" | "descuentos";

interface CanalConceptosModalProps {
    isOpen: boolean;
    onClose: () => void;
    canalId: number;
    canalNombre: string;
    canalBaseId?: number | null;
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers internos
// ─────────────────────────────────────────────────────────────────────────────
type NameMap = Record<number, string>;

async function loadNameMap(entity: string, field: string): Promise<NameMap> {
    try {
        const r = await fetchAPI(`${API_BASE_URL}/api/${entity}?page=0&size=500`);
        const json = await r.json();
        const content = json.content || [];
        const map: NameMap = {};
        for (const item of content) {
            map[item.id] = item[field] || item.nombre || item.descripcion || `#${item.id}`;
        }
        return map;
    } catch {
        return {};
    }
}

function SimpleTable({ headers, children }: { headers: string[]; children: React.ReactNode }) {
    return (
        <div className="border border-gray-200 rounded overflow-hidden">
            <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-200">
                    <tr>
                        {headers.map((h) => (
                            <th key={h} className="text-left px-3 py-2 font-semibold text-gray-600 text-xs">{h}</th>
                        ))}
                    </tr>
                </thead>
                <tbody>{children}</tbody>
            </table>
        </div>
    );
}

function QuitarBtn({ onClick }: { onClick: () => void }) {
    return (
        <button
            onClick={onClick}
            className="text-red-500 hover:text-red-700 text-xs font-medium px-2 py-1 rounded hover:bg-red-50 transition"
        >
            Quitar
        </button>
    );
}

function Badge({ text, color }: { text: string; color: "green" | "red" | "blue" | "gray" }) {
    const colors = {
        green: "bg-green-100 text-green-700",
        red: "bg-red-100 text-red-700",
        blue: "bg-blue-100 text-blue-700",
        gray: "bg-gray-100 text-gray-600",
    };
    return (
        <span className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${colors[color]}`}>
            {text}
        </span>
    );
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab Conceptos — agrupado por etapa, con búsqueda y validaciones cruzadas
// ─────────────────────────────────────────────────────────────────────────────

interface CanalCheck {
    severidad: "warning" | "info";
    titulo: string;
    detalle: string;
}

/**
 * Computa advertencias / sugerencias a partir de los conceptos asignados.
 * Detecta configuraciones contradictorias o casos donde la convención por
 * defecto puede sorprender al usuario. No es bloqueante — solo informativo.
 */
function validarConceptosCanal(
    conceptos: CanalConceptoDTO[],
    canalBaseId: number | null | undefined,
): CanalCheck[] {
    const checks: CanalCheck[] = [];
    const tieneAplicaSobre = (a: string) => conceptos.some((c) => c.aplicaSobre === a);
    const cantidadDe = (a: string) => conceptos.filter((c) => c.aplicaSobre === a).length;

    // 1) IIBB sin IVA: poco común fiscalmente. IIBB se suma al IVA en el divisor.
    if (tieneAplicaSobre("IMPUESTO_EN_FACTOR_IMP") && !tieneAplicaSobre("FLAG_APLICAR_IVA")) {
        checks.push({
            severidad: "warning",
            titulo: "Hay impuestos adicionales pero el IVA no está habilitado",
            detalle: "Tenés un IMPUESTO_EN_FACTOR_IMP (ej: IIBB) asignado pero falta FLAG_APLICAR_IVA. Los impuestos adicionales se suman al IVA en el factor de impuestos — sin IVA, el factor solo aplica el porcentaje del impuesto adicional.",
        });
    }

    // 2) Ambos flags de margen → uno cancela al otro.
    if (tieneAplicaSobre("FLAG_USAR_MARGEN_MINORISTA") && tieneAplicaSobre("FLAG_USAR_MARGEN_MAYORISTA")) {
        checks.push({
            severidad: "warning",
            titulo: "Hay flags de margen contradictorios",
            detalle: "El canal tiene asignados FLAG_USAR_MARGEN_MINORISTA y FLAG_USAR_MARGEN_MAYORISTA al mismo tiempo. Solo uno se usa — el sistema decide por orden interno y puede sorprenderte. Dejá uno solo.",
        });
    }

    // 3) Sin ningún flag de margen → se usa minorista por default.
    //    Salvo que el canal use CALCULO_SOBRE_CANAL_BASE (hereda del padre,
    //    margen propio se ignora) — en ese caso la advertencia es ruidosa.
    const usaCanalBase = tieneAplicaSobre("CALCULO_SOBRE_CANAL_BASE")
        || tieneAplicaSobre("CALCULO_SOBRE_CANAL_BASE_RESELLER");
    if (!usaCanalBase
        && !tieneAplicaSobre("FLAG_USAR_MARGEN_MINORISTA")
        && !tieneAplicaSobre("FLAG_USAR_MARGEN_MAYORISTA")
        && conceptos.length > 0) {
        checks.push({
            severidad: "info",
            titulo: "El canal no tiene flag de margen explícito",
            detalle: "Si no asignás FLAG_USAR_MARGEN_MINORISTA ni FLAG_USAR_MARGEN_MAYORISTA, el sistema usa el margen minorista del producto por default. Si es el comportamiento que querés, podés explicitar el flag para evitar confusión.",
        });
    }

    // 4) CALCULO_SOBRE_CANAL_BASE pero sin canalBase configurado.
    const tieneCalculoBase = tieneAplicaSobre("CALCULO_SOBRE_CANAL_BASE")
        || tieneAplicaSobre("CALCULO_SOBRE_CANAL_BASE_RESELLER");
    if (tieneCalculoBase && (canalBaseId == null)) {
        checks.push({
            severidad: "warning",
            titulo: "El canal usa cálculo sobre canal base pero no tiene canal base configurado",
            detalle: "Asignaste CALCULO_SOBRE_CANAL_BASE (o variante reseller) pero el campo Canal Base de este canal está vacío. El cálculo no va a funcionar — definí un canal base en la edición del canal.",
        });
    }

    // 5) Más de un FLAG_FINANCIACION_PROVEEDOR / FLAG_APLICAR_IVA / FLAG_APLICAR_PRECIO_INFLADO.
    // Son flags binarios — duplicarlos no aporta nada y puede confundir.
    const flagsBinarios = ["FLAG_FINANCIACION_PROVEEDOR", "FLAG_APLICAR_IVA", "FLAG_APLICAR_PRECIO_INFLADO",
        "FLAG_INCLUIR_ENVIO", "FLAG_COMISION_ML"];
    for (const flag of flagsBinarios) {
        if (cantidadDe(flag) > 1) {
            checks.push({
                severidad: "info",
                titulo: `Hay ${cantidadDe(flag)} conceptos con ${flag}`,
                detalle: "Es un flag binario (on/off). Más de uno no agrega nada al cálculo — tener varios solo confunde la lectura. Dejá uno solo.",
            });
        }
    }

    // 6) CALCULO_SOBRE_CANAL_BASE Y CALCULO_SOBRE_CANAL_BASE_RESELLER ambos.
    if (tieneAplicaSobre("CALCULO_SOBRE_CANAL_BASE") && tieneAplicaSobre("CALCULO_SOBRE_CANAL_BASE_RESELLER")) {
        checks.push({
            severidad: "info",
            titulo: "Mezclás canal base 'propio' y 'reseller' en el mismo canal",
            detalle: "Es válido pero raro. El factor reseller corta el ingreso del dueño en su punto de aplicación; los factores no-RESELLER agregan markup al PVP sin captura para el dueño. Verificá que sea el efecto que querés.",
        });
    }

    return checks;
}

function ChecksPanel({ checks }: { checks: CanalCheck[] }) {
    if (checks.length === 0) return null;
    return (
        <div className="flex flex-col gap-1.5">
            {checks.map((check, i) => {
                const c = check.severidad === "warning"
                    ? { bg: "bg-amber-50 dark:bg-amber-900/20", border: "border-amber-200 dark:border-amber-800/60",
                        text: "text-amber-900 dark:text-amber-200", icon: "⚠" }
                    : { bg: "bg-blue-50 dark:bg-blue-900/20", border: "border-blue-200 dark:border-blue-800/60",
                        text: "text-blue-900 dark:text-blue-200", icon: "ℹ" };
                return (
                    <div key={i} className={`rounded-md border ${c.border} ${c.bg} p-2.5 text-xs ${c.text}`}>
                        <div className="flex items-start gap-2">
                            <span className="text-base leading-none mt-0.5">{c.icon}</span>
                            <div className="flex-1 min-w-0">
                                <p className="font-bold">{check.titulo}</p>
                                <p className="mt-0.5 opacity-90">{check.detalle}</p>
                            </div>
                        </div>
                    </div>
                );
            })}
        </div>
    );
}

function ConceptosTab({ canalId, canalBaseId }: { canalId: number; canalBaseId: number | null | undefined }) {
    const [conceptosAsignados, setConceptosAsignados] = useState<CanalConceptoDTO[]>([]);
    const [allConceptos, setAllConceptos] = useState<ConceptoCalculoOption[]>([]);
    const [selectedConceptoId, setSelectedConceptoId] = useState<number | "">("");
    const [isLoading, setIsLoading] = useState(false);
    const [isAdding, setIsAdding] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [filtroTexto, setFiltroTexto] = useState("");

    const load = useCallback(async () => {
        setIsLoading(true);
        setError(null);
        try {
            const result = await getConceptosPorCanalAPI(canalId);
            setConceptosAsignados(result);
        } catch (e: any) {
            setError(e.message || "Error al cargar conceptos");
        } finally {
            setIsLoading(false);
        }
    }, [canalId]);

    useEffect(() => {
        load();
        getAllConceptosCalculoAPI().then(setAllConceptos).catch((e) => console.warn("No se pudieron cargar conceptos de cálculo:", e));
        setSelectedConceptoId("");
        setError(null);
    }, [canalId, load]);

    const handleAgregar = async () => {
        if (!selectedConceptoId) return;
        setIsAdding(true);
        try {
            await asignarConceptoAPI(canalId, Number(selectedConceptoId), "FORM");
            await load();
            setSelectedConceptoId("");
        } catch (e: any) {
            setError(e.message || "Error al asignar concepto");
        } finally {
            setIsAdding(false);
        }
    };

    const handleEliminar = async (conceptoId: number) => {
        if (!(await confirmDialog({ title: "Eliminar", message: "¿Quitar este concepto del canal?", confirmText: "Eliminar", variant: "danger" }))) return;
        try {
            await eliminarConceptoDelCanalAPI(canalId, conceptoId, "FORM");
            await load();
        } catch (e: any) {
            setError(e.message || "Error al eliminar concepto");
        }
    };

    const assignedIds = new Set(conceptosAsignados.map((c) => c.conceptoId));
    const available = allConceptos.filter((c) => !assignedIds.has(c.id));

    // Agrupa los conceptos disponibles por etapa para mostrarlos en <optgroup>.
    // Conserva el orden COSTO → MARGEN → IMPUESTOS → PRECIO → POST_PRECIO.
    const availablePorEtapa = new Map<EtapaId, ConceptoCalculoOption[]>();
    for (const c of available) {
        const etapa = toEtapaId(c.etapa);
        if (!availablePorEtapa.has(etapa)) availablePorEtapa.set(etapa, []);
        availablePorEtapa.get(etapa)!.push(c);
    }
    // Formato del label de cada opción del select.
    // Nombre primero (el usuario busca por nombre), luego un descriptor visual:
    //   KH_RELMKUP        — +25%
    //   LZ_GASTRO_DESC    — -28%
    //   FINANCIACION_PROV — 🚩 flag
    // El signo explícito "+" en positivos hace fácil distinguir recargo vs descuento
    // (los <option> nativos no permiten color, así que el signo es el único indicador).
    const formatOptionLabel = (c: ConceptoCalculoOption): string => {
        const esFlag = c.aplicaSobre.startsWith("FLAG_");
        if (esFlag) return `${c.label}  —  🚩 flag`;
        if (c.porcentaje == null) return c.label;
        const sign = c.porcentaje > 0 ? "+" : "";
        return `${c.label}  —  ${sign}${c.porcentaje}%`;
    };

    // Filtro por texto: matchea nombre, descripción o aplicaSobre.
    const textoNorm = filtroTexto.trim().toLowerCase();
    const conceptosFiltrados = textoNorm
        ? conceptosAsignados.filter((c) => {
            return c.nombre.toLowerCase().includes(textoNorm)
                || (c.descripcion?.toLowerCase().includes(textoNorm) ?? false)
                || c.aplicaSobre.toLowerCase().includes(textoNorm)
                || (APLICA_SOBRE_LABEL[c.aplicaSobre]?.toLowerCase().includes(textoNorm) ?? false);
        })
        : conceptosAsignados;

    // Agrupar por etapa para mostrar el orden del cálculo
    // (Costo → Margen → Impuestos → Precio → Post-Precio).
    const conceptosPorEtapa = new Map<EtapaId, CanalConceptoDTO[]>();
    for (const c of conceptosFiltrados) {
        const etapa = toEtapaId(c.etapa);
        if (!conceptosPorEtapa.has(etapa)) conceptosPorEtapa.set(etapa, []);
        conceptosPorEtapa.get(etapa)!.push(c);
    }

    const isFlag = (aplicaSobre: string) => aplicaSobre.startsWith("FLAG_");

    // Validaciones cruzadas — corren sobre los conceptos asignados completos
    // (no sobre los filtrados), para que un filtro activo no oculte warnings.
    const checks = validarConceptosCanal(conceptosAsignados, canalBaseId);

    return (
        <div className="flex flex-col gap-4">
            {error && <div className="p-2 bg-red-100 text-red-700 rounded text-sm">{error}</div>}
            <div className="flex gap-2">
                <select
                    className="flex-1 border border-gray-300 rounded p-2 text-sm"
                    value={selectedConceptoId}
                    onChange={(e) => setSelectedConceptoId(e.target.value ? Number(e.target.value) : "")}
                >
                    <option value="">-- Seleccionar concepto --</option>
                    {ETAPAS_INFO.map((etapa, idx) => {
                        const items = availablePorEtapa.get(etapa.id);
                        if (!items || items.length === 0) return null;
                        return (
                            <optgroup key={etapa.id} label={`${etapa.icon}  Etapa ${idx + 1} — ${etapa.label}`}>
                                {items.map((c) => (
                                    <option key={c.id} value={c.id}>
                                        {formatOptionLabel(c)}
                                    </option>
                                ))}
                            </optgroup>
                        );
                    })}
                </select>
                <Button variant="dark" onClick={handleAgregar} disabled={!selectedConceptoId || isAdding}>
                    {isAdding ? "Agregando..." : "Agregar"}
                </Button>
            </div>
            <ChecksPanel checks={checks} />
            {/* Buscador: filtra por nombre, descripcion o aplicaSobre */}
            {!isLoading && conceptosAsignados.length > 0 && (
                <div className="relative">
                    <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-gray-400">
                        <svg
                            className="h-4 w-4"
                            fill="none"
                            stroke="currentColor"
                            viewBox="0 0 24 24"
                            aria-hidden="true"
                        >
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-4.35-4.35M11 19a8 8 0 100-16 8 8 0 000 16z" />
                        </svg>
                    </span>
                    <input
                        type="text"
                        placeholder="Filtrar conceptos por nombre, descripción o tipo..."
                        value={filtroTexto}
                        onChange={(e) => setFiltroTexto(e.target.value)}
                        className="w-full rounded border border-gray-300 bg-white py-2 pl-9! pr-9! text-sm dark:border-slate-700 dark:bg-slate-800"
                    />
                    {filtroTexto && (
                        <span className="absolute inset-y-0 right-0 flex items-center pr-2">
                            <button
                                type="button"
                                onClick={() => setFiltroTexto("")}
                                className="flex h-6 w-6 items-center justify-center rounded text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-slate-700"
                                title="Limpiar filtro"
                            >
                                ×
                            </button>
                        </span>
                    )}
                </div>
            )}
            {isLoading ? (
                <p className="text-sm text-gray-400 text-center py-4">Cargando...</p>
            ) : conceptosAsignados.length === 0 ? (
                <p className="text-sm text-gray-400 text-center py-4">No hay conceptos asignados.</p>
            ) : conceptosFiltrados.length === 0 ? (
                <p className="text-sm text-gray-400 text-center py-4">
                    Ningún concepto coincide con &quot;{filtroTexto}&quot;.
                </p>
            ) : (
                <div className="flex flex-col gap-3">
                    {ETAPAS_INFO.map((etapa, idx) => {
                        const items = conceptosPorEtapa.get(etapa.id);
                        if (!items || items.length === 0) return null;
                        return (
                            <div
                                key={etapa.id}
                                className={`overflow-hidden rounded-lg border-l-4 ${etapa.accentClass} border-y border-r border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900`}
                            >
                                <div className={`flex items-center justify-between gap-3 px-3 py-2 border-b border-slate-100 dark:border-slate-700 ${etapa.colorClass}`}>
                                    <div className="flex items-center gap-2">
                                        <span className="text-base">{etapa.icon}</span>
                                        <div>
                                            <span className="text-[10px] font-bold uppercase tracking-wider opacity-70">
                                                Etapa {idx + 1}
                                            </span>
                                            <p className="text-sm font-bold leading-tight">{etapa.label}</p>
                                        </div>
                                    </div>
                                    <span className="text-[11px] opacity-70">
                                        {items.length} concepto{items.length === 1 ? "" : "s"}
                                    </span>
                                </div>
                                <table className="w-full text-sm">
                                    <tbody>
                                        {items.map((item) => (
                                            <tr
                                                key={item.conceptoId}
                                                className="border-b last:border-0 border-slate-100 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-800/50"
                                            >
                                                <td className="px-3 py-2 text-gray-800 dark:text-slate-100 font-medium w-[180px]">
                                                    {item.nombre}
                                                </td>
                                                <td className="px-3 py-2 text-xs font-mono w-[60px]">
                                                    {isFlag(item.aplicaSobre) ? (
                                                        <span className="text-slate-400" title="Flag — el porcentaje no aplica">🚩</span>
                                                    ) : (
                                                        <span className={item.porcentaje > 0 ? "text-emerald-700" : item.porcentaje < 0 ? "text-rose-700" : "text-slate-400"}>
                                                            {item.porcentaje}%
                                                        </span>
                                                    )}
                                                </td>
                                                <td className="px-3 py-2 w-[200px]">
                                                    {(() => {
                                                        const ap = getAplicaSobreInfo(item.aplicaSobre);
                                                        return (
                                                            <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-semibold ${getAplicaSobreBadgeClass(item.aplicaSobre)}`} title={ap.descripcion}>
                                                                <span>{ap.icon}</span>
                                                                <span>{ap.labelCorto}</span>
                                                            </span>
                                                        );
                                                    })()}
                                                </td>
                                                <td className="px-3 py-2 w-[140px]">
                                                    {(() => {
                                                        const nat = getNaturalezaInfo(item.naturaleza);
                                                        return (
                                                            <span
                                                                className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold ${nat.badgeClass}`}
                                                                title={nat.descripcion}
                                                            >
                                                                <span>{nat.icon}</span>
                                                                <span>{nat.label}</span>
                                                            </span>
                                                        );
                                                    })()}
                                                </td>
                                                <td className="px-3 py-2 text-gray-500 dark:text-slate-400 text-xs italic">
                                                    {item.descripcion || "-"}
                                                </td>
                                                <td className="px-3 py-2 text-right w-[80px]">
                                                    <QuitarBtn onClick={() => handleEliminar(item.conceptoId)} />
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab Cuotas
// ─────────────────────────────────────────────────────────────────────────────
const CUOTAS_OPTIONS = [
    { value: -1, label: "-1 (Transferencia)" },
    { value: 0, label: "0 (Contado)" },
    ...Array.from({ length: 12 }, (_, i) => ({ value: i + 1, label: `${i + 1}` })),
];

function CuotasTab({ canalId }: { canalId: number }) {
    const [cuotas, setCuotas] = useState<any[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isAdding, setIsAdding] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [form, setForm] = useState({ descripcion: "", cuotas: 0, porcentaje: 0 });

    const load = useCallback(async () => {
        setIsLoading(true);
        try {
            const r = await fetchAPI(`${API_BASE_URL}/api/canal-concepto-cuotas/canal/${canalId}`);
            const data = await r.json();
            setCuotas(Array.isArray(data) ? data : (data.content || []));
        } catch (e: any) {
            setError(e.message);
        } finally {
            setIsLoading(false);
        }
    }, [canalId]);

    useEffect(() => {
        load();
        setForm({ descripcion: "", cuotas: 0, porcentaje: 0 });
        setError(null);
    }, [canalId, load]);

    const handleAgregar = async () => {
        if (!form.descripcion.trim()) return setError("La descripción es obligatoria");
        setIsAdding(true);
        setError(null);
        try {
            await createCuotaAPI({ canalId, ...form });
            await load();
            setForm({ descripcion: "", cuotas: 0, porcentaje: 0 });
        } catch (e: any) {
            setError(e.message || "Error al agregar cuota");
        } finally {
            setIsAdding(false);
        }
    };

    const handleEliminar = async (id: number) => {
        if (!(await confirmDialog({ title: "Eliminar", message: "¿Eliminar esta cuota?", confirmText: "Eliminar", variant: "danger" }))) return;
        try { await deleteCuotaAPI(id); await load(); }
        catch (e: any) { setError(e.message); }
    };

    const formatCuotas = (n: number) => String(n);

    return (
        <div className="flex flex-col gap-4">
            {error && <div className="p-2 bg-red-100 text-red-700 rounded text-sm">{error}</div>}
            <div className="grid grid-cols-3 gap-2 border border-gray-200 rounded p-3 bg-gray-50">
                <label className="block col-span-3">
                    <span className="text-xs font-semibold text-gray-600">Descripción <span className="text-red-500">*</span></span>
                    <input
                        className="w-full border rounded p-1.5 text-sm mt-0.5"
                        placeholder="Ej: Ahora 12"
                        value={form.descripcion}
                        onChange={(e) => setForm({ ...form, descripcion: e.target.value })}
                    />
                </label>
                <label className="block">
                    <span className="text-xs font-semibold text-gray-600">Cuotas</span>
                    <select
                        className="w-full border rounded p-1.5 text-sm mt-0.5"
                        value={form.cuotas}
                        onChange={(e) => setForm({ ...form, cuotas: Number(e.target.value) })}
                    >
                        {CUOTAS_OPTIONS.map((o) => (
                            <option key={o.value} value={o.value}>{o.label}</option>
                        ))}
                    </select>
                </label>
                <label className="block">
                    <span className="text-xs font-semibold text-gray-600">% Recargo/Desc.</span>
                    <input
                        type="number"
                        step="1"
                        className="w-full border rounded p-1.5 text-sm mt-0.5"
                        value={form.porcentaje}
                        onChange={(e) => setForm({ ...form, porcentaje: Number(e.target.value) })}
                    />
                </label>
                <div className="flex items-end justify-end">
                    <Button variant="dark" onClick={handleAgregar} disabled={isAdding}>
                        {isAdding ? "Agregando..." : "Agregar"}
                    </Button>
                </div>
            </div>

            {isLoading ? (
                <p className="text-sm text-gray-400 text-center py-4">Cargando...</p>
            ) : cuotas.length === 0 ? (
                <p className="text-sm text-gray-400 text-center py-4">No hay cuotas configuradas.</p>
            ) : (
                <SimpleTable headers={["Descripción", "Cuotas", "%", ""]}>
                    {cuotas.map((item) => (
                        <tr key={item.id} className="border-b last:border-0 hover:bg-gray-50">
                            <td className="px-3 py-2 text-gray-800">{item.descripcion}</td>
                            <td className="px-3 py-2 text-gray-600 text-sm">{formatCuotas(item.cuotas)}</td>
                            <td className="px-3 py-2">
                                <span className={`font-bold text-xs ${item.porcentaje >= 0 ? "text-green-600" : "text-red-600"}`}>
                                    {item.porcentaje > 0 ? "+" : ""}{item.porcentaje}%
                                </span>
                            </td>
                            <td className="px-3 py-2 text-right">
                                <QuitarBtn onClick={() => handleEliminar(item.id)} />
                            </td>
                        </tr>
                    ))}
                </SimpleTable>
            )}
        </div>
    );
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab Reglas del Canal (CanalRegla) — define qué productos APLICAN al canal
// según condiciones (tag, tipo, clasif, marca, producto específico, tiene_envio).
// A diferencia de ReglasTab (CanalConceptoRegla), estas reglas filtran productos
// a nivel del canal completo, no de un concepto individual.
// ─────────────────────────────────────────────────────────────────────────────
interface ReglaCanalForm {
    tipoRegla: "INCLUIR" | "EXCLUIR";
    tipoId: number | null;
    tipoNombre: string;
    clasifGralId: number | null;
    clasifGralNombre: string;
    clasifGastroId: number | null;
    clasifGastroNombre: string;
    marcaId: number | null;
    marcaNombre: string;
    productoId: number | null;
    productoLabel: string;
    tag: "" | "MAQUINA" | "REPUESTO" | "MENAJE";
    tieneEnvio: boolean | null;
}

const REGLA_CANAL_FORM_INITIAL: ReglaCanalForm = {
    tipoRegla: "EXCLUIR",
    tipoId: null, tipoNombre: "",
    clasifGralId: null, clasifGralNombre: "",
    clasifGastroId: null, clasifGastroNombre: "",
    marcaId: null, marcaNombre: "",
    productoId: null, productoLabel: "",
    tag: "", tieneEnvio: null,
};

function ReglasCanalTab({ canalId }: { canalId: number }) {
    const [reglas, setReglas] = useState<any[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isAdding, setIsAdding] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [form, setForm] = useState<ReglaCanalForm>({ ...REGLA_CANAL_FORM_INITIAL });
    const [showForm, setShowForm] = useState(false);

    const [nameMaps, setNameMaps] = useState<{
        tipos: NameMap; clasifGral: NameMap; clasifGastro: NameMap; marcas: NameMap;
    }>({ tipos: {}, clasifGral: {}, clasifGastro: {}, marcas: {} });

    const load = useCallback(async () => {
        setIsLoading(true);
        try {
            const r = await fetchAPI(`${API_BASE_URL}/api/canal-reglas/canal/${canalId}`);
            const data = await r.json();
            setReglas(Array.isArray(data) ? data : (data.content || []));
        } catch (e: any) {
            setError(e.message);
        } finally {
            setIsLoading(false);
        }
    }, [canalId]);

    useEffect(() => {
        load();
        Promise.all([
            loadNameMap("tipos", "nombre"),
            loadNameMap("clasif-gral", "nombre"),
            loadNameMap("clasif-gastro", "nombre"),
            loadNameMap("marcas", "nombre"),
        ]).then(([tipos, clasifGral, clasifGastro, marcas]) => {
            setNameMaps({ tipos, clasifGral, clasifGastro, marcas });
        }).catch(() => {
            // loadNameMap ya tiene try/catch interno y devuelve {} en error,
            // pero por defensa dejamos los mapas vacíos si algo inesperado falla.
            // Los labels caerán a "#id" en filtersOf hasta que el usuario recargue.
        });
        setForm({ ...REGLA_CANAL_FORM_INITIAL });
        setError(null);
        setShowForm(false);
    }, [canalId, load]);

    const handleAgregar = async () => {
        setIsAdding(true);
        setError(null);
        try {
            const res = await fetchAPI(`${API_BASE_URL}/api/canal-reglas`, {
                method: "POST",
                headers: { "Content-Type": "application/json", "X-Audit-Origin": "FORM" },
                body: JSON.stringify({
                    canalId,
                    tipoRegla: form.tipoRegla,
                    tipoId: form.tipoId,
                    clasifGralId: form.clasifGralId,
                    clasifGastroId: form.clasifGastroId,
                    marcaId: form.marcaId,
                    productoId: form.productoId,
                    tag: form.tag || null,
                    tieneEnvio: form.tieneEnvio,
                }),
            });
            if (!res.ok) throw new Error("Error al agregar regla del canal");
            await load();
            setForm({ ...REGLA_CANAL_FORM_INITIAL });
            setShowForm(false);
        } catch (e: any) {
            setError(e.message || "Error al agregar regla del canal");
        } finally {
            setIsAdding(false);
        }
    };

    const handleEliminar = async (id: number) => {
        if (!(await confirmDialog({ title: "Eliminar", message: "¿Eliminar esta regla del canal?", confirmText: "Eliminar", variant: "danger" }))) return;
        try {
            const res = await fetchAPI(`${API_BASE_URL}/api/canal-reglas/${id}`, {
                method: "DELETE",
                headers: { "X-Audit-Origin": "TABLE" },
            });
            if (!res.ok) throw new Error("Error al eliminar regla del canal");
            await load();
        } catch (e: any) { setError(e.message); }
    };

    const filtersOf = (item: any) => {
        const parts: string[] = [];
        if (item.productoId) parts.push(`Producto: ${item.productoLabel || `#${item.productoId}`}`);
        if (item.tipoId) parts.push(`Tipo: ${nameMaps.tipos[item.tipoId] || `#${item.tipoId}`}`);
        if (item.clasifGralId) parts.push(`Rubro: ${nameMaps.clasifGral[item.clasifGralId] || `#${item.clasifGralId}`}`);
        if (item.clasifGastroId) parts.push(`Gastro: ${nameMaps.clasifGastro[item.clasifGastroId] || `#${item.clasifGastroId}`}`);
        if (item.marcaId) parts.push(`Marca: ${nameMaps.marcas[item.marcaId] || `#${item.marcaId}`}`);
        if (item.tag) parts.push(`Tag: ${item.tag}`);
        if (item.tieneEnvio === true) parts.push("Con envío");
        if (item.tieneEnvio === false) parts.push("Sin envío");
        return parts.length > 0 ? parts.join(" | ") : "Sin filtros (aplica a todos los productos)";
    };

    return (
        <div className="flex flex-col gap-4">
            <div className="text-xs text-gray-500 italic">
                Estas reglas filtran <strong>qué productos aplican al canal</strong>. Si un producto cumple una regla EXCLUIR, no se calcula su precio en este canal. Si hay reglas INCLUIR, solo se calculan los productos que cumplan alguna.
            </div>
            {error && <div className="p-2 bg-red-100 text-red-700 rounded text-sm">{error}</div>}

            {!showForm ? (
                <div className="flex justify-end">
                    <Button variant="dark" onClick={() => setShowForm(true)}>
                        + Crear Regla
                    </Button>
                </div>
            ) : (
                <div className="border border-gray-200 rounded p-3 bg-gray-50 flex flex-col gap-3">
                    <label className="block">
                        <span className="text-xs font-semibold text-gray-600">Acción <span className="text-red-500">*</span></span>
                        <select
                            className="w-full border rounded p-1.5 text-sm mt-0.5"
                            value={form.tipoRegla}
                            onChange={(e) => setForm({ ...form, tipoRegla: e.target.value as "INCLUIR" | "EXCLUIR" })}
                        >
                            <option value="EXCLUIR">EXCLUIR (No calcular precio para estos productos)</option>
                            <option value="INCLUIR">INCLUIR (Solo calcular para estos productos)</option>
                        </select>
                    </label>

                    <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide">Filtros opcionales (aplica solo a productos que coincidan)</p>

                    <div className="grid grid-cols-2 gap-2">
                        <AsyncSelect
                            label="Tipo"
                            placeholder="Todos los tipos..."
                            loadOptions={async (q) => (await searchTipos(q)).map((o: any) => ({ id: o.id, label: o.label }))}
                            onChange={(val, label) => setForm({ ...form, tipoId: val ? Number(val) : null, tipoNombre: label || "" })}
                            displayValue={form.tipoId ? (form.tipoNombre || "Tipo seleccionado") : undefined}
                        />
                        <AsyncSelect
                            label="Rubro (Clasif. Gral)"
                            placeholder="Todos los rubros..."
                            loadOptions={async (q) => (await searchClasifGral(q)).map((o: any) => ({ id: o.id, label: o.label }))}
                            onChange={(val, label) => setForm({ ...form, clasifGralId: val ? Number(val) : null, clasifGralNombre: label || "" })}
                            displayValue={form.clasifGralId ? (form.clasifGralNombre || "Rubro seleccionado") : undefined}
                        />
                        <AsyncSelect
                            label="Gastro (Clasif. Gastro)"
                            placeholder="Todas las categorías..."
                            loadOptions={async (q) => (await searchClasifGastro(q)).map((o: any) => ({ id: o.id, label: o.label }))}
                            onChange={(val, label) => setForm({ ...form, clasifGastroId: val ? Number(val) : null, clasifGastroNombre: label || "" })}
                            displayValue={form.clasifGastroId ? (form.clasifGastroNombre || "Gastro seleccionado") : undefined}
                        />
                        <AsyncSelect
                            label="Marca"
                            placeholder="Todas las marcas..."
                            loadOptions={async (q) => (await searchMarcas(q)).map((o: any) => ({ id: o.id, label: o.label }))}
                            onChange={(val, label) => setForm({ ...form, marcaId: val ? Number(val) : null, marcaNombre: label || "" })}
                            displayValue={form.marcaId ? (form.marcaNombre || "Marca seleccionada") : undefined}
                        />
                    </div>

                    <AsyncSelect
                        label="Producto específico (opcional)"
                        placeholder="Buscar por SKU o descripción..."
                        loadOptions={async (q) => {
                            const r = await fetchAPI(`${API_BASE_URL}/api/productos?page=0&size=15&search=${encodeURIComponent(q)}`);
                            const j = await r.json();
                            return (j.content || []).map((item: any) => ({
                                id: item.id,
                                label: `[${item.sku}] ${item.tituloWeb || item.descripcion || ""}`.trim(),
                            }));
                        }}
                        onChange={(val, label) => setForm({ ...form, productoId: val ? Number(val) : null, productoLabel: label || "" })}
                        displayValue={form.productoId ? (form.productoLabel || "Producto seleccionado") : undefined}
                    />

                    <div className="grid grid-cols-2 gap-2">
                        <label className="block">
                            <span className="text-xs font-semibold text-gray-600">Tag</span>
                            <select
                                className="w-full border rounded p-1.5 text-sm mt-0.5 bg-white"
                                value={form.tag}
                                onChange={(e) => setForm({ ...form, tag: e.target.value as "" | "MAQUINA" | "REPUESTO" | "MENAJE" })}
                            >
                                <option value="">Sin filtro</option>
                                <option value="MAQUINA">Máquina</option>
                                <option value="REPUESTO">Repuesto</option>
                                <option value="MENAJE">Menaje</option>
                            </select>
                        </label>
                        <label className="block">
                            <span className="text-xs font-semibold text-gray-600">Tiene Envío</span>
                            <select
                                className="w-full border rounded p-1.5 text-sm mt-0.5 bg-white"
                                value={form.tieneEnvio === true ? "true" : form.tieneEnvio === false ? "false" : ""}
                                onChange={(e) => {
                                    const v = e.target.value;
                                    setForm({ ...form, tieneEnvio: v === "true" ? true : v === "false" ? false : null });
                                }}
                            >
                                <option value="">Sin filtro</option>
                                <option value="true">Sí tiene envío</option>
                                <option value="false">No tiene envío</option>
                            </select>
                        </label>
                    </div>

                    <div className="flex justify-end gap-2">
                        <Button variant="light" onClick={() => { setShowForm(false); setForm({ ...REGLA_CANAL_FORM_INITIAL }); }}>
                            Cancelar
                        </Button>
                        <Button variant="dark" onClick={handleAgregar} disabled={isAdding}>
                            {isAdding ? "Agregando..." : "Agregar Regla"}
                        </Button>
                    </div>
                </div>
            )}

            {isLoading ? (
                <p className="text-sm text-gray-400 text-center py-4">Cargando...</p>
            ) : reglas.length === 0 ? (
                <p className="text-sm text-gray-400 text-center py-4">No hay reglas a nivel del canal. Sin reglas, todos los productos aplican.</p>
            ) : (
                <SimpleTable headers={["Acción", "Filtros", ""]}>
                    {reglas.map((item) => (
                        <tr key={item.id} className="border-b last:border-0 hover:bg-gray-50">
                            <td className="px-3 py-2 w-[100px]">
                                <Badge
                                    text={item.tipoRegla}
                                    color={item.tipoRegla === "INCLUIR" ? "green" : "red"}
                                />
                            </td>
                            <td className="px-3 py-2 text-gray-600 text-xs">
                                {filtersOf(item)}
                            </td>
                            <td className="px-3 py-2 text-right w-[60px]">
                                <QuitarBtn onClick={() => handleEliminar(item.id)} />
                            </td>
                        </tr>
                    ))}
                </SimpleTable>
            )}
        </div>
    );
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab Reglas de Excepción
// ─────────────────────────────────────────────────────────────────────────────
interface ReglaForm {
    conceptoId: number | "";
    tipoRegla: "INCLUIR" | "EXCLUIR";
    tipoId: number | null;
    tipoNombre: string;
    clasifGralId: number | null;
    clasifGralNombre: string;
    clasifGastroId: number | null;
    clasifGastroNombre: string;
    marcaId: number | null;
    marcaNombre: string;
    tag: "" | "MAQUINA" | "REPUESTO" | "MENAJE";
    tieneEnvio: boolean | null;
}

const REGLA_FORM_INITIAL: ReglaForm = {
    conceptoId: "", tipoRegla: "EXCLUIR",
    tipoId: null, tipoNombre: "",
    clasifGralId: null, clasifGralNombre: "",
    clasifGastroId: null, clasifGastroNombre: "",
    marcaId: null, marcaNombre: "",
    tag: "", tieneEnvio: null,
};

function ReglasTab({ canalId }: { canalId: number }) {
    const [reglas, setReglas] = useState<any[]>([]);
    const [allConceptos, setAllConceptos] = useState<{ id: number; label: string }[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isAdding, setIsAdding] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [form, setForm] = useState<ReglaForm>({ ...REGLA_FORM_INITIAL });
    const [showForm, setShowForm] = useState(false);

    // Mapas de nombres para resolver IDs
    const [nameMaps, setNameMaps] = useState<{
        tipos: NameMap; clasifGral: NameMap; clasifGastro: NameMap; marcas: NameMap;
    }>({ tipos: {}, clasifGral: {}, clasifGastro: {}, marcas: {} });

    const load = useCallback(async () => {
        setIsLoading(true);
        try {
            const r = await fetchAPI(`${API_BASE_URL}/api/canal-concepto-reglas/canal/${canalId}`);
            const data = await r.json();
            setReglas(Array.isArray(data) ? data : (data.content || []));
        } catch (e: any) {
            setError(e.message);
        } finally {
            setIsLoading(false);
        }
    }, [canalId]);

    useEffect(() => {
        load();
        getAllConceptosCalculoAPI().then(setAllConceptos).catch((e) => console.warn("No se pudieron cargar conceptos de cálculo:", e));
        Promise.all([
            loadNameMap("tipos", "nombre"),
            loadNameMap("clasif-gral", "nombre"),
            loadNameMap("clasif-gastro", "nombre"),
            loadNameMap("marcas", "nombre"),
        ]).then(([tipos, clasifGral, clasifGastro, marcas]) => {
            setNameMaps({ tipos, clasifGral, clasifGastro, marcas });
        }).catch(() => {
            // loadNameMap ya tiene try/catch interno y devuelve {} en error,
            // pero por defensa dejamos los mapas vacíos si algo inesperado falla.
            // Los labels caerán a "#id" en filtersOf hasta que el usuario recargue.
        });
        setForm({ ...REGLA_FORM_INITIAL });
        setError(null);
        setShowForm(false);
    }, [canalId, load]);

    const handleAgregar = async () => {
        if (!form.conceptoId) return setError("Seleccioná un concepto");
        setIsAdding(true);
        setError(null);
        try {
            await createReglaExcepcionAPI({
                canalId,
                conceptoId: Number(form.conceptoId),
                tipoRegla: form.tipoRegla,
                tipoId: form.tipoId,
                clasifGralId: form.clasifGralId,
                clasifGastroId: form.clasifGastroId,
                marcaId: form.marcaId,
                tag: form.tag || null,
                tieneEnvio: form.tieneEnvio,
            });
            await load();
            setForm({ ...REGLA_FORM_INITIAL });
            setShowForm(false);
        } catch (e: any) {
            setError(e.message || "Error al agregar regla");
        } finally {
            setIsAdding(false);
        }
    };

    const handleEliminar = async (id: number) => {
        if (!(await confirmDialog({ title: "Eliminar", message: "¿Eliminar esta regla?", confirmText: "Eliminar", variant: "danger" }))) return;
        try { await deleteReglaExcepcionAPI(id); await load(); }
        catch (e: any) { setError(e.message); }
    };

    const resolveConcepto = (id: number) => allConceptos.find((c) => c.id === id)?.label ?? `#${id}`;

    const filtersOf = (item: any) => {
        const parts: string[] = [];
        if (item.tipoId) parts.push(`Tipo: ${nameMaps.tipos[item.tipoId] || `#${item.tipoId}`}`);
        if (item.clasifGralId) parts.push(`Rubro: ${nameMaps.clasifGral[item.clasifGralId] || `#${item.clasifGralId}`}`);
        if (item.clasifGastroId) parts.push(`Gastro: ${nameMaps.clasifGastro[item.clasifGastroId] || `#${item.clasifGastroId}`}`);
        if (item.marcaId) parts.push(`Marca: ${nameMaps.marcas[item.marcaId] || `#${item.marcaId}`}`);
        if (item.tag) parts.push(`Tag: ${item.tag}`);
        if (item.tieneEnvio === true) parts.push("Con envío");
        if (item.tieneEnvio === false) parts.push("Sin envío");
        return parts.length > 0 ? parts.join(" | ") : "Sin filtros (aplica a todos)";
    };

    return (
        <div className="flex flex-col gap-4">
            {error && <div className="p-2 bg-red-100 text-red-700 rounded text-sm">{error}</div>}

            {!showForm ? (
                <div className="flex justify-end">
                    <Button variant="dark" onClick={() => setShowForm(true)}>
                        + Crear Regla
                    </Button>
                </div>
            ) : (
                <div className="border border-gray-200 rounded p-3 bg-gray-50 flex flex-col gap-3">
                    <div className="grid grid-cols-2 gap-2">
                        <label className="block">
                            <span className="text-xs font-semibold text-gray-600">Concepto <span className="text-red-500">*</span></span>
                            <select
                                className="w-full border rounded p-1.5 text-sm mt-0.5"
                                value={form.conceptoId}
                                onChange={(e) => setForm({ ...form, conceptoId: e.target.value ? Number(e.target.value) : "" })}
                            >
                                <option value="">-- Seleccionar --</option>
                                {allConceptos.map((c) => (
                                    <option key={c.id} value={c.id}>{c.label}</option>
                                ))}
                            </select>
                        </label>
                        <label className="block">
                            <span className="text-xs font-semibold text-gray-600">Acción <span className="text-red-500">*</span></span>
                            <select
                                className="w-full border rounded p-1.5 text-sm mt-0.5"
                                value={form.tipoRegla}
                                onChange={(e) => setForm({ ...form, tipoRegla: e.target.value as "INCLUIR" | "EXCLUIR" })}
                            >
                                <option value="EXCLUIR">EXCLUIR (No aplicar este gasto)</option>
                                <option value="INCLUIR">INCLUIR (Forzar este gasto)</option>
                            </select>
                        </label>
                    </div>

                    <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide">Filtros opcionales (aplica solo a productos que coincidan)</p>

                    <div className="grid grid-cols-2 gap-2">
                        <AsyncSelect
                            label="Tipo"
                            placeholder="Todos los tipos..."
                            loadOptions={async (q) => (await searchTipos(q)).map((o: any) => ({ id: o.id, label: o.label }))}
                            onChange={(val, label) => setForm({ ...form, tipoId: val ? Number(val) : null, tipoNombre: label || "" })}
                            displayValue={form.tipoId ? (form.tipoNombre || "Tipo seleccionado") : undefined}
                        />
                        <AsyncSelect
                            label="Rubro (Clasif. Gral)"
                            placeholder="Todos los rubros..."
                            loadOptions={async (q) => (await searchClasifGral(q)).map((o: any) => ({ id: o.id, label: o.label }))}
                            onChange={(val, label) => setForm({ ...form, clasifGralId: val ? Number(val) : null, clasifGralNombre: label || "" })}
                            displayValue={form.clasifGralId ? (form.clasifGralNombre || "Rubro seleccionado") : undefined}
                        />
                        <AsyncSelect
                            label="Gastro (Clasif. Gastro)"
                            placeholder="Todas las categorías..."
                            loadOptions={async (q) => (await searchClasifGastro(q)).map((o: any) => ({ id: o.id, label: o.label }))}
                            onChange={(val, label) => setForm({ ...form, clasifGastroId: val ? Number(val) : null, clasifGastroNombre: label || "" })}
                            displayValue={form.clasifGastroId ? (form.clasifGastroNombre || "Gastro seleccionado") : undefined}
                        />
                        <AsyncSelect
                            label="Marca"
                            placeholder="Todas las marcas..."
                            loadOptions={async (q) => (await searchMarcas(q)).map((o: any) => ({ id: o.id, label: o.label }))}
                            onChange={(val, label) => setForm({ ...form, marcaId: val ? Number(val) : null, marcaNombre: label || "" })}
                            displayValue={form.marcaId ? (form.marcaNombre || "Marca seleccionada") : undefined}
                        />
                    </div>

                    <div className="grid grid-cols-2 gap-2">
                        <label className="block">
                            <span className="text-xs font-semibold text-gray-600">Tag</span>
                            <select
                                className="w-full border rounded p-1.5 text-sm mt-0.5 bg-white"
                                value={form.tag}
                                onChange={(e) => setForm({ ...form, tag: e.target.value as "" | "MAQUINA" | "REPUESTO" | "MENAJE" })}
                            >
                                <option value="">Sin filtro</option>
                                <option value="MAQUINA">Máquina</option>
                                <option value="REPUESTO">Repuesto</option>
                                <option value="MENAJE">Menaje</option>
                            </select>
                        </label>
                        <label className="block">
                            <span className="text-xs font-semibold text-gray-600">Tiene Envío</span>
                            <select
                                className="w-full border rounded p-1.5 text-sm mt-0.5 bg-white"
                                value={form.tieneEnvio === true ? "true" : form.tieneEnvio === false ? "false" : ""}
                                onChange={(e) => {
                                    const v = e.target.value;
                                    setForm({ ...form, tieneEnvio: v === "true" ? true : v === "false" ? false : null });
                                }}
                            >
                                <option value="">Sin filtro</option>
                                <option value="true">Sí tiene envío</option>
                                <option value="false">No tiene envío</option>
                            </select>
                        </label>
                    </div>

                    <div className="flex justify-end gap-2">
                        <Button variant="light" onClick={() => { setShowForm(false); setForm({ ...REGLA_FORM_INITIAL }); }}>
                            Cancelar
                        </Button>
                        <Button variant="dark" onClick={handleAgregar} disabled={isAdding}>
                            {isAdding ? "Agregando..." : "Agregar Regla"}
                        </Button>
                    </div>
                </div>
            )}

            {isLoading ? (
                <p className="text-sm text-gray-400 text-center py-4">Cargando...</p>
            ) : reglas.length === 0 ? (
                <p className="text-sm text-gray-400 text-center py-4">No hay reglas de excepción.</p>
            ) : (
                <SimpleTable headers={["Concepto", "Acción", "Filtros", ""]}>
                    {reglas.map((item) => (
                        <tr key={item.id} className="border-b last:border-0 hover:bg-gray-50">
                            <td className="px-3 py-2 text-gray-800 font-medium">
                                {resolveConcepto(item.conceptoId)}
                            </td>
                            <td className="px-3 py-2">
                                <Badge
                                    text={item.tipoRegla}
                                    color={item.tipoRegla === "INCLUIR" ? "green" : "red"}
                                />
                            </td>
                            <td className="px-3 py-2 text-gray-500 text-xs max-w-[220px]">
                                {filtersOf(item)}
                            </td>
                            <td className="px-3 py-2 text-right">
                                <QuitarBtn onClick={() => handleEliminar(item.id)} />
                            </td>
                        </tr>
                    ))}
                </SimpleTable>
            )}
        </div>
    );
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab Descuentos
// ─────────────────────────────────────────────────────────────────────────────
interface DescuentoForm {
    descripcion: string;
    descuentoPorcentaje: number;
    montoMinimo: number | "";
    prioridad: number;
    activo: boolean;
    catalogoId: number | null;
    catalogoNombre: string;
    clasifGralId: number | null;
    clasifGralNombre: string;
    clasifGastroId: number | null;
    clasifGastroNombre: string;
}

const DESCUENTO_FORM_INITIAL: DescuentoForm = {
    descripcion: "", descuentoPorcentaje: 0, montoMinimo: "", prioridad: 1, activo: true,
    catalogoId: null, catalogoNombre: "",
    clasifGralId: null, clasifGralNombre: "",
    clasifGastroId: null, clasifGastroNombre: "",
};

function DescuentosTab({ canalId }: { canalId: number }) {
    const [descuentos, setDescuentos] = useState<any[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isAdding, setIsAdding] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [form, setForm] = useState<DescuentoForm>({ ...DESCUENTO_FORM_INITIAL });
    const [showForm, setShowForm] = useState(false);

    // Mapas de nombres para resolver IDs
    const [nameMaps, setNameMaps] = useState<{
        catalogos: NameMap; clasifGral: NameMap; clasifGastro: NameMap;
    }>({ catalogos: {}, clasifGral: {}, clasifGastro: {} });

    const load = useCallback(async () => {
        setIsLoading(true);
        try {
            const r = await fetchAPI(`${API_BASE_URL}/api/reglas-descuento/canal/${canalId}`);
            const data = await r.json();
            setDescuentos(Array.isArray(data) ? data : (data.content || []));
        } catch (e: any) {
            setError(e.message);
        } finally {
            setIsLoading(false);
        }
    }, [canalId]);

    useEffect(() => {
        load();
        Promise.all([
            loadNameMap("catalogos", "catalogo"),
            loadNameMap("clasif-gral", "nombre"),
            loadNameMap("clasif-gastro", "nombre"),
        ]).then(([catalogos, clasifGral, clasifGastro]) => {
            setNameMaps({ catalogos, clasifGral, clasifGastro });
        }).catch(() => { /* defensivo: ver Tab Reglas para detalle */ });
        setForm({ ...DESCUENTO_FORM_INITIAL });
        setError(null);
        setShowForm(false);
    }, [canalId, load]);

    const handleAgregar = async () => {
        if (!form.descripcion.trim()) return setError("La descripción es obligatoria");
        setIsAdding(true);
        setError(null);
        try {
            await createDescuentoAPI({
                canalId,
                descripcion: form.descripcion,
                descuentoPorcentaje: form.descuentoPorcentaje,
                montoMinimo: form.montoMinimo !== "" ? Number(form.montoMinimo) : 0,
                prioridad: form.prioridad,
                activo: form.activo,
                catalogoId: form.catalogoId,
                clasifGralId: form.clasifGralId,
                clasifGastroId: form.clasifGastroId,
            } as any);
            await load();
            setForm({ ...DESCUENTO_FORM_INITIAL });
            setShowForm(false);
        } catch (e: any) {
            setError(e.message || "Error al agregar descuento");
        } finally {
            setIsAdding(false);
        }
    };

    const handleEliminar = async (id: number) => {
        if (!(await confirmDialog({ title: "Eliminar", message: "¿Eliminar este descuento?", confirmText: "Eliminar", variant: "danger" }))) return;
        try { await deleteDescuentoAPI(id); await load(); }
        catch (e: any) { setError(e.message); }
    };

    const scopeOf = (item: any) => {
        const parts: string[] = [];
        if (item.catalogoId) parts.push(`Catálogo: ${nameMaps.catalogos[item.catalogoId] || `#${item.catalogoId}`}`);
        if (item.clasifGralId) parts.push(`Rubro: ${nameMaps.clasifGral[item.clasifGralId] || `#${item.clasifGralId}`}`);
        if (item.clasifGastroId) parts.push(`Gastro: ${nameMaps.clasifGastro[item.clasifGastroId] || `#${item.clasifGastroId}`}`);
        return parts.length > 0 ? parts.join(" | ") : "Todos";
    };

    return (
        <div className="flex flex-col gap-4">
            {error && <div className="p-2 bg-red-100 text-red-700 rounded text-sm">{error}</div>}

            {!showForm ? (
                <div className="flex justify-end">
                    <Button variant="dark" onClick={() => setShowForm(true)}>
                        + Crear Descuento
                    </Button>
                </div>
            ) : (
                <div className="border border-gray-200 rounded p-3 bg-gray-50 flex flex-col gap-3">
                    <label className="block">
                        <span className="text-xs font-semibold text-gray-600">Descripción <span className="text-red-500">*</span></span>
                        <input
                            className="w-full border rounded p-1.5 text-sm mt-0.5"
                            placeholder="Ej: Descuento cliente VIP"
                            value={form.descripcion}
                            onChange={(e) => setForm({ ...form, descripcion: e.target.value })}
                        />
                    </label>

                    <div className="grid grid-cols-3 gap-2">
                        <label className="block">
                            <span className="text-xs font-semibold text-gray-600">% Descuento <span className="text-red-500">*</span></span>
                            <input
                                type="number" step="1"
                                className="w-full border rounded p-1.5 text-sm mt-0.5"
                                value={form.descuentoPorcentaje}
                                onChange={(e) => setForm({ ...form, descuentoPorcentaje: Number(e.target.value) })}
                            />
                        </label>
                        <label className="block">
                            <span className="text-xs font-semibold text-gray-600">Monto Mínimo (opc.)</span>
                            <input
                                type="number" step="1" placeholder="Sin mínimo"
                                className="w-full border rounded p-1.5 text-sm mt-0.5"
                                value={form.montoMinimo}
                                onChange={(e) => setForm({ ...form, montoMinimo: e.target.value ? Number(e.target.value) : "" })}
                            />
                        </label>
                        <label className="block">
                            <span className="text-xs font-semibold text-gray-600">Prioridad</span>
                            <input
                                type="number" min={1}
                                className="w-full border rounded p-1.5 text-sm mt-0.5"
                                value={form.prioridad}
                                onChange={(e) => setForm({ ...form, prioridad: Number(e.target.value) })}
                            />
                        </label>
                    </div>

                    <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide">Alcance (opcional, limitar a productos específicos)</p>

                    <div className="grid grid-cols-3 gap-2">
                        <AsyncSelect
                            label="Catálogo"
                            placeholder="Todos..."
                            loadOptions={async (q) => (await searchCatalogos(q)).map((o: any) => ({ id: o.id, label: o.label }))}
                            onChange={(val, label) => setForm({ ...form, catalogoId: val ? Number(val) : null, catalogoNombre: label || "" })}
                            displayValue={form.catalogoId ? (form.catalogoNombre || "Catálogo seleccionado") : undefined}
                        />
                        <AsyncSelect
                            label="Rubro (Clasif. Gral)"
                            placeholder="Todos..."
                            loadOptions={async (q) => (await searchClasifGral(q)).map((o: any) => ({ id: o.id, label: o.label }))}
                            onChange={(val, label) => setForm({ ...form, clasifGralId: val ? Number(val) : null, clasifGralNombre: label || "" })}
                            displayValue={form.clasifGralId ? (form.clasifGralNombre || "Rubro seleccionado") : undefined}
                        />
                        <AsyncSelect
                            label="Gastro (Clasif. Gastro)"
                            placeholder="Todos..."
                            loadOptions={async (q) => (await searchClasifGastro(q)).map((o: any) => ({ id: o.id, label: o.label }))}
                            onChange={(val, label) => setForm({ ...form, clasifGastroId: val ? Number(val) : null, clasifGastroNombre: label || "" })}
                            displayValue={form.clasifGastroId ? (form.clasifGastroNombre || "Gastro seleccionado") : undefined}
                        />
                    </div>

                    <label className="flex items-center gap-2">
                        <input
                            type="checkbox" checked={form.activo} className="w-4 h-4"
                            onChange={(e) => setForm({ ...form, activo: e.target.checked })}
                        />
                        <span className="text-xs font-semibold text-gray-600">Activo</span>
                    </label>

                    <div className="flex justify-end gap-2">
                        <Button variant="light" onClick={() => { setShowForm(false); setForm({ ...DESCUENTO_FORM_INITIAL }); }}>
                            Cancelar
                        </Button>
                        <Button variant="dark" onClick={handleAgregar} disabled={isAdding}>
                            {isAdding ? "Agregando..." : "Agregar Descuento"}
                        </Button>
                    </div>
                </div>
            )}

            {isLoading ? (
                <p className="text-sm text-gray-400 text-center py-4">Cargando...</p>
            ) : descuentos.length === 0 ? (
                <p className="text-sm text-gray-400 text-center py-4">No hay reglas de descuento.</p>
            ) : (
                <SimpleTable headers={["Descripción", "% Desc.", "Mínimo", "Alcance", "Prior.", "Activo", ""]}>
                    {descuentos.map((item) => (
                        <tr key={item.id} className="border-b last:border-0 hover:bg-gray-50">
                            <td className="px-3 py-2 text-gray-800">{item.descripcion || "-"}</td>
                            <td className="px-3 py-2 font-bold text-red-600 text-sm">{item.descuentoPorcentaje ?? "-"}%</td>
                            <td className="px-3 py-2 text-gray-500 text-xs">{item.montoMinimo != null && item.montoMinimo > 0 ? `$${item.montoMinimo}` : "-"}</td>
                            <td className="px-3 py-2 text-gray-500 text-xs max-w-[180px]">{scopeOf(item)}</td>
                            <td className="px-3 py-2 text-gray-500 text-xs text-center">{item.prioridad ?? "-"}</td>
                            <td className="px-3 py-2 text-center">
                                <Badge text={item.activo ? "Sí" : "No"} color={item.activo ? "green" : "gray"} />
                            </td>
                            <td className="px-3 py-2 text-right">
                                <QuitarBtn onClick={() => handleEliminar(item.id)} />
                            </td>
                        </tr>
                    ))}
                </SimpleTable>
            )}
        </div>
    );
}

// ─────────────────────────────────────────────────────────────────────────────
// Modal principal con tabs
// ─────────────────────────────────────────────────────────────────────────────
const TABS: { id: TabId; label: string }[] = [
    { id: "conceptos", label: "Conceptos" },
    { id: "cuotas", label: "Cuotas" },
    { id: "reglas-canal", label: "Reglas Canal" },
    { id: "reglas-conceptos", label: "Reglas Conceptos" },
    { id: "descuentos", label: "Descuentos" },
];

export default function CanalConceptosModal({
    isOpen,
    onClose,
    canalId,
    canalNombre,
    canalBaseId,
}: CanalConceptosModalProps) {
    const [activeTab, setActiveTab] = useState<TabId>("conceptos");

    useEffect(() => {
        if (isOpen) setActiveTab("conceptos");
    }, [isOpen]);

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={`Canal: ${canalNombre}`}
            size="2xl"
            footer={<Button text="Cerrar" variant="light" onClick={onClose} />}
        >
            <div className="flex flex-col gap-4 min-w-[900px]">
                <div className="flex border-b border-gray-200">
                    {TABS.map((tab) => (
                        <button
                            key={tab.id}
                            onClick={() => setActiveTab(tab.id)}
                            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${activeTab === tab.id
                                ? "border-blue-600 text-blue-700"
                                : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
                                }`}
                        >
                            {tab.label}
                        </button>
                    ))}
                </div>
                <div className="min-h-[250px] max-h-[55vh] overflow-y-auto pr-1">
                    {activeTab === "conceptos" && <ConceptosTab canalId={canalId} canalBaseId={canalBaseId} />}
                    {activeTab === "cuotas" && <CuotasTab canalId={canalId} />}
                    {activeTab === "reglas-canal" && <ReglasCanalTab canalId={canalId} />}
                    {activeTab === "reglas-conceptos" && <ReglasTab canalId={canalId} />}
                    {activeTab === "descuentos" && <DescuentosTab canalId={canalId} />}
                </div>
            </div>
        </Modal>
    );
}

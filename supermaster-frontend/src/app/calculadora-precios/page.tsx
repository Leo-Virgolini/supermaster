"use client";
import { useEffect, useMemo, useState } from "react";
import { CalculatorIcon, InformationCircleIcon, ChevronDownIcon } from "@heroicons/react/24/outline";
import { toast } from "sonner";
import CanalSelectBadge from "../components/CanalSelectBadge/CanalSelectBadge";
import SimuladorForm from "./SimuladorForm";
import { getAllCanalesSimpleAPI, type CanalListItem } from "../canal-formula/canalFormulaService";
import { getCuotasPorCanalAPI } from "../canal-concepto-cuotas/canalConceptoCuotaService";

interface CuotaSimple {
    cuotas: number;
    descripcion: string;
}

export default function CalculadoraPreciosPage() {
    const [canales, setCanales] = useState<CanalListItem[]>([]);
    const [canalIdSel, setCanalIdSel] = useState<number | null>(null);
    const [cuotasDisponibles, setCuotasDisponibles] = useState<CuotaSimple[]>([]);
    // null = "sin plan / contado"; number = una cuota concreta del canal.
    const [cuotasSel, setCuotasSel] = useState<number | null>(null);
    const [isLoadingCanales, setIsLoadingCanales] = useState(true);
    const [isLoadingCuotas, setIsLoadingCuotas] = useState(false);
    const [ayudaAbierta, setAyudaAbierta] = useState(false);

    // Carga inicial de canales.
    useEffect(() => {
        let cancelled = false;
        (async () => {
            try {
                setIsLoadingCanales(true);
                const data = await getAllCanalesSimpleAPI();
                if (cancelled) return;
                setCanales(data);
                if (data.length > 0) setCanalIdSel(data[0].id);
            } catch (e: any) {
                toast.error(e?.message || "Error al cargar canales");
            } finally {
                if (!cancelled) setIsLoadingCanales(false);
            }
        })();
        return () => { cancelled = true; };
    }, []);

    // Cuotas del canal seleccionado.
    useEffect(() => {
        if (canalIdSel == null) {
            setCuotasDisponibles([]);
            setCuotasSel(null);
            return;
        }
        let cancelled = false;
        setCuotasDisponibles([]);
        (async () => {
            try {
                setIsLoadingCuotas(true);
                const data = await getCuotasPorCanalAPI(canalIdSel);
                if (cancelled) return;
                const list = data.map((c) => ({ cuotas: c.cuotas, descripcion: c.descripcion ?? "" }));
                setCuotasDisponibles(list);
                // Si la cuota actual ya no existe en el nuevo canal, defaulteamos a la primera
                // (mismo comportamiento que el Monitor de Precios).
                setCuotasSel((prev) => {
                    if (prev == null) return list.length > 0 ? list[0].cuotas : null;
                    const stillExists = list.some((c) => c.cuotas === prev);
                    if (stillExists) return prev;
                    return list.length > 0 ? list[0].cuotas : null;
                });
            } catch {
                if (!cancelled) {
                    setCuotasDisponibles([]);
                    setCuotasSel(null);
                }
            } finally {
                if (!cancelled) setIsLoadingCuotas(false);
            }
        })();
        return () => { cancelled = true; };
    }, [canalIdSel]);

    // Etiqueta de la cuota actual para el resumen / labels
    const cuotaLabel = (cuotas: number | null): string => {
        if (cuotas == null) return "Sin plan (contado)";
        if (cuotas === -1) return "Transferencia";
        if (cuotas === 0) return "Contado";
        return `${cuotas} cuotas`;
    };

    const canalSeleccionado = useMemo(
        () => canales.find((c) => c.id === canalIdSel) ?? null,
        [canales, canalIdSel],
    );

    return (
        <main className="flex flex-col gap-4 bg-gray-50 p-4 dark:bg-slate-950">
            <header>
                <h1 className="flex items-center gap-2 text-3xl font-bold text-gray-800 dark:text-slate-100">
                    <CalculatorIcon className="h-8 w-8 text-gray-600 dark:text-slate-400" />
                    Calculadora de Precios
                </h1>
                <p className="mt-1 text-sm text-gray-500 dark:text-slate-400">
                    Simulá el PVP final de un producto hipotético contra cualquier canal — sin persistir nada.
                </p>
            </header>

            {/* Banner de ayuda */}
            <div className="rounded-lg border border-blue-200 bg-blue-50 dark:border-blue-800/60 dark:bg-blue-900/20">
                <button
                    type="button"
                    onClick={() => setAyudaAbierta((v) => !v)}
                    className="flex w-full items-center justify-between gap-2 px-4 py-2 text-left text-sm font-semibold text-blue-900 dark:text-blue-200"
                >
                    <span className="flex items-center gap-2">
                        <InformationCircleIcon className="h-5 w-5" />
                        ¿Para qué sirve la Calculadora de Precios?
                    </span>
                    <ChevronDownIcon className={`h-4 w-4 transition-transform ${ayudaAbierta ? "rotate-180" : ""}`} />
                </button>
                {ayudaAbierta && (
                    <div className="border-t border-blue-200 px-4 py-3 text-sm text-blue-900 dark:border-blue-800/60 dark:text-blue-100">
                        <p className="mb-3">
                            Permite responder preguntas como <strong>&quot;¿qué precio tendría un producto con estos atributos en este canal?&quot;</strong>{" "}
                            sin necesidad de crear el producto en el catálogo. El sistema ejecuta el motor de cálculo real
                            sobre un producto temporal y devuelve la fórmula paso a paso.
                        </p>

                        <div className="mb-3 rounded-md bg-white/60 p-3 text-xs dark:bg-blue-950/40">
                            <p className="mb-2 font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">Casos de uso</p>
                            <ul className="ml-4 list-disc space-y-0.5">
                                <li>Estimar el PVP antes de cargar un producto nuevo.</li>
                                <li>Probar &quot;qué pasaría si subo el costo un 10%&quot; o &quot;si la marca cambiara a X&quot;.</li>
                                <li>Comparar el mismo producto hipotético en distintos canales.</li>
                                <li>Validar que los conceptos y reglas de un canal se aplican como esperás.</li>
                            </ul>
                        </div>

                        <div className="mb-3">
                            <p className="mb-1 font-semibold">Indicadores del resultado:</p>
                            <ul className="ml-4 list-disc space-y-0.5">
                                <li><strong>PVP</strong>: precio final de venta al público que paga el cliente, incluye IVA, impuestos, comisiones y recargo por cuotas.</li>
                                <li><strong>PVP Inflado</strong>: precio &quot;tachado&quot; mostrado como referencia cuando hay una regla de precio inflado configurada.</li>
                                <li><strong>Costos Venta</strong>: suma de todo lo que se va en la venta — comisiones del canal, descuentos, recargos por cupón, envío, recargo por cuotas.</li>
                                <li><strong>Ingreso Neto Vendedor</strong>: lo que efectivamente recibís después de descontar IVA, impuestos y costos de venta. Fórmula: <span className="font-mono">PVP − IVA − impuestos − costos venta</span>.</li>
                                <li><strong>Ganancia</strong>: beneficio real de la venta. Fórmula: <span className="font-mono">ingreso neto − costo producto</span>.</li>
                                <li><strong>Margen s/PVP</strong>: <span className="font-mono">ganancia / PVP</span>. Cuánto representa la ganancia respecto al precio final.</li>
                                <li><strong>Margen s/Ingreso Neto</strong>: <span className="font-mono">ganancia / ingreso neto</span>. Es el margen &quot;real&quot;, sin el ruido del IVA y los impuestos.</li>
                                <li><strong>Markup</strong>: <span className="font-mono">ganancia / costo producto</span>. Cuánto se recarga sobre el costo. Sirve para comparar contra el costo &quot;a pelo&quot;.</li>
                            </ul>
                        </div>

                        <div className="mb-3">
                            <p className="mb-1 font-semibold">Leyenda de colores en márgenes / markup:</p>
                            <ul className="ml-4 list-disc space-y-0.5">
                                <li><span className="text-red-600 dark:text-red-400 font-semibold">&lt; 0%</span> — pérdida (vendés debajo del costo).</li>
                                <li><span className="text-orange-500 font-semibold">0–15%</span> — bajo.</li>
                                <li><span className="text-yellow-600 dark:text-yellow-400 font-semibold">15–25%</span> — moderado.</li>
                                <li><span className="text-green-600 dark:text-green-400 font-semibold">25–40%</span> — bueno.</li>
                                <li><span className="text-emerald-600 dark:text-emerald-400 font-semibold">&gt; 40%</span> — excelente.</li>
                            </ul>
                        </div>

                        <div className="rounded-md border border-amber-300 bg-amber-50 p-2.5 text-xs text-amber-900 dark:border-amber-700/60 dark:bg-amber-900/30 dark:text-amber-100">
                            <strong>Importante:</strong> esta calculadora usa <em>exactamente</em> el mismo motor que el Monitor de Precios.
                            La diferencia es que acá no necesitás un producto real — definís sus atributos al vuelo. Algunas reglas que
                            apuntan a un producto específico por SKU no van a matchear en simulación; eso es esperado.
                        </div>
                    </div>
                )}
            </div>

            {/* Selector de canal + cuotas */}
            <div className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
                <div className="flex items-center gap-2">
                    <span className="text-sm font-bold text-gray-700 dark:text-slate-300">Canal:</span>
                    {isLoadingCanales ? (
                        <span className="text-sm text-slate-400 dark:text-slate-500">Cargando...</span>
                    ) : canales.length === 0 ? (
                        <span className="text-sm text-slate-400 dark:text-slate-500">No hay canales</span>
                    ) : (
                        <CanalSelectBadge
                            canales={canales}
                            value={canalIdSel}
                            onChange={(id) => setCanalIdSel(id)}
                        />
                    )}
                </div>

                <div className="flex items-center gap-2">
                    <span className="text-sm font-bold text-gray-700 dark:text-slate-300">Cuotas:</span>
                    {isLoadingCuotas ? (
                        <span className="text-sm text-slate-400 dark:text-slate-500">Cargando...</span>
                    ) : cuotasDisponibles.length === 0 ? (
                        <span className="text-sm italic text-slate-400 dark:text-slate-500">El canal no tiene planes configurados</span>
                    ) : (
                        <select
                            className="min-w-[200px] rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-slate-800 outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100 dark:focus:border-blue-600 dark:focus:ring-blue-500/30"
                            value={cuotasSel ?? ""}
                            onChange={(e) => setCuotasSel(e.target.value === "" ? null : Number(e.target.value))}
                        >
                            {cuotasDisponibles.map((c) => (
                                <option key={c.cuotas} value={c.cuotas}>
                                    {cuotaLabel(c.cuotas)}
                                    {c.descripcion ? ` — ${c.descripcion}` : ""}
                                </option>
                            ))}
                        </select>
                    )}
                </div>
            </div>

            {/* Form + resultado — sin key para persistir inputs entre cambios de canal */}
            {canalSeleccionado ? (
                <SimuladorForm
                    canalId={canalSeleccionado.id}
                    cuotas={cuotasSel}
                />
            ) : (
                <div className="flex h-64 items-center justify-center rounded-xl border-2 border-dashed border-slate-200 bg-white text-sm text-slate-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400">
                    Seleccioná un canal para empezar.
                </div>
            )}
        </main>
    );
}

"use client";

import { useState, useEffect } from "react";
import { OperacionPanel } from "../components/OperacionPanel/OperacionPanel";
import { BoltIcon, TruckIcon, ReceiptPercentIcon, Cog6ToothIcon, CheckIcon } from "@heroicons/react/24/outline";
import { useAuth } from "../context/AuthContext";
import { useConfiguracionML } from "../configuracion-ml/useConfiguracionML";
import { ConfiguracionMlDTO } from "../configuracion-ml/types";
import Button from "../components/Button/Button";

const inputClassName = "w-full rounded-2xl border border-slate-300 bg-white pl-8 pr-4 py-3 text-sm text-slate-700 shadow-sm outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-100 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100 dark:focus:border-blue-500 dark:focus:ring-blue-500/20";
const labelClassName = "text-sm font-semibold text-slate-700 dark:text-slate-200";
const hintClassName = "text-xs text-slate-400 dark:text-slate-500";

function MoneyInput({ value, onChange, className = "" }: { value: number; onChange: (v: number) => void; className?: string }) {
    return (
        <div className={`relative ${className}`}>
            <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-sm font-medium text-slate-400 dark:text-slate-500 pointer-events-none">$</span>
            <input
                type="number"
                step="1"
                min="0"
                className={inputClassName}
                value={value}
                onChange={(e) => onChange(Number(e.target.value))}
            />
        </div>
    );
}

export default function OperacionesMLPage() {
    const { usuario } = useAuth();
    const isAdmin = (usuario.rol || "").trim().toUpperCase() === "ADMIN";
    const [envioEnProceso, setEnvioEnProceso] = useState(false);
    const [comisionEnProceso, setComisionEnProceso] = useState(false);

    const { data, isLoading: configLoading, isSaving, error: configError, successMsg, save } = useConfiguracionML();
    const [form, setForm] = useState<ConfiguracionMlDTO>({
        id: null,
        umbralEnvioGratis: 0,
        tier1Hasta: 0,
        tier1Costo: 0,
        tier2Hasta: 0,
        tier2Costo: 0,
        tier3Costo: 0,
    });

    useEffect(() => {
        if (data) setForm(data);
    }, [data]);

    const handleChange = (field: keyof ConfiguracionMlDTO, value: number) => {
        setForm((prev) => ({ ...prev, [field]: value }));
    };

    const handleSave = () => save(form);

    const tiers = [
        {
            key: "tier1",
            title: "Tier 1",
            subtitle: "Bajo",
            accent: "text-blue-600 dark:text-blue-300",
            badge: "border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-500/30 dark:bg-blue-500/15 dark:text-blue-200",
            fields: [
                { name: "tier1Hasta" as const, label: "Hasta" },
                { name: "tier1Costo" as const, label: "Costo envío" },
            ],
        },
        {
            key: "tier2",
            title: "Tier 2",
            subtitle: "Medio",
            accent: "text-emerald-600 dark:text-emerald-300",
            badge: "border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-500/30 dark:bg-emerald-500/15 dark:text-emerald-200",
            fields: [
                { name: "tier2Hasta" as const, label: "Hasta" },
                { name: "tier2Costo" as const, label: "Costo envío" },
            ],
        },
        {
            key: "tier3",
            title: "Tier 3",
            subtitle: "Alto",
            accent: "text-violet-600 dark:text-violet-300",
            badge: "border-violet-200 bg-violet-50 text-violet-700 dark:border-violet-500/30 dark:bg-violet-500/15 dark:text-violet-200",
            fields: [
                { name: "tier3Costo" as const, label: "Costo envío", hint: "Aplica a PVP > tier 2" },
            ],
        },
    ];

    if (!isAdmin) {
        return (
            <main className="p-4 md:p-5 bg-gray-50 min-h-0 flex flex-col overflow-hidden dark:bg-slate-900">
                <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-amber-800 dark:border-amber-800 dark:bg-amber-500/10 dark:text-amber-200">
                    Solo los administradores pueden acceder a Operaciones ML.
                </div>
            </main>
        );
    }

    return (
        <main className="p-4 md:p-5 bg-gray-50 dark:bg-slate-900 flex flex-col gap-4">
            <div>
                <h1 className="text-3xl font-bold text-gray-800 dark:text-slate-100 flex items-center gap-3">
                    <BoltIcon className="w-8 h-8 text-blue-600 dark:text-blue-400" />
                    Operaciones ML
                </h1>
                <p className="text-gray-500 dark:text-slate-400 text-sm mt-1">
                    Ejecuta procesos masivos y configurá envíos de Mercado Libre.
                </p>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                <section className="bg-sky-50 dark:bg-sky-950/30 rounded-xl border border-sky-200 dark:border-sky-900 p-6 flex flex-col gap-4">
                    <div className="flex items-center gap-3">
                        <div className="flex items-center justify-center w-10 h-10 rounded-xl bg-sky-100 dark:bg-sky-900/50">
                            <TruckIcon className="w-5 h-5 text-sky-700 dark:text-sky-300" />
                        </div>
                        <div>
                            <h2 className="text-lg font-bold text-sky-900 dark:text-sky-300">Costos de Envío</h2>
                            <p className="text-sm text-sky-700 dark:text-sky-400">Recalcula los costos de envío de todos los MLAs.</p>
                        </div>
                    </div>
                    <OperacionPanel
                        titulo="Actualizar Costos de Envío ML"
                        descripcion=""
                        endpointIniciar="/api/ml/costo-envio"
                        endpointEstado="/api/ml/costo-envio/estado"
                        endpointCancelar="/api/ml/costo-envio/cancelar"
                        endpointResultado="/api/ml/costo-envio/resultado"
                        onRunningChange={setEnvioEnProceso}
                        disabled={comisionEnProceso}
                        disabledReason="Hay una actualización de comisiones en curso. Esperá a que termine."
                        embedded
                        procesoId="costo-envio"
                    />
                </section>

                <section className="bg-amber-50 dark:bg-amber-950/30 rounded-xl border border-amber-200 dark:border-amber-900 p-6 flex flex-col gap-4">
                    <div className="flex items-center gap-3">
                        <div className="flex items-center justify-center w-10 h-10 rounded-xl bg-amber-100 dark:bg-amber-900/50">
                            <ReceiptPercentIcon className="w-5 h-5 text-amber-700 dark:text-amber-300" />
                        </div>
                        <div>
                            <h2 className="text-lg font-bold text-amber-900 dark:text-amber-300">Comisiones</h2>
                            <p className="text-sm text-amber-700 dark:text-amber-400">Recalcula las comisiones de ML por categoría.</p>
                        </div>
                    </div>
                    <OperacionPanel
                        titulo="Actualizar Comisiones ML"
                        descripcion=""
                        endpointIniciar="/api/ml/costo-venta"
                        endpointEstado="/api/ml/costo-venta/estado"
                        endpointCancelar="/api/ml/costo-venta/cancelar"
                        endpointResultado="/api/ml/costo-venta/resultado"
                        onRunningChange={setComisionEnProceso}
                        disabled={envioEnProceso}
                        disabledReason="Hay una actualización de costos de envío en curso. Esperá a que termine."
                        embedded
                        procesoId="costo-venta"
                    />
                </section>
            </div>

            {/* Configuración Envíos ML */}
            <section className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-800/90 p-6 md:p-7 flex flex-col gap-5">
                <div className="flex items-center gap-3">
                    <div className="flex items-center justify-center w-10 h-10 rounded-xl bg-indigo-100 dark:bg-indigo-900/50">
                        <Cog6ToothIcon className="w-5 h-5 text-indigo-700 dark:text-indigo-300" />
                    </div>
                    <div>
                        <h2 className="text-lg font-bold text-slate-800 dark:text-slate-100">Configuración Envíos ML</h2>
                        <p className="text-sm text-slate-500 dark:text-slate-400">
                            Umbrales y costos de envío que impactan en el cálculo de precios y márgenes.
                        </p>
                    </div>
                </div>

                {configLoading ? (
                    <p className="text-gray-500 dark:text-slate-400 text-sm">Cargando configuración...</p>
                ) : (
                    <div className="grid gap-5">
                        {configError && (
                            <div className="rounded-2xl border border-red-200 bg-red-50 p-3 text-sm text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-200">
                                {configError}
                            </div>
                        )}
                        {successMsg && (
                            <div className="rounded-2xl border border-green-200 bg-green-50 p-3 text-sm text-green-700 dark:border-green-500/30 dark:bg-green-500/10 dark:text-green-200">
                                {successMsg}
                            </div>
                        )}

                        <div className="rounded-2xl border border-slate-200 bg-slate-50/90 p-5 dark:border-slate-700 dark:bg-slate-900/70">
                            <div className="flex flex-col gap-1">
                                <h3 className="text-base font-semibold text-slate-800 dark:text-slate-100">Envío gratis</h3>
                                <p className="text-sm text-slate-500 dark:text-slate-400">
                                    Precio mínimo desde el cual Mercado Libre considera el envío bonificado.
                                </p>
                            </div>
                            <div className="mt-4">
                                <label className={labelClassName}>Umbral envío gratis</label>
                                <MoneyInput className="mt-2" value={form.umbralEnvioGratis} onChange={(v) => handleChange("umbralEnvioGratis", v)} />
                            </div>
                        </div>

                        <div className="grid gap-4 xl:grid-cols-3">
                            {tiers.map((tier) => (
                                <div key={tier.key} className="rounded-2xl border border-slate-200 bg-slate-50/90 p-5 dark:border-slate-700 dark:bg-slate-900/70">
                                    <div className="flex items-start justify-between gap-3">
                                        <div>
                                            <h3 className="text-base font-semibold text-slate-800 dark:text-slate-100">{tier.title}</h3>
                                            <p className={`mt-1 text-sm font-medium ${tier.accent}`}>{tier.subtitle}</p>
                                        </div>
                                        <span className={`rounded-full border px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.14em] ${tier.badge}`}>
                                            Costo ML
                                        </span>
                                    </div>
                                    <div className={`mt-5 grid gap-4 ${tier.fields.length > 1 ? "sm:grid-cols-2 xl:grid-cols-1 2xl:grid-cols-2" : ""}`}>
                                        {tier.fields.map((field) => (
                                            <div key={field.name}>
                                                <label className={labelClassName}>{field.label}</label>
                                                {"hint" in field && field.hint ? (
                                                    <p className={`${hintClassName} mt-1`}>{field.hint}</p>
                                                ) : null}
                                                <MoneyInput className="mt-2" value={form[field.name]} onChange={(v) => handleChange(field.name, v)} />
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            ))}
                        </div>

                        <div className="flex flex-col gap-4 rounded-2xl border border-slate-200 bg-slate-50/80 p-4 dark:border-slate-700 dark:bg-slate-900/70 md:flex-row md:items-center md:justify-between">
                            <div>
                                <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">Aplicar configuración</p>
                                <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                                    Los valores guardados se usarán en el cálculo de precios y costos de envío de ML.
                                </p>
                            </div>
                            <Button variant="dark" onClick={handleSave} disabled={isSaving}>
                                <CheckIcon className="w-4 h-4" />
                                {isSaving ? "Guardando..." : "Guardar configuración"}
                            </Button>
                        </div>
                    </div>
                )}
            </section>
        </main>
    );
}

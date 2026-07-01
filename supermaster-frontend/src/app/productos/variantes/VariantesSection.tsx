"use client";
import { VarianteBorrador, nuevaVariante } from "./types";

type EjeOpcion = { id: string; name: string; values: { id: string | null; name: string }[] };
type Cuota = { cuotas: number; descripcion: string };

type Props = {
    tiene: boolean; onTiene: (v: boolean) => void;
    ejeOpciones: EjeOpcion[];
    ejeAtributoId: string; onEje: (id: string) => void;
    ejeValorBase: string; ejeValorBaseId: string | null; onEjeValorBase: (id: string | null, nombre: string) => void;
    skuBase: string;
    cuotasMlOpts: Cuota[]; cuotasHogarOpts: Cuota[]; cuotasGastroOpts: Cuota[];
    cuotasDefault: { ml: number; hogar: number; gastro: number };
    variantes: VarianteBorrador[]; onVariantes: (v: VarianteBorrador[]) => void;
    subirMl: boolean; subirKtHogar: boolean; subirKtGastro: boolean;
    inputCls: string; selectCls: string;
};

const full = (base: string) => `${base} w-full`;

export default function VariantesSection(p: Props) {
    const ejeDef = p.ejeOpciones.find(e => e.id === p.ejeAtributoId);
    const valoresEje = ejeDef?.values ?? [];
    const setV = (id: string, patch: Partial<VarianteBorrador>) =>
        p.onVariantes(p.variantes.map(v => (v.id === id ? { ...v, ...patch } : v)));

    const selectorValor = (
        valorId: string | null, valorNombre: string,
        onChange: (id: string | null, nombre: string) => void,
    ) => valoresEje.length > 0 ? (
        <select className={full(p.selectCls)} value={valorId ?? valorNombre}
            onChange={e => {
                const opt = valoresEje.find(x => (x.id ?? x.name) === e.target.value);
                onChange(opt?.id ?? null, opt?.name ?? "");
            }}>
            <option value="">— elegir —</option>
            {valoresEje.map(x => <option key={x.id ?? x.name} value={x.id ?? x.name}>{x.name}</option>)}
        </select>
    ) : (
        <input className={p.inputCls} value={valorNombre} onChange={e => onChange(null, e.target.value)} placeholder="Valor del eje" />
    );

    return (
        <div className="rounded-2xl border border-slate-200 bg-white/60 p-3 dark:border-slate-700 dark:bg-slate-800/40">
            <label className="flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-200">
                <input type="checkbox" className="h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary"
                    checked={p.tiene} onChange={e => p.onTiene(e.target.checked)} />
                Este producto tiene variantes (Mercado Libre)
            </label>
            {p.tiene && (
                <div className="mt-3 space-y-3">
                    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                        <label className="block">
                            <span className="text-xs text-slate-500">Eje de variación</span>
                            <select className={full(p.selectCls)} value={p.ejeAtributoId} onChange={e => p.onEje(e.target.value)}>
                                <option value="">— elegir eje —</option>
                                {p.ejeOpciones.map(e => <option key={e.id} value={e.id}>{e.name}</option>)}
                            </select>
                            {p.ejeOpciones.length === 0 && <span className="mt-0.5 block text-[11px] text-amber-600">Elegí primero la categoría ML; el eje sale de sus atributos.</span>}
                        </label>
                        <label className="block">
                            <span className="text-xs text-slate-500">Valor del eje · variante #1 ({p.skuBase || "SKU base"})</span>
                            {selectorValor(p.ejeValorBaseId, p.ejeValorBase, p.onEjeValorBase)}
                        </label>
                    </div>

                    <div className="space-y-2">
                        {p.variantes.map((v, i) => (
                            <div key={v.id} className="rounded-xl border border-slate-200 bg-slate-50/70 p-2.5 dark:border-slate-700 dark:bg-slate-800/60">
                                <div className="flex items-center gap-2">
                                    <button type="button" onClick={() => setV(v.id, { expandida: !v.expandida })}
                                        className="text-xs font-semibold text-slate-600 dark:text-slate-300">{v.expandida ? "▾" : "▸"} Variante {i + 2}</button>
                                    <span className="truncate text-xs text-slate-400">{v.sku || "sin SKU"} · {v.ejeValorNombre || "sin valor"}</span>
                                    <button type="button" onClick={() => p.onVariantes(p.variantes.filter(x => x.id !== v.id))}
                                        className="ml-auto text-xs font-medium text-red-500 hover:underline">Quitar</button>
                                </div>
                                {v.expandida && (
                                    <div className="mt-2 grid grid-cols-1 gap-2 sm:grid-cols-2">
                                        <label className="block"><span className="text-[11px] text-slate-500">SKU</span>
                                            <input className={p.inputCls} value={v.sku} onChange={e => setV(v.id, { sku: e.target.value })} placeholder="SKU de la variante" /></label>
                                        <label className="block"><span className="text-[11px] text-slate-500">Valor del eje</span>
                                            {selectorValor(v.ejeValorId, v.ejeValorNombre, (id, nombre) => setV(v.id, { ejeValorId: id, ejeValorNombre: nombre }))}</label>
                                        <label className="block"><span className="text-[11px] text-slate-500">Stock</span>
                                            <input type="number" min={0} step={1} className={p.inputCls} value={v.stock} onChange={e => setV(v.id, { stock: e.target.value === "" ? "" : Number(e.target.value) })} placeholder="0" /></label>
                                        <label className="block"><span className="text-[11px] text-slate-500">EAN</span>
                                            <input className={p.inputCls} value={v.ean} onChange={e => setV(v.id, { ean: e.target.value })} placeholder="Código de barras" /></label>
                                        {p.subirMl && <label className="block"><span className="text-[11px] text-slate-500">Cuota ML</span>
                                            <select className={full(p.selectCls)} value={v.cuotaMl} onChange={e => setV(v.id, { cuotaMl: Number(e.target.value) })}>
                                                {p.cuotasMlOpts.map(c => <option key={c.cuotas} value={c.cuotas}>{c.descripcion}</option>)}</select></label>}
                                        {p.subirKtHogar && <label className="block"><span className="text-[11px] text-slate-500">Cuota KT HOGAR</span>
                                            <select className={full(p.selectCls)} value={v.cuotaHogar} onChange={e => setV(v.id, { cuotaHogar: Number(e.target.value) })}>
                                                {p.cuotasHogarOpts.map(c => <option key={c.cuotas} value={c.cuotas}>{c.descripcion}</option>)}</select></label>}
                                        {p.subirKtGastro && <label className="block"><span className="text-[11px] text-slate-500">Cuota KT GASTRO</span>
                                            <select className={full(p.selectCls)} value={v.cuotaGastro} onChange={e => setV(v.id, { cuotaGastro: Number(e.target.value) })}>
                                                {p.cuotasGastroOpts.map(c => <option key={c.cuotas} value={c.cuotas}>{c.descripcion}</option>)}</select></label>}
                                    </div>
                                )}
                            </div>
                        ))}
                        <button type="button" onClick={() => p.onVariantes([...p.variantes, nuevaVariante(p.cuotasDefault)])}
                            className="rounded-lg border border-dashed border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-300">+ Agregar variante</button>
                    </div>
                </div>
            )}
        </div>
    );
}
